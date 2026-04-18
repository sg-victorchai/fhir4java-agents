package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * JPA Entity for event delivery tracking.
 * <p>
 * Supports the database-backed retry mechanism with:
 * <ul>
 *   <li>Full audit trail of delivery attempts</li>
 *   <li>Exponential backoff retry scheduling</li>
 *   <li>Dead-letter handling for failed deliveries</li>
 *   <li>Acknowledgement tracking</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "event_delivery_log", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDeliveryLogEntity {

    /**
     * Status constants for delivery tracking.
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the event.
     */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /**
     * Type of event (e.g., "resource-change").
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * FHIR resource type that triggered the event.
     */
    @Column(name = "resource_type", length = 100)
    private String resourceType;

    /**
     * ID of the resource that triggered the event.
     */
    @Column(name = "resource_id", length = 64)
    private String resourceId;

    /**
     * Action performed: create, update, delete.
     */
    @Column(name = "action", length = 20)
    private String action;

    /**
     * Tenant context.
     */
    @Column(name = "tenant_id", length = 64)
    @Builder.Default
    private String tenantId = "default";

    /**
     * Reference to the subscription this delivery is for.
     */
    @Column(name = "subscription_id", nullable = false, length = 64)
    private String subscriptionId;

    /**
     * Type of subscription: SSE, FHIR_SUBSCRIPTION, WEBHOOK.
     */
    @Column(name = "subscription_type", nullable = false, length = 20)
    private String subscriptionType;

    /**
     * Delivery status: PENDING, DELIVERED, FAILED, DEAD_LETTER.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Timestamp when delivery succeeded.
     */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /**
     * HTTP response code for webhook deliveries.
     */
    @Column(name = "response_code")
    private Integer responseCode;

    /**
     * Response body for debugging (may be truncated).
     */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /**
     * Error message if delivery failed.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of retry attempts made.
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum number of retries allowed.
     */
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * Timestamp for next retry attempt (exponential backoff).
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * Whether the event was acknowledged by the subscriber.
     */
    @Column(name = "acknowledged")
    @Builder.Default
    private Boolean acknowledged = false;

    /**
     * Timestamp when acknowledgement was received.
     */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /**
     * Client-provided acknowledgement ID.
     */
    @Column(name = "acknowledgement_id", length = 64)
    private String acknowledgementId;

    /**
     * Timestamp when this record was created.
     */
    @Column(name = "created_at")
    private Instant createdAt;

    /**
     * Timestamp when this record was last updated.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Full event payload stored for retry attempts.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> eventPayload;

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
     * Check if this delivery can be retried.
     *
     * @return true if retry count is less than max retries and status allows retry
     */
    public boolean canRetry() {
        return retryCount < maxRetries &&
                (STATUS_PENDING.equals(status) || STATUS_FAILED.equals(status));
    }

    /**
     * Check if this delivery is ready for retry.
     *
     * @return true if it can be retried and the next retry time has passed
     */
    public boolean isRetryDue() {
        if (!canRetry()) {
            return false;
        }
        if (nextRetryAt == null) {
            return true;
        }
        return Instant.now().isAfter(nextRetryAt) || Instant.now().equals(nextRetryAt);
    }

    /**
     * Mark delivery as successful.
     *
     * @param responseCode The HTTP response code received
     * @param responseBody The response body (may be truncated for storage)
     */
    public void markDelivered(Integer responseCode, String responseBody) {
        this.status = STATUS_DELIVERED;
        this.deliveredAt = Instant.now();
        this.responseCode = responseCode;
        this.responseBody = truncateResponseBody(responseBody);
        this.nextRetryAt = null;
        this.errorMessage = null;
    }

    /**
     * Mark delivery as failed and schedule retry.
     *
     * @param errorMessage   The error message
     * @param responseCode   Optional HTTP response code
     * @param initialDelayMs Initial delay for exponential backoff
     * @param multiplier     Multiplier for exponential backoff
     * @param maxDelayMs     Maximum delay cap
     */
    public void markFailed(String errorMessage, Integer responseCode,
                           long initialDelayMs, double multiplier, long maxDelayMs) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        this.responseCode = responseCode;

        if (this.retryCount >= this.maxRetries) {
            this.status = STATUS_DEAD_LETTER;
            this.nextRetryAt = null;
        } else {
            this.status = STATUS_FAILED;
            this.nextRetryAt = calculateNextRetryTime(initialDelayMs, multiplier, maxDelayMs);
        }
    }

    /**
     * Mark as dead letter without incrementing retry count.
     * Used when delivery permanently fails.
     *
     * @param errorMessage The error message
     */
    public void markDeadLetter(String errorMessage) {
        this.status = STATUS_DEAD_LETTER;
        this.errorMessage = errorMessage;
        this.nextRetryAt = null;
    }

    /**
     * Record acknowledgement from subscriber.
     *
     * @param acknowledgementId Client-provided acknowledgement ID
     */
    public void acknowledge(String acknowledgementId) {
        this.acknowledged = true;
        this.acknowledgedAt = Instant.now();
        this.acknowledgementId = acknowledgementId;
    }

    /**
     * Calculate the next retry time using exponential backoff.
     *
     * @param initialDelayMs Initial delay in milliseconds
     * @param multiplier     Multiplier for each retry
     * @param maxDelayMs     Maximum delay cap in milliseconds
     * @return The calculated next retry time
     */
    public Instant calculateNextRetryTime(long initialDelayMs, double multiplier, long maxDelayMs) {
        // Exponential backoff: delay = initialDelay * (multiplier ^ retryCount)
        long delayMs = (long) (initialDelayMs * Math.pow(multiplier, retryCount - 1));
        // Cap at max delay
        delayMs = Math.min(delayMs, maxDelayMs);
        return Instant.now().plusMillis(delayMs);
    }

    /**
     * Truncate response body to fit in database column.
     *
     * @param body The response body
     * @return Truncated body if necessary
     */
    private String truncateResponseBody(String body) {
        if (body == null) {
            return null;
        }
        // Limit to 10000 characters for storage
        if (body.length() > 10000) {
            return body.substring(0, 10000) + "... (truncated)";
        }
        return body;
    }
}
