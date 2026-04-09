package org.fhirframework.api.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Server-Sent Events (SSE) streaming of resource change events.
 * <p>
 * This controller provides a real-time event stream that clients can subscribe to
 * for receiving notifications about FHIR resource changes (create, update, delete).
 * </p>
 * <p>
 * The stream supports optional filtering by:
 * <ul>
 *   <li><b>topics</b> - Filter by resource types (e.g., Patient, Observation)</li>
 *   <li><b>actions</b> - Filter by action types (create, update, delete)</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>
 * # Subscribe to all events
 * GET /api/events/stream
 *
 * # Subscribe to Patient events only
 * GET /api/events/stream?topics=Patient
 *
 * # Subscribe to Patient and Observation events
 * GET /api/events/stream?topics=Patient,Observation
 *
 * # Subscribe to create and update events only
 * GET /api/events/stream?actions=create,update
 *
 * # Subscribe to Patient create events only
 * GET /api/events/stream?topics=Patient&amp;actions=create
 * </pre>
 *
 * @see ResourceChangeEvent
 */
@RestController
@RequestMapping("/api/events")
public class EventStreamController {

    private static final Logger log = LoggerFactory.getLogger(EventStreamController.class);

    /**
     * Multicast sink for broadcasting events to all subscribers.
     * Uses backpressure buffering to handle slow subscribers.
     */
    private final Sinks.Many<ResourceChangeEvent> eventSink =
            Sinks.many().multicast().onBackpressureBuffer();

    /**
     * SSE stream endpoint for subscribing to resource change events.
     * <p>
     * Returns a Flux that emits ServerSentEvents containing ResourceChangeEvent data.
     * Each event includes:
     * <ul>
     *   <li><b>id</b> - Unique identifier (UUID) for the event</li>
     *   <li><b>event</b> - Event type, always "resource-change"</li>
     *   <li><b>data</b> - The ResourceChangeEvent payload</li>
     * </ul>
     * </p>
     *
     * @param topics  Optional list of resource types to filter by (e.g., Patient, Observation).
     *                If null or empty, all resource types are included.
     * @param actions Optional list of actions to filter by (create, update, delete).
     *                If null or empty, all actions are included.
     * @return Flux of ServerSentEvents containing filtered ResourceChangeEvents
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ResourceChangeEvent>> stream(
            @RequestParam(required = false) List<String> topics,
            @RequestParam(required = false) List<String> actions) {

        log.debug("New SSE subscription: topics={}, actions={}", topics, actions);

        return eventSink.asFlux()
                .filter(event -> filterByTopics(event, topics))
                .filter(event -> filterByActions(event, actions))
                .map(event -> ServerSentEvent.<ResourceChangeEvent>builder()
                        .id(UUID.randomUUID().toString())
                        .event("resource-change")
                        .data(event)
                        .build())
                .doOnSubscribe(subscription -> log.debug("SSE client subscribed"))
                .doOnCancel(() -> log.debug("SSE client disconnected"));
    }

    /**
     * Publish an event to the SSE stream.
     * <p>
     * This method is called by the EventPublisher or other components to broadcast
     * events to all connected SSE clients.
     * </p>
     *
     * @param event The event to publish. Null events are ignored.
     */
    public void publishToStream(ResourceChangeEvent event) {
        if (event == null) {
            log.debug("Ignoring null event");
            return;
        }

        log.debug("Publishing event to SSE stream: {} {} {}",
                event.action(), event.resourceType(), event.resourceId());

        Sinks.EmitResult result = eventSink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit event to SSE stream: {} - result: {}",
                    event.resourceId(), result);
        }
    }

    /**
     * Filter events by resource type (topics).
     *
     * @param event  The event to check
     * @param topics List of resource types to accept, or null/empty to accept all
     * @return true if the event should be included, false to filter it out
     */
    private boolean filterByTopics(ResourceChangeEvent event, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return true;
        }
        return topics.contains(event.resourceType());
    }

    /**
     * Filter events by action type.
     *
     * @param event   The event to check
     * @param actions List of actions to accept, or null/empty to accept all
     * @return true if the event should be included, false to filter it out
     */
    private boolean filterByActions(ResourceChangeEvent event, List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return true;
        }
        return actions.contains(event.action());
    }
}
