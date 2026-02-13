package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Repository for FHIR resources stored in dedicated schemas.
 * <p>
 * Uses native SQL to access schema-qualified tables since JPA entities
 * are bound to a single schema. This allows dynamic schema routing for
 * resources configured with schema.type=dedicated.
 * </p>
 */
@Repository
public class DedicatedSchemaRepository {

    private static final Logger log = LoggerFactory.getLogger(DedicatedSchemaRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public DedicatedSchemaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Saves a FHIR resource entity to a dedicated schema.
     *
     * @param schemaName the dedicated schema name
     * @param entity the entity to save
     * @return the saved entity with generated ID
     */
    public FhirResourceEntity save(String schemaName, FhirResourceEntity entity) {
        validateSchemaName(schemaName);

        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }

        String sql = String.format("""
            INSERT INTO %s.fhir_resource (
                id, resource_type, resource_id, fhir_version, version_id,
                is_current, is_deleted, content, last_updated, created_at,
                source_uri, tenant_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """, schemaName);

        Instant now = Instant.now();
        if (entity.getLastUpdated() == null) {
            entity.setLastUpdated(now);
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }

        jdbcTemplate.update(sql,
                entity.getId(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getFhirVersion(),
                entity.getVersionId(),
                entity.getIsCurrent(),
                entity.getIsDeleted(),
                entity.getContent(),
                Timestamp.from(entity.getLastUpdated()),
                Timestamp.from(entity.getCreatedAt()),
                entity.getSourceUri(),
                entity.getTenantId()
        );

        log.debug("Saved {}/{} to schema {}", entity.getResourceType(), entity.getResourceId(), schemaName);
        return entity;
    }

    /**
     * Finds the current version of a resource.
     */
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String schemaName, String tenantId, String resourceType, String resourceId) {

        validateSchemaName(schemaName);

        String sql = String.format("""
            SELECT * FROM %s.fhir_resource
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? AND is_current = TRUE
            """, schemaName);

        List<FhirResourceEntity> results = jdbcTemplate.query(sql, new FhirResourceEntityRowMapper(),
                tenantId, resourceType, resourceId);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds a specific version of a resource.
     */
    public Optional<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
            String schemaName, String tenantId, String resourceType, String resourceId, Integer versionId) {

        validateSchemaName(schemaName);

        String sql = String.format("""
            SELECT * FROM %s.fhir_resource
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? AND version_id = ?
            """, schemaName);

        List<FhirResourceEntity> results = jdbcTemplate.query(sql, new FhirResourceEntityRowMapper(),
                tenantId, resourceType, resourceId, versionId);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Finds all versions of a resource ordered by version descending.
     */
    public List<FhirResourceEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
            String schemaName, String tenantId, String resourceType, String resourceId) {

        validateSchemaName(schemaName);

        String sql = String.format("""
            SELECT * FROM %s.fhir_resource
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
            ORDER BY version_id DESC
            """, schemaName);

        return jdbcTemplate.query(sql, new FhirResourceEntityRowMapper(),
                tenantId, resourceType, resourceId);
    }

    /**
     * Checks if a resource exists.
     */
    public boolean existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
            String schemaName, String tenantId, String resourceType, String resourceId) {

        validateSchemaName(schemaName);

        String sql = String.format("""
            SELECT COUNT(*) FROM %s.fhir_resource
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? AND is_current = TRUE
            """, schemaName);

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                tenantId, resourceType, resourceId);

        return count != null && count > 0;
    }

    /**
     * Gets the maximum version ID for a resource.
     */
    public Integer findMaxVersionId(String schemaName, String tenantId, String resourceType, String resourceId) {
        validateSchemaName(schemaName);

        String sql = String.format("""
            SELECT COALESCE(MAX(version_id), 0) FROM %s.fhir_resource
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
            """, schemaName);

        return jdbcTemplate.queryForObject(sql, Integer.class,
                tenantId, resourceType, resourceId);
    }

    /**
     * Marks all versions of a resource as not current.
     */
    public void markAllVersionsNotCurrent(String schemaName, String tenantId, String resourceType, String resourceId) {
        validateSchemaName(schemaName);

        String sql = String.format("""
            UPDATE %s.fhir_resource SET is_current = FALSE
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
            """, schemaName);

        jdbcTemplate.update(sql, tenantId, resourceType, resourceId);
    }

    /**
     * Soft deletes a resource.
     */
    public void softDelete(String schemaName, String tenantId, String resourceType, String resourceId, Instant now) {
        validateSchemaName(schemaName);

        String sql = String.format("""
            UPDATE %s.fhir_resource SET is_deleted = TRUE, last_updated = ?
            WHERE tenant_id = ? AND resource_type = ? AND resource_id = ? AND is_current = TRUE
            """, schemaName);

        jdbcTemplate.update(sql, Timestamp.from(now), tenantId, resourceType, resourceId);
    }

    /**
     * Searches for resources with parameters.
     * <p>
     * This is a simplified search implementation for dedicated schemas.
     * For complex search scenarios, the search predicates are built similarly
     * to FhirResourceRepositoryImpl but using native SQL.
     * </p>
     */
    public Page<FhirResourceEntity> searchWithParams(
            String schemaName, String tenantId, String resourceType,
            Map<String, String> searchParams, Pageable pageable) {

        validateSchemaName(schemaName);

        // Build base query conditions
        StringBuilder whereClause = new StringBuilder(
                "WHERE tenant_id = ? AND resource_type = ? AND is_current = TRUE AND is_deleted = FALSE");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(resourceType);

        // Add search parameter conditions
        for (Map.Entry<String, String> entry : searchParams.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();

            if (paramValue == null || paramValue.isBlank()) {
                continue;
            }

            // Skip pagination/special params
            if (isSpecialParam(paramName)) {
                continue;
            }

            // Handle _id specially
            if ("_id".equals(paramName)) {
                whereClause.append(" AND resource_id = ?");
                params.add(paramValue);
                continue;
            }

            // Handle _lastUpdated
            if ("_lastUpdated".equals(paramName)) {
                // Simplified: exact match on date
                whereClause.append(" AND DATE(last_updated) = DATE(?)");
                params.add(paramValue);
                continue;
            }

            // Generic JSONB search using @> operator for exact matches
            // This is a simplified implementation - complex searches should use the full
            // FhirResourceRepositoryImpl logic adapted for native SQL
            String jsonPath = mapParamToSimpleJsonPath(paramName);
            if (jsonPath != null) {
                whereClause.append(" AND content->>? ILIKE ?");
                params.add(jsonPath);
                params.add("%" + paramValue + "%");
            }
        }

        // Count query
        String countSql = String.format("SELECT COUNT(*) FROM %s.fhir_resource %s",
                schemaName, whereClause);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) total = 0L;

        // Data query with pagination
        String dataSql = String.format("""
            SELECT * FROM %s.fhir_resource %s
            ORDER BY last_updated DESC
            LIMIT ? OFFSET ?
            """, schemaName, whereClause);

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageable.getPageSize());
        dataParams.add(pageable.getOffset());

        List<FhirResourceEntity> results = jdbcTemplate.query(dataSql, new FhirResourceEntityRowMapper(),
                dataParams.toArray());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Validates schema name to prevent SQL injection.
     */
    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        // Only allow alphanumeric and underscore
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }

    private boolean isSpecialParam(String paramName) {
        return Set.of("_count", "_offset", "_sort", "_include", "_revinclude",
                "_summary", "_elements", "_contained", "_containedType",
                "_total", "_format").contains(paramName);
    }

    /**
     * Maps common search parameters to simple JSON paths.
     * This is a simplified version for basic searches.
     */
    private String mapParamToSimpleJsonPath(String paramName) {
        return switch (paramName) {
            case "status" -> "status";
            case "identifier" -> "identifier";
            case "subject" -> "subject";
            case "patient" -> "patient";
            case "category" -> "category";
            case "code" -> "code";
            case "date" -> "date";
            case "intent" -> "intent";
            case "title" -> "title";
            case "description" -> "description";
            default -> paramName;
        };
    }

    /**
     * Row mapper for FhirResourceEntity.
     */
    private static class FhirResourceEntityRowMapper implements RowMapper<FhirResourceEntity> {
        @Override
        public FhirResourceEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            FhirResourceEntity entity = new FhirResourceEntity();
            entity.setId(UUID.fromString(rs.getString("id")));
            entity.setResourceType(rs.getString("resource_type"));
            entity.setResourceId(rs.getString("resource_id"));
            entity.setFhirVersion(rs.getString("fhir_version"));
            entity.setVersionId(rs.getInt("version_id"));
            entity.setIsCurrent(rs.getBoolean("is_current"));
            entity.setIsDeleted(rs.getBoolean("is_deleted"));
            entity.setContent(rs.getString("content"));
            Timestamp lastUpdated = rs.getTimestamp("last_updated");
            if (lastUpdated != null) {
                entity.setLastUpdated(lastUpdated.toInstant());
            }
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                entity.setCreatedAt(createdAt.toInstant());
            }
            entity.setSourceUri(rs.getString("source_uri"));
            entity.setTenantId(rs.getString("tenant_id"));
            return entity;
        }
    }
}
