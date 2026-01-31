package org.fhirframework.plugin.performance;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Plugin interface for performance tracking.
 * <p>
 * Performance plugins track operation timing and resource usage
 * for performance monitoring and optimization.
 * </p>
 */
public interface PerformancePlugin extends FhirPlugin {

    /**
     * Attribute key for storing operation start time.
     */
    String START_TIME_ATTRIBUTE = "perf.startTime";

    /**
     * Attribute key for storing performance metrics.
     */
    String METRICS_ATTRIBUTE = "perf.metrics";

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNC;
    }

    @Override
    default int getPriority() {
        // Performance tracking runs very early
        return 5;
    }

    /**
     * Record performance metrics for an operation.
     *
     * @param metrics The performance metrics
     */
    void recordPerformance(PerformanceMetrics metrics);

    /**
     * Get performance statistics for a resource type.
     *
     * @param resourceType The resource type
     * @return Performance statistics
     */
    PerformanceStats getStats(String resourceType);

    /**
     * Get overall performance statistics.
     *
     * @return Overall performance statistics
     */
    PerformanceStats getOverallStats();

    @Override
    default PluginResult executeBefore(PluginContext context) {
        context.setAttribute(START_TIME_ATTRIBUTE, Instant.now());
        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeAfter(PluginContext context) {
        recordMetrics(context, true);
        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeOnError(PluginContext context, Exception exception) {
        recordMetrics(context, false);
        return PluginResult.continueProcessing();
    }

    private void recordMetrics(PluginContext context, boolean success) {
        context.getAttribute(START_TIME_ATTRIBUTE, Instant.class).ifPresent(startTime -> {
            Duration duration = Duration.between(startTime, Instant.now());
            PerformanceMetrics metrics = new PerformanceMetrics(
                    context.getRequestId(),
                    context.getTimestamp(),
                    context.getOperationType().name(),
                    context.getResourceType(),
                    context.getFhirVersion().getCode(),
                    duration.toMillis(),
                    success,
                    Map.of()
            );
            recordPerformance(metrics);
        });
    }

    /**
     * Performance metrics for a single operation.
     */
    record PerformanceMetrics(
            String requestId,
            Instant timestamp,
            String operationType,
            String resourceType,
            String fhirVersion,
            long durationMs,
            boolean success,
            Map<String, Object> additionalMetrics
    ) {
        public PerformanceMetrics withAdditionalMetrics(Map<String, Object> metrics) {
            return new PerformanceMetrics(
                    requestId, timestamp, operationType, resourceType,
                    fhirVersion, durationMs, success, metrics
            );
        }
    }

    /**
     * Aggregated performance statistics.
     */
    record PerformanceStats(
            String resourceType,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double averageDurationMs,
            double p50DurationMs,
            double p90DurationMs,
            double p99DurationMs,
            long maxDurationMs,
            long minDurationMs
    ) {
        public double getSuccessRate() {
            if (totalRequests == 0) return 0.0;
            return (double) successfulRequests / totalRequests;
        }

        public double getFailureRate() {
            if (totalRequests == 0) return 0.0;
            return (double) failedRequests / totalRequests;
        }
    }
}
