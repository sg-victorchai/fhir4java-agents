package org.fhirframework.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request DTO for MCP (Model Context Protocol) messages.
 * <p>
 * Follows the JSON-RPC 2.0 style structure used by MCP protocol.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpRequest {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private Map<String, Object> params;

    @JsonProperty("id")
    private Object id;  // Object to preserve numeric (e.g. 1) vs string (e.g. "1") IDs per JSON-RPC 2.0 spec

    public McpRequest() {
    }

    public McpRequest(String method, Map<String, Object> params, Object id) {
        this.method = method;
        this.params = params;
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params == null ? null : Map.copyOf(params);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "McpRequest{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", method='" + method + '\'' +
                ", params=" + params +
                ", id=" + id +
                '}';
    }
}