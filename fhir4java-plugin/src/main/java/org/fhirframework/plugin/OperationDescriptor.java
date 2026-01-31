package org.fhirframework.plugin;

import org.fhirframework.core.version.FhirVersion;

import java.util.Objects;

/**
 * Descriptor for matching plugins to operations.
 * <p>
 * Used by plugins to declare which operations they handle and by the
 * PluginOrchestrator to match incoming requests to appropriate plugins.
 * </p>
 * <p>
 * Supports wildcards via null values:
 * - null resourceType matches all resource types
 * - null operationType matches all operation types
 * - null operationCode matches all operations (for OPERATION type) or is ignored
 * - null fhirVersion matches all FHIR versions
 * </p>
 */
public record OperationDescriptor(
        String resourceType,
        OperationType operationType,
        String operationCode,
        FhirVersion fhirVersion
) {

    /**
     * Create a descriptor for a CRUD operation.
     */
    public static OperationDescriptor forCrud(String resourceType, OperationType operationType, FhirVersion version) {
        return new OperationDescriptor(resourceType, operationType, null, version);
    }

    /**
     * Create a descriptor for an extended operation.
     */
    public static OperationDescriptor forOperation(String resourceType, String operationCode, FhirVersion version) {
        return new OperationDescriptor(resourceType, OperationType.OPERATION, operationCode, version);
    }

    /**
     * Create a wildcard descriptor that matches all operations for a resource type.
     */
    public static OperationDescriptor forAllOperations(String resourceType) {
        return new OperationDescriptor(resourceType, null, null, null);
    }

    /**
     * Create a wildcard descriptor that matches all resources for an operation type.
     */
    public static OperationDescriptor forAllResources(OperationType operationType) {
        return new OperationDescriptor(null, operationType, null, null);
    }

    /**
     * Create a wildcard descriptor that matches everything.
     */
    public static OperationDescriptor matchAll() {
        return new OperationDescriptor(null, null, null, null);
    }

    /**
     * Check if this descriptor matches the given context.
     * <p>
     * A null value in the descriptor acts as a wildcard that matches any value.
     * </p>
     */
    public boolean matches(OperationDescriptor other) {
        if (other == null) {
            return false;
        }

        // Check resource type (null = wildcard)
        if (resourceType != null && !resourceType.equals(other.resourceType)) {
            return false;
        }

        // Check operation type (null = wildcard)
        if (operationType != null && operationType != other.operationType) {
            return false;
        }

        // Check operation code for extended operations (null = wildcard)
        if (operationType == OperationType.OPERATION && operationCode != null) {
            if (!operationCode.equals(other.operationCode)) {
                return false;
            }
        }

        // Check FHIR version (null = wildcard)
        if (fhirVersion != null && fhirVersion != other.fhirVersion) {
            return false;
        }

        return true;
    }

    /**
     * Check if this descriptor matches the given context.
     */
    public boolean matches(PluginContext context) {
        return matches(context.toDescriptor());
    }

    /**
     * Calculate specificity score for prioritizing plugin matches.
     * Higher score means more specific match.
     */
    public int getSpecificity() {
        int score = 0;
        if (resourceType != null) score += 4;
        if (operationType != null) score += 2;
        if (operationCode != null) score += 2;
        if (fhirVersion != null) score += 1;
        return score;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OperationDescriptor{");
        sb.append("resourceType=").append(resourceType != null ? resourceType : "*");
        sb.append(", operationType=").append(operationType != null ? operationType : "*");
        if (operationType == OperationType.OPERATION) {
            sb.append(", operationCode=").append(operationCode != null ? operationCode : "*");
        }
        sb.append(", fhirVersion=").append(fhirVersion != null ? fhirVersion.getCode() : "*");
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationDescriptor that = (OperationDescriptor) o;
        return Objects.equals(resourceType, that.resourceType) &&
                operationType == that.operationType &&
                Objects.equals(operationCode, that.operationCode) &&
                fhirVersion == that.fhirVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, operationType, operationCode, fhirVersion);
    }
}
