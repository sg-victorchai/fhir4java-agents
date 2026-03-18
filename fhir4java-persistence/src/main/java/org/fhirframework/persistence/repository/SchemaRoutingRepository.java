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
 * This repository acts as a facade that routes operations based on the configured schema:
 * <ul>
 *   <li><b>Default schema ("fhir")</b>: Uses {@link FhirResourceRepository} (standard JPA)</li>
 *   <li><b>Custom schema</b>: Uses {@link SchemaAwareRepository} (JPA with dynamic schema switching)</li>
 * </ul>
 * </p>
 * <p>
 * Both paths use Hibernate/JPA for consistent type handling (including JSONB).
 * The schema name is configured in the resource YAML files via {@code schema.name}.
 * </p>
 * <p>
 * Examples:
 * <ul>
 *   <li>{@code schema.name: fhir} → Default repository (no schema switch)</li>
 *   <li>{@code schema.name: masterdata} → SchemaAwareRepository with "masterdata" schema</li>
 *   <li>{@code schema.name: careplan} → SchemaAwareRepository with "careplan" schema</li>
 * </ul>
 * </p>
 */
@Repository
public class SchemaRoutingRepository {

    private static final Logger log = LoggerFactory.getLogger(SchemaRoutingRepository.class);

    private final FhirResourceRepository sharedRepository;
    private final SchemaAwareRepository schemaAwareRepository;
    private final SchemaResolver schemaResolver;

    public SchemaRoutingRepository(
            FhirResourceRepository sharedRepository,
            SchemaAwareRepository schemaAwareRepository,
            SchemaResolver schemaResolver) {
        this.sharedRepository = sharedRepository;
        this.schemaAwareRepository = schemaAwareRepository;
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
        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            log.debug("Routing save for {} to custom schema: {}", resourceType, schemaName);
            return schemaAwareRepository.save(schemaName, entity);
        }
        log.debug("Routing save for {} to shared schema", resourceType);
        return sharedRepository.save(entity);
    }

    /**
     * Finds the current version of a resource.
     */
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String tenantId, String resourceType, String resourceId) {

        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return schemaAwareRepository.findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
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

        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return schemaAwareRepository.findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
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

        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return schemaAwareRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
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

        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return schemaAwareRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                    schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                tenantId, resourceType, resourceId);
    }

    /**
     * Gets the maximum version ID for a resource.
     */
    public Integer findMaxVersionId(String tenantId, String resourceType, String resourceId) {
        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            return schemaAwareRepository.findMaxVersionId(schemaName, tenantId, resourceType, resourceId);
        }
        return sharedRepository.findMaxVersionId(tenantId, resourceType, resourceId);
    }

    /**
     * Marks all versions of a resource as not current.
     */
    public void markAllVersionsNotCurrent(String tenantId, String resourceType, String resourceId) {
        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            schemaAwareRepository.markAllVersionsNotCurrent(schemaName, tenantId, resourceType, resourceId);
        } else {
            sharedRepository.markAllVersionsNotCurrent(tenantId, resourceType, resourceId);
        }
    }

    /**
     * Soft deletes a resource.
     */
    public void softDelete(String tenantId, String resourceType, String resourceId, Instant now) {
        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            schemaAwareRepository.softDelete(schemaName, tenantId, resourceType, resourceId, now);
        } else {
            sharedRepository.softDelete(tenantId, resourceType, resourceId, now);
        }
    }

    /**
     * Searches for resources with parameters.
     */
    public Page<FhirResourceEntity> searchWithParams(
            String tenantId, String resourceType, Map<String, String> searchParams, Pageable pageable) {

        if (schemaResolver.requiresSchemaSwitch(resourceType)) {
            String schemaName = schemaResolver.resolveSchema(resourceType);
            log.debug("Routing search for {} to custom schema: {}", resourceType, schemaName);
            return schemaAwareRepository.searchWithParams(schemaName, tenantId, resourceType, searchParams, pageable);
        }
        log.debug("Routing search for {} to shared schema", resourceType);
        return sharedRepository.searchWithParams(tenantId, resourceType, searchParams, pageable);
    }
}
