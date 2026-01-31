package org.fhirframework.plugin.impl;

import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.auth.AuthorizationPlugin;

/**
 * A no-operation authorization plugin that permits all requests.
 * <p>
 * Use this for development, testing, or internal-only deployments
 * where authorization is not required.
 * </p>
 */
public class NoOpAuthorizationPlugin implements AuthorizationPlugin {

    private boolean enabled = true;

    @Override
    public String getName() {
        return "noop-authorization";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public AuthorizationDecision authorize(PluginContext context) {
        // Permit all requests
        return AuthorizationDecision.permit();
    }
}
