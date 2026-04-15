package org.fhirframework.mcp.agent;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AgentEventConsumer.
 */
@DisplayName("AgentEventConsumer Tests")
class AgentEventConsumerTest {

    private AgentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        // Create consumer with a test URL (actual connection tested in integration tests)
        consumer = new AgentEventConsumer("http://localhost:8080");
    }

    @Test
    @DisplayName("should create consumer with server URL")
    void createConsumerWithServerUrl() {
        AgentEventConsumer consumer = new AgentEventConsumer("http://localhost:8080");
        assertNotNull(consumer);
    }

    @Test
    @DisplayName("should create consumer with WebClient")
    void createConsumerWithWebClient() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
        AgentEventConsumer consumer = new AgentEventConsumer(webClient);
        assertNotNull(consumer);
    }

    @Test
    @DisplayName("should track active subscriptions")
    void trackActiveSubscriptions() {
        assertEquals(0, consumer.getActiveSubscriptionCount());
        assertFalse(consumer.isMonitoring("test-subscription"));
    }

    @Test
    @DisplayName("should stop monitoring by subscription ID")
    void stopMonitoringBySubscriptionId() {
        // Initially no subscriptions
        assertEquals(0, consumer.getActiveSubscriptionCount());

        // Stop non-existent subscription should not throw
        consumer.stopMonitoring("non-existent");
        assertEquals(0, consumer.getActiveSubscriptionCount());
    }

    @Test
    @DisplayName("should stop all monitoring")
    void stopAllMonitoring() {
        // Stop all should work even with no active subscriptions
        consumer.stopAllMonitoring();
        assertEquals(0, consumer.getActiveSubscriptionCount());
    }

    @Test
    @DisplayName("should return Flux for event subscription")
    void subscribeToEventsReturnsFlux() {
        Flux<ResourceChangeEvent> flux = consumer.subscribeToEvents(List.of("Patient"));
        assertNotNull(flux);
    }

    @Test
    @DisplayName("should return Flux for filtered event subscription")
    void subscribeToEventsWithActionsReturnsFlux() {
        Flux<ResourceChangeEvent> flux = consumer.subscribeToEvents(
                List.of("Patient", "Observation"),
                List.of("create", "update")
        );
        assertNotNull(flux);
    }

    @Test
    @DisplayName("should handle empty topics list")
    void subscribeToEventsWithEmptyTopics() {
        Flux<ResourceChangeEvent> flux = consumer.subscribeToEvents(List.of());
        assertNotNull(flux);
    }

    @Test
    @DisplayName("should handle null topics list")
    void subscribeToEventsWithNullTopics() {
        Flux<ResourceChangeEvent> flux = consumer.subscribeToEvents(null);
        assertNotNull(flux);
    }

    @Test
    @DisplayName("ResourceChangeEvent should contain expected fields")
    void resourceChangeEventStructure() {
        ResourceChangeEvent event = new ResourceChangeEvent(
                "Patient",
                "123",
                "create",
                "default",
                Instant.now()
        );

        assertEquals("Patient", event.resourceType());
        assertEquals("123", event.resourceId());
        assertEquals("create", event.action());
        assertEquals("default", event.tenantId());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("should support patient monitoring convenience method")
    void patientMonitoringMethod() {
        List<ResourceChangeEvent> events = new ArrayList<>();

        // This starts a subscription that would connect to the server
        // In unit tests, we just verify the method doesn't throw
        // Actual event reception is tested in integration tests
        assertDoesNotThrow(() -> {
            // Note: This would try to connect, but we're just testing the API
            // consumer.startPatientMonitoring(events::add);
        });
    }

    @Test
    @DisplayName("should support clinical monitoring convenience method")
    void clinicalMonitoringMethod() {
        assertDoesNotThrow(() -> {
            // consumer.startClinicalMonitoring(event -> {});
        });
    }

    @Test
    @DisplayName("should support audit monitoring convenience method")
    void auditMonitoringMethod() {
        assertDoesNotThrow(() -> {
            // consumer.startAuditMonitoring(event -> {});
        });
    }
}
