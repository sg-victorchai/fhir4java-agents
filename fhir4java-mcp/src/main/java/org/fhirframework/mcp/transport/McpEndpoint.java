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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
@org.springframework.web.bind.annotation.CrossOrigin(
		origins = "*",
		allowedHeaders = "*",
		methods = {
				org.springframework.web.bind.annotation.RequestMethod.GET,
				org.springframework.web.bind.annotation.RequestMethod.POST,
				org.springframework.web.bind.annotation.RequestMethod.OPTIONS,
				org.springframework.web.bind.annotation.RequestMethod.DELETE
		},
		exposedHeaders = {"Mcp-Session-Id", "Content-Type"}
		)
public class McpEndpoint {

	private static final Logger log = LoggerFactory.getLogger(McpEndpoint.class);

	// MCP Protocol methods
	private static final String METHOD_INITIALIZE = "initialize";
	private static final String METHOD_INITIALIZED = "notifications/initialized";
	private static final String METHOD_TOOLS_LIST = "tools/list";
	private static final String METHOD_TOOLS_CALL = "tools/call";

	private static final String JSON_RPC_VERSION = "2.0";

	// MCP Protocol version - using the standard MCP protocol version
	private static final String MCP_PROTOCOL_VERSION = "2025-11-25";

	// Server info
	private static final String SERVER_NAME = "fhir4java-mcp";

	private final ObjectMapper objectMapper = new ObjectMapper();

	// Store active SSE emitters for server-to-client push (keyed by session)
	private final java.util.concurrent.ConcurrentHashMap<String, SseEmitter> sseEmitters
	= new java.util.concurrent.ConcurrentHashMap<>();

	// Session ID assigned on initialize - required by MCP 2025-11-25 spec
	private volatile String sessionId = null;

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

			Object requestedVersion = params.get("protocolVersion");
			if (requestedVersion != null) {
				log.debug("Client requested protocol version: {}", requestedVersion);
			}
		}

		// Assign a session ID for this connection (required by MCP 2025-11-25)
		sessionId = UUID.randomUUID().toString();
		log.debug("Assigned MCP session ID: {}", sessionId);

		// Build server capabilities.
		// tools: empty object {} means tools are supported but listChanged notifications
		// are NOT sent (omitting listChanged is equivalent to false per spec).
		Map<String, Object> toolsCapability = new LinkedHashMap<>();
		Map<String, Object> capabilities = new LinkedHashMap<>();
		capabilities.put("tools", toolsCapability);

		// Build server info
		Map<String, Object> serverInfo = new LinkedHashMap<>();
		serverInfo.put("name", SERVER_NAME);
		serverInfo.put("version", serverVersion != null ? serverVersion : "1.0.0");

		// Build the result per MCP 2025-11-25 spec
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("protocolVersion", MCP_PROTOCOL_VERSION);
		result.put("capabilities", capabilities);
		result.put("serverInfo", serverInfo);
		// instructions is optional but helps clients understand the server's purpose
		result.put("instructions", "FHIR4Java MCP server providing FHIR R5 tools for querying and managing FHIR resources.");

		// Mark as initialized immediately - do not wait for notifications/initialized
		initialized = true;

		log.info("MCP server initialized: name={}, version={}, protocolVersion={}, sessionId={}",
				SERVER_NAME, serverVersion, MCP_PROTOCOL_VERSION, sessionId);

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
		// Return null to signal the HTTP layer (handle(McpRequest, HttpServletRequest))
		// to send 202 Accepted with no body, as required by MCP 2025-11-25 spec.
		return null;
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
	 * SSE endpoint for MCP Streamable HTTP transport (2025-11-25).
	 * <p>
	 * Per the MCP 2025-11-25 Streamable HTTP spec, the GET SSE stream is a
	 * <em>server-to-client</em> channel only. The client POSTs initialize first,
	 * then opens this stream to receive server-initiated messages. The stream
	 * must stay open silently — do NOT send an {@code endpoint} event here,
	 * that belongs to the older HTTP+SSE transport and will confuse Streamable
	 * HTTP clients like VSCode into an infinite wait.
	 * </p>
	 * <p>Expected flow:</p>
	 * <ol>
	 *   <li>Client {@code POST /api/mcp} → {@code initialize} → {@code 200 OK} JSON + {@code Mcp-Session-Id}</li>
	 *   <li>Client {@code GET /api/mcp} with {@code Mcp-Session-Id} → SSE stream opens (ping to flush)</li>
	 *   <li>Client {@code POST /api/mcp} → {@code notifications/initialized} → {@code 202 Accepted}</li>
	 *   <li>Client {@code POST /api/mcp} → {@code tools/list}, {@code tools/call}, etc.</li>
	 * </ol>
	 */
	@GetMapping(value = "/api/mcp", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> streamableHttpSse(HttpServletRequest request) {
		String incomingSessionId = request.getHeader("Mcp-Session-Id");
		log.info("MCP GET /api/mcp - opening Streamable HTTP SSE channel, incomingSession={}",
				incomingSessionId);

		// Use the incoming session ID if present, otherwise generate a temporary one
		String emitterId = (incomingSessionId != null) ? incomingSessionId : UUID.randomUUID().toString();
		log.debug("Using SSE emitter ID: {}", emitterId);

		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		sseEmitters.put(emitterId, emitter);

		emitter.onCompletion(() -> {
			log.debug("MCP SSE stream completed for session: {}", emitterId);
			sseEmitters.remove(emitterId);
		});
		emitter.onTimeout(() -> {
			log.debug("MCP SSE stream timed out for session: {}", emitterId);
			sseEmitters.remove(emitterId);
			emitter.complete();
		});
		emitter.onError(e -> {
			log.debug("MCP SSE stream error for session {}: {}", emitterId, e.getMessage());
			sseEmitters.remove(emitterId);
		});

		// Send a comment to flush HTTP headers/buffers immediately.
		// Tomcat buffers the response until bytes are written — without this the
		// client never sees the open connection. SSE comments are invisible to the
		// MCP protocol layer. Do NOT send an 'endpoint' event — that is the old
		// HTTP+SSE transport protocol and will break Streamable HTTP clients.
		try {
			emitter.send(SseEmitter.event().comment("ping"));
			log.debug("MCP SSE headers flushed for session: {}", emitterId);
		} catch (Exception e) {
			log.warn("Failed to send initial SSE ping for session {}: {}", emitterId, e.getMessage());
		}

		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.header("Cache-Control", "no-cache, no-transform")
				.header("X-Accel-Buffering", "no")
				.header("Mcp-Session-Id", emitterId)
				.body(emitter);
	}



	/**
	 * Processes the core MCP request logic and returns a {@link McpResponse}.
	 * <p>
	 * This method contains the pure protocol logic with no HTTP concerns,
	 * making it suitable for direct unit testing.
	 * </p>
	 *
	 * @param request the MCP request
	 * @return the MCP response
	 */
	McpResponse handle(McpRequest request) {
		String method = request.getMethod();
		String id = request.getId();
		String jsonrpc = request.getJsonrpc();

		log.debug("MCP request: method={}, id={}", method, id);

		try {
			if (!JSON_RPC_VERSION.equals(jsonrpc)) {
				log.warn("Invalid JSON-RPC version: {}", jsonrpc);
				return McpResponse.error(
						McpError.invalidRequest("Invalid JSON-RPC version. Expected '2.0'"), id);
			} else if (method == null) {
				log.warn("Missing method in MCP request");
				return McpResponse.error(
						McpError.invalidRequest("Missing 'method' field"), id);
			} else {
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
			}
		} catch (Exception e) {
			log.error("Error processing MCP request", e);
			return McpResponse.error(McpError.internalError("Internal server error"), id);
		}
	}

	/**
	 * Handles MCP protocol POST requests using Streamable HTTP transport.
	 * <p>
	 * Uses {@code void} return type so Spring's {@code DispatcherServlet} treats the
	 * response as already handled and does NOT invoke {@code FhirExceptionHandler}.
	 * </p>
	 * <p>Per MCP 2025-11-25 spec, ALL POST responses are plain {@code application/json}.</p>
	 * <p>The {@code Accept: text/event-stream} header on the POST request means the client
	 * <em>can</em> accept SSE, but POST responses must still be plain JSON — SSE is only
	 * used on the separate GET channel for server-initiated messages.</p>
	 * <ul>
	 *   <li>{@code notifications/initialized} → HTTP 202 Accepted, no body</li>
	 *   <li>{@code initialize} → HTTP 200, plain JSON + {@code Mcp-Session-Id} header</li>
	 *   <li>All other methods → HTTP 200, plain JSON</li>
	 * </ul>
	 */
	@PostMapping(
			value = {"/api/mcp", "/api/mcp/message"},
			consumes = MediaType.APPLICATION_JSON_VALUE
			)
	public void handle(@RequestBody McpRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpServletResponse) throws Exception {
		String method = request.getMethod();

		// notifications/initialized: 202 Accepted, no body
		if (METHOD_INITIALIZED.equals(method)) {
			log.info("MCP client initialized notification received - session is now active");
			initialized = true;
			log.debug("Returning 202 Accepted for notifications/initialized");
			httpServletResponse.setStatus(HttpServletResponse.SC_ACCEPTED);
			httpServletResponse.flushBuffer();
			return;
		}

		McpResponse response = handle(request);

		// Per MCP 2025-11-25 spec: ALL POST responses are plain application/json.
		// The Accept: text/event-stream header on the request only signals that the
		// client supports SSE on the GET channel — it does NOT mean POST responses
		// should be SSE-formatted. Responding with text/event-stream to a POST causes
		// VSCode to fail silently parsing the JSON-RPC response and stall indefinitely.
		httpServletResponse.setStatus(HttpServletResponse.SC_OK);
		httpServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
		httpServletResponse.setCharacterEncoding("UTF-8");

		if (METHOD_INITIALIZE.equals(method)) {
			httpServletResponse.setHeader("Mcp-Session-Id", sessionId);
			log.debug("Set Mcp-Session-Id header on initialize response: {}", sessionId);
		}

		String json = objectMapper.writeValueAsString(response);
		httpServletResponse.getWriter().write(json);
		httpServletResponse.getWriter().flush();

		log.debug("JSON response flushed for method: {}", method);
	}

	/**
	 * Handles MCP session termination per MCP 2025-11-25 spec §6.4.
	 * <p>
	 * The client sends {@code DELETE /api/mcp} with the {@code Mcp-Session-Id} header
	 * to cleanly terminate the session. The server closes the SSE stream and resets state.
	 * </p>
	 */
	@org.springframework.web.bind.annotation.DeleteMapping("/api/mcp")
	public ResponseEntity<Void> deleteSession(HttpServletRequest httpRequest) {
		String incomingSessionId = httpRequest.getHeader("Mcp-Session-Id");
		log.info("MCP DELETE /api/mcp - session termination, sessionId={}", incomingSessionId);

		if (incomingSessionId != null) {
			SseEmitter emitter = sseEmitters.remove(incomingSessionId);
			if (emitter != null) {
				log.debug("Closing SSE stream for terminated session: {}", incomingSessionId);
				emitter.complete();
			}
			if (incomingSessionId.equals(sessionId)) {
				sessionId = null;
				initialized = false;
				log.info("MCP session terminated and state reset: {}", incomingSessionId);
			}
		}

		return ResponseEntity.noContent().build();
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
