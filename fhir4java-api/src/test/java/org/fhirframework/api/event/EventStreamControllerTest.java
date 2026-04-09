package org.fhirframework.api.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EventStreamController} SSE event streaming.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventStreamController")
class EventStreamControllerTest {

    private EventStreamController controller;

    @BeforeEach
    void setUp() {
        controller = new EventStreamController();
    }

    @Nested
    @DisplayName("stream endpoint")
    class StreamEndpoint {

        @Test
        @DisplayName("should return text/event-stream content type compatible flux")
        void shouldReturnEventStreamContentType() {
            // The stream endpoint produces text/event-stream as declared by the annotation
            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);
            assertNotNull(flux, "Stream flux should not be null");
        }

        @Test
        @DisplayName("should receive published events")
        void shouldReceivePublishedEvents() {
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(1))
                    .then(() -> controller.publishToStream(event))
                    .assertNext(sse -> {
                        assertNotNull(sse.id(), "SSE should have an ID");
                        assertEquals("resource-change", sse.event(), "SSE event type should be 'resource-change'");
                        assertNotNull(sse.data(), "SSE should have data");
                        assertEquals("Patient", sse.data().resourceType());
                        assertEquals("123", sse.data().resourceId());
                        assertEquals("create", sse.data().action());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should include unique id in each SSE event")
        void shouldIncludeUniqueIdInEvents() {
            ResourceChangeEvent event1 = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent event2 = new ResourceChangeEvent(
                    "Observation", "456", "update", "default", Instant.now());

            List<String> ids = new ArrayList<>();
            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(2))
                    .then(() -> {
                        controller.publishToStream(event1);
                        controller.publishToStream(event2);
                    })
                    .assertNext(sse -> {
                        assertNotNull(sse.id());
                        ids.add(sse.id());
                    })
                    .assertNext(sse -> {
                        assertNotNull(sse.id());
                        ids.add(sse.id());
                        assertNotEquals(ids.get(0), ids.get(1), "SSE IDs should be unique");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("filtering by topics (resourceType)")
    class FilteringByTopics {

        @Test
        @DisplayName("should filter events by single topic")
        void shouldFilterBySingleTopic() {
            ResourceChangeEvent patientEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent observationEvent = new ResourceChangeEvent(
                    "Observation", "456", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(
                    List.of("Patient"), null);

            StepVerifier.create(flux.take(1).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(observationEvent);  // Should be filtered out
                        controller.publishToStream(patientEvent);       // Should be received
                    })
                    .assertNext(sse -> {
                        assertEquals("Patient", sse.data().resourceType());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should filter events by multiple topics")
        void shouldFilterByMultipleTopics() {
            ResourceChangeEvent patientEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent observationEvent = new ResourceChangeEvent(
                    "Observation", "456", "create", "default", Instant.now());
            ResourceChangeEvent encounterEvent = new ResourceChangeEvent(
                    "Encounter", "789", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(
                    List.of("Patient", "Observation"), null);

            StepVerifier.create(flux.take(2).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(encounterEvent);     // Should be filtered out
                        controller.publishToStream(patientEvent);       // Should be received
                        controller.publishToStream(observationEvent);   // Should be received
                    })
                    .assertNext(sse -> assertEquals("Patient", sse.data().resourceType()))
                    .assertNext(sse -> assertEquals("Observation", sse.data().resourceType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should receive all events when topics is null")
        void shouldReceiveAllWhenTopicsNull() {
            ResourceChangeEvent patientEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent observationEvent = new ResourceChangeEvent(
                    "Observation", "456", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(2).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(patientEvent);
                        controller.publishToStream(observationEvent);
                    })
                    .assertNext(sse -> assertEquals("Patient", sse.data().resourceType()))
                    .assertNext(sse -> assertEquals("Observation", sse.data().resourceType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should receive all events when topics is empty")
        void shouldReceiveAllWhenTopicsEmpty() {
            ResourceChangeEvent patientEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent observationEvent = new ResourceChangeEvent(
                    "Observation", "456", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(List.of(), null);

            StepVerifier.create(flux.take(2).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(patientEvent);
                        controller.publishToStream(observationEvent);
                    })
                    .assertNext(sse -> assertEquals("Patient", sse.data().resourceType()))
                    .assertNext(sse -> assertEquals("Observation", sse.data().resourceType()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("filtering by actions")
    class FilteringByActions {

        @Test
        @DisplayName("should filter events by single action")
        void shouldFilterBySingleAction() {
            ResourceChangeEvent createEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent updateEvent = new ResourceChangeEvent(
                    "Patient", "456", "update", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(
                    null, List.of("create"));

            StepVerifier.create(flux.take(1).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(updateEvent);  // Should be filtered out
                        controller.publishToStream(createEvent);  // Should be received
                    })
                    .assertNext(sse -> {
                        assertEquals("create", sse.data().action());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should filter events by multiple actions")
        void shouldFilterByMultipleActions() {
            ResourceChangeEvent createEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent updateEvent = new ResourceChangeEvent(
                    "Patient", "456", "update", "default", Instant.now());
            ResourceChangeEvent deleteEvent = new ResourceChangeEvent(
                    "Patient", "789", "delete", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(
                    null, List.of("create", "update"));

            StepVerifier.create(flux.take(2).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(deleteEvent);  // Should be filtered out
                        controller.publishToStream(createEvent);  // Should be received
                        controller.publishToStream(updateEvent);  // Should be received
                    })
                    .assertNext(sse -> assertEquals("create", sse.data().action()))
                    .assertNext(sse -> assertEquals("update", sse.data().action()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should receive all events when actions is null")
        void shouldReceiveAllWhenActionsNull() {
            ResourceChangeEvent createEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent deleteEvent = new ResourceChangeEvent(
                    "Patient", "456", "delete", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(2).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(createEvent);
                        controller.publishToStream(deleteEvent);
                    })
                    .assertNext(sse -> assertEquals("create", sse.data().action()))
                    .assertNext(sse -> assertEquals("delete", sse.data().action()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("combined filtering")
    class CombinedFiltering {

        @Test
        @DisplayName("should filter by both topics and actions")
        void shouldFilterByBothTopicsAndActions() {
            ResourceChangeEvent patientCreate = new ResourceChangeEvent(
                    "Patient", "1", "create", "default", Instant.now());
            ResourceChangeEvent patientUpdate = new ResourceChangeEvent(
                    "Patient", "2", "update", "default", Instant.now());
            ResourceChangeEvent observationCreate = new ResourceChangeEvent(
                    "Observation", "3", "create", "default", Instant.now());

            // Only want Patient creates
            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(
                    List.of("Patient"), List.of("create"));

            StepVerifier.create(flux.take(1).timeout(Duration.ofSeconds(2)))
                    .then(() -> {
                        controller.publishToStream(patientUpdate);       // Filtered: wrong action
                        controller.publishToStream(observationCreate);   // Filtered: wrong topic
                        controller.publishToStream(patientCreate);       // Should be received
                    })
                    .assertNext(sse -> {
                        assertEquals("Patient", sse.data().resourceType());
                        assertEquals("create", sse.data().action());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("multiple subscribers")
    class MultipleSubscribers {

        @Test
        @DisplayName("should deliver events to multiple concurrent subscribers")
        void shouldDeliverToMultipleSubscribers() throws InterruptedException {
            AtomicInteger subscriber1Count = new AtomicInteger(0);
            AtomicInteger subscriber2Count = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(2);

            Flux<ServerSentEvent<ResourceChangeEvent>> flux1 = controller.stream(null, null);
            Flux<ServerSentEvent<ResourceChangeEvent>> flux2 = controller.stream(null, null);

            // Subscribe both
            flux1.take(1).subscribe(sse -> {
                subscriber1Count.incrementAndGet();
                latch.countDown();
            });

            flux2.take(1).subscribe(sse -> {
                subscriber2Count.incrementAndGet();
                latch.countDown();
            });

            // Give time for subscriptions to be established
            Thread.sleep(100);

            // Publish event
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            controller.publishToStream(event);

            // Wait for both subscribers to receive
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Both subscribers should receive events");
            assertEquals(1, subscriber1Count.get(), "Subscriber 1 should receive 1 event");
            assertEquals(1, subscriber2Count.get(), "Subscriber 2 should receive 1 event");
        }

        @Test
        @DisplayName("each subscriber should apply its own filters")
        void eachSubscriberShouldApplyOwnFilters() throws InterruptedException {
            AtomicInteger patientCount = new AtomicInteger(0);
            AtomicInteger observationCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(2);

            // Subscriber 1 only wants Patient events
            Flux<ServerSentEvent<ResourceChangeEvent>> patientFlux =
                    controller.stream(List.of("Patient"), null);

            // Subscriber 2 only wants Observation events
            Flux<ServerSentEvent<ResourceChangeEvent>> observationFlux =
                    controller.stream(List.of("Observation"), null);

            patientFlux.take(1).subscribe(sse -> {
                patientCount.incrementAndGet();
                latch.countDown();
            });

            observationFlux.take(1).subscribe(sse -> {
                observationCount.incrementAndGet();
                latch.countDown();
            });

            // Give time for subscriptions to be established
            Thread.sleep(100);

            // Publish both types
            ResourceChangeEvent patientEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent observationEvent = new ResourceChangeEvent(
                    "Observation", "456", "create", "default", Instant.now());

            controller.publishToStream(patientEvent);
            controller.publishToStream(observationEvent);

            // Wait for both subscribers to receive their filtered events
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Both subscribers should receive filtered events");
            assertEquals(1, patientCount.get(), "Patient subscriber should receive 1 Patient event");
            assertEquals(1, observationCount.get(), "Observation subscriber should receive 1 Observation event");
        }
    }

    @Nested
    @DisplayName("event format")
    class EventFormat {

        @Test
        @DisplayName("should include correct event type in SSE")
        void shouldIncludeCorrectEventType() {
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(1))
                    .then(() -> controller.publishToStream(event))
                    .assertNext(sse -> {
                        assertEquals("resource-change", sse.event());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should include all event data fields")
        void shouldIncludeAllEventDataFields() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "pat-123", "update", "tenant-abc", timestamp);

            Flux<ServerSentEvent<ResourceChangeEvent>> flux = controller.stream(null, null);

            StepVerifier.create(flux.take(1))
                    .then(() -> controller.publishToStream(event))
                    .assertNext(sse -> {
                        ResourceChangeEvent data = sse.data();
                        assertEquals("Patient", data.resourceType());
                        assertEquals("pat-123", data.resourceId());
                        assertEquals("update", data.action());
                        assertEquals("tenant-abc", data.tenantId());
                        assertEquals(timestamp, data.timestamp());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("publishToStream method")
    class PublishToStreamMethod {

        @Test
        @DisplayName("should handle null events gracefully")
        void shouldHandleNullEventsGracefully() {
            // Should not throw exception
            assertDoesNotThrow(() -> controller.publishToStream(null));
        }
    }
}
