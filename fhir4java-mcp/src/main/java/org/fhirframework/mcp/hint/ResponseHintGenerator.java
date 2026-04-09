package org.fhirframework.mcp.hint;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates contextual hints for AI agents based on the operation result.
 * <p>
 * This component analyzes the completed operation context and suggests
 * logical next actions that an AI agent might want to perform.
 * </p>
 * <p>
 * Hint rules:
 * <ul>
 *   <li>After Patient search/read - suggest searching for related Observations, Conditions, MedicationRequests</li>
 *   <li>After Observation search - suggest reading the Patient reference</li>
 *   <li>After resource create - suggest reading the created resource, searching for similar resources</li>
 *   <li>After delete - suggest searching to verify deletion</li>
 *   <li>After discovering resources - suggest querying them</li>
 *   <li>After discovering operations - suggest executing them</li>
 * </ul>
 * </p>
 */
@Component
public class ResponseHintGenerator {

    // Patient-related resources that are commonly queried together
    private static final Set<String> PATIENT_RELATED_RESOURCES = Set.of(
            "Observation", "Condition", "MedicationRequest", "AllergyIntolerance",
            "Procedure", "Encounter", "DiagnosticReport", "CarePlan"
    );

    // Tools
    private static final String FHIR_QUERY = "fhir_query";
    private static final String FHIR_MUTATE = "fhir_mutate";
    private static final String FHIR_DISCOVER = "fhir_discover";

    // Actions
    private static final String ACTION_SEARCH = "search";
    private static final String ACTION_READ = "read";
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_RESOURCES = "resources";
    private static final String ACTION_OPERATIONS = "operations";
    private static final String ACTION_SEARCH_PARAMS = "search_params";

    /**
     * Generates hints based on the operation context.
     *
     * @param context the context of the completed operation
     * @return a list of hint strings suggesting next actions
     */
    public List<String> generateHints(HintContext context) {
        if (context == null || context.toolName() == null || context.action() == null) {
            return List.of();
        }

        String toolName = context.toolName();
        String action = context.action().toLowerCase();

        return switch (toolName) {
            case FHIR_QUERY -> generateQueryHints(context, action);
            case FHIR_MUTATE -> generateMutateHints(context, action);
            case FHIR_DISCOVER -> generateDiscoverHints(context, action);
            default -> List.of();
        };
    }

    /**
     * Generates hints after query operations.
     */
    private List<String> generateQueryHints(HintContext context, String action) {
        return switch (action) {
            case ACTION_SEARCH -> generateSearchHints(context);
            case ACTION_READ -> generateReadHints(context);
            default -> List.of();
        };
    }

    /**
     * Generates hints after a search operation.
     */
    private List<String> generateSearchHints(HintContext context) {
        String resourceType = context.resourceType();
        if (resourceType == null) {
            return List.of();
        }

        List<String> hints = new ArrayList<>();

        if ("Patient".equals(resourceType)) {
            hints.addAll(generatePatientRelatedHints(context));
        } else if ("Observation".equals(resourceType)) {
            hints.addAll(generateObservationRelatedHints(context));
        }

        return hints;
    }

    /**
     * Generates hints after a read operation.
     */
    private List<String> generateReadHints(HintContext context) {
        String resourceType = context.resourceType();
        if (resourceType == null) {
            return List.of();
        }

        List<String> hints = new ArrayList<>();

        if ("Patient".equals(resourceType)) {
            hints.addAll(generatePatientRelatedHints(context));
        }

        return hints;
    }

    /**
     * Generates hints for related Patient resources.
     */
    private List<String> generatePatientRelatedHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        List<String> resourceIds = context.resourceIds();

        if (resourceIds == null || resourceIds.isEmpty()) {
            return hints;
        }

        String patientId = resourceIds.get(0);

        // Suggest searching for Observations
        hints.add(String.format(
                "To get observations for this patient, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"Observation\", \"searchParams\": {\"patient\": \"Patient/%s\"}}",
                patientId
        ));

        // Suggest searching for Conditions
        hints.add(String.format(
                "To get conditions for this patient, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"Condition\", \"searchParams\": {\"patient\": \"Patient/%s\"}}",
                patientId
        ));

        // Suggest searching for MedicationRequests
        hints.add(String.format(
                "To get medication requests for this patient, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"MedicationRequest\", \"searchParams\": {\"patient\": \"Patient/%s\"}}",
                patientId
        ));

        return hints;
    }

    /**
     * Generates hints after an Observation search.
     */
    private List<String> generateObservationRelatedHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        Map<String, Object> metadata = context.metadata();

        if (metadata != null && metadata.containsKey("patientId")) {
            String patientId = metadata.get("patientId").toString();
            hints.add(String.format(
                    "To get the patient for this observation, use fhir_query with: {\"action\": \"read\", \"resourceType\": \"Patient\", \"id\": \"%s\"}",
                    patientId
            ));
        }

        return hints;
    }

    /**
     * Generates hints after mutation operations.
     */
    private List<String> generateMutateHints(HintContext context, String action) {
        return switch (action) {
            case ACTION_CREATE -> generateCreateHints(context);
            case ACTION_UPDATE -> generateUpdateHints(context);
            case ACTION_DELETE -> generateDeleteHints(context);
            default -> List.of();
        };
    }

    /**
     * Generates hints after a create operation.
     */
    private List<String> generateCreateHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        String resourceType = context.resourceType();
        List<String> resourceIds = context.resourceIds();

        if (resourceType == null || resourceIds == null || resourceIds.isEmpty()) {
            return hints;
        }

        String resourceId = resourceIds.get(0);

        // Suggest reading the created resource
        hints.add(String.format(
                "To verify the created resource, use fhir_query with: {\"action\": \"read\", \"resourceType\": \"%s\", \"id\": \"%s\"}",
                resourceType, resourceId
        ));

        // Suggest searching for similar resources
        hints.add(String.format(
                "To search for similar %s resources, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"%s\"}",
                resourceType, resourceType
        ));

        return hints;
    }

    /**
     * Generates hints after an update operation.
     */
    private List<String> generateUpdateHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        String resourceType = context.resourceType();
        List<String> resourceIds = context.resourceIds();

        if (resourceType == null || resourceIds == null || resourceIds.isEmpty()) {
            return hints;
        }

        String resourceId = resourceIds.get(0);

        // Suggest reading the updated resource
        hints.add(String.format(
                "To verify the update, use fhir_query with: {\"action\": \"read\", \"resourceType\": \"%s\", \"id\": \"%s\"}",
                resourceType, resourceId
        ));

        return hints;
    }

    /**
     * Generates hints after a delete operation.
     */
    private List<String> generateDeleteHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        String resourceType = context.resourceType();

        if (resourceType == null) {
            return hints;
        }

        // Suggest searching to verify deletion
        hints.add(String.format(
                "To verify the deletion, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"%s\"} to confirm the resource is no longer present",
                resourceType
        ));

        return hints;
    }

    /**
     * Generates hints after discovery operations.
     */
    private List<String> generateDiscoverHints(HintContext context, String action) {
        return switch (action) {
            case ACTION_RESOURCES -> generateResourceDiscoveryHints(context);
            case ACTION_OPERATIONS -> generateOperationDiscoveryHints(context);
            case ACTION_SEARCH_PARAMS -> generateSearchParamDiscoveryHints(context);
            default -> List.of();
        };
    }

    /**
     * Generates hints after discovering available resources.
     */
    @SuppressWarnings("unchecked")
    private List<String> generateResourceDiscoveryHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        Map<String, Object> metadata = context.metadata();

        if (metadata != null && metadata.containsKey("discoveredResources")) {
            Object discovered = metadata.get("discoveredResources");
            if (discovered instanceof List) {
                List<String> resources = (List<String>) discovered;
                if (!resources.isEmpty()) {
                    String firstResource = resources.get(0);
                    hints.add(String.format(
                            "To query %s resources, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"%s\"}",
                            firstResource, firstResource
                    ));
                }
            }
        }

        return hints;
    }

    /**
     * Generates hints after discovering available operations.
     */
    @SuppressWarnings("unchecked")
    private List<String> generateOperationDiscoveryHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        String resourceType = context.resourceType();
        Map<String, Object> metadata = context.metadata();

        if (metadata != null && metadata.containsKey("discoveredOperations")) {
            Object discovered = metadata.get("discoveredOperations");
            if (discovered instanceof List) {
                List<String> operations = (List<String>) discovered;
                if (!operations.isEmpty()) {
                    String operation = operations.get(0);
                    if (resourceType != null) {
                        hints.add(String.format(
                                "To execute the %s operation, use fhir_query with: {\"action\": \"operation\", \"resourceType\": \"%s\", \"operationName\": \"%s\"}",
                                operation, resourceType, operation
                        ));
                    } else {
                        hints.add(String.format(
                                "Discovered operations include: %s. Use fhir_query with action \"operation\" to execute them.",
                                String.join(", ", operations)
                        ));
                    }
                }
            }
        }

        return hints;
    }

    /**
     * Generates hints after discovering search parameters.
     */
    @SuppressWarnings("unchecked")
    private List<String> generateSearchParamDiscoveryHints(HintContext context) {
        List<String> hints = new ArrayList<>();
        String resourceType = context.resourceType();

        if (resourceType != null) {
            hints.add(String.format(
                    "To search for %s resources, use fhir_query with: {\"action\": \"search\", \"resourceType\": \"%s\", \"searchParams\": {}}",
                    resourceType, resourceType
            ));
        }

        return hints;
    }

    /**
     * Generates hints as a formatted JSON string for inclusion in responses.
     *
     * @param context the hint context
     * @return formatted hints JSON string or null if no hints
     */
    public String generateHintsJson(HintContext context) {
        List<String> hints = generateHints(context);
        if (hints.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"hints\": [");
        for (int i = 0; i < hints.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(escapeJson(hints.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Escapes special characters for JSON string.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
