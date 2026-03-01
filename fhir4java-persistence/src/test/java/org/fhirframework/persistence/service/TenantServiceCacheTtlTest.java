package org.fhirframework.persistence.service;

import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService - TTL Cache")
class TenantServiceCacheTtlTest {

    @Mock
    private FhirTenantRepository tenantRepository;

    private TenantProperties tenantProperties;
    private TenantService tenantService;

    private static final UUID TENANT_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantProperties.setEnabled(true);
        tenantService = new TenantService(tenantRepository, tenantProperties);
    }

    @Test
    @DisplayName("should use default 5 minute TTL")
    void shouldUseDefaultTtl() {
        assertEquals(Duration.ofMinutes(5), tenantService.getCacheTtl());
    }

    @Test
    @DisplayName("should allow setting custom TTL")
    void shouldAllowCustomTtl() {
        tenantService.setCacheTtl(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(10), tenantService.getCacheTtl());
    }

    @Test
    @DisplayName("should expire cache entries after TTL")
    void shouldExpireCacheEntriesAfterTtl() {
        // Set a very short TTL (0 seconds = immediate expiry)
        tenantService.setCacheTtl(Duration.ZERO);

        FhirTenantEntity tenant = FhirTenantEntity.builder()
                .externalId(TENANT_UUID)
                .internalId("hosp-a")
                .enabled(true)
                .build();
        when(tenantRepository.findByExternalId(TENANT_UUID)).thenReturn(Optional.of(tenant));

        // First call - hits DB and caches
        tenantService.resolveInternalTenantId(TENANT_UUID);

        // Second call - cache entry is expired (TTL=0), should hit DB again
        tenantService.resolveInternalTenantId(TENANT_UUID);

        verify(tenantRepository, times(2)).findByExternalId(TENANT_UUID);
    }

    @Test
    @DisplayName("should serve from cache when entry is within TTL")
    void shouldServeFromCacheWithinTtl() {
        // Set a long TTL
        tenantService.setCacheTtl(Duration.ofHours(1));

        FhirTenantEntity tenant = FhirTenantEntity.builder()
                .externalId(TENANT_UUID)
                .internalId("hosp-a")
                .enabled(true)
                .build();
        when(tenantRepository.findByExternalId(TENANT_UUID)).thenReturn(Optional.of(tenant));

        // First call - hits DB
        tenantService.resolveInternalTenantId(TENANT_UUID);
        // Second call - within TTL, should use cache
        tenantService.resolveInternalTenantId(TENANT_UUID);

        verify(tenantRepository, times(1)).findByExternalId(TENANT_UUID);
    }

    @Test
    @DisplayName("should report correct cache size")
    void shouldReportCorrectCacheSize() {
        tenantService.setCacheTtl(Duration.ofHours(1));

        FhirTenantEntity tenant = FhirTenantEntity.builder()
                .externalId(TENANT_UUID)
                .internalId("hosp-a")
                .enabled(true)
                .build();
        when(tenantRepository.findByExternalId(TENANT_UUID)).thenReturn(Optional.of(tenant));

        assertEquals(0, tenantService.getCacheSize());

        tenantService.resolveInternalTenantId(TENANT_UUID);
        assertEquals(1, tenantService.getCacheSize());

        tenantService.clearCache();
        assertEquals(0, tenantService.getCacheSize());
    }

    @Test
    @DisplayName("should invalidate specific cache entry")
    void shouldInvalidateSpecificEntry() {
        tenantService.setCacheTtl(Duration.ofHours(1));

        FhirTenantEntity tenant = FhirTenantEntity.builder()
                .externalId(TENANT_UUID)
                .internalId("hosp-a")
                .enabled(true)
                .build();
        when(tenantRepository.findByExternalId(TENANT_UUID)).thenReturn(Optional.of(tenant));

        tenantService.resolveInternalTenantId(TENANT_UUID);
        assertEquals(1, tenantService.getCacheSize());

        tenantService.invalidateCache(TENANT_UUID);
        assertEquals(0, tenantService.getCacheSize());

        // Should hit DB again
        tenantService.resolveInternalTenantId(TENANT_UUID);
        verify(tenantRepository, times(2)).findByExternalId(TENANT_UUID);
    }
}
