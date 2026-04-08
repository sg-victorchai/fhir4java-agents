package org.fhirframework.core.subscription;

import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FHIR Subscription Support.
 * Tests SubscriptionTopic, SubscriptionMatcher, and SubscriptionManager.
 */
@DisplayName("FHIR Subscription Support")
class SubscriptionManagerTest {

    private SubscriptionManager subscriptionManager;
    private SubscriptionMatcher subscriptionMatcher;
    private TestEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        subscriptionMatcher = new SubscriptionMatcher();
        subscriptionManager = new SubscriptionManager(subscriptionMatcher, eventPublisher);
    }

    @Nested
    @DisplayName("SubscriptionTopic")
    class SubscriptionTopicTests {

        @Test
        @DisplayName("should match event by resourceType")
        void shouldMatchByResourceType() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create", "update"),
                    Map.of()
            );

            assertTrue(topic.matches("Patient", "create"));
            assertTrue(topic.matches("Patient", "update"));
            assertFalse(topic.matches("Observation", "create"));
        }

        @Test
        @DisplayName("should match event by action")
        void shouldMatchByAction() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );

            assertTrue(topic.matches("Patient", "create"));
            assertFalse(topic.matches("Patient", "update"));
            assertFalse(topic.matches("Patient", "delete"));
        }

        @Test
        @DisplayName("should match all actions when events list is empty")
        void shouldMatchAllActionsWhenEmpty() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of(),
                    Map.of()
            );

            assertTrue(topic.matches("Patient", "create"));
            assertTrue(topic.matches("Patient", "update"));
            assertTrue(topic.matches("Patient", "delete"));
        }

        @Test
        @DisplayName("should be case-insensitive for action matching")
        void shouldBeCaseInsensitiveForActions() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("CREATE", "Update"),
                    Map.of()
            );

            assertTrue(topic.matches("Patient", "create"));
            assertTrue(topic.matches("Patient", "update"));
            assertTrue(topic.matches("Patient", "CREATE"));
        }

        @Test
        @DisplayName("should store filters correctly")
        void shouldStoreFilters() {
            Map<String, String> filters = Map.of("status", "active", "category", "vital-signs");
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Observation",
                    List.of("create"),
                    filters
            );

            assertEquals(filters, topic.filters());
        }
    }

    @Nested
    @DisplayName("SubscriptionMatcher")
    class SubscriptionMatcherTests {

        @Test
        @DisplayName("should match event to subscription topic")
        void shouldMatchEventToTopic() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create", "update"),
                    Map.of()
            );
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );

            assertTrue(subscriptionMatcher.matches(event, topic));
        }

        @Test
        @DisplayName("should not match event with different resource type")
        void shouldNotMatchDifferentResourceType() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Observation", "456", "create", "tenant-1", Instant.now()
            );

            assertFalse(subscriptionMatcher.matches(event, topic));
        }

        @Test
        @DisplayName("should not match event with non-subscribed action")
        void shouldNotMatchNonSubscribedAction() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "delete", "tenant-1", Instant.now()
            );

            assertFalse(subscriptionMatcher.matches(event, topic));
        }
    }

    @Nested
    @DisplayName("SubscriptionManager Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register subscription successfully")
        void shouldRegisterSubscription() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );

            subscriptionManager.registerSubscription("sub-1", topic);

            assertTrue(subscriptionManager.hasSubscription("sub-1"));
            assertEquals(1, subscriptionManager.getSubscriptionCount());
        }

        @Test
        @DisplayName("should unregister subscription successfully")
        void shouldUnregisterSubscription() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            subscriptionManager.registerSubscription("sub-1", topic);

            subscriptionManager.unregisterSubscription("sub-1");

            assertFalse(subscriptionManager.hasSubscription("sub-1"));
            assertEquals(0, subscriptionManager.getSubscriptionCount());
        }

        @Test
        @DisplayName("should handle unregistering non-existent subscription")
        void shouldHandleUnregisteringNonExistent() {
            assertDoesNotThrow(() -> subscriptionManager.unregisterSubscription("non-existent"));
        }

        @Test
        @DisplayName("should replace existing subscription with same ID")
        void shouldReplaceExistingSubscription() {
            SubscriptionTopic topic1 = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            SubscriptionTopic topic2 = new SubscriptionTopic(
                    "Observation",
                    List.of("update"),
                    Map.of()
            );

            subscriptionManager.registerSubscription("sub-1", topic1);
            subscriptionManager.registerSubscription("sub-1", topic2);

            assertEquals(1, subscriptionManager.getSubscriptionCount());
            // Verify the topic was replaced by checking matching
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Observation", "123", "update", "tenant-1", Instant.now()
            );
            List<String> matches = subscriptionManager.getMatchingSubscriptionIds(event);
            assertTrue(matches.contains("sub-1"));
        }
    }

    @Nested
    @DisplayName("SubscriptionManager Matching")
    class MatchingTests {

        @Test
        @DisplayName("should find matching subscription for event")
        void shouldFindMatchingSubscription() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create", "update"),
                    Map.of()
            );
            subscriptionManager.registerSubscription("sub-1", topic);

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );

            List<String> matches = subscriptionManager.getMatchingSubscriptionIds(event);
            assertEquals(1, matches.size());
            assertTrue(matches.contains("sub-1"));
        }

        @Test
        @DisplayName("should return empty list when no subscriptions match")
        void shouldReturnEmptyWhenNoMatch() {
            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            subscriptionManager.registerSubscription("sub-1", topic);

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Observation", "456", "create", "tenant-1", Instant.now()
            );

            List<String> matches = subscriptionManager.getMatchingSubscriptionIds(event);
            assertTrue(matches.isEmpty());
        }

        @Test
        @DisplayName("should find multiple matching subscriptions")
        void shouldFindMultipleMatches() {
            SubscriptionTopic topic1 = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            SubscriptionTopic topic2 = new SubscriptionTopic(
                    "Patient",
                    List.of("create", "update"),
                    Map.of()
            );
            subscriptionManager.registerSubscription("sub-1", topic1);
            subscriptionManager.registerSubscription("sub-2", topic2);

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );

            List<String> matches = subscriptionManager.getMatchingSubscriptionIds(event);
            assertEquals(2, matches.size());
            assertTrue(matches.contains("sub-1"));
            assertTrue(matches.contains("sub-2"));
        }

        @Test
        @DisplayName("should only match subscriptions with correct action")
        void shouldOnlyMatchCorrectAction() {
            SubscriptionTopic createOnly = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            SubscriptionTopic updateOnly = new SubscriptionTopic(
                    "Patient",
                    List.of("update"),
                    Map.of()
            );
            subscriptionManager.registerSubscription("create-sub", createOnly);
            subscriptionManager.registerSubscription("update-sub", updateOnly);

            ResourceChangeEvent updateEvent = new ResourceChangeEvent(
                    "Patient", "123", "update", "tenant-1", Instant.now()
            );

            List<String> matches = subscriptionManager.getMatchingSubscriptionIds(updateEvent);
            assertEquals(1, matches.size());
            assertTrue(matches.contains("update-sub"));
            assertFalse(matches.contains("create-sub"));
        }
    }

    @Nested
    @DisplayName("Event Publisher Integration")
    class EventPublisherIntegrationTests {

        @Test
        @DisplayName("should subscribe to EventPublisher on construction")
        void shouldSubscribeToEventPublisher() {
            assertEquals(1, eventPublisher.getSubscriberCount());
        }

        @Test
        @DisplayName("should receive events from EventPublisher and match subscriptions")
        void shouldReceiveEventsAndMatch() {
            // Create a tracking list for delivered events
            List<DeliveredEvent> deliveredEvents = new CopyOnWriteArrayList<>();

            // Create a custom subscription manager that tracks deliveries
            SubscriptionManager trackingManager = new SubscriptionManager(subscriptionMatcher, eventPublisher) {
                @Override
                public void deliverToSubscription(String subscriptionId, ResourceChangeEvent event) {
                    deliveredEvents.add(new DeliveredEvent(subscriptionId, event));
                }
            };

            SubscriptionTopic topic = new SubscriptionTopic(
                    "Patient",
                    List.of("create"),
                    Map.of()
            );
            trackingManager.registerSubscription("sub-1", topic);

            // Publish event through EventPublisher
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );
            eventPublisher.publish(event);

            // Verify delivery
            assertEquals(1, deliveredEvents.size());
            assertEquals("sub-1", deliveredEvents.get(0).subscriptionId());
            assertEquals(event, deliveredEvents.get(0).event());
        }

        @Test
        @DisplayName("should deliver event to multiple matching subscriptions")
        void shouldDeliverToMultipleSubscriptions() {
            List<DeliveredEvent> deliveredEvents = new CopyOnWriteArrayList<>();

            SubscriptionManager trackingManager = new SubscriptionManager(subscriptionMatcher, eventPublisher) {
                @Override
                public void deliverToSubscription(String subscriptionId, ResourceChangeEvent event) {
                    deliveredEvents.add(new DeliveredEvent(subscriptionId, event));
                }
            };

            SubscriptionTopic topic1 = new SubscriptionTopic("Patient", List.of("create"), Map.of());
            SubscriptionTopic topic2 = new SubscriptionTopic("Patient", List.of("create", "update"), Map.of());
            trackingManager.registerSubscription("sub-1", topic1);
            trackingManager.registerSubscription("sub-2", topic2);

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );
            eventPublisher.publish(event);

            assertEquals(2, deliveredEvents.size());
        }

        @Test
        @DisplayName("unregistered subscription should not receive events")
        void unregisteredSubscriptionShouldNotReceiveEvents() {
            List<DeliveredEvent> deliveredEvents = new CopyOnWriteArrayList<>();

            SubscriptionManager trackingManager = new SubscriptionManager(subscriptionMatcher, eventPublisher) {
                @Override
                public void deliverToSubscription(String subscriptionId, ResourceChangeEvent event) {
                    deliveredEvents.add(new DeliveredEvent(subscriptionId, event));
                }
            };

            SubscriptionTopic topic = new SubscriptionTopic("Patient", List.of("create"), Map.of());
            trackingManager.registerSubscription("sub-1", topic);
            trackingManager.unregisterSubscription("sub-1");

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );
            eventPublisher.publish(event);

            assertTrue(deliveredEvents.isEmpty());
        }

        @Test
        @DisplayName("should not deliver events that do not match any subscription")
        void shouldNotDeliverNonMatchingEvents() {
            List<DeliveredEvent> deliveredEvents = new CopyOnWriteArrayList<>();

            SubscriptionManager trackingManager = new SubscriptionManager(subscriptionMatcher, eventPublisher) {
                @Override
                public void deliverToSubscription(String subscriptionId, ResourceChangeEvent event) {
                    deliveredEvents.add(new DeliveredEvent(subscriptionId, event));
                }
            };

            SubscriptionTopic topic = new SubscriptionTopic("Patient", List.of("create"), Map.of());
            trackingManager.registerSubscription("sub-1", topic);

            // Publish non-matching event (different resource type)
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Observation", "456", "create", "tenant-1", Instant.now()
            );
            eventPublisher.publish(event);

            assertTrue(deliveredEvents.isEmpty());
        }
    }

    @Nested
    @DisplayName("Delivery")
    class DeliveryTests {

        @Test
        @DisplayName("deliverToSubscription should not throw for valid subscription")
        void deliverToSubscriptionShouldNotThrow() {
            SubscriptionTopic topic = new SubscriptionTopic("Patient", List.of("create"), Map.of());
            subscriptionManager.registerSubscription("sub-1", topic);

            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );

            assertDoesNotThrow(() -> subscriptionManager.deliverToSubscription("sub-1", event));
        }

        @Test
        @DisplayName("deliverToSubscription should handle non-existent subscription gracefully")
        void deliverToNonExistentSubscriptionShouldNotThrow() {
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "tenant-1", Instant.now()
            );

            assertDoesNotThrow(() -> subscriptionManager.deliverToSubscription("non-existent", event));
        }
    }

    /**
     * Record for tracking delivered events in tests.
     */
    record DeliveredEvent(String subscriptionId, ResourceChangeEvent event) {}

    /**
     * Simple in-memory EventPublisher implementation for testing.
     */
    static class TestEventPublisher implements EventPublisher {
        private final List<Consumer<ResourceChangeEvent>> subscribers = new CopyOnWriteArrayList<>();

        @Override
        public void publish(ResourceChangeEvent event) {
            for (Consumer<ResourceChangeEvent> subscriber : subscribers) {
                subscriber.accept(event);
            }
        }

        @Override
        public void subscribe(Consumer<ResourceChangeEvent> subscriber) {
            subscribers.add(subscriber);
        }

        @Override
        public boolean unsubscribe(Consumer<ResourceChangeEvent> subscriber) {
            return subscribers.remove(subscriber);
        }

        @Override
        public int getSubscriberCount() {
            return subscribers.size();
        }
    }
}
