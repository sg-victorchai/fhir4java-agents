package org.fhirframework.api.exception;

import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating FHIR OperationOutcome resources.
 */
public class OperationOutcomeBuilder {

    private static final String ISSUE_TYPE_SYSTEM = "http://hl7.org/fhir/issue-type";

    private final List<OperationOutcomeIssueComponent> issues = new ArrayList<>();

    public OperationOutcomeBuilder() {
    }

    /**
     * Adds an error issue.
     */
    public OperationOutcomeBuilder error(IssueType type, String message) {
        return addIssue(IssueSeverity.ERROR, type, message, null);
    }

    /**
     * Adds an error issue with diagnostics.
     */
    public OperationOutcomeBuilder error(IssueType type, String message, String diagnostics) {
        return addIssue(IssueSeverity.ERROR, type, message, diagnostics);
    }

    /**
     * Adds a warning issue.
     */
    public OperationOutcomeBuilder warning(IssueType type, String message) {
        return addIssue(IssueSeverity.WARNING, type, message, null);
    }

    /**
     * Adds an information issue.
     */
    public OperationOutcomeBuilder information(IssueType type, String message) {
        return addIssue(IssueSeverity.INFORMATION, type, message, null);
    }

    /**
     * Adds a custom issue.
     */
    public OperationOutcomeBuilder addIssue(IssueSeverity severity, IssueType type,
                                            String message, String diagnostics) {
        OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
        issue.setSeverity(severity);
        issue.setCode(type);

        CodeableConcept details = new CodeableConcept();
        details.setText(message);
        details.addCoding(new Coding()
                .setSystem(ISSUE_TYPE_SYSTEM)
                .setCode(type.toCode())
                .setDisplay(type.getDisplay()));
        issue.setDetails(details);

        if (diagnostics != null && !diagnostics.isBlank()) {
            issue.setDiagnostics(diagnostics);
        }

        issues.add(issue);
        return this;
    }

    /**
     * Adds an issue with a specific location expression.
     */
    public OperationOutcomeBuilder addIssueWithLocation(IssueSeverity severity, IssueType type,
                                                        String message, String location) {
        OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
        issue.setSeverity(severity);
        issue.setCode(type);

        CodeableConcept details = new CodeableConcept();
        details.setText(message);
        issue.setDetails(details);

        if (location != null) {
            issue.addExpression(location);
        }

        issues.add(issue);
        return this;
    }

    /**
     * Builds the OperationOutcome resource.
     */
    public OperationOutcome build() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setIssue(issues);
        return outcome;
    }

    // ========== Static Factory Methods ==========

    /**
     * Creates an OperationOutcome for a not found error.
     */
    public static OperationOutcome notFound(String resourceType, String resourceId) {
        return new OperationOutcomeBuilder()
                .error(IssueType.NOTFOUND,
                        String.format("Resource %s/%s not found", resourceType, resourceId),
                        "The requested resource does not exist on this server")
                .build();
    }

    /**
     * Creates an OperationOutcome for an unsupported resource type.
     */
    public static OperationOutcome unsupportedResourceType(String resourceType) {
        return new OperationOutcomeBuilder()
                .error(IssueType.NOTSUPPORTED,
                        String.format("Resource type '%s' is not supported", resourceType),
                        "This resource type is not configured on this server")
                .build();
    }

    /**
     * Creates an OperationOutcome for an unsupported FHIR version.
     */
    public static OperationOutcome unsupportedVersion(String resourceType, String version) {
        return new OperationOutcomeBuilder()
                .error(IssueType.NOTSUPPORTED,
                        String.format("FHIR version '%s' is not supported for resource type '%s'",
                                version, resourceType),
                        "Try using a different FHIR version in the request path")
                .build();
    }

    /**
     * Creates an OperationOutcome for a disabled interaction.
     */
    public static OperationOutcome interactionDisabled(String resourceType, String interaction) {
        return new OperationOutcomeBuilder()
                .error(IssueType.NOTSUPPORTED,
                        String.format("Interaction '%s' is not supported for resource type '%s'",
                                interaction, resourceType),
                        "This interaction is disabled in the server configuration")
                .build();
    }

    /**
     * Creates an OperationOutcome for validation errors.
     */
    public static OperationOutcome validationError(String message, String diagnostics) {
        return new OperationOutcomeBuilder()
                .error(IssueType.INVALID, message, diagnostics)
                .build();
    }

    /**
     * Creates an OperationOutcome for an invalid search parameter.
     */
    public static OperationOutcome invalidSearchParameter(String paramName, String resourceType) {
        return new OperationOutcomeBuilder()
                .error(IssueType.INVALID,
                        String.format("Search parameter '%s' is not valid for resource type '%s'",
                                paramName, resourceType),
                        "Check the CapabilityStatement for supported search parameters")
                .build();
    }

    /**
     * Creates an OperationOutcome for a denied search parameter.
     */
    public static OperationOutcome deniedSearchParameter(String paramName, String resourceType) {
        return new OperationOutcomeBuilder()
                .error(IssueType.FORBIDDEN,
                        String.format("Search parameter '%s' is not allowed for resource type '%s'",
                                paramName, resourceType),
                        "This search parameter has been restricted in the server configuration")
                .build();
    }

    /**
     * Creates an OperationOutcome for an internal server error.
     */
    public static OperationOutcome internalError(String message, String diagnostics) {
        return new OperationOutcomeBuilder()
                .error(IssueType.EXCEPTION, message, diagnostics)
                .build();
    }

    /**
     * Creates an OperationOutcome for a bad request.
     */
    public static OperationOutcome badRequest(String message) {
        return new OperationOutcomeBuilder()
                .error(IssueType.INVALID, message, null)
                .build();
    }
}
