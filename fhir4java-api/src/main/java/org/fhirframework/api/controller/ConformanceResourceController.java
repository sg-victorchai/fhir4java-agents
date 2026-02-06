package org.fhirframework.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServletRequest;
import org.fhirframework.api.config.FhirMediaType;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.conformance.ConformanceResourceRegistry;
import org.fhirframework.core.conformance.ConformanceResourceType;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.Bundle.SearchEntryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for FHIR conformance resources.
 * <p>
 * Provides read-only endpoints for StructureDefinition, SearchParameter,
 * and OperationDefinition resources that are loaded from static JSON files.
 * </p>
 * <p>
 * Supports both versioned paths ({@code /fhir/r5/StructureDefinition/Patient})
 * and unversioned paths ({@code /fhir/StructureDefinition/Patient}).
 * </p>
 */
@RestController
public class ConformanceResourceController {

    private static final Logger log = LoggerFactory.getLogger(ConformanceResourceController.class);

    private static final String VERSION_PATTERN = "r5|r4b|R5|R4B";
    private static final int DEFAULT_COUNT = 20;
    private static final int MAX_COUNT = 1000;

    private final ConformanceResourceRegistry registry;
    private final FhirContextFactory contextFactory;

    public ConformanceResourceController(ConformanceResourceRegistry registry,
                                         FhirContextFactory contextFactory) {
        this.registry = registry;
        this.contextFactory = contextFactory;
    }

    // ========== StructureDefinition Endpoints ==========

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/StructureDefinition/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readStructureDefinitionVersioned(
            @PathVariable String version,
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.STRUCTURE_DEFINITION, id, request);
    }

    @GetMapping(
            path = "/fhir/StructureDefinition/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readStructureDefinitionUnversioned(
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.STRUCTURE_DEFINITION, id, request);
    }

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/StructureDefinition",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchStructureDefinitionVersioned(
            @PathVariable String version,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.STRUCTURE_DEFINITION, params, request);
    }

    @GetMapping(
            path = "/fhir/StructureDefinition",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchStructureDefinitionUnversioned(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.STRUCTURE_DEFINITION, params, request);
    }

    // ========== SearchParameter Endpoints ==========

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/SearchParameter/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readSearchParameterVersioned(
            @PathVariable String version,
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.SEARCH_PARAMETER, id, request);
    }

    @GetMapping(
            path = "/fhir/SearchParameter/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readSearchParameterUnversioned(
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.SEARCH_PARAMETER, id, request);
    }

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/SearchParameter",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchSearchParameterVersioned(
            @PathVariable String version,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.SEARCH_PARAMETER, params, request);
    }

    @GetMapping(
            path = "/fhir/SearchParameter",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchSearchParameterUnversioned(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.SEARCH_PARAMETER, params, request);
    }

    // ========== OperationDefinition Endpoints ==========

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/OperationDefinition/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readOperationDefinitionVersioned(
            @PathVariable String version,
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.OPERATION_DEFINITION, id, request);
    }

    @GetMapping(
            path = "/fhir/OperationDefinition/{id}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> readOperationDefinitionUnversioned(
            @PathVariable String id,
            HttpServletRequest request) {
        return readConformanceResource(ConformanceResourceType.OPERATION_DEFINITION, id, request);
    }

    @GetMapping(
            path = "/fhir/{version:" + VERSION_PATTERN + "}/OperationDefinition",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchOperationDefinitionVersioned(
            @PathVariable String version,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.OPERATION_DEFINITION, params, request);
    }

    @GetMapping(
            path = "/fhir/OperationDefinition",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> searchOperationDefinitionUnversioned(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return searchConformanceResources(ConformanceResourceType.OPERATION_DEFINITION, params, request);
    }

    // ========== Private Helper Methods ==========

    private ResponseEntity<String> readConformanceResource(
            ConformanceResourceType type, String id, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("READ {}/{} (version={})", type.getResourceTypeName(), id, version);

        Optional<String> content = registry.getById(version, type, id);
        if (content.isEmpty()) {
            throw new ResourceNotFoundException(type.getResourceTypeName(), id);
        }

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(content.get());
    }

    private ResponseEntity<String> searchConformanceResources(
            ConformanceResourceType type, Map<String, String> params, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("SEARCH {} with params {} (version={})", type.getResourceTypeName(), params.keySet(), version);

        // Extract pagination parameters
        int count = parseCount(params.get("_count"));
        int offset = parseOffset(params.get("_offset"));

        // Build search params (exclude pagination params)
        Map<String, String> searchParams = new HashMap<>(params);
        searchParams.remove("_count");
        searchParams.remove("_offset");

        // Execute search
        List<String> results = registry.search(version, type, searchParams, count, offset);
        int total = registry.searchCount(version, type, searchParams);

        // Build Bundle response
        Bundle bundle = buildSearchBundle(version, type, results, total, count, offset, request);

        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(parser.encodeResourceToString(bundle));
    }

    private Bundle buildSearchBundle(FhirVersion version, ConformanceResourceType type,
                                     List<String> results, int total, int count, int offset,
                                     HttpServletRequest request) {
        Bundle bundle = new Bundle();
        bundle.setType(BundleType.SEARCHSET);
        bundle.setTotal(total);

        // Build base URL for pagination links
        String baseUrl = buildBaseUrl(request);

        // Add self link
        bundle.addLink(new BundleLinkComponent()
                .setRelation(Bundle.LinkRelationTypes.SELF)
                .setUrl(buildPageUrl(baseUrl, count, offset)));

        // Add first link
        bundle.addLink(new BundleLinkComponent()
                .setRelation(Bundle.LinkRelationTypes.FIRST)
                .setUrl(buildPageUrl(baseUrl, count, 0)));

        // Add previous link if applicable
        if (offset > 0) {
            int prevOffset = Math.max(0, offset - count);
            bundle.addLink(new BundleLinkComponent()
                    .setRelation(Bundle.LinkRelationTypes.PREVIOUS)
                    .setUrl(buildPageUrl(baseUrl, count, prevOffset)));
        }

        // Add next link if applicable
        if (offset + results.size() < total) {
            int nextOffset = offset + count;
            bundle.addLink(new BundleLinkComponent()
                    .setRelation(Bundle.LinkRelationTypes.NEXT)
                    .setUrl(buildPageUrl(baseUrl, count, nextOffset)));
        }

        // Add last link
        int lastOffset = Math.max(0, ((total - 1) / count) * count);
        bundle.addLink(new BundleLinkComponent()
                .setRelation(Bundle.LinkRelationTypes.LAST)
                .setUrl(buildPageUrl(baseUrl, count, lastOffset)));

        // Add entries
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser();

        for (String json : results) {
            try {
                BundleEntryComponent entry = new BundleEntryComponent();
                entry.setFullUrl(buildFullUrl(request, type, extractIdFromJson(json, parser, type)));
                entry.setResource((org.hl7.fhir.r5.model.Resource) parser.parseResource(json));
                entry.getSearch().setMode(SearchEntryMode.MATCH);
                bundle.addEntry(entry);
            } catch (Exception e) {
                log.warn("Failed to add conformance resource to bundle: {}", e.getMessage());
            }
        }

        return bundle;
    }

    private String extractIdFromJson(String json, IParser parser, ConformanceResourceType type) {
        try {
            var resource = parser.parseResource(json);
            return resource.getIdElement().getIdPart();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String buildBaseUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            // Remove pagination params from query string
            String cleanQuery = queryString
                    .replaceAll("(&|^)_count=[^&]*", "")
                    .replaceAll("(&|^)_offset=[^&]*", "")
                    .replaceAll("^&", "");
            if (!cleanQuery.isEmpty()) {
                url.append("?").append(cleanQuery);
            }
        }
        return url.toString();
    }

    private String buildPageUrl(String baseUrl, int count, int offset) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "_count=" + count + "&_offset=" + offset;
    }

    private String buildFullUrl(HttpServletRequest request, ConformanceResourceType type, String id) {
        String requestUrl = request.getRequestURL().toString();
        // Extract base URL up to the resource type
        int idx = requestUrl.lastIndexOf("/" + type.getResourceTypeName());
        if (idx > 0) {
            return requestUrl.substring(0, idx) + "/" + type.getResourceTypeName() + "/" + id;
        }
        return requestUrl + "/" + id;
    }

    private int parseCount(String countParam) {
        if (countParam == null) {
            return DEFAULT_COUNT;
        }
        try {
            int count = Integer.parseInt(countParam);
            return Math.min(Math.max(1, count), MAX_COUNT);
        } catch (NumberFormatException e) {
            return DEFAULT_COUNT;
        }
    }

    private int parseOffset(String offsetParam) {
        if (offsetParam == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(offsetParam));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
