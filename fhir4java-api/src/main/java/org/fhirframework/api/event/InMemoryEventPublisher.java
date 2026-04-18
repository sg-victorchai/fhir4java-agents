package org.fhirframework.api.event;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory implementation of EventPublisher (development mode).
 * <p>
 * This lightweight publisher is enabled when {@code fhir4java.events.persistence.enabled=false}
 * (the default), making it ideal for development and testing.
 * </p>
 * <p>
 * Uses a thread-safe CopyOnWriteArrayList for subscribers to allow
 * concurrent publishing and subscription modifications.
 * </p>
 * <p>
 * When an {@link EventStreamController} is available, events are also
 * published to the SSE stream for real-time client notifications.
 * </p>
 * <p>
 * For production deployments requiring persistence, retry, and audit trail,
 * enable {@link PersistentEventPublisher} by setting
 * {@code fhir4java.events.persistence.enabled=true}.
 * </p>
 *
 * @see PersistentEventPublisher
 */
@Component
@ConditionalOnProperty(name = "fhir4java.events.persistence.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    private final List<Consumer<ResourceChangeEvent>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Optional SSE stream controller for broadcasting events to SSE clients.
     * May be null if SSE is not configured.
     */
    private EventStreamController eventStreamController;

    /**
     * Default constructor for testing without SSE support.
     */
    public InMemoryEventPublisher() {
    }

    /**
     * Constructor with SSE stream controller injection.
     *
     * @param eventStreamController The SSE controller to broadcast events to
     */
    @Autowired(required = false)
    public void setEventStreamController(EventStreamController eventStreamController) {
        this.eventStreamController = eventStreamController;
        if (eventStreamController != null) {
            log.info("SSE event streaming enabled");
        }
    }

    @Override
    public void publish(ResourceChangeEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null event, ignoring");
            return;
        }

        log.debug("Publishing event: {} {} {} (tenant: {})",
                event.action(), event.resourceType(), event.resourceId(), event.tenantId());

        // Publish to traditional subscribers
        for (Consumer<ResourceChangeEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.error("Error notifying subscriber of event: {} {} {}",
                        event.action(), event.resourceType(), event.resourceId(), e);
            }
        }

        // Also publish to SSE stream if available
        if (eventStreamController != null) {
            try {
                // Convert core event to API event for SSE stream
                org.fhirframework.api.event.ResourceChangeEvent apiEvent =
                        org.fhirframework.api.event.ResourceChangeEvent.from(event);
                eventStreamController.publishToStream(apiEvent);
            } catch (Exception e) {
                log.error("Error publishing event to SSE stream: {} {} {}",
                        event.action(), event.resourceType(), event.resourceId(), e);
            }
        }

        log.trace("Event published to {} subscribers", subscribers.size());
    }

    @Override
    public void subscribe(Consumer<ResourceChangeEvent> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        subscribers.add(subscriber);
        log.debug("Subscriber added, total subscribers: {}", subscribers.size());
    }

    @Override
    public boolean unsubscribe(Consumer<ResourceChangeEvent> subscriber) {
        if (subscriber == null) {
            return false;
        }
        boolean removed = subscribers.remove(subscriber);
        if (removed) {
            log.debug("Subscriber removed, total subscribers: {}", subscribers.size());
        }
        return removed;
    }

    @Override
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Clear all subscribers. Primarily for testing purposes.
     */
    public void clearSubscribers() {
        subscribers.clear();
        log.debug("All subscribers cleared");
    }
}
