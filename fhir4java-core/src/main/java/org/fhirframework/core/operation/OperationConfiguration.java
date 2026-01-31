package org.fhirframework.core.operation;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a FHIR extended operation.
 * <p>
 * Loaded from YAML files in fhir-config/{version}/operations/ directory.
 * Controls whether operations are enabled and which scopes/resource types they apply to.
 * </p>
 */
public class OperationConfiguration {

    private String operationName;
    private boolean enabled = true;
    private String description;
    private List<String> scopes = new ArrayList<>();
    private List<String> resourceTypes = new ArrayList<>();
    private List<ParameterConfig> parameters = new ArrayList<>();

    public OperationConfiguration() {
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes != null ? scopes : new ArrayList<>();
    }

    public List<String> getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(List<String> resourceTypes) {
        this.resourceTypes = resourceTypes != null ? resourceTypes : new ArrayList<>();
    }

    public List<ParameterConfig> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterConfig> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "OperationConfiguration{operationName='" + operationName + "'" +
                ", enabled=" + enabled +
                ", scopes=" + scopes +
                ", resourceTypes=" + resourceTypes + "}";
    }

    /**
     * Configuration for an operation parameter.
     */
    public static class ParameterConfig {
        private String name;
        private String type;
        private boolean required;
        private String description;

        public ParameterConfig() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
