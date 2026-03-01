package org.fhirframework.plugin.cache;

import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that CachePlugin enforces tenant isolation through
 * tenant-scoped cache keys.
 */
class CachePluginTenantTest {

    private InMemoryTestCachePlugin cachePlugin;

    @BeforeEach
    void setUp() {
        cachePlugin = new InMemoryTestCachePlugin();
    }

    @Test
    void differentTenantsProduceDifferentCacheKeys() {
        PluginContext tenantAContext = createReadContext("Patient", "123", "tenant-a");
        PluginContext tenantBContext = createReadContext("Patient", "123", "tenant-b");

        Optional<String> keyA = cachePlugin.tenantScopedCacheKey(tenantAContext);
        Optional<String> keyB = cachePlugin.tenantScopedCacheKey(tenantBContext);

        assertThat(keyA).isPresent();
        assertThat(keyB).isPresent();
        assertThat(keyA.get()).isNotEqualTo(keyB.get());
        assertThat(keyA.get()).startsWith("tenant-a:");
        assertThat(keyB.get()).startsWith("tenant-b:");
    }

    @Test
    void cacheHitForTenantADoesNotReturnTenantBResource() {
        // Cache a resource for tenant-a
        PluginContext tenantAContext = createReadContext("Patient", "456", "tenant-a");
        Patient tenantAPatient = new Patient();
        tenantAPatient.setId("Patient/456");
        tenantAPatient.addName().setFamily("Smith");

        String tenantAKey = cachePlugin.tenantScopedCacheKey(tenantAContext).orElseThrow();
        cachePlugin.put(tenantAKey, tenantAPatient, Duration.ofMinutes(5));

        // Verify tenant-a can retrieve it
        Optional<IBaseResource> tenantAResult = cachePlugin.get(tenantAKey);
        assertThat(tenantAResult).isPresent();

        // Verify tenant-b cannot retrieve it (different scoped key)
        PluginContext tenantBContext = createReadContext("Patient", "456", "tenant-b");
        String tenantBKey = cachePlugin.tenantScopedCacheKey(tenantBContext).orElseThrow();
        Optional<IBaseResource> tenantBResult = cachePlugin.get(tenantBKey);
        assertThat(tenantBResult).isEmpty();
    }

    @Test
    void invalidateByTenantOnlyRemovesTargetTenantEntries() {
        // Cache resources for two tenants
        Patient patientA = new Patient();
        patientA.setId("Patient/789");
        Patient patientB = new Patient();
        patientB.setId("Patient/789");

        PluginContext ctxA = createReadContext("Patient", "789", "tenant-x");
        PluginContext ctxB = createReadContext("Patient", "789", "tenant-y");

        String keyA = cachePlugin.tenantScopedCacheKey(ctxA).orElseThrow();
        String keyB = cachePlugin.tenantScopedCacheKey(ctxB).orElseThrow();

        cachePlugin.put(keyA, patientA, Duration.ofMinutes(5));
        cachePlugin.put(keyB, patientB, Duration.ofMinutes(5));

        // Both exist
        assertThat(cachePlugin.get(keyA)).isPresent();
        assertThat(cachePlugin.get(keyB)).isPresent();

        // Invalidate tenant-x only
        cachePlugin.invalidateByTenant("tenant-x");

        // tenant-x gone, tenant-y remains
        assertThat(cachePlugin.get(keyA)).isEmpty();
        assertThat(cachePlugin.get(keyB)).isPresent();
    }

    // --- Test Helpers ---

    private PluginContext createReadContext(String resourceType, String resourceId, String tenantId) {
        return PluginContext.builder()
                .operationType(OperationType.READ)
                .fhirVersion(FhirVersion.R5)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .tenantId(tenantId)
                .build();
    }

    /**
     * Simple in-memory CachePlugin implementation for testing.
     */
    private static class InMemoryTestCachePlugin implements CachePlugin {

        private final Map<String, IBaseResource> cache = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return "test-cache-plugin";
        }

        @Override
        public Optional<String> generateCacheKey(PluginContext context) {
            return context.getResourceId()
                    .map(id -> context.getResourceType() + "/" + id);
        }

        @Override
        public Optional<IBaseResource> get(String cacheKey) {
            return Optional.ofNullable(cache.get(cacheKey));
        }

        @Override
        public void put(String cacheKey, IBaseResource resource, Duration ttl) {
            cache.put(cacheKey, resource);
        }

        @Override
        public void invalidate(String cacheKey) {
            cache.remove(cacheKey);
        }

        @Override
        public void invalidateByType(String resourceType) {
            cache.entrySet().removeIf(entry -> entry.getKey().contains(resourceType + "/"));
        }

        @Override
        public void invalidateByTenant(String tenantId) {
            String prefix = tenantId + ":";
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        }
    }
}
