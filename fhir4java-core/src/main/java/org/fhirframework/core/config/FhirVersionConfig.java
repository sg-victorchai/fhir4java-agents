package org.fhirframework.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.fhirframework.core.version.FhirVersion;

/**
 * Configuration for a specific FHIR version within a resource configuration.
 */
public class FhirVersionConfig {

    private String version;

    @JsonProperty("default")
    private boolean defaultVersion;

    public FhirVersionConfig() {
    }

    public FhirVersionConfig(String version, boolean defaultVersion) {
        this.version = version;
        this.defaultVersion = defaultVersion;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isDefault() {
        return defaultVersion;
    }

    public void setDefault(boolean defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    /**
     * Converts the version string to FhirVersion enum.
     */
    public FhirVersion toFhirVersion() {
        return FhirVersion.fromCode(version);
    }

    @Override
    public String toString() {
        return "FhirVersionConfig{version='" + version + "', default=" + defaultVersion + "}";
    }
}
