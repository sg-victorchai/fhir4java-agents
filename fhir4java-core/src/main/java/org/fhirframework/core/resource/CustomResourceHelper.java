package org.fhirframework.core.resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.version.FhirVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper for detecting and handling custom FHIR resource types.
 * <p>
 * Custom resources are resource types that are configured in the resource registry
 * but are not known to HAPI FHIR's built-in parser (i.e., not standard FHIR resources).
 * </p>
 */
@Component
public class CustomResourceHelper {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceHelper.class);

    private final FhirContextFactory contextFactory;
    private final ResourceRegistry resourceRegistry;
    private final ObjectMapper objectMapper;

    public CustomResourceHelper(FhirContextFactory contextFactory, ResourceRegistry resourceRegistry) {
        this.contextFactory = contextFactory;
        this.resourceRegistry = resourceRegistry;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Check if a resource type is a custom resource (not known to HAPI FHIR).
     *
     * @param resourceType The resource type name
     * @param version      The FHIR version
     * @return true if this is a custom resource type
     */
    public boolean isCustomResource(String resourceType, FhirVersion version) {
        // First check if it's configured in our registry
        if (!resourceRegistry.isResourceConfigured(resourceType)) {
            return false;
        }

        // Check if HAPI knows about this resource type
        return !isKnownToHapi(resourceType, version);
    }

    /**
     * Check if a resource type is known to HAPI FHIR.
     *
     * @param resourceType The resource type name
     * @param version      The FHIR version
     * @return true if HAPI knows about this resource type
     */
    public boolean isKnownToHapi(String resourceType, FhirVersion version) {
        try {
            FhirContext ctx = contextFactory.getContext(version);
            RuntimeResourceDefinition def = ctx.getResourceDefinition(resourceType);
            return def != null;
        } catch (Exception e) {
            // HAPI throws DataFormatException for unknown resources
            log.debug("Resource type '{}' is not known to HAPI FHIR: {}", resourceType, e.getMessage());
            return false;
        }
    }

    /**
     * Extract the resource type from a JSON body.
     *
     * @param jsonBody The JSON resource body
     * @return The resource type, or null if not found
     */
    public String extractResourceType(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode resourceTypeNode = root.get("resourceType");
            if (resourceTypeNode != null && resourceTypeNode.isTextual()) {
                return resourceTypeNode.asText();
            }
        } catch (Exception e) {
            log.debug("Failed to extract resourceType from JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validate that the JSON body has a valid structure for a FHIR resource.
     * This performs basic structural validation without HAPI parsing.
     *
     * @param jsonBody     The JSON resource body
     * @param resourceType The expected resource type
     * @return true if the structure is valid
     */
    public boolean validateBasicStructure(String jsonBody, String resourceType) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            // Must be an object
            if (!root.isObject()) {
                return false;
            }

            // Must have resourceType matching expected
            JsonNode resourceTypeNode = root.get("resourceType");
            if (resourceTypeNode == null || !resourceTypeNode.isTextual()) {
                return false;
            }

            String actualType = resourceTypeNode.asText();
            if (!resourceType.equals(actualType)) {
                log.debug("Resource type mismatch: expected '{}', got '{}'", resourceType, actualType);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.debug("Invalid JSON structure: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ensure the JSON body has an ID, generating one if not present.
     *
     * @param jsonBody The JSON resource body
     * @return The JSON body with an ID
     */
    public String ensureId(String jsonBody) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonBody);

            if (!root.has("id") || root.get("id").isNull() || root.get("id").asText().isBlank()) {
                root.put("id", UUID.randomUUID().toString());
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to ensure ID in JSON: {}", e.getMessage());
            return jsonBody;
        }
    }

    /**
     * Extract the ID from a JSON body.
     *
     * @param jsonBody The JSON resource body
     * @return The resource ID, or null if not found
     */
    public String extractId(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode idNode = root.get("id");
            if (idNode != null && idNode.isTextual()) {
                return idNode.asText();
            }
        } catch (Exception e) {
            log.debug("Failed to extract id from JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Set the ID in a JSON body.
     *
     * @param jsonBody The JSON resource body
     * @param id       The ID to set
     * @return The modified JSON body
     */
    public String setId(String jsonBody, String id) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonBody);
            root.put("id", id);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to set ID in JSON: {}", e.getMessage());
            return jsonBody;
        }
    }

    /**
     * Update the meta element with versionId and lastUpdated.
     *
     * @param jsonBody    The JSON resource body
     * @param versionId   The version ID
     * @param lastUpdated The last updated timestamp (ISO-8601)
     * @return The modified JSON body
     */
    public String updateMeta(String jsonBody, int versionId, String lastUpdated) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonBody);

            ObjectNode meta;
            if (root.has("meta") && root.get("meta").isObject()) {
                meta = (ObjectNode) root.get("meta");
            } else {
                meta = objectMapper.createObjectNode();
                root.set("meta", meta);
            }

            meta.put("versionId", String.valueOf(versionId));
            meta.put("lastUpdated", lastUpdated);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to update meta in JSON: {}", e.getMessage());
            return jsonBody;
        }
    }
}
