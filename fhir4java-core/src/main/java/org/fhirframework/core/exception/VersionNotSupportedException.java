package org.fhirframework.core.exception;

import org.fhirframework.core.version.FhirVersion;

/**
 * Exception thrown when a resource does not support the requested FHIR version.
 */
public class VersionNotSupportedException extends FhirException {

    private final String resourceType;
    private final FhirVersion requestedVersion;

    public VersionNotSupportedException(String resourceType, FhirVersion requestedVersion) {
        super(String.format("Resource type '%s' does not support FHIR version '%s'",
                resourceType, requestedVersion.getCode()),
                "not-supported",
                String.format("The requested FHIR version %s is not available for %s resources",
                        requestedVersion.getCode().toUpperCase(), resourceType));
        this.resourceType = resourceType;
        this.requestedVersion = requestedVersion;
    }

    public String getResourceType() {
        return resourceType;
    }

    public FhirVersion getRequestedVersion() {
        return requestedVersion;
    }
}
