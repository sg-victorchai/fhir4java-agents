package org.fhirframework.persistence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service for applying RFC 6902 JSON Patch operations to FHIR resources.
 */
@Service
public class JsonPatchService {

    private static final Logger log = LoggerFactory.getLogger(JsonPatchService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Apply a JSON Patch (RFC 6902) to a resource JSON string.
     *
     * @param resourceJson the current resource JSON
     * @param patchJson    the JSON Patch document (array of operations)
     * @return the patched resource JSON string
     * @throws JsonPatchException if the patch cannot be applied
     * @throws IOException        if JSON parsing fails
     */
    public String applyPatch(String resourceJson, String patchJson) throws JsonPatchException, IOException {
        JsonNode resourceNode = objectMapper.readTree(resourceJson);
        JsonNode patchNode = objectMapper.readTree(patchJson);

        JsonPatch patch = JsonPatch.fromJson(patchNode);
        JsonNode patchedNode = patch.apply(resourceNode);

        log.debug("Applied JSON Patch successfully");
        return objectMapper.writeValueAsString(patchedNode);
    }
}
