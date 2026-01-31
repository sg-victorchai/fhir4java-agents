package org.fhirframework.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.api.config.FhirMediaType;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.FhirException;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.service.BundleProcessorService;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginOrchestrator;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.OperationOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for FHIR batch and transaction bundle processing.
 * <p>
 * Handles POST to /fhir and /fhir/{version} for Bundle resources
 * of type batch or transaction.
 * </p>
 */
@RestController
public class BundleController {

    private static final Logger log = LoggerFactory.getLogger(BundleController.class);

    private final FhirContextFactory contextFactory;
    private final BundleProcessorService bundleProcessorService;
    private final PluginOrchestrator pluginOrchestrator;

    public BundleController(FhirContextFactory contextFactory,
                            BundleProcessorService bundleProcessorService,
                            PluginOrchestrator pluginOrchestrator) {
        this.contextFactory = contextFactory;
        this.bundleProcessorService = bundleProcessorService;
        this.pluginOrchestrator = pluginOrchestrator;
    }

    /**
     * Process a batch/transaction bundle (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:r5|r4b|R5|R4B}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> processBundleVersioned(
            @PathVariable String version,
            @RequestBody String body,
            HttpServletRequest request) {
        return processBundle(body, request);
    }

    /**
     * Process a batch/transaction bundle (unversioned path).
     */
    @PostMapping(
            path = "/fhir",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> processBundleUnversioned(
            @RequestBody String body,
            HttpServletRequest request) {
        return processBundle(body, request);
    }

    private ResponseEntity<String> processBundle(String body, HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("POST bundle (version={})", version);

        FhirContext ctx = contextFactory.getContext(version);
        IParser parser = ctx.newJsonParser().setPrettyPrint(true);

        // Parse the incoming bundle
        Bundle bundle;
        try {
            bundle = parser.parseResource(Bundle.class, body);
        } catch (Exception e) {
            return buildErrorResponse(ctx, "Invalid Bundle resource: " + e.getMessage(), 400);
        }

        // Validate bundle type
        BundleType bundleType = bundle.getType();
        if (bundleType != BundleType.BATCH && bundleType != BundleType.TRANSACTION) {
            return buildErrorResponse(ctx,
                    "Bundle type '" + bundleType + "' is not supported. Only 'batch' and 'transaction' are allowed.",
                    400);
        }

        // Execute BEFORE plugins
        PluginContext pluginContext = PluginContext.builder()
                .operationType(OperationType.BATCH)
                .fhirVersion(version)
                .inputResource(bundle)
                .build();

        PluginResult beforeResult = pluginOrchestrator.executeBefore(pluginContext);
        if (beforeResult.isAborted()) {
            return buildAbortResponse(beforeResult, ctx);
        }

        // Process the bundle
        Bundle response;
        try {
            response = bundleProcessorService.processBundle(bundle, version);
        } catch (FhirException e) {
            return buildErrorResponse(ctx, e.getMessage(), 400);
        } catch (Exception e) {
            log.error("Bundle processing failed", e);
            return buildErrorResponse(ctx, "Bundle processing failed: " + e.getMessage(), 500);
        }

        // Execute AFTER plugins
        pluginContext.setOutputResource(response);
        pluginOrchestrator.executeAfter(pluginContext);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(parser.encodeResourceToString(response));
    }

    private ResponseEntity<String> buildErrorResponse(FhirContext ctx, String message, int status) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        issue.setDiagnostics(message);

        String body = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome);

        return ResponseEntity
                .status(HttpStatus.valueOf(status))
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(body);
    }

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
