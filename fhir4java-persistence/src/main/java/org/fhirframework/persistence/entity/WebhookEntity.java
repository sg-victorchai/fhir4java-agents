package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA Entity for webhook registrations.
 * <p>
 * Webhooks allow external systems (including AI agents) to subscribe to
 * FHIR resource change events and receive HTTP POST notifications.
 * Payloads are signed using HMAC with the configured secret.
 * </p>
 */
@Entity
@Table(name = "webhook", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * URL to POST event notifications to.
     */
    @Column(name = "callback_url", nullable = false, length = 512)
    private String callbackUrl;

    /**
     * Comma-separated list of topics to subscribe to.
     * Format: "ResourceType.action" (e.g., "Patient.create,Patient.update,Observation.delete")
     */
    @Column(name = "topics", length = 1024)
    private String topics;

    /**
     * HMAC secret for signing webhook payloads.
     * Used to generate X-Webhook-Signature header.
     */
    @Column(name = "secret", length = 256)
    private String secret;

    /**
     * Tenant ID this webhook is associated with.
     * Webhooks only receive events for their tenant.
     */
    @Column(name = "tenant_id", length = 64)
    @Builder.Default
    private String tenantId = "default";

    /**
     * Whether this webhook is enabled.
     * Disabled webhooks do not receive notifications.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Timestamp when this webhook was created.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Check if this webhook matches a given topic.
     * Topics are in format "ResourceType.action" (e.g., "Patient.create").
     *
     * @param resourceType The resource type (e.g., "Patient")
     * @param action The action (e.g., "create", "update", "delete")
     * @return true if this webhook is subscribed to the given topic
     */
    public boolean matchesTopic(String resourceType, String action) {
        if (topics == null || topics.isBlank()) {
            // No topics specified means subscribe to all
            return true;
        }
        String topic = resourceType + "." + action;
        String[] subscribedTopics = topics.split(",");
        for (String subscribedTopic : subscribedTopics) {
            String trimmed = subscribedTopic.trim();
            if (trimmed.equals(topic) || trimmed.equals(resourceType + ".*") || trimmed.equals("*.*")) {
                return true;
            }
        }
        return false;
    }
}
