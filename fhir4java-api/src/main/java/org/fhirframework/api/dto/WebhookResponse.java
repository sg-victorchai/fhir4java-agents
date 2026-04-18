package org.fhirframework.api.dto;

import org.fhirframework.persistence.entity.EventSubscriptionEntity;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for webhook information.
 *
 * @param subscriptionId Unique identifier of the webhook subscription
 * @param callbackUrl URL to POST event notifications to
 * @param topics List of subscribed topics
 * @param enabled Whether the webhook is enabled
 * @param status The subscription status (ACTIVE, PAUSED, etc.)
 * @param createdAt When the webhook was created
 */
public record WebhookResponse(
        String subscriptionId,
        String callbackUrl,
        List<String> topics,
        boolean enabled,
        String status,
        Instant createdAt
) {
    /**
     * Create a WebhookResponse from an EventSubscriptionEntity.
     *
     * @param entity The entity to convert
     * @return The response DTO
     */
    public static WebhookResponse from(EventSubscriptionEntity entity) {
        return new WebhookResponse(
                entity.getSubscriptionId(),
                entity.getSubscriberEndpoint(),
                entity.getTopicList(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
