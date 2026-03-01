package org.fhirframework.plugin.cache;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.OperationDescriptor;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Plugin interface for caching FHIR resources.
 * <p>
 * Cache plugins can serve cached responses for read operations
 * and invalidate cache entries on write operations.
 * They execute synchronously to enable cache hits.
 * </p>
 */
public interface CachePlugin extends FhirPlugin {

    /**
     * Attribute key indicating a cache hit.
     */
    String CACHE_HIT_ATTRIBUTE = "cache.hit";

    /**
     * Attribute key for the cached resource.
     */
    String CACHED_RESOURCE_ATTRIBUTE = "cache.resource";

    @Override
    default ExecutionMode getExecutionMode() {
        return ExecutionMode.SYNC;
    }

    @Override
    default int getPriority() {
        // Cache runs early, after auth
        return 30;
    }

    @Override
    default List<OperationDescriptor> getSupportedOperations() {
        // Cache only applies to certain operations
        return List.of(
                OperationDescriptor.forAllResources(OperationType.READ),
                OperationDescriptor.forAllResources(OperationType.VREAD),
                OperationDescriptor.forAllResources(OperationType.CREATE),
                OperationDescriptor.forAllResources(OperationType.UPDATE),
                OperationDescriptor.forAllResources(OperationType.DELETE)
        );
    }

    /**
     * Generate a resource-specific cache key for the given context.
     * <p>
     * Implementations should NOT include the tenant ID in the key;
     * tenant scoping is handled automatically by {@link #tenantScopedCacheKey(PluginContext)}.
     * </p>
     *
     * @param context The plugin context
     * @return Cache key, or empty if not cacheable
     */
    Optional<String> generateCacheKey(PluginContext context);

    /**
     * Generate a tenant-scoped cache key by prefixing the resource-specific
     * key with the tenant ID from the plugin context.
     * <p>
     * This ensures cache entries are isolated between tenants automatically.
     * </p>
     *
     * @param context The plugin context
     * @return Tenant-scoped cache key, or empty if not cacheable
     */
    default Optional<String> tenantScopedCacheKey(PluginContext context) {
        return generateCacheKey(context)
                .map(key -> context.getTenantId().orElse("default") + ":" + key);
    }

    /**
     * Get a cached resource.
     *
     * @param cacheKey The cache key
     * @return Cached resource, or empty if not in cache
     */
    Optional<IBaseResource> get(String cacheKey);

    /**
     * Put a resource in the cache.
     *
     * @param cacheKey The cache key
     * @param resource The resource to cache
     * @param ttl      Time-to-live for the cache entry
     */
    void put(String cacheKey, IBaseResource resource, Duration ttl);

    /**
     * Invalidate a cache entry.
     *
     * @param cacheKey The cache key
     */
    void invalidate(String cacheKey);

    /**
     * Invalidate all cache entries for a resource type.
     *
     * @param resourceType The resource type
     */
    void invalidateByType(String resourceType);

    /**
     * Invalidate all cache entries for a specific tenant.
     *
     * @param tenantId The tenant ID whose cache entries should be evicted
     */
    void invalidateByTenant(String tenantId);

    /**
     * Get the default TTL for cache entries.
     */
    default Duration getDefaultTtl() {
        return Duration.ofMinutes(5);
    }

    @Override
    default PluginResult executeBefore(PluginContext context) {
        // Only cache READ operations
        if (context.getOperationType() == OperationType.READ ||
            context.getOperationType() == OperationType.VREAD) {

            Optional<String> cacheKey = tenantScopedCacheKey(context);
            if (cacheKey.isPresent()) {
                Optional<IBaseResource> cached = get(cacheKey.get());
                if (cached.isPresent()) {
                    context.setAttribute(CACHE_HIT_ATTRIBUTE, true);
                    context.setAttribute(CACHED_RESOURCE_ATTRIBUTE, cached.get());
                    context.setOutputResource(cached.get());
                    return PluginResult.skipRemainingWithResource(cached.get());
                }
            }
        }

        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeAfter(PluginContext context) {
        OperationType opType = context.getOperationType();

        // Cache successful READ responses
        if ((opType == OperationType.READ || opType == OperationType.VREAD) &&
            !context.hasAttribute(CACHE_HIT_ATTRIBUTE)) {

            context.getOutputResource().ifPresent(resource -> {
                tenantScopedCacheKey(context).ifPresent(key ->
                        put(key, resource, getDefaultTtl())
                );
            });
        }

        // Invalidate cache on write operations
        if (opType.isWriteOperation()) {
            tenantScopedCacheKey(context).ifPresent(this::invalidate);
        }

        return PluginResult.continueProcessing();
    }
}
