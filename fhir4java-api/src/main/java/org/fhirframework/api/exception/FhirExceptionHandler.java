package org.fhirframework.api.exception;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.FhirException;
import org.fhirframework.core.exception.InteractionDisabledException;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.exception.TenantDisabledException;
import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.core.exception.VersionNotSupportedException;
import org.fhirframework.core.version.FhirVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for FHIR API endpoints.
 * <p>
 * Converts exceptions to FHIR OperationOutcome responses with appropriate
 * HTTP status codes following FHIR specification guidelines:
 * - 400 Bad Request: Malformed JSON/XML or unparseable content
 * - 422 Unprocessable Entity: Valid structure but fails validation/business rules
 * - 404 Not Found: Resource or endpoint doesn't exist
 * - 500 Internal Server Error: Unexpected server errors
 * </p>
 */
@RestControllerAdvice
public class FhirExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FhirExceptionHandler.class);

    private final FhirContextFactory contextFactory;

    public FhirExceptionHandler(FhirContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFound(ResourceNotFoundException ex,
                                                         HttpServletRequest request) {
        log.debug("Resource not found: {}", ex.getMessage());

        OperationOutcome outcome;
        if (ex.getResourceId() != null) {
            outcome = OperationOutcomeBuilder.notFound(ex.getResourceType(), ex.getResourceId());
        } else {
            outcome = OperationOutcomeBuilder.unsupportedResourceType(ex.getResourceType());
        }

        return buildResponse(outcome, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(VersionNotSupportedException.class)
    public ResponseEntity<String> handleVersionNotSupported(VersionNotSupportedException ex,
                                                            HttpServletRequest request) {
        log.debug("Version not supported: {}", ex.getMessage());

        OperationOutcome outcome = OperationOutcomeBuilder.unsupportedVersion(
                ex.getResourceType(), ex.getRequestedVersion().getCode());

        return buildResponse(outcome, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InteractionDisabledException.class)
    public ResponseEntity<String> handleInteractionDisabled(InteractionDisabledException ex,
                                                            HttpServletRequest request) {
        log.debug("Interaction disabled: {}", ex.getMessage());

        OperationOutcome outcome = OperationOutcomeBuilder.interactionDisabled(
                ex.getResourceType(), ex.getInteraction().getCode());

        return buildResponse(outcome, HttpStatus.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<String> handleTenantNotFound(TenantNotFoundException ex,
                                                       HttpServletRequest request) {
        log.warn("Tenant not found: {}", ex.getMessage());

        OperationOutcome outcome = OperationOutcomeBuilder.badRequest(ex.getMessage());
        return buildResponse(outcome, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(TenantDisabledException.class)
    public ResponseEntity<String> handleTenantDisabled(TenantDisabledException ex,
                                                       HttpServletRequest request) {
        log.warn("Tenant disabled: {}", ex.getMessage());

        OperationOutcome outcome = new OperationOutcomeBuilder()
                .error(IssueType.FORBIDDEN, ex.getMessage(), ex.getDiagnostics())
                .build();
        return buildResponse(outcome, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(FhirException.class)
    public ResponseEntity<String> handleFhirException(FhirException ex,
                                                      HttpServletRequest request) {
        log.warn("FHIR exception: {}", ex.getMessage());

        OperationOutcome outcome = new OperationOutcomeBuilder()
                .error(mapIssueCode(ex.getIssueCode()), ex.getMessage(), ex.getDiagnostics())
                .build();

        HttpStatus status = mapToHttpStatus(ex.getIssueCode());
        return buildResponse(outcome, status, request);
    }

    /**
     * Handle HAPI FHIR parsing errors (e.g., invalid JSON structure, invalid enum values).
     * Returns 422 Unprocessable Entity as the content is well-formed but semantically invalid.
     */
    @ExceptionHandler(DataFormatException.class)
    public ResponseEntity<String> handleDataFormatException(DataFormatException ex,
                                                            HttpServletRequest request) {
        log.debug("Invalid resource format: {}", ex.getMessage());

        // Extract more meaningful error message from DataFormatException
        String errorMessage = ex.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = "Invalid resource format or content";
        }

        OperationOutcome outcome = new OperationOutcomeBuilder()
                .error(IssueType.STRUCTURE, "Resource validation failed", errorMessage)
                .build();

        return buildResponse(outcome, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<String> handleNoHandlerFound(NoHandlerFoundException ex,
                                                       HttpServletRequest request) {
        log.debug("No handler found: {}", ex.getRequestURL());

        OperationOutcome outcome = OperationOutcomeBuilder.notFound("Unknown", ex.getRequestURL());
        return buildResponse(outcome, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex,
                                                      HttpServletRequest request) {
        // Static resources like favicon.ico - just return 404 without FHIR OperationOutcome
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex,
                                                        HttpServletRequest request) {
        log.debug("Invalid argument: {}", ex.getMessage());

        OperationOutcome outcome = OperationOutcomeBuilder.badRequest(ex.getMessage());
        return buildResponse(outcome, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<String> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                               HttpServletRequest request) {
        log.debug("Media type not acceptable: {}", ex.getMessage());

        OperationOutcome outcome = OperationOutcomeBuilder.badRequest(
                "The requested media type is not supported. Use application/fhir+json or application/json.");
        return buildResponse(outcome, HttpStatus.NOT_ACCEPTABLE, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex,
                                                         HttpServletRequest request) {
        log.error("Unexpected error processing FHIR request", ex);

        OperationOutcome outcome = OperationOutcomeBuilder.internalError(
                "An unexpected error occurred",
                ex.getClass().getSimpleName() + ": " + ex.getMessage());

        return buildResponse(outcome, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<String> buildResponse(OperationOutcome outcome,
                                                 HttpStatus status,
                                                 HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        String body = parser.encodeResourceToString(outcome);

        // Use APPLICATION_JSON which is universally supported by Spring's message converters
        // The response is still valid FHIR JSON
        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private IssueType mapIssueCode(String issueCode) {
        if (issueCode == null) {
            return IssueType.EXCEPTION;
        }

        return switch (issueCode.toLowerCase()) {
            case "not-found" -> IssueType.NOTFOUND;
            case "not-supported" -> IssueType.NOTSUPPORTED;
            case "invalid" -> IssueType.INVALID;
            case "structure" -> IssueType.STRUCTURE;
            case "required" -> IssueType.REQUIRED;
            case "value" -> IssueType.VALUE;
            case "invariant" -> IssueType.INVARIANT;
            case "forbidden" -> IssueType.FORBIDDEN;
            case "security" -> IssueType.SECURITY;
            case "processing" -> IssueType.PROCESSING;
            case "business-rule" -> IssueType.BUSINESSRULE;
            case "conflict" -> IssueType.CONFLICT;
            case "duplicate" -> IssueType.DUPLICATE;
            case "expired" -> IssueType.EXPIRED;
            case "too-costly" -> IssueType.TOOCOSTLY;
            case "informational" -> IssueType.INFORMATIONAL;
            default -> IssueType.EXCEPTION;
        };
    }

    /**
     * Map FHIR issue codes to appropriate HTTP status codes following FHIR specification.
     * 
     * @param issueCode The FHIR issue code from FhirException
     * @return Appropriate HTTP status code
     */
    private HttpStatus mapToHttpStatus(String issueCode) {
        if (issueCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (issueCode.toLowerCase()) {
            // 404 Not Found
            case "not-found" -> HttpStatus.NOT_FOUND;
            
            // 501 Not Implemented
            case "not-supported" -> HttpStatus.NOT_IMPLEMENTED;
            
            // 422 Unprocessable Entity - Validation and business rule failures
            case "invalid", "structure", "required", "value", "invariant", "business-rule" -> 
                HttpStatus.UNPROCESSABLE_ENTITY;
            
            // 403 Forbidden
            case "forbidden", "security" -> HttpStatus.FORBIDDEN;
            
            // 409 Conflict
            case "conflict", "duplicate" -> HttpStatus.CONFLICT;
            
            // 429 Too Many Requests
            case "too-costly" -> HttpStatus.TOO_MANY_REQUESTS;
            
            // 500 Internal Server Error (default)
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
