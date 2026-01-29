package org.fhirframework.core.validation;

import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

/**
 * Represents a single validation issue found during resource or parameter validation.
 */
public record ValidationIssue(
        IssueSeverity severity,
        IssueType issueType,
        String message,
        String location,
        String diagnostics
) {
    /**
     * Create an error issue with a message.
     */
    public static ValidationIssue error(String message) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.INVALID, message, null, null);
    }

    /**
     * Create an error issue with a message and location.
     */
    public static ValidationIssue error(String message, String location) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.INVALID, message, location, null);
    }

    /**
     * Create an error issue with full details.
     */
    public static ValidationIssue error(IssueType issueType, String message, String location, String diagnostics) {
        return new ValidationIssue(IssueSeverity.ERROR, issueType, message, location, diagnostics);
    }

    /**
     * Create a warning issue with a message.
     */
    public static ValidationIssue warning(String message) {
        return new ValidationIssue(IssueSeverity.WARNING, IssueType.INVALID, message, null, null);
    }

    /**
     * Create a warning issue with a message and location.
     */
    public static ValidationIssue warning(String message, String location) {
        return new ValidationIssue(IssueSeverity.WARNING, IssueType.INVALID, message, location, null);
    }

    /**
     * Create an informational issue.
     */
    public static ValidationIssue information(String message) {
        return new ValidationIssue(IssueSeverity.INFORMATION, IssueType.INFORMATIONAL, message, null, null);
    }

    /**
     * Create a "not found" error for unknown parameters or resources.
     */
    public static ValidationIssue notFound(String message) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.NOTFOUND, message, null, null);
    }

    /**
     * Create a "not supported" error for disallowed operations.
     */
    public static ValidationIssue notSupported(String message) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.NOTSUPPORTED, message, null, null);
    }

    /**
     * Create a "business rule" error.
     */
    public static ValidationIssue businessRule(String message) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.BUSINESSRULE, message, null, null);
    }

    /**
     * Create a "required" error for missing required elements.
     */
    public static ValidationIssue required(String message, String location) {
        return new ValidationIssue(IssueSeverity.ERROR, IssueType.REQUIRED, message, location, null);
    }

    /**
     * Check if this is an error-level issue.
     */
    public boolean isError() {
        return severity == IssueSeverity.ERROR;
    }

    /**
     * Check if this is a warning-level issue.
     */
    public boolean isWarning() {
        return severity == IssueSeverity.WARNING;
    }
}
