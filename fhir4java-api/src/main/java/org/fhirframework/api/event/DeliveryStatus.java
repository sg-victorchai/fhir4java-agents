package org.fhirframework.api.event;

/**
 * Status values for event delivery tracking.
 * <p>
 * Tracks the state of individual event deliveries through the retry mechanism.
 * </p>
 */
public enum DeliveryStatus {

    /**
     * Delivery is pending initial attempt.
     * <p>
     * The event has been queued but not yet delivered.
     * </p>
     */
    PENDING("PENDING"),

    /**
     * Delivery was successful.
     * <p>
     * The event was delivered to the subscriber and a successful
     * response was received (e.g., HTTP 2xx for webhooks).
     * </p>
     */
    DELIVERED("DELIVERED"),

    /**
     * Delivery failed but may be retried.
     * <p>
     * The delivery attempt failed (e.g., network error, HTTP 5xx),
     * but retry attempts remain. The event will be retried according
     * to the exponential backoff schedule.
     * </p>
     */
    FAILED("FAILED"),

    /**
     * Delivery permanently failed (dead letter).
     * <p>
     * All retry attempts have been exhausted, or the failure is
     * permanent (e.g., HTTP 4xx, invalid endpoint). The event will
     * not be retried and is moved to the dead letter queue.
     * </p>
     */
    DEAD_LETTER("DEAD_LETTER");

    private final String value;

    DeliveryStatus(String value) {
        this.value = value;
    }

    /**
     * Get the string value for database storage.
     *
     * @return the string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Parse a string value to enum.
     *
     * @param value the string value
     * @return the matching enum value
     * @throws IllegalArgumentException if the value is not recognized
     */
    public static DeliveryStatus fromValue(String value) {
        for (DeliveryStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown delivery status: " + value);
    }

    /**
     * Check if this status allows retry.
     *
     * @return true if the delivery can be retried
     */
    public boolean allowsRetry() {
        return this == PENDING || this == FAILED;
    }

    /**
     * Check if this is a terminal status.
     *
     * @return true if no further delivery attempts will be made
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == DEAD_LETTER;
    }

    @Override
    public String toString() {
        return value;
    }
}
