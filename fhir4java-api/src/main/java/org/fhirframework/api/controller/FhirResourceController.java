package org.fhirframework.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.api.config.FhirMediaType;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.guard.InteractionGuard;
import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.service.FhirResourceService;
import org.fhirframework.persistence.service.FhirResourceService.ResourceResult;
import org.fhirframework.persistence.service.JsonPatchService;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginOrchestrator;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.instance.model.api.IBaseResource;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Main FHIR resource controller with dual path support.
 * <p>
 * Handles both versioned paths ({@code /fhir/r5/Patient}) and
 * unversioned paths ({@code /fhir/Patient}).
 * </p>
 * <p>
 * Uses regex patterns to distinguish:
 * <ul>
 *   <li>version: {@code r5|r4b} (case insensitive)</li>
 *   <li>resourceType: starts with uppercase letter {@code [A-Z][a-zA-Z]+}</li>
 * </ul>
 * </p>
 */
@RestController
public class FhirResourceController {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceController.class);

    // Regex patterns
    private static final String VERSION_PATTERN = "r5|r4b|R5|R4B";
    private static final String RESOURCE_TYPE_PATTERN = "[A-Z][a-zA-Z]+";

    private final FhirContextFactory contextFactory;
    private final InteractionGuard interactionGuard;
    private final FhirResourceService resourceService;
    private final PluginOrchestrator pluginOrchestrator;
    private final JsonPatchService jsonPatchService;

    public FhirResourceController(FhirContextFactory contextFactory,
                                  InteractionGuard interactionGuard,
                                  FhirResourceService resourceService,
                                  PluginOrchestrator pluginOrchestrator,
                                  JsonPatchService jsonPatchService) {
        this.contextFactory = contextFactory;
        this.interactionGuard = interactionGuard;
        this.resourceService = resourceService;
        this.pluginOrchestrator = pluginOrchestrator;
        this.jsonPatchService = jsonPatchService;
    }

    // ========== READ Operations ==========

    /**
     * Read a resource by ID (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            HttpServletRequest request) {
        return read(resourceType, id, request);
    }

    /**
     * Read a resource by ID (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            HttpServletRequest request) {
        return read(resourceType, id, request);
    }

    private ResponseEntity<String> read(String resourceType, String id, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("READ {}/{} (version={})", resourceType, id, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.READ);

        ResourceResult result = resourceService.read(resourceType, id, version);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .header("Last-Modified", formatLastModified(result))
                .body(result.content());
    }

    // ========== VREAD Operations ==========

    /**
     * Version-specific read (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}/_history/{vid}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> vreadVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String vid,
            HttpServletRequest request) {
        return vread(resourceType, id, vid, request);
    }

    /**
     * Version-specific read (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}/_history/{vid}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> vreadUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable String vid,
            HttpServletRequest request) {
        return vread(resourceType, id, vid, request);
    }

    private ResponseEntity<String> vread(String resourceType, String id, String vid, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("VREAD {}/{}/_history/{} (version={})", resourceType, id, vid, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.VREAD);

        int versionId = Integer.parseInt(vid);
        ResourceResult result = resourceService.vread(resourceType, id, versionId, version);

        if (result.deleted()) {
            return ResponseEntity
                    .status(HttpStatus.GONE)
                    .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                    .header("ETag", "W/\"" + result.versionId() + "\"")
                    .build();
        }

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .header("Last-Modified", formatLastModified(result))
                .body(result.content());
    }

    // ========== CREATE Operations ==========

    /**
     * Create a new resource (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE,
                    MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> createVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @RequestBody String body,
            HttpServletRequest request) {
        return create(resourceType, body, request);
    }

    /**
     * Create a new resource (unversioned path).
     */
    @PostMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE,
                    MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> createUnversioned(
            @PathVariable String resourceType,
            @RequestBody String body,
            HttpServletRequest request) {
        return create(resourceType, body, request);
    }

    private ResponseEntity<String> create(String resourceType, String body, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("CREATE {} (version={})", resourceType, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.CREATE);

        // Build plugin context
        FhirContext ctx = contextFactory.getContext(version);
        IBaseResource parsedResource = ctx.newJsonParser().parseResource(body);

        PluginContext pluginContext = PluginContext.builder()
                .operationType(OperationType.CREATE)
                .resourceType(resourceType)
                .fhirVersion(version)
                .inputResource(parsedResource)
                .build();

        // Execute BEFORE plugins (validation, enrichment)
        PluginResult beforeResult = pluginOrchestrator.executeBefore(pluginContext);
        if (beforeResult.isAborted()) {
            return buildAbortResponse(beforeResult, ctx);
        }

        // Re-encode the resource after plugins (plugins may modify in-place)
        IBaseResource modified = pluginContext.getInputResource().orElse(parsedResource);
        String effectiveBody = ctx.newJsonParser().encodeResourceToString(modified);

        // Execute core operation
        ResourceResult result = resourceService.create(resourceType, effectiveBody, version);

        // Set output resource on context for AFTER plugins
        IBaseResource outputResource = ctx.newJsonParser().parseResource(result.content());
        pluginContext.setOutputResource(outputResource);

        // Execute AFTER plugins (notifications, enrichment)
        pluginOrchestrator.executeAfter(pluginContext);

        String locationUri = String.format("/fhir/%s/%s/%s/_history/%d",
                version.getCode(), resourceType, result.resourceId(), result.versionId());

        return ResponseEntity
                .created(URI.create(locationUri))
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .header("Last-Modified", formatLastModified(result))
                .body(result.content());
    }

    // ========== UPDATE Operations ==========

    /**
     * Update an existing resource (versioned path).
     */
    @PutMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE,
                    MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> updateVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody String body,
            HttpServletRequest request) {
        return update(resourceType, id, body, request);
    }

    /**
     * Update an existing resource (unversioned path).
     */
    @PutMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE,
                    MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> updateUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody String body,
            HttpServletRequest request) {
        return update(resourceType, id, body, request);
    }

    private ResponseEntity<String> update(String resourceType, String id, String body, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("UPDATE {}/{} (version={})", resourceType, id, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.UPDATE);

        // Check if this is a create or update
        boolean exists = resourceService.exists(resourceType, id);

        ResourceResult result = resourceService.update(resourceType, id, body, version);

        String locationUri = String.format("/fhir/%s/%s/%s/_history/%d",
                version.getCode(), resourceType, id, result.versionId());

        ResponseEntity.BodyBuilder builder = exists
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create(locationUri));

        return builder
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .header("Content-Location", locationUri)
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .header("Last-Modified", formatLastModified(result))
                .body(result.content());
    }

    // ========== PATCH Operations ==========

    /**
     * Patch an existing resource (versioned path).
     */
    @PatchMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            consumes = {FhirMediaType.APPLICATION_JSON_PATCH_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> patchVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody String body,
            HttpServletRequest request) {
        return patch(resourceType, id, body, request);
    }

    /**
     * Patch an existing resource (unversioned path).
     */
    @PatchMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}",
            consumes = {FhirMediaType.APPLICATION_JSON_PATCH_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> patchUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody String body,
            HttpServletRequest request) {
        return patch(resourceType, id, body, request);
    }

    private ResponseEntity<String> patch(String resourceType, String id, String body, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("PATCH {}/{} (version={})", resourceType, id, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.PATCH);

        // Read the current resource
        ResourceResult current = resourceService.read(resourceType, id, version);

        // Apply JSON Patch
        String patchedJson;
        try {
            patchedJson = jsonPatchService.applyPatch(current.content(), body);
        } catch (Exception e) {
            log.warn("Failed to apply JSON Patch to {}/{}: {}", resourceType, id, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                    .body("{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"invalid\",\"diagnostics\":\"" +
                            e.getMessage().replace("\"", "'") + "\"}]}");
        }

        // Update via the standard update path
        ResourceResult result = resourceService.update(resourceType, id, patchedJson, version);

        String locationUri = String.format("/fhir/%s/%s/%s/_history/%d",
                version.getCode(), resourceType, id, result.versionId());

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .header("Content-Location", locationUri)
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .header("Last-Modified", formatLastModified(result))
                .body(result.content());
    }

    // ========== DELETE Operations ==========

    /**
     * Delete a resource (versioned path).
     */
    @DeleteMapping(path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}")
    public ResponseEntity<Void> deleteVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            HttpServletRequest request) {
        return delete(resourceType, id, request);
    }

    /**
     * Delete a resource (unversioned path).
     */
    @DeleteMapping(path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}")
    public ResponseEntity<Void> deleteUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            HttpServletRequest request) {
        return delete(resourceType, id, request);
    }

    private ResponseEntity<Void> delete(String resourceType, String id, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("DELETE {}/{} (version={})", resourceType, id, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.DELETE);

        ResourceResult result = resourceService.delete(resourceType, id, version);

        return ResponseEntity
                .noContent()
                .header("ETag", "W/\"" + result.versionId() + "\"")
                .build();
    }

    // ========== SEARCH Operations ==========

    /**
     * Search resources via GET (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchVersionedGet(
            @PathVariable String version,
            @PathVariable String resourceType,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return search(resourceType, params, request);
    }

    /**
     * Search resources via GET (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchUnversionedGet(
            @PathVariable String resourceType,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return search(resourceType, params, request);
    }

    /**
     * Search resources via POST (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/_search",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchVersionedPost(
            @PathVariable String version,
            @PathVariable String resourceType,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return search(resourceType, params, request);
    }

    /**
     * Search resources via POST (unversioned path).
     */
    @PostMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/_search",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchUnversionedPost(
            @PathVariable String resourceType,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return search(resourceType, params, request);
    }

    private ResponseEntity<String> search(String resourceType, Map<String, String> params, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("SEARCH {} with params {} (version={})", resourceType, params.keySet(), version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.SEARCH);

        // Get count parameter or default to 20
        int count = 20;
        if (params.containsKey("_count")) {
            try {
                count = Integer.parseInt(params.get("_count"));
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        Bundle bundle = resourceService.search(resourceType, params, version, count);

        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(parser.encodeResourceToString(bundle));
    }

    // ========== HISTORY Operations ==========

    /**
     * Get history for a resource (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}/_history",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> historyVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return history(resourceType, id, params, request);
    }

    /**
     * Get history for a resource (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:" + RESOURCE_TYPE_PATTERN + "}/{id}/_history",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> historyUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return history(resourceType, id, params, request);
    }

    private ResponseEntity<String> history(String resourceType, String id, Map<String, String> params, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("HISTORY {}/{} (version={})", resourceType, id, version);

        interactionGuard.validateInteraction(resourceType, version, InteractionType.HISTORY);

        Bundle bundle = resourceService.history(resourceType, id, version);

        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(parser.encodeResourceToString(bundle));
    }

    private String formatLastModified(ResourceResult result) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                result.lastUpdated().atZone(java.time.ZoneOffset.UTC));
    }

    /**
     * Build an error response from an aborted plugin result.
     */
    private ResponseEntity<String> buildAbortResponse(PluginResult pluginResult, FhirContext ctx) {
        int status = pluginResult.getHttpStatus() > 0 ? pluginResult.getHttpStatus() : 400;

        OperationOutcome outcome = pluginResult.getOperationOutcome().orElseGet(() -> {
            OperationOutcome oo = new OperationOutcome();
            OperationOutcome.OperationOutcomeIssueComponent issue = oo.addIssue();
            issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
            issue.setCode(OperationOutcome.IssueType.PROCESSING);
            issue.setDiagnostics(pluginResult.getMessage().orElse("Operation aborted by plugin"));
            return oo;
        });

        String body = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);

        return ResponseEntity
                .status(HttpStatus.valueOf(status))
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(body);
    }
}
