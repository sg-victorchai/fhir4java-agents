package org.fhirframework.plugin.business;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome;

import java.util.Map;
import java.util.Optional;

/**
 * Result of a core FHIR operation.
 * <p>
 * Passed to business logic plugins in the AFTER phase so they can
 * inspect or modify the result before it's returned to the client.
 * </p>
 */
public class OperationResult {

    private final boolean success;
    private IBaseResource resource;
    private final OperationOutcome operationOutcome;
    private final int httpStatus;
    private final Map<String, String> responseHeaders;

    private OperationResult(boolean success, IBaseResource resource, OperationOutcome operationOutcome,
                            int httpStatus, Map<String, String> responseHeaders) {
        this.success = success;
        this.resource = resource;
        this.operationOutcome = operationOutcome;
        this.httpStatus = httpStatus;
        this.responseHeaders = responseHeaders != null ? responseHeaders : Map.of();
    }

    /**
     * Create a successful result with a resource.
     */
    public static OperationResult success(IBaseResource resource, int httpStatus) {
        return new OperationResult(true, resource, null, httpStatus, null);
    }

    /**
     * Create a successful result with a resource and headers.
     */
    public static OperationResult success(IBaseResource resource, int httpStatus, Map<String, String> headers) {
        return new OperationResult(true, resource, null, httpStatus, headers);
    }

    /**
     * Create a successful result for operations that don't return a resource (e.g., DELETE).
     */
    public static OperationResult successNoContent() {
        return new OperationResult(true, null, null, 204, null);
    }

    /**
     * Create a failure result.
     */
    public static OperationResult failure(OperationOutcome outcome, int httpStatus) {
        return new OperationResult(false, null, outcome, httpStatus, null);
    }

    /**
     * Create a failure result from an exception.
     */
    public static OperationResult failure(String message, int httpStatus) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.EXCEPTION);
        issue.setDiagnostics(message);
        return new OperationResult(false, null, outcome, httpStatus, null);
    }

    // Getters

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public Optional<IBaseResource> getResource() {
        return Optional.ofNullable(resource);
    }

    public Optional<OperationOutcome> getOperationOutcome() {
        return Optional.ofNullable(operationOutcome);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * Get a specific response header.
     */
    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(responseHeaders.get(name));
    }

    // Mutation methods for AFTER plugins

    /**
     * Replace the resource in the result.
     */
    public void setResource(IBaseResource resource) {
        this.resource = resource;
    }

    /**
     * Create a new result with a modified resource.
     */
    public OperationResult withResource(IBaseResource resource) {
        return new OperationResult(success, resource, operationOutcome, httpStatus, responseHeaders);
    }

    /**
     * Create a new result with additional headers.
     */
    public OperationResult withHeaders(Map<String, String> additionalHeaders) {
        Map<String, String> merged = new java.util.HashMap<>(responseHeaders);
        merged.putAll(additionalHeaders);
        return new OperationResult(success, resource, operationOutcome, httpStatus, merged);
    }

    @Override
    public String toString() {
        return "OperationResult{" +
                "success=" + success +
                ", httpStatus=" + httpStatus +
                ", hasResource=" + (resource != null) +
                ", hasOutcome=" + (operationOutcome != null) +
                '}';
    }
}
