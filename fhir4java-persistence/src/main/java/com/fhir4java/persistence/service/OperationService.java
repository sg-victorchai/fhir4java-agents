package com.fhir4java.persistence.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir4java.core.context.FhirContextFactory;
import com.fhir4java.core.operation.*;
import com.fhir4java.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service for executing FHIR extended operations.
 */
@Service
public class OperationService {

    private static final Logger log = LoggerFactory.getLogger(OperationService.class);

    private final OperationRegistry operationRegistry;
    private final FhirContextFactory contextFactory;
    private final FhirResourceService resourceService;

    public OperationService(OperationRegistry operationRegistry,
                            FhirContextFactory contextFactory,
                            FhirResourceService resourceService) {
        this.operationRegistry = operationRegistry;
        this.contextFactory = contextFactory;
        this.resourceService = resourceService;
    }

    /**
     * Execute a system-level operation.
     *
     * @param operationName the operation name (without $)
     * @param inputJson     the input resource JSON (may be null)
     * @param params        query parameters
     * @param version       the FHIR version
     * @return operation result
     */
    public OperationResult executeSystemOperation(String operationName, String inputJson,
                                                   Map<String, String> params, FhirVersion version) {
        return executeOperation(operationName, OperationScope.SYSTEM, null, null, inputJson, params, version);
    }

    /**
     * Execute a type-level operation.
     *
     * @param operationName the operation name (without $)
     * @param resourceType  the resource type
     * @param inputJson     the input resource JSON (may be null)
     * @param params        query parameters
     * @param version       the FHIR version
     * @return operation result
     */
    public OperationResult executeTypeOperation(String operationName, String resourceType,
                                                 String inputJson, Map<String, String> params,
                                                 FhirVersion version) {
        return executeOperation(operationName, OperationScope.TYPE, resourceType, null, inputJson, params, version);
    }

    /**
     * Execute an instance-level operation.
     *
     * @param operationName the operation name (without $)
     * @param resourceType  the resource type
     * @param resourceId    the resource ID
     * @param inputJson     the input resource JSON (may be null)
     * @param params        query parameters
     * @param version       the FHIR version
     * @return operation result
     */
    public OperationResult executeInstanceOperation(String operationName, String resourceType,
                                                     String resourceId, String inputJson,
                                                     Map<String, String> params, FhirVersion version) {
        return executeOperation(operationName, OperationScope.INSTANCE, resourceType, resourceId, inputJson, params, version);
    }

    private OperationResult executeOperation(String operationName, OperationScope scope,
                                              String resourceType, String resourceId,
                                              String inputJson, Map<String, String> params,
                                              FhirVersion version) {
        log.debug("Executing operation ${} at {} level for {}/{}",
                operationName, scope, resourceType, resourceId);

        Optional<OperationHandler> handlerOpt = operationRegistry.findHandler(
                operationName, scope, resourceType, version);

        if (handlerOpt.isEmpty()) {
            return createNotSupportedResult(operationName, scope, resourceType, resourceId, version);
        }

        OperationHandler handler = handlerOpt.get();

        // Parse input resource if provided
        IBaseResource inputResource = null;
        if (inputJson != null && !inputJson.isBlank()) {
            FhirContext context = contextFactory.getContext(version);
            IParser parser = context.newJsonParser();
            try {
                inputResource = parser.parseResource(inputJson);
            } catch (Exception e) {
                log.warn("Failed to parse input for operation ${}: {}", operationName, e.getMessage());
                return createInvalidInputResult(operationName, e.getMessage(), version);
            }
        }

        // Build context
        OperationContext context = OperationContext.builder()
                .operationName(operationName)
                .scope(scope)
                .version(version)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .inputResource(inputResource)
                .parameters(params)
                .build();

        try {
            IBaseResource result = handler.execute(context);
            return new OperationResult(true, result, 200);
        } catch (Exception e) {
            log.error("Error executing operation ${}: {}", operationName, e.getMessage(), e);
            return createErrorResult(operationName, e, version);
        }
    }

    private OperationResult createNotSupportedResult(String operationName, OperationScope scope,
                                                      String resourceType, String resourceId,
                                                      FhirVersion version) {
        String location;
        if (resourceType == null) {
            location = "system";
        } else if (resourceId == null) {
            location = resourceType;
        } else {
            location = resourceType + "/" + resourceId;
        }

        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.NOTSUPPORTED)
                .setDiagnostics(String.format("Operation '$%s' is not supported at %s level",
                        operationName, location));

        return new OperationResult(false, outcome, 501);
    }

    private OperationResult createInvalidInputResult(String operationName, String message,
                                                      FhirVersion version) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.INVALID)
                .setDiagnostics(String.format("Invalid input for operation '$%s': %s",
                        operationName, message));

        return new OperationResult(false, outcome, 400);
    }

    private OperationResult createErrorResult(String operationName, Exception e,
                                               FhirVersion version) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.EXCEPTION)
                .setDiagnostics(String.format("Error executing operation '$%s': %s",
                        operationName, e.getMessage()));

        return new OperationResult(false, outcome, 500);
    }

    /**
     * Check if an operation is supported.
     */
    public boolean isSupported(String operationName, OperationScope scope,
                               String resourceType, FhirVersion version) {
        return operationRegistry.isSupported(operationName, scope, resourceType, version);
    }

    /**
     * Result of an operation execution.
     */
    public record OperationResult(
            boolean success,
            IBaseResource result,
            int httpStatus
    ) {}
}
