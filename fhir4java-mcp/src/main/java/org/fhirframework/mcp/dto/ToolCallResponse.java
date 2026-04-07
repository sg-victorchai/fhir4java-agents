package org.fhirframework.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for tool execution results in MCP protocol.
 * <p>
 * Contains the content returned by the tool and metadata about the execution.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallResponse {

    @JsonProperty("content")
    private List<ContentBlock> content;

    @JsonProperty("isError")
    private boolean isError;

    public ToolCallResponse() {
    }

    private ToolCallResponse(List<ContentBlock> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    /**
     * Creates a successful response with text content.
     */
    public static ToolCallResponse success(String text) {
        return new ToolCallResponse(
                List.of(new ContentBlock("text", text)),
                false
        );
    }

    /**
     * Creates a successful response with text content and hints.
     */
    public static ToolCallResponse success(String text, String hints) {
        ContentBlock contentBlock = new ContentBlock("text", text);
        contentBlock.setHints(hints);
        return new ToolCallResponse(List.of(contentBlock), false);
    }

    /**
     * Creates an error response.
     */
    public static ToolCallResponse error(String errorMessage) {
        return new ToolCallResponse(
                List.of(new ContentBlock("text", errorMessage)),
                true
        );
    }

    /**
     * Creates an error response with hints.
     */
    public static ToolCallResponse error(String errorMessage, String hints) {
        ContentBlock contentBlock = new ContentBlock("text", errorMessage);
        contentBlock.setHints(hints);
        return new ToolCallResponse(List.of(contentBlock), true);
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public void setContent(List<ContentBlock> content) {
        this.content = content;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean isError) {
        this.isError = isError;
    }

    /**
     * Returns the first text content if available.
     */
    @JsonIgnore
    public String getFirstTextContent() {
        if (content != null && !content.isEmpty()) {
            return content.get(0).getText();
        }
        return null;
    }

    @Override
    public String toString() {
        return "ToolCallResponse{" +
                "content=" + content +
                ", isError=" + isError +
                '}';
    }

    /**
     * Content block in MCP response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentBlock {

        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        private String text;

        @JsonProperty("hints")
        private String hints;

        public ContentBlock() {
        }

        public ContentBlock(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getHints() {
            return hints;
        }

        public void setHints(String hints) {
            this.hints = hints;
        }

        @Override
        public String toString() {
            return "ContentBlock{" +
                    "type='" + type + '\'' +
                    ", text='" + text + '\'' +
                    ", hints='" + hints + '\'' +
                    '}';
        }
    }
}
