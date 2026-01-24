package com.fhir4java.core.exception;

/**
 * Base exception for all FHIR-related errors.
 */
public class FhirException extends RuntimeException {

    private final String issueCode;
    private final String diagnostics;

    public FhirException(String message) {
        this(message, "processing", null);
    }

    public FhirException(String message, String issueCode) {
        this(message, issueCode, null);
    }

    public FhirException(String message, String issueCode, String diagnostics) {
        super(message);
        this.issueCode = issueCode;
        this.diagnostics = diagnostics;
    }

    public FhirException(String message, Throwable cause) {
        super(message, cause);
        this.issueCode = "exception";
        this.diagnostics = cause != null ? cause.getMessage() : null;
    }

    /**
     * Returns the FHIR issue type code for OperationOutcome.
     */
    public String getIssueCode() {
        return issueCode;
    }

    /**
     * Returns additional diagnostic information.
     */
    public String getDiagnostics() {
        return diagnostics;
    }
}
