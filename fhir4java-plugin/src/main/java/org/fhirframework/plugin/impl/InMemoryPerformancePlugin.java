package org.fhirframework.plugin.impl;

import org.fhirframework.plugin.performance.PerformancePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A performance plugin that tracks metrics in memory.
 * <p>
 * Useful for development and monitoring. For production use with
 * high traffic, consider exporting to external metrics systems.
 * </p>
 */
public class InMemoryPerformancePlugin implements PerformancePlugin {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPerformancePlugin.class);

    private static final int MAX_SAMPLES = 10000;

    private final Map<String, List<PerformanceMetrics>> metricsByType = new ConcurrentHashMap<>();
    private final List<PerformanceMetrics> allMetrics = new CopyOnWriteArrayList<>();

    private boolean enabled = true;

    @Override
    public String getName() {
        return "in-memory-performance";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void recordPerformance(PerformanceMetrics metrics) {
        // Store by resource type
        String key = metrics.resourceType() != null ? metrics.resourceType() : "unknown";
        metricsByType.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(metrics);

        // Store overall
        allMetrics.add(metrics);

        // Trim if too large
        trimMetrics();

        log.debug("PERF | {} {} | {}ms | success={}",
                metrics.operationType(),
                metrics.resourceType(),
                metrics.durationMs(),
                metrics.success());
    }

    private void trimMetrics() {
        if (allMetrics.size() > MAX_SAMPLES) {
            int toRemove = allMetrics.size() - MAX_SAMPLES;
            for (int i = 0; i < toRemove; i++) {
                allMetrics.remove(0);
            }
        }

        metricsByType.forEach((key, list) -> {
            if (list.size() > MAX_SAMPLES / 10) {
                int toRemove = list.size() - (MAX_SAMPLES / 10);
                for (int i = 0; i < toRemove; i++) {
                    list.remove(0);
                }
            }
        });
    }

    @Override
    public PerformanceStats getStats(String resourceType) {
        List<PerformanceMetrics> metrics = metricsByType.get(resourceType);
        if (metrics == null || metrics.isEmpty()) {
            return new PerformanceStats(resourceType, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return calculateStats(resourceType, metrics);
    }

    @Override
    public PerformanceStats getOverallStats() {
        if (allMetrics.isEmpty()) {
            return new PerformanceStats("overall", 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return calculateStats("overall", allMetrics);
    }

    private PerformanceStats calculateStats(String resourceType, List<PerformanceMetrics> metrics) {
        long total = metrics.size();
        long successful = metrics.stream().filter(PerformanceMetrics::success).count();
        long failed = total - successful;

        DoubleSummaryStatistics stats = metrics.stream()
                .mapToDouble(PerformanceMetrics::durationMs)
                .summaryStatistics();

        // Calculate percentiles
        List<Long> sortedDurations = metrics.stream()
                .map(PerformanceMetrics::durationMs)
                .sorted()
                .toList();

        double p50 = percentile(sortedDurations, 50);
        double p90 = percentile(sortedDurations, 90);
        double p99 = percentile(sortedDurations, 99);

        return new PerformanceStats(
                resourceType,
                total,
                successful,
                failed,
                stats.getAverage(),
                p50,
                p90,
                p99,
                (long) stats.getMax(),
                (long) stats.getMin()
        );
    }

    private double percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * Clear all collected metrics.
     */
    public void clear() {
        allMetrics.clear();
        metricsByType.clear();
    }

    /**
     * Get the number of collected metrics.
     */
    public int getMetricsCount() {
        return allMetrics.size();
    }
}
