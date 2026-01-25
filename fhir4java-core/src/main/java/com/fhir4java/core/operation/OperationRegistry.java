package com.fhir4java.core.operation;

import com.fhir4java.core.version.FhirVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for FHIR operation handlers.
 * <p>
 * Manages registration and lookup of operation handlers by name, scope,
 * and resource type.
 * </p>
 */
@Component
public class OperationRegistry {

    private static final Logger log = LoggerFactory.getLogger(OperationRegistry.class);

    // Map: operationName -> scope -> resourceType -> handler
    private final Map<String, Map<OperationScope, Map<String, OperationHandler>>> handlers =
            new ConcurrentHashMap<>();

    /**
     * Create an empty registry.
     */
    public OperationRegistry() {
    }

    /**
     * Create a registry with the given handlers.
     *
     * @param operationHandlers list of operation handlers to register
     */
    public OperationRegistry(List<OperationHandler> operationHandlers) {
        if (operationHandlers != null) {
            for (OperationHandler handler : operationHandlers) {
                register(handler);
            }
        }
    }

    /**
     * Register an operation handler.
     *
     * @param handler the handler to register
     */
    public void register(OperationHandler handler) {
        String operationName = handler.getOperationName().toLowerCase();
        OperationScope[] scopes = handler.getScopes();
        String[] resourceTypes = handler.getResourceTypes();

        for (OperationScope scope : scopes) {
            handlers.computeIfAbsent(operationName, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(scope, k -> new ConcurrentHashMap<>());

            Map<String, OperationHandler> typeMap = handlers.get(operationName).get(scope);

            if (resourceTypes == null || resourceTypes.length == 0) {
                // System-level or applies to all types
                typeMap.put("*", handler);
                log.info("Registered operation ${} at {} level for all types",
                        operationName, scope);
            } else {
                for (String resourceType : resourceTypes) {
                    typeMap.put(resourceType, handler);
                }
                log.info("Registered operation ${} at {} level for types: {}",
                        operationName, scope, Arrays.toString(resourceTypes));
            }
        }
    }

    /**
     * Find a handler for the given operation.
     *
     * @param operationName the operation name (without $ prefix)
     * @param scope         the operation scope
     * @param resourceType  the resource type (null for system-level)
     * @param version       the FHIR version
     * @return the handler, or empty if not found
     */
    public Optional<OperationHandler> findHandler(String operationName, OperationScope scope,
                                                   String resourceType, FhirVersion version) {
        String normalizedName = operationName.toLowerCase();

        Map<OperationScope, Map<String, OperationHandler>> scopeMap = handlers.get(normalizedName);
        if (scopeMap == null) {
            return Optional.empty();
        }

        Map<String, OperationHandler> typeMap = scopeMap.get(scope);
        if (typeMap == null) {
            return Optional.empty();
        }

        // Try specific resource type first
        if (resourceType != null) {
            OperationHandler handler = typeMap.get(resourceType);
            if (handler != null && handler.supportsVersion(version)) {
                return Optional.of(handler);
            }
        }

        // Try wildcard handler
        OperationHandler wildcardHandler = typeMap.get("*");
        if (wildcardHandler != null && wildcardHandler.supportsVersion(version)) {
            return Optional.of(wildcardHandler);
        }

        return Optional.empty();
    }

    /**
     * Check if an operation is supported.
     *
     * @param operationName the operation name
     * @param scope         the operation scope
     * @param resourceType  the resource type
     * @param version       the FHIR version
     * @return true if supported
     */
    public boolean isSupported(String operationName, OperationScope scope,
                               String resourceType, FhirVersion version) {
        return findHandler(operationName, scope, resourceType, version).isPresent();
    }

    /**
     * Get all registered operation names.
     *
     * @return set of operation names
     */
    public Set<String> getRegisteredOperations() {
        return new HashSet<>(handlers.keySet());
    }

    /**
     * Get all handlers for a specific scope and resource type.
     *
     * @param scope        the operation scope
     * @param resourceType the resource type (null for system-level)
     * @return list of handlers
     */
    public List<OperationHandler> getHandlers(OperationScope scope, String resourceType) {
        List<OperationHandler> result = new ArrayList<>();

        for (Map<OperationScope, Map<String, OperationHandler>> scopeMap : handlers.values()) {
            Map<String, OperationHandler> typeMap = scopeMap.get(scope);
            if (typeMap != null) {
                if (resourceType != null && typeMap.containsKey(resourceType)) {
                    result.add(typeMap.get(resourceType));
                } else if (typeMap.containsKey("*")) {
                    result.add(typeMap.get("*"));
                }
            }
        }

        return result;
    }
}
