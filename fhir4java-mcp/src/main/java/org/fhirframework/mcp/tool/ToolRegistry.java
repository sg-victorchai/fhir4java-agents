package org.fhirframework.mcp.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP tools.
 * <p>
 * Automatically collects all {@link McpTool} beans from the Spring context
 * and provides methods to query and retrieve them.
 * </p>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    /**
     * Creates a new ToolRegistry and registers all provided tools.
     *
     * @param mcpTools collection of McpTool beans from Spring context
     */
    public ToolRegistry(List<McpTool> mcpTools) {
        for (McpTool tool : mcpTools) {
            registerTool(tool);
        }
        log.info("ToolRegistry initialized with {} tools: {}", tools.size(), tools.keySet());
    }

    /**
     * Registers a tool in the registry.
     *
     * @param tool the tool to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    public void registerTool(McpTool tool) {
        String name = tool.getName();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool with name '" + name + "' is already registered");
        }
        tools.put(name, tool);
        log.debug("Registered MCP tool: {}", name);
    }

    /**
     * Gets a tool by name.
     *
     * @param name the tool name
     * @return Optional containing the tool if found
     */
    public Optional<McpTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Gets all registered tools.
     *
     * @return unmodifiable collection of all tools
     */
    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Lists tool definitions in MCP protocol format.
     * <p>
     * Returns a list of tool definition maps suitable for the tools/list response.
     * </p>
     *
     * @return list of tool definitions
     */
    public List<Map<String, Object>> listToolDefinitions() {
        return tools.values().stream()
                .map(this::toToolDefinition)
                .toList();
    }

    /**
     * Returns the number of registered tools.
     *
     * @return tool count
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Checks if a tool with the given name is registered.
     *
     * @param name the tool name
     * @return true if the tool exists
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Converts a tool to its MCP definition format.
     */
    private Map<String, Object> toToolDefinition(McpTool tool) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", tool.getName());
        definition.put("description", tool.getDescription());
        definition.put("inputSchema", tool.getInputSchema());
        return definition;
    }
}
