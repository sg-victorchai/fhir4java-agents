package org.fhirframework.mcp.tool;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.hint.HintContext;
import org.fhirframework.mcp.hint.ResponseHintGenerator;
import org.fhirframework.persistence.service.FhirResourceService;
import org.fhirframework.persistence.service.FhirResourceService.ResourceResult;
import org.hl7.fhir.r5.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool for querying FHIR resources.
 * <p>
 * Provides AI agents with the ability to read, search, retrieve history,
 * and execute operations on FHIR resources.
 * </p>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Read a specific resource
 * {"action": "read", "resourceType": "Patient", "id": "123"}
 *
 * // Search for resources
 * {"action": "search", "resourceType": "Patient", "searchParams": {"family": "Smith"}}
 *
 * // Get resource history
 * {"action": "history", "resourceType": "Patient", "id": "123"}
 *
 * // Execute an operation
 * {"action": "operation", "resourceType": "Patient", "id": "123", "operationName": "$everything"}
 * }</pre>
 */
@Component
public class FhirQueryTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(FhirQueryTool.class);

    private static final String TOOL_NAME = "fhir_query";
    private static final String TOOL_DESCRIPTION =
            "Query FHIR resources - read, search, history, and execute operations";

    private static final Set<String> VALID_ACTIONS = Set.of("read", "search", "history", "operation");
    private static final int DEFAULT_SEARCH_COUNT = 20;

    private final FhirResourceService fhirResourceService;
    private final ResponseHintGenerator hintGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new FhirQueryTool.
     *
     * @param fhirResourceService the service for FHIR resource operations
     * @param hintGenerator the generator for contextual hints
     */
    public FhirQueryTool(FhirResourceService fhirResourceService, ResponseHintGenerator hintGenerator) {
        this.fhirResourceService = fhirResourceService;
        this.hintGenerator = hintGenerator;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        // Action property
        Map<String, Object> actionProp = new LinkedHashMap<>();
        actionProp.put("type", "string");
        actionProp.put("enum", List.of("read", "search", "history", "operation"));
        actionProp.put("description", "The query action to perform");
        properties.put("action", actionProp);

        // ResourceType property
        Map<String, Object> resourceTypeProp = new LinkedHashMap<>();
        resourceTypeProp.put("type", "string");
        resourceTypeProp.put("description", "FHIR resource type (e.g., Patient, Observation)");
        properties.put("resourceType", resourceTypeProp);

        // Id property
        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "string");
        idProp.put("description", "Resource ID (required for read and instance-level operations)");
        properties.put("id", idProp);

        // SearchParams property
        Map<String, Object> searchParamsProp = new LinkedHashMap<>();
        searchParamsProp.put("type", "object");
        searchParamsProp.put("description", "Search parameters as key-value pairs");
        properties.put("searchParams", searchParamsProp);

        // OperationName property
        Map<String, Object> operationNameProp = new LinkedHashMap<>();
        operationNameProp.put("type", "string");
        operationNameProp.put("description", "Operation name (e.g., $validate, $everything)");
        properties.put("operationName", operationNameProp);

        // OperationParams property
        Map<String, Object> operationParamsProp = new LinkedHashMap<>();
        operationParamsProp.put("type", "object");
        operationParamsProp.put("description", "Operation parameters");
        properties.put("operationParams", operationParamsProp);

        // FhirVersion property
        Map<String, Object> fhirVersionProp = new LinkedHashMap<>();
        fhirVersionProp.put("type", "string");
        fhirVersionProp.put("enum", List.of("R5", "R4B"));
        fhirVersionProp.put("default", "R5");
        fhirVersionProp.put("description", "FHIR version to query");
        properties.put("fhirVersion", fhirVersionProp);

        schema.put("properties", properties);
        schema.put("required", List.of("action", "resourceType"));

        return schema;
    }

    @Override
    public ToolCallResponse execute(ToolCallRequest request) {
        log.debug("Executing fhir_query tool with arguments: {}", request.getArguments());

        try {
            // Validate required parameters
            String action = request.getArgumentAsString("action");
            if (action == null || action.isBlank()) {
                return ToolCallResponse.error("Missing required parameter: action. " +
                        "Valid values are: read, search, history, operation");
            }

            String resourceType = request.getArgumentAsString("resourceType");
            if (resourceType == null || resourceType.isBlank()) {
                return ToolCallResponse.error("Missing required parameter: resourceType");
            }

            // Validate action
            String actionLower = action.toLowerCase();
            if (!VALID_ACTIONS.contains(actionLower)) {
                return ToolCallResponse.error("Invalid action: " + action + ". " +
                        "Valid values are: read, search, history, operation");
            }

            // Parse FHIR version (default to R5)
            String versionStr = request.getArgumentAsString("fhirVersion");
            FhirVersion fhirVersion;
            if (versionStr == null || versionStr.isBlank()) {
                fhirVersion = FhirVersion.R5;
            } else {
                try {
                    fhirVersion = FhirVersion.fromCode(versionStr);
                } catch (IllegalArgumentException e) {
                    return ToolCallResponse.error("Invalid FHIR version: " + versionStr + ". " +
                            "Valid values are: R5, R4B");
                }
            }

            // Execute the appropriate action
            return switch (actionLower) {
                case "read" -> executeRead(request, resourceType, fhirVersion);
                case "search" -> executeSearch(request, resourceType, fhirVersion);
                case "history" -> executeHistory(request, resourceType, fhirVersion);
                case "operation" -> executeOperation(request, resourceType, fhirVersion);
                default -> ToolCallResponse.error("Unknown action: " + action);
            };

        } catch (Exception e) {
            log.error("Query failed", e);
            return ToolCallResponse.error("Query failed: " + e.getMessage());
        }
    }

    /**
     * Executes a read action to retrieve a single resource by ID.
     */
    private ToolCallResponse executeRead(ToolCallRequest request, String resourceType, FhirVersion fhirVersion) {
        String id = request.getArgumentAsString("id");
        if (id == null || id.isBlank()) {
            return ToolCallResponse.error("Missing required parameter 'id' for read action");
        }

        log.debug("Reading: resourceType={}, id={}, fhirVersion={}", resourceType, id, fhirVersion);

        ResourceResult result = fhirResourceService.read(resourceType, id, fhirVersion);

        if (result.deleted()) {
            return ToolCallResponse.error("Resource " + resourceType + "/" + id + " has been deleted");
        }

        log.debug("Read completed successfully");

        // Generate hints for read operation
        HintContext hintContext = new HintContext(
                TOOL_NAME,
                "read",
                resourceType,
                List.of(id),
                Map.of()
        );
        String hints = hintGenerator.generateHintsJson(hintContext);

        return ToolCallResponse.success(result.content(), hints);
    }

    /**
     * Executes a search action to find resources matching the given parameters.
     */
    private ToolCallResponse executeSearch(ToolCallRequest request, String resourceType, FhirVersion fhirVersion) {
        // Extract search parameters
        Map<String, String> searchParams = extractSearchParams(request);

        log.debug("Searching: resourceType={}, params={}, fhirVersion={}", resourceType, searchParams, fhirVersion);

        Bundle bundle = fhirResourceService.search(resourceType, searchParams, fhirVersion, DEFAULT_SEARCH_COUNT);

        // Serialize bundle to JSON
        String jsonResponse = serializeBundleToJson(bundle, fhirVersion);

        log.debug("Search completed successfully with {} results", bundle.getTotal());

        // Extract resource IDs from search results
        List<String> resourceIds = extractResourceIdsFromBundle(bundle);

        // Build metadata for hints
        Map<String, Object> metadata = new HashMap<>();
        if (searchParams.containsKey("patient")) {
            // Extract patient ID from patient search param
            String patientRef = searchParams.get("patient");
            if (patientRef != null && patientRef.contains("/")) {
                metadata.put("patientId", patientRef.substring(patientRef.lastIndexOf('/') + 1));
            } else if (patientRef != null) {
                metadata.put("patientId", patientRef);
            }
        }

        // Generate hints for search operation
        HintContext hintContext = new HintContext(
                TOOL_NAME,
                "search",
                resourceType,
                resourceIds,
                metadata
        );
        String hints = hintGenerator.generateHintsJson(hintContext);

        return ToolCallResponse.success(jsonResponse, hints);
    }

    /**
     * Executes a history action to retrieve the version history of a resource.
     */
    private ToolCallResponse executeHistory(ToolCallRequest request, String resourceType, FhirVersion fhirVersion) {
        String id = request.getArgumentAsString("id");
        if (id == null || id.isBlank()) {
            return ToolCallResponse.error("Missing required parameter 'id' for history action");
        }

        log.debug("Getting history: resourceType={}, id={}, fhirVersion={}", resourceType, id, fhirVersion);

        Bundle bundle = fhirResourceService.history(resourceType, id, fhirVersion);

        // Serialize bundle to JSON
        String jsonResponse = serializeBundleToJson(bundle, fhirVersion);

        log.debug("History completed successfully with {} versions", bundle.getTotal());
        return ToolCallResponse.success(jsonResponse);
    }

    /**
     * Executes an operation on a resource.
     */
    private ToolCallResponse executeOperation(ToolCallRequest request, String resourceType, FhirVersion fhirVersion) {
        String operationName = request.getArgumentAsString("operationName");
        if (operationName == null || operationName.isBlank()) {
            return ToolCallResponse.error("Missing required parameter 'operationName' for operation action");
        }

        String id = request.getArgumentAsString("id");

        log.debug("Executing operation: resourceType={}, id={}, operationName={}, fhirVersion={}",
                resourceType, id, operationName, fhirVersion);

        // Note: Extended operations are not yet fully implemented in FhirResourceService
        // This is a placeholder for future implementation
        return ToolCallResponse.error("Extended operation '" + operationName + "' is not yet implemented via MCP. " +
                "Use the FHIR REST API directly for operation invocation.");
    }

    /**
     * Extracts search parameters from the request arguments.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractSearchParams(ToolCallRequest request) {
        Map<String, String> searchParams = new HashMap<>();

        Object searchParamsObj = request.getArgument("searchParams");
        if (searchParamsObj instanceof Map) {
            Map<String, Object> paramsMap = (Map<String, Object>) searchParamsObj;
            for (Map.Entry<String, Object> entry : paramsMap.entrySet()) {
                if (entry.getValue() != null) {
                    searchParams.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        return searchParams;
    }

    /**
     * Serializes a Bundle to JSON string using HAPI FHIR parser.
     */
    private String serializeBundleToJson(Bundle bundle, FhirVersion fhirVersion) {
        FhirContext context = FhirContext.forR5();
        IParser parser = context.newJsonParser();
        parser.setPrettyPrint(true);
        return parser.encodeResourceToString(bundle);
    }

    /**
     * Extracts resource IDs from a search result bundle.
     */
    private List<String> extractResourceIdsFromBundle(Bundle bundle) {
        List<String> ids = new ArrayList<>();
        if (bundle.getEntry() != null) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.getResource() != null && entry.getResource().getIdElement() != null) {
                    String id = entry.getResource().getIdElement().getIdPart();
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }
}
