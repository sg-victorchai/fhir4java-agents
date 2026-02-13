package org.fhirframework.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaConfigTest {

    @Test
    @DisplayName("Should return configured name for dedicated schema")
    void getEffectiveSchemaName_DedicatedWithName_ReturnsConfiguredName() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName("careplan");

        assertEquals("careplan", config.getEffectiveSchemaName());
    }

    @Test
    @DisplayName("Should return default fhir for shared schema")
    void getEffectiveSchemaName_Shared_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("shared");
        config.setName("fhir");

        assertEquals("fhir", config.getEffectiveSchemaName());
    }

    @Test
    @DisplayName("Should return default fhir when dedicated but no name")
    void getEffectiveSchemaName_DedicatedNoName_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName(null);

        assertEquals("fhir", config.getEffectiveSchemaName());
    }

    @Test
    @DisplayName("Should return default fhir when dedicated with blank name")
    void getEffectiveSchemaName_DedicatedBlankName_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName("   ");

        assertEquals("fhir", config.getEffectiveSchemaName());
    }

    @Test
    @DisplayName("isDedicated should return true for dedicated type")
    void isDedicated_DedicatedType_ReturnsTrue() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");

        assertTrue(config.isDedicated());
    }

    @Test
    @DisplayName("isDedicated should return false for shared type")
    void isDedicated_SharedType_ReturnsFalse() {
        SchemaConfig config = new SchemaConfig();
        config.setType("shared");

        assertFalse(config.isDedicated());
    }

    @Test
    @DisplayName("isShared should return true for shared type")
    void isShared_SharedType_ReturnsTrue() {
        SchemaConfig config = new SchemaConfig();
        config.setType("shared");

        assertTrue(config.isShared());
    }
}
