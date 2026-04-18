package org.fhirframework.api.event;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.persistence.entity.EventDeliveryLogEntity;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Production implementation of EventPublisher with database-backed persistence.
 * <p>
 * This publisher is enabled when {@code fhir4java.events.persistence.enabled=true}.
 * </p>
 * <p>
 * Features:
 * <ul>
 *   <li>Persistent subscription storage</li>
 *   <li>Event delivery logging with full audit trail</li>
 *   <li>Database-backed retry mechanism with exponential backoff</li>
 *   <li>Acknowledgement tracking</li>
 * </ul>
 * </p>
 * <p>
 * When an event is published:
 * <ol>
 *   <li>Find all matching active subscriptions from the database</li>
 *   <li>Create delivery log entries for each subscription</li>
 *   <li>Attempt immediate delivery for webhooks</li>
 *   <li>Record delivery results (success/failure)</li>
 *   <li>Schedule retries for failed deliveries</li>
 * </ol>
 * </p>
 *
 * @see InMemoryEventPublisher
 * @see EventDeliveryService
 * @see EventRetryScheduler
 */
@Component
@ConditionalOnProperty(name = "fhir4java.events.persistence.enabled", havingValue = "true")
public class PersistentEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PersistentEventPublisher.class);

    private final List<Consumer<ResourceChangeEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final EventSubscriptionRepository subscriptionRepository;
    private final EventDeliveryService deliveryService;
    private final EventsConfig eventsConfig;
    private final ExecutorService executorService;

    /**
     * Optional SSE stream controller for broadcasting events to SSE clients.
     */
    private EventStreamController eventStreamController;

    public PersistentEventPublisher(
            EventSubscriptionRepository subscriptionRepository,
            EventDeliveryService deliveryService,
            EventsConfig eventsConfig) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryService = deliveryService;
        this.eventsConfig = eventsConfig;
        this.executorService = Executors.newFixedThreadPool(10);
        log.info("PersistentEventPublisher initialized (production mode)");
    }

    @Autowired(required = false)
    public void setEventStreamController(EventStreamController eventStreamController) {
        this.eventStreamController = eventStreamController;
        if (eventStreamController != null) {
            log.info("SSE event streaming enabled in PersistentEventPublisher");
        }
    }

    @Override
    public void publish(ResourceChangeEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null event, ignoring");
            return;
        }

        if (!eventsConfig.isEventsEnabled()) {
            log.trace("Events system is disabled, ignoring event");
            return;
        }

        log.debug("Publishing event: {} {} {} (tenant: {})",
                event.action(), event.resourceType(), event.resourceId(), event.tenantId());

        // Notify in-memory subscribers (for backward compatibility and local consumers)
        notifyInMemorySubscribers(event);

        // Publish to SSE stream if available
        publishToSseStream(event);

        // Find matching subscriptions from database and attempt delivery
        publishToPersistentSubscriptions(event);

        log.trace("Event published");
    }

    /**
     * Notify in-memory subscribers (legacy support).
     */
    private void notifyInMemorySubscribers(ResourceChangeEvent event) {
        for (Consumer<ResourceChangeEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.error("Error notifying in-memory subscriber: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Publish to SSE stream if available.
     */
    private void publishToSseStream(ResourceChangeEvent event) {
        if (eventStreamController != null && eventsConfig.isSseEnabled()) {
            try {
                org.fhirframework.api.event.ResourceChangeEvent apiEvent =
                        org.fhirframework.api.event.ResourceChangeEvent.from(event);
                eventStreamController.publishToStream(apiEvent);
            } catch (Exception e) {
                log.error("Error publishing event to SSE stream: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Find matching persistent subscriptions and deliver events.
     */
    private void publishToPersistentSubscriptions(ResourceChangeEvent event) {
        // Find active webhook subscriptions
        List<EventSubscriptionEntity> webhookSubscriptions =
                subscriptionRepository.findActiveByType(SubscriptionType.WEBHOOK.getValue());

        // Filter by tenant and event matching
        List<EventSubscriptionEntity> matchingSubscriptions = webhookSubscriptions.stream()
                .filter(sub -> matchesTenant(sub, event.tenantId()))
                .filter(sub -> sub.matchesEvent(event.resourceType(), event.action()))
                .toList();

        log.debug("Found {} matching webhook subscriptions for event {} {}",
                matchingSubscriptions.size(), event.resourceType(), event.resourceId());

        // Create delivery logs and attempt delivery
        for (EventSubscriptionEntity subscription : matchingSubscriptions) {
            executorService.submit(() -> {
                try {
                    // Create delivery log entry
                    EventDeliveryLogEntity deliveryLog = deliveryService.createDeliveryLog(event, subscription);

                    // Attempt immediate delivery
                    deliveryService.deliverToWebhook(deliveryLog, subscription);

                } catch (Exception e) {
                    log.error("Failed to process delivery to subscription {}: {}",
                            subscription.getSubscriptionId(), e.getMessage(), e);
                }
            });
        }

        // Also handle FHIR Subscription type
        List<EventSubscriptionEntity> fhirSubscriptions =
                subscriptionRepository.findActiveByType(SubscriptionType.FHIR_SUBSCRIPTION.getValue());

        List<EventSubscriptionEntity> matchingFhirSubscriptions = fhirSubscriptions.stream()
                .filter(sub -> matchesTenant(sub, event.tenantId()))
                .filter(sub -> sub.matchesEvent(event.resourceType(), event.action()))
                .toList();

        for (EventSubscriptionEntity subscription : matchingFhirSubscriptions) {
            executorService.submit(() -> {
                try {
                    EventDeliveryLogEntity deliveryLog = deliveryService.createDeliveryLog(event, subscription);
                    deliveryService.deliverToWebhook(deliveryLog, subscription);
                } catch (Exception e) {
                    log.error("Failed to process FHIR subscription delivery to {}: {}",
                            subscription.getSubscriptionId(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Check if subscription matches tenant.
     */
    private boolean matchesTenant(EventSubscriptionEntity subscription, String eventTenantId) {
        String subTenant = subscription.getTenantId();
        // If subscription has no specific tenant or is 'default', match all
        if (subTenant == null || subTenant.equals("default")) {
            return true;
        }
        // Otherwise must match exactly
        return subTenant.equals(eventTenantId);
    }

    @Override
    public void subscribe(Consumer<ResourceChangeEvent> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        subscribers.add(subscriber);
        log.debug("In-memory subscriber added, total: {}", subscribers.size());
    }

    @Override
    public boolean unsubscribe(Consumer<ResourceChangeEvent> subscriber) {
        if (subscriber == null) {
            return false;
        }
        boolean removed = subscribers.remove(subscriber);
        if (removed) {
            log.debug("In-memory subscriber removed, total: {}", subscribers.size());
        }
        return removed;
    }

    @Override
    public int getSubscriberCount() {
        // Return combined count of in-memory and persistent subscriptions
        long persistentCount = subscriptionRepository.countActiveByType(SubscriptionType.WEBHOOK.getValue())
                + subscriptionRepository.countActiveByType(SubscriptionType.FHIR_SUBSCRIPTION.getValue());
        return subscribers.size() + (int) persistentCount;
    }

    /**
     * Get count of in-memory subscribers only.
     *
     * @return count of in-memory subscribers
     */
    public int getInMemorySubscriberCount() {
        return subscribers.size();
    }

    /**
     * Get count of persistent subscriptions.
     *
     * @return count of persistent subscriptions
     */
    public long getPersistentSubscriptionCount() {
        return subscriptionRepository.countActiveByType(SubscriptionType.WEBHOOK.getValue())
                + subscriptionRepository.countActiveByType(SubscriptionType.FHIR_SUBSCRIPTION.getValue())
                + subscriptionRepository.countActiveByType(SubscriptionType.SSE.getValue());
    }

    /**
     * Shutdown the executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        log.info("PersistentEventPublisher shut down");
    }
}
