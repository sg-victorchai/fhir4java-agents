package org.fhirframework.core.subscription;

import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages FHIR subscriptions and handles event delivery.
 * <p>
 * The SubscriptionManager is responsible for:
 * <ul>
 *   <li>Registering and unregistering subscriptions</li>
 *   <li>Listening to resource change events from the EventPublisher</li>
 *   <li>Matching events against registered subscriptions</li>
 *   <li>Delivering events to matching subscriptions (rest-hook channel)</li>
 * </ul>
 * </p>
 * <p>
 * In production mode (when persistence is enabled), subscriptions can be
 * persisted to the database for durability across server restarts.
 * </p>
 * <p>
 * This implementation is thread-safe and suitable for concurrent access.
 * </p>
 */
@Service
public class SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private final Map<String, SubscriptionTopic> activeSubscriptions = new ConcurrentHashMap<>();
    private final SubscriptionMatcher subscriptionMatcher;
    private final EventPublisher eventPublisher;
    private final Consumer<ResourceChangeEvent> eventHandler;

    /**
     * Flag indicating whether persistence mode is enabled.
     */
    @Value("${fhir4java.events.persistence.enabled:false}")
    private boolean persistenceEnabled;

    /**
     * Creates a new SubscriptionManager and subscribes to the EventPublisher.
     *
     * @param subscriptionMatcher The matcher used to match events against topics
     * @param eventPublisher      The event publisher to subscribe to for resource changes
     */
    public SubscriptionManager(SubscriptionMatcher subscriptionMatcher, EventPublisher eventPublisher) {
        this.subscriptionMatcher = subscriptionMatcher;
        this.eventPublisher = eventPublisher;

        // Create and store the event handler reference for potential unsubscription
        this.eventHandler = this::handleEvent;

        // Subscribe to resource change events
        eventPublisher.subscribe(eventHandler);
        logger.info("SubscriptionManager initialized and subscribed to EventPublisher");
    }

    /**
     * Registers a subscription with the given ID and topic.
     * If a subscription with the same ID already exists, it will be replaced.
     *
     * @param subscriptionId The unique identifier for the subscription
     * @param topic          The subscription topic defining what events to monitor
     */
    public void registerSubscription(String subscriptionId, SubscriptionTopic topic) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be null or blank");
        }
        if (topic == null) {
            throw new IllegalArgumentException("topic must not be null");
        }

        activeSubscriptions.put(subscriptionId, topic);
        logger.info("Registered subscription: {} for resourceType: {} with events: {}",
                subscriptionId, topic.resourceType(), topic.events());
    }

    /**
     * Unregisters a subscription by its ID.
     * If the subscription does not exist, this method does nothing.
     *
     * @param subscriptionId The unique identifier of the subscription to remove
     */
    public void unregisterSubscription(String subscriptionId) {
        SubscriptionTopic removed = activeSubscriptions.remove(subscriptionId);
        if (removed != null) {
            logger.info("Unregistered subscription: {}", subscriptionId);
        } else {
            logger.debug("Attempted to unregister non-existent subscription: {}", subscriptionId);
        }
    }

    /**
     * Checks if a subscription with the given ID is registered.
     *
     * @param subscriptionId The subscription ID to check
     * @return true if the subscription exists
     */
    public boolean hasSubscription(String subscriptionId) {
        return activeSubscriptions.containsKey(subscriptionId);
    }

    /**
     * Gets the number of currently registered subscriptions.
     *
     * @return The count of active subscriptions
     */
    public int getSubscriptionCount() {
        return activeSubscriptions.size();
    }

    /**
     * Finds all subscription IDs that match the given event.
     *
     * @param event The resource change event to match
     * @return List of subscription IDs that match the event
     */
    public List<String> getMatchingSubscriptionIds(ResourceChangeEvent event) {
        List<String> matches = new ArrayList<>();

        for (Map.Entry<String, SubscriptionTopic> entry : activeSubscriptions.entrySet()) {
            if (subscriptionMatcher.matches(event, entry.getValue())) {
                matches.add(entry.getKey());
            }
        }

        return matches;
    }

    /**
     * Delivers an event to a specific subscription.
     * <p>
     * This method can be overridden in subclasses to implement different
     * delivery mechanisms (e.g., rest-hook, email, websocket).
     * </p>
     * <p>
     * The default implementation logs the delivery. In a production system,
     * this would make an HTTP POST to the subscription's endpoint.
     * </p>
     *
     * @param subscriptionId The subscription to deliver to
     * @param event          The event to deliver
     */
    public void deliverToSubscription(String subscriptionId, ResourceChangeEvent event) {
        SubscriptionTopic topic = activeSubscriptions.get(subscriptionId);
        if (topic == null) {
            logger.warn("Attempted to deliver to non-existent subscription: {}", subscriptionId);
            return;
        }

        // In a real implementation, this would:
        // 1. Look up the subscription's endpoint URL
        // 2. Create the notification payload (FHIR Bundle with the changed resource)
        // 3. POST to the endpoint with appropriate headers
        // 4. Handle retries and error tracking

        logger.debug("Delivering event to subscription {}: resourceType={}, resourceId={}, action={}",
                subscriptionId, event.resourceType(), event.resourceId(), event.action());
    }

    /**
     * Handles incoming resource change events from the EventPublisher.
     * Finds all matching subscriptions and delivers the event to each.
     *
     * @param event The resource change event
     */
    private void handleEvent(ResourceChangeEvent event) {
        logger.debug("Received event: resourceType={}, resourceId={}, action={}",
                event.resourceType(), event.resourceId(), event.action());

        List<String> matchingSubscriptions = getMatchingSubscriptionIds(event);

        if (matchingSubscriptions.isEmpty()) {
            logger.trace("No subscriptions match event for {} {}", event.resourceType(), event.resourceId());
            return;
        }

        logger.debug("Found {} matching subscription(s) for event", matchingSubscriptions.size());

        for (String subscriptionId : matchingSubscriptions) {
            try {
                deliverToSubscription(subscriptionId, event);
            } catch (Exception e) {
                logger.error("Failed to deliver event to subscription {}: {}",
                        subscriptionId, e.getMessage(), e);
            }
        }
    }

    /**
     * Check if persistence mode is enabled.
     * <p>
     * When persistence is enabled, subscriptions are stored in the database
     * and managed by the SubscriptionPersistenceService in the API layer.
     * </p>
     *
     * @return true if persistence mode is enabled
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    /**
     * Get all active subscription IDs.
     *
     * @return List of active subscription IDs
     */
    public List<String> getAllSubscriptionIds() {
        return new ArrayList<>(activeSubscriptions.keySet());
    }

    /**
     * Get subscription topic by ID.
     *
     * @param subscriptionId The subscription ID
     * @return The subscription topic or null if not found
     */
    public SubscriptionTopic getSubscriptionTopic(String subscriptionId) {
        return activeSubscriptions.get(subscriptionId);
    }
}
