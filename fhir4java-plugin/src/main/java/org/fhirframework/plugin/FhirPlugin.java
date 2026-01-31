package org.fhirframework.plugin;

import java.util.List;

/**
 * Base interface for all FHIR plugins.
 * <p>
 * Plugins can intercept and modify FHIR operations at various phases
 * in the request lifecycle. They declare their supported operations
 * using OperationDescriptors and execute in the specified mode (sync/async).
 * </p>
 *
 * <h2>Plugin Lifecycle</h2>
 * <ol>
 *   <li>Plugin registered with PluginOrchestrator</li>
 *   <li>For each request, matching plugins are identified via descriptors</li>
 *   <li>BEFORE phase: plugins execute in priority order</li>
 *   <li>Core operation executes</li>
 *   <li>AFTER phase: plugins execute in priority order</li>
 *   <li>ON_ERROR phase: plugins execute if an error occurred</li>
 * </ol>
 *
 * <h2>Execution Modes</h2>
 * <ul>
 *   <li>SYNC: Plugin blocks the request pipeline until complete</li>
 *   <li>ASYNC: Plugin executes asynchronously without blocking</li>
 * </ul>
 */
public interface FhirPlugin {

    /**
     * Get the unique name of this plugin.
     */
    String getName();

    /**
     * Get the execution mode for this plugin.
     * <p>
     * SYNC plugins block the request pipeline and can modify the request/response.
     * ASYNC plugins execute asynchronously and cannot modify the pipeline.
     * </p>
     */
    ExecutionMode getExecutionMode();

    /**
     * Get the priority for this plugin (lower number = higher priority).
     * <p>
     * Plugins with lower priority numbers execute first within their phase.
     * Default priority is 100.
     * </p>
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Get the operation descriptors this plugin handles.
     * <p>
     * Return an empty list to match all operations.
     * Return specific descriptors to match only certain operations.
     * </p>
     */
    default List<OperationDescriptor> getSupportedOperations() {
        return List.of(OperationDescriptor.matchAll());
    }

    /**
     * Check if this plugin is enabled.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Check if this plugin supports the given operation.
     */
    default boolean supports(PluginContext context) {
        if (!isEnabled()) {
            return false;
        }
        OperationDescriptor contextDescriptor = context.toDescriptor();
        return getSupportedOperations().stream()
                .anyMatch(descriptor -> descriptor.matches(contextDescriptor));
    }

    /**
     * Execute the plugin before the core operation.
     * <p>
     * Can validate, transform, or abort the request.
     * Only called for SYNC plugins during the BEFORE phase.
     * </p>
     *
     * @param context The plugin context with request information
     * @return Result indicating whether to continue, modify, or abort
     */
    default PluginResult executeBefore(PluginContext context) {
        return PluginResult.continueProcessing();
    }

    /**
     * Execute the plugin after the core operation completes successfully.
     * <p>
     * Can enrich, transform, or trigger side effects.
     * Called for both SYNC and ASYNC plugins during the AFTER phase.
     * </p>
     *
     * @param context The plugin context with request and response information
     * @return Result indicating outcome (ignored for ASYNC plugins)
     */
    default PluginResult executeAfter(PluginContext context) {
        return PluginResult.continueProcessing();
    }

    /**
     * Execute the plugin when an error occurs.
     * <p>
     * Can log errors, transform error responses, or trigger alerts.
     * Called for both SYNC and ASYNC plugins during the ON_ERROR phase.
     * </p>
     *
     * @param context   The plugin context
     * @param exception The exception that occurred
     * @return Result indicating how to handle the error
     */
    default PluginResult executeOnError(PluginContext context, Exception exception) {
        return PluginResult.continueProcessing();
    }

    /**
     * Initialize the plugin.
     * <p>
     * Called once when the plugin is registered with the orchestrator.
     * </p>
     */
    default void initialize() {
        // Default: no initialization needed
    }

    /**
     * Destroy the plugin.
     * <p>
     * Called when the plugin is unregistered or the application shuts down.
     * </p>
     */
    default void destroy() {
        // Default: no cleanup needed
    }
}
