package org.fhirframework.plugin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the plugin system.
 */
@Component
public class PluginConfig {

    @Value("${fhir4java.plugins.enabled:true}")
    private boolean pluginsEnabled;

    @Value("${fhir4java.plugins.authentication.enabled:false}")
    private boolean authenticationEnabled;

    @Value("${fhir4java.plugins.authorization.enabled:false}")
    private boolean authorizationEnabled;

    @Value("${fhir4java.plugins.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${fhir4java.plugins.telemetry.enabled:true}")
    private boolean telemetryEnabled;

    @Value("${fhir4java.plugins.performance.enabled:true}")
    private boolean performanceEnabled;

    @Value("${fhir4java.plugins.cache.enabled:false}")
    private boolean cacheEnabled;

    @Value("${fhir4java.plugins.async-pool-size:4}")
    private int asyncPoolSize;

    @Value("${fhir4java.plugins.business-logic.fail-on-error:true}")
    private boolean businessLogicFailOnError;

    // Getters

    public boolean isPluginsEnabled() {
        return pluginsEnabled;
    }

    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    public boolean isAuthorizationEnabled() {
        return authorizationEnabled;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public boolean isTelemetryEnabled() {
        return telemetryEnabled;
    }

    public boolean isPerformanceEnabled() {
        return performanceEnabled;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    public boolean isBusinessLogicFailOnError() {
        return businessLogicFailOnError;
    }
}
