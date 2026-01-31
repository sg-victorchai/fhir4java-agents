package org.fhirframework.plugin.business;

import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.Map;
import java.util.Optional;

/**
 * Context for business logic plugin execution.
 * <p>
 * Extends the base plugin context with business-specific information
 * such as the original and modified resources, operation parameters,
 * and operation-specific metadata.
 * </p>
 */
public class BusinessContext {

    private final PluginContext pluginContext;
    private final IBaseResource originalResource;
    private IBaseResource currentResource;
    private final Map<String, Object> operationParameters;
    private boolean aborted;
    private String abortReason;

    public BusinessContext(PluginContext pluginContext, IBaseResource originalResource,
                           Map<String, Object> operationParameters) {
        this.pluginContext = pluginContext;
        this.originalResource = originalResource;
        this.currentResource = originalResource;
        this.operationParameters = operationParameters != null ? operationParameters : Map.of();
        this.aborted = false;
    }

    /**
     * Create a business context from a plugin context.
     */
    public static BusinessContext from(PluginContext context) {
        return new BusinessContext(
                context,
                context.getInputResource().orElse(null),
                Map.of()
        );
    }

    /**
     * Create a business context with operation parameters.
     */
    public static BusinessContext from(PluginContext context, Map<String, Object> operationParameters) {
        return new BusinessContext(
                context,
                context.getInputResource().orElse(null),
                operationParameters
        );
    }

    // Delegate methods to PluginContext

    public String getRequestId() {
        return pluginContext.getRequestId();
    }

    public OperationType getOperationType() {
        return pluginContext.getOperationType();
    }

    public FhirVersion getFhirVersion() {
        return pluginContext.getFhirVersion();
    }

    public String getResourceType() {
        return pluginContext.getResourceType();
    }

    public Optional<String> getResourceId() {
        return pluginContext.getResourceId();
    }

    public Optional<String> getOperationCode() {
        return pluginContext.getOperationCode();
    }

    public Optional<String> getTenantId() {
        return pluginContext.getTenantId();
    }

    public Optional<String> getUserId() {
        return pluginContext.getUserId();
    }

    public Optional<String> getClientId() {
        return pluginContext.getClientId();
    }

    public Map<String, String[]> getSearchParameters() {
        return pluginContext.getParameters();
    }

    // Business-specific methods

    /**
     * Get the original resource (before any modifications).
     */
    public Optional<IBaseResource> getOriginalResource() {
        return Optional.ofNullable(originalResource);
    }

    /**
     * Get the current resource (may have been modified by plugins).
     */
    public Optional<IBaseResource> getCurrentResource() {
        return Optional.ofNullable(currentResource);
    }

    /**
     * Set the current resource (modified by a plugin).
     */
    public void setCurrentResource(IBaseResource resource) {
        this.currentResource = resource;
        pluginContext.setInputResource(resource);
    }

    /**
     * Get operation parameters for extended operations.
     */
    public Map<String, Object> getOperationParameters() {
        return operationParameters;
    }

    /**
     * Get a specific operation parameter.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOperationParameter(String name, Class<T> type) {
        Object value = operationParameters.get(name);
        if (value != null && type.isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    /**
     * Check if the operation has been aborted.
     */
    public boolean isAborted() {
        return aborted;
    }

    /**
     * Abort the operation with a reason.
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    /**
     * Get the abort reason.
     */
    public Optional<String> getAbortReason() {
        return Optional.ofNullable(abortReason);
    }

    /**
     * Get the underlying plugin context.
     */
    public PluginContext getPluginContext() {
        return pluginContext;
    }

    /**
     * Set an attribute on the underlying plugin context.
     */
    public void setAttribute(String key, Object value) {
        pluginContext.setAttribute(key, value);
    }

    /**
     * Get an attribute from the underlying plugin context.
     */
    public <T> Optional<T> getAttribute(String key, Class<T> type) {
        return pluginContext.getAttribute(key, type);
    }

    @Override
    public String toString() {
        return "BusinessContext{" +
                "requestId='" + getRequestId() + '\'' +
                ", operationType=" + getOperationType() +
                ", resourceType='" + getResourceType() + '\'' +
                ", aborted=" + aborted +
                '}';
    }
}
