package org.fhirframework.mcp.transport;

import org.fhirframework.mcp.dto.McpError;
import org.fhirframework.mcp.dto.McpRequest;
import org.fhirframework.mcp.dto.McpResponse;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.tool.McpTool;
import org.fhirframework.mcp.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoint for MCP (Model Context Protocol) over HTTP.
 * <p>
 * Handles JSON-RPC style requests for tool listing and execution.
 * </p>
 *
 * <h3>Supported Methods:</h3>
 * <ul>
 *   <li>{@code tools/list} - Returns list of available tools</li>
 *   <li>{@code tools/call} - Invokes a specific tool with arguments</li>
 * </ul>
 *
 * <h3>Example Request:</h3>
 * <pre>{@code
 * POST /api/mcp
 * Content-Type: application/json
 *
 * {
 *   "jsonrpc": "2.0",
 *   "method": "tools/list",
 *   "id": "1"
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/mcp")
public class McpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpEndpoint.class);

    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";

    private static final String JSON_RPC_VERSION = "2.0";

    private final ToolRegistry toolRegistry;

    public McpEndpoint(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Handles MCP protocol requests.
     *
     * @param request the MCP request
     * @return the MCP response
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public McpResponse handle(@RequestBody McpRequest request) {
        String method = request.getMethod();
        String id = request.getId();
        String jsonrpc = request.getJsonrpc();

        log.debug("MCP request: method={}, id={}", method, id);

        try {
            // Validate JSON-RPC version
            if (!JSON_RPC_VERSION.equals(jsonrpc)) {
                log.warn("Invalid JSON-RPC version: {}", jsonrpc);
                return McpResponse.error(
                        McpError.invalidRequest("Invalid JSON-RPC version. Expected '2.0'"),
                        id
                );
            }

            // Validate method is present
            if (method == null) {
                log.warn("Missing method in MCP request");
                return McpResponse.error(
                        McpError.invalidRequest("Missing 'method' field"),
                        id
                );
            }

            return switch (method) {
                case METHOD_TOOLS_LIST -> listTools(id);
                case METHOD_TOOLS_CALL -> callTool(request.getParams(), id);
                default -> {
                    log.warn("Unknown MCP method: {}", method);
                    yield McpResponse.methodNotFound(method, id);
                }
            };
        } catch (Exception e) {
            log.error("Error processing MCP request", e);
            return McpResponse.error(
                    McpError.internalError("Internal server error"),
                    id
            );
        }
    }

    /**
     * Handles tools/list method.
     */
    private McpResponse listTools(String id) {
        log.debug("Listing {} tools", toolRegistry.getToolCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", toolRegistry.listToolDefinitions());

        return McpResponse.success(result, id);
    }

    /**
     * Handles tools/call method.
     */
    private McpResponse callTool(Map<String, Object> params, String id) {
        if (params == null) {
            return McpResponse.error(
                    McpError.invalidParams("Missing params"),
                    id
            );
        }

        // Extract tool name from params
        Object nameObj = params.get("name");
        if (nameObj == null || !(nameObj instanceof String)) {
            return McpResponse.error(
                    McpError.invalidParams("Missing or invalid 'name' parameter"),
                    id
            );
        }
        String toolName = (String) nameObj;

        // Extract arguments from params
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = params.get("arguments") instanceof Map
                ? (Map<String, Object>) params.get("arguments")
                : Map.of();

        log.debug("Calling tool: {} with arguments: {}", toolName, arguments);

        // Find the tool
        Optional<McpTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            log.warn("Tool not found: {}", toolName);
            return McpResponse.error(
                    new McpError(McpError.METHOD_NOT_FOUND, "Tool not found: " + toolName),
                    id
            );
        }

        // Execute the tool
        McpTool tool = toolOpt.get();
        ToolCallRequest toolRequest = new ToolCallRequest(toolName, arguments);

        try {
            ToolCallResponse toolResponse = tool.execute(toolRequest);
            return McpResponse.success(toolResponse, id);
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return McpResponse.error(
                    McpError.internalError("Tool execution failed"),
                    id
            );
        }
    }
}
