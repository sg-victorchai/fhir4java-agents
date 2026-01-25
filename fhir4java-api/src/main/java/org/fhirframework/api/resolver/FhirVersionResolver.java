package org.fhirframework.api.resolver;

import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves FHIR version from request URL paths.
 * <p>
 * Supports two URL patterns:
 * <ul>
 *   <li>Versioned: {@code /fhir/r5/Patient/123} - explicit version in path</li>
 *   <li>Unversioned: {@code /fhir/Patient/123} - uses default version</li>
 * </ul>
 * </p>
 */
@Component
public class FhirVersionResolver {

    private static final Logger log = LoggerFactory.getLogger(FhirVersionResolver.class);

    // Pattern to match versioned paths: /fhir/{version}/{resourcePath}
    // Captures: group(1) = version code (r5, r4b), group(2) = remaining path
    private static final Pattern VERSIONED_PATH_PATTERN =
            Pattern.compile("^/fhir/(r5|r4b)/(.*)$", Pattern.CASE_INSENSITIVE);

    // Pattern to match unversioned paths: /fhir/{resourcePath}
    // Captures: group(1) = remaining path
    private static final Pattern UNVERSIONED_PATH_PATTERN =
            Pattern.compile("^/fhir/(.*)$", Pattern.CASE_INSENSITIVE);

    // Pattern to extract resource type from path
    private static final Pattern RESOURCE_TYPE_PATTERN =
            Pattern.compile("^([A-Z][a-zA-Z]+)(?:/.*|\\?.*)?$");

    private final ResourceRegistry resourceRegistry;

    @Value("${fhir4java.server.base-path:/fhir}")
    private String basePath;

    public FhirVersionResolver(ResourceRegistry resourceRegistry) {
        this.resourceRegistry = resourceRegistry;
    }

    /**
     * Resolves the FHIR version from a request path.
     *
     * @param requestPath the full request URI path (e.g., "/fhir/r5/Patient/123")
     * @return the resolved version with path information
     */
    public ResolvedVersion resolve(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return ResolvedVersion.defaultVersion(
                    resourceRegistry.getGlobalDefaultVersion(), "");
        }

        // Try versioned path first
        Matcher versionedMatcher = VERSIONED_PATH_PATTERN.matcher(requestPath);
        if (versionedMatcher.matches()) {
            String versionCode = versionedMatcher.group(1);
            String resourcePath = versionedMatcher.group(2);
            FhirVersion version = FhirVersion.fromCode(versionCode);

            log.debug("Resolved explicit version {} from path: {}", version, requestPath);
            return ResolvedVersion.explicit(version, resourcePath);
        }

        // Try unversioned path
        Matcher unversionedMatcher = UNVERSIONED_PATH_PATTERN.matcher(requestPath);
        if (unversionedMatcher.matches()) {
            String resourcePath = unversionedMatcher.group(1);
            FhirVersion defaultVersion = resolveDefaultVersion(resourcePath);

            log.debug("Using default version {} for path: {}", defaultVersion, requestPath);
            return ResolvedVersion.defaultVersion(defaultVersion, resourcePath);
        }

        // Fallback to global default
        log.debug("Path does not match FHIR pattern, using global default: {}", requestPath);
        return ResolvedVersion.defaultVersion(
                resourceRegistry.getGlobalDefaultVersion(), requestPath);
    }

    /**
     * Resolves the default version for a resource path.
     * <p>
     * First tries to determine the resource type from the path and get its
     * configured default version. Falls back to global default if not found.
     * </p>
     */
    private FhirVersion resolveDefaultVersion(String resourcePath) {
        Optional<String> resourceType = extractResourceType(resourcePath);

        if (resourceType.isPresent()) {
            return resourceRegistry.getDefaultVersion(resourceType.get());
        }

        return resourceRegistry.getGlobalDefaultVersion();
    }

    /**
     * Extracts the resource type from a path.
     *
     * @param path the resource path (e.g., "Patient/123" or "Patient")
     * @return the resource type if found
     */
    public Optional<String> extractResourceType(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = RESOURCE_TYPE_PATTERN.matcher(path);
        if (matcher.matches()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    /**
     * Extracts the resource ID from a path.
     *
     * @param path the resource path (e.g., "Patient/123")
     * @return the resource ID if present
     */
    public Optional<String> extractResourceId(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        String[] parts = path.split("/");
        if (parts.length >= 2 && !parts[1].isEmpty() && !parts[1].startsWith("$") && !parts[1].startsWith("_")) {
            return Optional.of(parts[1]);
        }

        return Optional.empty();
    }

    /**
     * Checks if a path represents a versioned URL.
     */
    public boolean isVersionedPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        return VERSIONED_PATH_PATTERN.matcher(requestPath).matches();
    }

    /**
     * Builds a versioned URL path.
     *
     * @param version      the FHIR version
     * @param resourcePath the resource path
     * @return the full versioned path
     */
    public String buildVersionedPath(FhirVersion version, String resourcePath) {
        return basePath + "/" + version.getCode() + "/" + resourcePath;
    }

    /**
     * Builds an unversioned URL path.
     *
     * @param resourcePath the resource path
     * @return the full unversioned path
     */
    public String buildUnversionedPath(String resourcePath) {
        return basePath + "/" + resourcePath;
    }
}
