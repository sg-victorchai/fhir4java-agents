package org.fhirframework.plugin.event;

import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginConfig;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ResourceChangePlugin.
 */
class ResourceChangePluginTest {

    private ResourceChangePlugin plugin;
    private TestEventPublisher eventPublisher;
    private TestPluginConfig pluginConfig;

    @BeforeEach
    void setUp() {
        eventPublisher = new TestEventPublisher();
        pluginConfig = new TestPluginConfig();
        pluginConfig.setResourceChangeEnabled(true);
        plugin = new ResourceChangePlugin(eventPublisher, pluginConfig);
        plugin.init(); // Trigger @PostConstruct initialization
    }

    @Test
    void getName_returnsResourceChangePlugin() {
        assertThat(plugin.getName()).isEqualTo("resource-change-plugin");
    }

    @Test
    void getExecutionMode_returnsAsync() {
        // Event publishing should be async to not impact request latency
        assertThat(plugin.getExecutionMode()).isEqualTo(ExecutionMode.ASYNC);
    }

    @Test
    void isEnabled_returnsTrueByDefault() {
        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    void executeAfter_forCreateOperation_publishesCreateEvent() {
        // Arrange
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .resourceId("123")
                .tenantId("tenant-a")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);

        ResourceChangeEvent event = eventPublisher.getPublishedEvents().get(0);
        assertThat(event.resourceType()).isEqualTo("Patient");
        assertThat(event.resourceId()).isEqualTo("123");
        assertThat(event.action()).isEqualTo(ResourceChangeEvent.ACTION_CREATE);
        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void executeAfter_forUpdateOperation_publishesUpdateEvent() {
        // Arrange
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.UPDATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Observation")
                .resourceId("obs-456")
                .tenantId("tenant-b")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);

        ResourceChangeEvent event = eventPublisher.getPublishedEvents().get(0);
        assertThat(event.resourceType()).isEqualTo("Observation");
        assertThat(event.resourceId()).isEqualTo("obs-456");
        assertThat(event.action()).isEqualTo(ResourceChangeEvent.ACTION_UPDATE);
        assertThat(event.tenantId()).isEqualTo("tenant-b");
    }

    @Test
    void executeAfter_forDeleteOperation_publishesDeleteEvent() {
        // Arrange
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.DELETE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Condition")
                .resourceId("cond-789")
                .tenantId("tenant-c")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);

        ResourceChangeEvent event = eventPublisher.getPublishedEvents().get(0);
        assertThat(event.resourceType()).isEqualTo("Condition");
        assertThat(event.resourceId()).isEqualTo("cond-789");
        assertThat(event.action()).isEqualTo(ResourceChangeEvent.ACTION_DELETE);
        assertThat(event.tenantId()).isEqualTo("tenant-c");
    }

    @Test
    void executeAfter_forReadOperation_doesNotPublishEvent() {
        // Arrange - READ is not a write operation, so no event should be published
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.READ)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .resourceId("123")
                .tenantId("tenant-a")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).isEmpty();
    }

    @Test
    void executeAfter_forSearchOperation_doesNotPublishEvent() {
        // Arrange - SEARCH is not a write operation
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.SEARCH)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .tenantId("tenant-a")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).isEmpty();
    }

    @Test
    void executeAfter_includesCorrectTenantId() {
        // Arrange
        String expectedTenantId = "hospital-xyz-tenant";
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .resourceId("123")
                .tenantId(expectedTenantId)
                .build();

        // Act
        plugin.executeAfter(context);

        // Assert
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
        assertThat(eventPublisher.getPublishedEvents().get(0).tenantId()).isEqualTo(expectedTenantId);
    }

    @Test
    void executeAfter_withNullTenantId_publishesEventWithNullTenant() {
        // Arrange - tenant can be null in single-tenant mode
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .resourceId("123")
                .build();

        // Act
        plugin.executeAfter(context);

        // Assert
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
        assertThat(eventPublisher.getPublishedEvents().get(0).tenantId()).isNull();
    }

    @Test
    void executeAfter_withMissingResourceId_doesNotPublishEvent() {
        // Arrange - Some operations like search don't have a specific resource ID
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                // No resourceId
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert - Should continue processing but not publish incomplete event
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).isEmpty();
    }

    @Test
    void executeAfter_supportsR4BVersion() {
        // Arrange
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.UPDATE)
                .fhirVersion(FhirVersion.R4B)
                .resourceType("MedicationRequest")
                .resourceId("med-001")
                .tenantId("tenant-r4b")
                .build();

        // Act
        PluginResult result = plugin.executeAfter(context);

        // Assert
        assertThat(result.shouldContinue()).isTrue();
        assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
        assertThat(eventPublisher.getPublishedEvents().get(0).resourceType()).isEqualTo("MedicationRequest");
    }

    @Test
    void setEnabled_canDisablePlugin() {
        // Arrange
        plugin.setEnabled(false);
        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .resourceId("123")
                .tenantId("tenant-a")
                .build();

        // Act & Assert
        assertThat(plugin.isEnabled()).isFalse();
        // When disabled, supports() returns false, so plugin won't be called by orchestrator
    }

    // --- Test Helper ---

    /**
     * Test implementation of EventPublisher that captures published events.
     */
    private static class TestEventPublisher implements EventPublisher {
        private final List<ResourceChangeEvent> publishedEvents = new CopyOnWriteArrayList<>();
        private final List<Consumer<ResourceChangeEvent>> subscribers = new ArrayList<>();

        @Override
        public void publish(ResourceChangeEvent event) {
            publishedEvents.add(event);
            subscribers.forEach(s -> s.accept(event));
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

        public List<ResourceChangeEvent> getPublishedEvents() {
            return new ArrayList<>(publishedEvents);
        }

        public void clear() {
            publishedEvents.clear();
        }
    }

    /**
     * Test implementation of PluginConfig for controlling plugin behavior in tests.
     */
    private static class TestPluginConfig extends PluginConfig {
        private boolean resourceChangeEnabled = true;

        public void setResourceChangeEnabled(boolean enabled) {
            this.resourceChangeEnabled = enabled;
        }

        @Override
        public boolean isResourceChangeEnabled() {
            return resourceChangeEnabled;
        }
    }
}
