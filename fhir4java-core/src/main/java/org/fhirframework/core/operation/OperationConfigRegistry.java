package org.fhirframework.core.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fhirframework.core.version.FhirVersion;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for FHIR operation configurations loaded from YAML files.
 * <p>
 * Loads operation configurations from {@code fhir-config/{version}/operations/*.yml}
 * and provides version-aware access to operation settings.
 * </p>
 * <p>
 * If no configuration exists for an operation, it is considered unrestricted
 * (allowed if a handler is registered).
 * </p>
 */
@Component
public class OperationConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(OperationConfigRegistry.class);

    // version -> operationName -> config
    private final Map<String, Map<String, OperationConfiguration>> configurations = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Value("${fhir4java.config.operations-path:classpath:fhir-config/}")
    private String basePath;

    public OperationConfigRegistry() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void loadConfigurations() {
        log.info("Loading operation configurations from: {}", basePath);

        for (FhirVersion version : FhirVersion.values()) {
            loadConfigurationsForVersion(version);
        }

        int total = configurations.values().stream().mapToInt(Map::size).sum();
        log.info("Loaded {} operation configurations across {} versions", total, configurations.size());
    }

    private void loadConfigurationsForVersion(FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        String pattern = basePath + versionCode + "/operations/*.yml";

        try {
            Resource[] configFiles = resourceResolver.getResources(pattern);

            if (configFiles.length == 0) {
                log.debug("No operation configuration files found for version {} at pattern: {}", versionCode, pattern);
                return;
            }

            log.debug("Found {} operation configuration files for version {}", configFiles.length, versionCode);

            for (Resource configFile : configFiles) {
                loadOperationConfiguration(configFile, versionCode);
            }

        } catch (IOException e) {
            log.debug("No operation configurations found for version {}: {}", versionCode, e.getMessage());
        }
    }

    private void loadOperationConfiguration(Resource configFile, String versionCode) {
        String filename = configFile.getFilename();
        try (InputStream is = configFile.getInputStream()) {
            OperationConfiguration config = yamlMapper.readValue(is, OperationConfiguration.class);
            if (config.getOperationName() == null || config.getOperationName().isBlank()) {
                log.warn("Skipping operation configuration file {} - missing operationName", filename);
                return;
            }

            configurations
                    .computeIfAbsent(versionCode, k -> new ConcurrentHashMap<>())
                    .put(config.getOperationName(), config);

            log.debug("Loaded operation configuration: {} (enabled={}, version={})",
                    config.getOperationName(), config.isEnabled(), versionCode);

        } catch (IOException e) {
            log.error("Failed to load operation configuration from: {}", filename, e);
        }
    }

    /**
     * Check if an operation is enabled for a given FHIR version.
     * <p>
     * If no configuration exists, the operation is considered unrestricted (enabled).
     * </p>
     */
    public boolean isOperationEnabled(String operationName, FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        Map<String, OperationConfiguration> versionConfigs = configurations.get(versionCode);

        if (versionConfigs == null) {
            return true; // No config = unrestricted
        }

        OperationConfiguration config = versionConfigs.get(operationName);
        if (config == null) {
            return true; // No config for this operation = unrestricted
        }

        return config.isEnabled();
    }

    /**
     * Get the configuration for an operation, if it exists.
     */
    public Optional<OperationConfiguration> getConfiguration(String operationName, FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        Map<String, OperationConfiguration> versionConfigs = configurations.get(versionCode);

        if (versionConfigs == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(versionConfigs.get(operationName));
    }

    /**
     * Get all operation configurations for a given FHIR version.
     */
    public List<OperationConfiguration> getAllConfigurations(FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        Map<String, OperationConfiguration> versionConfigs = configurations.get(versionCode);

        if (versionConfigs == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(versionConfigs.values());
    }

    /**
     * Get all configured operation names for a given FHIR version.
     */
    public Set<String> getConfiguredOperations(FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        Map<String, OperationConfiguration> versionConfigs = configurations.get(versionCode);

        if (versionConfigs == null) {
            return Collections.emptySet();
        }

        return new HashSet<>(versionConfigs.keySet());
    }

    /**
     * Register an operation configuration programmatically.
     */
    public void registerConfiguration(OperationConfiguration config, FhirVersion version) {
        String versionCode = version.getCode().toLowerCase();
        configurations
                .computeIfAbsent(versionCode, k -> new ConcurrentHashMap<>())
                .put(config.getOperationName(), config);
    }
}
