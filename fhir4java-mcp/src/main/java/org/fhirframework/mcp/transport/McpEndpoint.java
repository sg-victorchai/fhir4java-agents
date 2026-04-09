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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST endpoint for MCP (Model Context Protocol) over HTTP.
 * <p>
 * Handles JSON-RPC 2.0 style requests for MCP protocol operations including
 * session initialization, tool listing, and tool execution.
 * </p>
 *
 * <h3>Supported Methods:</h3>
 * <ul>
 *   <li>{@code initialize} - Initialize the MCP session (must be called first)</li>
 *   <li>{@code notifications/initialized} - Client notification that initialization is complete</li>
 *   <li>{@code tools/list} - Returns list of available tools</li>
 *   <li>{@code tools/call} - Invokes a specific tool with arguments</li>
 * </ul>
 *
 * <h3>Session Initialization Example:</h3>
 * <pre>{@code
 * POST /api/mcp
 * Content-Type: application/json
 *
 * {
 *   "jsonrpc": "2.0",
 *   "method": "initialize",
 *   "params": {
 *     "protocolVersion": "2024-11-05",
 *     "capabilities": {},
 *     "clientInfo": {
 *       "name": "vscode",
 *       "version": "1.0.0"
 *     }
 *   },
 *   "id": "1"
 * }
 * }</pre>
 *
 * <h3>Tool List Example:</h3>
 * <pre>{@code
 * POST /api/mcp
 * Content-Type: application/json
 *
 * {
 *   "jsonrpc": "2.0",
 *   "method": "tools/list",
 *   "id": "2"
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/mcp")
public class McpEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpEndpoint.class);

    // MCP Protocol methods
    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_INITIALIZED = "notifications/initialized";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";

    private static final String JSON_RPC_VERSION = "2.0";

    // MCP Protocol version - using the standard MCP protocol version
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    // Server info
    private static final String SERVER_NAME = "fhir4java-mcp";

    @Value("${fhir4java.mcp.version:1.0.0}")
    private String serverVersion;

    private final ToolRegistry toolRegistry;

    // Track if the session has been initialized
    private volatile boolean initialized = false;

    public McpEndpoint(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Handles the MCP initialize request.
     * <p>
     * This is the first message sent by the client to establish the MCP session.
     * The server responds with its capabilities and protocol version.
     * </p>
     *
     * @param params the initialization parameters from the client
     * @param id the request ID
     * @return the initialization response with server info and capabilities
     */
    private McpResponse initialize(Map<String, Object> params, String id) {
        log.info("MCP initialize request received");

        // Extract client info for logging
        if (params != null) {
            Object clientInfo = params.get("clientInfo");
            if (clientInfo instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> info = (Map<String, Object>) clientInfo;
                log.info("MCP client connecting: name={}, version={}",
                        info.get("name"), info.get("version"));
            }

            // Log requested protocol version
            Object requestedVersion = params.get("protocolVersion");
            if (requestedVersion != null) {
                log.debug("Client requested protocol version: {}", requestedVersion);
            }
        }

        // Build server capabilities
        Map<String, Object> capabilities = new LinkedHashMap<>();

        // Tools capability - we support tools
        Map<String, Object> toolsCapability = new LinkedHashMap<>();
        toolsCapability.put("listChanged", false); // We don't support dynamic tool list changes yet
        capabilities.put("tools", toolsCapability);

        // Build server info
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", serverVersion != null ? serverVersion : "1.0.0");

        // Build the result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);

        log.info("MCP server initialized: name={}, version={}, protocolVersion={}",
                SERVER_NAME, serverVersion, MCP_PROTOCOL_VERSION);

        return McpResponse.success(result, id);
    }

    /**
     * Handles the notifications/initialized notification from the client.
     * <p>
     * This is sent by the client after it has processed the initialize response.
     * It signals that the client is ready to begin normal operations.
     * </p>
     *
     * @param id the request ID (may be null for notifications)
     * @return an empty success response
     */
    private McpResponse handleInitialized(String id) {
        log.info("MCP client initialized notification received - session is now active");
        initialized = true;

        // For notifications, we can return an empty success or just acknowledge
        // MCP spec says notifications don't require a response, but we'll send one for HTTP
        return McpResponse.success(Map.of(), id);
    }

    /**
     * Checks if the MCP session has been initialized.
     *
     * @return true if the session is initialized
     */
    public boolean isInitialized() {
        return initialized;
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
                case METHOD_INITIALIZE -> initialize(request.getParams(), id);
                case METHOD_INITIALIZED -> handleInitialized(id);
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
