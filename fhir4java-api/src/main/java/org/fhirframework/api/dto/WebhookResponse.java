package org.fhirframework.api.dto;

import org.fhirframework.persistence.entity.WebhookEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO for webhook information.
 *
 * @param id Unique identifier of the webhook
 * @param callbackUrl URL to POST event notifications to
 * @param topics List of subscribed topics
 * @param enabled Whether the webhook is enabled
 * @param createdAt When the webhook was created
 */
public record WebhookResponse(
        Long id,
        String callbackUrl,
        List<String> topics,
        boolean enabled,
        Instant createdAt
) {
    /**
     * Create a WebhookResponse from a WebhookEntity.
     *
     * @param entity The entity to convert
     * @return The response DTO
     */
    public static WebhookResponse from(WebhookEntity entity) {
        List<String> topicsList;
        if (entity.getTopics() == null || entity.getTopics().isBlank()) {
            topicsList = Collections.emptyList();
        } else {
            topicsList = Arrays.stream(entity.getTopics().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        return new WebhookResponse(
                entity.getId(),
                entity.getCallbackUrl(),
                topicsList,
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCreatedAt()
        );
    }
}
