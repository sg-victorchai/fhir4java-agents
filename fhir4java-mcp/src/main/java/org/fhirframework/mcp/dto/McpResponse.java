package org.fhirframework.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for MCP (Model Context Protocol) messages.
 * <p>
 * Follows the JSON-RPC 2.0 style structure used by MCP protocol.
 * Either result or error will be present, but not both.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("result")
    private Object result;

    @JsonProperty("error")
    private McpError error;

    @JsonProperty("id")
    private String id;

    public McpResponse() {
    }

    private McpResponse(Object result, McpError error, String id) {
        this.result = result;
        this.error = error;
        this.id = id;
    }

    /**
     * Creates a successful response with the given result.
     */
    public static McpResponse success(Object result, String id) {
        return new McpResponse(result, null, id);
    }

    /**
     * Creates an error response with the given error.
     */
    public static McpResponse error(McpError error, String id) {
        return new McpResponse(null, error, id);
    }

    /**
     * Creates an error response with a simple error message.
     */
    public static McpResponse error(String message, String id) {
        return new McpResponse(null, new McpError(McpError.INTERNAL_ERROR, message), id);
    }

    /**
     * Creates a method not found error response.
     */
    public static McpResponse methodNotFound(String method, String id) {
        return new McpResponse(null, McpError.methodNotFound(method), id);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public McpError getError() {
        return error;
    }

    public void setError(McpError error) {
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns true if this response is an error.
     */
    public boolean isError() {
        return error != null;
    }

    @Override
    public String toString() {
        return "McpResponse{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", result=" + result +
                ", error=" + error +
                ", id='" + id + '\'' +
                '}';
    }
}
