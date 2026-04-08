package org.fhirframework.api.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.persistence.entity.WebhookEntity;
import org.fhirframework.persistence.repository.WebhookRepository;
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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for managing webhooks and dispatching events to registered callbacks.
 * <p>
 * Subscribes to the {@link EventPublisher} to receive resource change events
 * and dispatches them to matching webhooks via HTTP POST with HMAC signatures.
 * </p>
 */
@Service
public class WebhookRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebhookRegistry.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final WebhookRepository webhookRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private EventPublisher eventPublisher;

    /**
     * Constructor with required dependencies.
     *
     * @param webhookRepository Repository for webhook operations
     */
    @Autowired
    public WebhookRegistry(WebhookRepository webhookRepository) {
        this(webhookRepository, new RestTemplate());
    }

    /**
     * Constructor with RestTemplate for testing.
     *
     * @param webhookRepository Repository for webhook operations
     * @param restTemplate RestTemplate for HTTP calls
     */
    public WebhookRegistry(WebhookRepository webhookRepository, RestTemplate restTemplate) {
        this.webhookRepository = webhookRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.executorService = Executors.newFixedThreadPool(10);
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

        List<WebhookEntity> matchingWebhooks = findMatchingWebhooks(
                event.resourceType(), event.action(), event.tenantId());

        for (WebhookEntity webhook : matchingWebhooks) {
            executorService.submit(() -> {
                try {
                    dispatchEvent(webhook, event);
                } catch (Exception e) {
                    log.error("Failed to dispatch event to webhook {}: {}",
                            webhook.getId(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Find webhooks that match a given resource type, action, and tenant.
     *
     * @param resourceType The FHIR resource type (e.g., "Patient")
     * @param action The action (e.g., "create", "update", "delete")
     * @param tenantId The tenant ID
     * @return List of matching webhooks
     */
    public List<WebhookEntity> findMatchingWebhooks(String resourceType, String action, String tenantId) {
        List<WebhookEntity> webhooks;
        if (tenantId != null && !tenantId.isBlank()) {
            webhooks = webhookRepository.findByTenantIdAndEnabled(tenantId, true);
        } else {
            webhooks = webhookRepository.findByEnabled(true);
        }

        return webhooks.stream()
                .filter(webhook -> webhook.matchesTopic(resourceType, action))
                .toList();
    }

    /**
     * Dispatch an event to a webhook.
     *
     * @param webhook The webhook to dispatch to
     * @param event The event to dispatch
     */
    public void dispatchEvent(WebhookEntity webhook, ResourceChangeEvent event) {
        if (webhook == null || event == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add HMAC signature if secret is configured
            if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
                String signature = computeHmacSignature(payload, webhook.getSecret());
                headers.set(SIGNATURE_HEADER, signature);
            }

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            log.debug("Dispatching event to webhook {}: {}", webhook.getId(), webhook.getCallbackUrl());
            restTemplate.postForEntity(webhook.getCallbackUrl(), request, String.class);
            log.info("Successfully dispatched event to webhook {}", webhook.getId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for webhook {}: {}", webhook.getId(), e.getMessage());
            throw new RuntimeException("Failed to serialize event", e);
        } catch (Exception e) {
            log.error("Failed to dispatch event to webhook {}: {}", webhook.getId(), e.getMessage());
            throw new RuntimeException("Failed to dispatch event to webhook", e);
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
}
