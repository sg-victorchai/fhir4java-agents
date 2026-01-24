package com.fhir4java.core.config;

/**
 * Database schema configuration for a resource.
 */
public class SchemaConfig {

    private String type = "shared";
    private String name = "fhir";

    public SchemaConfig() {
    }

    public SchemaConfig(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns true if this is a shared schema (multiple resources in one schema).
     */
    public boolean isShared() {
        return "shared".equalsIgnoreCase(type);
    }

    /**
     * Returns true if this is a dedicated schema (one resource per schema).
     */
    public boolean isDedicated() {
        return "dedicated".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return "SchemaConfig{type='" + type + "', name='" + name + "'}";
    }
}
