package org.fhirframework.plugin;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome;

import java.util.Optional;

/**
 * Result of a plugin execution.
 * <p>
 * Plugins return this result to indicate success, continuation with
 * modifications, or abort with an error response.
 * </p>
 */
public class PluginResult {

    /**
     * Plugin execution outcome.
     */
    public enum Outcome {
        /**
         * Continue processing with no changes.
         */
        CONTINUE,

        /**
         * Continue processing with modified resource or parameters.
         */
        MODIFIED,

        /**
         * Abort processing and return an error response.
         */
        ABORT,

        /**
         * Skip remaining plugins of the same type.
         */
        SKIP_REMAINING
    }

    private final Outcome outcome;
    private final IBaseResource modifiedResource;
    private final OperationOutcome operationOutcome;
    private final int httpStatus;
    private final String message;

    private PluginResult(Outcome outcome, IBaseResource modifiedResource,
                         OperationOutcome operationOutcome, int httpStatus, String message) {
        this.outcome = outcome;
        this.modifiedResource = modifiedResource;
        this.operationOutcome = operationOutcome;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * Create a result indicating the operation should continue normally.
     */
    public static PluginResult continueProcessing() {
        return new PluginResult(Outcome.CONTINUE, null, null, 0, null);
    }

    /**
     * Create a result indicating the operation should continue with a modified resource.
     */
    public static PluginResult continueWithResource(IBaseResource modifiedResource) {
        return new PluginResult(Outcome.MODIFIED, modifiedResource, null, 0, null);
    }

    /**
     * Create a result indicating the operation should be aborted.
     */
    public static PluginResult abort(int httpStatus, OperationOutcome outcome) {
        return new PluginResult(Outcome.ABORT, null, outcome, httpStatus, null);
    }

    /**
     * Create a result indicating the operation should be aborted with a message.
     */
    public static PluginResult abort(int httpStatus, String message) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.FORBIDDEN);
        issue.setDiagnostics(message);
        return new PluginResult(Outcome.ABORT, null, outcome, httpStatus, message);
    }

    /**
     * Create a result indicating authentication failure.
     */
    public static PluginResult unauthorized(String message) {
        return abort(401, message);
    }

    /**
     * Create a result indicating authorization failure.
     */
    public static PluginResult forbidden(String message) {
        return abort(403, message);
    }

    /**
     * Create a result indicating a bad request.
     */
    public static PluginResult badRequest(String message) {
        OperationOutcome outcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();
        issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issue.setCode(OperationOutcome.IssueType.INVALID);
        issue.setDiagnostics(message);
        return new PluginResult(Outcome.ABORT, null, outcome, 400, message);
    }

    /**
     * Create a result indicating remaining plugins should be skipped.
     */
    public static PluginResult skipRemaining() {
        return new PluginResult(Outcome.SKIP_REMAINING, null, null, 0, null);
    }

    /**
     * Create a result indicating remaining plugins should be skipped with a modified resource.
     */
    public static PluginResult skipRemainingWithResource(IBaseResource modifiedResource) {
        return new PluginResult(Outcome.SKIP_REMAINING, modifiedResource, null, 0, null);
    }

    // Getters

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean shouldContinue() {
        return outcome == Outcome.CONTINUE || outcome == Outcome.MODIFIED;
    }

    public boolean isAborted() {
        return outcome == Outcome.ABORT;
    }

    public boolean isModified() {
        return outcome == Outcome.MODIFIED || outcome == Outcome.SKIP_REMAINING;
    }

    public boolean shouldSkipRemaining() {
        return outcome == Outcome.SKIP_REMAINING;
    }

    public Optional<IBaseResource> getModifiedResource() {
        return Optional.ofNullable(modifiedResource);
    }

    public Optional<OperationOutcome> getOperationOutcome() {
        return Optional.ofNullable(operationOutcome);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PluginResult{");
        sb.append("outcome=").append(outcome);
        if (httpStatus > 0) {
            sb.append(", httpStatus=").append(httpStatus);
        }
        if (message != null) {
            sb.append(", message='").append(message).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
