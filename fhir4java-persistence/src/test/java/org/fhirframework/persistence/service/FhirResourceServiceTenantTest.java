package org.fhirframework.persistence.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.validation.ProfileValidator;
import org.fhirframework.core.validation.SearchParameterValidator;
import org.fhirframework.core.validation.ValidationConfig;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.fhirframework.persistence.repository.SchemaRoutingRepository;
import org.hl7.fhir.r5.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FhirResourceService} verifying tenant isolation in CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FhirResourceService - Tenant Isolation")
class FhirResourceServiceTenantTest {

    @Mock
    private SchemaRoutingRepository schemaRoutingRepository;

    @Mock
    private FhirContextFactory contextFactory;

    @Mock
    private SearchParameterValidator searchParameterValidator;

    @Mock
    private ProfileValidator profileValidator;

    @Mock
    private ValidationConfig validationConfig;

    private FhirResourceService service;

    private static final String TENANT_A = "hosp-a";
    private static final String TENANT_B = "hosp-b";
    private static final String RESOURCE_TYPE = "Patient";
    private static final FhirVersion VERSION = FhirVersion.R5;

    private static final String PATIENT_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "Smith", "given": ["John"]}]
            }
            """;

    @BeforeEach
    void setUp() {
        service = new FhirResourceService(
                schemaRoutingRepository, contextFactory,
                searchParameterValidator, profileValidator, validationConfig);

        FhirContext r5Context = FhirContext.forR5();
        lenient().when(contextFactory.getContext(VERSION)).thenReturn(r5Context);
        lenient().when(validationConfig.isEnabled()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should set tenant ID from TenantContext on new entity")
        void shouldSetTenantIdOnEntity() {
            TenantContext.setCurrentTenantId(TENANT_A);
            when(schemaRoutingRepository.save(eq(RESOURCE_TYPE), any(FhirResourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            service.create(RESOURCE_TYPE, PATIENT_JSON, VERSION);

            ArgumentCaptor<FhirResourceEntity> captor = ArgumentCaptor.forClass(FhirResourceEntity.class);
            verify(schemaRoutingRepository).save(eq(RESOURCE_TYPE), captor.capture());
            assertEquals(TENANT_A, captor.getValue().getTenantId());
        }

        @Test
        @DisplayName("should use default tenant when TenantContext is not set")
        void shouldUseDefaultTenant() {
            // TenantContext not set, should default to "default"
            when(schemaRoutingRepository.save(eq(RESOURCE_TYPE), any(FhirResourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            service.create(RESOURCE_TYPE, PATIENT_JSON, VERSION);

            ArgumentCaptor<FhirResourceEntity> captor = ArgumentCaptor.forClass(FhirResourceEntity.class);
            verify(schemaRoutingRepository).save(eq(RESOURCE_TYPE), captor.capture());
            assertEquals("default", captor.getValue().getTenantId());
        }

        @Test
        @DisplayName("should create same resource type with different tenant IDs")
        void shouldCreateWithDifferentTenants() {
            when(schemaRoutingRepository.save(eq(RESOURCE_TYPE), any(FhirResourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            // Create with tenant A
            TenantContext.setCurrentTenantId(TENANT_A);
            service.create(RESOURCE_TYPE, PATIENT_JSON, VERSION);

            // Create with tenant B
            TenantContext.setCurrentTenantId(TENANT_B);
            service.create(RESOURCE_TYPE, PATIENT_JSON, VERSION);

            ArgumentCaptor<FhirResourceEntity> captor = ArgumentCaptor.forClass(FhirResourceEntity.class);
            verify(schemaRoutingRepository, times(2)).save(eq(RESOURCE_TYPE), captor.capture());

            List<FhirResourceEntity> saved = captor.getAllValues();
            assertEquals(TENANT_A, saved.get(0).getTenantId());
            assertEquals(TENANT_B, saved.get(1).getTenantId());
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("should query with correct tenant ID")
        void shouldQueryWithCorrectTenantId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            FhirResourceEntity entity = buildEntity(resourceId, TENANT_A, 1, false);
            when(schemaRoutingRepository.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(Optional.of(entity));

            FhirResourceService.ResourceResult result = service.read(RESOURCE_TYPE, resourceId, VERSION);
            assertEquals(resourceId, result.resourceId());
        }

        @Test
        @DisplayName("should not find resource from different tenant")
        void shouldNotFindResourceFromDifferentTenant() {
            TenantContext.setCurrentTenantId(TENANT_B);
            String resourceId = "test-id";
            when(schemaRoutingRepository.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_B, RESOURCE_TYPE, resourceId))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.read(RESOURCE_TYPE, resourceId, VERSION));
        }
    }

    @Nested
    @DisplayName("vread")
    class Vread {

        @Test
        @DisplayName("should query with correct tenant ID and version")
        void shouldQueryWithTenantIdAndVersion() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            FhirResourceEntity entity = buildEntity(resourceId, TENANT_A, 2, false);
            when(schemaRoutingRepository.findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
                    TENANT_A, RESOURCE_TYPE, resourceId, 2))
                    .thenReturn(Optional.of(entity));

            FhirResourceService.ResourceResult result = service.vread(RESOURCE_TYPE, resourceId, 2, VERSION);
            assertEquals(2, result.versionId());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should set tenant ID on updated entity")
        void shouldSetTenantIdOnUpdatedEntity() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            when(schemaRoutingRepository.findMaxVersionId(TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(1);
            when(schemaRoutingRepository.save(eq(RESOURCE_TYPE), any(FhirResourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            service.update(RESOURCE_TYPE, resourceId, PATIENT_JSON, VERSION);

            ArgumentCaptor<FhirResourceEntity> captor = ArgumentCaptor.forClass(FhirResourceEntity.class);
            verify(schemaRoutingRepository).save(eq(RESOURCE_TYPE), captor.capture());
            assertEquals(TENANT_A, captor.getValue().getTenantId());
            assertEquals(2, captor.getValue().getVersionId());
        }

        @Test
        @DisplayName("should query max version with correct tenant ID")
        void shouldQueryMaxVersionWithTenantId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            when(schemaRoutingRepository.findMaxVersionId(TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(null);
            when(schemaRoutingRepository.save(eq(RESOURCE_TYPE), any(FhirResourceEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            service.update(RESOURCE_TYPE, resourceId, PATIENT_JSON, VERSION);

            verify(schemaRoutingRepository).findMaxVersionId(TENANT_A, RESOURCE_TYPE, resourceId);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should check existence with correct tenant ID")
        void shouldCheckExistenceWithTenantId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            when(schemaRoutingRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(true);
            when(schemaRoutingRepository.findMaxVersionId(TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(1);

            service.delete(RESOURCE_TYPE, resourceId, VERSION);

            verify(schemaRoutingRepository).softDelete(eq(TENANT_A), eq(RESOURCE_TYPE), eq(resourceId), any(Instant.class));
        }

        @Test
        @DisplayName("should not delete resource from different tenant")
        void shouldNotDeleteFromDifferentTenant() {
            TenantContext.setCurrentTenantId(TENANT_B);
            String resourceId = "test-id";
            when(schemaRoutingRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_B, RESOURCE_TYPE, resourceId))
                    .thenReturn(false);

            assertThrows(ResourceNotFoundException.class,
                    () -> service.delete(RESOURCE_TYPE, resourceId, VERSION));
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("should search with correct tenant ID")
        void shouldSearchWithTenantId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            when(schemaRoutingRepository.searchWithParams(
                    eq(TENANT_A), eq(RESOURCE_TYPE), anyMap(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            service.search(RESOURCE_TYPE, Map.of(), VERSION, 20);

            verify(schemaRoutingRepository).searchWithParams(
                    eq(TENANT_A), eq(RESOURCE_TYPE), anyMap(), any(Pageable.class));
        }

        @Test
        @DisplayName("should return only tenant-specific results")
        void shouldReturnOnlyTenantResults() {
            TenantContext.setCurrentTenantId(TENANT_A);
            FhirResourceEntity entity = buildEntity("id-1", TENANT_A, 1, false);
            when(schemaRoutingRepository.searchWithParams(
                    eq(TENANT_A), eq(RESOURCE_TYPE), anyMap(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(entity)));

            var bundle = service.search(RESOURCE_TYPE, Map.of(), VERSION, 20);
            assertEquals(1, bundle.getTotal());
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        @Test
        @DisplayName("should return history for correct tenant only")
        void shouldReturnHistoryForCorrectTenant() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            FhirResourceEntity v1 = buildEntity(resourceId, TENANT_A, 1, false);
            FhirResourceEntity v2 = buildEntity(resourceId, TENANT_A, 2, false);
            when(schemaRoutingRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                    TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(List.of(v2, v1));

            var bundle = service.history(RESOURCE_TYPE, resourceId, VERSION);
            assertEquals(2, bundle.getTotal());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for different tenant's resource")
        void shouldThrowForDifferentTenantResource() {
            TenantContext.setCurrentTenantId(TENANT_B);
            String resourceId = "test-id";
            when(schemaRoutingRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                    TENANT_B, RESOURCE_TYPE, resourceId))
                    .thenReturn(Collections.emptyList());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.history(RESOURCE_TYPE, resourceId, VERSION));
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("should check existence with correct tenant ID")
        void shouldCheckExistenceWithTenantId() {
            TenantContext.setCurrentTenantId(TENANT_A);
            String resourceId = "test-id";
            when(schemaRoutingRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_A, RESOURCE_TYPE, resourceId))
                    .thenReturn(true);

            assertTrue(service.exists(RESOURCE_TYPE, resourceId));
        }

        @Test
        @DisplayName("should return false for resource in different tenant")
        void shouldReturnFalseForDifferentTenant() {
            TenantContext.setCurrentTenantId(TENANT_B);
            String resourceId = "test-id";
            when(schemaRoutingRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    TENANT_B, RESOURCE_TYPE, resourceId))
                    .thenReturn(false);

            assertFalse(service.exists(RESOURCE_TYPE, resourceId));
        }
    }

    private FhirResourceEntity buildEntity(String resourceId, String tenantId, int versionId, boolean isDeleted) {
        Patient patient = new Patient();
        patient.setId("Patient/" + resourceId);
        patient.setActive(true);
        patient.addName().setFamily("Smith").addGiven("John");
        FhirContext ctx = FhirContext.forR5();
        String json = ctx.newJsonParser().encodeResourceToString(patient);

        return FhirResourceEntity.builder()
                .resourceType(RESOURCE_TYPE)
                .resourceId(resourceId)
                .fhirVersion("5.0.0")
                .versionId(versionId)
                .isCurrent(true)
                .isDeleted(isDeleted)
                .content(json)
                .lastUpdated(Instant.now())
                .tenantId(tenantId)
                .build();
    }
}
