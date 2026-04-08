package org.fhirframework.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.validation.IssueSeverity;
import org.fhirframework.core.validation.ProfileValidator;
import org.fhirframework.core.validation.ValidationIssue;
import org.fhirframework.core.validation.ValidationResult;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.hint.ResponseHintGenerator;
import org.fhirframework.persistence.service.FhirResourceService;
import org.fhirframework.persistence.service.FhirResourceService.ResourceResult;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FhirMutateTool}.
 */
@ExtendWith(MockitoExtension.class)
class FhirMutateToolTest {

    @Mock
    private FhirResourceService fhirResourceService;

    @Mock
    private ProfileValidator profileValidator;

    @Mock
    private FhirContextFactory contextFactory;

    @Mock
    private ResponseHintGenerator hintGenerator;

    private FhirMutateTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tool = new FhirMutateTool(fhirResourceService, profileValidator, contextFactory, hintGenerator);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Tool metadata")
    class MetadataTests {

        @Test
        @DisplayName("should have correct tool name")
        void shouldHaveCorrectName() {
            assertEquals("fhir_mutate", tool.getName());
        }

        @Test
        @DisplayName("should have descriptive description")
        void shouldHaveDescription() {
            String description = tool.getDescription();
            assertNotNull(description);
            assertTrue(description.toLowerCase().contains("create"));
            assertTrue(description.toLowerCase().contains("update"));
            assertTrue(description.toLowerCase().contains("delete"));
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
            assertTrue(actionEnum.contains("create"));
            assertTrue(actionEnum.contains("update"));
            assertTrue(actionEnum.contains("patch"));
            assertTrue(actionEnum.contains("delete"));

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

            // Check body property
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyProp = (Map<String, Object>) properties.get("body");
            assertNotNull(bodyProp);
            assertEquals("object", bodyProp.get("type"));

            // Check dryRun property
            @SuppressWarnings("unchecked")
            Map<String, Object> dryRunProp = (Map<String, Object>) properties.get("dryRun");
            assertNotNull(dryRunProp);
            assertEquals("boolean", dryRunProp.get("type"));
            assertEquals(false, dryRunProp.get("default"));

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
    @DisplayName("Create action")
    class CreateActionTests {

        @Test
        @DisplayName("should create resource successfully and return created resource")
        void shouldCreateResourceSuccessfully() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of(
                    "resourceType", "Patient",
                    "name", List.of(Map.of("family", "Smith", "given", List.of("John")))
            );
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            ResourceResult result = new ResourceResult("new-id-123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.create(eq("Patient"), anyString(), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("created"));
            assertTrue(response.getFirstTextContent().contains("new-id-123"));

            verify(fhirResourceService).create(eq("Patient"), anyString(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should return error when body is missing for create")
        void shouldReturnErrorWhenBodyMissingForCreate() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("body"));

            verifyNoInteractions(fhirResourceService);
        }
    }

    @Nested
    @DisplayName("Update action")
    class UpdateActionTests {

        @Test
        @DisplayName("should update resource successfully and return updated resource")
        void shouldUpdateResourceSuccessfully() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of(
                    "resourceType", "Patient",
                    "id", "123",
                    "name", List.of(Map.of("family", "Johnson", "given", List.of("Jane")))
            );
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            ResourceResult result = new ResourceResult("123", 2, resourceJson, Instant.now(), false);

            when(fhirResourceService.update(eq("Patient"), eq("123"), anyString(), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "update",
                            "resourceType", "Patient",
                            "id", "123",
                            "body", patientBody
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("updated"));
            assertTrue(response.getFirstTextContent().contains("123"));

            verify(fhirResourceService).update(eq("Patient"), eq("123"), anyString(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should return error when id is missing for update")
        void shouldReturnErrorWhenIdMissingForUpdate() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "update",
                            "resourceType", "Patient",
                            "body", Map.of("resourceType", "Patient")
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("id"));

            verifyNoInteractions(fhirResourceService);
        }

        @Test
        @DisplayName("should return error when body is missing for update")
        void shouldReturnErrorWhenBodyMissingForUpdate() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "update",
                            "resourceType", "Patient",
                            "id", "123"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("body"));

            verifyNoInteractions(fhirResourceService);
        }
    }

    @Nested
    @DisplayName("Patch action")
    class PatchActionTests {

        @Test
        @DisplayName("should patch resource successfully and return patched resource")
        void shouldPatchResourceSuccessfully() throws JsonProcessingException {
            // Given
            Map<String, Object> patchBody = Map.of(
                    "resourceType", "Patient",
                    "name", List.of(Map.of("family", "UpdatedName"))
            );
            String resourceJson = objectMapper.writeValueAsString(patchBody);
            ResourceResult result = new ResourceResult("456", 3, resourceJson, Instant.now(), false);

            when(fhirResourceService.update(eq("Patient"), eq("456"), anyString(), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "patch",
                            "resourceType", "Patient",
                            "id", "456",
                            "body", patchBody
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("patched"));
            assertTrue(response.getFirstTextContent().contains("456"));

            verify(fhirResourceService).update(eq("Patient"), eq("456"), anyString(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should return error when id is missing for patch")
        void shouldReturnErrorWhenIdMissingForPatch() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "patch",
                            "resourceType", "Patient",
                            "body", Map.of()
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("id"));

            verifyNoInteractions(fhirResourceService);
        }

        @Test
        @DisplayName("should return error when body is missing for patch")
        void shouldReturnErrorWhenBodyMissingForPatch() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "patch",
                            "resourceType", "Patient",
                            "id", "456"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("body"));

            verifyNoInteractions(fhirResourceService);
        }
    }

    @Nested
    @DisplayName("Delete action")
    class DeleteActionTests {

        @Test
        @DisplayName("should delete resource successfully")
        void shouldDeleteResourceSuccessfully() {
            // Given
            ResourceResult result = new ResourceResult("789", 1, null, Instant.now(), true);

            when(fhirResourceService.delete(eq("Patient"), eq("789"), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
                            "resourceType", "Patient",
                            "id", "789"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            assertNotNull(response.getFirstTextContent());
            assertTrue(response.getFirstTextContent().contains("deleted"));
            assertTrue(response.getFirstTextContent().contains("789"));

            verify(fhirResourceService).delete("Patient", "789", FhirVersion.R5);
        }

        @Test
        @DisplayName("should return error when id is missing for delete")
        void shouldReturnErrorWhenIdMissingForDelete() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
                            "resourceType", "Patient"
                    )
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("id"));

            verifyNoInteractions(fhirResourceService);
        }

        @Test
        @DisplayName("should return error when resource not found for delete")
        void shouldReturnErrorWhenResourceNotFoundForDelete() {
            // Given
            when(fhirResourceService.delete(anyString(), anyString(), any(FhirVersion.class)))
                    .thenThrow(new ResourceNotFoundException("Patient", "999"));

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
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
    @DisplayName("Dry-run mode")
    class DryRunTests {

        @Test
        @DisplayName("dryRun=true should validate without persisting")
        void dryRunShouldValidateWithoutPersisting() {
            // Given
            Map<String, Object> patientBody = Map.of(
                    "resourceType", "Patient",
                    "name", List.of(Map.of("family", "Smith"))
            );

            // Mock contextFactory to return a valid FhirContext
            ca.uhn.fhir.context.FhirContext fhirContext = ca.uhn.fhir.context.FhirContext.forR5();
            when(contextFactory.getContext(FhirVersion.R5)).thenReturn(fhirContext);

            // Mock profileValidator to return a valid result
            ValidationResult validationResult = ValidationResult.success();
            when(profileValidator.validateAgainstRequiredProfiles(any(), eq(FhirVersion.R5)))
                    .thenReturn(validationResult);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody,
                            "dryRun", true
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"dryRun\""));
            assertTrue(content.contains("true"));
            assertTrue(content.contains("\"valid\""));

            // Verify that fhirResourceService was NOT called (no persistence)
            verifyNoInteractions(fhirResourceService);

            // Verify that validation was called
            verify(profileValidator).validateAgainstRequiredProfiles(any(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("dryRun should return validation errors when resource is invalid")
        void dryRunShouldReturnValidationErrorsWhenInvalid() {
            // Given
            Map<String, Object> invalidPatient = Map.of(
                    "resourceType", "Patient"
                    // Missing required fields
            );

            // Mock contextFactory to return a valid FhirContext
            ca.uhn.fhir.context.FhirContext fhirContext = ca.uhn.fhir.context.FhirContext.forR5();
            when(contextFactory.getContext(FhirVersion.R5)).thenReturn(fhirContext);

            // Mock profileValidator to return validation errors
            ValidationResult validationResult = new ValidationResult();
            validationResult.addIssue(new ValidationIssue(
                    IssueSeverity.ERROR,
                    IssueType.REQUIRED,
                    "Patient.name is required",
                    "Patient.name",
                    null
            ));
            when(profileValidator.validateAgainstRequiredProfiles(any(), eq(FhirVersion.R5)))
                    .thenReturn(validationResult);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", invalidPatient,
                            "dryRun", true
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError()); // Response itself is not an error
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"dryRun\""));
            assertTrue(content.contains("\"valid\""));
            assertTrue(content.contains("false")); // validation failed
            assertTrue(content.contains("issues"));

            // Verify that fhirResourceService was NOT called
            verifyNoInteractions(fhirResourceService);
        }

        @Test
        @DisplayName("dryRun delete should verify resource exists")
        void dryRunDeleteShouldVerifyResourceExists() {
            // Given
            when(fhirResourceService.exists("Patient", "123")).thenReturn(true);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
                            "resourceType", "Patient",
                            "id", "123",
                            "dryRun", true
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"dryRun\""));
            assertTrue(content.contains("\"valid\""));
            assertTrue(content.contains("true"));

            // Verify exists was called but delete was NOT called
            verify(fhirResourceService).exists("Patient", "123");
            verify(fhirResourceService, never()).delete(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("dryRun delete should return error when resource does not exist")
        void dryRunDeleteShouldReturnErrorWhenResourceNotFound() {
            // Given
            when(fhirResourceService.exists("Patient", "nonexistent")).thenReturn(false);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
                            "resourceType", "Patient",
                            "id", "nonexistent",
                            "dryRun", true
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError()); // Response format is not error
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"dryRun\""));
            assertTrue(content.contains("\"valid\""));
            assertTrue(content.contains("false")); // validation failed - resource not found

            // Verify exists was called but delete was NOT called
            verify(fhirResourceService).exists("Patient", "nonexistent");
            verify(fhirResourceService, never()).delete(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return error for missing action")
        void shouldReturnErrorForMissingAction() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
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
                    "fhir_mutate",
                    Map.of("action", "create")
            );

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
            assertTrue(response.getFirstTextContent().toLowerCase().contains("resourcetype"));
        }

        @Test
        @DisplayName("should return error for unknown action")
        void shouldReturnErrorForUnknownAction() {
            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
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
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", Map.of("resourceType", "Patient"),
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
            ToolCallRequest request = new ToolCallRequest("fhir_mutate", null);

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
        }

        @Test
        @DisplayName("should handle empty arguments map")
        void shouldHandleEmptyArgumentsMap() {
            ToolCallRequest request = new ToolCallRequest("fhir_mutate", Map.of());

            ToolCallResponse response = tool.execute(request);

            assertTrue(response.isError());
        }

        @Test
        @DisplayName("should handle service exceptions gracefully")
        void shouldHandleServiceExceptionsGracefully() {
            // Given
            when(fhirResourceService.create(anyString(), anyString(), any(FhirVersion.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", Map.of("resourceType", "Patient")
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
    @DisplayName("FHIR version handling")
    class FhirVersionTests {

        @Test
        @DisplayName("should use R5 as default FHIR version when not specified")
        void shouldUseR5AsDefaultVersion() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of("resourceType", "Patient");
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.create(anyString(), anyString(), eq(FhirVersion.R5)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody
                    )
            );

            // When
            tool.execute(request);

            // Then
            verify(fhirResourceService).create(anyString(), anyString(), eq(FhirVersion.R5));
        }

        @Test
        @DisplayName("should use R4B when specified")
        void shouldUseR4BWhenSpecified() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of("resourceType", "Patient");
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.create(anyString(), anyString(), eq(FhirVersion.R4B)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody,
                            "fhirVersion", "R4B"
                    )
            );

            // When
            tool.execute(request);

            // Then
            verify(fhirResourceService).create(anyString(), anyString(), eq(FhirVersion.R4B));
        }

        @Test
        @DisplayName("should handle case-insensitive FHIR version")
        void shouldHandleCaseInsensitiveFhirVersion() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of("resourceType", "Patient");
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            ResourceResult result = new ResourceResult("123", 1, resourceJson, Instant.now(), false);

            when(fhirResourceService.create(anyString(), anyString(), eq(FhirVersion.R4B)))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody,
                            "fhirVersion", "r4b"
                    )
            );

            // When
            tool.execute(request);

            // Then
            verify(fhirResourceService).create(anyString(), anyString(), eq(FhirVersion.R4B));
        }
    }

    @Nested
    @DisplayName("Response format")
    class ResponseFormatTests {

        @Test
        @DisplayName("create response should include id and versionId")
        void createResponseShouldIncludeIdAndVersion() throws JsonProcessingException {
            // Given
            Map<String, Object> patientBody = Map.of("resourceType", "Patient");
            String resourceJson = objectMapper.writeValueAsString(patientBody);
            Instant now = Instant.now();
            ResourceResult result = new ResourceResult("new-id", 1, resourceJson, now, false);

            when(fhirResourceService.create(anyString(), anyString(), any()))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "create",
                            "resourceType", "Patient",
                            "body", patientBody
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"id\""));
            assertTrue(content.contains("new-id"));
            assertTrue(content.contains("\"versionId\""));
            assertTrue(content.contains("1"));
            assertTrue(content.contains("\"lastUpdated\""));
        }

        @Test
        @DisplayName("delete response should include deleted resource info")
        void deleteResponseShouldIncludeDeletedInfo() {
            // Given
            ResourceResult result = new ResourceResult("del-id", 1, null, Instant.now(), true);
            when(fhirResourceService.delete(anyString(), anyString(), any()))
                    .thenReturn(result);

            ToolCallRequest request = new ToolCallRequest(
                    "fhir_mutate",
                    Map.of(
                            "action", "delete",
                            "resourceType", "Patient",
                            "id", "del-id"
                    )
            );

            // When
            ToolCallResponse response = tool.execute(request);

            // Then
            assertFalse(response.isError());
            String content = response.getFirstTextContent();
            assertTrue(content.contains("\"action\""));
            assertTrue(content.contains("deleted"));
            assertTrue(content.contains("\"id\""));
            assertTrue(content.contains("del-id"));
        }
    }
}
