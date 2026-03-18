package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema-aware repository that delegates to {@link DedicatedSchemaRepository}.
 * <p>
 * This repository acts as a facade for non-default schema operations, delegating
 * all work to {@link DedicatedSchemaRepository} which uses JdbcTemplate with
 * native SQL for dynamic schema routing.
 * </p>
 * <p>
 * <b>Why JdbcTemplate instead of Hibernate?</b>
 * Hibernate resolves {@code @Table(schema=...)} once at startup into fixed SQL.
 * There is no supported public API to override the schema per-call at runtime
 * without hacking the SessionFactory. JdbcTemplate with
 * {@code String.format("%s.fhir_resource", schemaName)} is the idiomatic,
 * reliable, and test-friendly solution for dynamic schema routing.
 * </p>
 *
 * @see DedicatedSchemaRepository
 */
@Repository
public class SchemaAwareRepository {

    private static final Logger log = LoggerFactory.getLogger(SchemaAwareRepository.class);

    private final DedicatedSchemaRepository delegate;

    public SchemaAwareRepository(DedicatedSchemaRepository dedicatedSchemaRepository) {
        this.delegate = dedicatedSchemaRepository;
    }

    // ========== CRUD Operations ==========

    /**
     * Saves a FHIR resource entity to a specific schema.
     *
     * @param schemaName the target schema name
     * @param entity the entity to save
     * @return the saved entity with generated ID
     */
    @Transactional
    public FhirResourceEntity save(String schemaName, FhirResourceEntity entity) {
        log.debug("Saving {}/{} to schema {}", entity.getResourceType(), entity.getResourceId(), schemaName);
        return delegate.save(schemaName, entity);
    }

    /**
     * Finds the current version of a resource.
     */
    @Transactional(readOnly = true)
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String schemaName, String tenantId, String resourceType, String resourceId) {
        return delegate.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                schemaName, tenantId, resourceType, resourceId);
    }

    /**
     * Finds a specific version of a resource.
     */
    @Transactional(readOnly = true)
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
            String schemaName, String tenantId, String resourceType, String resourceId, Integer versionId) {
        return delegate.findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
                schemaName, tenantId, resourceType, resourceId, versionId);
    }

    /**
     * Finds all versions of a resource ordered by version descending.
     */
    @Transactional(readOnly = true)
    public List<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
            String schemaName, String tenantId, String resourceType, String resourceId) {
        return delegate.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                schemaName, tenantId, resourceType, resourceId);
    }

    /**
     * Checks if a resource exists.
     */
    @Transactional(readOnly = true)
    public boolean existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String schemaName, String tenantId, String resourceType, String resourceId) {
        return delegate.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                schemaName, tenantId, resourceType, resourceId);
    }

    /**
     * Gets the maximum version ID for a resource.
     */
    @Transactional(readOnly = true)
    public Integer findMaxVersionId(String schemaName, String tenantId, String resourceType, String resourceId) {
        return delegate.findMaxVersionId(schemaName, tenantId, resourceType, resourceId);
    }

    /**
     * Marks all versions of a resource as not current.
     */
    @Transactional
    public void markAllVersionsNotCurrent(String schemaName, String tenantId, String resourceType, String resourceId) {
        delegate.markAllVersionsNotCurrent(schemaName, tenantId, resourceType, resourceId);
    }

    /**
     * Soft deletes a resource.
     */
    @Transactional
    public void softDelete(String schemaName, String tenantId, String resourceType, String resourceId, Instant now) {
        delegate.softDelete(schemaName, tenantId, resourceType, resourceId, now);
    }

    /**
     * Searches for resources with parameters.
     */
    @Transactional(readOnly = true)
    public Page<FhirResourceEntity> searchWithParams(
            String schemaName, String tenantId, String resourceType,
            Map<String, String> searchParams, Pageable pageable) {
        return delegate.searchWithParams(schemaName, tenantId, resourceType, searchParams, pageable);
    }
}
