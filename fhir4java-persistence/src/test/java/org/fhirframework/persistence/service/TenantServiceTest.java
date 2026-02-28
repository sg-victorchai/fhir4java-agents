package org.fhirframework.persistence.service;

import org.fhirframework.core.exception.TenantDisabledException;
import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantService} resolution logic, caching, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService")
class TenantServiceTest {

    @Mock
    private FhirTenantRepository tenantRepository;

    private TenantProperties tenantProperties;
    private TenantService tenantService;

    private static final UUID TENANT_A_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B_UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID UNKNOWN_UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantService = new TenantService(tenantRepository, tenantProperties);
    }

    @Nested
    @DisplayName("resolveEffectiveTenantId - tenancy disabled")
    class TenancyDisabled {

        @BeforeEach
        void setUp() {
            tenantProperties.setEnabled(false);
        }

        @Test
        @DisplayName("should return default tenant ID when tenancy is disabled")
        void shouldReturnDefaultTenantId() {
            String result = tenantService.resolveEffectiveTenantId(null);
            assertEquals("default", result);
        }

        @Test
        @DisplayName("should return default tenant ID even with header present")
        void shouldReturnDefaultEvenWithHeader() {
            String result = tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            assertEquals("default", result);
        }

        @Test
        @DisplayName("should not call repository when tenancy is disabled")
        void shouldNotCallRepository() {
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            verifyNoInteractions(tenantRepository);
        }

        @Test
        @DisplayName("should return custom default tenant ID when configured")
        void shouldReturnCustomDefaultTenantId() {
            tenantProperties.setDefaultTenantId("custom-default");
            String result = tenantService.resolveEffectiveTenantId(null);
            assertEquals("custom-default", result);
        }
    }

    @Nested
    @DisplayName("resolveEffectiveTenantId - tenancy enabled")
    class TenancyEnabled {

        @BeforeEach
        void setUp() {
            tenantProperties.setEnabled(true);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when header is null")
        void shouldThrowWhenHeaderIsNull() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tenantService.resolveEffectiveTenantId(null));
            assertTrue(ex.getMessage().contains("Missing required header"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when header is blank")
        void shouldThrowWhenHeaderIsBlank() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tenantService.resolveEffectiveTenantId("  "));
            assertTrue(ex.getMessage().contains("Missing required header"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when header is empty")
        void shouldThrowWhenHeaderIsEmpty() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tenantService.resolveEffectiveTenantId(""));
            assertTrue(ex.getMessage().contains("Missing required header"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid UUID format")
        void shouldThrowForInvalidUuidFormat() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tenantService.resolveEffectiveTenantId("not-a-uuid"));
            assertTrue(ex.getMessage().contains("Invalid tenant ID format"));
        }

        @Test
        @DisplayName("should resolve valid tenant UUID to internal ID")
        void shouldResolveValidTenantUuid() {
            FhirTenantEntity tenant = createTenant(TENANT_A_UUID, "hosp-a", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenant));

            String result = tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            assertEquals("hosp-a", result);
        }

        @Test
        @DisplayName("should throw TenantNotFoundException for unknown UUID")
        void shouldThrowForUnknownUuid() {
            when(tenantRepository.findByExternalId(UNKNOWN_UUID)).thenReturn(Optional.empty());

            TenantNotFoundException ex = assertThrows(TenantNotFoundException.class,
                    () -> tenantService.resolveEffectiveTenantId(UNKNOWN_UUID.toString()));
            assertEquals(UNKNOWN_UUID.toString(), ex.getTenantExternalId());
        }

        @Test
        @DisplayName("should throw TenantDisabledException for disabled tenant")
        void shouldThrowForDisabledTenant() {
            FhirTenantEntity tenant = createTenant(TENANT_A_UUID, "hosp-a", false);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenant));

            TenantDisabledException ex = assertThrows(TenantDisabledException.class,
                    () -> tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString()));
            assertEquals(TENANT_A_UUID.toString(), ex.getTenantExternalId());
        }

        @Test
        @DisplayName("should trim whitespace from header value")
        void shouldTrimWhitespace() {
            FhirTenantEntity tenant = createTenant(TENANT_A_UUID, "hosp-a", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenant));

            String result = tenantService.resolveEffectiveTenantId("  " + TENANT_A_UUID + "  ");
            assertEquals("hosp-a", result);
        }

        @Test
        @DisplayName("should include header name in error message")
        void shouldIncludeHeaderNameInErrorMessage() {
            tenantProperties.setHeaderName("X-Custom-Tenant");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tenantService.resolveEffectiveTenantId(null));
            assertTrue(ex.getMessage().contains("X-Custom-Tenant"));
        }
    }

    @Nested
    @DisplayName("Caching behavior")
    class Caching {

        @BeforeEach
        void setUp() {
            tenantProperties.setEnabled(true);
        }

        @Test
        @DisplayName("should cache resolved tenant ID")
        void shouldCacheResolvedTenantId() {
            FhirTenantEntity tenant = createTenant(TENANT_A_UUID, "hosp-a", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenant));

            // First call - hits DB
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            // Second call - should use cache
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());

            // Repository should only be called once
            verify(tenantRepository, times(1)).findByExternalId(TENANT_A_UUID);
        }

        @Test
        @DisplayName("should invalidate single cache entry")
        void shouldInvalidateSingleCacheEntry() {
            FhirTenantEntity tenantA = createTenant(TENANT_A_UUID, "hosp-a", true);
            FhirTenantEntity tenantB = createTenant(TENANT_B_UUID, "hosp-b", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenantA));
            when(tenantRepository.findByExternalId(TENANT_B_UUID)).thenReturn(Optional.of(tenantB));

            // Populate cache
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            tenantService.resolveEffectiveTenantId(TENANT_B_UUID.toString());

            // Invalidate only tenant A
            tenantService.invalidateCache(TENANT_A_UUID);

            // Resolve again - A should hit DB, B should use cache
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            tenantService.resolveEffectiveTenantId(TENANT_B_UUID.toString());

            verify(tenantRepository, times(2)).findByExternalId(TENANT_A_UUID);
            verify(tenantRepository, times(1)).findByExternalId(TENANT_B_UUID);
        }

        @Test
        @DisplayName("should clear entire cache")
        void shouldClearEntireCache() {
            FhirTenantEntity tenantA = createTenant(TENANT_A_UUID, "hosp-a", true);
            FhirTenantEntity tenantB = createTenant(TENANT_B_UUID, "hosp-b", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenantA));
            when(tenantRepository.findByExternalId(TENANT_B_UUID)).thenReturn(Optional.of(tenantB));

            // Populate cache
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            tenantService.resolveEffectiveTenantId(TENANT_B_UUID.toString());

            // Clear entire cache
            tenantService.clearCache();

            // Both should hit DB again
            tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            tenantService.resolveEffectiveTenantId(TENANT_B_UUID.toString());

            verify(tenantRepository, times(2)).findByExternalId(TENANT_A_UUID);
            verify(tenantRepository, times(2)).findByExternalId(TENANT_B_UUID);
        }

        @Test
        @DisplayName("should not cache disabled tenant lookups")
        void shouldNotCacheDisabledTenant() {
            FhirTenantEntity disabledTenant = createTenant(TENANT_A_UUID, "hosp-a", false);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(disabledTenant));

            // First attempt should throw
            assertThrows(TenantDisabledException.class,
                    () -> tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString()));

            // Re-enable tenant
            FhirTenantEntity enabledTenant = createTenant(TENANT_A_UUID, "hosp-a", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(enabledTenant));

            // Second attempt should succeed (not cached from disabled state)
            String result = tenantService.resolveEffectiveTenantId(TENANT_A_UUID.toString());
            assertEquals("hosp-a", result);

            verify(tenantRepository, times(2)).findByExternalId(TENANT_A_UUID);
        }
    }

    @Nested
    @DisplayName("findByExternalId")
    class FindByExternalId {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            FhirTenantEntity tenant = createTenant(TENANT_A_UUID, "hosp-a", true);
            when(tenantRepository.findByExternalId(TENANT_A_UUID)).thenReturn(Optional.of(tenant));

            Optional<FhirTenantEntity> result = tenantService.findByExternalId(TENANT_A_UUID);
            assertTrue(result.isPresent());
            assertEquals("hosp-a", result.get().getInternalId());
        }

        @Test
        @DisplayName("should return empty for unknown UUID")
        void shouldReturnEmptyForUnknown() {
            when(tenantRepository.findByExternalId(UNKNOWN_UUID)).thenReturn(Optional.empty());

            Optional<FhirTenantEntity> result = tenantService.findByExternalId(UNKNOWN_UUID);
            assertTrue(result.isEmpty());
        }
    }

    private FhirTenantEntity createTenant(UUID externalId, String internalId, boolean enabled) {
        return FhirTenantEntity.builder()
                .externalId(externalId)
                .internalId(internalId)
                .tenantCode(internalId.toUpperCase())
                .tenantName("Test Tenant " + internalId)
                .enabled(enabled)
                .build();
    }
}
