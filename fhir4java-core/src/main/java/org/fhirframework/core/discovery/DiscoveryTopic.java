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
     * Discover event subscription capabilities (SSE, webhooks, FHIR Subscriptions).
     * <p>
     * Returns information about:
     * <ul>
     *   <li>SSE endpoint and supported filters</li>
     *   <li>Webhook registration endpoint and topic patterns</li>
     *   <li>FHIR Subscription channels</li>
     * </ul>
     * </p>
     */
    EVENT_CAPABILITIES,

    /**
     * Discover all topics combined (resources, search params, and operations).
     */
    ALL
}
