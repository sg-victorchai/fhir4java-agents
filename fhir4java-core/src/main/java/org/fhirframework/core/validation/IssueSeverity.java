package org.fhirframework.core.validation;

/**
 * Severity levels for validation issues.
 * Maps to FHIR OperationOutcome issue severity.
 */
public enum IssueSeverity {
    /**
     * The issue is serious enough to prevent the action from being performed.
     */
    ERROR,

    /**
     * The issue may cause problems but does not prevent the action.
     */
    WARNING,

    /**
     * Informational message that does not indicate a problem.
     */
    INFORMATION;

    /**
     * Convert to FHIR R5 IssueSeverity.
     */
    public org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity toFhirSeverity() {
        return switch (this) {
            case ERROR -> org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.ERROR;
            case WARNING -> org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.WARNING;
            case INFORMATION -> org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity.INFORMATION;
        };
    }
}
