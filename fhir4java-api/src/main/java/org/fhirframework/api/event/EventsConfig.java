package org.fhirframework.api.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the real-time events system.
 * <p>
 * Controls enabling/disabling of:
 * <ul>
 *   <li>The entire event system (master switch)</li>
 *   <li>SSE streaming endpoint</li>
 *   <li>Webhook delivery</li>
 *   <li>Persistent storage mode (production vs development)</li>
 *   <li>Retry mechanism settings</li>
 *   <li>Acknowledgement tracking</li>
 * </ul>
 * </p>
 *
 * @see EventStreamController
 * @see InMemoryEventPublisher
 * @see PersistentEventPublisher
 */
@Component
public class EventsConfig {

    // === Master and Channel Switches ===

    @Value("${fhir4java.events.enabled:true}")
    private boolean eventsEnabled;

    @Value("${fhir4java.events.sse.enabled:true}")
    private boolean sseEnabled;

    @Value("${fhir4java.events.webhooks.enabled:true}")
    private boolean webhooksEnabled;

    // === Persistence Configuration ===

    /**
     * Enable persistent storage mode (production mode).
     * When true, uses database-backed event publishing with full audit trail.
     * When false (default), uses lightweight in-memory event publishing for development.
     */
    @Value("${fhir4java.events.persistence.enabled:false}")
    private boolean persistenceEnabled;

    // === Retry Configuration ===

    /**
     * Enable the retry mechanism for failed deliveries.
     */
    @Value("${fhir4java.events.retry.enabled:true}")
    private boolean retryEnabled;

    /**
     * Maximum number of retry attempts before marking as dead letter.
     */
    @Value("${fhir4java.events.retry.max-retries:3}")
    private int maxRetries;

    /**
     * Initial delay in milliseconds for exponential backoff.
     */
    @Value("${fhir4java.events.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    /**
     * Maximum delay cap in milliseconds for exponential backoff.
     */
    @Value("${fhir4java.events.retry.max-delay-ms:60000}")
    private long maxDelayMs;

    /**
     * Multiplier for exponential backoff calculation.
     */
    @Value("${fhir4java.events.retry.multiplier:2.0}")
    private double multiplier;

    /**
     * Interval in milliseconds for the retry scheduler to poll for retryable deliveries.
     */
    @Value("${fhir4java.events.retry.poll-interval-ms:5000}")
    private long pollIntervalMs;

    /**
     * Maximum number of deliveries to process per retry poll.
     */
    @Value("${fhir4java.events.retry.batch-size:100}")
    private int retryBatchSize;

    // === Acknowledgement Configuration ===

    /**
     * Enable acknowledgement tracking for event deliveries.
     */
    @Value("${fhir4java.events.acknowledgement.enabled:true}")
    private boolean acknowledgementEnabled;

    /**
     * Timeout in milliseconds to wait for acknowledgement before considering delivery unconfirmed.
     */
    @Value("${fhir4java.events.acknowledgement.timeout-ms:30000}")
    private long acknowledgementTimeoutMs;

    // === Master and Channel Getters ===

    /**
     * Check if the entire events system is enabled.
     *
     * @return true if events are enabled, false otherwise
     */
    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    /**
     * Check if SSE streaming is enabled.
     * Returns false if either the master switch or SSE-specific switch is disabled.
     *
     * @return true if SSE streaming is enabled, false otherwise
     */
    public boolean isSseEnabled() {
        return eventsEnabled && sseEnabled;
    }

    /**
     * Check if webhook delivery is enabled.
     * Returns false if either the master switch or webhook-specific switch is disabled.
     *
     * @return true if webhooks are enabled, false otherwise
     */
    public boolean isWebhooksEnabled() {
        return eventsEnabled && webhooksEnabled;
    }

    // === Persistence Getters ===

    /**
     * Check if persistent storage mode is enabled.
     * <p>
     * When true, uses database-backed event publishing with:
     * <ul>
     *   <li>Persistent subscription storage</li>
     *   <li>Event delivery logging</li>
     *   <li>Database-backed retry mechanism</li>
     *   <li>Full audit trail</li>
     * </ul>
     * When false (default), uses lightweight in-memory mode for development.
     * </p>
     *
     * @return true if persistence is enabled (production mode)
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    // === Retry Getters ===

    /**
     * Check if the retry mechanism is enabled.
     *
     * @return true if retry is enabled
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    /**
     * Get the maximum number of retry attempts.
     *
     * @return max retries before dead letter
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Get the initial delay for exponential backoff.
     *
     * @return initial delay in milliseconds
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    /**
     * Get the maximum delay cap for exponential backoff.
     *
     * @return maximum delay in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    /**
     * Get the multiplier for exponential backoff.
     *
     * @return the multiplier
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * Get the interval for polling retryable deliveries.
     *
     * @return poll interval in milliseconds
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Get the batch size for retry processing.
     *
     * @return maximum deliveries to process per poll
     */
    public int getRetryBatchSize() {
        return retryBatchSize;
    }

    // === Acknowledgement Getters ===

    /**
     * Check if acknowledgement tracking is enabled.
     *
     * @return true if acknowledgement is enabled
     */
    public boolean isAcknowledgementEnabled() {
        return acknowledgementEnabled;
    }

    /**
     * Get the acknowledgement timeout.
     *
     * @return timeout in milliseconds
     */
    public long getAcknowledgementTimeoutMs() {
        return acknowledgementTimeoutMs;
    }
}
