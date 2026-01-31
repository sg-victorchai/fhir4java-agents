package org.fhirframework.plugin;

import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Context information passed to plugins during execution.
 * <p>
 * Contains all relevant information about the current request,
 * including the operation type, resource details, and request metadata.
 * </p>
 */
public class PluginContext {

    private final String requestId;
    private final Instant timestamp;
    private final OperationType operationType;
    private final FhirVersion fhirVersion;
    private final String resourceType;
    private final String resourceId;
    private final String operationCode;
    private final Map<String, String[]> parameters;
    private final Map<String, Object> attributes;

    // Mutable fields that may be set during processing
    private IBaseResource inputResource;
    private IBaseResource outputResource;
    private String tenantId;
    private String userId;
    private String clientId;

    private PluginContext(Builder builder) {
        this.requestId = builder.requestId != null ? builder.requestId : UUID.randomUUID().toString();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.operationType = builder.operationType;
        this.fhirVersion = builder.fhirVersion;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.operationCode = builder.operationCode;
        this.parameters = builder.parameters != null ? new HashMap<>(builder.parameters) : new HashMap<>();
        this.attributes = new HashMap<>();
        this.inputResource = builder.inputResource;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.clientId = builder.clientId;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public FhirVersion getFhirVersion() {
        return fhirVersion;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Optional<String> getResourceId() {
        return Optional.ofNullable(resourceId);
    }

    public Optional<String> getOperationCode() {
        return Optional.ofNullable(operationCode);
    }

    public Map<String, String[]> getParameters() {
        return new HashMap<>(parameters);
    }

    public Optional<String[]> getParameter(String name) {
        return Optional.ofNullable(parameters.get(name));
    }

    public Optional<String> getFirstParameter(String name) {
        String[] values = parameters.get(name);
        if (values != null && values.length > 0) {
            return Optional.of(values[0]);
        }
        return Optional.empty();
    }

    public Optional<IBaseResource> getInputResource() {
        return Optional.ofNullable(inputResource);
    }

    public Optional<IBaseResource> getOutputResource() {
        return Optional.ofNullable(outputResource);
    }

    public Optional<String> getTenantId() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<String> getUserId() {
        return Optional.ofNullable(userId);
    }

    public Optional<String> getClientId() {
        return Optional.ofNullable(clientId);
    }

    // Attribute management for plugin communication

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    // Setters for mutable fields

    public void setInputResource(IBaseResource resource) {
        this.inputResource = resource;
    }

    public void setOutputResource(IBaseResource resource) {
        this.outputResource = resource;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Create a descriptor that uniquely identifies this operation for plugin matching.
     */
    public OperationDescriptor toDescriptor() {
        return new OperationDescriptor(
                resourceType,
                operationType,
                operationCode,
                fhirVersion
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PluginContext{");
        sb.append("requestId='").append(requestId).append('\'');
        sb.append(", operation=").append(operationType);
        sb.append(", resourceType='").append(resourceType).append('\'');
        if (resourceId != null) {
            sb.append(", resourceId='").append(resourceId).append('\'');
        }
        if (operationCode != null) {
            sb.append(", operationCode='").append(operationCode).append('\'');
        }
        sb.append(", version=").append(fhirVersion);
        sb.append('}');
        return sb.toString();
    }

    public static class Builder {
        private String requestId;
        private Instant timestamp;
        private OperationType operationType;
        private FhirVersion fhirVersion;
        private String resourceType;
        private String resourceId;
        private String operationCode;
        private Map<String, String[]> parameters;
        private IBaseResource inputResource;
        private String tenantId;
        private String userId;
        private String clientId;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder operationType(OperationType operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder fhirVersion(FhirVersion fhirVersion) {
            this.fhirVersion = fhirVersion;
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

        public Builder operationCode(String operationCode) {
            this.operationCode = operationCode;
            return this;
        }

        public Builder parameters(Map<String, String[]> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder inputResource(IBaseResource inputResource) {
            this.inputResource = inputResource;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public PluginContext build() {
            if (operationType == null) {
                throw new IllegalStateException("operationType is required");
            }
            if (fhirVersion == null) {
                throw new IllegalStateException("fhirVersion is required");
            }
            if (resourceType == null && operationType != OperationType.BATCH && operationType != OperationType.METADATA) {
                throw new IllegalStateException("resourceType is required for operation: " + operationType);
            }
            return new PluginContext(this);
        }
    }
}
