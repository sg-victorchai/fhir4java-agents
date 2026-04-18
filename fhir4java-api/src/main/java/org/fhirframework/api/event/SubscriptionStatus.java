package org.fhirframework.api.event;

/**
 * Status values for event subscriptions.
 * <p>
 * Tracks the lifecycle state of a subscription.
 * </p>
 */
public enum SubscriptionStatus {

    /**
     * Subscription is active and receiving events.
     */
    ACTIVE("ACTIVE"),

    /**
     * Subscription is temporarily paused.
     * <p>
     * Events are not delivered while paused, but the subscription
     * remains registered and can be resumed.
     * </p>
     */
    PAUSED("PAUSED"),

    /**
     * Subscription has expired.
     * <p>
     * The subscription has passed its expiration time and is no longer active.
     * It may be cleaned up by maintenance jobs.
     * </p>
     */
    EXPIRED("EXPIRED"),

    /**
     * Subscription has been terminated.
     * <p>
     * The subscription was explicitly cancelled by the subscriber or
     * administratively terminated. It will not receive further events.
     * </p>
     */
    TERMINATED("TERMINATED");

    private final String value;

    SubscriptionStatus(String value) {
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
    public static SubscriptionStatus fromValue(String value) {
        for (SubscriptionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown subscription status: " + value);
    }

    /**
     * Check if this status allows event delivery.
     *
     * @return true if events can be delivered in this status
     */
    public boolean allowsDelivery() {
        return this == ACTIVE;
    }

    @Override
    public String toString() {
        return value;
    }
}
