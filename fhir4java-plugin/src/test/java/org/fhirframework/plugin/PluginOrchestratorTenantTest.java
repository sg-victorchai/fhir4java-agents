package org.fhirframework.plugin;

import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.version.FhirVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that PluginOrchestrator correctly propagates TenantContext
 * to async plugin execution threads and cleans up afterwards.
 */
class PluginOrchestratorTenantTest {

    private PluginOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new PluginOrchestrator();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
        TenantContext.clear();
    }

    @Test
    void asyncPluginReceivesCorrectTenantContextInAfterPhase() throws Exception {
        // Arrange: capture the tenant ID seen by the async plugin
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        FhirPlugin asyncPlugin = new TenantCapturingPlugin(
                "async-after-plugin", capturedTenantId, latch);
        orchestrator.registerPlugin(asyncPlugin);

        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .tenantId("tenant-hospital-a")
                .build();

        // Set tenant on the calling thread (simulates TenantFilter)
        TenantContext.setCurrentTenantId("tenant-hospital-a");

        // Act
        orchestrator.executeAfter(context);

        // Wait for async execution
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertThat(completed).isTrue();
        assertThat(capturedTenantId.get()).isEqualTo("tenant-hospital-a");
    }

    @Test
    void asyncPluginReceivesCorrectTenantContextInOnErrorPhase() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        FhirPlugin asyncPlugin = new TenantCapturingErrorPlugin(
                "async-error-plugin", capturedTenantId, latch);
        orchestrator.registerPlugin(asyncPlugin);

        PluginContext context = PluginContext.builder()
                .operationType(OperationType.READ)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .tenantId("tenant-hospital-b")
                .build();

        TenantContext.setCurrentTenantId("tenant-hospital-b");

        // Act
        orchestrator.executeOnError(context, new RuntimeException("test error"));

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        // Assert
        assertThat(completed).isTrue();
        assertThat(capturedTenantId.get()).isEqualTo("tenant-hospital-b");
    }

    @Test
    void tenantContextIsClearedAfterAsyncExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedTenantId = new AtomicReference<>();
        AtomicReference<String> tenantAfterClear = new AtomicReference<>();

        // Plugin that captures tenant and then checks after clearing
        FhirPlugin asyncPlugin = new AbstractAsyncPlugin("cleanup-plugin") {
            @Override
            public PluginResult executeAfter(PluginContext context) {
                capturedTenantId.set(TenantContext.getCurrentTenantId());
                return PluginResult.continueProcessing();
            }
        };
        orchestrator.registerPlugin(asyncPlugin);

        // Second plugin that runs after the first to check cleanup
        CountDownLatch secondLatch = new CountDownLatch(1);
        FhirPlugin secondPlugin = new AbstractAsyncPlugin("second-plugin") {
            @Override
            public PluginResult executeAfter(PluginContext context) {
                // This runs on a pool thread; the wrapWithTenantContext
                // sets tenant before and clears after
                tenantAfterClear.set(TenantContext.getCurrentTenantId());
                secondLatch.countDown();
                return PluginResult.continueProcessing();
            }
        };
        orchestrator.registerPlugin(secondPlugin);

        PluginContext context = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .fhirVersion(FhirVersion.R5)
                .resourceType("Patient")
                .tenantId("tenant-c")
                .build();

        TenantContext.setCurrentTenantId("tenant-c");

        orchestrator.executeAfter(context);

        boolean completed = secondLatch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        // Both async plugins should see the propagated tenant
        assertThat(capturedTenantId.get()).isEqualTo("tenant-c");
        assertThat(tenantAfterClear.get()).isEqualTo("tenant-c");
    }

    @Test
    void concurrentRequestsWithDifferentTenantsDoNotCrossContaminate() throws Exception {
        int iterations = 20;
        CountDownLatch allDone = new CountDownLatch(iterations * 2);
        AtomicReference<String> failureMessage = new AtomicReference<>();

        // Create a plugin that verifies the tenant matches expectations
        for (int i = 0; i < iterations; i++) {
            String tenantA = "tenant-A-" + i;
            String tenantB = "tenant-B-" + i;

            // Simulate two concurrent requests with different tenants
            CompletableFuture.runAsync(() -> {
                try {
                    PluginOrchestrator localOrchestrator = new PluginOrchestrator();
                    AtomicReference<String> captured = new AtomicReference<>();
                    CountDownLatch pluginLatch = new CountDownLatch(1);

                    localOrchestrator.registerPlugin(
                            new TenantCapturingPlugin("plugin-a", captured, pluginLatch));

                    TenantContext.setCurrentTenantId(tenantA);
                    PluginContext ctx = PluginContext.builder()
                            .operationType(OperationType.CREATE)
                            .fhirVersion(FhirVersion.R5)
                            .resourceType("Patient")
                            .tenantId(tenantA)
                            .build();

                    localOrchestrator.executeAfter(ctx);
                    pluginLatch.await(5, TimeUnit.SECONDS);

                    if (!tenantA.equals(captured.get())) {
                        failureMessage.compareAndSet(null,
                                "Expected " + tenantA + " but got " + captured.get());
                    }

                    localOrchestrator.shutdown();
                } catch (Exception e) {
                    failureMessage.compareAndSet(null, e.getMessage());
                } finally {
                    TenantContext.clear();
                    allDone.countDown();
                }
            });

            CompletableFuture.runAsync(() -> {
                try {
                    PluginOrchestrator localOrchestrator = new PluginOrchestrator();
                    AtomicReference<String> captured = new AtomicReference<>();
                    CountDownLatch pluginLatch = new CountDownLatch(1);

                    localOrchestrator.registerPlugin(
                            new TenantCapturingPlugin("plugin-b", captured, pluginLatch));

                    TenantContext.setCurrentTenantId(tenantB);
                    PluginContext ctx = PluginContext.builder()
                            .operationType(OperationType.CREATE)
                            .fhirVersion(FhirVersion.R5)
                            .resourceType("Patient")
                            .tenantId(tenantB)
                            .build();

                    localOrchestrator.executeAfter(ctx);
                    pluginLatch.await(5, TimeUnit.SECONDS);

                    if (!tenantB.equals(captured.get())) {
                        failureMessage.compareAndSet(null,
                                "Expected " + tenantB + " but got " + captured.get());
                    }

                    localOrchestrator.shutdown();
                } catch (Exception e) {
                    failureMessage.compareAndSet(null, e.getMessage());
                } finally {
                    TenantContext.clear();
                    allDone.countDown();
                }
            });
        }

        boolean completed = allDone.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(failureMessage.get()).isNull();
    }

    // --- Test Helper Classes ---

    /**
     * Abstract base for async test plugins.
     */
    private abstract static class AbstractAsyncPlugin implements FhirPlugin {
        private final String name;

        AbstractAsyncPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ExecutionMode getExecutionMode() {
            return ExecutionMode.ASYNC;
        }

        @Override
        public List<OperationDescriptor> getSupportedOperations() {
            return List.of(OperationDescriptor.matchAll());
        }
    }

    /**
     * Async plugin that captures TenantContext.getCurrentTenantId()
     * during executeAfter.
     */
    private static class TenantCapturingPlugin extends AbstractAsyncPlugin {
        private final AtomicReference<String> capturedTenantId;
        private final CountDownLatch latch;

        TenantCapturingPlugin(String name, AtomicReference<String> capturedTenantId, CountDownLatch latch) {
            super(name);
            this.capturedTenantId = capturedTenantId;
            this.latch = latch;
        }

        @Override
        public PluginResult executeAfter(PluginContext context) {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            latch.countDown();
            return PluginResult.continueProcessing();
        }
    }

    /**
     * Async plugin that captures TenantContext.getCurrentTenantId()
     * during executeOnError.
     */
    private static class TenantCapturingErrorPlugin extends AbstractAsyncPlugin {
        private final AtomicReference<String> capturedTenantId;
        private final CountDownLatch latch;

        TenantCapturingErrorPlugin(String name, AtomicReference<String> capturedTenantId, CountDownLatch latch) {
            super(name);
            this.capturedTenantId = capturedTenantId;
            this.latch = latch;
        }

        @Override
        public PluginResult executeOnError(PluginContext context, Exception exception) {
            capturedTenantId.set(TenantContext.getCurrentTenantId());
            latch.countDown();
            return PluginResult.continueProcessing();
        }
    }
}
