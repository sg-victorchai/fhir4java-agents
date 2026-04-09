package org.fhirframework.mcp.tool;

import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;

import java.util.Map;

/**
 * Interface for MCP (Model Context Protocol) tools.
 * <p>
 * Each tool implementation provides a specific capability that can be
 * invoked by AI agents through the MCP protocol.
 * </p>
 * <p>
 * Implementations should be annotated with {@code @Component} to be
 * automatically discovered and registered by the {@link ToolRegistry}.
 * </p>
 *
 * <h3>Example Implementation:</h3>
 * <pre>{@code
 * @Component
 * public class PatientSearchTool implements McpTool {
 *     @Override
 *     public String getName() {
 *         return "fhir_search";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Search FHIR resources by type and parameters";
 *     }
 *
 *     @Override
 *     public Map<String, Object> getInputSchema() {
 *         return Map.of(
 *             "type", "object",
 *             "properties", Map.of(
 *                 "resourceType", Map.of("type", "string"),
 *                 "query", Map.of("type", "string")
 *             ),
 *             "required", List.of("resourceType")
 *         );
 *     }
 *
 *     @Override
 *     public ToolCallResponse execute(ToolCallRequest request) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 */
public interface McpTool {

    /**
     * Returns the unique name of this tool.
     * <p>
     * This name is used to identify the tool in tool/list and tools/call methods.
     * </p>
     *
     * @return the tool name, must be unique across all registered tools
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     * <p>
     * This description is shown to AI agents to help them understand
     * when and how to use the tool.
     * </p>
     *
     * @return the tool description
     */
    String getDescription();

    /**
     * Returns the JSON Schema that describes the input parameters for this tool.
     * <p>
     * The schema should follow JSON Schema specification and typically includes:
     * <ul>
     *   <li>type: usually "object"</li>
     *   <li>properties: definitions of each parameter</li>
     *   <li>required: list of required parameter names</li>
     * </ul>
     * </p>
     *
     * @return the input schema as a Map structure
     */
    Map<String, Object> getInputSchema();

    /**
     * Executes the tool with the given request parameters.
     * <p>
     * Implementations should:
     * <ul>
     *   <li>Validate input parameters</li>
     *   <li>Perform the tool's operation</li>
     *   <li>Return a response with content or an error</li>
     *   <li>Handle exceptions gracefully and return error responses</li>
     * </ul>
     * </p>
     *
     * @param request the tool call request containing arguments
     * @return the tool call response with results or error
     */
    ToolCallResponse execute(ToolCallRequest request);
}
