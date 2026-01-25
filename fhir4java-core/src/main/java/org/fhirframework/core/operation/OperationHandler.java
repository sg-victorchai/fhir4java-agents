package org.fhirframework.core.operation;

import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Interface for FHIR operation handlers.
 * <p>
 * Implement this interface to provide custom handling for FHIR extended
 * operations (e.g., $validate, $everything, $expand).
 * </p>
 */
public interface OperationHandler {

    /**
     * Get the operation name (without the $ prefix).
     *
     * @return operation name, e.g., "validate", "everything"
     */
    String getOperationName();

    /**
     * Get the operation scopes.
     *
     * @return the scopes where this operation can be invoked
     */
    OperationScope[] getScopes();

    /**
     * Get the resource types this operation applies to.
     * Return null or empty array for system-level operations.
     *
     * @return array of resource type names, or null for all types
     */
    String[] getResourceTypes();

    /**
     * Execute the operation.
     *
     * @param context operation context containing request details
     * @return the operation result resource
     */
    IBaseResource execute(OperationContext context);

    /**
     * Check if this operation supports the given FHIR version.
     *
     * @param version the FHIR version
     * @return true if supported
     */
    default boolean supportsVersion(FhirVersion version) {
        return true;
    }
}
