package org.fhirframework.plugin.auth;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;

import java.security.Principal;
import java.util.Optional;

/**
 * Plugin interface for authentication.
 * <p>
 * Authentication plugins verify the identity of the caller and
 * populate the plugin context with user/client information.
 * They execute synchronously in the BEFORE phase.
 * </p>
 */
public interface AuthenticationPlugin extends FhirPlugin {

    /**
     * Attribute key for storing the authenticated principal.
     */
    String PRINCIPAL_ATTRIBUTE = "auth.principal";

    /**
     * Attribute key for storing authentication claims.
     */
    String CLAIMS_ATTRIBUTE = "auth.claims";

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNC;
    }

    @Override
    default int getPriority() {
        // Authentication runs early
        return 10;
    }

    /**
     * Authenticate the request.
     *
     * @param context The plugin context
     * @return Authentication result with principal if successful
     */
    AuthenticationResult authenticate(PluginContext context);

    @Override
    default PluginResult executeBefore(PluginContext context) {
        AuthenticationResult result = authenticate(context);

        if (!result.isAuthenticated()) {
            return PluginResult.unauthorized(result.getErrorMessage().orElse("Authentication required"));
        }

        // Store principal in context
        result.getPrincipal().ifPresent(p -> context.setAttribute(PRINCIPAL_ATTRIBUTE, p));
        result.getClaims().ifPresent(c -> context.setAttribute(CLAIMS_ATTRIBUTE, c));

        // Set user/client IDs if available
        result.getUserId().ifPresent(context::setUserId);
        result.getClientId().ifPresent(context::setClientId);

        return PluginResult.continueProcessing();
    }

    /**
     * Result of an authentication attempt.
     */
    record AuthenticationResult(
            boolean authenticated,
            Principal principal,
            Object claims,
            String userId,
            String clientId,
            String errorMessage
    ) {
        public static AuthenticationResult success(Principal principal, String userId, String clientId) {
            return new AuthenticationResult(true, principal, null, userId, clientId, null);
        }

        public static AuthenticationResult success(Principal principal, Object claims, String userId, String clientId) {
            return new AuthenticationResult(true, principal, claims, userId, clientId, null);
        }

        public static AuthenticationResult failure(String errorMessage) {
            return new AuthenticationResult(false, null, null, null, null, errorMessage);
        }

        public static AuthenticationResult anonymous() {
            return new AuthenticationResult(true, null, null, null, null, null);
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public Optional<Principal> getPrincipal() {
            return Optional.ofNullable(principal);
        }

        public Optional<Object> getClaims() {
            return Optional.ofNullable(claims);
        }

        public Optional<String> getUserId() {
            return Optional.ofNullable(userId);
        }

        public Optional<String> getClientId() {
            return Optional.ofNullable(clientId);
        }

        public Optional<String> getErrorMessage() {
            return Optional.ofNullable(errorMessage);
        }
    }
}
