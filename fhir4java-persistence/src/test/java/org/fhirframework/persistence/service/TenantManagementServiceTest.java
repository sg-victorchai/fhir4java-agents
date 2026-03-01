package org.fhirframework.persistence.service;

import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantManagementService")
class TenantManagementServiceTest {

    @Mock
    private FhirTenantRepository tenantRepository;

    @Mock
    private TenantService tenantService;

    private TenantManagementService managementService;

    private static final UUID EXT_ID_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EXT_ID_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        managementService = new TenantManagementService(tenantRepository, tenantService);
    }

    @Nested
    @DisplayName("listAllTenants")
    class ListAllTenants {

        @Test
        @DisplayName("should return all tenants")
        void shouldReturnAllTenants() {
            List<FhirTenantEntity> tenants = List.of(
                    createTenant(1L, EXT_ID_A, "hosp-a", true),
                    createTenant(2L, EXT_ID_B, "hosp-b", false)
            );
            when(tenantRepository.findAll()).thenReturn(tenants);

            List<FhirTenantEntity> result = managementService.listAllTenants();
            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("listEnabledTenants")
    class ListEnabledTenants {

        @Test
        @DisplayName("should return only enabled tenants")
        void shouldReturnOnlyEnabled() {
            List<FhirTenantEntity> tenants = List.of(createTenant(1L, EXT_ID_A, "hosp-a", true));
            when(tenantRepository.findByEnabledTrue()).thenReturn(tenants);

            List<FhirTenantEntity> result = managementService.listEnabledTenants();
            assertEquals(1, result.size());
            assertTrue(result.getFirst().getEnabled());
        }
    }

    @Nested
    @DisplayName("getTenantByExternalId")
    class GetTenantByExternalId {

        @Test
        @DisplayName("should return tenant when found")
        void shouldReturnTenantWhenFound() {
            FhirTenantEntity tenant = createTenant(1L, EXT_ID_A, "hosp-a", true);
            when(tenantRepository.findByExternalId(EXT_ID_A)).thenReturn(Optional.of(tenant));

            FhirTenantEntity result = managementService.getTenantByExternalId(EXT_ID_A);
            assertEquals("hosp-a", result.getInternalId());
        }

        @Test
        @DisplayName("should throw TenantNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(tenantRepository.findByExternalId(EXT_ID_A)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class,
                    () -> managementService.getTenantByExternalId(EXT_ID_A));
        }
    }

    @Nested
    @DisplayName("createTenant")
    class CreateTenant {

        @Test
        @DisplayName("should create tenant successfully")
        void shouldCreateSuccessfully() {
            FhirTenantEntity tenant = createTenant(null, EXT_ID_A, "hosp-a", true);
            FhirTenantEntity saved = createTenant(1L, EXT_ID_A, "hosp-a", true);

            when(tenantRepository.existsByExternalId(EXT_ID_A)).thenReturn(false);
            when(tenantRepository.existsByInternalId("hosp-a")).thenReturn(false);
            when(tenantRepository.save(tenant)).thenReturn(saved);

            FhirTenantEntity result = managementService.createTenant(tenant);
            assertEquals(1L, result.getId());
            verify(tenantRepository).save(tenant);
        }

        @Test
        @DisplayName("should throw when external ID already exists")
        void shouldThrowWhenExternalIdExists() {
            FhirTenantEntity tenant = createTenant(null, EXT_ID_A, "hosp-a", true);
            when(tenantRepository.existsByExternalId(EXT_ID_A)).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> managementService.createTenant(tenant));
            assertTrue(ex.getMessage().contains("external ID"));
        }

        @Test
        @DisplayName("should throw when internal ID already exists")
        void shouldThrowWhenInternalIdExists() {
            FhirTenantEntity tenant = createTenant(null, EXT_ID_A, "hosp-a", true);
            when(tenantRepository.existsByExternalId(EXT_ID_A)).thenReturn(false);
            when(tenantRepository.existsByInternalId("hosp-a")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> managementService.createTenant(tenant));
            assertTrue(ex.getMessage().contains("internal ID"));
        }
    }

    @Nested
    @DisplayName("updateTenant")
    class UpdateTenant {

        @Test
        @DisplayName("should update tenant fields")
        void shouldUpdateFields() {
            FhirTenantEntity existing = createTenant(1L, EXT_ID_A, "hosp-a", true);
            existing.setTenantName("Hospital A");

            FhirTenantEntity updates = FhirTenantEntity.builder()
                    .tenantName("Updated Hospital A")
                    .description("New description")
                    .build();

            when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FhirTenantEntity result = managementService.updateTenant(1L, updates);
            assertEquals("Updated Hospital A", result.getTenantName());
            assertEquals("New description", result.getDescription());
            verify(tenantService).invalidateCache(EXT_ID_A);
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenNotFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class,
                    () -> managementService.updateTenant(99L, FhirTenantEntity.builder().build()));
        }

        @Test
        @DisplayName("should validate uniqueness when external ID changes")
        void shouldValidateUniquenessOnExternalIdChange() {
            FhirTenantEntity existing = createTenant(1L, EXT_ID_A, "hosp-a", true);
            FhirTenantEntity updates = FhirTenantEntity.builder().externalId(EXT_ID_B).build();

            when(tenantRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(tenantRepository.existsByExternalId(EXT_ID_B)).thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> managementService.updateTenant(1L, updates));
        }
    }

    @Nested
    @DisplayName("enableTenant / disableTenant")
    class EnableDisable {

        @Test
        @DisplayName("should enable tenant")
        void shouldEnableTenant() {
            FhirTenantEntity tenant = createTenant(1L, EXT_ID_A, "hosp-a", false);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FhirTenantEntity result = managementService.enableTenant(1L);
            assertTrue(result.getEnabled());
            verify(tenantService).invalidateCache(EXT_ID_A);
        }

        @Test
        @DisplayName("should disable tenant")
        void shouldDisableTenant() {
            FhirTenantEntity tenant = createTenant(1L, EXT_ID_A, "hosp-a", true);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FhirTenantEntity result = managementService.disableTenant(1L);
            assertFalse(result.getEnabled());
            verify(tenantService).invalidateCache(EXT_ID_A);
        }

        @Test
        @DisplayName("should throw when enabling non-existent tenant")
        void shouldThrowOnEnableNotFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(TenantNotFoundException.class, () -> managementService.enableTenant(99L));
        }
    }

    @Nested
    @DisplayName("deleteTenant")
    class DeleteTenant {

        @Test
        @DisplayName("should delete tenant and invalidate cache")
        void shouldDeleteAndInvalidateCache() {
            FhirTenantEntity tenant = createTenant(1L, EXT_ID_A, "hosp-a", true);
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

            managementService.deleteTenant(1L);

            verify(tenantService).invalidateCache(EXT_ID_A);
            verify(tenantRepository).delete(tenant);
        }

        @Test
        @DisplayName("should throw when deleting non-existent tenant")
        void shouldThrowWhenNotFound() {
            when(tenantRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(TenantNotFoundException.class, () -> managementService.deleteTenant(99L));
        }
    }

    private FhirTenantEntity createTenant(Long id, UUID externalId, String internalId, boolean enabled) {
        return FhirTenantEntity.builder()
                .id(id)
                .externalId(externalId)
                .internalId(internalId)
                .tenantCode(internalId.toUpperCase())
                .tenantName("Test Tenant " + internalId)
                .enabled(enabled)
                .build();
    }
}
