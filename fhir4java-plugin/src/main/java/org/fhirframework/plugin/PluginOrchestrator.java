package org.fhirframework.plugin;

import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.plugin.business.BusinessContext;
import org.fhirframework.plugin.business.BusinessLogicPlugin;
import org.fhirframework.plugin.business.OperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Orchestrates plugin execution for FHIR operations.
 * <p>
 * Manages the plugin execution pipeline, executing plugins in the correct
 * order based on their phase, priority, and execution mode.
 * </p>
 *
 * <h2>Execution Order</h2>
 * <ol>
 *   <li>BEFORE phase: SYNC plugins execute in priority order</li>
 *   <li>Core operation executes</li>
 *   <li>AFTER phase: SYNC plugins first, then ASYNC plugins</li>
 *   <li>ON_ERROR phase: All plugins if an error occurred</li>
 * </ol>
 */
@Component
public class PluginOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PluginOrchestrator.class);

    private final List<FhirPlugin> plugins = new CopyOnWriteArrayList<>();
    private final ExecutorService asyncExecutor;

    public PluginOrchestrator() {
        this.asyncExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "plugin-async");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    @PostConstruct
    public void initialize() {
        log.info("Plugin orchestrator initialized with {} plugins", plugins.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down plugin orchestrator");
        asyncExecutor.shutdown();
        plugins.forEach(FhirPlugin::destroy);
    }

    /**
     * Wraps a task with tenant context propagation for async execution.
     * Captures the current tenant ID from the calling thread and sets it
     * on the async thread before execution, clearing it afterwards to
     * prevent thread pool contamination.
     */
    private Runnable wrapWithTenantContext(Runnable task) {
        String tenantId = TenantContext.getCurrentTenantId();
        return () -> {
            TenantContext.setCurrentTenantId(tenantId);
            try {
                task.run();
            } finally {
                TenantContext.clear();
            }
        };
    }

    /**
     * Register a plugin with the orchestrator.
     */
    public void registerPlugin(FhirPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        plugin.initialize();
        plugins.add(plugin);
        log.info("Registered plugin: {} (mode={}, priority={})",
                plugin.getName(), plugin.getExecutionMode(), plugin.getPriority());
    }

    /**
     * Unregister a plugin from the orchestrator.
     */
    public void unregisterPlugin(FhirPlugin plugin) {
        if (plugins.remove(plugin)) {
            plugin.destroy();
            log.info("Unregistered plugin: {}", plugin.getName());
        }
    }

    /**
     * Get all registered plugins.
     */
    public List<FhirPlugin> getPlugins() {
        return new ArrayList<>(plugins);
    }

    /**
     * Get plugins that match the given context.
     */
    public List<FhirPlugin> getMatchingPlugins(PluginContext context) {
        return plugins.stream()
                .filter(p -> p.isEnabled() && p.supports(context))
                .sorted(Comparator.comparingInt(FhirPlugin::getPriority))
                .toList();
    }

    /**
     * Execute the BEFORE phase for all matching plugins.
     *
     * @param context The plugin context
     * @return Result indicating whether to continue
     */
    public PluginResult executeBefore(PluginContext context) {
        List<FhirPlugin> matchingPlugins = getMatchingPlugins(context);

        for (FhirPlugin plugin : matchingPlugins) {
            if (plugin.getExecutionMode() == ExecutionMode.SYNC) {
                try {
                    log.debug("Executing BEFORE: {} for {}", plugin.getName(), context);
                    PluginResult result = plugin.executeBefore(context);

                    if (result.isAborted()) {
                        log.info("Plugin {} aborted operation: {}", plugin.getName(), result.getMessage().orElse(""));
                        return result;
                    }

                    if (result.isModified()) {
                        result.getModifiedResource().ifPresent(context::setInputResource);
                    }

                    if (result.shouldSkipRemaining()) {
                        log.debug("Plugin {} requested skip remaining", plugin.getName());
                        return result;
                    }

                } catch (Exception e) {
                    log.error("Plugin {} threw exception in BEFORE phase", plugin.getName(), e);
                    return PluginResult.abort(500, "Plugin error: " + e.getMessage());
                }
            }
        }

        return PluginResult.continueProcessing();
    }

    /**
     * Execute the AFTER phase for all matching plugins.
     *
     * @param context The plugin context
     * @return Result indicating outcome
     */
    public PluginResult executeAfter(PluginContext context) {
        List<FhirPlugin> matchingPlugins = getMatchingPlugins(context);

        // Execute SYNC plugins first
        for (FhirPlugin plugin : matchingPlugins) {
            if (plugin.getExecutionMode() == ExecutionMode.SYNC) {
                try {
                    log.debug("Executing AFTER (sync): {} for {}", plugin.getName(), context);
                    PluginResult result = plugin.executeAfter(context);

                    if (result.isModified()) {
                        result.getModifiedResource().ifPresent(context::setOutputResource);
                    }

                    if (result.shouldSkipRemaining()) {
                        return result;
                    }

                } catch (Exception e) {
                    log.error("Plugin {} threw exception in AFTER phase", plugin.getName(), e);
                    // Don't fail the operation for after-phase errors
                }
            }
        }

        // Execute ASYNC plugins in parallel
        List<FhirPlugin> asyncPlugins = matchingPlugins.stream()
                .filter(p -> p.getExecutionMode() == ExecutionMode.ASYNC)
                .toList();

        if (!asyncPlugins.isEmpty()) {
            PluginContext contextCopy = context; // Capture for lambda
            asyncPlugins.forEach(plugin ->
                    CompletableFuture.runAsync(wrapWithTenantContext(() -> {
                        try {
                            log.debug("Executing AFTER (async): {} for {}", plugin.getName(), contextCopy);
                            plugin.executeAfter(contextCopy);
                        } catch (Exception e) {
                            log.error("Async plugin {} threw exception in AFTER phase", plugin.getName(), e);
                        }
                    }), asyncExecutor)
            );
        }

        return PluginResult.continueProcessing();
    }

    /**
     * Execute the ON_ERROR phase for all matching plugins.
     *
     * @param context   The plugin context
     * @param exception The exception that occurred
     */
    public void executeOnError(PluginContext context, Exception exception) {
        List<FhirPlugin> matchingPlugins = getMatchingPlugins(context);

        // Execute SYNC plugins first
        for (FhirPlugin plugin : matchingPlugins) {
            if (plugin.getExecutionMode() == ExecutionMode.SYNC) {
                try {
                    log.debug("Executing ON_ERROR (sync): {} for {}", plugin.getName(), context);
                    plugin.executeOnError(context, exception);
                } catch (Exception e) {
                    log.error("Plugin {} threw exception in ON_ERROR phase", plugin.getName(), e);
                }
            }
        }

        // Execute ASYNC plugins in parallel
        List<FhirPlugin> asyncPlugins = matchingPlugins.stream()
                .filter(p -> p.getExecutionMode() == ExecutionMode.ASYNC)
                .toList();

        if (!asyncPlugins.isEmpty()) {
            asyncPlugins.forEach(plugin ->
                    CompletableFuture.runAsync(wrapWithTenantContext(() -> {
                        try {
                            log.debug("Executing ON_ERROR (async): {} for {}", plugin.getName(), context);
                            plugin.executeOnError(context, exception);
                        } catch (Exception e) {
                            log.error("Async plugin {} threw exception in ON_ERROR phase", plugin.getName(), e);
                        }
                    }), asyncExecutor)
            );
        }
    }

    /**
     * Execute a complete CRUD operation with plugin pipeline.
     *
     * @param context       The plugin context
     * @param coreOperation The core operation to execute
     * @return The operation result
     */
    public OperationResult executeCrudOperation(PluginContext context,
                                                Supplier<OperationResult> coreOperation) {
        try {
            // BEFORE phase
            PluginResult beforeResult = executeBefore(context);
            if (beforeResult.isAborted()) {
                return OperationResult.failure(
                        beforeResult.getMessage().orElse("Operation aborted"),
                        beforeResult.getHttpStatus()
                );
            }

            // Core operation
            OperationResult result = coreOperation.get();

            // Update context with result
            result.getResource().ifPresent(context::setOutputResource);

            // AFTER phase
            executeAfter(context);

            // Return potentially modified result
            return context.getOutputResource()
                    .map(r -> result.withResource(r))
                    .orElse(result);

        } catch (Exception e) {
            log.error("Error during operation execution", e);
            executeOnError(context, e);
            return OperationResult.failure(e.getMessage(), 500);
        }
    }

    /**
     * Execute an extended operation with plugin pipeline.
     *
     * @param context       The plugin context
     * @param coreOperation The core operation to execute
     * @return The operation result
     */
    public OperationResult executeExtendedOperation(PluginContext context,
                                                    Supplier<OperationResult> coreOperation) {
        // Extended operations use the same pipeline as CRUD
        return executeCrudOperation(context, coreOperation);
    }

    /**
     * Execute business logic plugins specifically.
     *
     * @param context The business context
     * @return Business result
     */
    public BusinessLogicPlugin.BusinessResult executeBusinessLogicBefore(BusinessContext context) {
        List<FhirPlugin> businessPlugins = getMatchingPlugins(context.getPluginContext()).stream()
                .filter(p -> p instanceof BusinessLogicPlugin)
                .toList();

        for (FhirPlugin plugin : businessPlugins) {
            BusinessLogicPlugin blPlugin = (BusinessLogicPlugin) plugin;
            try {
                BusinessLogicPlugin.BusinessResult result = blPlugin.beforeOperation(context);
                if (result.isAborted()) {
                    return result;
                }
                if (result.getModifiedResource() != null) {
                    context.setCurrentResource(result.getModifiedResource());
                }
            } catch (Exception e) {
                log.error("Business logic plugin {} threw exception", plugin.getName(), e);
                return BusinessLogicPlugin.BusinessResult.abort("Plugin error: " + e.getMessage(), 500);
            }
        }

        return BusinessLogicPlugin.BusinessResult.proceed();
    }

    /**
     * Execute business logic plugins after operation.
     *
     * @param context The business context
     * @param result  The operation result
     * @return Business result
     */
    public BusinessLogicPlugin.BusinessResult executeBusinessLogicAfter(BusinessContext context,
                                                                        OperationResult result) {
        List<FhirPlugin> businessPlugins = getMatchingPlugins(context.getPluginContext()).stream()
                .filter(p -> p instanceof BusinessLogicPlugin)
                .toList();

        for (FhirPlugin plugin : businessPlugins) {
            BusinessLogicPlugin blPlugin = (BusinessLogicPlugin) plugin;
            try {
                BusinessLogicPlugin.BusinessResult blResult = blPlugin.afterOperation(context, result);
                if (blResult.isAborted()) {
                    return blResult;
                }
            } catch (Exception e) {
                log.error("Business logic plugin {} threw exception in after phase", plugin.getName(), e);
                // Don't fail for after-phase errors
            }
        }

        return BusinessLogicPlugin.BusinessResult.proceed();
    }
}
