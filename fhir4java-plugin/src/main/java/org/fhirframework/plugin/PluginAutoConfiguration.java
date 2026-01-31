package org.fhirframework.plugin;

import org.fhirframework.plugin.impl.InMemoryPerformancePlugin;
import org.fhirframework.plugin.impl.LoggingAuditPlugin;
import org.fhirframework.plugin.impl.LoggingTelemetryPlugin;
import org.fhirframework.plugin.impl.NoOpAuthenticationPlugin;
import org.fhirframework.plugin.impl.NoOpAuthorizationPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Auto-configuration for the plugin system.
 * <p>
 * Registers default plugins based on configuration and any
 * Spring-managed FhirPlugin beans found in the context.
 * </p>
 */
@Configuration
public class PluginAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginAutoConfiguration.class);

    private final PluginOrchestrator orchestrator;
    private final PluginConfig config;
    private final List<FhirPlugin> discoveredPlugins;

    @Autowired
    public PluginAutoConfiguration(PluginOrchestrator orchestrator,
                                   PluginConfig config,
                                   @Autowired(required = false) List<FhirPlugin> discoveredPlugins) {
        this.orchestrator = orchestrator;
        this.config = config;
        this.discoveredPlugins = discoveredPlugins != null ? discoveredPlugins : List.of();
    }

    @PostConstruct
    public void configurePlugins() {
        if (!config.isPluginsEnabled()) {
            log.info("Plugin system is disabled");
            return;
        }

        log.info("Configuring plugin system...");

        // Register default plugins based on configuration
        registerDefaultPlugins();

        // Register any discovered Spring-managed plugins
        registerDiscoveredPlugins();

        log.info("Plugin system configured with {} plugins", orchestrator.getPlugins().size());
    }

    private void registerDefaultPlugins() {
        // Performance tracking (runs first to capture full request timing)
        if (config.isPerformanceEnabled()) {
            InMemoryPerformancePlugin performancePlugin = new InMemoryPerformancePlugin();
            orchestrator.registerPlugin(performancePlugin);
            log.debug("Registered default performance plugin");
        }

        // Authentication (if enabled, uses NoOp by default)
        if (config.isAuthenticationEnabled()) {
            NoOpAuthenticationPlugin authPlugin = new NoOpAuthenticationPlugin();
            orchestrator.registerPlugin(authPlugin);
            log.debug("Registered NoOp authentication plugin");
        }

        // Authorization (if enabled, uses NoOp by default)
        if (config.isAuthorizationEnabled()) {
            NoOpAuthorizationPlugin authzPlugin = new NoOpAuthorizationPlugin();
            orchestrator.registerPlugin(authzPlugin);
            log.debug("Registered NoOp authorization plugin");
        }

        // Telemetry
        if (config.isTelemetryEnabled()) {
            LoggingTelemetryPlugin telemetryPlugin = new LoggingTelemetryPlugin();
            orchestrator.registerPlugin(telemetryPlugin);
            log.debug("Registered logging telemetry plugin");
        }

        // Audit logging
        if (config.isAuditEnabled()) {
            LoggingAuditPlugin auditPlugin = new LoggingAuditPlugin();
            orchestrator.registerPlugin(auditPlugin);
            log.debug("Registered logging audit plugin");
        }
    }

    private void registerDiscoveredPlugins() {
        for (FhirPlugin plugin : discoveredPlugins) {
            // Skip plugins that are already part of auto-configuration
            if (isDefaultPlugin(plugin)) {
                continue;
            }

            orchestrator.registerPlugin(plugin);
            log.info("Registered discovered plugin: {}", plugin.getName());
        }
    }

    private boolean isDefaultPlugin(FhirPlugin plugin) {
        return plugin instanceof NoOpAuthenticationPlugin ||
               plugin instanceof NoOpAuthorizationPlugin ||
               plugin instanceof LoggingAuditPlugin ||
               plugin instanceof LoggingTelemetryPlugin ||
               plugin instanceof InMemoryPerformancePlugin;
    }
}
