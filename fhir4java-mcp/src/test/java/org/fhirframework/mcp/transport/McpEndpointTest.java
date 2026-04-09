package org.fhirframework.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.mcp.dto.McpError;
import org.fhirframework.mcp.dto.McpRequest;
import org.fhirframework.mcp.dto.McpResponse;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.tool.McpTool;
import org.fhirframework.mcp.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpEndpoint}.
 */
class McpEndpointTest {

    private McpEndpoint endpoint;
    private ToolRegistry toolRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Create a test tool
        McpTool testTool = new TestTool();
        toolRegistry = new ToolRegistry(List.of(testTool));
        endpoint = new McpEndpoint(toolRegistry);
    }

    @Nested
    @DisplayName("initialize method")
    class InitializeTests {

        @Test
        @DisplayName("should return protocol version, capabilities, and server info")
        void shouldReturnInitializeResponse() {
            Map<String, Object> params = Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "vscode", "version", "1.0.0")
            );
            McpRequest request = new McpRequest("initialize", params, "init-1");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());
            assertEquals("init-1", response.getId());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertNotNull(result);

            // Check protocol version
            assertEquals("2024-11-05", result.get("protocolVersion"));

            // Check capabilities
            @SuppressWarnings("unchecked")
            Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
            assertNotNull(capabilities);
            assertNotNull(capabilities.get("tools"));

            // Check server info
            @SuppressWarnings("unchecked")
            Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
            assertNotNull(serverInfo);
            assertEquals("fhir4java-mcp", serverInfo.get("name"));
            assertNotNull(serverInfo.get("version"));
        }

        @Test
        @DisplayName("should handle initialize without params")
        void shouldHandleInitializeWithoutParams() {
            McpRequest request = new McpRequest("initialize", null, "init-2");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertNotNull(result);
            assertEquals("2024-11-05", result.get("protocolVersion"));
        }

        @Test
        @DisplayName("should handle notifications/initialized")
        void shouldHandleInitializedNotification() {
            McpRequest request = new McpRequest("notifications/initialized", null, "init-3");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());
            assertTrue(endpoint.isInitialized());
        }

        @Test
        @DisplayName("should not be initialized before notifications/initialized")
        void shouldNotBeInitializedBeforeNotification() {
            assertFalse(endpoint.isInitialized());

            // Call initialize
            McpRequest initRequest = new McpRequest("initialize", null, "init-4");
            endpoint.handle(initRequest);

            // Still not initialized until notifications/initialized
            assertFalse(endpoint.isInitialized());
        }
    }

    @Nested
    @DisplayName("tools/list method")
    class ToolsListTests {

        @Test
        @DisplayName("should return list of tool definitions")
        void shouldReturnToolDefinitions() {
            McpRequest request = new McpRequest("tools/list", null, "1");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());
            assertEquals("1", response.getId());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertNotNull(result);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertNotNull(tools);
            assertEquals(1, tools.size());

            Map<String, Object> toolDef = tools.get(0);
            assertEquals("test_tool", toolDef.get("name"));
            assertEquals("A test tool for unit testing", toolDef.get("description"));
            assertNotNull(toolDef.get("inputSchema"));
        }

        @Test
        @DisplayName("should return empty list when no tools registered")
        void shouldReturnEmptyListWhenNoTools() {
            ToolRegistry emptyRegistry = new ToolRegistry(List.of());
            McpEndpoint emptyEndpoint = new McpEndpoint(emptyRegistry);
            McpRequest request = new McpRequest("tools/list", null, "2");

            McpResponse response = emptyEndpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
            assertTrue(tools.isEmpty());
        }
    }

    @Nested
    @DisplayName("tools/call method")
    class ToolsCallTests {

        @Test
        @DisplayName("should invoke correct tool and return result")
        void shouldInvokeToolAndReturnResult() {
            Map<String, Object> params = Map.of(
                    "name", "test_tool",
                    "arguments", Map.of("input", "test value")
            );
            McpRequest request = new McpRequest("tools/call", params, "3");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());
            assertEquals("3", response.getId());

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertNotNull(toolResponse);
            assertFalse(toolResponse.isError());
            assertEquals("Received input: test value", toolResponse.getFirstTextContent());
        }

        @Test
        @DisplayName("should return error when tool not found")
        void shouldReturnErrorWhenToolNotFound() {
            Map<String, Object> params = Map.of(
                    "name", "nonexistent_tool",
                    "arguments", Map.of()
            );
            McpRequest request = new McpRequest("tools/call", params, "4");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("nonexistent_tool"));
        }

        @Test
        @DisplayName("should return error when name parameter missing")
        void shouldReturnErrorWhenNameMissing() {
            Map<String, Object> params = Map.of(
                    "arguments", Map.of()
            );
            McpRequest request = new McpRequest("tools/call", params, "5");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode());
        }

        @Test
        @DisplayName("should return error when params is null")
        void shouldReturnErrorWhenParamsNull() {
            McpRequest request = new McpRequest("tools/call", null, "6");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_PARAMS, response.getError().getCode());
        }

        @Test
        @DisplayName("should handle tool with no arguments")
        void shouldHandleToolWithNoArguments() {
            Map<String, Object> params = Map.of(
                    "name", "test_tool"
            );
            McpRequest request = new McpRequest("tools/call", params, "7");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNull(response.getError());

            ToolCallResponse toolResponse = objectMapper.convertValue(
                    response.getResult(), ToolCallResponse.class);
            assertFalse(toolResponse.isError());
        }
    }

    @Nested
    @DisplayName("Unknown method handling")
    class UnknownMethodTests {

        @Test
        @DisplayName("should return error for unknown method")
        void shouldReturnErrorForUnknownMethod() {
            McpRequest request = new McpRequest("unknown/method", null, "8");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.METHOD_NOT_FOUND, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("unknown/method"));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle tool execution errors gracefully")
        void shouldHandleToolExecutionErrors() {
            // Create a tool that throws an exception
            McpTool errorTool = new ErrorThrowingTool();
            ToolRegistry registryWithErrorTool = new ToolRegistry(List.of(errorTool));
            McpEndpoint endpointWithErrorTool = new McpEndpoint(registryWithErrorTool);

            Map<String, Object> params = Map.of(
                    "name", "error_tool",
                    "arguments", Map.of()
            );
            McpRequest request = new McpRequest("tools/call", params, "9");

            McpResponse response = endpointWithErrorTool.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INTERNAL_ERROR, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("Tool execution failed"));
        }

        @Test
        @DisplayName("should return error when method is null")
        void shouldReturnErrorWhenMethodNull() {
            McpRequest request = new McpRequest(null, null, "10");

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_REQUEST, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("method"));
        }

        @Test
        @DisplayName("should return error for invalid JSON-RPC version")
        void shouldReturnErrorForInvalidJsonRpcVersion() {
            McpRequest request = new McpRequest("tools/list", null, "11");
            request.setJsonrpc("1.0");  // Invalid version

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_REQUEST, response.getError().getCode());
            assertTrue(response.getError().getMessage().contains("JSON-RPC version"));
        }

        @Test
        @DisplayName("should return error when JSON-RPC version is null")
        void shouldReturnErrorWhenJsonRpcVersionNull() {
            McpRequest request = new McpRequest("tools/list", null, "12");
            request.setJsonrpc(null);

            McpResponse response = endpoint.handle(request);

            assertNotNull(response);
            assertNotNull(response.getError());
            assertEquals(McpError.INVALID_REQUEST, response.getError().getCode());
        }
    }

    /**
     * Test tool implementation for unit tests.
     */
    static class TestTool implements McpTool {

        @Override
        public String getName() {
            return "test_tool";
        }

        @Override
        public String getDescription() {
            return "A test tool for unit testing";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "input", Map.of("type", "string", "description", "Test input")
                    )
            );
        }

        @Override
        public ToolCallResponse execute(ToolCallRequest request) {
            String input = request.getArgumentAsString("input");
            if (input != null) {
                return ToolCallResponse.success("Received input: " + input);
            }
            return ToolCallResponse.success("No input provided");
        }
    }

    /**
     * Tool that throws an exception for testing error handling.
     */
    static class ErrorThrowingTool implements McpTool {

        @Override
        public String getName() {
            return "error_tool";
        }

        @Override
        public String getDescription() {
            return "A tool that throws errors for testing";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            return Map.of("type", "object");
        }

        @Override
        public ToolCallResponse execute(ToolCallRequest request) {
            throw new RuntimeException("Intentional test error");
        }
    }
}
