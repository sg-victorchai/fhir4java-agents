package org.fhirframework.core.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for multi-tenancy support.
 * <p>
 * Binds to {@code fhir4java.tenant.*} properties in application.yml.
 * </p>
 */
@ConfigurationProperties(prefix = "fhir4java.tenant")
public class TenantProperties {

    private boolean enabled = false;
    private String defaultTenantId = "default";
    private String headerName = "X-Tenant-ID";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
