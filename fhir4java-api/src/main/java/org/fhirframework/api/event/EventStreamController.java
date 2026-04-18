package org.fhirframework.api.event;

import org.fhirframework.api.subscription.SubscriptionPersistenceService;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private final EventsConfig eventsConfig;

    /**
     * Optional subscription persistence service for tracking SSE subscriptions.
     */
    private SubscriptionPersistenceService subscriptionPersistenceService;

    /**
     * Optional event delivery service for acknowledgements.
     */
    private EventDeliveryService eventDeliveryService;

    /**
     * Multicast sink for broadcasting events to all subscribers.
     * Uses backpressure buffering to handle slow subscribers.
     */
    private final Sinks.Many<ResourceChangeEvent> eventSink =
            Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Track active SSE subscriptions by subscription ID.
     * Maps subscription ID to the subscription entity.
     */
    private final Map<String, EventSubscriptionEntity> activeSseSubscriptions = new ConcurrentHashMap<>();

    /**
     * Creates a new EventStreamController.
     *
     * @param eventsConfig The events configuration for checking enabled state
     */
    public EventStreamController(EventsConfig eventsConfig) {
        this.eventsConfig = eventsConfig;
        log.info("EventStreamController initialized: SSE enabled={}", eventsConfig.isSseEnabled());
    }

    @Autowired(required = false)
    public void setSubscriptionPersistenceService(SubscriptionPersistenceService service) {
        this.subscriptionPersistenceService = service;
        if (service != null) {
            log.info("SSE subscription persistence enabled");
        }
    }

    @Autowired(required = false)
    public void setEventDeliveryService(EventDeliveryService service) {
        this.eventDeliveryService = service;
        if (service != null) {
            log.info("SSE acknowledgement tracking enabled");
        }
    }

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
            @RequestParam(required = false) List<String> actions,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {

        // Check if SSE streaming is enabled
        if (!eventsConfig.isSseEnabled()) {
            log.warn("SSE streaming is disabled, rejecting subscription request");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SSE streaming is disabled. Enable via fhir4java.events.sse.enabled=true");
        }

        log.debug("New SSE subscription: topics={}, actions={}, tenant={}", topics, actions, tenantId);

        // Create persistent subscription if persistence is enabled
        final String subscriptionId = createPersistentSubscription(topics, actions, tenantId);

        return eventSink.asFlux()
                .filter(event -> filterByTopics(event, topics))
                .filter(event -> filterByActions(event, actions))
                .map(event -> ServerSentEvent.<ResourceChangeEvent>builder()
                        .id(UUID.randomUUID().toString())
                        .event("resource-change")
                        .data(event)
                        .build())
                .doOnSubscribe(subscription -> {
                    log.debug("SSE client subscribed: {}", subscriptionId);
                })
                .doOnCancel(() -> {
                    log.debug("SSE client disconnected: {}", subscriptionId);
                    cleanupSubscription(subscriptionId);
                })
                .doOnTerminate(() -> {
                    log.debug("SSE stream terminated: {}", subscriptionId);
                    cleanupSubscription(subscriptionId);
                });
    }

    /**
     * Get subscription info for an active SSE subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Subscription info or 404 if not found
     */
    @GetMapping("/stream/{subscriptionId}")
    public ResponseEntity<Map<String, Object>> getSubscriptionInfo(
            @PathVariable String subscriptionId) {

        EventSubscriptionEntity subscription = activeSseSubscriptions.get(subscriptionId);
        if (subscription == null && subscriptionPersistenceService != null) {
            subscription = subscriptionPersistenceService.findBySubscriptionId(subscriptionId)
                    .orElse(null);
        }

        if (subscription == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> info = Map.of(
                "subscriptionId", subscription.getSubscriptionId(),
                "type", subscription.getSubscriptionType(),
                "status", subscription.getStatus(),
                "topics", subscription.getTopics() != null ? subscription.getTopics() : "",
                "actions", subscription.getActions() != null ? subscription.getActions() : "",
                "createdAt", subscription.getCreatedAt() != null ? subscription.getCreatedAt().toString() : "",
                "lastEventAt", subscription.getLastEventAt() != null ? subscription.getLastEventAt().toString() : ""
        );

        return ResponseEntity.ok(info);
    }

    /**
     * Acknowledge receipt of an event.
     *
     * @param subscriptionId    The subscription ID
     * @param eventId          The event ID to acknowledge
     * @param acknowledgementId Optional client-provided acknowledgement ID
     * @return 200 OK if acknowledged, 400 if acknowledgement is disabled
     */
    @PostMapping("/stream/{subscriptionId}/ack")
    public ResponseEntity<Map<String, Object>> acknowledgeEvent(
            @PathVariable String subscriptionId,
            @RequestParam String eventId,
            @RequestParam(required = false) String acknowledgementId) {

        if (!eventsConfig.isAcknowledgementEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Acknowledgement tracking is disabled"
            ));
        }

        if (eventDeliveryService == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Event delivery service not available (persistence mode not enabled)"
            ));
        }

        boolean acknowledged = eventDeliveryService.acknowledgeDelivery(
                eventId, subscriptionId, acknowledgementId);

        if (acknowledged) {
            return ResponseEntity.ok(Map.of(
                    "acknowledged", true,
                    "eventId", eventId,
                    "subscriptionId", subscriptionId
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create a persistent subscription if persistence is enabled.
     */
    private String createPersistentSubscription(List<String> topics, List<String> actions, String tenantId) {
        String subscriptionId = UUID.randomUUID().toString();

        if (subscriptionPersistenceService != null && eventsConfig.isPersistenceEnabled()) {
            try {
                String topicsStr = topics != null ? String.join(",", topics) : null;
                String actionsStr = actions != null ? String.join(",", actions) : null;

                EventSubscriptionEntity subscription = subscriptionPersistenceService.createSseSubscription(
                        tenantId, topicsStr, actionsStr);

                subscriptionId = subscription.getSubscriptionId();
                activeSseSubscriptions.put(subscriptionId, subscription);

                log.info("Created persistent SSE subscription: {}", subscriptionId);
            } catch (Exception e) {
                log.warn("Failed to create persistent subscription, using in-memory: {}", e.getMessage());
            }
        }

        return subscriptionId;
    }

    /**
     * Cleanup subscription when client disconnects.
     * <p>
     * This method is called from reactive callbacks (doOnCancel, doOnTerminate)
     * which run on Netty event loop threads. The SubscriptionPersistenceService
     * uses programmatic transaction management to handle this properly.
     * </p>
     */
    private void cleanupSubscription(String subscriptionId) {
        if (subscriptionId == null) {
            return;
        }

        log.info("Cleaning up SSE subscription: {}", subscriptionId);
        activeSseSubscriptions.remove(subscriptionId);

        if (subscriptionPersistenceService != null && eventsConfig.isPersistenceEnabled()) {
            try {
                boolean terminated = subscriptionPersistenceService.terminateSubscription(subscriptionId);
                if (terminated) {
                    log.info("Successfully terminated persistent SSE subscription: {}", subscriptionId);
                } else {
                    log.warn("Subscription {} not found or already terminated", subscriptionId);
                }
            } catch (Exception e) {
                log.error("Failed to terminate persistent subscription {}: {}", subscriptionId, e.getMessage(), e);
            }
        }
    }

    /**
     * Get count of active SSE subscriptions.
     *
     * @return Count of active subscriptions
     */
    public int getActiveSubscriptionCount() {
        return activeSseSubscriptions.size();
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
