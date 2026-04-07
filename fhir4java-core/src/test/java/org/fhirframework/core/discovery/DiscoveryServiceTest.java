package org.fhirframework.core.discovery;

import org.fhirframework.core.config.FhirVersionConfig;
import org.fhirframework.core.config.InteractionsConfig;
import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationRegistry;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.SearchParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private ResourceRegistry resourceRegistry;

    @Mock
    private SearchParameterRegistry searchParameterRegistry;

    @Mock
    private OperationRegistry operationRegistry;

    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(resourceRegistry, searchParameterRegistry, operationRegistry);
    }

    @Nested
    @DisplayName("Resource Discovery")
    class ResourceDiscoveryTests {

        @Test
        @DisplayName("Should return enabled resources when discovering RESOURCES topic")
        void discover_ResourcesTopic_ReturnsEnabledResources() {
            // Arrange
            ResourceConfiguration patientConfig = createResourceConfig("Patient", true, FhirVersion.R5);
            ResourceConfiguration observationConfig = createResourceConfig("Observation", true, FhirVersion.R5, FhirVersion.R4B);
            when(resourceRegistry.getEnabledResources()).thenReturn(List.of(patientConfig, observationConfig));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.RESOURCES, null, null);

            // Assert
            assertNotNull(response);
            assertEquals(2, response.resources().size());

            DiscoveryResponse.ResourceInfo patient = response.resources().stream()
                    .filter(r -> r.resourceType().equals("Patient"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Patient", patient.resourceType());
            assertTrue(patient.enabled());
            assertTrue(patient.supportedVersions().contains(FhirVersion.R5));

            DiscoveryResponse.ResourceInfo observation = response.resources().stream()
                    .filter(r -> r.resourceType().equals("Observation"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(observation.supportedVersions().contains(FhirVersion.R5));
            assertTrue(observation.supportedVersions().contains(FhirVersion.R4B));
        }

        @Test
        @DisplayName("Should filter resources by FHIR version when version is specified")
        void discover_ResourcesTopicWithVersion_ReturnsFilteredResources() {
            // Arrange
            ResourceConfiguration patientConfig = createResourceConfig("Patient", true, FhirVersion.R5);
            ResourceConfiguration observationConfig = createResourceConfig("Observation", true, FhirVersion.R5, FhirVersion.R4B);
            when(resourceRegistry.getResourcesForVersion(FhirVersion.R4B)).thenReturn(List.of(observationConfig));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.RESOURCES, null, FhirVersion.R4B);

            // Assert
            assertEquals(1, response.resources().size());
            assertEquals("Observation", response.resources().get(0).resourceType());
        }

        @Test
        @DisplayName("Should include interaction types for resources")
        void discover_ResourcesTopic_IncludesInteractions() {
            // Arrange
            ResourceConfiguration patientConfig = createResourceConfigWithInteractions("Patient", true);
            when(resourceRegistry.getEnabledResources()).thenReturn(List.of(patientConfig));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.RESOURCES, null, null);

            // Assert
            assertNotNull(response);
            assertFalse(response.resources().isEmpty());
            assertFalse(response.resources().get(0).interactions().isEmpty());
        }
    }

    @Nested
    @DisplayName("Search Parameter Discovery")
    class SearchParameterDiscoveryTests {

        @Test
        @DisplayName("Should return search parameters for specified resource type")
        void discover_SearchParamsTopic_ReturnsSearchParameters() {
            // Arrange
            SearchParameter identifierParam = createSearchParameter("identifier", "token", "Patient.identifier");
            SearchParameter familyParam = createSearchParameter("family", "string", "Patient.name.family");
            when(searchParameterRegistry.getSearchParameters(FhirVersion.R5, "Patient"))
                    .thenReturn(List.of(identifierParam, familyParam));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.SEARCH_PARAMS, "Patient", FhirVersion.R5);

            // Assert
            assertNotNull(response);
            assertEquals(2, response.searchParameters().size());

            DiscoveryResponse.SearchParameterInfo identifier = response.searchParameters().stream()
                    .filter(sp -> sp.name().equals("identifier"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("token", identifier.type());
            assertEquals("Patient.identifier", identifier.expression());
        }

        @Test
        @DisplayName("Should return empty list when no resource type specified")
        void discover_SearchParamsTopicNoResourceType_ReturnsEmpty() {
            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.SEARCH_PARAMS, null, FhirVersion.R5);

            // Assert
            assertNotNull(response);
            assertTrue(response.searchParameters().isEmpty());
        }

        @Test
        @DisplayName("Should use default version when version not specified")
        void discover_SearchParamsTopicNoVersion_UsesDefaultVersion() {
            // Arrange
            SearchParameter param = createSearchParameter("_id", "token", "Resource.id");
            when(searchParameterRegistry.getSearchParameters(FhirVersion.R5, "Patient"))
                    .thenReturn(List.of(param));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.SEARCH_PARAMS, "Patient", null);

            // Assert
            verify(searchParameterRegistry).getSearchParameters(FhirVersion.R5, "Patient");
            assertFalse(response.searchParameters().isEmpty());
        }
    }

    @Nested
    @DisplayName("Operation Discovery")
    class OperationDiscoveryTests {

        @Test
        @DisplayName("Should return registered operations")
        void discover_OperationsTopic_ReturnsOperations() {
            // Arrange
            OperationHandler validateHandler = createOperationHandler("validate",
                    new String[]{"Patient", "Observation"},
                    new OperationScope[]{OperationScope.TYPE, OperationScope.INSTANCE});
            OperationHandler everythingHandler = createOperationHandler("everything",
                    new String[]{"Patient"},
                    new OperationScope[]{OperationScope.INSTANCE});
            when(operationRegistry.getRegisteredOperations()).thenReturn(Set.of("validate", "everything"));
            when(operationRegistry.getHandlers(any(), any())).thenReturn(List.of());

            // Mock finding handlers for different scopes
            when(operationRegistry.getHandlers(OperationScope.SYSTEM, null)).thenReturn(List.of());
            when(operationRegistry.getHandlers(OperationScope.TYPE, null)).thenReturn(List.of(validateHandler));
            when(operationRegistry.getHandlers(OperationScope.INSTANCE, null)).thenReturn(List.of(validateHandler, everythingHandler));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.OPERATIONS, null, null);

            // Assert
            assertNotNull(response);
            assertFalse(response.operations().isEmpty());
        }

        @Test
        @DisplayName("Should return empty operations when no handlers registered")
        void discover_OperationsTopic_NoHandlers_ReturnsEmpty() {
            // Arrange
            when(operationRegistry.getRegisteredOperations()).thenReturn(Set.of());

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.OPERATIONS, null, null);

            // Assert
            assertNotNull(response);
            assertTrue(response.operations().isEmpty());
        }
    }

    @Nested
    @DisplayName("ALL Topic Discovery")
    class AllTopicDiscoveryTests {

        @Test
        @DisplayName("Should return combined data for ALL topic")
        void discover_AllTopic_ReturnsCombinedData() {
            // Arrange
            ResourceConfiguration patientConfig = createResourceConfig("Patient", true, FhirVersion.R5);
            // When version is specified, getResourcesForVersion is called instead of getEnabledResources
            when(resourceRegistry.getResourcesForVersion(FhirVersion.R5)).thenReturn(List.of(patientConfig));

            SearchParameter param = createSearchParameter("identifier", "token", "Patient.identifier");
            when(searchParameterRegistry.getSearchParameters(FhirVersion.R5, "Patient"))
                    .thenReturn(List.of(param));

            when(operationRegistry.getRegisteredOperations()).thenReturn(Set.of("validate"));
            OperationHandler handler = createOperationHandler("validate", new String[]{"Patient"}, new OperationScope[]{OperationScope.TYPE});
            when(operationRegistry.getHandlers(any(), any())).thenReturn(List.of(handler));

            // Act
            DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.ALL, "Patient", FhirVersion.R5);

            // Assert
            assertNotNull(response);
            assertFalse(response.resources().isEmpty());
            assertFalse(response.searchParameters().isEmpty());
            assertFalse(response.operations().isEmpty());
        }
    }

    // Helper methods

    private ResourceConfiguration createResourceConfig(String resourceType, boolean enabled, FhirVersion... versions) {
        ResourceConfiguration config = new ResourceConfiguration();
        config.setResourceType(resourceType);
        config.setEnabled(enabled);

        List<FhirVersionConfig> versionConfigs = new java.util.ArrayList<>();
        for (int i = 0; i < versions.length; i++) {
            FhirVersionConfig versionConfig = new FhirVersionConfig();
            versionConfig.setVersion(versions[i].name());
            versionConfig.setDefault(i == 0);
            versionConfigs.add(versionConfig);
        }
        config.setFhirVersions(versionConfigs);

        return config;
    }

    private ResourceConfiguration createResourceConfigWithInteractions(String resourceType, boolean enabled) {
        ResourceConfiguration config = createResourceConfig(resourceType, enabled, FhirVersion.R5);
        InteractionsConfig interactions = new InteractionsConfig();
        interactions.setRead(true);
        interactions.setCreate(true);
        interactions.setUpdate(true);
        interactions.setSearch(true);
        config.setInteractions(interactions);
        return config;
    }

    private SearchParameter createSearchParameter(String code, String type, String expression) {
        SearchParameter sp = new SearchParameter();
        sp.setCode(code);
        sp.setType(Enumerations.SearchParamType.fromCode(type));
        sp.setExpression(expression);
        sp.setDescription("Search parameter for " + code);
        return sp;
    }

    private OperationHandler createOperationHandler(String name, String[] resourceTypes, OperationScope[] scopes) {
        OperationHandler handler = mock(OperationHandler.class);
        when(handler.getOperationName()).thenReturn(name);
        when(handler.getResourceTypes()).thenReturn(resourceTypes);
        when(handler.getScopes()).thenReturn(scopes);
        return handler;
    }
}
