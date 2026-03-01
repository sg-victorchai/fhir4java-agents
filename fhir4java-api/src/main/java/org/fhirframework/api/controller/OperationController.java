package org.fhirframework.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.api.config.FhirMediaType;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.guard.InteractionGuard;
import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.service.OperationService;
import org.fhirframework.persistence.service.OperationService.OperationResult;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginOrchestrator;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.r5.model.OperationOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for FHIR extended operations ($operation).
 * <p>
 * Handles operations at system level ({@code /fhir/$operation}),
 * type level ({@code /fhir/Patient/$operation}), and
 * instance level ({@code /fhir/Patient/123/$operation}).
 * </p>
 * <p>
 * Note: Uses regex patterns to match the $ character in operation names.
 * </p>
 */
@RestController
public class OperationController {

    private static final Logger log = LoggerFactory.getLogger(OperationController.class);

    private final FhirContextFactory contextFactory;
    private final InteractionGuard interactionGuard;
    private final OperationService operationService;
    private final PluginOrchestrator pluginOrchestrator;

    public OperationController(FhirContextFactory contextFactory,
                               InteractionGuard interactionGuard,
                               OperationService operationService,
                               PluginOrchestrator pluginOrchestrator) {
        this.contextFactory = contextFactory;
        this.interactionGuard = interactionGuard;
        this.operationService = operationService;
        this.pluginOrchestrator = pluginOrchestrator;
    }

    // ========== System-Level Operations ==========

    /**
     * System-level operation via GET (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> systemOperationGetVersioned(
            @PathVariable String version,
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return systemOperation(operation, null, params, request);
    }

    /**
     * System-level operation via GET (unversioned path).
     */
    @GetMapping(
            path = "/fhir/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> systemOperationGetUnversioned(
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return systemOperation(operation, null, params, request);
    }

    /**
     * System-level operation via POST (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> systemOperationPostVersioned(
            @PathVariable String version,
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return systemOperation(operation, body, params, request);
    }

    /**
     * System-level operation via POST (unversioned path).
     */
    @PostMapping(
            path = "/fhir/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> systemOperationPostUnversioned(
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return systemOperation(operation, body, params, request);
    }

    private ResponseEntity<String> systemOperation(String operation, String body,
                                                    Map<String, String> params,
                                                    HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("System operation ${} (version={})", operation, version);

        FhirContext ctx = contextFactory.getContext(version);

        // Build plugin context (system-level: no resourceType)
        PluginContext pluginContext = PluginContext.builder()
                .operationType(OperationType.OPERATION)
                .operationCode(operation)
                .fhirVersion(version)
                .tenantId(TenantContext.getCurrentTenantId())
                .build();

        // Execute BEFORE plugins
        PluginResult beforeResult = pluginOrchestrator.executeBefore(pluginContext);
        if (beforeResult.isAborted()) {
            return buildAbortResponse(beforeResult, ctx);
        }

        // Execute core operation
        OperationResult result = operationService.executeSystemOperation(
                operation, body, params != null ? params : new HashMap<>(), version);

        // Set output on context for AFTER plugins
        pluginContext.setOutputResource(result.result());

        // Execute AFTER plugins
        pluginOrchestrator.executeAfter(pluginContext);

        return buildResponse(result, version);
    }

    // ========== Type-Level Operations ==========

    /**
     * Type-level operation via GET (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/{resourceType:[A-Z][a-zA-Z]+}/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> typeOperationGetVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return typeOperation(resourceType, operation, null, params, request);
    }

    /**
     * Type-level operation via GET (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:[A-Z][a-zA-Z]+}/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> typeOperationGetUnversioned(
            @PathVariable String resourceType,
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return typeOperation(resourceType, operation, null, params, request);
    }

    /**
     * Type-level operation via POST (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/{resourceType:[A-Z][a-zA-Z]+}/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> typeOperationPostVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return typeOperation(resourceType, operation, body, params, request);
    }

    /**
     * Type-level operation via POST (unversioned path).
     */
    @PostMapping(
            path = "/fhir/{resourceType:[A-Z][a-zA-Z]+}/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> typeOperationPostUnversioned(
            @PathVariable String resourceType,
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return typeOperation(resourceType, operation, body, params, request);
    }

    private ResponseEntity<String> typeOperation(String resourceType, String operation,
                                                  String body, Map<String, String> params,
                                                  HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("Type operation {}/${} (version={})", resourceType, operation, version);

        interactionGuard.validateResourceType(resourceType);

        FhirContext ctx = contextFactory.getContext(version);

        // Build plugin context
        PluginContext pluginContext = PluginContext.builder()
                .operationType(OperationType.OPERATION)
                .operationCode(operation)
                .resourceType(resourceType)
                .fhirVersion(version)
                .tenantId(TenantContext.getCurrentTenantId())
                .build();

        // Execute BEFORE plugins
        PluginResult beforeResult = pluginOrchestrator.executeBefore(pluginContext);
        if (beforeResult.isAborted()) {
            return buildAbortResponse(beforeResult, ctx);
        }

        // Execute core operation
        OperationResult result = operationService.executeTypeOperation(
                operation, resourceType, body, params != null ? params : new HashMap<>(), version);

        // Set output on context for AFTER plugins
        pluginContext.setOutputResource(result.result());

        // Execute AFTER plugins
        pluginOrchestrator.executeAfter(pluginContext);

        return buildResponse(result, version);
    }

    // ========== Instance-Level Operations ==========

    /**
     * Instance-level operation via GET (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/{resourceType:[A-Z][a-zA-Z]+}/{id}/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> instanceOperationGetVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return instanceOperation(resourceType, id, operation, null, params, request);
    }

    /**
     * Instance-level operation via GET (unversioned path).
     */
    @GetMapping(
            path = "/fhir/{resourceType:[A-Z][a-zA-Z]+}/{id}/\\${operation}",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> instanceOperationGetUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable("operation") String operation,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return instanceOperation(resourceType, id, operation, null, params, request);
    }

    /**
     * Instance-level operation via POST (versioned path).
     */
    @PostMapping(
            path = "/fhir/{version:(?i)r[0-9][a-z0-9]*}/{resourceType:[A-Z][a-zA-Z]+}/{id}/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> instanceOperationPostVersioned(
            @PathVariable String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return instanceOperation(resourceType, id, operation, body, params, request);
    }

    /**
     * Instance-level operation via POST (unversioned path).
     */
    @PostMapping(
            path = "/fhir/{resourceType:[A-Z][a-zA-Z]+}/{id}/\\${operation}",
            consumes = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> instanceOperationPostUnversioned(
            @PathVariable String resourceType,
            @PathVariable String id,
            @PathVariable("operation") String operation,
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return instanceOperation(resourceType, id, operation, body, params, request);
    }

    private ResponseEntity<String> instanceOperation(String resourceType, String id, String operation,
                                                     String body, Map<String, String> params,
                                                     HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("Instance operation {}/{}/{} (version={})", resourceType, id, operation, version);

        interactionGuard.validateResourceType(resourceType);

        FhirContext ctx = contextFactory.getContext(version);

        // Build plugin context
        PluginContext pluginContext = PluginContext.builder()
                .operationType(OperationType.OPERATION)
                .operationCode(operation)
                .resourceType(resourceType)
                .resourceId(id)
                .fhirVersion(version)
                .tenantId(TenantContext.getCurrentTenantId())
                .build();

        // Execute BEFORE plugins
        PluginResult beforeResult = pluginOrchestrator.executeBefore(pluginContext);
        if (beforeResult.isAborted()) {
            return buildAbortResponse(beforeResult, ctx);
        }

        // Execute core operation
        OperationResult result = operationService.executeInstanceOperation(
                operation, resourceType, id, body, params != null ? params : new HashMap<>(), version);

        // Set output on context for AFTER plugins
        pluginContext.setOutputResource(result.result());

        // Execute AFTER plugins
        pluginOrchestrator.executeAfter(pluginContext);

        return buildResponse(result, version);
    }

    private ResponseEntity<String> buildResponse(OperationResult result, FhirVersion version) {
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        String body = parser.encodeResourceToString(result.result());
        HttpStatus status = HttpStatus.valueOf(result.httpStatus());

        return ResponseEntity
                .status(status)
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
