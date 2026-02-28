package org.fhirframework.core.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TenantContext} ThreadLocal lifecycle management.
 */
@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getCurrentTenantId")
    class GetCurrentTenantId {

        @Test
        @DisplayName("should return 'default' when no tenant ID is set")
        void shouldReturnDefaultWhenNotSet() {
            assertEquals("default", TenantContext.getCurrentTenantId());
        }

        @Test
        @DisplayName("should return the set tenant ID")
        void shouldReturnSetTenantId() {
            TenantContext.setCurrentTenantId("tenant-a");
            assertEquals("tenant-a", TenantContext.getCurrentTenantId());
        }

        @Test
        @DisplayName("should return the most recently set tenant ID")
        void shouldReturnMostRecentTenantId() {
            TenantContext.setCurrentTenantId("tenant-a");
            TenantContext.setCurrentTenantId("tenant-b");
            assertEquals("tenant-b", TenantContext.getCurrentTenantId());
        }

        @Test
        @DisplayName("should return 'default' after clear()")
        void shouldReturnDefaultAfterClear() {
            TenantContext.setCurrentTenantId("tenant-a");
            TenantContext.clear();
            assertEquals("default", TenantContext.getCurrentTenantId());
        }
    }

    @Nested
    @DisplayName("getTenantIdIfSet")
    class GetTenantIdIfSet {

        @Test
        @DisplayName("should return empty Optional when no tenant ID is set")
        void shouldReturnEmptyWhenNotSet() {
            Optional<String> result = TenantContext.getTenantIdIfSet();
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return Optional with value when tenant ID is set")
        void shouldReturnValueWhenSet() {
            TenantContext.setCurrentTenantId("tenant-a");
            Optional<String> result = TenantContext.getTenantIdIfSet();
            assertTrue(result.isPresent());
            assertEquals("tenant-a", result.get());
        }

        @Test
        @DisplayName("should return empty Optional after clear()")
        void shouldReturnEmptyAfterClear() {
            TenantContext.setCurrentTenantId("tenant-a");
            TenantContext.clear();
            Optional<String> result = TenantContext.getTenantIdIfSet();
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Thread isolation")
    class ThreadIsolation {

        @Test
        @DisplayName("should isolate tenant IDs between threads")
        void shouldIsolateBetweenThreads() throws InterruptedException {
            TenantContext.setCurrentTenantId("main-thread-tenant");

            AtomicReference<String> childTenantId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            Thread child = new Thread(() -> {
                try {
                    // Child thread should NOT see the parent's tenant ID
                    childTenantId.set(TenantContext.getCurrentTenantId());
                } finally {
                    TenantContext.clear();
                    latch.countDown();
                }
            });
            child.start();
            latch.await();

            assertEquals("default", childTenantId.get(),
                    "Child thread should not inherit tenant ID from parent thread");
            assertEquals("main-thread-tenant", TenantContext.getCurrentTenantId(),
                    "Parent thread tenant ID should be unaffected");
        }

        @Test
        @DisplayName("should allow different tenants in concurrent threads")
        void shouldAllowDifferentTenantsInConcurrentThreads() throws InterruptedException {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch done = new CountDownLatch(2);
            AtomicReference<String> thread1Tenant = new AtomicReference<>();
            AtomicReference<String> thread2Tenant = new AtomicReference<>();

            Thread t1 = new Thread(() -> {
                try {
                    TenantContext.setCurrentTenantId("tenant-1");
                    ready.countDown();
                    ready.await();
                    thread1Tenant.set(TenantContext.getCurrentTenantId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });

            Thread t2 = new Thread(() -> {
                try {
                    TenantContext.setCurrentTenantId("tenant-2");
                    ready.countDown();
                    ready.await();
                    thread2Tenant.set(TenantContext.getCurrentTenantId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });

            t1.start();
            t2.start();
            done.await();

            assertEquals("tenant-1", thread1Tenant.get());
            assertEquals("tenant-2", thread2Tenant.get());
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should be safe to call when nothing is set")
        void shouldBeSafeWhenNothingSet() {
            assertDoesNotThrow(() -> TenantContext.clear());
        }

        @Test
        @DisplayName("should be safe to call multiple times")
        void shouldBeSafeToCallMultipleTimes() {
            TenantContext.setCurrentTenantId("tenant-a");
            TenantContext.clear();
            assertDoesNotThrow(() -> TenantContext.clear());
        }
    }
}
