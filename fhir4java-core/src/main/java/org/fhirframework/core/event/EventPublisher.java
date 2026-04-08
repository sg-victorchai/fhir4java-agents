package org.fhirframework.core.event;

import java.util.function.Consumer;

/**
 * Interface for publishing and subscribing to resource change events.
 * <p>
 * Implementations can use various messaging mechanisms such as:
 * <ul>
 *   <li>In-memory pub/sub (for testing and single-instance deployments)</li>
 *   <li>Message queues (RabbitMQ, Kafka, etc.)</li>
 *   <li>Event streaming platforms</li>
 * </ul>
 * </p>
 */
public interface EventPublisher {

    /**
     * Publish a resource change event to all subscribers.
     *
     * @param event The event to publish
     */
    void publish(ResourceChangeEvent event);

    /**
     * Subscribe to receive resource change events.
     *
     * @param subscriber Consumer that will receive published events
     */
    void subscribe(Consumer<ResourceChangeEvent> subscriber);

    /**
     * Unsubscribe from receiving resource change events.
     *
     * @param subscriber Consumer to unsubscribe
     * @return true if the subscriber was removed, false if it was not subscribed
     */
    boolean unsubscribe(Consumer<ResourceChangeEvent> subscriber);

    /**
     * Get the number of current subscribers.
     *
     * @return The subscriber count
     */
    int getSubscriberCount();
}
