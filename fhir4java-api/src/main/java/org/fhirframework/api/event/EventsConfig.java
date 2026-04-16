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
 * </ul>
 * </p>
 *
 * @see EventStreamController
 * @see InMemoryEventPublisher
 */
@Component
public class EventsConfig {

    @Value("${fhir4java.events.enabled:true}")
    private boolean eventsEnabled;

    @Value("${fhir4java.events.sse.enabled:true}")
    private boolean sseEnabled;

    @Value("${fhir4java.events.webhooks.enabled:true}")
    private boolean webhooksEnabled;

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
}
