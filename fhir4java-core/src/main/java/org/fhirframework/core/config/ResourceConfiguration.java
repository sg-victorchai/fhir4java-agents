package org.fhirframework.core.config;

import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.version.FhirVersion;

import java.util.*;

/**
 * Complete configuration for a FHIR resource type.
 * <p>
 * Loaded from YAML files in fhir-config/resources/ directory.
 * Supports multi-version FHIR, interaction controls, and search parameter restrictions.
 * </p>
 */
public class ResourceConfiguration {

    private String resourceType;
    private boolean enabled = true;
    private List<FhirVersionConfig> fhirVersions = new ArrayList<>();
    private SchemaConfig schema = new SchemaConfig();
    private InteractionsConfig interactions = new InteractionsConfig();
    private SearchParameterConfig searchParameters;
    private List<ProfileConfig> profiles = new ArrayList<>();

    public ResourceConfiguration() {
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<FhirVersionConfig> getFhirVersions() {
        return fhirVersions;
    }

    public void setFhirVersions(List<FhirVersionConfig> fhirVersions) {
        this.fhirVersions = fhirVersions != null ? fhirVersions : new ArrayList<>();
    }

    public SchemaConfig getSchema() {
        return schema;
    }

    public void setSchema(SchemaConfig schema) {
        this.schema = schema;
    }

    public InteractionsConfig getInteractions() {
        return interactions;
    }

    public void setInteractions(InteractionsConfig interactions) {
        this.interactions = interactions;
    }

    public SearchParameterConfig getSearchParameters() {
        return searchParameters;
    }

    public void setSearchParameters(SearchParameterConfig searchParameters) {
        this.searchParameters = searchParameters;
    }

    public List<ProfileConfig> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileConfig> profiles) {
        this.profiles = profiles != null ? profiles : new ArrayList<>();
    }

    // ========== Version Support Methods ==========

    /**
     * Returns the default FHIR version for this resource.
     * Falls back to R5 if no default is specified.
     */
    public FhirVersion getDefaultVersion() {
        return fhirVersions.stream()
                .filter(FhirVersionConfig::isDefault)
                .map(FhirVersionConfig::toFhirVersion)
                .findFirst()
                .orElse(FhirVersion.R5);
    }

    /**
     * Checks if this resource supports the given FHIR version.
     */
    public boolean supportsVersion(FhirVersion version) {
        if (fhirVersions.isEmpty()) {
            // If no versions specified, assume R5 only
            return version == FhirVersion.R5;
        }
        return fhirVersions.stream()
                .map(FhirVersionConfig::toFhirVersion)
                .anyMatch(v -> v == version);
    }

    /**
     * Returns all supported FHIR versions for this resource.
     */
    public Set<FhirVersion> getSupportedVersions() {
        if (fhirVersions.isEmpty()) {
            return EnumSet.of(FhirVersion.R5);
        }
        Set<FhirVersion> versions = EnumSet.noneOf(FhirVersion.class);
        for (FhirVersionConfig config : fhirVersions) {
            versions.add(config.toFhirVersion());
        }
        return versions;
    }

    // ========== Interaction Methods ==========

    /**
     * Checks if the given interaction is enabled for this resource.
     */
    public boolean isInteractionEnabled(InteractionType type) {
        return enabled && interactions.isEnabled(type);
    }

    /**
     * Returns all enabled interactions for this resource.
     */
    public Set<InteractionType> getEnabledInteractions() {
        if (!enabled) {
            return EnumSet.noneOf(InteractionType.class);
        }
        return interactions.getEnabledInteractions();
    }

    // ========== Search Parameter Methods ==========

    /**
     * Checks if a search parameter is allowed for this resource.
     *
     * @param paramName the parameter name
     * @param isCommon  true if this is a common parameter (_id, _lastUpdated, etc.)
     * @return true if the parameter is allowed, or if no restrictions are configured
     */
    public boolean isSearchParameterAllowed(String paramName, boolean isCommon) {
        if (searchParameters == null || !searchParameters.hasRestrictions()) {
            return true; // No restrictions means all parameters allowed
        }
        return searchParameters.isAllowed(paramName, isCommon);
    }

    /**
     * Returns true if this resource has search parameter restrictions configured.
     */
    public boolean hasSearchParameterRestrictions() {
        return searchParameters != null && searchParameters.hasRestrictions();
    }

    /**
     * Returns the search parameter configuration, or null if not configured.
     */
    public Optional<SearchParameterConfig> getSearchParameterConfig() {
        return Optional.ofNullable(searchParameters);
    }

    // ========== Profile Methods ==========

    /**
     * Returns all required profile URLs for this resource.
     */
    public List<String> getRequiredProfiles() {
        return profiles.stream()
                .filter(ProfileConfig::isRequired)
                .map(ProfileConfig::getUrl)
                .toList();
    }

    /**
     * Returns all profile URLs (required and optional) for this resource.
     */
    public List<String> getAllProfiles() {
        return profiles.stream()
                .map(ProfileConfig::getUrl)
                .toList();
    }

    @Override
    public String toString() {
        return "ResourceConfiguration{resourceType='" + resourceType + "'" +
                ", enabled=" + enabled +
                ", fhirVersions=" + fhirVersions +
                ", interactions=" + interactions +
                ", hasSearchRestrictions=" + hasSearchParameterRestrictions() + "}";
    }
}
