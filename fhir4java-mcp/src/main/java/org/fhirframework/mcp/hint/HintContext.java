package org.fhirframework.mcp.hint;

import java.util.List;
import java.util.Map;

/**
 * Context object for hint generation.
 * <p>
 * Provides all the information needed to generate contextual hints
 * for AI agents based on the completed operation.
 * </p>
 *
 * @param toolName     the name of the MCP tool that was executed (e.g., "fhir_query", "fhir_mutate")
 * @param action       the action performed (e.g., "search", "read", "create")
 * @param resourceType the FHIR resource type involved (e.g., "Patient", "Observation")
 * @param resourceIds  the IDs of resources returned or affected by the operation
 * @param metadata     additional context information for hint generation
 */
public record HintContext(
    String toolName,
    String action,
    String resourceType,
    List<String> resourceIds,
    Map<String, Object> metadata
) {}
