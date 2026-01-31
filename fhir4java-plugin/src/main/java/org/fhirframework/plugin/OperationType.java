package org.fhirframework.plugin;

/**
 * Types of FHIR operations that plugins can intercept.
 */
public enum OperationType {
    /**
     * Read a single resource by ID.
     */
    READ,

    /**
     * Read a specific version of a resource.
     */
    VREAD,

    /**
     * Create a new resource.
     */
    CREATE,

    /**
     * Update an existing resource.
     */
    UPDATE,

    /**
     * Delete a resource.
     */
    DELETE,

    /**
     * Search for resources.
     */
    SEARCH,

    /**
     * Get resource history.
     */
    HISTORY,

    /**
     * Extended operation (e.g., $validate, $merge).
     */
    OPERATION,

    /**
     * Batch/transaction bundle processing.
     */
    BATCH,

    /**
     * CapabilityStatement/metadata request.
     */
    METADATA;

    /**
     * Check if this is a write operation (CREATE, UPDATE, DELETE).
     */
    public boolean isWriteOperation() {
        return this == CREATE || this == UPDATE || this == DELETE;
    }

    /**
     * Check if this is a read operation (READ, VREAD, SEARCH, HISTORY).
     */
    public boolean isReadOperation() {
        return this == READ || this == VREAD || this == SEARCH || this == HISTORY;
    }

    /**
     * Check if this is an extended operation.
     */
    public boolean isExtendedOperation() {
        return this == OPERATION;
    }
}
