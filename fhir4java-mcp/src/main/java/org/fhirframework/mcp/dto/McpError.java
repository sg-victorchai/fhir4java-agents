package org.fhirframework.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error object for MCP responses following JSON-RPC 2.0 error format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpError {

    /**
     * Standard MCP/JSON-RPC error codes.
     */
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Object data;

    public McpError() {
    }

    public McpError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public McpError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * Creates a method not found error.
     */
    public static McpError methodNotFound(String method) {
        return new McpError(METHOD_NOT_FOUND, "Method not found: " + method);
    }

    /**
     * Creates an invalid params error.
     */
    public static McpError invalidParams(String details) {
        return new McpError(INVALID_PARAMS, "Invalid params: " + details);
    }

    /**
     * Creates an internal error.
     */
    public static McpError internalError(String details) {
        return new McpError(INTERNAL_ERROR, "Internal error: " + details);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "McpError{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
