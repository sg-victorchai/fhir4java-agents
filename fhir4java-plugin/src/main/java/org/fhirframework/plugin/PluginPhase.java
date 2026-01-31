package org.fhirframework.plugin;

/**
 * Phase of plugin execution in the request lifecycle.
 */
public enum PluginPhase {
    /**
     * Execute before the core operation.
     * Plugins can validate, transform, or abort the request.
     */
    BEFORE,

    /**
     * Execute after the core operation completes successfully.
     * Plugins can enrich, transform, or trigger side effects.
     */
    AFTER,

    /**
     * Execute when an error occurs during the operation.
     * Plugins can log errors, transform error responses, or trigger alerts.
     */
    ON_ERROR
}
