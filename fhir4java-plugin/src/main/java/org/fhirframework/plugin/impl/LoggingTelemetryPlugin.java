package org.fhirframework.plugin.impl;

import org.fhirframework.plugin.telemetry.TelemetryPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A telemetry plugin that logs metrics using SLF4J.
 * <p>
 * Suitable for development and testing. For production use,
 * consider integrating with Prometheus, Datadog, or similar.
 * </p>
 */
public class LoggingTelemetryPlugin implements TelemetryPlugin {

    private static final Logger metricsLog = LoggerFactory.getLogger("metrics");

    private boolean enabled = true;

    @Override
    public String getName() {
        return "logging-telemetry";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        String tagsStr = tags.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));

        metricsLog.info("METRIC | {}={} | {}", name, value, tagsStr);
    }
}
