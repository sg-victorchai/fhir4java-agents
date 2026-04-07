package org.fhirframework.core.discovery;

import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.version.FhirVersion;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Response DTO for the DiscoveryService containing server capability information.
 * <p>
 * Contains information about available resources, search parameters, and operations
 * that can be used by AI agents and MCP tools to understand server capabilities.
 * </p>
 */
public record DiscoveryResponse(
        List<ResourceInfo> resources,
        List<SearchParameterInfo> searchParameters,
        List<OperationInfo> operations
) {
    /**
     * Creates an empty DiscoveryResponse.
     */
    public static DiscoveryResponse empty() {
        return new DiscoveryResponse(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    /**
     * Creates a DiscoveryResponse with only resources.
     */
    public static DiscoveryResponse withResources(List<ResourceInfo> resources) {
        return new DiscoveryResponse(resources, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Creates a DiscoveryResponse with only search parameters.
     */
    public static DiscoveryResponse withSearchParameters(List<SearchParameterInfo> searchParameters) {
        return new DiscoveryResponse(Collections.emptyList(), searchParameters, Collections.emptyList());
    }

    /**
     * Creates a DiscoveryResponse with only operations.
     */
    public static DiscoveryResponse withOperations(List<OperationInfo> operations) {
        return new DiscoveryResponse(Collections.emptyList(), Collections.emptyList(), operations);
    }

    /**
     * Information about a FHIR resource type.
     *
     * @param resourceType      the resource type name (e.g., "Patient", "Observation")
     * @param enabled           whether the resource is enabled
     * @param supportedVersions the FHIR versions this resource supports
     * @param interactions      the enabled interactions for this resource
     */
    public record ResourceInfo(
            String resourceType,
            boolean enabled,
            Set<FhirVersion> supportedVersions,
            Set<InteractionType> interactions
    ) {
    }

    /**
     * Information about a search parameter.
     *
     * @param name        the search parameter name (e.g., "identifier", "family")
     * @param type        the search parameter type (e.g., "token", "string", "reference")
     * @param expression  the FHIRPath expression defining the parameter
     * @param description optional description of the parameter
     */
    public record SearchParameterInfo(
            String name,
            String type,
            String expression,
            String description
    ) {
        /**
         * Creates a SearchParameterInfo without a description.
         */
        public SearchParameterInfo(String name, String type, String expression) {
            this(name, type, expression, null);
        }
    }

    /**
     * Information about an extended operation.
     *
     * @param name          the operation name without $ prefix (e.g., "validate", "everything")
     * @param resourceTypes the resource types this operation applies to (empty for system-level)
     * @param scopes        the scopes at which this operation can be invoked
     */
    public record OperationInfo(
            String name,
            List<String> resourceTypes,
            Set<OperationScope> scopes
    ) {
    }
}
