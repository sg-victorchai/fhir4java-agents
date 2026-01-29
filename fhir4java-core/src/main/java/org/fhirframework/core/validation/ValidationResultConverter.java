package org.fhirframework.core.validation;

import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.springframework.stereotype.Component;

/**
 * Converts ValidationResult to FHIR OperationOutcome resources.
 * <p>
 * This converter creates properly formatted OperationOutcome resources
 * that can be returned as API responses for validation errors.
 * </p>
 */
@Component
public class ValidationResultConverter {

    /**
     * Convert a ValidationResult to an OperationOutcome.
     *
     * @param result The validation result to convert
     * @return OperationOutcome representation
     */
    public OperationOutcome toOperationOutcome(ValidationResult result) {
        OperationOutcome outcome = new OperationOutcome();

        if (result == null || result.isEmpty()) {
            // No issues - return success
            addSuccessIssue(outcome);
            return outcome;
        }

        for (ValidationIssue issue : result.getIssues()) {
            addIssue(outcome, issue);
        }

        // If valid but has warnings/info, add success indicator
        if (result.isValid() && !result.isEmpty()) {
            // Keep existing issues (warnings/info), they already indicate non-error status
        }

        return outcome;
    }

    /**
     * Convert a ValidationResult to an OperationOutcome, with custom success message.
     *
     * @param result         The validation result to convert
     * @param successMessage Message to include if validation is successful
     * @return OperationOutcome representation
     */
    public OperationOutcome toOperationOutcome(ValidationResult result, String successMessage) {
        if (result != null && result.isValid()) {
            OperationOutcome outcome = new OperationOutcome();

            // Add any non-error issues (warnings, info)
            for (ValidationIssue issue : result.getIssues()) {
                if (!issue.isError()) {
                    addIssue(outcome, issue);
                }
            }

            // Add success message
            OperationOutcomeIssueComponent successIssue = outcome.addIssue();
            successIssue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
            successIssue.setCode(OperationOutcome.IssueType.INFORMATIONAL);
            successIssue.setDiagnostics(successMessage);

            return outcome;
        }

        return toOperationOutcome(result);
    }

    /**
     * Create an OperationOutcome for a single error message.
     *
     * @param errorMessage The error message
     * @return OperationOutcome with the error
     */
    public OperationOutcome createErrorOutcome(String errorMessage) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        issue.setDiagnostics(errorMessage);
        return outcome;
    }

    /**
     * Create an OperationOutcome for a "not found" error.
     *
     * @param resourceType The resource type
     * @param resourceId   The resource ID that was not found
     * @return OperationOutcome with the not found error
     */
    public OperationOutcome createNotFoundOutcome(String resourceType, String resourceId) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.NOTFOUND);
        issue.setDiagnostics(String.format("Resource %s/%s not found", resourceType, resourceId));
        return outcome;
    }

    /**
     * Create an OperationOutcome for an "invalid parameter" error.
     *
     * @param parameterName The invalid parameter name
     * @param message       Additional message
     * @return OperationOutcome with the invalid parameter error
     */
    public OperationOutcome createInvalidParameterOutcome(String parameterName, String message) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        issue.setDiagnostics(message);
        issue.addLocation("parameter:" + parameterName);
        return outcome;
    }

    /**
     * Create a success OperationOutcome.
     *
     * @param message Success message
     * @return OperationOutcome indicating success
     */
    public OperationOutcome createSuccessOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        addSuccessIssue(outcome, message);
        return outcome;
    }

    /**
     * Add a ValidationIssue to an OperationOutcome.
     */
    private void addIssue(OperationOutcome outcome, ValidationIssue issue) {
        OperationOutcomeIssueComponent component = outcome.addIssue();

        // Set severity
        component.setSeverity(issue.severity().toFhirSeverity());

        // Set issue type
        component.setCode(issue.issueType());

        // Set message/diagnostics
        if (issue.message() != null) {
            component.setDiagnostics(issue.message());
        }

        // Set location
        if (issue.location() != null) {
            component.addLocation(issue.location());
        }

        // Set additional diagnostics
        if (issue.diagnostics() != null && !issue.diagnostics().equals(issue.message())) {
            // If diagnostics is different from message, append it
            String existing = component.getDiagnostics();
            if (existing != null && !existing.isBlank()) {
                component.setDiagnostics(existing + " - " + issue.diagnostics());
            } else {
                component.setDiagnostics(issue.diagnostics());
            }
        }
    }

    /**
     * Add a success issue to an OperationOutcome.
     */
    private void addSuccessIssue(OperationOutcome outcome) {
        addSuccessIssue(outcome, "Validation successful");
    }

    /**
     * Add a success issue with custom message to an OperationOutcome.
     */
    private void addSuccessIssue(OperationOutcome outcome, String message) {
        OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
        issue.setCode(OperationOutcome.IssueType.INFORMATIONAL);
        issue.setDiagnostics(message);
    }

    /**
     * Check if an OperationOutcome indicates success (no errors).
     *
     * @param outcome The OperationOutcome to check
     * @return true if no error-level issues
     */
    public boolean isSuccessful(OperationOutcome outcome) {
        if (outcome == null || outcome.getIssue().isEmpty()) {
            return true;
        }

        return outcome.getIssue().stream()
                .noneMatch(issue ->
                        issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR ||
                        issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL);
    }

    /**
     * Count errors in an OperationOutcome.
     *
     * @param outcome The OperationOutcome to check
     * @return Number of error-level issues
     */
    public int countErrors(OperationOutcome outcome) {
        if (outcome == null || outcome.getIssue().isEmpty()) {
            return 0;
        }

        return (int) outcome.getIssue().stream()
                .filter(issue ->
                        issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR ||
                        issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
                .count();
    }
}
