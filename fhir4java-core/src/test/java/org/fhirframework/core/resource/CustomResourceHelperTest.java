package org.fhirframework.core.resource;

import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.version.FhirVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomResourceHelper")
class CustomResourceHelperTest {

    @Mock
    private FhirContextFactory contextFactory;

    @Mock
    private ResourceRegistry resourceRegistry;

    private CustomResourceHelper helper;

    @BeforeEach
    void setUp() {
        helper = new CustomResourceHelper(contextFactory, resourceRegistry);
    }

    @Nested
    @DisplayName("isCustomResource")
    class IsCustomResource {

        @Test
        @DisplayName("should return false for unconfigured resource")
        void shouldReturnFalseForUnconfiguredResource() {
            when(resourceRegistry.isResourceConfigured("Unknown")).thenReturn(false);

            boolean result = helper.isCustomResource("Unknown", FhirVersion.R5);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for standard FHIR resource like Patient")
        void shouldReturnFalseForStandardResource() {
            when(resourceRegistry.isResourceConfigured("Patient")).thenReturn(true);
            when(contextFactory.getContext(FhirVersion.R5))
                    .thenReturn(ca.uhn.fhir.context.FhirContext.forR5());

            boolean result = helper.isCustomResource("Patient", FhirVersion.R5);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return true for configured custom resource")
        void shouldReturnTrueForConfiguredCustomResource() {
            when(resourceRegistry.isResourceConfigured("MedicationInventory")).thenReturn(true);
            when(contextFactory.getContext(FhirVersion.R5))
                    .thenReturn(ca.uhn.fhir.context.FhirContext.forR5());

            boolean result = helper.isCustomResource("MedicationInventory", FhirVersion.R5);

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("isKnownToHapi")
    class IsKnownToHapi {

        @Test
        @DisplayName("should return true for Patient")
        void shouldReturnTrueForPatient() {
            when(contextFactory.getContext(FhirVersion.R5))
                    .thenReturn(ca.uhn.fhir.context.FhirContext.forR5());

            boolean result = helper.isKnownToHapi("Patient", FhirVersion.R5);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return true for Observation")
        void shouldReturnTrueForObservation() {
            when(contextFactory.getContext(FhirVersion.R5))
                    .thenReturn(ca.uhn.fhir.context.FhirContext.forR5());

            boolean result = helper.isKnownToHapi("Observation", FhirVersion.R5);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false for unknown type")
        void shouldReturnFalseForUnknownType() {
            when(contextFactory.getContext(FhirVersion.R5))
                    .thenReturn(ca.uhn.fhir.context.FhirContext.forR5());

            boolean result = helper.isKnownToHapi("MedicationInventory", FhirVersion.R5);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("extractResourceType")
    class ExtractResourceType {

        @Test
        @DisplayName("should extract resourceType from valid JSON")
        void shouldExtractResourceType() {
            String json = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";

            String result = helper.extractResourceType(json);

            assertEquals("Patient", result);
        }

        @Test
        @DisplayName("should return null for missing resourceType")
        void shouldReturnNullForMissingResourceType() {
            String json = "{\"id\":\"123\"}";

            String result = helper.extractResourceType(json);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for invalid JSON")
        void shouldReturnNullForInvalidJson() {
            String json = "not valid json";

            String result = helper.extractResourceType(json);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("validateBasicStructure")
    class ValidateBasicStructure {

        @Test
        @DisplayName("should return true for valid structure")
        void shouldReturnTrueForValidStructure() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"123\"}";

            boolean result = helper.validateBasicStructure(json, "MedicationInventory");

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false for resourceType mismatch")
        void shouldReturnFalseForMismatch() {
            String json = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";

            boolean result = helper.validateBasicStructure(json, "MedicationInventory");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for missing resourceType")
        void shouldReturnFalseForMissingResourceType() {
            String json = "{\"id\":\"123\"}";

            boolean result = helper.validateBasicStructure(json, "MedicationInventory");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for non-object JSON")
        void shouldReturnFalseForNonObject() {
            String json = "[1,2,3]";

            boolean result = helper.validateBasicStructure(json, "MedicationInventory");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for invalid JSON")
        void shouldReturnFalseForInvalidJson() {
            String json = "not valid";

            boolean result = helper.validateBasicStructure(json, "MedicationInventory");

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("ensureId")
    class EnsureId {

        @Test
        @DisplayName("should generate ID when missing")
        void shouldGenerateIdWhenMissing() {
            String json = "{\"resourceType\":\"MedicationInventory\"}";

            String result = helper.ensureId(json);

            assertNotNull(result);
            assertTrue(result.contains("\"id\":"));
        }

        @Test
        @DisplayName("should preserve existing ID")
        void shouldPreserveExistingId() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"my-id\"}";

            String result = helper.ensureId(json);

            assertTrue(result.contains("\"id\":\"my-id\""));
        }
    }

    @Nested
    @DisplayName("extractId")
    class ExtractId {

        @Test
        @DisplayName("should extract ID from JSON")
        void shouldExtractId() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"123\"}";

            String result = helper.extractId(json);

            assertEquals("123", result);
        }

        @Test
        @DisplayName("should return null for missing ID")
        void shouldReturnNullForMissingId() {
            String json = "{\"resourceType\":\"MedicationInventory\"}";

            String result = helper.extractId(json);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("setId")
    class SetId {

        @Test
        @DisplayName("should set ID in JSON")
        void shouldSetId() {
            String json = "{\"resourceType\":\"MedicationInventory\"}";

            String result = helper.setId(json, "new-id");

            assertTrue(result.contains("\"id\":\"new-id\""));
        }

        @Test
        @DisplayName("should replace existing ID")
        void shouldReplaceExistingId() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"old-id\"}";

            String result = helper.setId(json, "new-id");

            assertTrue(result.contains("\"id\":\"new-id\""));
            assertFalse(result.contains("old-id"));
        }
    }

    @Nested
    @DisplayName("updateMeta")
    class UpdateMeta {

        @Test
        @DisplayName("should add meta element when missing")
        void shouldAddMeta() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"123\"}";

            String result = helper.updateMeta(json, 1, "2024-01-01T00:00:00Z");

            assertTrue(result.contains("\"meta\""));
            assertTrue(result.contains("\"versionId\":\"1\""));
            assertTrue(result.contains("\"lastUpdated\":\"2024-01-01T00:00:00Z\""));
        }

        @Test
        @DisplayName("should update existing meta element")
        void shouldUpdateExistingMeta() {
            String json = "{\"resourceType\":\"MedicationInventory\",\"id\":\"123\",\"meta\":{\"versionId\":\"1\"}}";

            String result = helper.updateMeta(json, 2, "2024-01-02T00:00:00Z");

            assertTrue(result.contains("\"versionId\":\"2\""));
            assertTrue(result.contains("\"lastUpdated\":\"2024-01-02T00:00:00Z\""));
        }
    }
}
