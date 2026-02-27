package org.fhirframework.core.tenant;

import java.util.Optional;

/**
 * ThreadLocal-based holder for the current tenant ID.
 * <p>
 * Set by {@code TenantFilter} at the start of each request and
 * cleared in its {@code finally} block to prevent ThreadLocal leaks.
 * </p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> currentTenantId = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    /**
     * Get the current tenant ID, or {@code "default"} if not set.
     */
    public static String getCurrentTenantId() {
        String tenantId = currentTenantId.get();
        return tenantId != null ? tenantId : "default";
    }

    /**
     * Set the current tenant ID for this thread/request.
     */
    public static void setCurrentTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    /**
     * Get the current tenant ID as an Optional (empty if not explicitly set).
     */
    public static Optional<String> getTenantIdIfSet() {
        return Optional.ofNullable(currentTenantId.get());
    }

    /**
     * Clear the current tenant ID. Must be called in a {@code finally} block
     * to prevent ThreadLocal leaks in pooled thread environments.
     */
    public static void clear() {
        currentTenantId.remove();
    }
}
