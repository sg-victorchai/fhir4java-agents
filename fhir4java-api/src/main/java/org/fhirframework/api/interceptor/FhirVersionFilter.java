package org.fhirframework.api.interceptor;

import org.fhirframework.api.resolver.FhirVersionResolver;
import org.fhirframework.api.resolver.ResolvedVersion;
import org.fhirframework.core.exception.VersionNotSupportedException;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.version.FhirVersion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that resolves and validates FHIR version from incoming requests.
 * <p>
 * This filter:
 * <ul>
 *   <li>Resolves the FHIR version from the request path</li>
 *   <li>Validates that the resource supports the requested version</li>
 *   <li>Stores version information in request attributes</li>
 *   <li>Adds X-FHIR-Version header to responses</li>
 * </ul>
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FhirVersionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FhirVersionFilter.class);

    public static final String FHIR_VERSION_ATTRIBUTE = "fhir.version";
    public static final String RESOLVED_VERSION_ATTRIBUTE = "fhir.resolvedVersion";
    public static final String RESOURCE_TYPE_ATTRIBUTE = "fhir.resourceType";
    public static final String RESOURCE_ID_ATTRIBUTE = "fhir.resourceId";

    public static final String X_FHIR_VERSION_HEADER = "X-FHIR-Version";

    private final FhirVersionResolver versionResolver;
    private final ResourceRegistry resourceRegistry;

    public FhirVersionFilter(FhirVersionResolver versionResolver, ResourceRegistry resourceRegistry) {
        this.versionResolver = versionResolver;
        this.resourceRegistry = resourceRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip non-FHIR paths
        if (!isFhirPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Resolve version from path
            ResolvedVersion resolvedVersion = versionResolver.resolve(requestPath);
            FhirVersion version = resolvedVersion.version();

            // Extract resource type if present
            Optional<String> resourceType = versionResolver.extractResourceType(resolvedVersion.resourcePath());

            // Validate version support if resource type is known
            if (resourceType.isPresent()) {
                validateVersionSupport(resourceType.get(), version);
            }

            // Store resolved information in request attributes
            request.setAttribute(FHIR_VERSION_ATTRIBUTE, version);
            request.setAttribute(RESOLVED_VERSION_ATTRIBUTE, resolvedVersion);
            resourceType.ifPresent(rt -> request.setAttribute(RESOURCE_TYPE_ATTRIBUTE, rt));

            // Extract and store resource ID if present
            versionResolver.extractResourceId(resolvedVersion.resourcePath())
                    .ifPresent(id -> request.setAttribute(RESOURCE_ID_ATTRIBUTE, id));

            // Add version header to response
            response.setHeader(X_FHIR_VERSION_HEADER, version.getVersion());

            log.debug("FHIR request: version={}, explicit={}, resourceType={}, path={}",
                    version, resolvedVersion.explicit(),
                    resourceType.orElse("none"), resolvedVersion.resourcePath());

            filterChain.doFilter(request, response);

        } catch (VersionNotSupportedException e) {
            log.warn("Version not supported: {}", e.getMessage());
            throw e; // Let exception handler deal with it
        }
    }

    private boolean isFhirPath(String path) {
        return path != null && path.startsWith("/fhir");
    }

    private void validateVersionSupport(String resourceType, FhirVersion version) {
        if (resourceRegistry.isResourceSupported(resourceType)
                && !resourceRegistry.supportsVersion(resourceType, version)) {
            throw new VersionNotSupportedException(resourceType, version);
        }
    }

    /**
     * Utility method to get the FHIR version from a request.
     */
    public static FhirVersion getVersion(HttpServletRequest request) {
        Object version = request.getAttribute(FHIR_VERSION_ATTRIBUTE);
        if (version instanceof FhirVersion fv) {
            return fv;
        }
        return FhirVersion.R5; // Default fallback
    }

    /**
     * Utility method to get the resolved version info from a request.
     */
    public static ResolvedVersion getResolvedVersion(HttpServletRequest request) {
        Object resolved = request.getAttribute(RESOLVED_VERSION_ATTRIBUTE);
        if (resolved instanceof ResolvedVersion rv) {
            return rv;
        }
        return null;
    }

    /**
     * Utility method to get the resource type from a request.
     */
    public static Optional<String> getResourceType(HttpServletRequest request) {
        Object resourceType = request.getAttribute(RESOURCE_TYPE_ATTRIBUTE);
        if (resourceType instanceof String rt) {
            return Optional.of(rt);
        }
        return Optional.empty();
    }

    /**
     * Utility method to get the resource ID from a request.
     */
    public static Optional<String> getResourceId(HttpServletRequest request) {
        Object resourceId = request.getAttribute(RESOURCE_ID_ATTRIBUTE);
        if (resourceId instanceof String id) {
            return Optional.of(id);
        }
        return Optional.empty();
    }
}
