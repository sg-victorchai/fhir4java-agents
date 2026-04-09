package org.fhirframework.api.event;

import java.util.function.Consumer;

/**
 * Interface for publishing and subscribing to resource change events.
 * <p>
 * This extends the core {@link org.fhirframework.core.event.EventPublisher}
 * and adds API-layer specific convenience methods.
 * </p>
 * <p>
 * Implementations can use various messaging mechanisms such as:
 * <ul>
 *   <li>In-memory pub/sub (for testing and single-instance deployments)</li>
 *   <li>Message queues (RabbitMQ, Kafka, etc.)</li>
 *   <li>Event streaming platforms</li>
 * </ul>
 * </p>
 *
 * @see org.fhirframework.core.event.EventPublisher
 */
public interface EventPublisher extends org.fhirframework.core.event.EventPublisher {

    /**
     * Publish an API-layer resource change event to all subscribers.
     * <p>
     * This converts the event to the core type and publishes it.
     * </p>
     *
     * @param event The event to publish
     */
    default void publish(ResourceChangeEvent event) {
        if (event != null) {
            publish(event.toCore());
        }
    }

    /**
     * Subscribe to receive resource change events as API-layer types.
     *
     * @param subscriber Consumer that will receive published events
     */
    default void subscribeApi(Consumer<ResourceChangeEvent> subscriber) {
        subscribe(coreEvent -> subscriber.accept(ResourceChangeEvent.from(coreEvent)));
    }
}
