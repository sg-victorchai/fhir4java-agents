package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for FHIR Resource operations.
 */
@Repository
public interface FhirResourceRepository extends JpaRepository<FhirResourceEntity, UUID>, FhirResourceRepositoryCustom {

    /**
     * Find the current version of a resource by type and ID.
     */
    Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String tenantId, String resourceType, String resourceId);

    /**
     * Find a specific version of a resource.
     */
    Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
            String tenantId, String resourceType, String resourceId, Integer versionId);

    /**
     * Find all versions of a resource (for history).
     */
    List<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
            String tenantId, String resourceType, String resourceId);

    /**
     * Find all current resources of a type.
     */
    Page<FhirResourceEntity> findByTenantIdAndResourceTypeAndIsCurrentTrueAndIsDeletedFalse(
            String tenantId, String resourceType, Pageable pageable);

    /**
     * Find all current resources updated after a specific time.
     */
    Page<FhirResourceEntity> findByTenantIdAndResourceTypeAndIsCurrentTrueAndIsDeletedFalseAndLastUpdatedAfter(
            String tenantId, String resourceType, Instant lastUpdated, Pageable pageable);

    /**
     * Count resources by type.
     */
    long countByTenantIdAndResourceTypeAndIsCurrentTrueAndIsDeletedFalse(
            String tenantId, String resourceType);

    /**
     * Check if a resource exists.
     */
    boolean existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String tenantId, String resourceType, String resourceId);

    /**
     * Get the maximum version ID for a resource.
     */
    @Query("SELECT COALESCE(MAX(r.versionId), 0) FROM FhirResourceEntity r " +
           "WHERE r.tenantId = :tenantId AND r.resourceType = :resourceType AND r.resourceId = :resourceId")
    Integer findMaxVersionId(
            @Param("tenantId") String tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    /**
     * Mark all versions of a resource as not current.
     */
    @Modifying
    @Query("UPDATE FhirResourceEntity r SET r.isCurrent = false " +
           "WHERE r.tenantId = :tenantId AND r.resourceType = :resourceType AND r.resourceId = :resourceId")
    void markAllVersionsNotCurrent(
            @Param("tenantId") String tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    /**
     * Soft delete a resource (mark as deleted).
     */
    @Modifying
    @Query("UPDATE FhirResourceEntity r SET r.isDeleted = true, r.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE r.tenantId = :tenantId AND r.resourceType = :resourceType AND r.resourceId = :resourceId AND r.isCurrent = true")
    void softDelete(
            @Param("tenantId") String tenantId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);

    /**
     * Find resources by type with history (for type-level history).
     */
    Page<FhirResourceEntity> findByTenantIdAndResourceTypeOrderByLastUpdatedDesc(
            String tenantId, String resourceType, Pageable pageable);
}
