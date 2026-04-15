package org.fhirframework.mcp.agent;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * SSE Event Consumer for AI Agents.
 * <p>
 * Demonstrates how AI agents can subscribe to and consume Server-Sent Events
 * from the FHIR server for real-time awareness of resource changes.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * AgentEventConsumer consumer = new AgentEventConsumer(webClient);
 *
 * // Subscribe to Patient and Observation events
 * consumer.subscribeToEvents(
 *     List.of("Patient", "Observation"),
 *     event -> {
 *         log.info("Received: {} {} on {}",
 *             event.action(), event.resourceType(), event.resourceId());
 *         // Trigger agent workflow based on event
 *     }
 * );
 *
 * // Later, stop monitoring
 * consumer.stopAllMonitoring();
 * }</pre>
 *
 * <h3>Use Cases:</h3>
 * <ul>
 *   <li>Care coordination agents monitoring patient admissions</li>
 *   <li>Clinical decision support reacting to new lab results</li>
 *   <li>Audit agents tracking all resource modifications</li>
 *   <li>Real-time dashboard updates</li>
 * </ul>
 */
public class AgentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentEventConsumer.class);

    private final WebClient webClient;
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * Creates a new AgentEventConsumer with the specified WebClient.
     *
     * @param webClient the WebClient configured with the FHIR server base URL
     */
    public AgentEventConsumer(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Creates a new AgentEventConsumer for the specified server URL.
     *
     * @param serverBaseUrl the base URL of the FHIR server (e.g., "http://localhost:8080")
     */
    public AgentEventConsumer(String serverBaseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(serverBaseUrl)
                .build();
    }

    /**
     * Subscribes to SSE events filtered by resource types.
     * <p>
     * AI agents use this to receive real-time notifications about resource changes.
     * The subscription remains active until explicitly cancelled.
     * </p>
     *
     * @param topics list of resource types to monitor (e.g., ["Patient", "Observation"])
     * @return a Flux of ResourceChangeEvent that can be subscribed to
     */
    public Flux<ResourceChangeEvent> subscribeToEvents(List<String> topics) {
        String topicsParam = topics != null && !topics.isEmpty()
                ? String.join(",", topics)
                : "";

        log.info("Subscribing to SSE events for topics: {}", topicsParam.isEmpty() ? "ALL" : topicsParam);

        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/events/stream");
                    if (!topicsParam.isEmpty()) {
                        uriBuilder.queryParam("topics", topicsParam);
                    }
                    return uriBuilder.build();
                })
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ResourceChangeEvent.class)
                .doOnSubscribe(s -> log.debug("SSE subscription established"))
                .doOnNext(event -> log.debug("Received event: {} {} {}",
                        event.resourceType(), event.resourceId(), event.action()))
                .doOnError(e -> log.error("SSE subscription error", e))
                .doOnComplete(() -> log.info("SSE stream completed"));
    }

    /**
     * Subscribes to SSE events with a callback handler.
     * <p>
     * This is a convenience method that handles subscription management automatically.
     * </p>
     *
     * @param topics        list of resource types to monitor
     * @param eventHandler  callback to handle each received event
     * @param subscriptionId unique identifier for this subscription (for later cancellation)
     */
    public void subscribeToEvents(List<String> topics, Consumer<ResourceChangeEvent> eventHandler,
                                  String subscriptionId) {
        // Cancel any existing subscription with the same ID
        stopMonitoring(subscriptionId);

        Disposable subscription = subscribeToEvents(topics)
                .subscribe(
                        eventHandler,
                        error -> log.error("Event subscription {} failed", subscriptionId, error),
                        () -> log.info("Event subscription {} completed", subscriptionId)
                );

        activeSubscriptions.put(subscriptionId, subscription);
        log.info("Started event monitoring with subscription ID: {}", subscriptionId);
    }

    /**
     * Subscribes to events filtered by both resource types and actions.
     *
     * @param topics  list of resource types to monitor
     * @param actions list of actions to monitor (e.g., ["create", "update"])
     * @return a Flux of ResourceChangeEvent
     */
    public Flux<ResourceChangeEvent> subscribeToEvents(List<String> topics, List<String> actions) {
        String topicsParam = topics != null && !topics.isEmpty()
                ? String.join(",", topics)
                : "";
        String actionsParam = actions != null && !actions.isEmpty()
                ? String.join(",", actions)
                : "";

        log.info("Subscribing to SSE events for topics: {}, actions: {}",
                topicsParam.isEmpty() ? "ALL" : topicsParam,
                actionsParam.isEmpty() ? "ALL" : actionsParam);

        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/events/stream");
                    if (!topicsParam.isEmpty()) {
                        uriBuilder.queryParam("topics", topicsParam);
                    }
                    if (!actionsParam.isEmpty()) {
                        uriBuilder.queryParam("actions", actionsParam);
                    }
                    return uriBuilder.build();
                })
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ResourceChangeEvent.class);
    }

    /**
     * Starts monitoring for Patient-related events.
     * <p>
     * Example use case: Care coordination agent that needs to track
     * patient admissions, discharges, and demographic updates.
     * </p>
     *
     * @param eventHandler callback to handle patient events
     */
    public void startPatientMonitoring(Consumer<ResourceChangeEvent> eventHandler) {
        subscribeToEvents(
                List.of("Patient", "Encounter", "Observation"),
                eventHandler,
                "patient-monitoring"
        );
    }

    /**
     * Starts monitoring for clinical events (lab results, orders, notes).
     * <p>
     * Example use case: Clinical decision support agent that reacts to
     * new lab results and generates alerts for abnormal values.
     * </p>
     *
     * @param eventHandler callback to handle clinical events
     */
    public void startClinicalMonitoring(Consumer<ResourceChangeEvent> eventHandler) {
        subscribeToEvents(
                List.of("Observation", "DiagnosticReport", "ServiceRequest", "MedicationRequest"),
                eventHandler,
                "clinical-monitoring"
        );
    }

    /**
     * Starts monitoring all resource changes (for audit purposes).
     * <p>
     * Example use case: Audit agent that logs all modifications for compliance.
     * </p>
     *
     * @param eventHandler callback to handle all events
     */
    public void startAuditMonitoring(Consumer<ResourceChangeEvent> eventHandler) {
        subscribeToEvents(
                List.of(), // Empty list = all resources
                eventHandler,
                "audit-monitoring"
        );
    }

    /**
     * Stops monitoring for a specific subscription.
     *
     * @param subscriptionId the subscription ID to cancel
     */
    public void stopMonitoring(String subscriptionId) {
        Disposable subscription = activeSubscriptions.remove(subscriptionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Stopped monitoring for subscription: {}", subscriptionId);
        }
    }

    /**
     * Stops all active monitoring subscriptions.
     */
    public void stopAllMonitoring() {
        activeSubscriptions.forEach((id, subscription) -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        activeSubscriptions.clear();
        log.info("Stopped all event monitoring");
    }

    /**
     * Returns the number of active subscriptions.
     *
     * @return count of active subscriptions
     */
    public int getActiveSubscriptionCount() {
        return (int) activeSubscriptions.values().stream()
                .filter(d -> !d.isDisposed())
                .count();
    }

    /**
     * Checks if a specific subscription is active.
     *
     * @param subscriptionId the subscription ID to check
     * @return true if the subscription is active
     */
    public boolean isMonitoring(String subscriptionId) {
        Disposable subscription = activeSubscriptions.get(subscriptionId);
        return subscription != null && !subscription.isDisposed();
    }
}
