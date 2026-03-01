package org.fhirframework.persistence.service;

import org.fhirframework.core.exception.TenantDisabledException;
import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for resolving and validating tenants.
 * <p>
 * Resolves external tenant GUIDs (from HTTP headers) to internal tenant IDs
 * used for row-level isolation in resource tables. Caches lookups for performance.
 * </p>
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final FhirTenantRepository tenantRepository;
    private final TenantProperties tenantProperties;

    // TTL-based in-memory cache: external UUID -> CacheEntry(internal ID, expiry)
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private final Map<UUID, CacheEntry> tenantCache = new ConcurrentHashMap<>();
    private Duration cacheTtl = DEFAULT_CACHE_TTL;

    record CacheEntry(String internalId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public TenantService(FhirTenantRepository tenantRepository,
                         TenantProperties tenantProperties) {
        this.tenantRepository = tenantRepository;
        this.tenantProperties = tenantProperties;
    }

    /**
     * Get the effective tenant ID for a request, considering whether multi-tenancy is enabled.
     *
     * @param headerValue the raw X-Tenant-ID header value (may be null)
     * @return the internal tenant ID to use for this request
     * @throws TenantNotFoundException if the header value doesn't match a known tenant
     * @throws TenantDisabledException if the matched tenant is disabled
     * @throws IllegalArgumentException if the header is missing/empty when tenancy is enabled
     */
    public String resolveEffectiveTenantId(String headerValue) {
        if (!tenantProperties.isEnabled()) {
            return tenantProperties.getDefaultTenantId();
        }

        if (headerValue == null || headerValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required header '" + tenantProperties.getHeaderName() +
                    "' — multi-tenancy is enabled");
        }

        UUID externalId;
        try {
            externalId = UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid tenant ID format in header '" + tenantProperties.getHeaderName() +
                    "': expected UUID, got '" + headerValue + "'");
        }

        return resolveInternalTenantId(externalId);
    }

    /**
     * Resolve an external tenant UUID to its internal tenant ID.
     *
     * @param externalId the external tenant UUID
     * @return the internal tenant ID
     * @throws TenantNotFoundException if the UUID is not known
     * @throws TenantDisabledException if the tenant is disabled
     */
    public String resolveInternalTenantId(UUID externalId) {
        // Check cache first (with TTL)
        CacheEntry cached = tenantCache.get(externalId);
        if (cached != null && !cached.isExpired()) {
            return cached.internalId();
        }
        // Remove expired entry
        if (cached != null) {
            tenantCache.remove(externalId);
        }

        FhirTenantEntity tenant = tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TenantNotFoundException(externalId.toString()));

        if (!Boolean.TRUE.equals(tenant.getEnabled())) {
            throw new TenantDisabledException(externalId.toString());
        }

        // Cache the mapping with TTL
        tenantCache.put(externalId, new CacheEntry(tenant.getInternalId(), Instant.now().plus(cacheTtl)));
        log.debug("Resolved tenant: external={} -> internal={} (TTL={})", externalId, tenant.getInternalId(), cacheTtl);

        return tenant.getInternalId();
    }

    /**
     * Look up a tenant entity by external ID.
     */
    public Optional<FhirTenantEntity> findByExternalId(UUID externalId) {
        return tenantRepository.findByExternalId(externalId);
    }

    /**
     * Invalidate the cache for a specific tenant. Call after tenant update/disable.
     */
    public void invalidateCache(UUID externalId) {
        tenantCache.remove(externalId);
    }

    /**
     * Clear the entire tenant cache.
     */
    public void clearCache() {
        tenantCache.clear();
    }

    /**
     * Set the cache TTL. Useful for testing and runtime configuration.
     */
    public void setCacheTtl(Duration ttl) {
        this.cacheTtl = ttl;
        log.info("Tenant cache TTL set to {}", ttl);
    }

    /**
     * Get the current cache TTL.
     */
    public Duration getCacheTtl() {
        return cacheTtl;
    }

    /**
     * Get the current cache size (for monitoring/testing).
     */
    public int getCacheSize() {
        return tenantCache.size();
    }
}
