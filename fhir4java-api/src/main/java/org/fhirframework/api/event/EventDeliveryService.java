package org.fhirframework.api.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirframework.persistence.entity.EventDeliveryLogEntity;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventDeliveryLogRepository;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling event delivery to subscribers.
 * <p>
 * Manages the actual delivery of events to webhooks, FHIR subscriptions,
 * and SSE endpoints, with result recording and retry scheduling.
 * </p>
 */
@Service
public class EventDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EventDeliveryService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    private static final String EVENT_ID_HEADER = "X-Event-ID";

    private final EventDeliveryLogRepository deliveryLogRepository;
    private final EventSubscriptionRepository subscriptionRepository;
    private final EventsConfig eventsConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EventDeliveryService(
            EventDeliveryLogRepository deliveryLogRepository,
            EventSubscriptionRepository subscriptionRepository,
            EventsConfig eventsConfig) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventsConfig = eventsConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Create a delivery log entry for an event-subscription pair.
     *
     * @param event        The resource change event
     * @param subscription The subscription to deliver to
     * @return The created delivery log entity
     */
    @Transactional
    public EventDeliveryLogEntity createDeliveryLog(
            org.fhirframework.core.event.ResourceChangeEvent event,
            EventSubscriptionEntity subscription) {

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> eventPayload = buildEventPayload(event);

        EventDeliveryLogEntity deliveryLog = EventDeliveryLogEntity.builder()
                .eventId(eventId)
                .eventType("resource-change")
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .action(event.action())
                .tenantId(event.tenantId())
                .subscriptionId(subscription.getSubscriptionId())
                .subscriptionType(subscription.getSubscriptionType())
                .status(DeliveryStatus.PENDING.getValue())
                .maxRetries(eventsConfig.getMaxRetries())
                .eventPayload(eventPayload)
                .build();

        return deliveryLogRepository.save(deliveryLog);
    }

    /**
     * Attempt to deliver an event to a webhook endpoint.
     *
     * @param deliveryLog  The delivery log entry
     * @param subscription The subscription to deliver to
     * @return true if delivery was successful
     */
    @Transactional
    public boolean deliverToWebhook(EventDeliveryLogEntity deliveryLog, EventSubscriptionEntity subscription) {
        if (subscription.getSubscriberEndpoint() == null) {
            log.error("No endpoint configured for subscription {}", subscription.getSubscriptionId());
            deliveryLog.markDeadLetter("No endpoint configured");
            deliveryLogRepository.save(deliveryLog);
            return false;
        }

        try {
            String payload = objectMapper.writeValueAsString(deliveryLog.getEventPayload());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(EVENT_ID_HEADER, deliveryLog.getEventId());

            // Add HMAC signature if secret is configured
            if (subscription.getSecret() != null && !subscription.getSecret().isBlank()) {
                String signature = computeHmacSignature(payload, subscription.getSecret());
                headers.set(SIGNATURE_HEADER, signature);
            }

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            log.debug("Delivering event {} to webhook {} for subscription {}",
                    deliveryLog.getEventId(), subscription.getSubscriberEndpoint(),
                    subscription.getSubscriptionId());

            ResponseEntity<String> response = restTemplate.postForEntity(
                    subscription.getSubscriberEndpoint(), request, String.class);

            // Success
            deliveryLog.markDelivered(response.getStatusCode().value(), response.getBody());
            deliveryLogRepository.save(deliveryLog);

            // Update subscription's last event timestamp
            subscription.recordEventDelivery();
            subscriptionRepository.save(subscription);

            log.info("Successfully delivered event {} to subscription {}",
                    deliveryLog.getEventId(), subscription.getSubscriptionId());
            return true;

        } catch (HttpClientErrorException e) {
            // 4xx errors - don't retry, mark as dead letter
            log.warn("Client error delivering event {} to subscription {}: {} {}",
                    deliveryLog.getEventId(), subscription.getSubscriptionId(),
                    e.getStatusCode(), e.getMessage());
            deliveryLog.markDeadLetter("Client error: " + e.getStatusCode() + " " + e.getMessage());
            deliveryLogRepository.save(deliveryLog);
            return false;

        } catch (HttpServerErrorException e) {
            // 5xx errors - retry
            log.warn("Server error delivering event {} to subscription {}: {} {}",
                    deliveryLog.getEventId(), subscription.getSubscriptionId(),
                    e.getStatusCode(), e.getMessage());
            deliveryLog.markFailed("Server error: " + e.getStatusCode() + " " + e.getMessage(),
                    e.getStatusCode().value(),
                    eventsConfig.getInitialDelayMs(),
                    eventsConfig.getMultiplier(),
                    eventsConfig.getMaxDelayMs());
            deliveryLogRepository.save(deliveryLog);
            return false;

        } catch (ResourceAccessException e) {
            // Network errors - retry
            log.warn("Network error delivering event {} to subscription {}: {}",
                    deliveryLog.getEventId(), subscription.getSubscriptionId(), e.getMessage());
            deliveryLog.markFailed("Network error: " + e.getMessage(),
                    null,
                    eventsConfig.getInitialDelayMs(),
                    eventsConfig.getMultiplier(),
                    eventsConfig.getMaxDelayMs());
            deliveryLogRepository.save(deliveryLog);
            return false;

        } catch (JsonProcessingException e) {
            // Serialization error - don't retry
            log.error("Failed to serialize event payload for delivery {}: {}",
                    deliveryLog.getEventId(), e.getMessage());
            deliveryLog.markDeadLetter("Serialization error: " + e.getMessage());
            deliveryLogRepository.save(deliveryLog);
            return false;

        } catch (Exception e) {
            // Unexpected error - retry
            log.error("Unexpected error delivering event {} to subscription {}: {}",
                    deliveryLog.getEventId(), subscription.getSubscriptionId(), e.getMessage(), e);
            deliveryLog.markFailed("Unexpected error: " + e.getMessage(),
                    null,
                    eventsConfig.getInitialDelayMs(),
                    eventsConfig.getMultiplier(),
                    eventsConfig.getMaxDelayMs());
            deliveryLogRepository.save(deliveryLog);
            return false;
        }
    }

    /**
     * Retry a failed delivery.
     *
     * @param deliveryLog The delivery log entry to retry
     * @return true if retry was successful
     */
    @Transactional
    public boolean retryDelivery(EventDeliveryLogEntity deliveryLog) {
        // Find the subscription
        return subscriptionRepository.findBySubscriptionId(deliveryLog.getSubscriptionId())
                .map(subscription -> {
                    if (!subscription.isActive()) {
                        log.info("Subscription {} is no longer active, marking delivery {} as dead letter",
                                subscription.getSubscriptionId(), deliveryLog.getEventId());
                        deliveryLog.markDeadLetter("Subscription no longer active");
                        deliveryLogRepository.save(deliveryLog);
                        return false;
                    }

                    String subType = subscription.getSubscriptionType();
                    if (SubscriptionType.WEBHOOK.getValue().equals(subType)) {
                        return deliverToWebhook(deliveryLog, subscription);
                    } else {
                        // For SSE and FHIR subscriptions, delivery is handled differently
                        log.debug("Retry not applicable for subscription type {}", subType);
                        return false;
                    }
                })
                .orElseGet(() -> {
                    log.warn("Subscription {} not found for delivery {}",
                            deliveryLog.getSubscriptionId(), deliveryLog.getEventId());
                    deliveryLog.markDeadLetter("Subscription not found");
                    deliveryLogRepository.save(deliveryLog);
                    return false;
                });
    }

    /**
     * Acknowledge a delivery.
     *
     * @param eventId          The event ID
     * @param subscriptionId   The subscription ID
     * @param acknowledgementId The acknowledgement ID from the client
     * @return true if acknowledgement was recorded
     */
    @Transactional
    public boolean acknowledgeDelivery(String eventId, String subscriptionId, String acknowledgementId) {
        int updated = deliveryLogRepository.acknowledgeDelivery(
                eventId, subscriptionId,
                acknowledgementId != null ? acknowledgementId : UUID.randomUUID().toString());
        if (updated > 0) {
            log.info("Acknowledged delivery for event {} subscription {}",
                    eventId, subscriptionId);
            return true;
        }
        log.warn("No delivery found for event {} subscription {} to acknowledge",
                eventId, subscriptionId);
        return false;
    }

    /**
     * Build the event payload for storage and delivery.
     *
     * @param event The resource change event
     * @return Map representation of the event
     */
    private Map<String, Object> buildEventPayload(org.fhirframework.core.event.ResourceChangeEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resourceType", event.resourceType());
        payload.put("resourceId", event.resourceId());
        payload.put("action", event.action());
        payload.put("tenantId", event.tenantId());
        payload.put("timestamp", event.timestamp().toString());
        return payload;
    }

    /**
     * Compute HMAC-SHA256 signature for a payload.
     *
     * @param payload The payload to sign
     * @param secret  The HMAC secret
     * @return Base64-encoded signature
     */
    private String computeHmacSignature(String payload, String secret) {
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
     * Get delivery statistics.
     *
     * @return Map of status to count
     */
    public Map<String, Long> getDeliveryStats() {
        Map<String, Long> stats = new HashMap<>();
        for (DeliveryStatus status : DeliveryStatus.values()) {
            stats.put(status.getValue(), deliveryLogRepository.countByStatus(status.getValue()));
        }
        return stats;
    }

    /**
     * Clean up old delivery logs.
     *
     * @param retentionDays Number of days to retain logs
     * @return Number of deleted records
     */
    @Transactional
    public int cleanupOldDeliveryLogs(int retentionDays) {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
        int deleted = deliveryLogRepository.deleteOlderThan(cutoff);
        log.info("Cleaned up {} old delivery logs older than {} days", deleted, retentionDays);
        return deleted;
    }
}
