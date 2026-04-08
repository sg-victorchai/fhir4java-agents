package org.fhirframework.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.mcp.dto.McpError;
import org.fhirframework.mcp.dto.McpRequest;
import org.fhirframework.mcp.dto.McpResponse;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End MCP Integration Test for the FHIR4Java AI-Ready Platform.
 * <p>
 * Tests the full MCP workflow including:
 * <ul>
 *   <li>Tool listing (tools/list)</li>
 *   <li>Discovery operations (fhir_discover)</li>
 *   <li>Query operations (fhir_query)</li>
 *   <li>Mutation operations with dry-run (fhir_mutate)</li>
 *   <li>Error handling for unknown tools</li>
 * </ul>
 * </p>
 */
@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@DisplayName("MCP Integration Test")
class McpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String mcpUrl;

    @BeforeEach
    void setUp() {
        mcpUrl = "http://localhost:" + port + "/api/mcp";
    }

    /**
     * Helper method to call MCP endpoint with a request.
     *
     * @param method the MCP method (e.g., "tools/list", "tools/call")
     * @param params the parameters for the method
     * @return the MCP response
     */
    private McpResponse callMcp(String method, Map<String, Object> params) {
        McpRequest request = new McpRequest(method, params, UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<McpRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<McpResponse> response = restTemplate.postForEntity(
                mcpUrl,
                entity,
                McpResponse.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "MCP endpoint should return 200 OK for all requests");
        assertNotNull(response.getBody(), "Response body should not be null");

        return response.getBody();
    }

    /**
     * Helper method to call a specific tool.
     *
     * @param toolName the name of the tool
     * @param arguments the tool arguments
     * @return the MCP response
     */
    private McpResponse callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);
        return callMcp("tools/call", params);
    }

    @Nested
    @DisplayName("Tools List")
    class ToolsListTests {

        @Test
        @DisplayName("tools/list should return all registered tools")
        void toolsList_returnsAllTools() {
            McpResponse response = callMcp("tools/list", null);

            assertFalse(response.isError(), "Response should not be an error");
            assertNull(response.getError(), "Error should be null");
            assertNotNull(response.getResult(), "Result should not be null");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertNotNull(result.get("tools"), "Tools list should be present");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertNotNull(tools, "Tools list should not be null");
            assertTrue(tools.size() >= 3, "Should have at least 3 tools registered");

            // Verify all three core tools are present
            List<String> toolNames = tools.stream()
                    .map(tool -> (String) tool.get("name"))
                    .toList();

            assertTrue(toolNames.contains("fhir_discover"), "Should have fhir_discover tool");
            assertTrue(toolNames.contains("fhir_query"), "Should have fhir_query tool");
            assertTrue(toolNames.contains("fhir_mutate"), "Should have fhir_mutate tool");

            // Verify tool structure
            for (Map<String, Object> tool : tools) {
                assertNotNull(tool.get("name"), "Tool should have a name");
                assertNotNull(tool.get("description"), "Tool should have a description");
                assertNotNull(tool.get("inputSchema"), "Tool should have an inputSchema");
            }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should return error for unknown tool")
        void unknownTool_returnsError() {
            McpResponse response = callTool("nonexistent_tool", Map.of());

            assertTrue(response.isError(), "Response should be an error");
            assertNotNull(response.getError(), "Error should not be null");
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode(),
                    "Should return METHOD_NOT_FOUND error code");
            assertTrue(response.getError().getMessage().contains("nonexistent_tool"),
                    "Error message should mention the unknown tool name");
        }

        @Test
        @DisplayName("should return error for unknown MCP method")
        void unknownMethod_returnsError() {
            McpResponse response = callMcp("unknown/method", null);

            assertTrue(response.isError(), "Response should be an error");
            assertNotNull(response.getError(), "Error should not be null");
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode(),
                    "Should return METHOD_NOT_FOUND error code");
        }

        @Test
        @DisplayName("should return error when tool name is missing")
        void missingToolName_returnsError() {
            Map<String, Object> params = Map.of("arguments", Map.of());
            McpResponse response = callMcp("tools/call", params);

            assertTrue(response.isError(), "Response should be an error");
            assertNotNull(response.getError(), "Error should not be null");
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode(),
                    "Should return INVALID_PARAMS error code");
        }

        @Test
        @DisplayName("should return error when params is null for tools/call")
        void nullParams_returnsError() {
            McpResponse response = callMcp("tools/call", null);

            assertTrue(response.isError(), "Response should be an error");
            assertNotNull(response.getError(), "Error should not be null");
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode(),
                    "Should return INVALID_PARAMS error code");
        }

        @Test
        @DisplayName("should return error for invalid JSON-RPC version")
        void invalidJsonRpcVersion_returnsError() {
            McpRequest request = new McpRequest("tools/list", null, UUID.randomUUID().toString());
            request.setJsonrpc("1.0");  // Invalid version

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<McpRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<McpResponse> response = restTemplate.postForEntity(
                    mcpUrl, entity, McpResponse.class);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            McpResponse mcpResponse = response.getBody();
            assertNotNull(mcpResponse);
            assertTrue(mcpResponse.isError(), "Response should be an error");
            assertEquals(McpError.INVALID_REQUEST, mcpResponse.getError().getCode(),
                    "Should return INVALID_REQUEST error code");
        }
    }

    @Nested
    @DisplayName("fhir_discover Tool")
    class FhirDiscoverTests {

        @Test
        @DisplayName("should discover available resources")
        void discoverResources() {
            McpResponse response = callTool("fhir_discover", Map.of(
                    "topic", "RESOURCES",
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "Response should not be an error");
            assertNotNull(response.getResult(), "Result should not be null");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertFalse(toolResponse.isError(), "Tool response should not be an error");

            String content = toolResponse.getFirstTextContent();
            assertNotNull(content, "Content should not be null");

            // Verify the response contains expected resource info
            assertTrue(content.contains("Patient") || content.contains("resources"),
                    "Response should contain resource information");
        }

        @Test
        @DisplayName("should discover search parameters for a resource")
        void discoverSearchParams() {
            McpResponse response = callTool("fhir_discover", Map.of(
                    "topic", "SEARCH_PARAMS",
                    "resourceType", "Patient",
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "Response should not be an error");
            assertNotNull(response.getResult(), "Result should not be null");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertFalse(toolResponse.isError(), "Tool response should not be an error");

            String content = toolResponse.getFirstTextContent();
            assertNotNull(content, "Content should not be null");
        }

        @Test
        @DisplayName("should return error for missing topic")
        void discoverMissingTopic_returnsError() {
            McpResponse response = callTool("fhir_discover", Map.of(
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("topic"),
                    "Error should mention missing topic parameter");
        }

        @Test
        @DisplayName("should return error for invalid topic")
        void discoverInvalidTopic_returnsError() {
            McpResponse response = callTool("fhir_discover", Map.of(
                    "topic", "INVALID_TOPIC",
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().toLowerCase().contains("invalid"),
                    "Error should indicate invalid topic");
        }
    }

    @Nested
    @DisplayName("fhir_query Tool")
    class FhirQueryTests {

        @Test
        @DisplayName("should search for resources")
        void searchResources() {
            McpResponse response = callTool("fhir_query", Map.of(
                    "action", "search",
                    "resourceType", "Patient",
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "Response should not be an error");
            assertNotNull(response.getResult(), "Result should not be null");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertFalse(toolResponse.isError(), "Tool response should not be an error");

            String content = toolResponse.getFirstTextContent();
            assertNotNull(content, "Content should not be null");

            // Verify the response is a valid Bundle
            assertTrue(content.contains("Bundle") || content.contains("searchset") || content.contains("total"),
                    "Response should contain Bundle information");
        }

        @Test
        @DisplayName("should return error for missing action")
        void queryMissingAction_returnsError() {
            McpResponse response = callTool("fhir_query", Map.of(
                    "resourceType", "Patient"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("action"),
                    "Error should mention missing action parameter");
        }

        @Test
        @DisplayName("should return error for missing resourceType")
        void queryMissingResourceType_returnsError() {
            McpResponse response = callTool("fhir_query", Map.of(
                    "action", "search"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("resourceType"),
                    "Error should mention missing resourceType parameter");
        }

        @Test
        @DisplayName("should return error for read without id")
        void queryReadWithoutId_returnsError() {
            McpResponse response = callTool("fhir_query", Map.of(
                    "action", "read",
                    "resourceType", "Patient"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("id"),
                    "Error should mention missing id parameter");
        }
    }

    @Nested
    @DisplayName("fhir_mutate Tool")
    class FhirMutateTests {

        @Test
        @DisplayName("should validate resource with dry-run")
        void mutateDryRun_validatesResource() {
            Map<String, Object> patientBody = new LinkedHashMap<>();
            patientBody.put("resourceType", "Patient");
            patientBody.put("active", true);
            patientBody.put("name", List.of(Map.of(
                    "family", "TestFamily",
                    "given", List.of("TestGiven")
            )));
            patientBody.put("gender", "male");

            McpResponse response = callTool("fhir_mutate", Map.of(
                    "action", "create",
                    "resourceType", "Patient",
                    "body", patientBody,
                    "dryRun", true,
                    "fhirVersion", "R5"
            ));

            assertFalse(response.isError(), "Response should not be an error");
            assertNotNull(response.getResult(), "Result should not be null");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertFalse(toolResponse.isError(), "Tool response should not be an error");

            String content = toolResponse.getFirstTextContent();
            assertNotNull(content, "Content should not be null");
            assertTrue(content.contains("dryRun") && content.contains("true"),
                    "Response should indicate dry-run mode");
        }

        @Test
        @DisplayName("should return error for missing action")
        void mutateMissingAction_returnsError() {
            McpResponse response = callTool("fhir_mutate", Map.of(
                    "resourceType", "Patient"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("action"),
                    "Error should mention missing action parameter");
        }

        @Test
        @DisplayName("should return error for create without body")
        void mutateCreateWithoutBody_returnsError() {
            McpResponse response = callTool("fhir_mutate", Map.of(
                    "action", "create",
                    "resourceType", "Patient"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("body"),
                    "Error should mention missing body parameter");
        }

        @Test
        @DisplayName("should return error for delete without id")
        void mutateDeleteWithoutId_returnsError() {
            McpResponse response = callTool("fhir_mutate", Map.of(
                    "action", "delete",
                    "resourceType", "Patient"
            ));

            assertFalse(response.isError(), "MCP response should not be an error");

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertTrue(toolResponse.isError(), "Tool response should be an error");
            assertTrue(toolResponse.getFirstTextContent().contains("id"),
                    "Error should mention missing id parameter");
        }
    }

    @Nested
    @DisplayName("Full Workflow")
    class FullWorkflowTests {

        @Test
        @DisplayName("complete MCP workflow - discover, query, mutate with dry-run")
        void mcpWorkflow_discoverQueryMutate() {
            // Step 1: Discover available resources
            McpResponse discoverResponse = callTool("fhir_discover", Map.of(
                    "topic", "RESOURCES",
                    "fhirVersion", "R5"
            ));

            assertFalse(discoverResponse.isError(), "Discover response should not be an error");
            ToolCallResponse discoverToolResponse = objectMapper.convertValue(
                    discoverResponse.getResult(), ToolCallResponse.class);
            assertFalse(discoverToolResponse.isError(), "Discover tool should succeed");

            String discoverContent = discoverToolResponse.getFirstTextContent();
            assertNotNull(discoverContent, "Discover content should not be null");

            // Step 2: Search for resources
            McpResponse searchResponse = callTool("fhir_query", Map.of(
                    "action", "search",
                    "resourceType", "Patient",
                    "fhirVersion", "R5"
            ));

            assertFalse(searchResponse.isError(), "Search response should not be an error");
            ToolCallResponse searchToolResponse = objectMapper.convertValue(
                    searchResponse.getResult(), ToolCallResponse.class);
            assertFalse(searchToolResponse.isError(), "Search tool should succeed");

            String searchContent = searchToolResponse.getFirstTextContent();
            assertNotNull(searchContent, "Search content should not be null");
            assertTrue(searchContent.contains("Bundle") || searchContent.contains("total"),
                    "Search should return a Bundle");

            // Step 3: Validate a new resource with dry-run
            Map<String, Object> newPatient = new LinkedHashMap<>();
            newPatient.put("resourceType", "Patient");
            newPatient.put("active", true);
            newPatient.put("name", List.of(Map.of(
                    "family", "WorkflowTest",
                    "given", List.of("Integration")
            )));
            newPatient.put("gender", "female");

            McpResponse dryRunResponse = callTool("fhir_mutate", Map.of(
                    "action", "create",
                    "resourceType", "Patient",
                    "body", newPatient,
                    "dryRun", true,
                    "fhirVersion", "R5"
            ));

            assertFalse(dryRunResponse.isError(), "Dry-run response should not be an error");
            ToolCallResponse dryRunToolResponse = objectMapper.convertValue(
                    dryRunResponse.getResult(), ToolCallResponse.class);
            assertFalse(dryRunToolResponse.isError(), "Dry-run should succeed");

            String dryRunContent = dryRunToolResponse.getFirstTextContent();
            assertNotNull(dryRunContent, "Dry-run content should not be null");
            assertTrue(dryRunContent.contains("dryRun"),
                    "Response should indicate dry-run mode");

            // Step 4: Verify tools list contains all tools
            McpResponse listResponse = callMcp("tools/list", null);
            assertFalse(listResponse.isError(), "Tools list should not be an error");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) listResponse.getResult();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");

            List<String> toolNames = tools.stream()
                    .map(tool -> (String) tool.get("name"))
                    .toList();

            assertTrue(toolNames.contains("fhir_discover"), "Should have fhir_discover");
            assertTrue(toolNames.contains("fhir_query"), "Should have fhir_query");
            assertTrue(toolNames.contains("fhir_mutate"), "Should have fhir_mutate");
        }

        @Test
        @DisplayName("create and read resource workflow")
        void createAndReadWorkflow() {
            // Step 1: Create a new patient
            Map<String, Object> patientBody = new LinkedHashMap<>();
            patientBody.put("resourceType", "Patient");
            patientBody.put("active", true);
            patientBody.put("name", List.of(Map.of(
                    "family", "McpTest",
                    "given", List.of("Integration")
            )));
            patientBody.put("gender", "male");

            McpResponse createResponse = callTool("fhir_mutate", Map.of(
                    "action", "create",
                    "resourceType", "Patient",
                    "body", patientBody,
                    "fhirVersion", "R5"
            ));

            assertFalse(createResponse.isError(), "Create response should not be an error");
            ToolCallResponse createToolResponse = objectMapper.convertValue(
                    createResponse.getResult(), ToolCallResponse.class);
            assertFalse(createToolResponse.isError(), "Create tool should succeed");

            String createContent = createToolResponse.getFirstTextContent();
            assertNotNull(createContent, "Create content should not be null");
            assertTrue(createContent.contains("created"), "Response should indicate creation");
            assertTrue(createContent.contains("id"), "Response should contain resource ID");

            // Extract the resource ID from the response
            // The response is JSON with "id" field
            String resourceId = extractIdFromResponse(createContent);
            assertNotNull(resourceId, "Should be able to extract resource ID");

            // Step 2: Read the created resource
            McpResponse readResponse = callTool("fhir_query", Map.of(
                    "action", "read",
                    "resourceType", "Patient",
                    "id", resourceId,
                    "fhirVersion", "R5"
            ));

            assertFalse(readResponse.isError(), "Read response should not be an error");
            ToolCallResponse readToolResponse = objectMapper.convertValue(
                    readResponse.getResult(), ToolCallResponse.class);
            assertFalse(readToolResponse.isError(), "Read tool should succeed");

            String readContent = readToolResponse.getFirstTextContent();
            assertNotNull(readContent, "Read content should not be null");
            assertTrue(readContent.contains("Patient"), "Response should contain Patient");
            assertTrue(readContent.contains("McpTest"), "Response should contain the family name");
        }
    }

    /**
     * Extracts the resource ID from a JSON response containing an "id" field.
     */
    private String extractIdFromResponse(String jsonContent) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(jsonContent, Map.class);
            Object id = responseMap.get("id");
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            // Try simple string extraction as fallback
            int idIndex = jsonContent.indexOf("\"id\"");
            if (idIndex >= 0) {
                int startQuote = jsonContent.indexOf("\"", idIndex + 4) + 1;
                int endQuote = jsonContent.indexOf("\"", startQuote);
                if (startQuote > 0 && endQuote > startQuote) {
                    return jsonContent.substring(startQuote, endQuote);
                }
            }
            return null;
        }
    }
}
