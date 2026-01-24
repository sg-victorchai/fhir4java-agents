package com.fhir4java.core.context;

import ca.uhn.fhir.context.FhirContext;
import com.fhir4java.core.version.FhirVersion;

/**
 * Factory interface for obtaining version-specific FHIR contexts.
 * <p>
 * HAPI FHIR contexts are expensive to create and should be reused.
 * This factory provides cached contexts for each supported FHIR version.
 * </p>
 */
public interface FhirContextFactory {

    /**
     * Returns the FHIR context for the specified version.
     *
     * @param version the FHIR version
     * @return the cached FhirContext instance
     * @throws IllegalArgumentException if the version is not supported
     */
    FhirContext getContext(FhirVersion version);

    /**
     * Returns the FHIR context for R5 (default version).
     */
    default FhirContext getR5Context() {
        return getContext(FhirVersion.R5);
    }

    /**
     * Returns the FHIR context for R4B.
     */
    default FhirContext getR4BContext() {
        return getContext(FhirVersion.R4B);
    }

    /**
     * Returns the default FHIR context (R5).
     */
    default FhirContext getDefaultContext() {
        return getR5Context();
    }
}
