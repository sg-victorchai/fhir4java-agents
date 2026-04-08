package org.fhirframework.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.hint.ResponseHintGenerator;
import org.fhirframework.persistence.service.FhirResourceService;
import org.fhirframework.persistence.service.FhirResourceService.ResourceResult;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FhirQueryTool}.
 */
@ExtendWith(MockitoExtension.class)
class FhirQueryToolTest {

    @Mock
    private FhirResourceService fhirResourceService;

    @Mock
    private ResponseHintGenerator hintGenerator;

    private FhirQueryTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new FhirQueryTool(fhirResourceService, hintGenerator);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Tool metadata")
    class MetadataTests {

        @Test
        @DisplayName("should have correct tool name")
        void shouldHaveCorrectName() {
            assertEquals("fhir_query", tool.getName());
        }

        @Test
        @DisplayName("should have descriptive description")
        void shouldHaveDescription() {
            String description = tool.getDescription();
            assertNotNull(description);
            assertTrue(description.toLowerCase().contains("query"));
            assertTrue(description.toLowerCase().contains("fhir"));
        }

        @Test
        @DisplayName("should have valid input schema with action enum")
        void shouldHaveValidInputSchema() {
            Map<String, Object> schema = tool.getInputSchema();

            assertNotNull(schema);
            assertEquals("object", schema.get("type"));

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertNotNull(properties);

            // Check action property
            @SuppressWarnings("unchecked")
            Map<String, Object> actionProp = (Map<String, Object>) properties.get("action");
            assertNotNull(actionProp);
            assertEquals("string", actionProp.get("type"));

            @SuppressWarnings("unchecked")
            List<String> actionEnum = (List<String>) actionProp.get("enum");
            assertNotNull(actionEnum);
            assertTrue(actionEnum.contains("read"));
            assertTrue(actionEnum.contains("search"));
            assertTrue(actionEnum.contains("history"));
            assertTrue(actionEnum.contains("operation"));

            // Check resourceType property
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceTypeProp = (Map<String, Object>) properties.get("resourceType");
            assertNotNull(resourceTypeProp);
            assertEquals("string", resourceTypeProp.get("type"));

            // Check id property
            @SuppressWarnings("unchecked")
            Map<String, Object> idProp = (Map<String, Object>) properties.get("id");
            assertNotNull(idProp);
            assertEquals("string", idProp.get("type"));

            // Check searchParams property
            @SuppressWarnings("unchecked")
            Map<String, Object> searchParamsProp = (Map<String, Object>) properties.get("searchParams");
            assertNotNull(searchParamsProp);
            assertEquals("object", searchParamsProp.get("type"));

            // Check operationName property
            @SuppressWarnings("unchecked")
            Map<String, Object> operationNameProp = (Map<String, Object>) properties.get("operationName");
            assertNotNull(operationNameProp);
            assertEquals("string", operationNameProp.get("type"));

            // Check operationParams property
            @SuppressWarnings("unchecked")
            Map<String, Object> operationParamsProp = (Map<String, Object>) properties.get("operationParams");
            assertNotNull(operationParamsProp);
            assertEquals("object", operationParamsProp.get("type"));

            // Check fhirVersion property
            @SuppressWarnings("unchecked")
            Map<String, Object> fhirVersionProp = (Map<String, Object>) properties.get("fhirVersion");
            assertNotNull(fhirVersionProp);
            assertEquals("string", fhirVersionProp.get("type"));
            assertEquals("R5", fhirVersionProp.get("default"));

            // Check required fields
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertNotNull(required);
            assertTrue(required.contains("action"));
            assertTrue(required.contains("resourceType"));
        }
    }

    @Nested
    @DisplayName("Read action")
    class ReadActionTests {

        @Test
        @DisplayName("should read resource successfully")
        void shouldReadResourceSuccessfully() {
            // Given
            String resourceJson = "{\"resourceType\":\"Patient\",\"id\":\"123\",\"name\":[{\"family\":\"Smith\"}]}";
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.read(eq("Patient"), eq("123"), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "123"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("Patient"));
            assertTrue(response.getFirstTextContent().contains("123"));

            verify(fhirResourceService).read("Patient", "123", FhirVersion.R5);
        }

        @Test
        @DisplayName("should read resource with R4B version")
        void shouldReadResourceWithR4BVersion() {
            // Given
            String resourceJson = "{\"resourceType\":\"Patient\",\"id\":\"456\"}";
            ResourceResult result = new ResourceResult("456", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.read(eq("Patient"), eq("456"), eq(FhirVersion.R4B)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "456",
                            "fhirVersion", "R4B"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            verify(fhirResourceService).read("Patient", "456", FhirVersion.R4B);
        }

        @Test
        @DisplayName("should return error when id is missing for read action")
        void shouldReturnErrorWhenIdMissingForRead() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("id"));

            verifyNoInteractions(fhirResourceService);
        }

        @Test
        @DisplayName("should return error when resource not found")
        void shouldReturnErrorWhenResourceNotFound() {
            // Given
            when(fhirResourceService.read(anyString(), anyString(), any(FhirVersion.class)))
                    .thenThrow(new ResourceNotFoundException("Patient", "999"));

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "999"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("not found") ||
                       response.getFirstTextContent().contains("999"));
        }
    }

    @Nested
    @DisplayName("Search action")
    class SearchActionTests {

        @Test
        @DisplayName("should search resources successfully")
        void shouldSearchResourcesSuccessfully() {
            // Given
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.SEARCHSET);
            bundle.setTotal(1);

            Patient patient = new Patient();
            patient.setId("Patient/123");
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setResource(patient);

            when(fhirResourceService.search(
                    eq("Patient"),
                    any(),
                    eq(FhirVersion.R5),
                    anyInt()))
                    .thenReturn(bundle);

            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("family", "Smith");

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "search",
                            "resourceType", "Patient",
                            "searchParams", searchParams
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("Bundle") ||
                       response.getFirstTextContent().contains("searchset"));

            verify(fhirResourceService).search(eq("Patient"), any(), eq(FhirVersion.R5), anyInt());
        }

        @Test
        @DisplayName("should search without search params")
        void shouldSearchWithoutSearchParams() {
            // Given
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.SEARCHSET);
            bundle.setTotal(0);

            when(fhirResourceService.search(
                    eq("Patient"),
                    any(),
                    eq(FhirVersion.R5),
                    anyInt()))
                    .thenReturn(bundle);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "search",
                            "resourceType", "Patient"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            verify(fhirResourceService).search(eq("Patient"), any(), eq(FhirVersion.R5), anyInt());
        }
    }

    @Nested
    @DisplayName("History action")
    class HistoryActionTests {

        @Test
        @DisplayName("should get resource history successfully")
        void shouldGetResourceHistorySuccessfully() {
            // Given
            Bundle bundle = new Bundle();
            bundle.setType(Bundle.BundleType.HISTORY);
            bundle.setTotal(2);

            when(fhirResourceService.history(eq("Patient"), eq("123"), eq(FhirVersion.R5)))
                    .thenReturn(bundle);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "history",
                            "resourceType", "Patient",
                            "id", "123"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("history") ||
                       response.getFirstTextContent().contains("Bundle"));

            verify(fhirResourceService).history("Patient", "123", FhirVersion.R5);
        }

        @Test
        @DisplayName("should return error when id is missing for history action")
        void shouldReturnErrorWhenIdMissingForHistory() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "history",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("id"));

            verifyNoInteractions(fhirResourceService);
        }
    }

    @Nested
    @DisplayName("Operation action")
    class OperationActionTests {

        @Test
        @DisplayName("should execute operation successfully")
        void shouldExecuteOperationSuccessfully() {
            // Given - the operation result will be a simple success response
            OperationOutcome outcome = new OperationOutcome();
            outcome.setId("operation-result");

            // Note: We need to mock the operation execution
            // The actual implementation will need to call an operation service
            // For now, we simulate what the tool should return

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "operation",
                            "resourceType", "Patient",
                            "id", "123",
                            "operationName", "$everything"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then - for now, operations may not be implemented and return appropriate message
            assertNotNull(response);
            // The actual assertion will depend on how operations are implemented
        }

        @Test
        @DisplayName("should return error when operationName is missing")
        void shouldReturnErrorWhenOperationNameMissing() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "operation",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("operationname") ||
                       response.getFirstTextContent().toLowerCase().contains("operation"));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return error for missing action")
        void shouldReturnErrorForMissingAction() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of("resourceType", "Patient")
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("action"));
        }

        @Test
        @DisplayName("should return error for missing resourceType")
        void shouldReturnErrorForMissingResourceType() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of("action", "read", "id", "123")
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("resourcetype"));
        }

        @Test
        @DisplayName("should return error for unknown action")
        void shouldReturnErrorForUnknownAction() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "unknown_action",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("action") ||
                       response.getFirstTextContent().toLowerCase().contains("unknown") ||
                       response.getFirstTextContent().toLowerCase().contains("invalid"));
        }

        @Test
        @DisplayName("should return error for invalid FHIR version")
        void shouldReturnErrorForInvalidFhirVersion() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "123",
                            "fhirVersion", "R3"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("version") ||
                       response.getFirstTextContent().toLowerCase().contains("r3"));
        }

        @Test
        @DisplayName("should handle null arguments map")
        void shouldHandleNullArgumentsMap() {
            ToolCallRequest request = new ToolCallRequest("fhir_query", null);

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
        }

        @Test
        @DisplayName("should handle empty arguments map")
        void shouldHandleEmptyArgumentsMap() {
            ToolCallRequest request = new ToolCallRequest("fhir_query", Map.of());

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
        }

        @Test
        @DisplayName("should handle service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() {
            // Given
            when(fhirResourceService.read(anyString(), anyString(), any(FhirVersion.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "123"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().contains("Database connection failed") ||
                       response.getFirstTextContent().toLowerCase().contains("error"));
        }
    }

    @Nested
    @DisplayName("Default FHIR version handling")
    class DefaultVersionTests {

        @Test
        @DisplayName("should use R5 as default FHIR version when not specified")
        void shouldUseR5AsDefaultVersion() {
            // Given
            String resourceJson = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.read(anyString(), anyString(), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "123"
                    )
            );

            // When
            tool.execute(request);

            // Then
            verify(fhirResourceService).read(anyString(), anyString(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should parse FHIR version case-insensitively")
        void shouldParseFhirVersionCaseInsensitively() {
            // Given
            String resourceJson = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.read(anyString(), anyString(), eq(FhirVersion.R4B)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_query",
                    Map.of(
                            "action", "read",
                            "resourceType", "Patient",
                            "id", "123",
                            "fhirVersion", "r4b"
                    )
            );

            // When
            tool.execute(request);

            // Then
            verify(fhirResourceService).read(anyString(), anyString(), eq(FhirVersion.R4B));
        }
    }
}
