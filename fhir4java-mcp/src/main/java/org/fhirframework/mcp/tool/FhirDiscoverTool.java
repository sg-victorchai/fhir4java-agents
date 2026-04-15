package org.fhirframework.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.fhirframework.core.discovery.DiscoveryResponse;
import org.fhirframework.core.discovery.DiscoveryService;
import org.fhirframework.core.discovery.DiscoveryTopic;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.hint.HintContext;
import org.fhirframework.mcp.hint.ResponseHintGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for discovering FHIR server capabilities.
 * <p>
 * Provides AI agents with the ability to query the FHIR server's capabilities
 * including available resources, search parameters, and extended operations.
 * </p>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Discover all available resources
 * {"topic": "RESOURCES", "fhirVersion": "R5"}
 *
 * // Discover search parameters for Patient
 * {"topic": "SEARCH_PARAMS", "resourceType": "Patient"}
 *
 * // Discover all capabilities for a resource
 * {"topic": "ALL", "resourceType": "Patient", "fhirVersion": "R5"}
 * }</pre>
 */
@Component
public class FhirDiscoverTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(FhirDiscoverTool.class);

    private static final String TOOL_NAME = "fhir_discover";
    private static final String TOOL_DESCRIPTION =
            "Discover FHIR server capabilities including resources, search parameters, and operations";

    private final DiscoveryService discoveryService;
    private final ResponseHintGenerator hintGenerator;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new FhirDiscoverTool.
     *
     * @param discoveryService the discovery service for querying server capabilities
     * @param hintGenerator    the generator for contextual hints
     */
    public FhirDiscoverTool(DiscoveryService discoveryService, ResponseHintGenerator hintGenerator) {
        this.discoveryService = discoveryService;
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

        // Topic property
        Map<String, Object> topicProp = new LinkedHashMap<>();
        topicProp.put("type", "string");
        topicProp.put("enum", List.of("RESOURCES", "SEARCH_PARAMS", "OPERATIONS", "EVENT_CAPABILITIES", "ALL"));
        topicProp.put("description", "What to discover: RESOURCES (available resource types), " +
                "SEARCH_PARAMS (search parameters), OPERATIONS (extended operations), " +
                "EVENT_CAPABILITIES (SSE, webhooks, subscriptions), ALL (everything)");
        properties.put("topic", topicProp);

        // ResourceType property
        Map<String, Object> resourceTypeProp = new LinkedHashMap<>();
        resourceTypeProp.put("type", "string");
        resourceTypeProp.put("description", "Optional: Filter by resource type");
        properties.put("resourceType", resourceTypeProp);

        // FhirVersion property
        Map<String, Object> fhirVersionProp = new LinkedHashMap<>();
        fhirVersionProp.put("type", "string");
        fhirVersionProp.put("enum", List.of("R5", "R4B"));
        fhirVersionProp.put("default", "R5");
        fhirVersionProp.put("description", "FHIR version to query");
        properties.put("fhirVersion", fhirVersionProp);

        schema.put("properties", properties);

        return schema;
    }

    @Override
    public ToolCallResponse execute(ToolCallRequest request) {
        log.debug("Executing fhir_discover tool with arguments: {}", request.getArguments());

        try {
            // Extract and validate topic
            String topicStr = request.getArgumentAsString("topic");
            if (topicStr == null || topicStr.isBlank()) {
                return ToolCallResponse.error("Missing required parameter: topic. " +
                        "Valid values are: RESOURCES, SEARCH_PARAMS, OPERATIONS, ALL");
            }

            DiscoveryTopic topic;
            try {
                topic = DiscoveryTopic.valueOf(topicStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ToolCallResponse.error("Invalid topic: " + topicStr + ". " +
                        "Valid values are: RESOURCES, SEARCH_PARAMS, OPERATIONS, EVENT_CAPABILITIES, ALL");
            }

            // Handle EVENT_CAPABILITIES specially (not part of core DiscoveryService)
            if (topic == DiscoveryTopic.EVENT_CAPABILITIES) {
                return handleEventCapabilitiesDiscovery();
            }

            // Extract optional resourceType
            String resourceType = request.getArgumentAsString("resourceType");

            // Extract and validate FHIR version (default to R5)
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

            log.debug("Discovering: topic={}, resourceType={}, fhirVersion={}",
                    topic, resourceType, fhirVersion);

            // Execute discovery
            DiscoveryResponse response = discoveryService.discover(topic, resourceType, fhirVersion);

            // Convert response to JSON
            String jsonResponse = objectMapper.writeValueAsString(response);

            // Build hint context based on discovery topic
            Map<String, Object> metadata = buildHintMetadata(topic, response);
            HintContext hintContext = new HintContext(
                    TOOL_NAME,
                    topic.name().toLowerCase(),
                    resourceType,
                    List.of(),
                    metadata
            );
            String hints = hintGenerator.generateHintsJson(hintContext);

            log.debug("Discovery completed successfully");
            return ToolCallResponse.success(jsonResponse, hints);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize discovery response", e);
            return ToolCallResponse.error("Failed to serialize discovery response: " + e.getMessage());
        } catch (Exception e) {
            log.error("Discovery failed", e);
            return ToolCallResponse.error("Discovery failed: " + e.getMessage());
        }
    }

    /**
     * Handles discovery of event capabilities (SSE, webhooks, FHIR Subscriptions).
     * <p>
     * This provides AI agents with information about real-time event subscription options.
     * </p>
     */
    private ToolCallResponse handleEventCapabilitiesDiscovery() {
        log.debug("Discovering event capabilities");

        Map<String, Object> capabilities = new LinkedHashMap<>();

        // SSE capabilities
        Map<String, Object> sse = new LinkedHashMap<>();
        sse.put("endpoint", "/api/events/stream");
        sse.put("method", "GET");
        sse.put("accept", "text/event-stream");
        sse.put("supportedFilters", List.of(
                Map.of("name", "topics", "description", "Comma-separated resource types (e.g., Patient,Observation)"),
                Map.of("name", "actions", "description", "Comma-separated actions (e.g., create,update,delete)")
        ));
        sse.put("eventTypes", List.of(
                Map.of("type", "resource-change", "description", "Emitted when a resource is created, updated, or deleted")
        ));
        sse.put("eventPayload", Map.of(
                "resourceType", "The FHIR resource type",
                "resourceId", "The logical ID of the resource",
                "action", "create | update | delete",
                "tenantId", "The tenant context",
                "timestamp", "ISO 8601 timestamp"
        ));
        capabilities.put("sse", sse);

        // Webhook capabilities
        Map<String, Object> webhooks = new LinkedHashMap<>();
        webhooks.put("endpoint", "/api/webhooks");
        webhooks.put("methods", Map.of(
                "POST", "Register a new webhook",
                "GET", "List all webhooks for tenant",
                "GET /{id}", "Get specific webhook",
                "DELETE /{id}", "Delete webhook",
                "POST /{id}/enable", "Enable webhook",
                "POST /{id}/disable", "Disable webhook"
        ));
        webhooks.put("requestSchema", Map.of(
                "callbackUrl", "URL to POST event notifications (required)",
                "topics", "List of topics (e.g., ['Patient.create', 'Patient.*'])",
                "secret", "Optional HMAC-SHA256 secret for signing payloads"
        ));
        webhooks.put("supportedTopicPatterns", List.of(
                Map.of("pattern", "{ResourceType}.{action}", "example", "Patient.create"),
                Map.of("pattern", "{ResourceType}.*", "example", "Patient.*", "description", "All actions for resource"),
                Map.of("pattern", "*.*", "description", "All events")
        ));
        webhooks.put("signatureHeader", "X-Webhook-Signature");
        webhooks.put("signatureFormat", "sha256=<base64-encoded-hmac>");
        capabilities.put("webhooks", webhooks);

        // FHIR Subscription capabilities
        Map<String, Object> subscriptions = new LinkedHashMap<>();
        subscriptions.put("channels", List.of(
                Map.of("type", "rest-hook", "description", "HTTP POST callback"),
                Map.of("type", "sse", "description", "Server-Sent Events stream")
        ));
        subscriptions.put("topicMatching", Map.of(
                "resourceType", "Exact match on resource type",
                "events", "List of events to match (create, update, delete)",
                "filters", "Additional filter criteria (future)"
        ));
        capabilities.put("subscriptions", subscriptions);

        // Usage hints
        Map<String, Object> usageHints = new LinkedHashMap<>();
        usageHints.put("forRealTimeMonitoring", "Use SSE for continuous real-time updates in client applications");
        usageHints.put("forServerToServer", "Use webhooks for server-to-server notifications");
        usageHints.put("forFhirCompliance", "Use FHIR Subscriptions for standards-compliant subscription management");
        capabilities.put("usageHints", usageHints);

        try {
            String jsonResponse = objectMapper.writeValueAsString(capabilities);

            // Generate hints for event capabilities
            HintContext hintContext = new HintContext(
                    TOOL_NAME,
                    "event_capabilities",
                    null,
                    List.of(),
                    Map.of("hasEventCapabilities", true)
            );
            String hints = hintGenerator.generateHintsJson(hintContext);

            return ToolCallResponse.success(jsonResponse, hints);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event capabilities", e);
            return ToolCallResponse.error("Failed to serialize event capabilities: " + e.getMessage());
        }
    }

    /**
     * Builds metadata for hint generation based on the discovery topic and response.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildHintMetadata(DiscoveryTopic topic, DiscoveryResponse response) {
        Map<String, Object> metadata = new HashMap<>();

        try {
            // Convert response to a map to extract discovered items
            String jsonStr = objectMapper.writeValueAsString(response);
            Map<String, Object> responseMap = objectMapper.readValue(jsonStr, Map.class);

            switch (topic) {
                case RESOURCES:
                    // Extract resource names from the response
                    if (responseMap.containsKey("resources")) {
                        Object resources = responseMap.get("resources");
                        if (resources instanceof List) {
                            List<String> resourceNames = new ArrayList<>();
                            for (Object resource : (List<?>) resources) {
                                if (resource instanceof Map) {
                                    Object name = ((Map<?, ?>) resource).get("name");
                                    if (name != null) {
                                        resourceNames.add(name.toString());
                                    }
                                } else if (resource instanceof String) {
                                    resourceNames.add((String) resource);
                                }
                            }
                            metadata.put("discoveredResources", resourceNames);
                        }
                    }
                    break;

                case OPERATIONS:
                    // Extract operation names from the response
                    if (responseMap.containsKey("operations")) {
                        Object operations = responseMap.get("operations");
                        if (operations instanceof List) {
                            List<String> operationNames = new ArrayList<>();
                            for (Object operation : (List<?>) operations) {
                                if (operation instanceof Map) {
                                    Object name = ((Map<?, ?>) operation).get("name");
                                    if (name != null) {
                                        operationNames.add(name.toString());
                                    }
                                } else if (operation instanceof String) {
                                    operationNames.add((String) operation);
                                }
                            }
                            metadata.put("discoveredOperations", operationNames);
                        }
                    }
                    break;

                case SEARCH_PARAMS:
                    // Extract search parameter names from the response
                    if (responseMap.containsKey("searchParameters") || responseMap.containsKey("searchParams")) {
                        Object params = responseMap.containsKey("searchParameters") ?
                                responseMap.get("searchParameters") : responseMap.get("searchParams");
                        if (params instanceof List) {
                            List<String> paramNames = new ArrayList<>();
                            for (Object param : (List<?>) params) {
                                if (param instanceof Map) {
                                    Object name = ((Map<?, ?>) param).get("name");
                                    if (name != null) {
                                        paramNames.add(name.toString());
                                    }
                                } else if (param instanceof String) {
                                    paramNames.add((String) param);
                                }
                            }
                            metadata.put("discoveredParams", paramNames);
                        }
                    }
                    break;

                case ALL:
                    // For ALL, extract both resources and operations
                    if (responseMap.containsKey("resources")) {
                        Object resources = responseMap.get("resources");
                        if (resources instanceof List) {
                            List<String> resourceNames = new ArrayList<>();
                            for (Object resource : (List<?>) resources) {
                                if (resource instanceof Map) {
                                    Object name = ((Map<?, ?>) resource).get("name");
                                    if (name != null) {
                                        resourceNames.add(name.toString());
                                    }
                                } else if (resource instanceof String) {
                                    resourceNames.add((String) resource);
                                }
                            }
                            metadata.put("discoveredResources", resourceNames);
                        }
                    }
                    break;

                case EVENT_CAPABILITIES:
                    // EVENT_CAPABILITIES is handled separately, but add metadata if needed
                    metadata.put("hasEventCapabilities", true);
                    metadata.put("supportedChannels", List.of("sse", "webhooks", "subscriptions"));
                    break;
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to build hint metadata from discovery response", e);
        }

        return metadata;
    }
}
