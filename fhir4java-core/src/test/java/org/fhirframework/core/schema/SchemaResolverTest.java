package org.fhirframework.core.schema;

import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.config.SchemaConfig;
import org.fhirframework.core.resource.ResourceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaResolverTest {

    private static final String DEFAULT_SCHEMA = "fhir";

    @Mock
    private ResourceRegistry resourceRegistry;

    private SchemaResolver schemaResolver;

    @BeforeEach
    void setUp() {
        schemaResolver = new SchemaResolver(resourceRegistry, DEFAULT_SCHEMA);
    }

    @Test
    @DisplayName("Should return dedicated schema name for dedicated resource type")
    void resolveSchema_DedicatedResource_ReturnsDedicatedSchema() {
        // Arrange
        ResourceConfiguration carePlanConfig = createResourceConfig("CarePlan", "dedicated", "careplan");
        when(resourceRegistry.getResource("CarePlan")).thenReturn(Optional.of(carePlanConfig));

        // Act
        String schema = schemaResolver.resolveSchema("CarePlan");

        // Assert
        assertEquals("careplan", schema);
    }

    @Test
    @DisplayName("Should return default schema for shared resource type")
    void resolveSchema_SharedResource_ReturnsDefaultSchema() {
        // Arrange
        ResourceConfiguration patientConfig = createResourceConfig("Patient", "shared", "fhir");
        when(resourceRegistry.getResource("Patient")).thenReturn(Optional.of(patientConfig));

        // Act
        String schema = schemaResolver.resolveSchema("Patient");

        // Assert
        assertEquals("fhir", schema);
    }

    @Test
    @DisplayName("Should return default schema for unknown resource type")
    void resolveSchema_UnknownResource_ReturnsDefaultSchema() {
        // Arrange
        when(resourceRegistry.getResource("Unknown")).thenReturn(Optional.empty());

        // Act
        String schema = schemaResolver.resolveSchema("Unknown");

        // Assert
        assertEquals(DEFAULT_SCHEMA, schema);
    }

    @Test
    @DisplayName("Should return custom schema for shared resource with custom schema name")
    void resolveSchema_SharedResourceWithCustomSchema_ReturnsCustomSchema() {
        // Arrange
        ResourceConfiguration patientConfig = createResourceConfig("Patient", "shared", "masterdata");
        when(resourceRegistry.getResource("Patient")).thenReturn(Optional.of(patientConfig));

        // Act
        String schema = schemaResolver.resolveSchema("Patient");

        // Assert
        assertEquals("masterdata", schema);
    }

    @Test
    @DisplayName("Should require schema switch for non-default schema")
    void requiresSchemaSwitch_NonDefaultSchema_ReturnsTrue() {
        // Arrange
        ResourceConfiguration carePlanConfig = createResourceConfig("CarePlan", "dedicated", "careplan");
        when(resourceRegistry.getResource("CarePlan")).thenReturn(Optional.of(carePlanConfig));

        // Act
        boolean requiresSwitch = schemaResolver.requiresSchemaSwitch("CarePlan");

        // Assert
        assertTrue(requiresSwitch);
    }

    @Test
    @DisplayName("Should not require schema switch for default schema")
    void requiresSchemaSwitch_DefaultSchema_ReturnsFalse() {
        // Arrange
        ResourceConfiguration patientConfig = createResourceConfig("Patient", "shared", "fhir");
        when(resourceRegistry.getResource("Patient")).thenReturn(Optional.of(patientConfig));

        // Act
        boolean requiresSwitch = schemaResolver.requiresSchemaSwitch("Patient");

        // Assert
        assertFalse(requiresSwitch);
    }

    @Test
    @DisplayName("Should not require schema switch for unknown resource")
    void requiresSchemaSwitch_UnknownResource_ReturnsFalse() {
        // Arrange
        when(resourceRegistry.getResource("Unknown")).thenReturn(Optional.empty());

        // Act
        boolean requiresSwitch = schemaResolver.requiresSchemaSwitch("Unknown");

        // Assert
        assertFalse(requiresSwitch);
    }

    @Test
    @DisplayName("Should identify dedicated schema resource correctly")
    void isDedicatedSchema_DedicatedResource_ReturnsTrue() {
        // Arrange
        ResourceConfiguration carePlanConfig = createResourceConfig("CarePlan", "dedicated", "careplan");
        when(resourceRegistry.getResource("CarePlan")).thenReturn(Optional.of(carePlanConfig));

        // Act
        boolean isDedicated = schemaResolver.isDedicatedSchema("CarePlan");

        // Assert
        assertTrue(isDedicated);
    }

    @Test
    @DisplayName("Should identify shared schema resource correctly")
    void isDedicatedSchema_SharedResource_ReturnsFalse() {
        // Arrange
        ResourceConfiguration patientConfig = createResourceConfig("Patient", "shared", "fhir");
        when(resourceRegistry.getResource("Patient")).thenReturn(Optional.of(patientConfig));

        // Act
        boolean isDedicated = schemaResolver.isDedicatedSchema("Patient");

        // Assert
        assertFalse(isDedicated);
    }

    @Test
    @DisplayName("Should return false for unknown resource type")
    void isDedicatedSchema_UnknownResource_ReturnsFalse() {
        // Arrange
        when(resourceRegistry.getResource("Unknown")).thenReturn(Optional.empty());

        // Act
        boolean isDedicated = schemaResolver.isDedicatedSchema("Unknown");

        // Assert
        assertFalse(isDedicated);
    }

    @Test
    @DisplayName("Should return injected default schema name")
    void getDefaultSchema_ReturnsInjectedValue() {
        // Act & Assert
        assertEquals(DEFAULT_SCHEMA, schemaResolver.getDefaultSchema());
    }

    @Test
    @DisplayName("Should use custom default schema when configured")
    void getDefaultSchema_CustomDefault_ReturnsCustomValue() {
        // Arrange
        SchemaResolver customResolver = new SchemaResolver(resourceRegistry, "fhircommon");

        // Act & Assert
        assertEquals("fhircommon", customResolver.getDefaultSchema());
    }

    private ResourceConfiguration createResourceConfig(String resourceType, String schemaType, String schemaName) {
        SchemaConfig schemaConfig = new SchemaConfig();
        schemaConfig.setType(schemaType);
        schemaConfig.setName(schemaName);

        ResourceConfiguration config = new ResourceConfiguration();
        config.setResourceType(resourceType);
        config.setSchema(schemaConfig);

        return config;
    }
}
