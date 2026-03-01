package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for the tenant mapping table.
 * <p>
 * Maps external tenant GUIDs (received via X-Tenant-ID header) to
 * internal tenant IDs used for row-level isolation in resource tables.
 * </p>
 */
@Entity
@Table(name = "fhir_tenant", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FhirTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private UUID externalId;

    @Column(name = "internal_id", nullable = false, unique = true, length = 64)
    private String internalId;

    @Column(name = "tenant_code", length = 50)
    private String tenantCode;

    @Column(name = "tenant_name", length = 255)
    private String tenantName;

    @Column(name = "description")
    private String description;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
