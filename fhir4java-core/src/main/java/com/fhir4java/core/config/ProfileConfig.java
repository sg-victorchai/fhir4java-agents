package com.fhir4java.core.config;

/**
 * Profile configuration for a resource.
 * <p>
 * Specifies which StructureDefinition profiles apply to this resource
 * and whether conformance is required or optional.
 * </p>
 */
public class ProfileConfig {

    private String url;
    private boolean required;

    public ProfileConfig() {
    }

    public ProfileConfig(String url, boolean required) {
        this.url = url;
        this.required = required;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public String toString() {
        return "ProfileConfig{url='" + url + "', required=" + required + "}";
    }
}
