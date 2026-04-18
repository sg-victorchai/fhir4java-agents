package org.fhirframework.api.event;

/**
 * Types of event subscriptions supported by the real-time event system.
 * <p>
 * The system supports three subscription types:
 * <ul>
 *   <li>{@link #SSE} - Server-Sent Events for browser/client streaming</li>
 *   <li>{@link #FHIR_SUBSCRIPTION} - FHIR R5 Subscription resources</li>
 *   <li>{@link #WEBHOOK} - HTTP webhook callbacks</li>
 * </ul>
 * </p>
 */
public enum SubscriptionType {

    /**
     * Server-Sent Events (SSE) subscription.
     * <p>
     * Used for real-time streaming to browser clients via HTTP streaming.
     * Events are pushed to clients over a persistent HTTP connection.
     * </p>
     */
    SSE("SSE"),

    /**
     * FHIR R5 Subscription resource.
     * <p>
     * Native FHIR Subscription implementation following HL7 FHIR R5 specification.
     * Supports rest-hook channel type for HTTP callbacks.
     * </p>
     */
    FHIR_SUBSCRIPTION("FHIR_SUBSCRIPTION"),

    /**
     * HTTP Webhook subscription.
     * <p>
     * General-purpose webhook for external systems and AI agents.
     * Events are delivered via HTTP POST with HMAC signature verification.
     * </p>
     */
    WEBHOOK("WEBHOOK");

    private final String value;

    SubscriptionType(String value) {
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
    public static SubscriptionType fromValue(String value) {
        for (SubscriptionType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown subscription type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
