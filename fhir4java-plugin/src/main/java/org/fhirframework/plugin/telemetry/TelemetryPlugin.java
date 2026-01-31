package org.fhirframework.plugin.telemetry;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;

import java.util.Map;

/**
 * Plugin interface for telemetry and observability.
 * <p>
 * Telemetry plugins emit metrics, traces, and logs for monitoring
 * and observability. They execute asynchronously to minimize
 * performance impact.
 * </p>
 */
public interface TelemetryPlugin extends FhirPlugin {

    /**
     * Attribute key for storing span context.
     */
    String SPAN_CONTEXT_ATTRIBUTE = "telemetry.spanContext";

    /**
     * Attribute key for storing trace ID.
     */
    String TRACE_ID_ATTRIBUTE = "telemetry.traceId";

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.ASYNC;
    }

    @Override
    default int getPriority() {
        // Telemetry runs late in the pipeline
        return 190;
    }

    /**
     * Start a span for the operation.
     * <p>
     * Called at the beginning of request processing.
     * </p>
     *
     * @param context The plugin context
     * @return Span context to be stored in the plugin context
     */
    default Object startSpan(PluginContext context) {
        return null;
    }

    /**
     * End the span for the operation.
     * <p>
     * Called when request processing completes (success or failure).
     * </p>
     *
     * @param context     The plugin context
     * @param spanContext The span context from startSpan
     * @param success     Whether the operation succeeded
     */
    default void endSpan(PluginContext context, Object spanContext, boolean success) {
        // Default: no-op
    }

    /**
     * Record a metric for the operation.
     *
     * @param name       Metric name
     * @param value      Metric value
     * @param tags       Additional tags
     */
    void recordMetric(String name, double value, Map<String, String> tags);

    /**
     * Record operation latency.
     *
     * @param context      The plugin context
     * @param latencyMs    Latency in milliseconds
     */
    default void recordLatency(PluginContext context, long latencyMs) {
        recordMetric("fhir.operation.latency", latencyMs, Map.of(
                "operation", context.getOperationType().name(),
                "resourceType", context.getResourceType() != null ? context.getResourceType() : "unknown",
                "version", context.getFhirVersion().getCode()
        ));
    }

    /**
     * Record operation count.
     *
     * @param context The plugin context
     * @param success Whether the operation succeeded
     */
    default void recordOperation(PluginContext context, boolean success) {
        recordMetric("fhir.operation.count", 1, Map.of(
                "operation", context.getOperationType().name(),
                "resourceType", context.getResourceType() != null ? context.getResourceType() : "unknown",
                "version", context.getFhirVersion().getCode(),
                "success", String.valueOf(success)
        ));
    }

    @Override
    default PluginResult executeBefore(PluginContext context) {
        Object spanContext = startSpan(context);
        if (spanContext != null) {
            context.setAttribute(SPAN_CONTEXT_ATTRIBUTE, spanContext);
        }
        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeAfter(PluginContext context) {
        context.getAttribute(SPAN_CONTEXT_ATTRIBUTE, Object.class)
                .ifPresent(spanContext -> endSpan(context, spanContext, true));
        recordOperation(context, true);
        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeOnError(PluginContext context, Exception exception) {
        context.getAttribute(SPAN_CONTEXT_ATTRIBUTE, Object.class)
                .ifPresent(spanContext -> endSpan(context, spanContext, false));
        recordOperation(context, false);
        return PluginResult.continueProcessing();
    }
}
