package org.fhirframework.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for the Model Context Protocol (MCP) integration.
 * <p>
 * Provides AI-ready capabilities for the FHIR server by exposing
 * MCP-compliant endpoints for tool discovery and execution.
 * </p>
 * <p>
 * This configuration is enabled by default but can be disabled by setting
 * {@code fhir4java.mcp.enabled=false} in the application configuration.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
@ConditionalOnProperty(name = "fhir4java.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAutoConfiguration.class);

    private final McpProperties properties;

    public McpAutoConfiguration(McpProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        log.info("MCP integration enabled with base path: {}", properties.getBasePath());
    }
}
