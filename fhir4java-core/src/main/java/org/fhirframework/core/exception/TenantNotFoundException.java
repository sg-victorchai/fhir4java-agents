package org.fhirframework.core.exception;

/**
 * Exception thrown when a tenant cannot be found by its external ID.
 */
public class TenantNotFoundException extends FhirException {

    private final String tenantExternalId;

    public TenantNotFoundException(String tenantExternalId) {
        super(String.format("Tenant with external ID '%s' not found", tenantExternalId),
                "security",
                "The provided X-Tenant-ID does not match any known tenant");
        this.tenantExternalId = tenantExternalId;
    }

    public String getTenantExternalId() {
        return tenantExternalId;
    }
}
