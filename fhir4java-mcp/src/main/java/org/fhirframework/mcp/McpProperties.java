package org.fhirframework.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Model Context Protocol (MCP) integration.
 * <p>
 * Binds to {@code fhir4java.mcp.*} properties in application.yml.
 * </p>
 * <p>
 * Example configuration:
 * <pre>
 * fhir4java:
 *   mcp:
 *     enabled: true
 *     base-path: /api/mcp
 * </pre>
 * </p>
 */
@ConfigurationProperties(prefix = "fhir4java.mcp")
public class McpProperties {

    /**
     * Whether MCP integration is enabled.
     * Defaults to true.
     */
    private boolean enabled = true;

    /**
     * Base path for MCP endpoints.
     * Defaults to "/api/mcp".
     */
    private String basePath = "/api/mcp";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
