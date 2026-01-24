package com.fhir4java.core.searchparam;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fhir4java.core.context.FhirContextFactory;
import com.fhir4java.core.resource.ResourceRegistry;
import com.fhir4java.core.version.FhirVersion;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.SearchParameter;
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

/**
 * Registry for FHIR search parameters with version-aware loading.
 * <p>
 * Loads search parameter definitions from JSON files organized by FHIR version.
 * Supports the FHIR search parameter inheritance model:
 * - Resource-* parameters apply to all resources
 * - DomainResource-* parameters apply to all except Bundle, Parameters, Binary
 * - ResourceType-* parameters apply to specific resource types
 * </p>
 */
@Component
public class SearchParameterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SearchParameterRegistry.class);

    private static final Set<String> NON_DOMAIN_RESOURCES = Set.of("Bundle", "Parameters", "Binary");

    private final FhirContextFactory contextFactory;
    private final PathMatchingResourcePatternResolver resourceResolver;

    // Version -> List of Resource-* search parameters (applies to all resources)
    private final Map<FhirVersion, List<SearchParameter>> resourceBaseParams = new EnumMap<>(FhirVersion.class);

    // Version -> List of DomainResource-* search parameters (applies to non-Bundle/Parameters/Binary)
    private final Map<FhirVersion, List<SearchParameter>> domainResourceParams = new EnumMap<>(FhirVersion.class);

    // Version -> ResourceType -> List of SearchParameters
    private final Map<FhirVersion, Map<String, List<SearchParameter>>> resourceSpecificParams = new ConcurrentHashMap<>();

    // Version -> "ResourceType:paramName" -> SearchParameter (lookup cache)
    private final Map<FhirVersion, Map<String, SearchParameter>> parameterLookup = new ConcurrentHashMap<>();

    @Value("${fhir4java.config.base-path:classpath:fhir-config/}")
    private String basePath;

    public SearchParameterRegistry(FhirContextFactory contextFactory) {
        this.contextFactory = contextFactory;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    @PostConstruct
    public void loadSearchParameters() {
        log.info("Loading search parameters from: {}", basePath);

        for (FhirVersion version : FhirVersion.values()) {
            loadSearchParametersForVersion(version);
        }

        logSummary();
    }

    private void loadSearchParametersForVersion(FhirVersion version) {
        String versionPath = basePath + version.getCode() + "/searchparameters/";
        log.info("Loading search parameters for version {} from: {}", version, versionPath);

        resourceBaseParams.put(version, new ArrayList<>());
        domainResourceParams.put(version, new ArrayList<>());
        resourceSpecificParams.put(version, new ConcurrentHashMap<>());
        parameterLookup.put(version, new ConcurrentHashMap<>());

        try {
            String pattern = versionPath + "SearchParameter-*.json";
            Resource[] files = resourceResolver.getResources(pattern);
            log.debug("Found {} search parameter files for version {}", files.length, version);

            FhirContext fhirContext = contextFactory.getContext(version);
            IParser parser = fhirContext.newJsonParser();

            for (Resource file : files) {
                loadSearchParameterFile(version, file, parser);
            }

        } catch (IOException e) {
            log.warn("Failed to load search parameters for version {}: {}", version, e.getMessage());
        }
    }

    private void loadSearchParameterFile(FhirVersion version, Resource file, IParser parser) {
        String filename = file.getFilename();
        try (InputStream is = file.getInputStream()) {
            SearchParameter sp = parser.parseResource(SearchParameter.class, is);

            if (sp.getBase() == null || sp.getBase().isEmpty()) {
                log.warn("Skipping search parameter {} - no base resource types", filename);
                return;
            }

            // Determine category based on filename
            if (filename != null && filename.startsWith("SearchParameter-Resource-")) {
                resourceBaseParams.get(version).add(sp);
            } else if (filename != null && filename.startsWith("SearchParameter-DomainResource-")) {
                domainResourceParams.get(version).add(sp);
            } else {
                // Resource-specific parameter
                for (var base : sp.getBase()) {
                    String resourceType = base.getCode();
                    resourceSpecificParams.get(version)
                            .computeIfAbsent(resourceType, k -> new ArrayList<>())
                            .add(sp);

                    // Add to lookup cache
                    String key = resourceType + ":" + sp.getCode();
                    parameterLookup.get(version).put(key, sp);
                }
            }

            log.trace("Loaded search parameter: {} for base types: {}",
                    sp.getCode(), sp.getBase().stream().map(b -> b.getCode()).toList());

        } catch (Exception e) {
            log.error("Failed to parse search parameter file: {}", filename, e);
        }
    }

    private void logSummary() {
        for (FhirVersion version : FhirVersion.values()) {
            int resourceCount = resourceBaseParams.getOrDefault(version, Collections.emptyList()).size();
            int domainCount = domainResourceParams.getOrDefault(version, Collections.emptyList()).size();
            int specificCount = resourceSpecificParams.getOrDefault(version, Collections.emptyMap())
                    .values().stream().mapToInt(List::size).sum();

            log.info("Search parameters loaded for {}: Resource={}, DomainResource={}, ResourceSpecific={}",
                    version, resourceCount, domainCount, specificCount);
        }
    }

    /**
     * Returns all search parameters applicable to a resource type.
     * Includes inherited Resource-* and DomainResource-* parameters.
     */
    public List<SearchParameter> getSearchParameters(FhirVersion version, String resourceType) {
        List<SearchParameter> params = new ArrayList<>();

        // Add Resource-* parameters (apply to all)
        params.addAll(resourceBaseParams.getOrDefault(version, Collections.emptyList()));

        // Add DomainResource-* parameters (not for Bundle, Parameters, Binary)
        if (isDomainResource(resourceType)) {
            params.addAll(domainResourceParams.getOrDefault(version, Collections.emptyList()));
        }

        // Add resource-specific parameters
        Map<String, List<SearchParameter>> versionSpecific = resourceSpecificParams.get(version);
        if (versionSpecific != null) {
            params.addAll(versionSpecific.getOrDefault(resourceType, Collections.emptyList()));
        }

        return params;
    }

    /**
     * Returns a specific search parameter by name for a resource type.
     */
    public Optional<SearchParameter> getSearchParameter(FhirVersion version, String resourceType, String paramName) {
        // Check resource-specific first
        String key = resourceType + ":" + paramName;
        Map<String, SearchParameter> lookup = parameterLookup.get(version);
        if (lookup != null && lookup.containsKey(key)) {
            return Optional.of(lookup.get(key));
        }

        // Check Resource-* parameters
        for (SearchParameter sp : resourceBaseParams.getOrDefault(version, Collections.emptyList())) {
            if (sp.getCode().equals(paramName)) {
                return Optional.of(sp);
            }
        }

        // Check DomainResource-* parameters (if applicable)
        if (isDomainResource(resourceType)) {
            for (SearchParameter sp : domainResourceParams.getOrDefault(version, Collections.emptyList())) {
                if (sp.getCode().equals(paramName)) {
                    return Optional.of(sp);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if a search parameter is defined for a resource type.
     */
    public boolean isSearchParameterDefined(FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName).isPresent();
    }

    /**
     * Returns the search parameter type for a given parameter.
     */
    public Optional<Enumerations.SearchParamType> getSearchParameterType(
            FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName)
                .map(SearchParameter::getType);
    }

    /**
     * Returns the FHIRPath expression for a search parameter.
     */
    public Optional<String> getSearchParameterExpression(
            FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName)
                .map(SearchParameter::getExpression);
    }

    /**
     * Returns all allowed search parameters for a resource type,
     * considering resource configuration restrictions.
     */
    public List<SearchParameter> getAllowedSearchParameters(
            FhirVersion version, String resourceType, ResourceRegistry resourceRegistry) {

        List<SearchParameter> allParams = getSearchParameters(version, resourceType);

        // If no restrictions configured, return all parameters
        if (!resourceRegistry.hasSearchParameterRestrictions(resourceType)) {
            return allParams;
        }

        // Filter based on resource configuration
        return allParams.stream()
                .filter(sp -> {
                    boolean isCommon = isCommonParameter(sp.getCode());
                    return resourceRegistry.isSearchParameterAllowed(resourceType, sp.getCode(), isCommon);
                })
                .toList();
    }

    /**
     * Checks if a search parameter is allowed for a resource type,
     * considering both definition and configuration restrictions.
     */
    public boolean isSearchParameterAllowed(
            FhirVersion version, String resourceType, String paramName, ResourceRegistry resourceRegistry) {

        // First check if parameter is defined
        if (!isSearchParameterDefined(version, resourceType, paramName)) {
            return false;
        }

        // Then check configuration restrictions
        boolean isCommon = isCommonParameter(paramName);
        return resourceRegistry.isSearchParameterAllowed(resourceType, paramName, isCommon);
    }

    /**
     * Checks if a resource type is a DomainResource (for inheritance purposes).
     */
    public boolean isDomainResource(String resourceType) {
        return !NON_DOMAIN_RESOURCES.contains(resourceType);
    }

    /**
     * Checks if a parameter is a common parameter (applies to all resources).
     */
    private boolean isCommonParameter(String paramName) {
        // Common parameters start with underscore
        return paramName.startsWith("_");
    }

    /**
     * Returns all Resource-* search parameters for a version.
     */
    public List<SearchParameter> getResourceBaseParameters(FhirVersion version) {
        return Collections.unmodifiableList(
                resourceBaseParams.getOrDefault(version, Collections.emptyList()));
    }

    /**
     * Returns all DomainResource-* search parameters for a version.
     */
    public List<SearchParameter> getDomainResourceParameters(FhirVersion version) {
        return Collections.unmodifiableList(
                domainResourceParams.getOrDefault(version, Collections.emptyList()));
    }
}
