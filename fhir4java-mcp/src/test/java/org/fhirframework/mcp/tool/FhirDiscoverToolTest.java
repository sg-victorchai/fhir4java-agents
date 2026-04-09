package org.fhirframework.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.core.discovery.DiscoveryResponse;
import org.fhirframework.core.discovery.DiscoveryService;
import org.fhirframework.core.discovery.DiscoveryTopic;
import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.hint.ResponseHintGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FhirDiscoverTool}.
 */
@ExtendWith(MockitoExtension.class)
class FhirDiscoverToolTest {

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private ResponseHintGenerator hintGenerator;

    private FhirDiscoverTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new FhirDiscoverTool(discoveryService, hintGenerator);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Tool metadata")
    class MetadataTests {

        @Test
        @DisplayName("should have correct tool name")
        void shouldHaveCorrectName() {
            assertEquals("fhir_discover", tool.getName());
        }

        @Test
        @DisplayName("should have descriptive description")
        void shouldHaveDescription() {
            String description = tool.getDescription();
            assertNotNull(description);
            assertTrue(description.contains("Discover"));
            assertTrue(description.toLowerCase().contains("capabilities"));
        }

        @Test
        @DisplayName("should have valid input schema with topic enum")
        void shouldHaveValidInputSchema() {
            Map<String, Object> schema = tool.getInputSchema();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertNotNull(properties);

            // Check topic property
            @SuppressWarnings("unchecked")
            Map<String, Object> topicProp = (Map<String, Object>) properties.get("topic");
            assertNotNull(topicProp);
            assertEquals("string", topicProp.get("type"));

            @SuppressWarnings("unchecked")
            List<String> topicEnum = (List<String>) topicProp.get("enum");
            assertNotNull(topicEnum);
            assertTrue(topicEnum.contains("RESOURCES"));
            assertTrue(topicEnum.contains("SEARCH_PARAMS"));
            assertTrue(topicEnum.contains("OPERATIONS"));
            assertTrue(topicEnum.contains("ALL"));

            // Check resourceType property
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceTypeProp = (Map<String, Object>) properties.get("resourceType");
            assertNotNull(resourceTypeProp);
            assertEquals("string", resourceTypeProp.get("type"));

            // Check fhirVersion property
            @SuppressWarnings("unchecked")
            Map<String, Object> fhirVersionProp = (Map<String, Object>) properties.get("fhirVersion");
            assertNotNull(fhirVersionProp);
            assertEquals("string", fhirVersionProp.get("type"));
            assertEquals("R5", fhirVersionProp.get("default"));

            @SuppressWarnings("unchecked")
            List<String> versionEnum = (List<String>) fhirVersionProp.get("enum");
            assertNotNull(versionEnum);
            assertTrue(versionEnum.contains("R5"));
            assertTrue(versionEnum.contains("R4B"));
        }
    }

    @Nested
    @DisplayName("Resource discovery")
    class ResourceDiscoveryTests {

        @Test
        @DisplayName("should discover resources with default FHIR version")
        void shouldDiscoverResourcesWithDefaultVersion() throws JsonProcessingException {
            // Given
            List<DiscoveryResponse.ResourceInfo> resources = List.of(
                    new DiscoveryResponse.ResourceInfo(
                            "Patient",
                            true,
                            Set.of(FhirVersion.R5),
                            EnumSet.of(InteractionType.READ, InteractionType.CREATE)
                    ),
                    new DiscoveryResponse.ResourceInfo(
                            "Observation",
                            true,
                            Set.of(FhirVersion.R5, FhirVersion.R4B),
                            EnumSet.of(InteractionType.READ, InteractionType.SEARCH)
                    )
            );
            DiscoveryResponse response = DiscoveryResponse.withResources(resources);

            when(discoveryService.discover(eq(DiscoveryTopic.RESOURCES), any(), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            assertNotNull(result.getFirstTextContent());

            // Verify JSON contains resources
            String jsonContent = result.getFirstTextContent();
            assertTrue(jsonContent.contains("Patient"));
            assertTrue(jsonContent.contains("Observation"));

            verify(discoveryService).discover(DiscoveryTopic.RESOURCES, null, FhirVersion.R5);
        }

        @Test
        @DisplayName("should discover resources with explicit R4B version")
        void shouldDiscoverResourcesWithR4BVersion() {
            // Given
            List<DiscoveryResponse.ResourceInfo> resources = List.of(
                    new DiscoveryResponse.ResourceInfo(
                            "Patient",
                            true,
                            Set.of(FhirVersion.R4B),
                            EnumSet.of(InteractionType.READ)
                    )
            );
            DiscoveryResponse response = DiscoveryResponse.withResources(resources);

            when(discoveryService.discover(eq(DiscoveryTopic.RESOURCES), any(), eq(FhirVersion.R4B)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES", "fhirVersion", "R4B")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            verify(discoveryService).discover(DiscoveryTopic.RESOURCES, null, FhirVersion.R4B);
        }
    }

    @Nested
    @DisplayName("Search parameter discovery")
    class SearchParameterDiscoveryTests {

        @Test
        @DisplayName("should discover search parameters for a specific resource type")
        void shouldDiscoverSearchParametersForResourceType() {
            // Given
            List<DiscoveryResponse.SearchParameterInfo> searchParams = List.of(
                    new DiscoveryResponse.SearchParameterInfo(
                            "identifier",
                            "token",
                            "Patient.identifier",
                            "A patient identifier"
                    ),
                    new DiscoveryResponse.SearchParameterInfo(
                            "family",
                            "string",
                            "Patient.name.family",
                            "Family name"
                    )
            );
            DiscoveryResponse response = DiscoveryResponse.withSearchParameters(searchParams);

            when(discoveryService.discover(eq(DiscoveryTopic.SEARCH_PARAMS), eq("Patient"), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "SEARCH_PARAMS", "resourceType", "Patient")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            String jsonContent = result.getFirstTextContent();
            assertTrue(jsonContent.contains("identifier"));
            assertTrue(jsonContent.contains("family"));
            assertTrue(jsonContent.contains("token"));
            assertTrue(jsonContent.contains("string"));

            verify(discoveryService).discover(DiscoveryTopic.SEARCH_PARAMS, "Patient", FhirVersion.R5);
        }

        @Test
        @DisplayName("should return empty list when no resource type provided for search params")
        void shouldReturnEmptyWhenNoResourceTypeForSearchParams() {
            // Given
            DiscoveryResponse response = DiscoveryResponse.withSearchParameters(Collections.emptyList());

            when(discoveryService.discover(eq(DiscoveryTopic.SEARCH_PARAMS), isNull(), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "SEARCH_PARAMS")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            verify(discoveryService).discover(DiscoveryTopic.SEARCH_PARAMS, null, FhirVersion.R5);
        }
    }

    @Nested
    @DisplayName("Operations discovery")
    class OperationsDiscoveryTests {

        @Test
        @DisplayName("should discover available operations")
        void shouldDiscoverOperations() {
            // Given
            List<DiscoveryResponse.OperationInfo> operations = List.of(
                    new DiscoveryResponse.OperationInfo(
                            "validate",
                            List.of("Patient", "Observation"),
                            EnumSet.of(OperationScope.TYPE, OperationScope.INSTANCE)
                    ),
                    new DiscoveryResponse.OperationInfo(
                            "everything",
                            List.of("Patient"),
                            EnumSet.of(OperationScope.INSTANCE)
                    )
            );
            DiscoveryResponse response = DiscoveryResponse.withOperations(operations);

            when(discoveryService.discover(eq(DiscoveryTopic.OPERATIONS), any(), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "OPERATIONS")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            String jsonContent = result.getFirstTextContent();
            assertTrue(jsonContent.contains("validate"));
            assertTrue(jsonContent.contains("everything"));

            verify(discoveryService).discover(DiscoveryTopic.OPERATIONS, null, FhirVersion.R5);
        }

        @Test
        @DisplayName("should discover operations filtered by resource type")
        void shouldDiscoverOperationsFilteredByResourceType() {
            // Given
            List<DiscoveryResponse.OperationInfo> operations = List.of(
                    new DiscoveryResponse.OperationInfo(
                            "everything",
                            List.of("Patient"),
                            EnumSet.of(OperationScope.INSTANCE)
                    )
            );
            DiscoveryResponse response = DiscoveryResponse.withOperations(operations);

            when(discoveryService.discover(eq(DiscoveryTopic.OPERATIONS), eq("Patient"), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "OPERATIONS", "resourceType", "Patient")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            verify(discoveryService).discover(DiscoveryTopic.OPERATIONS, "Patient", FhirVersion.R5);
        }
    }

    @Nested
    @DisplayName("ALL topic discovery")
    class AllTopicDiscoveryTests {

        @Test
        @DisplayName("should discover all topics combined")
        void shouldDiscoverAllTopics() {
            // Given
            List<DiscoveryResponse.ResourceInfo> resources = List.of(
                    new DiscoveryResponse.ResourceInfo(
                            "Patient",
                            true,
                            Set.of(FhirVersion.R5),
                            EnumSet.of(InteractionType.READ)
                    )
            );
            List<DiscoveryResponse.SearchParameterInfo> searchParams = List.of(
                    new DiscoveryResponse.SearchParameterInfo(
                            "identifier",
                            "token",
                            "Patient.identifier"
                    )
            );
            List<DiscoveryResponse.OperationInfo> operations = List.of(
                    new DiscoveryResponse.OperationInfo(
                            "validate",
                            List.of("Patient"),
                            EnumSet.of(OperationScope.TYPE)
                    )
            );
            DiscoveryResponse response = new DiscoveryResponse(resources, searchParams, operations);

            when(discoveryService.discover(eq(DiscoveryTopic.ALL), eq("Patient"), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "ALL", "resourceType", "Patient")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertFalse(result.isError());
            String jsonContent = result.getFirstTextContent();
            assertTrue(jsonContent.contains("resources"));
            assertTrue(jsonContent.contains("searchParameters"));
            assertTrue(jsonContent.contains("operations"));
            assertTrue(jsonContent.contains("Patient"));
            assertTrue(jsonContent.contains("identifier"));
            assertTrue(jsonContent.contains("validate"));

            verify(discoveryService).discover(DiscoveryTopic.ALL, "Patient", FhirVersion.R5);
        }
    }

    @Nested
    @DisplayName("Default FHIR version handling")
    class DefaultVersionTests {

        @Test
        @DisplayName("should use R5 as default FHIR version when not specified")
        void shouldUseR5AsDefaultVersion() {
            // Given
            DiscoveryResponse response = DiscoveryResponse.empty();

            when(discoveryService.discover(any(), any(), eq(FhirVersion.R5)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES")
            );

            // When
            tool.execute(request);

            // Then
            verify(discoveryService).discover(any(), any(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should parse FHIR version case-insensitively")
        void shouldParseFhirVersionCaseInsensitively() {
            // Given
            DiscoveryResponse response = DiscoveryResponse.empty();

            when(discoveryService.discover(any(), any(), eq(FhirVersion.R4B)))
                    .thenReturn(response);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES", "fhirVersion", "r4b")
            );

            // When
            tool.execute(request);

            // Then
            verify(discoveryService).discover(any(), any(), eq(FhirVersion.R4B));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return error for invalid topic")
        void shouldReturnErrorForInvalidTopic() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "INVALID_TOPIC")
            );

            ToolCallResponse result = tool.execute(request);

            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().toLowerCase().contains("invalid"));
        }

        @Test
        @DisplayName("should return error for invalid FHIR version")
        void shouldReturnErrorForInvalidFhirVersion() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES", "fhirVersion", "R3")
            );

            ToolCallResponse result = tool.execute(request);

            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().toLowerCase().contains("fhir version") ||
                       result.getFirstTextContent().toLowerCase().contains("unknown"));
        }

        @Test
        @DisplayName("should return error when topic is missing")
        void shouldReturnErrorWhenTopicMissing() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("resourceType", "Patient")
            );

            ToolCallResponse result = tool.execute(request);

            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().toLowerCase().contains("topic"));
        }

        @Test
        @DisplayName("should handle discovery service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() {
            // Given
            when(discoveryService.discover(any(), any(), any()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_discover",
                    Map.of("topic", "RESOURCES")
            );

            // When
            ToolCallResponse result = tool.execute(request);

            // Then
            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().contains("Service unavailable") ||
                       result.getFirstTextContent().toLowerCase().contains("error"));
        }

        @Test
        @DisplayName("should handle null arguments map")
        void shouldHandleNullArgumentsMap() {
            ToolCallRequest request = new ToolCallRequest("fhir_discover", null);

            ToolCallResponse result = tool.execute(request);

            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().toLowerCase().contains("topic"));
        }

        @Test
        @DisplayName("should handle empty arguments map")
        void shouldHandleEmptyArgumentsMap() {
            ToolCallRequest request = new ToolCallRequest("fhir_discover", Map.of());

            ToolCallResponse result = tool.execute(request);

            assertTrue(result.isError());
            assertTrue(result.getFirstTextContent().toLowerCase().contains("topic"));
        }
    }
}
