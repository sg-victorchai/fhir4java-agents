package com.fhir4java.core.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fhir4java.core.config.ResourceConfiguration;
import com.fhir4java.core.config.SearchParameterConfig;
import com.fhir4java.core.interaction.InteractionType;
import com.fhir4java.core.version.FhirVersion;
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
 * Central registry for FHIR resource configurations.
 * <p>
 * Loads resource configurations from YAML files and provides version-aware
 * access to resource settings, interaction controls, and search parameter restrictions.
 * </p>
 */
@Component
public class ResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ResourceRegistry.class);

    private final Map<String, ResourceConfiguration> resources = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Value("${fhir4java.config.resources-path:classpath:fhir-config/resources/}")
    private String resourcesPath;

    @Value("${fhir4java.server.default-version:R5}")
    private String globalDefaultVersionStr;

    private FhirVersion globalDefaultVersion;

    public ResourceRegistry() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void loadResourceConfigurations() {
        this.globalDefaultVersion = FhirVersion.fromCode(globalDefaultVersionStr);
        log.info("Loading resource configurations from: {}", resourcesPath);
        log.info("Global default FHIR version: {}", globalDefaultVersion);

        try {
            String pattern = resourcesPath + "*.yml";
            Resource[] configFiles = resourceResolver.getResources(pattern);
            log.info("Found {} resource configuration files", configFiles.length);

            for (Resource configFile : configFiles) {
                loadResourceConfiguration(configFile);
            }

            log.info("Loaded {} resource configurations: {}",
                    resources.size(),
                    resources.keySet().stream().sorted().collect(Collectors.joining(", ")));

        } catch (IOException e) {
            log.error("Failed to load resource configurations from: {}", resourcesPath, e);
            throw new RuntimeException("Failed to load resource configurations", e);
        }
    }

    private void loadResourceConfiguration(Resource configFile) {
        String filename = configFile.getFilename();
        try (InputStream is = configFile.getInputStream()) {
            ResourceConfiguration config = yamlMapper.readValue(is, ResourceConfiguration.class);
            if (config.getResourceType() == null || config.getResourceType().isBlank()) {
                log.warn("Skipping configuration file {} - missing resourceType", filename);
                return;
            }

            registerResource(config);
            log.debug("Loaded configuration for resource type: {} (enabled={}, versions={})",
                    config.getResourceType(),
                    config.isEnabled(),
                    config.getSupportedVersions());

        } catch (IOException e) {
            log.error("Failed to load resource configuration from: {}", filename, e);
        }
    }

    /**
     * Registers a resource configuration programmatically.
     */
    public void registerResource(ResourceConfiguration config) {
        resources.put(config.getResourceType(), config);
    }

    /**
     * Returns the configuration for a resource type, if it exists.
     */
    public Optional<ResourceConfiguration> getResource(String resourceType) {
        return Optional.ofNullable(resources.get(resourceType));
    }

    /**
     * Returns all registered resource configurations.
     */
    public List<ResourceConfiguration> getAllResources() {
        return new ArrayList<>(resources.values());
    }

    /**
     * Returns all enabled resource configurations.
     */
    public List<ResourceConfiguration> getEnabledResources() {
        return resources.values().stream()
                .filter(ResourceConfiguration::isEnabled)
                .toList();
    }

    /**
     * Returns all resources that support the given FHIR version.
     */
    public List<ResourceConfiguration> getResourcesForVersion(FhirVersion version) {
        return resources.values().stream()
                .filter(ResourceConfiguration::isEnabled)
                .filter(r -> r.supportsVersion(version))
                .toList();
    }

    /**
     * Checks if a resource type is registered and enabled.
     */
    public boolean isResourceSupported(String resourceType) {
        ResourceConfiguration config = resources.get(resourceType);
        return config != null && config.isEnabled();
    }

    /**
     * Returns the default FHIR version for a resource type.
     * Falls back to the global default if not specified in resource config.
     */
    public FhirVersion getDefaultVersion(String resourceType) {
        return getResource(resourceType)
                .map(ResourceConfiguration::getDefaultVersion)
                .orElse(globalDefaultVersion);
    }

    /**
     * Checks if a resource type supports the given FHIR version.
     */
    public boolean supportsVersion(String resourceType, FhirVersion version) {
        return getResource(resourceType)
                .map(r -> r.supportsVersion(version))
                .orElse(false);
    }

    /**
     * Returns all supported FHIR versions for a resource type.
     */
    public Set<FhirVersion> getSupportedVersions(String resourceType) {
        return getResource(resourceType)
                .map(ResourceConfiguration::getSupportedVersions)
                .orElse(Collections.emptySet());
    }

    /**
     * Checks if an interaction is enabled for a resource type.
     */
    public boolean isInteractionEnabled(String resourceType, InteractionType type) {
        return getResource(resourceType)
                .map(r -> r.isInteractionEnabled(type))
                .orElse(false);
    }

    /**
     * Checks if an interaction is enabled for a resource type and version.
     */
    public boolean isInteractionEnabled(String resourceType, FhirVersion version, InteractionType type) {
        return getResource(resourceType)
                .filter(r -> r.supportsVersion(version))
                .map(r -> r.isInteractionEnabled(type))
                .orElse(false);
    }

    /**
     * Returns all enabled interactions for a resource type.
     */
    public Set<InteractionType> getEnabledInteractions(String resourceType) {
        return getResource(resourceType)
                .map(ResourceConfiguration::getEnabledInteractions)
                .orElse(EnumSet.noneOf(InteractionType.class));
    }

    /**
     * Checks if a search parameter is allowed for a resource type.
     *
     * @param resourceType the resource type
     * @param paramName    the parameter name
     * @param isCommon     true if this is a common parameter (_id, _lastUpdated, etc.)
     * @return true if allowed, false if denied by configuration
     */
    public boolean isSearchParameterAllowed(String resourceType, String paramName, boolean isCommon) {
        return getResource(resourceType)
                .map(r -> r.isSearchParameterAllowed(paramName, isCommon))
                .orElse(true); // If resource not configured, allow all parameters
    }

    /**
     * Returns the search parameter configuration for a resource type.
     */
    public Optional<SearchParameterConfig> getSearchParameterConfig(String resourceType) {
        return getResource(resourceType)
                .flatMap(ResourceConfiguration::getSearchParameterConfig);
    }

    /**
     * Checks if a resource type has search parameter restrictions configured.
     */
    public boolean hasSearchParameterRestrictions(String resourceType) {
        return getResource(resourceType)
                .map(ResourceConfiguration::hasSearchParameterRestrictions)
                .orElse(false);
    }

    /**
     * Returns all required profile URLs for a resource type.
     */
    public List<String> getRequiredProfiles(String resourceType) {
        return getResource(resourceType)
                .map(ResourceConfiguration::getRequiredProfiles)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns the global default FHIR version.
     */
    public FhirVersion getGlobalDefaultVersion() {
        return globalDefaultVersion;
    }
}
