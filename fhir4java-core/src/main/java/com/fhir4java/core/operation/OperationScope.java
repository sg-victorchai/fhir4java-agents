package com.fhir4java.core.operation;

/**
 * Scope at which a FHIR operation can be invoked.
 */
public enum OperationScope {
    /**
     * System-level operation: /fhir/$operation
     */
    SYSTEM,

    /**
     * Type-level operation: /fhir/Patient/$operation
     */
    TYPE,

    /**
     * Instance-level operation: /fhir/Patient/123/$operation
     */
    INSTANCE
}
