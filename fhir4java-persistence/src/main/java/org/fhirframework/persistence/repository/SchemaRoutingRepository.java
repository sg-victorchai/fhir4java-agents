package org.fhirframework.persistence.repository;

import org.fhirframework.core.schema.SchemaResolver;
import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Routes FHIR resource operations to the appropriate repository based on schema configuration.
 * <p>
 * This repository acts as a facade that checks the resource configuration to determine
 * whether to use the shared schema (via FhirResourceRepository/JPA) or a dedicated schema
 * (via DedicatedSchemaRepository/native SQL).
 * </p>
 */
@Repository
public class SchemaRoutingRepository {

    private static final Logger log = LoggerFactory.getLogger(SchemaRoutingRepository.class);

    private final FhirResourceRepository sharedRepository;
    private final DedicatedSchemaRepository dedicatedRepository;
    private final SchemaResolver schemaResolver;

    public SchemaRoutingRepository(
            FhirResourceRepository sharedRepository,
            DedicatedSchemaRepository dedicatedRepository,
            SchemaResolver schemaResolver) {
        this.sharedRepository = sharedRepository;
        this.dedicatedRepository = dedicatedRepository;
        this.schemaResolver = schemaResolver;
    }

    /**
     * Saves a FHIR resource entity, routing to the appropriate schema.
     *
     * @param resourceType the FHIR resource type
     * @param entity the entity to save
     * @return the saved entity
     */
    public FhirResourceEntity save(String resourceType, FhirResourceEntity entity) {
        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            log.debug("Routing save for {} to dedicated schema: {}", resourceType, schemaName);
            return dedicatedRepository.save(schemaName, entity);
        }
        log.debug("Routing save for {} to shared schema", resourceType);
        return sharedRepository.save(entity);
    }

    /**
     * Finds the current version of a resource.
     */
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String tenantId, String resourceType, String resourceId) {

        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return dedicatedRepository.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                tenantId, resourceType, resourceId);
    }

    /**
     * Finds a specific version of a resource.
     */
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
            String tenantId, String resourceType, String resourceId, Integer versionId) {

        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return dedicatedRepository.findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
                    schemaName, tenantId, resourceType, resourceId, versionId);
        }
        return sharedRepository.findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
                tenantId, resourceType, resourceId, versionId);
    }

    /**
     * Finds all versions of a resource ordered by version descending.
     */
    public List<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
            String tenantId, String resourceType, String resourceId) {

        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return dedicatedRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                    schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                tenantId, resourceType, resourceId);
    }

    /**
     * Checks if a resource exists.
     */
    public boolean existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String tenantId, String resourceType, String resourceId) {

        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return dedicatedRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                tenantId, resourceType, resourceId);
    }

    /**
     * Gets the maximum version ID for a resource.
     */
    public Integer findMaxVersionId(String tenantId, String resourceType, String resourceId) {
        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return dedicatedRepository.findMaxVersionId(schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.findMaxVersionId(tenantId, resourceType, resourceId);
    }

    /**
     * Marks all versions of a resource as not current.
     */
    public void markAllVersionsNotCurrent(String tenantId, String resourceType, String resourceId) {
        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            dedicatedRepository.markAllVersionsNotCurrent(schemaName, tenantId, resourceType, resourceId);
        } else {
            sharedRepository.markAllVersionsNotCurrent(tenantId, resourceType, resourceId);
        }
    }

    /**
     * Soft deletes a resource.
     */
    public void softDelete(String tenantId, String resourceType, String resourceId, Instant now) {
        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            dedicatedRepository.softDelete(schemaName, tenantId, resourceType, resourceId, now);
        } else {
            sharedRepository.softDelete(tenantId, resourceType, resourceId, now);
        }
    }

    /**
     * Searches for resources with parameters.
     */
    public Page<FhirResourceEntity> searchWithParams(
            String tenantId, String resourceType, Map<String, String> searchParams, Pageable pageable) {

        if (schemaResolver.isDedicatedSchema(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            log.debug("Routing search for {} to dedicated schema: {}", resourceType, schemaName);
            return dedicatedRepository.searchWithParams(schemaName, tenantId, resourceType, searchParams, pageable);
        }
        log.debug("Routing search for {} to shared schema", resourceType);
        return sharedRepository.searchWithParams(tenantId, resourceType, searchParams, pageable);
    }
}
