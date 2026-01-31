package org.fhirframework.plugin;

/**
 * Execution mode for plugins.
 * <p>
 * Determines whether a plugin executes synchronously (blocking)
 * or asynchronously (non-blocking).
 * </p>
 */
public enum ExecutionMode {
    /**
     * Plugin executes synchronously and blocks the request pipeline.
     * Use for plugins that must complete before the operation continues
     * (e.g., authentication, authorization, validation).
     */
    SYNC,

    /**
     * Plugin executes asynchronously without blocking the request pipeline.
     * Use for plugins that can complete independently
     * (e.g., audit logging, telemetry, notifications).
     */
    ASYNC
}
