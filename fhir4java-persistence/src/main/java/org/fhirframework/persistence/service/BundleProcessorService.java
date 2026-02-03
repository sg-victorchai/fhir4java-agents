package org.fhirframework.persistence.service;

import org.fhirframework.core.exception.FhirException;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.service.FhirResourceService.ResourceResult;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r5.model.Bundle.BundleEntryResponseComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.Bundle.HTTPVerb;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Service for processing FHIR batch and transaction bundles.
 */
@Service
public class BundleProcessorService {

    private static final Logger log = LoggerFactory.getLogger(BundleProcessorService.class);

    private final FhirResourceService resourceService;
    private final JsonPatchService jsonPatchService;

    public BundleProcessorService(FhirResourceService resourceService,
                                   JsonPatchService jsonPatchService) {
        this.resourceService = resourceService;
        this.jsonPatchService = jsonPatchService;
    }

    /**
     * Process a bundle (batch or transaction).
     */
    public Bundle processBundle(Bundle bundle, FhirVersion version) {
        BundleType type = bundle.getType();

        if (type == BundleType.BATCH) {
            return processBatch(bundle, version);
        } else if (type == BundleType.TRANSACTION) {
            return processTransaction(bundle, version);
        } else {
            throw new FhirException(
                    "Bundle type '" + type + "' is not supported. Only 'batch' and 'transaction' are allowed.",
                    "invalid");
        }
    }

    /**
     * Process a batch bundle — each entry independently, errors recorded per entry.
     */
    public Bundle processBatch(Bundle bundle, FhirVersion version) {
        log.info("Processing batch bundle with {} entries", bundle.getEntry().size());

        Bundle response = new Bundle();
        response.setType(BundleType.BATCHRESPONSE);

        for (BundleEntryComponent entry : bundle.getEntry()) {
            BundleEntryComponent responseEntry = new BundleEntryComponent();
            try {
                processEntry(entry, version, responseEntry);
            } catch (Exception e) {
                log.warn("Batch entry failed: {}", e.getMessage());
                BundleEntryResponseComponent errorResponse = new BundleEntryResponseComponent();
                errorResponse.setStatus(resolveErrorStatus(e));
                errorResponse.setOutcome(buildErrorOutcome(e.getMessage()));
                responseEntry.setResponse(errorResponse);
            }
            response.addEntry(responseEntry);
        }

        return response;
    }

    /**
     * Process a transaction bundle — all-or-nothing with rollback on failure.
     */
    @Transactional
    public Bundle processTransaction(Bundle bundle, FhirVersion version) {
        log.info("Processing transaction bundle with {} entries", bundle.getEntry().size());

        Bundle response = new Bundle();
        response.setType(BundleType.TRANSACTIONRESPONSE);

        for (BundleEntryComponent entry : bundle.getEntry()) {
            BundleEntryComponent responseEntry = new BundleEntryComponent();
            // Transaction entries re-throw exceptions for rollback
            processEntry(entry, version, responseEntry);
            response.addEntry(responseEntry);
        }

        return response;
    }

    private void processEntry(BundleEntryComponent entry, FhirVersion version,
                               BundleEntryComponent responseEntry) {
        BundleEntryRequestComponent request = entry.getRequest();
        if (request == null || request.getMethod() == null) {
            throw new FhirException("Bundle entry is missing request or method", "required");
        }

        HTTPVerb method = request.getMethod();
        String url = request.getUrl();
        EntryUrl parsed = parseEntryUrl(url);

        switch (method) {
            case GET -> handleGet(parsed, version, responseEntry);
            case POST -> handlePost(entry, parsed, version, responseEntry);
            case PUT -> handlePut(entry, parsed, version, responseEntry);
            case DELETE -> handleDelete(parsed, version, responseEntry);
            case PATCH -> handlePatch(entry, parsed, version, responseEntry);
            default -> throw new FhirException("Unsupported HTTP method: " + method, "not-supported");
        }
    }

    private void handleGet(EntryUrl parsed, FhirVersion version, BundleEntryComponent responseEntry) {
        if (parsed.resourceId != null) {
            ResourceResult result = resourceService.read(parsed.resourceType, parsed.resourceId, version);
            BundleEntryResponseComponent response = new BundleEntryResponseComponent();
            response.setStatus("200 OK");
            response.setEtag("W/\"" + result.versionId() + "\"");
            response.setLastModified(Date.from(result.lastUpdated()));
            responseEntry.setResponse(response);
        } else {
            // Search - use placeholder URL since we're in batch/transaction context
            String placeholderUrl = "urn:uuid:" + parsed.resourceType;
            Bundle searchResult = resourceService.search(parsed.resourceType, java.util.Map.of(), version, 20, placeholderUrl);
            BundleEntryResponseComponent response = new BundleEntryResponseComponent();
            response.setStatus("200 OK");
            responseEntry.setResource(searchResult);
            responseEntry.setResponse(response);
        }
    }

    private void handlePost(BundleEntryComponent entry, EntryUrl parsed, FhirVersion version,
                             BundleEntryComponent responseEntry) {
        Resource resource = entry.getResource();
        if (resource == null) {
            throw new FhirException("POST entry is missing resource", "required");
        }

        // Use the resourceType from the resource itself or the URL
        String resourceType = parsed.resourceType != null ? parsed.resourceType : resource.fhirType();

        // Encode resource to JSON
        String json = encodeResourceToJson(resource);

        ResourceResult result = resourceService.create(resourceType, json, version);

        BundleEntryResponseComponent response = new BundleEntryResponseComponent();
        response.setStatus("201 Created");
        response.setLocation(resourceType + "/" + result.resourceId() + "/_history/" + result.versionId());
        response.setEtag("W/\"" + result.versionId() + "\"");
        response.setLastModified(Date.from(result.lastUpdated()));
        responseEntry.setResponse(response);
    }

    private void handlePut(BundleEntryComponent entry, EntryUrl parsed, FhirVersion version,
                            BundleEntryComponent responseEntry) {
        Resource resource = entry.getResource();
        if (resource == null) {
            throw new FhirException("PUT entry is missing resource", "required");
        }
        if (parsed.resourceId == null) {
            throw new FhirException("PUT entry URL must include resource ID", "required");
        }

        String json = encodeResourceToJson(resource);

        ResourceResult result = resourceService.update(parsed.resourceType, parsed.resourceId, json, version);

        BundleEntryResponseComponent response = new BundleEntryResponseComponent();
        response.setStatus("200 OK");
        response.setEtag("W/\"" + result.versionId() + "\"");
        response.setLastModified(Date.from(result.lastUpdated()));
        responseEntry.setResponse(response);
    }

    private void handleDelete(EntryUrl parsed, FhirVersion version, BundleEntryComponent responseEntry) {
        if (parsed.resourceId == null) {
            throw new FhirException("DELETE entry URL must include resource ID", "required");
        }

        resourceService.delete(parsed.resourceType, parsed.resourceId, version);

        BundleEntryResponseComponent response = new BundleEntryResponseComponent();
        response.setStatus("204 No Content");
        responseEntry.setResponse(response);
    }

    private void handlePatch(BundleEntryComponent entry, EntryUrl parsed, FhirVersion version,
                              BundleEntryComponent responseEntry) {
        if (parsed.resourceId == null) {
            throw new FhirException("PATCH entry URL must include resource ID", "required");
        }

        Resource patchResource = entry.getResource();
        if (patchResource == null) {
            throw new FhirException("PATCH entry is missing resource/patch document", "required");
        }

        // Read current resource
        ResourceResult current = resourceService.read(parsed.resourceType, parsed.resourceId, version);

        // Apply patch
        String patchJson = encodeResourceToJson(patchResource);
        String patchedJson;
        try {
            patchedJson = jsonPatchService.applyPatch(current.content(), patchJson);
        } catch (Exception e) {
            throw new FhirException("Failed to apply patch: " + e.getMessage(), "invalid");
        }

        ResourceResult result = resourceService.update(parsed.resourceType, parsed.resourceId, patchedJson, version);

        BundleEntryResponseComponent response = new BundleEntryResponseComponent();
        response.setStatus("200 OK");
        response.setEtag("W/\"" + result.versionId() + "\"");
        response.setLastModified(Date.from(result.lastUpdated()));
        responseEntry.setResponse(response);
    }

    /**
     * Parse a bundle entry URL like "Patient/123" or "Patient".
     */
    static EntryUrl parseEntryUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new FhirException("Bundle entry URL is required", "required");
        }

        // Strip leading slash if present
        String cleaned = url.startsWith("/") ? url.substring(1) : url;

        // Strip /fhir/ prefix if present
        if (cleaned.startsWith("fhir/")) {
            cleaned = cleaned.substring(5);
        }

        // Split by /
        String[] parts = cleaned.split("/");
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new FhirException("Invalid bundle entry URL: " + url, "invalid");
        }

        String resourceType = parts[0];
        String resourceId = parts.length > 1 ? parts[1] : null;

        return new EntryUrl(resourceType, resourceId);
    }

    private String encodeResourceToJson(Resource resource) {
        // Use HAPI FHIR's built-in JSON encoding
        ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR5();
        return ctx.newJsonParser().encodeResourceToString(resource);
    }

    private String resolveErrorStatus(Exception e) {
        if (e instanceof FhirException fe) {
            return switch (fe.getIssueCode()) {
                case "not-found" -> "404 Not Found";
                case "conflict" -> "409 Conflict";
                case "invalid" -> "400 Bad Request";
                default -> "400 Bad Request";
            };
        }
        return "500 Internal Server Error";
    }

    private OperationOutcome buildErrorOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.PROCESSING);
        issue.setDiagnostics(message);
        return outcome;
    }

    record EntryUrl(String resourceType, String resourceId) {}
}
