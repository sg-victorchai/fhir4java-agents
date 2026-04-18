package org.fhirframework.api.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirframework.api.event.EventsConfig;
import org.fhirframework.api.event.SubscriptionType;
import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for managing webhooks and dispatching events to registered callbacks.
 * <p>
 * Subscribes to the {@link EventPublisher} to receive resource change events
 * and dispatches them to matching webhooks via HTTP POST with HMAC signatures.
 * </p>
 * <p>
 * Webhooks are stored in the unified event_subscription table with
 * subscription_type = 'WEBHOOK'.
 * </p>
 */
@Service
public class WebhookRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebhookRegistry.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final EventSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private EventPublisher eventPublisher;

    /**
     * Optional events configuration for checking persistence mode.
     */
    private EventsConfig eventsConfig;

    /**
     * Constructor with required dependencies.
     *
     * @param subscriptionRepository Repository for subscription operations
     */
    @Autowired
    public WebhookRegistry(EventSubscriptionRepository subscriptionRepository) {
        this(subscriptionRepository, new RestTemplate());
    }

    /**
     * Constructor with RestTemplate for testing.
     *
     * @param subscriptionRepository Repository for subscription operations
     * @param restTemplate RestTemplate for HTTP calls
     */
    public WebhookRegistry(EventSubscriptionRepository subscriptionRepository, RestTemplate restTemplate) {
        this.subscriptionRepository = subscriptionRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Set events configuration for persistence mode checking.
     *
     * @param eventsConfig The events configuration
     */
    @Autowired(required = false)
    public void setEventsConfig(EventsConfig eventsConfig) {
        this.eventsConfig = eventsConfig;
    }

    /**
     * Set the event publisher and subscribe to events.
     * This is called by Spring after construction.
     *
     * @param eventPublisher The event publisher to subscribe to
     */
    @Autowired(required = false)
    public void setEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        if (eventPublisher != null) {
            subscribeToEvents();
        }
    }

    /**
     * Subscribe to the event publisher after bean construction.
     */
    @PostConstruct
    public void init() {
        if (eventPublisher != null) {
            subscribeToEvents();
        }
    }

    private void subscribeToEvents() {
        eventPublisher.subscribe(this::onResourceChange);
        log.info("WebhookRegistry subscribed to EventPublisher");
    }

    /**
     * Handle resource change events by dispatching to matching webhooks.
     *
     * @param event The resource change event
     */
    void onResourceChange(ResourceChangeEvent event) {
        if (event == null) {
            return;
        }

        log.debug("Received event: {} {} {} (tenant: {})",
                event.action(), event.resourceType(), event.resourceId(), event.tenantId());

        List<EventSubscriptionEntity> matchingSubscriptions = findMatchingSubscriptions(
                event.resourceType(), event.action(), event.tenantId());

        for (EventSubscriptionEntity subscription : matchingSubscriptions) {
            executorService.submit(() -> {
                try {
                    dispatchEvent(subscription, event);
                } catch (Exception e) {
                    log.error("Failed to dispatch event to subscription {}: {}",
                            subscription.getSubscriptionId(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Find webhook subscriptions that match a given resource type, action, and tenant.
     *
     * @param resourceType The FHIR resource type (e.g., "Patient")
     * @param action The action (e.g., "create", "update", "delete")
     * @param tenantId The tenant ID
     * @return List of matching subscriptions
     */
    public List<EventSubscriptionEntity> findMatchingSubscriptions(String resourceType, String action, String tenantId) {
        if (subscriptionRepository == null) {
            return Collections.emptyList();
        }

        List<EventSubscriptionEntity> subscriptions;
        if (tenantId != null && !tenantId.isBlank()) {
            subscriptions = subscriptionRepository.findActiveByTenantAndType(
                    tenantId, SubscriptionType.WEBHOOK.name());
        } else {
            subscriptions = subscriptionRepository.findActiveByType(SubscriptionType.WEBHOOK.name());
        }

        return subscriptions.stream()
                .filter(sub -> sub.matchesEvent(resourceType, action))
                .toList();
    }

    /**
     * Dispatch an event to a webhook subscription.
     *
     * @param subscription The subscription to dispatch to
     * @param event The event to dispatch
     */
    public void dispatchEvent(EventSubscriptionEntity subscription, ResourceChangeEvent event) {
        if (subscription == null || event == null || subscription.getSubscriberEndpoint() == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add HMAC signature if secret is configured
            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                String signature = computeHmacSignature(payload, subscription.getSecret());
                headers.set(SIGNATURE_HEADER, signature);
            }

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            log.debug("Dispatching event to subscription {}: {}",
                    subscription.getSubscriptionId(), subscription.getSubscriberEndpoint());
            restTemplate.postForEntity(subscription.getSubscriberEndpoint(), request, String.class);
            log.info("Successfully dispatched event to subscription {}", subscription.getSubscriptionId());

            // Update last event timestamp
            subscriptionRepository.updateLastEventAt(subscription.getSubscriptionId(), Instant.now());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for subscription {}: {}",
                    subscription.getSubscriptionId(), e.getMessage());
            throw new RuntimeException("Failed to serialize event", e);
        } catch (Exception e) {
            log.error("Failed to dispatch event to subscription {}: {}",
                    subscription.getSubscriptionId(), e.getMessage());
            throw new RuntimeException("Failed to dispatch event to subscription", e);
        }
    }

    /**
     * Compute HMAC-SHA256 signature for a payload.
     *
     * @param payload The payload to sign
     * @param secret The HMAC secret
     * @return Base64-encoded signature
     */
    public String computeHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to compute HMAC signature: {}", e.getMessage());
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Shutdown the executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Check if persistence mode is enabled.
     *
     * @return true if persistence is enabled
     */
    public boolean isPersistenceEnabled() {
        return eventsConfig != null && eventsConfig.isPersistenceEnabled();
    }
}
