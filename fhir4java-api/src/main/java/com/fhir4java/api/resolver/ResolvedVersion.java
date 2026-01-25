package com.fhir4java.api.resolver;

import com.fhir4java.core.version.FhirVersion;

/**
 * Result of resolving a FHIR version from a request path.
 *
 * @param version      the resolved FHIR version
 * @param explicit     true if the version was explicitly specified in the URL path
 * @param resourcePath the remaining path after version extraction (e.g., "Patient/123")
 */
public record ResolvedVersion(
        FhirVersion version,
        boolean explicit,
        String resourcePath
) {

    /**
     * Creates a ResolvedVersion for an explicitly versioned path.
     */
    public static ResolvedVersion explicit(FhirVersion version, String resourcePath) {
        return new ResolvedVersion(version, true, resourcePath);
    }

    /**
     * Creates a ResolvedVersion for an unversioned path using the default version.
     */
    public static ResolvedVersion defaultVersion(FhirVersion version, String resourcePath) {
        return new ResolvedVersion(version, false, resourcePath);
    }
}
