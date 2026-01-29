package org.fhirframework.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a validation operation, containing a list of issues found.
 * <p>
 * A validation is considered successful if there are no ERROR-level issues.
 * Warning and informational issues do not cause validation failure.
 * </p>
 */
public class ValidationResult {

    private final List<ValidationIssue> issues;

    public ValidationResult() {
        this.issues = new ArrayList<>();
    }

    public ValidationResult(List<ValidationIssue> issues) {
        this.issues = new ArrayList<>(issues);
    }

    /**
     * Create a successful validation result with no issues.
     */
    public static ValidationResult success() {
        return new ValidationResult();
    }

    /**
     * Create a successful validation result with an informational message.
     */
    public static ValidationResult success(String message) {
        ValidationResult result = new ValidationResult();
        result.addIssue(ValidationIssue.information(message));
        return result;
    }

    /**
     * Create a failed validation result with a single error.
     */
    public static ValidationResult failure(String errorMessage) {
        ValidationResult result = new ValidationResult();
        result.addIssue(ValidationIssue.error(errorMessage));
        return result;
    }

    /**
     * Create a validation result with the given issues.
     */
    public static ValidationResult of(List<ValidationIssue> issues) {
        return new ValidationResult(issues);
    }

    /**
     * Add an issue to this result.
     */
    public ValidationResult addIssue(ValidationIssue issue) {
        this.issues.add(issue);
        return this;
    }

    /**
     * Add multiple issues to this result.
     */
    public ValidationResult addIssues(List<ValidationIssue> issues) {
        this.issues.addAll(issues);
        return this;
    }

    /**
     * Merge another validation result into this one.
     */
    public ValidationResult merge(ValidationResult other) {
        if (other != null && other.issues != null) {
            this.issues.addAll(other.issues);
        }
        return this;
    }

    /**
     * Check if validation was successful (no ERROR-level issues).
     */
    public boolean isValid() {
        return issues.stream().noneMatch(ValidationIssue::isError);
    }

    /**
     * Check if validation failed (has ERROR-level issues).
     */
    public boolean hasErrors() {
        return issues.stream().anyMatch(ValidationIssue::isError);
    }

    /**
     * Check if validation has warnings.
     */
    public boolean hasWarnings() {
        return issues.stream().anyMatch(ValidationIssue::isWarning);
    }

    /**
     * Get all issues.
     */
    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    /**
     * Get only error-level issues.
     */
    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(ValidationIssue::isError)
                .toList();
    }

    /**
     * Get only warning-level issues.
     */
    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(ValidationIssue::isWarning)
                .toList();
    }

    /**
     * Get the number of issues.
     */
    public int getIssueCount() {
        return issues.size();
    }

    /**
     * Get the number of errors.
     */
    public int getErrorCount() {
        return (int) issues.stream().filter(ValidationIssue::isError).count();
    }

    /**
     * Check if there are no issues at all.
     */
    public boolean isEmpty() {
        return issues.isEmpty();
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "ValidationResult{valid=true, issues=[]}";
        }
        return String.format("ValidationResult{valid=%s, errors=%d, warnings=%d, total=%d}",
                isValid(), getErrorCount(), getWarnings().size(), getIssueCount());
    }
}
