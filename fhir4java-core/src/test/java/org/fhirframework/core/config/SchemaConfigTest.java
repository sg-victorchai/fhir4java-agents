package org.fhirframework.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaConfigTest {

    private static final String DEFAULT_SCHEMA = "fhir";

    @Test
    @DisplayName("Should return configured name for dedicated schema")
    void getEffectiveSchemaName_DedicatedWithName_ReturnsConfiguredName() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName("careplan");

        assertEquals("careplan", config.getEffectiveSchemaName(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("Should return default fhir for shared schema")
    void getEffectiveSchemaName_Shared_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("shared");
        config.setName("fhir");

        assertEquals("fhir", config.getEffectiveSchemaName(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("Should return default fhir when dedicated but no name")
    void getEffectiveSchemaName_DedicatedNoName_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName(null);

        assertEquals("fhir", config.getEffectiveSchemaName(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("Should return default fhir when dedicated with blank name")
    void getEffectiveSchemaName_DedicatedBlankName_ReturnsDefaultFhir() {
        SchemaConfig config = new SchemaConfig();
        config.setType("dedicated");
        config.setName("   ");

        assertEquals("fhir", config.getEffectiveSchemaName(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("Should return custom default schema when provided")
    void getEffectiveSchemaName_CustomDefault_ReturnsCustomDefault() {
        SchemaConfig config = new SchemaConfig();
        config.setType("shared");
        config.setName(null);

        assertEquals("fhircommon", config.getEffectiveSchemaName("fhircommon"));
    }

    @Test
    @DisplayName("isNonDefaultSchema returns true when schema differs from default")
    void isNonDefaultSchema_DifferentSchema_ReturnsTrue() {
        SchemaConfig config = new SchemaConfig();
        config.setName("careplan");

        assertTrue(config.isNonDefaultSchema(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("isNonDefaultSchema returns false when schema matches default")
    void isNonDefaultSchema_SameAsDefault_ReturnsFalse() {
        SchemaConfig config = new SchemaConfig();
        config.setName("fhir");

        assertFalse(config.isNonDefaultSchema(DEFAULT_SCHEMA));
    }

    @Test
    @DisplayName("isNonDefaultSchema is case insensitive")
    void isNonDefaultSchema_CaseInsensitive_ReturnsFalse() {
        SchemaConfig config = new SchemaConfig();
        config.setName("FHIR");

        assertFalse(config.isNonDefaultSchema(DEFAULT_SCHEMA));
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
