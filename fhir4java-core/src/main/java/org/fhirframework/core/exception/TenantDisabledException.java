package org.fhirframework.core.exception;

/**
 * Exception thrown when a request targets a disabled tenant.
 */
public class TenantDisabledException extends FhirException {

    private final String tenantExternalId;

    public TenantDisabledException(String tenantExternalId) {
        super(String.format("Tenant with external ID '%s' is disabled", tenantExternalId),
                "forbidden",
                "The target tenant is currently disabled and cannot process requests");
        this.tenantExternalId = tenantExternalId;
    }

    public String getTenantExternalId() {
        return tenantExternalId;
    }
}
