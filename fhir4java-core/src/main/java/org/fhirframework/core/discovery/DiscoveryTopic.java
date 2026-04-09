package org.fhirframework.core.discovery;

/**
 * Topics that can be discovered through the DiscoveryService.
 * <p>
 * Used by MCP tools to query server capabilities in a structured way.
 * </p>
 */
public enum DiscoveryTopic {

    /**
     * Discover available FHIR resource types and their configurations.
     */
    RESOURCES,

    /**
     * Discover search parameters for resource types.
     */
    SEARCH_PARAMS,

    /**
     * Discover available extended operations.
     */
    OPERATIONS,

    /**
     * Discover all topics combined (resources, search params, and operations).
     */
    ALL
}
