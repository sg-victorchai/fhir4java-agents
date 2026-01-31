package org.fhirframework.plugin.impl;

import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.auth.AuthenticationPlugin;

/**
 * A no-operation authentication plugin that allows all requests.
 * <p>
 * Use this for development, testing, or internal-only deployments
 * where authentication is not required.
 * </p>
 */
public class NoOpAuthenticationPlugin implements AuthenticationPlugin {

    private boolean enabled = true;

    @Override
    public String getName() {
        return "noop-authentication";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public AuthenticationResult authenticate(PluginContext context) {
        // Allow anonymous access
        return AuthenticationResult.anonymous();
    }
}
