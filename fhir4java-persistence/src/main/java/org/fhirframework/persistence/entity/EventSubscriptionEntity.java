package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JPA Entity for persistent subscription storage.
 * <p>
 * Supports all subscription types: SSE, FHIR Subscriptions, and Webhooks.
 * Provides unified subscription management with tenant isolation and lifecycle tracking.
 * </p>
 */
@Entity
@Table(name = "event_subscription", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the subscription.
     */
    @Column(name = "subscription_id", nullable = false, unique = true, length = 64)
    private String subscriptionId;

    /**
     * Type of subscription: SSE, FHIR_SUBSCRIPTION, WEBHOOK.
     */
    @Column(name = "subscription_type", nullable = false, length = 20)
    private String subscriptionType;

    /**
     * Tenant ID for multi-tenancy support.
     */
    @Column(name = "tenant_id", length = 64)
    @Builder.Default
    private String tenantId = "default";

    /**
     * Human-readable name for the subscriber.
     */
    @Column(name = "subscriber_name", length = 255)
    private String subscriberName;

    /**
     * Endpoint URL for WEBHOOK and FHIR_SUBSCRIPTION types.
     */
    @Column(name = "subscriber_endpoint", length = 512)
    private String subscriberEndpoint;

    /**
     * Comma-separated list of topics (resource types).
     */
    @Column(name = "topics", columnDefinition = "TEXT")
    private String topics;

    /**
     * Comma-separated list of actions (create, update, delete).
     */
    @Column(name = "actions", columnDefinition = "TEXT")
    private String actions;

    /**
     * JSON filter criteria for advanced filtering.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_criteria", columnDefinition = "jsonb")
    private Map<String, Object> filterCriteria;

    /**
     * Subscription status: ACTIVE, PAUSED, EXPIRED, TERMINATED.
     */
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * Whether the subscription is enabled.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * HMAC secret for webhook signature verification.
     */
    @Column(name = "secret", length = 256)
    private String secret;

    /**
     * Timestamp when this subscription was created.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Timestamp when this subscription was last updated.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Optional subscription expiration timestamp.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Last time an event was sent to this subscription.
     */
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    /**
     * Additional metadata as JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if this subscription is active and enabled.
     *
     * @return true if the subscription can receive events
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(enabled) && "ACTIVE".equals(status);
    }

    /**
     * Check if this subscription has expired.
     *
     * @return true if the subscription has passed its expiration time
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Get the topics as a list.
     *
     * @return List of topic strings (resource types)
     */
    public List<String> getTopicList() {
        if (topics == null || topics.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(topics.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Get the actions as a list.
     *
     * @return List of action strings
     */
    public List<String> getActionList() {
        if (actions == null || actions.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Check if this subscription matches a given resource type and action.
     * <p>
     * Supports multiple topic formats:
     * <ul>
     *   <li>Combined format: "Patient.create", "Patient.update", "Observation.*", "*.*"</li>
     *   <li>Separate format: topics="Patient,Observation" with actions="create,update"</li>
     *   <li>Wildcards: "Patient.*" matches all Patient actions, "*.*" matches all events</li>
     * </ul>
     * </p>
     *
     * @param resourceType The FHIR resource type (e.g., "Patient")
     * @param action       The action (e.g., "create", "update", "delete")
     * @return true if this subscription should receive events for this resource type and action
     */
    public boolean matchesEvent(String resourceType, String action) {
        // If no topics specified, match all events
        if (topics == null || topics.isBlank()) {
            return true;
        }

        List<String> topicList = getTopicList();

        // Check if any topic matches the event
        for (String topic : topicList) {
            // Global wildcard matches everything
            if ("*".equals(topic) || "*.*".equals(topic)) {
                return true;
            }

            // Check if topic contains a dot (combined format: "ResourceType.action")
            if (topic.contains(".")) {
                String[] parts = topic.split("\\.", 2);
                String topicResourceType = parts[0];
                String topicAction = parts.length > 1 ? parts[1] : "*";

                // Check resource type match
                boolean resourceMatches = "*".equals(topicResourceType) || topicResourceType.equals(resourceType);

                // Check action match
                boolean actionMatches = "*".equals(topicAction) || topicAction.equals(action);

                if (resourceMatches && actionMatches) {
                    return true;
                }
            } else {
                // Simple format: just resource type (e.g., "Patient")
                // In this case, also check the actions column
                if (topic.equals(resourceType) || "*".equals(topic)) {
                    // If actions column is specified, check it
                    if (actions != null && !actions.isBlank()) {
                        List<String> actionList = getActionList();
                        if (actionList.contains(action) || actionList.contains("*")) {
                            return true;
                        }
                    } else {
                        // No actions specified means match all actions
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if this subscription matches a given topic (resource type).
     * <p>
     * Note: For combined format topics (e.g., "Patient.create"), use {@link #matchesEvent(String, String)} instead.
     * </p>
     *
     * @param resourceType The resource type to check
     * @return true if this subscription subscribes to this resource type
     */
    public boolean matchesTopic(String resourceType) {
        if (topics == null || topics.isBlank()) {
            // No topics specified means subscribe to all
            return true;
        }
        List<String> topicList = getTopicList();

        for (String topic : topicList) {
            // Direct match or wildcard
            if (topic.equals(resourceType) || "*".equals(topic) || "*.*".equals(topic)) {
                return true;
            }
            // Combined format: extract resource type
            if (topic.contains(".")) {
                String topicResourceType = topic.split("\\.", 2)[0];
                if (topicResourceType.equals(resourceType) || "*".equals(topicResourceType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if this subscription matches a given action.
     *
     * @param action The action to check (create, update, delete)
     * @return true if this subscription subscribes to this action
     */
    public boolean matchesAction(String action) {
        if (actions == null || actions.isBlank()) {
            // No actions specified means subscribe to all
            return true;
        }
        List<String> actionList = getActionList();
        return actionList.contains(action) || actionList.contains("*");
    }

    /**
     * Update the last event timestamp.
     */
    public void recordEventDelivery() {
        this.lastEventAt = Instant.now();
    }
}
