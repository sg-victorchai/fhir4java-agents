package org.fhirframework.core.discovery;

import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationRegistry;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.SearchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for discovering FHIR server capabilities.
 * <p>
 * Provides a unified interface for AI agents and MCP tools to query
 * server capabilities including available resources, search parameters,
 * and extended operations.
 * </p>
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final ResourceRegistry resourceRegistry;
    private final SearchParameterRegistry searchParameterRegistry;
    private final OperationRegistry operationRegistry;

    /**
     * Creates a new DiscoveryService.
     *
     * @param resourceRegistry        the resource registry for resource configurations
     * @param searchParameterRegistry the search parameter registry
     * @param operationRegistry       the operation registry for extended operations
     */
    public DiscoveryService(ResourceRegistry resourceRegistry,
                            SearchParameterRegistry searchParameterRegistry,
                            OperationRegistry operationRegistry) {
        this.resourceRegistry = resourceRegistry;
        this.searchParameterRegistry = searchParameterRegistry;
        this.operationRegistry = operationRegistry;
    }

    /**
     * Discovers server capabilities based on the specified topic.
     *
     * @param topic        the discovery topic (RESOURCES, SEARCH_PARAMS, OPERATIONS, ALL)
     * @param resourceType optional resource type to filter results (required for SEARCH_PARAMS)
     * @param version      optional FHIR version to filter results (defaults to R5)
     * @return DiscoveryResponse containing the requested capability information
     */
    public DiscoveryResponse discover(DiscoveryTopic topic, String resourceType, FhirVersion version) {
        log.debug("Discovering capabilities: topic={}, resourceType={}, version={}",
                topic, resourceType, version);

        return switch (topic) {
            case RESOURCES -> discoverResources(version);
            case SEARCH_PARAMS -> discoverSearchParameters(resourceType, version);
            case OPERATIONS -> discoverOperations(resourceType, version);
            case EVENT_CAPABILITIES -> throw new UnsupportedOperationException(
                    "EVENT_CAPABILITIES is handled by FhirDiscoverTool directly");
            case ALL -> discoverAll(resourceType, version);
        };
    }

    /**
     * Discovers available FHIR resources.
     */
    private DiscoveryResponse discoverResources(FhirVersion version) {
        List<ResourceConfiguration> resources;

        if (version != null) {
            resources = resourceRegistry.getResourcesForVersion(version);
        } else {
            resources = resourceRegistry.getEnabledResources();
        }

        List<DiscoveryResponse.ResourceInfo> resourceInfos = resources.stream()
                .map(this::toResourceInfo)
                .toList();

        log.debug("Discovered {} resources", resourceInfos.size());
        return DiscoveryResponse.withResources(resourceInfos);
    }

    /**
     * Converts a ResourceConfiguration to ResourceInfo.
     */
    private DiscoveryResponse.ResourceInfo toResourceInfo(ResourceConfiguration config) {
        return new DiscoveryResponse.ResourceInfo(
                config.getResourceType(),
                config.isEnabled(),
                config.getSupportedVersions(),
                config.getEnabledInteractions()
        );
    }

    /**
     * Discovers search parameters for a specific resource type.
     */
    private DiscoveryResponse discoverSearchParameters(String resourceType, FhirVersion version) {
        if (resourceType == null || resourceType.isBlank()) {
            log.debug("No resource type specified for search parameter discovery");
            return DiscoveryResponse.withSearchParameters(Collections.emptyList());
        }

        FhirVersion effectiveVersion = version != null ? version : FhirVersion.R5;
        List<SearchParameter> params = searchParameterRegistry.getSearchParameters(effectiveVersion, resourceType);

        List<DiscoveryResponse.SearchParameterInfo> paramInfos = params.stream()
                .map(this::toSearchParameterInfo)
                .toList();

        log.debug("Discovered {} search parameters for resource type {}", paramInfos.size(), resourceType);
        return DiscoveryResponse.withSearchParameters(paramInfos);
    }

    /**
     * Converts a HAPI SearchParameter to SearchParameterInfo.
     */
    private DiscoveryResponse.SearchParameterInfo toSearchParameterInfo(SearchParameter sp) {
        String type = sp.getType() != null ? sp.getType().toCode() : "unknown";
        return new DiscoveryResponse.SearchParameterInfo(
                sp.getCode() != null ? sp.getCode() : "unknown",
                type,
                sp.getExpression() != null ? sp.getExpression() : "",
                sp.getDescription()
        );
    }

    /**
     * Discovers available extended operations.
     */
    private DiscoveryResponse discoverOperations(String resourceType, FhirVersion version) {
        Set<String> operationNames = operationRegistry.getRegisteredOperations();

        if (operationNames.isEmpty()) {
            return DiscoveryResponse.withOperations(Collections.emptyList());
        }

        // Collect unique operations with their handlers
        Map<String, DiscoveryResponse.OperationInfo> operationMap = new LinkedHashMap<>();

        for (OperationScope scope : OperationScope.values()) {
            List<OperationHandler> handlers = operationRegistry.getHandlers(scope, resourceType);

            for (OperationHandler handler : handlers) {
                String name = handler.getOperationName();
                if (!operationMap.containsKey(name)) {
                    operationMap.put(name, toOperationInfo(handler));
                }
            }
        }

        List<DiscoveryResponse.OperationInfo> operations = new ArrayList<>(operationMap.values());
        log.debug("Discovered {} operations", operations.size());
        return DiscoveryResponse.withOperations(operations);
    }

    /**
     * Converts an OperationHandler to OperationInfo.
     */
    private DiscoveryResponse.OperationInfo toOperationInfo(OperationHandler handler) {
        List<String> resourceTypes = handler.getResourceTypes() != null
                ? List.of(handler.getResourceTypes())
                : Collections.emptyList();

        Set<OperationScope> scopes = handler.getScopes() != null
                ? EnumSet.copyOf(List.of(handler.getScopes()))
                : EnumSet.noneOf(OperationScope.class);

        return new DiscoveryResponse.OperationInfo(
                handler.getOperationName() != null ? handler.getOperationName() : "unknown",
                resourceTypes,
                scopes
        );
    }

    /**
     * Discovers all topics combined.
     */
    private DiscoveryResponse discoverAll(String resourceType, FhirVersion version) {
        DiscoveryResponse resources = discoverResources(version);
        DiscoveryResponse searchParams = discoverSearchParameters(resourceType, version);
        DiscoveryResponse operations = discoverOperations(resourceType, version);

        return new DiscoveryResponse(
                resources.resources(),
                searchParams.searchParameters(),
                operations.operations()
        );
    }
}
