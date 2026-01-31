package org.fhirframework.plugin.auth;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Optional;
import java.util.Set;

/**
 * Plugin interface for authorization.
 * <p>
 * Authorization plugins determine whether the authenticated caller
 * is permitted to perform the requested operation. They execute
 * synchronously in the BEFORE phase, after authentication.
 * </p>
 */
public interface AuthorizationPlugin extends FhirPlugin {

    /**
     * Attribute key for storing authorization decision.
     */
    String DECISION_ATTRIBUTE = "authz.decision";

    /**
     * Attribute key for storing granted scopes.
     */
    String SCOPES_ATTRIBUTE = "authz.scopes";

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNC;
    }

    @Override
    default int getPriority() {
        // Authorization runs after authentication
        return 20;
    }

    /**
     * Authorize the request.
     *
     * @param context The plugin context with authentication info
     * @return Authorization decision
     */
    AuthorizationDecision authorize(PluginContext context);

    @Override
    default PluginResult executeBefore(PluginContext context) {
        AuthorizationDecision decision = authorize(context);

        // Store decision in context
        context.setAttribute(DECISION_ATTRIBUTE, decision);
        decision.getGrantedScopes().ifPresent(s -> context.setAttribute(SCOPES_ATTRIBUTE, s));

        if (!decision.isPermitted()) {
            return PluginResult.forbidden(decision.getReason().orElse("Access denied"));
        }

        return PluginResult.continueProcessing();
    }

    /**
     * Filter resources based on authorization.
     * <p>
     * Called during search operations to filter results based on
     * the caller's permissions.
     * </p>
     *
     * @param context  The plugin context
     * @param resource The resource to check
     * @return true if the caller can see this resource
     */
    default boolean canAccess(PluginContext context, IBaseResource resource) {
        return true;
    }

    /**
     * Result of an authorization decision.
     */
    record AuthorizationDecision(
            boolean permitted,
            String reason,
            Set<String> grantedScopes,
            Set<String> requiredScopes
    ) {
        public static AuthorizationDecision permit() {
            return new AuthorizationDecision(true, null, null, null);
        }

        public static AuthorizationDecision permit(Set<String> grantedScopes) {
            return new AuthorizationDecision(true, null, grantedScopes, null);
        }

        public static AuthorizationDecision deny(String reason) {
            return new AuthorizationDecision(false, reason, null, null);
        }

        public static AuthorizationDecision deny(String reason, Set<String> requiredScopes) {
            return new AuthorizationDecision(false, reason, null, requiredScopes);
        }

        public boolean isPermitted() {
            return permitted;
        }

        public Optional<String> getReason() {
            return Optional.ofNullable(reason);
        }

        public Optional<Set<String>> getGrantedScopes() {
            return Optional.ofNullable(grantedScopes);
        }

        public Optional<Set<String>> getRequiredScopes() {
            return Optional.ofNullable(requiredScopes);
        }
    }
}
