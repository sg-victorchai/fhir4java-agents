package org.fhirframework.core.operation;

import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Collections;
import java.util.Map;

/**
 * Context information for executing a FHIR operation.
 */
public class OperationContext {

    private final String operationName;
    private final OperationScope scope;
    private final FhirVersion version;
    private final String resourceType;
    private final String resourceId;
    private final IBaseResource inputResource;
    private final Map<String, String> parameters;
    private final String tenantId;

    private OperationContext(Builder builder) {
        this.operationName = builder.operationName;
        this.scope = builder.scope;
        this.version = builder.version;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.inputResource = builder.inputResource;
        this.parameters = builder.parameters != null ?
                Collections.unmodifiableMap(builder.parameters) :
                Collections.emptyMap();
        this.tenantId = builder.tenantId != null ? builder.tenantId : "default";
    }

    public String getOperationName() {
        return operationName;
    }

    public OperationScope getScope() {
        return scope;
    }

    public FhirVersion getVersion() {
        return version;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public IBaseResource getInputResource() {
        return inputResource;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    public String getTenantId() {
        return tenantId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String operationName;
        private OperationScope scope;
        private FhirVersion version;
        private String resourceType;
        private String resourceId;
        private IBaseResource inputResource;
        private Map<String, String> parameters;
        private String tenantId;

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder scope(OperationScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder version(FhirVersion version) {
            this.version = version;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder inputResource(IBaseResource inputResource) {
            this.inputResource = inputResource;
            return this;
        }

        public Builder parameters(Map<String, String> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public OperationContext build() {
            return new OperationContext(this);
        }
    }
}
