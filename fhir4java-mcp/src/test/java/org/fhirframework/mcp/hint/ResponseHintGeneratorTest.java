package org.fhirframework.mcp.hint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ResponseHintGenerator}.
 * <p>
 * Tests the smart response hints feature that provides AI agents with
 * contextual suggestions for logical next actions after FHIR operations.
 * </p>
 */
class ResponseHintGeneratorTest {

    private ResponseHintGenerator hintGenerator;

    @BeforeEach
    void setUp() {
        hintGenerator = new ResponseHintGenerator();
    }

    @Nested
    @DisplayName("Hints after Patient search")
    class PatientSearchHints {

        @Test
        @DisplayName("should suggest searching Observations for returned Patient")
        void shouldSuggestObservationSearch() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertFalse(hints.isEmpty());
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("observation") && h.contains("fhir_query")));
        }

        @Test
        @DisplayName("should suggest searching Conditions for returned Patient")
        void shouldSuggestConditionSearch() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("456"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("condition")));
        }

        @Test
        @DisplayName("should suggest searching MedicationRequests for returned Patient")
        void shouldSuggestMedicationRequestSearch() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("789"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("medicationrequest")));
        }

        @Test
        @DisplayName("should include example tool calls in hints")
        void shouldIncludeExampleToolCalls() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"action\"") && h.contains("\"search\"") && h.contains("\"resourceType\"")));
        }

        @Test
        @DisplayName("should include patient reference in example tool calls")
        void shouldIncludePatientReferenceInExamples() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("patient-abc"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("Patient/patient-abc")));
        }

        @Test
        @DisplayName("should handle multiple patient IDs")
        void shouldHandleMultiplePatientIds() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123", "456", "789"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertFalse(hints.isEmpty());
            // Should use the first patient ID for suggestions
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("Patient/123")));
        }
    }

    @Nested
    @DisplayName("Hints after Observation search")
    class ObservationSearchHints {

        @Test
        @DisplayName("should suggest reading the Patient reference")
        void shouldSuggestReadingPatient() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Observation",
                    List.of("obs-1"),
                    Map.of("patientId", "patient-123")
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("patient") && h.contains("\"read\"")));
        }
    }

    @Nested
    @DisplayName("Hints after resource create")
    class ResourceCreateHints {

        @Test
        @DisplayName("should suggest reading the created resource")
        void shouldSuggestReadingCreatedResource() {
            // Given
            HintContext context = new HintContext(
                    "fhir_mutate",
                    "create",
                    "Patient",
                    List.of("new-patient-id"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"read\"") && h.contains("new-patient-id")));
        }

        @Test
        @DisplayName("should suggest searching for similar resources")
        void shouldSuggestSearchingSimilarResources() {
            // Given
            HintContext context = new HintContext(
                    "fhir_mutate",
                    "create",
                    "Observation",
                    List.of("obs-new"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"search\"") && h.toLowerCase().contains("observation")));
        }
    }

    @Nested
    @DisplayName("Hints after resource update")
    class ResourceUpdateHints {

        @Test
        @DisplayName("should suggest reading the updated resource to verify")
        void shouldSuggestReadingUpdatedResource() {
            // Given
            HintContext context = new HintContext(
                    "fhir_mutate",
                    "update",
                    "Patient",
                    List.of("updated-patient-id"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"read\"") && h.contains("updated-patient-id")));
        }
    }

    @Nested
    @DisplayName("Hints after resource delete")
    class ResourceDeleteHints {

        @Test
        @DisplayName("should suggest searching to verify deletion")
        void shouldSuggestSearchingToVerifyDeletion() {
            // Given
            HintContext context = new HintContext(
                    "fhir_mutate",
                    "delete",
                    "Patient",
                    List.of("deleted-patient-id"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"search\"") || h.contains("verify")));
        }
    }

    @Nested
    @DisplayName("Hints after discovery")
    class DiscoveryHints {

        @Test
        @DisplayName("should suggest querying discovered resources")
        void shouldSuggestQueryingDiscoveredResources() {
            // Given
            HintContext context = new HintContext(
                    "fhir_discover",
                    "resources",
                    null,
                    List.of(),
                    Map.of("discoveredResources", List.of("Patient", "Observation", "Condition"))
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("fhir_query") && h.contains("\"search\"")));
        }

        @Test
        @DisplayName("should suggest executing discovered operations")
        void shouldSuggestExecutingDiscoveredOperations() {
            // Given
            HintContext context = new HintContext(
                    "fhir_discover",
                    "operations",
                    "Patient",
                    List.of(),
                    Map.of("discoveredOperations", List.of("$everything", "$validate"))
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("\"operation\"") || h.toLowerCase().contains("operation")));
        }

        @Test
        @DisplayName("should suggest querying when search parameters discovered")
        void shouldSuggestQueryingWithSearchParams() {
            // Given
            HintContext context = new HintContext(
                    "fhir_discover",
                    "search_params",
                    "Patient",
                    List.of(),
                    Map.of("discoveredParams", List.of("family", "given", "birthdate"))
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("fhir_query") && h.toLowerCase().contains("patient")));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should return empty hints when no suggestions applicable")
        void shouldReturnEmptyHintsWhenNoSuggestions() {
            // Given - an unknown tool that we don't have hints for
            HintContext context = new HintContext(
                    "unknown_tool",
                    "unknown_action",
                    "Unknown",
                    List.of(),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.isEmpty());
        }

        @Test
        @DisplayName("should handle null resource IDs list")
        void shouldHandleNullResourceIds() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    null,
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            // Should still generate generic hints even without specific IDs
        }

        @Test
        @DisplayName("should handle empty resource IDs list")
        void shouldHandleEmptyResourceIds() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of(),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            // Should not contain patient-specific hints without IDs
        }

        @Test
        @DisplayName("should handle null metadata")
        void shouldHandleNullMetadata() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123"),
                    null
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
        }

        @Test
        @DisplayName("should handle null action gracefully")
        void shouldHandleNullAction() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    null,
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.isEmpty());
        }

        @Test
        @DisplayName("should handle null tool name gracefully")
        void shouldHandleNullToolName() {
            // Given
            HintContext context = new HintContext(
                    null,
                    "search",
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.isEmpty());
        }
    }

    @Nested
    @DisplayName("Hint formatting")
    class HintFormatting {

        @Test
        @DisplayName("should format hints as JSON-like examples")
        void shouldFormatHintsAsJsonExamples() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertFalse(hints.isEmpty());
            // Hints should contain valid JSON structure indicators
            assertTrue(hints.stream().anyMatch(h ->
                h.contains("{") && h.contains("}")));
        }

        @Test
        @DisplayName("should include action description in hints")
        void shouldIncludeActionDescription() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "search",
                    "Patient",
                    List.of("123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("to get") ||
                h.toLowerCase().contains("use fhir_query")));
        }
    }

    @Nested
    @DisplayName("Read action hints")
    class ReadActionHints {

        @Test
        @DisplayName("should suggest related resources after reading Patient")
        void shouldSuggestRelatedResourcesAfterPatientRead() {
            // Given
            HintContext context = new HintContext(
                    "fhir_query",
                    "read",
                    "Patient",
                    List.of("patient-read-123"),
                    Map.of()
            );

            // When
            List<String> hints = hintGenerator.generateHints(context);

            // Then
            assertNotNull(hints);
            assertTrue(hints.stream().anyMatch(h ->
                h.toLowerCase().contains("observation") ||
                h.toLowerCase().contains("condition")));
        }
    }
}
