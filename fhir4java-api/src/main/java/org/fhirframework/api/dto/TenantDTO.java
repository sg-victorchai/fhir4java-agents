package org.fhirframework.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for tenant information exposed via the management API.
 * Decouples the API response from the JPA entity.
 */
public class TenantDTO {

    private Long id;
    private UUID externalId;
    private String internalId;
    private String tenantCode;
    private String tenantName;
    private String description;
    private Boolean enabled;
    private String settings;
    private Instant createdAt;
    private Instant updatedAt;

    public TenantDTO() {
    }

    public TenantDTO(Long id, UUID externalId, String internalId, String tenantCode,
                     String tenantName, String description, Boolean enabled,
                     String settings, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.externalId = externalId;
        this.internalId = internalId;
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
        this.description = description;
        this.enabled = enabled;
        this.settings = settings;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getExternalId() { return externalId; }
    public void setExternalId(UUID externalId) { this.externalId = externalId; }

    public String getInternalId() { return internalId; }
    public void setInternalId(String internalId) { this.internalId = internalId; }

    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
