package org.fhirframework.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request DTO for tool invocation in MCP protocol.
 * <p>
 * Represents the parameters passed to the tools/call method.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    public ToolCallRequest() {
    }

    public ToolCallRequest(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    /**
     * Gets an argument value by key, returning null if not present.
     */
    public Object getArgument(String key) {
        return arguments != null ? arguments.get(key) : null;
    }

    /**
     * Gets an argument value as String, returning null if not present or not a string.
     */
    public String getArgumentAsString(String key) {
        Object value = getArgument(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Gets an argument value as Integer, returning null if not present or not a number.
     */
    public Integer getArgumentAsInteger(String key) {
        Object value = getArgument(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * Gets an argument value as Boolean, returning null if not present or not a boolean.
     */
    public Boolean getArgumentAsBoolean(String key) {
        Object value = getArgument(key);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    @Override
    public String toString() {
        return "ToolCallRequest{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
