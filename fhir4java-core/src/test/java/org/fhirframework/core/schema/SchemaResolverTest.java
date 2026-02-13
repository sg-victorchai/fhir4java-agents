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

    @Mock
    private ResourceRegistry resourceRegistry;

    private SchemaResolver schemaResolver;

    @BeforeEach
    void setUp() {
        schemaResolver = new SchemaResolver(resourceRegistry);
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
        assertEquals("fhir", schema);
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
    @DisplayName("Should return correct default schema name")
    void getDefaultSchema_ReturnsCorrectValue() {
        // Act & Assert
        assertEquals("fhir", schemaResolver.getDefaultSchema());
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
