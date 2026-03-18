package org.fhirframework.core.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.config.SearchParameterConfig;
import org.fhirframework.core.interaction.InteractionType;
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
import java.util.concurrent.ConcurrentSkipListMap;
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

    // Use case-insensitive map to handle variations in resource type casing from clients
    // (e.g., "CarePlan" vs "CAREPLAN" vs "careplan")
    private final Map<String, ResourceConfiguration> resources =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final ObjectMapper yamlMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    @Value("${fhir4java.config.resources-path:classpath:fhir-config/resources/}")
    private String resourcesPath;

    @Value("${fhir4java.server.default-version:R5}")
    private String globalDefaultVersionStr;

    @Value("${spring.jpa.properties.hibernate.default_schema:fhir}")
    private String defaultSchema;

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

            // Validate schema configurations
            validateSchemaConfigurations();

        } catch (IOException e) {
            log.error("Failed to load resource configurations from: {}", resourcesPath, e);
            throw new RuntimeException("Failed to load resource configurations", e);
        }
    }

    /**
     * Validates schema configurations and logs warnings for potential issues.
     */
    private void validateSchemaConfigurations() {
        // Group resources by schema name
        Map<String, List<String>> schemaToResources = new java.util.HashMap<>();
        Map<String, List<String>> dedicatedSchemas = new java.util.HashMap<>();

        for (ResourceConfiguration config : resources.values()) {
            String schemaName = config.getSchema().getEffectiveSchemaName(defaultSchema);
            String resourceType = config.getResourceType();

            schemaToResources.computeIfAbsent(schemaName, k -> new java.util.ArrayList<>()).add(resourceType);

            if (config.getSchema().isDedicated()) {
                dedicatedSchemas.computeIfAbsent(schemaName, k -> new java.util.ArrayList<>()).add(resourceType);
            }
        }

        // Warn if multiple resources claim the same schema as "dedicated"
        for (Map.Entry<String, List<String>> entry : dedicatedSchemas.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.warn("Schema '{}' is marked as 'dedicated' by multiple resources: {}. " +
                         "Dedicated schemas should be exclusive to one resource type.",
                         entry.getKey(), entry.getValue());
            }
        }

        // Warn if a "dedicated" schema is also used by a "shared" resource
        for (Map.Entry<String, List<String>> entry : dedicatedSchemas.entrySet()) {
            String schemaName = entry.getKey();
            List<String> allResourcesInSchema = schemaToResources.get(schemaName);
            List<String> dedicatedResources = entry.getValue();

            if (allResourcesInSchema.size() > dedicatedResources.size()) {
                List<String> sharedResources = allResourcesInSchema.stream()
                        .filter(r -> !dedicatedResources.contains(r))
                        .toList();
                log.warn("Schema '{}' is marked as 'dedicated' by {} but also used as 'shared' by {}. " +
                         "Consider using consistent schema types.",
                         schemaName, dedicatedResources, sharedResources);
            }
        }

        // Log schema summary
        log.info("Schema configuration summary:");
        schemaToResources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String schemaName = entry.getKey();
                    List<String> resourceTypes = entry.getValue();
                    String schemaType = resourceTypes.size() == 1 &&
                            dedicatedSchemas.containsKey(schemaName) ? "dedicated" : "shared";
                    log.info("  Schema '{}' ({}): {}", schemaName, schemaType, resourceTypes);
                });
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
     * Checks if a resource type has a configuration (regardless of enabled status).
     * Used for validating resource type names.
     */
    public boolean isResourceConfigured(String resourceType) {
        return resources.containsKey(resourceType);
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
