package org.fhirframework.core.conformance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConformanceResourceType enum.
 */
class ConformanceResourceTypeTest {

    @Test
    void shouldReturnCorrectResourceTypeName() {
        assertEquals("StructureDefinition", ConformanceResourceType.STRUCTURE_DEFINITION.getResourceTypeName());
        assertEquals("SearchParameter", ConformanceResourceType.SEARCH_PARAMETER.getResourceTypeName());
        assertEquals("OperationDefinition", ConformanceResourceType.OPERATION_DEFINITION.getResourceTypeName());
    }

    @Test
    void shouldReturnCorrectDirectoryName() {
        assertEquals("profiles", ConformanceResourceType.STRUCTURE_DEFINITION.getDirectoryName());
        assertEquals("searchparameters", ConformanceResourceType.SEARCH_PARAMETER.getDirectoryName());
        assertEquals("operations", ConformanceResourceType.OPERATION_DEFINITION.getDirectoryName());
    }

    @Test
    void shouldReturnCorrectFilePrefix() {
        assertEquals("StructureDefinition-", ConformanceResourceType.STRUCTURE_DEFINITION.getFilePrefix());
        assertEquals("SearchParameter-", ConformanceResourceType.SEARCH_PARAMETER.getFilePrefix());
        assertEquals("OperationDefinition-", ConformanceResourceType.OPERATION_DEFINITION.getFilePrefix());
    }

    @Test
    void shouldReturnCorrectFilePattern() {
        assertEquals("StructureDefinition-*.json", ConformanceResourceType.STRUCTURE_DEFINITION.getFilePattern());
        assertEquals("SearchParameter-*.json", ConformanceResourceType.SEARCH_PARAMETER.getFilePattern());
        assertEquals("OperationDefinition-*.json", ConformanceResourceType.OPERATION_DEFINITION.getFilePattern());
    }

    @Test
    void shouldExtractIdFromFilename() {
        assertEquals("Patient",
                ConformanceResourceType.STRUCTURE_DEFINITION.extractId("StructureDefinition-Patient.json"));
        assertEquals("Patient-identifier",
                ConformanceResourceType.SEARCH_PARAMETER.extractId("SearchParameter-Patient-identifier.json"));
        assertEquals("Resource-validate",
                ConformanceResourceType.OPERATION_DEFINITION.extractId("OperationDefinition-Resource-validate.json"));
    }

    @Test
    void shouldReturnNullForInvalidFilename() {
        assertNull(ConformanceResourceType.STRUCTURE_DEFINITION.extractId("InvalidFile.json"));
        assertNull(ConformanceResourceType.STRUCTURE_DEFINITION.extractId("StructureDefinition-Patient.txt"));
        assertNull(ConformanceResourceType.STRUCTURE_DEFINITION.extractId(null));
    }

    @Test
    void shouldFindTypeFromResourceTypeName() {
        assertEquals(ConformanceResourceType.STRUCTURE_DEFINITION,
                ConformanceResourceType.fromResourceTypeName("StructureDefinition"));
        assertEquals(ConformanceResourceType.SEARCH_PARAMETER,
                ConformanceResourceType.fromResourceTypeName("SearchParameter"));
        assertEquals(ConformanceResourceType.OPERATION_DEFINITION,
                ConformanceResourceType.fromResourceTypeName("OperationDefinition"));
    }

    @Test
    void shouldReturnNullForUnknownResourceTypeName() {
        assertNull(ConformanceResourceType.fromResourceTypeName("Patient"));
        assertNull(ConformanceResourceType.fromResourceTypeName("Unknown"));
        assertNull(ConformanceResourceType.fromResourceTypeName(null));
    }

    @Test
    void shouldIdentifyConformanceResources() {
        assertTrue(ConformanceResourceType.isConformanceResource("StructureDefinition"));
        assertTrue(ConformanceResourceType.isConformanceResource("SearchParameter"));
        assertTrue(ConformanceResourceType.isConformanceResource("OperationDefinition"));

        assertFalse(ConformanceResourceType.isConformanceResource("Patient"));
        assertFalse(ConformanceResourceType.isConformanceResource("Observation"));
        assertFalse(ConformanceResourceType.isConformanceResource(null));
    }
}
