package org.fhirframework.persistence.repository;

import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.hl7.fhir.r5.model.Enumerations.SearchParamType;
import org.hl7.fhir.r5.model.SearchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repository for FHIR resources stored in non-default schemas.
 * <p>
 * Uses JdbcTemplate with native SQL to access schema-qualified tables.
 * This is the correct approach for dynamic schema routing because:
 * <ul>
 *   <li>Hibernate resolves {@code @Table(schema=...)} at startup - there's no supported API
 *       to override the schema per-call at runtime</li>
 *   <li>JdbcTemplate with {@code String.format("%s.fhir_resource", schemaName)} provides
 *       reliable, test-friendly dynamic schema routing</li>
 *   <li>{@code Types.OTHER} for JSONB content allows proper type handling in both
 *       PostgreSQL (JSONB) and H2 (CLOB in PostgreSQL mode)</li>
 * </ul>
 * </p>
 * <p>
 * This repository is used by {@link SchemaAwareRepository} as a delegate for all
 * non-default schema operations.
 * </p>
 */
@Repository
public class DedicatedSchemaRepository {

    private static final Logger log = LoggerFactory.getLogger(DedicatedSchemaRepository.class);

    // Pattern to extract prefix from value: (prefix)(value)
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(eq|ne|lt|gt|le|ge|sa|eb|ap)(.+)$");

    // Common FHIR search parameter prefixes that don't affect filtering
    private static final Set<String> SPECIAL_PARAMS = Set.of(
            "_count", "_offset", "_sort", "_include", "_revinclude",
            "_summary", "_elements", "_contained", "_containedType",
            "_total", "_format"
    );

    // Fallback date-type search parameters (when SearchParameterRegistry not available)
    private static final Set<String> DATE_PARAMS = Set.of(
            "_lastUpdated", "birthDate", "date", "authored", "issued",
            "effective", "onset", "recorded", "created", "period"
    );

    // Fallback number-type search parameters
    private static final Set<String> NUMBER_PARAMS = Set.of(
            "length", "value", "probability", "age"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SearchParameterRegistry searchParameterRegistry;

    public DedicatedSchemaRepository(JdbcTemplate jdbcTemplate, SearchParameterRegistry searchParameterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchParameterRegistry = searchParameterRegistry;
    }

    /**
     * Saves a FHIR resource entity to a dedicated schema.
     * <p>
     * Uses {@code Types.OTHER} for the JSONB content column to allow the JDBC driver
     * to handle the type conversion properly. This works for both PostgreSQL (JSONB)
     * and H2 (CLOB in PostgreSQL compatibility mode).
     * </p>
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
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, schemaName);

        Instant now = Instant.now();
        if (entity.getLastUpdated() == null) {
            entity.setLastUpdated(now);
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }

        // Use PreparedStatementSetter to have explicit control over SQL types.
        // Types.OTHER for the content column tells the JDBC driver to handle
        // the type based on column metadata (works for PostgreSQL JSONB and H2 CLOB).
        jdbcTemplate.update(sql, (PreparedStatement ps) -> {
            ps.setObject(1, entity.getId());
            ps.setString(2, entity.getResourceType());
            ps.setString(3, entity.getResourceId());
            ps.setString(4, entity.getFhirVersion());
            ps.setInt(5, entity.getVersionId());
            ps.setBoolean(6, entity.getIsCurrent());
            ps.setBoolean(7, entity.getIsDeleted());
            ps.setObject(8, entity.getContent(), Types.OTHER);  // JSONB/CLOB
            ps.setTimestamp(9, Timestamp.from(entity.getLastUpdated()));
            ps.setTimestamp(10, Timestamp.from(entity.getCreatedAt()));
            ps.setString(11, entity.getSourceUri());
            ps.setString(12, entity.getTenantId());
        });

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
     * Supports FHIR search parameters including:
     * - Token: system|code, |code, system|, code
     * - Quantity: [prefix]value|system|code
     * - Reference: [type]/[id], absolute URL, relative reference
     * - Composite: value1$value2
     * - Date: with comparison prefixes
     * - Number: with comparison prefixes
     * - String: with :exact, :contains, :missing modifiers
     * </p>
     */
    public Page<FhirResourceEntity> searchWithParams(
            String schemaName, String tenantId, String resourceType,
            Map<String, String> searchParams, Pageable pageable) {

        validateSchemaName(schemaName);
        log.debug("Searching {} in schema {} with params: {}", resourceType, schemaName, searchParams);

        // Build base query conditions
        StringBuilder whereClause = new StringBuilder(
                "WHERE tenant_id = ? AND resource_type = ? AND is_current = TRUE AND is_deleted = FALSE");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(resourceType);

        // Process search parameters
        for (Map.Entry<String, String> entry : searchParams.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();

            if (paramValue == null || paramValue.isBlank()) {
                continue;
            }

            // Skip special params that don't affect filtering
            if (SPECIAL_PARAMS.contains(paramName)) {
                continue;
            }

            // Build predicate for this parameter
            SqlPredicate predicate = buildParamPredicate(resourceType, paramName, paramValue);
            if (predicate != null && predicate.sql != null && !predicate.sql.isBlank()) {
                whereClause.append(" AND ").append(predicate.sql);
                params.addAll(predicate.params);
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

        log.debug("Search {} found {} results (total: {})", resourceType, results.size(), total);
        return new PageImpl<>(results, pageable, total);
    }

    // ==================== Predicate Building ====================

    /**
     * Builds a SQL predicate for a search parameter based on its type.
     */
    private SqlPredicate buildParamPredicate(String resourceType, String paramName, String paramValue) {
        // Handle _id specially
        if ("_id".equals(paramName)) {
            return buildIdPredicate(paramValue);
        }

        // Handle _lastUpdated specially (uses entity field, not JSONB)
        if ("_lastUpdated".equals(paramName)) {
            return buildLastUpdatedPredicate(paramValue);
        }

        // Strip modifiers for type detection
        String baseParamName = paramName.contains(":")
                ? paramName.substring(0, paramName.indexOf(':'))
                : paramName;

        // Try to get the parameter type from the registry
        SearchParamType paramType = getSearchParamType(resourceType, baseParamName);
        log.debug("Parameter {} has type {} for resource {}", baseParamName, paramType, resourceType);

        // Get the FHIRPath expression for the parameter
        String expression = getSearchParamExpression(resourceType, baseParamName);

        return switch (paramType) {
            case TOKEN -> buildTokenPredicate(paramName, paramValue, expression);
            case QUANTITY -> buildQuantityPredicate(paramName, paramValue, expression);
            case REFERENCE -> buildReferencePredicate(paramName, paramValue, expression);
            case COMPOSITE -> buildCompositePredicate(resourceType, paramName, paramValue);
            case DATE -> buildDatePredicate(paramName, paramValue, expression);
            case NUMBER -> buildNumberPredicate(paramName, paramValue, expression);
            case STRING -> buildStringPredicate(paramName, paramValue, expression);
            case URI -> buildUriPredicate(paramName, paramValue, expression);
            default -> buildStringPredicate(paramName, paramValue, expression);
        };
    }

    // ==================== Type Detection ====================

    /**
     * Gets the search parameter type from the registry or falls back to heuristics.
     */
    private SearchParamType getSearchParamType(String resourceType, String paramName) {
        // Try to get from registry (using R5 as default)
        Optional<SearchParamType> typeOpt = searchParameterRegistry.getSearchParameterType(
                FhirVersion.R5, resourceType, paramName);

        if (typeOpt.isPresent()) {
            return typeOpt.get();
        }

        // Fallback to hardcoded type detection
        if (DATE_PARAMS.contains(paramName)) {
            return SearchParamType.DATE;
        }
        if (NUMBER_PARAMS.contains(paramName)) {
            return SearchParamType.NUMBER;
        }

        // Default to string for unknown parameters
        return SearchParamType.STRING;
    }

    /**
     * Gets the search parameter FHIRPath expression from the registry.
     */
    private String getSearchParamExpression(String resourceType, String paramName) {
        return searchParameterRegistry.getSearchParameterExpression(
                        FhirVersion.R5, resourceType, paramName)
                .orElse(null);
    }

    // ==================== ID Predicate ====================

    private SqlPredicate buildIdPredicate(String value) {
        // Handle comma-separated IDs (OR)
        if (value.contains(",")) {
            String[] ids = value.split(",");
            StringBuilder sql = new StringBuilder("(");
            List<Object> params = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("resource_id = ?");
                params.add(ids[i].trim());
            }
            sql.append(")");
            return new SqlPredicate(sql.toString(), params);
        }
        return new SqlPredicate("resource_id = ?", List.of(value));
    }

    // ==================== Last Updated Predicate ====================

    private SqlPredicate buildLastUpdatedPredicate(String value) {
        ParsedValue parsed = parseValueWithPrefix(value);

        Instant instant = parseDate(parsed.value);
        if (instant == null) {
            log.warn("Invalid date format for _lastUpdated: {}", value);
            return null;
        }

        String sql = switch (parsed.prefix) {
            case "eq" -> "last_updated = ?";
            case "ne" -> "last_updated != ?";
            case "lt", "eb" -> "last_updated < ?";
            case "gt", "sa" -> "last_updated > ?";
            case "le" -> "last_updated <= ?";
            case "ge" -> "last_updated >= ?";
            default -> "last_updated = ?";
        };

        return new SqlPredicate(sql, List.of(Timestamp.from(instant)));
    }

    // ==================== Token Predicate ====================

    /**
     * Builds a predicate for token search parameters.
     * Supports formats: system|code, |code, system|, code
     */
    private SqlPredicate buildTokenPredicate(String paramName, String paramValue, String expression) {
        // Handle modifiers
        boolean exactMatch = paramName.endsWith(":exact");
        boolean textMatch = paramName.endsWith(":text");
        boolean notMatch = paramName.endsWith(":not");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        // Handle :missing modifier
        if (missingMatch) {
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            String sql = shouldBeMissing
                    ? String.format("jsonb_extract_path_text(content, %s) IS NULL", formatJsonPathArgs(jsonPath))
                    : String.format("jsonb_extract_path_text(content, %s) IS NOT NULL", formatJsonPathArgs(jsonPath));
            return new SqlPredicate(sql, List.of());
        }

        // Parse token value: system|code
        TokenValue token = parseTokenValue(paramValue);
        log.debug("Parsed token: system='{}', code='{}'", token.system, token.code);

        // Handle :text modifier - search on display/text fields
        if (textMatch) {
            String textPath = getTokenTextPath(jsonPath);
            String sql = String.format("LOWER(jsonb_extract_path_text(content, %s)) LIKE LOWER(?)",
                    formatJsonPathArgs(textPath));
            return new SqlPredicate(sql, List.of("%" + token.code + "%"));
        }

        // Build predicates based on what's provided
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        boolean isPrimitive = isPrimitiveCodePath(jsonPath);

        if (isPrimitive) {
            // Try primitive code matching (direct value match)
            if (token.code != null && !token.systemOnly) {
                // For primitive codes, system is ignored (they don't have systems)
                if (token.system == null || token.codeOnly) {
                    String sql = String.format("jsonb_extract_path_text(content, %s) = ?",
                            formatJsonPathArgs(jsonPath));
                    conditions.add(sql);
                    params.add(token.code);
                }
            }

            // Also try CodeableConcept pattern
            String codeableConceptPath = getCodeableConceptPath(jsonPath);
            if (!codeableConceptPath.equals(jsonPath)) {
                SqlPredicate ccPred = buildCodeableConceptCondition(codeableConceptPath, token);
                if (ccPred != null) {
                    conditions.add(ccPred.sql);
                    params.addAll(ccPred.params);
                }
            }
        } else {
            // Path already points to CodeableConcept/Coding structure
            SqlPredicate ccPred = buildCodeableConceptCondition(jsonPath, token);
            if (ccPred != null) {
                conditions.add(ccPred.sql);
                params.addAll(ccPred.params);
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        String sql = conditions.size() == 1
                ? conditions.get(0)
                : "(" + String.join(" OR ", conditions) + ")";

        // Handle :not modifier
        if (notMatch) {
            sql = "NOT (" + sql + ")";
        }

        return new SqlPredicate(sql, params);
    }

    /**
     * Builds a SQL condition for CodeableConcept/Coding types.
     */
    private SqlPredicate buildCodeableConceptCondition(String jsonPath, TokenValue token) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (token.system != null) {
            String systemPath = getTokenSystemPath(jsonPath);
            if (systemPath != null) {
                String sql = String.format("jsonb_extract_path_text(content, %s) = ?",
                        formatJsonPathArgs(systemPath));
                conditions.add(sql);
                params.add(token.system);
            }
        }

        if (token.code != null && !token.systemOnly) {
            String codePath = getTokenCodePath(jsonPath);
            String sql = String.format("jsonb_extract_path_text(content, %s) = ?",
                    formatJsonPathArgs(codePath));
            conditions.add(sql);
            params.add(token.code);
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return new SqlPredicate("(" + String.join(" AND ", conditions) + ")", params);
    }

    // ==================== Quantity Predicate ====================

    /**
     * Builds a predicate for quantity search parameters.
     * Supports format: [prefix]value|system|code
     */
    private SqlPredicate buildQuantityPredicate(String paramName, String paramValue, String expression) {
        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        // Parse quantity value: [prefix]value|system|code
        QuantityValue quantity = parseQuantityValue(paramValue);
        log.debug("Parsed quantity: prefix='{}', value={}, system='{}', code='{}'",
                quantity.prefix, quantity.value, quantity.system, quantity.code);

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        // Build value comparison
        if (quantity.value != null) {
            String valuePath = jsonPath + ",value";
            String valueExpr = String.format("CAST(COALESCE(jsonb_extract_path_text(content, %s), '0') AS DOUBLE PRECISION)",
                    formatJsonPathArgs(valuePath));

            String valueSql = switch (quantity.prefix) {
                case "eq" -> valueExpr + " = ?";
                case "ne" -> valueExpr + " != ?";
                case "lt" -> valueExpr + " < ?";
                case "gt" -> valueExpr + " > ?";
                case "le" -> valueExpr + " <= ?";
                case "ge" -> valueExpr + " >= ?";
                case "ap" -> valueExpr + " BETWEEN ? AND ?";
                default -> valueExpr + " = ?";
            };

            conditions.add(valueSql);
            if ("ap".equals(quantity.prefix)) {
                // Approximate: within 10%
                params.add(quantity.value * 0.9);
                params.add(quantity.value * 1.1);
            } else {
                params.add(quantity.value);
            }
        }

        // Build system comparison if specified
        if (quantity.system != null && !quantity.system.isEmpty()) {
            String systemPath = jsonPath + ",system";
            String sql = String.format("jsonb_extract_path_text(content, %s) = ?",
                    formatJsonPathArgs(systemPath));
            conditions.add(sql);
            params.add(quantity.system);
        }

        // Build code/unit comparison if specified
        if (quantity.code != null && !quantity.code.isEmpty()) {
            String codePath = jsonPath + ",code";
            String unitPath = jsonPath + ",unit";
            String sql = String.format("(jsonb_extract_path_text(content, %s) = ? OR jsonb_extract_path_text(content, %s) = ?)",
                    formatJsonPathArgs(codePath), formatJsonPathArgs(unitPath));
            conditions.add(sql);
            params.add(quantity.code);
            params.add(quantity.code);
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return new SqlPredicate("(" + String.join(" AND ", conditions) + ")", params);
    }

    // ==================== Reference Predicate ====================

    /**
     * Builds a predicate for reference search parameters.
     * Supports formats: [type]/[id], absolute URL, [id]
     */
    private SqlPredicate buildReferencePredicate(String paramName, String paramValue, String expression) {
        // Handle modifiers
        boolean identifierMatch = paramName.endsWith(":identifier");
        boolean missingMatch = paramName.endsWith(":missing");

        // Extract type modifier if present (e.g., subject:Patient)
        String typeModifier = null;
        String baseParamName = paramName;
        if (paramName.contains(":") && !identifierMatch && !missingMatch) {
            int colonIndex = paramName.indexOf(':');
            baseParamName = paramName.substring(0, colonIndex);
            String modifier = paramName.substring(colonIndex + 1);
            // Check if modifier is a resource type (starts with uppercase)
            if (!modifier.isEmpty() && Character.isUpperCase(modifier.charAt(0))) {
                typeModifier = modifier;
            }
        } else {
            baseParamName = stripModifier(paramName);
        }

        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        // Handle :missing modifier
        if (missingMatch) {
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            String sql = shouldBeMissing
                    ? String.format("jsonb_extract_path_text(content, %s) IS NULL", formatJsonPathArgs(jsonPath))
                    : String.format("jsonb_extract_path_text(content, %s) IS NOT NULL", formatJsonPathArgs(jsonPath));
            return new SqlPredicate(sql, List.of());
        }

        // Handle :identifier modifier - search on reference's identifier
        if (identifierMatch) {
            String identifierPath = jsonPath.replace(",reference", ",identifier");
            return buildTokenPredicate(baseParamName, paramValue, null);
        }

        // Parse reference value
        ReferenceValue ref = parseReferenceValue(paramValue, typeModifier);
        log.debug("Parsed reference: type='{}', id='{}', url='{}'", ref.type, ref.id, ref.url);

        String refPath = getReferencePath(jsonPath);
        String refExpr = String.format("jsonb_extract_path_text(content, %s)", formatJsonPathArgs(refPath));

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        // Match by full reference (type/id)
        if (ref.type != null && ref.id != null) {
            conditions.add(refExpr + " = ?");
            params.add(ref.type + "/" + ref.id);
            // Also match absolute URL patterns
            conditions.add(refExpr + " LIKE ?");
            params.add("%/" + ref.type + "/" + ref.id);
        } else if (ref.id != null) {
            // Match by ID only - could be at the end of any URL
            conditions.add(refExpr + " LIKE ?");
            params.add("%/" + ref.id);
            conditions.add(refExpr + " = ?");
            params.add(ref.id);
        } else if (ref.url != null) {
            // Match absolute URL
            conditions.add(refExpr + " = ?");
            params.add(ref.url);
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return new SqlPredicate("(" + String.join(" OR ", conditions) + ")", params);
    }

    // ==================== Date Predicate ====================

    /**
     * Builds a predicate for date search parameters.
     */
    private SqlPredicate buildDatePredicate(String paramName, String paramValue, String expression) {
        ParsedValue parsed = parseValueWithPrefix(paramValue);
        String jsonPath = mapParamToJsonPath(stripModifier(paramName), expression);

        if (jsonPath == null) {
            return null;
        }

        String jsonExpr = String.format("jsonb_extract_path_text(content, %s)", formatJsonPathArgs(jsonPath));
        String dateStr = parsed.value;

        String sql = switch (parsed.prefix) {
            case "eq" -> jsonExpr + " = ?";
            case "ne" -> jsonExpr + " != ?";
            case "lt", "eb" -> jsonExpr + " < ?";
            case "gt", "sa" -> jsonExpr + " > ?";
            case "le" -> jsonExpr + " <= ?";
            case "ge" -> jsonExpr + " >= ?";
            case "ap" -> jsonExpr + " LIKE ?";
            default -> jsonExpr + " = ?";
        };

        Object param = "ap".equals(parsed.prefix) ? dateStr + "%" : dateStr;
        return new SqlPredicate(sql, List.of(param));
    }

    // ==================== Number Predicate ====================

    /**
     * Builds a predicate for number search parameters.
     */
    private SqlPredicate buildNumberPredicate(String paramName, String paramValue, String expression) {
        ParsedValue parsed = parseValueWithPrefix(paramValue);
        String jsonPath = mapParamToJsonPath(stripModifier(paramName), expression);

        if (jsonPath == null) {
            return null;
        }

        try {
            double numValue = Double.parseDouble(parsed.value);

            String jsonExpr = String.format(
                    "CAST(COALESCE(jsonb_extract_path_text(content, %s), '0') AS DOUBLE PRECISION)",
                    formatJsonPathArgs(jsonPath));

            String sql = switch (parsed.prefix) {
                case "eq" -> jsonExpr + " = ?";
                case "ne" -> jsonExpr + " != ?";
                case "lt" -> jsonExpr + " < ?";
                case "gt" -> jsonExpr + " > ?";
                case "le" -> jsonExpr + " <= ?";
                case "ge" -> jsonExpr + " >= ?";
                default -> jsonExpr + " = ?";
            };

            return new SqlPredicate(sql, List.of(numValue));
        } catch (NumberFormatException e) {
            log.warn("Invalid number format for {}: {}", paramName, paramValue);
            return null;
        }
    }

    // ==================== String Predicate ====================

    /**
     * Builds a predicate for string search parameters.
     */
    private SqlPredicate buildStringPredicate(String paramName, String paramValue, String expression) {
        boolean exactMatch = paramName.endsWith(":exact");
        boolean containsMatch = paramName.endsWith(":contains");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        // Handle :missing modifier
        if (missingMatch) {
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            String sql = shouldBeMissing
                    ? String.format("jsonb_extract_path_text(content, %s) IS NULL", formatJsonPathArgs(jsonPath))
                    : String.format("jsonb_extract_path_text(content, %s) IS NOT NULL", formatJsonPathArgs(jsonPath));
            return new SqlPredicate(sql, List.of());
        }

        // Special handling for HumanName searches
        if (isHumanNameSearch(baseParamName)) {
            return buildHumanNamePredicate(baseParamName, paramValue, exactMatch, containsMatch);
        }

        String jsonExpr = String.format("jsonb_extract_path_text(content, %s)", formatJsonPathArgs(jsonPath));

        if (exactMatch) {
            return new SqlPredicate(jsonExpr + " = ?", List.of(paramValue));
        }

        if (containsMatch) {
            return new SqlPredicate("LOWER(" + jsonExpr + ") LIKE LOWER(?)",
                    List.of("%" + paramValue + "%"));
        }

        // Default: case-insensitive prefix match
        return new SqlPredicate("LOWER(" + jsonExpr + ") LIKE LOWER(?)",
                List.of(paramValue + "%"));
    }

    // ==================== URI Predicate ====================

    /**
     * Builds a predicate for URI search parameters.
     */
    private SqlPredicate buildUriPredicate(String paramName, String paramValue, String expression) {
        boolean aboveMatch = paramName.endsWith(":above");
        boolean belowMatch = paramName.endsWith(":below");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            return null;
        }

        String jsonExpr = String.format("jsonb_extract_path_text(content, %s)", formatJsonPathArgs(jsonPath));

        if (missingMatch) {
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            String sql = shouldBeMissing ? jsonExpr + " IS NULL" : jsonExpr + " IS NOT NULL";
            return new SqlPredicate(sql, List.of());
        }

        if (belowMatch) {
            // Match URIs that start with the parameter value
            return new SqlPredicate(jsonExpr + " LIKE ?", List.of(paramValue + "%"));
        }

        if (aboveMatch) {
            // Match URIs that are prefixes of the parameter value
            return new SqlPredicate("? LIKE " + jsonExpr + " || '%'", List.of(paramValue));
        }

        // Exact match
        return new SqlPredicate(jsonExpr + " = ?", List.of(paramValue));
    }

    // ==================== Composite Predicate ====================

    /**
     * Builds a predicate for composite search parameters.
     * Supports format: value1$value2
     */
    private SqlPredicate buildCompositePredicate(String resourceType, String paramName, String paramValue) {
        String baseParamName = stripModifier(paramName);

        // Get the composite search parameter definition
        Optional<SearchParameter> spOpt = searchParameterRegistry.getSearchParameter(
                FhirVersion.R5, resourceType, baseParamName);

        if (spOpt.isEmpty()) {
            log.warn("Composite search parameter not found: {}", paramName);
            return null;
        }

        SearchParameter sp = spOpt.get();

        // Get component search parameters
        List<SearchParameter.SearchParameterComponentComponent> components = sp.getComponent();
        if (components == null || components.isEmpty()) {
            log.warn("Composite search parameter has no components: {}", paramName);
            return null;
        }

        // Split the composite value by $
        String[] values = paramValue.split("\\$");

        if (values.length != components.size()) {
            log.warn("Composite value count {} doesn't match component count {} for {}",
                    values.length, components.size(), paramName);
            return null;
        }

        // Build predicates for each component
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < components.size(); i++) {
            SearchParameter.SearchParameterComponentComponent component = components.get(i);
            String componentValue = values[i];

            // Get the component's definition reference
            String componentParamRef = component.getDefinition();
            // Extract param name from reference (e.g., "SearchParameter/code" -> "code")
            String componentParamName = componentParamRef.substring(componentParamRef.lastIndexOf('/') + 1);
            if (componentParamName.contains("-")) {
                // Extract just the param name from "Resource-paramName"
                componentParamName = componentParamName.substring(componentParamName.lastIndexOf('-') + 1);
            }

            // Build predicate for this component
            SqlPredicate componentPred = buildParamPredicate(resourceType, componentParamName, componentValue);
            if (componentPred != null) {
                conditions.add(componentPred.sql);
                params.addAll(componentPred.params);
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return new SqlPredicate("(" + String.join(" AND ", conditions) + ")", params);
    }

    // ==================== HumanName Predicate ====================

    /**
     * Checks if a parameter is a HumanName search.
     */
    private boolean isHumanNameSearch(String paramName) {
        return "name".equals(paramName) ||
               "family".equals(paramName) ||
               "given".equals(paramName);
    }

    /**
     * Builds a predicate that searches across HumanName fields.
     */
    private SqlPredicate buildHumanNamePredicate(String paramName, String paramValue,
            boolean exactMatch, boolean containsMatch) {

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String pattern = containsMatch ? "%" + paramValue + "%" : paramValue + "%";

        if ("name".equals(paramName)) {
            // Search in multiple fields
            String[] paths = {"'name','0','text'", "'name','0','family'", "'name','0','given','0'",
                    "'name','0','prefix','0'", "'name','0','suffix','0'"};

            for (String path : paths) {
                if (exactMatch) {
                    conditions.add(String.format("jsonb_extract_path_text(content, %s) = ?", path));
                    params.add(paramValue);
                } else {
                    conditions.add(String.format("LOWER(jsonb_extract_path_text(content, %s)) LIKE LOWER(?)", path));
                    params.add(pattern);
                }
            }
        } else if ("family".equals(paramName)) {
            String path = "'name','0','family'";
            if (exactMatch) {
                conditions.add(String.format("jsonb_extract_path_text(content, %s) = ?", path));
                params.add(paramValue);
            } else {
                conditions.add(String.format("LOWER(jsonb_extract_path_text(content, %s)) LIKE LOWER(?)", path));
                params.add(pattern);
            }
        } else if ("given".equals(paramName)) {
            // Search first 2 given names
            for (int i = 0; i < 2; i++) {
                String path = String.format("'name','0','given','%d'", i);
                if (exactMatch) {
                    conditions.add(String.format("jsonb_extract_path_text(content, %s) = ?", path));
                    params.add(paramValue);
                } else {
                    conditions.add(String.format("LOWER(jsonb_extract_path_text(content, %s)) LIKE LOWER(?)", path));
                    params.add(pattern);
                }
            }
        }

        if (conditions.isEmpty()) {
            return null;
        }

        return new SqlPredicate("(" + String.join(" OR ", conditions) + ")", params);
    }

    // ==================== JSON Path Mapping ====================

    /**
     * Maps a search parameter to its JSON path.
     * Uses FHIRPath expression if available, otherwise falls back to hardcoded mappings.
     */
    private String mapParamToJsonPath(String paramName, String expression) {
        // If we have an expression, try to convert it to a JSON path
        if (expression != null && !expression.isEmpty()) {
            log.debug("Mapping parameter '{}' with expression '{}'", paramName, expression);

            // Split by pipe (|) to get individual paths for different resource types
            String[] pathOptions = expression.split("\\|");

            // Use the first path option
            String firstPath = pathOptions[0].trim();

            // Remove the resource type prefix (e.g., "Account." -> "")
            String pathWithoutResourceType = removeResourceTypePrefix(firstPath);

            // Resolve FHIRPath expression to JSON path
            String resolvedPath = resolveFhirExpression(pathWithoutResourceType);

            // For reference types, append ',reference' to get the reference URL
            if (isReferenceParameter(paramName, resolvedPath)) {
                resolvedPath = resolvedPath + ",reference";
            }

            return resolvedPath;
        }

        log.debug("No FHIRPath expression found for parameter '{}', using hardcoded mapping", paramName);

        // Fallback to hardcoded mappings
        return switch (paramName) {
            // Patient parameters
            case "identifier" -> "identifier,0,value";
            case "name" -> "name,0,text";
            case "family" -> "name,0,family";
            case "given" -> "name,0,given,0";
            case "gender" -> "gender";
            case "birthdate", "birthDate" -> "birthDate";
            case "address" -> "address,0,text";
            case "address-city" -> "address,0,city";
            case "address-state" -> "address,0,state";
            case "address-postalcode" -> "address,0,postalCode";
            case "address-country" -> "address,0,country";
            case "telecom" -> "telecom,0,value";
            case "phone" -> "telecom,0,value";
            case "email" -> "telecom,0,value";
            case "active" -> "active";

            // Common resource parameters
            case "status" -> "status";
            case "code" -> "code,coding,0,code";
            case "subject" -> "subject,reference";
            case "patient" -> "patient,reference";
            case "encounter" -> "encounter,reference";
            case "date" -> "date";
            case "authored" -> "authored";
            case "issued" -> "issued";
            case "effective" -> "effectiveDateTime";
            case "category" -> "category,0,coding,0,code";
            case "type" -> "type,coding,0,code";
            case "performer" -> "performer,0,reference";
            case "organization" -> "organization,reference";
            case "location" -> "location,reference";
            case "practitioner" -> "practitioner,reference";

            // Observation-specific
            case "value-quantity" -> "valueQuantity";
            case "value-concept" -> "valueCodeableConcept,coding,0,code";
            case "value-string" -> "valueString";

            // Condition-specific
            case "clinical-status" -> "clinicalStatus,coding,0,code";
            case "verification-status" -> "verificationStatus,coding,0,code";
            case "severity" -> "severity,coding,0,code";
            case "onset-date" -> "onsetDateTime";
            case "abatement-date" -> "abatementDateTime";

            default -> paramName;
        };
    }

    // ==================== FHIRPath Resolution ====================

    /**
     * Removes resource type prefix from FHIRPath expression.
     */
    private String removeResourceTypePrefix(String fhirPath) {
        if (fhirPath == null || fhirPath.isEmpty()) {
            return fhirPath;
        }

        String[] parts = fhirPath.split("\\.", 2);

        if (parts.length == 2) {
            String firstPart = parts[0].trim();
            if (!firstPart.isEmpty() && Character.isUpperCase(firstPart.charAt(0))) {
                return parts[1];
            }
        }

        return fhirPath;
    }

    /**
     * Resolves a FHIRPath expression to a JSON path format.
     */
    private String resolveFhirExpression(String fhirPath) {
        if (fhirPath == null || fhirPath.isEmpty()) {
            return null;
        }

        String path = fhirPath.trim();

        // Remove outer parentheses
        path = stripOuterParentheses(path);

        // Handle FHIRPath "as" operator
        path = resolveAsOperator(path);

        // Handle .ofType() for polymorphic elements
        path = resolveOfType(path);

        // Remove FHIRPath filters like .where(...)
        path = removeWhereFilters(path);

        // Remove .resolve() calls
        path = path.replaceAll("\\.resolve\\(\\)", "");

        // Remove type checking: "is ResourceType"
        path = path.replaceAll("\\s+is\\s+\\w+", "");

        // Remove function calls
        path = removeFunctionCalls(path);

        // Remove trailing dots
        path = path.replaceAll("\\.+$", "");

        path = path.trim();

        // Convert dot notation to comma-separated for JSONB path
        path = path.replace(".", ",");

        // Expand common patterns
        path = expandCommonPatterns(path);

        return path.isEmpty() ? null : path;
    }

    private String stripOuterParentheses(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String resolveAsOperator(String path) {
        Pattern asPattern = Pattern.compile("([\\w.]+)\\s+as\\s+(\\w+)");
        Matcher asMatcher = asPattern.matcher(path);

        if (asMatcher.find()) {
            String fullPath = asMatcher.group(1);
            String typeName = asMatcher.group(2);

            String elementName = fullPath.contains(".")
                    ? fullPath.substring(fullPath.lastIndexOf('.') + 1)
                    : fullPath;

            String capitalizedType = capitalizeFirstLetter(typeName);
            String polymorphicName = elementName + capitalizedType;

            return asMatcher.replaceFirst(polymorphicName);
        }

        return path;
    }

    private String resolveOfType(String path) {
        Pattern ofTypePattern = Pattern.compile("(\\w+)\\.ofType\\(([^)]+)\\)");
        Matcher ofTypeMatcher = ofTypePattern.matcher(path);

        if (ofTypeMatcher.find()) {
            String elementName = ofTypeMatcher.group(1);
            String typeName = ofTypeMatcher.group(2).trim();

            String capitalizedType = capitalizeFirstLetter(typeName);
            String polymorphicName = elementName + capitalizedType;

            return ofTypeMatcher.replaceFirst(polymorphicName);
        }

        return path;
    }

    private String removeWhereFilters(String path) {
        String result = path.replaceAll("\\.where\\([^()]*(?:\\([^()]*\\)[^()]*)*\\)", "");

        while (result.contains(".where(")) {
            int whereIndex = result.indexOf(".where(");
            if (whereIndex == -1) break;

            int parenCount = 0;
            int endIndex = whereIndex + 7;

            for (; endIndex < result.length(); endIndex++) {
                char c = result.charAt(endIndex);
                if (c == '(') parenCount++;
                else if (c == ')') {
                    if (parenCount == 0) {
                        result = result.substring(0, whereIndex) + result.substring(endIndex + 1);
                        break;
                    }
                    parenCount--;
                }
            }

            if (endIndex >= result.length()) break;
        }

        return result;
    }

    private String removeFunctionCalls(String path) {
        return path.replaceAll("\\.(?:exists|first|last|tail|skip|take|single|empty|count|distinct|allTrue|anyTrue|allFalse|anyFalse)\\([^)]*\\)", "");
    }

    private String expandCommonPatterns(String path) {
        // Handle nested code paths
        if (path.endsWith(",concept") && !path.contains(",coding,")) {
            return path.replace(",concept", ",concept,coding,0,code");
        }

        if (path.equals("name")) {
            return "name,0,text";
        }

        if (path.equals("identifier")) {
            return "identifier,0,value";
        }

        if (path.equals("address")) {
            return "address,0";
        }

        if (path.equals("telecom")) {
            return "telecom,0,value";
        }

        return path;
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private boolean isReferenceParameter(String paramName, String path) {
        Set<String> referenceParams = Set.of(
            "patient", "subject", "encounter", "performer",
            "requester", "participant", "actor", "beneficiary",
            "candidate", "link", "target", "location", "organization",
            "practitioner", "recorder", "asserter", "managingOrganization"
        );
        return referenceParams.contains(paramName);
    }

    // ==================== Token Path Helpers ====================

    private boolean isPrimitiveCodePath(String basePath) {
        if (basePath.contains(",coding,")) {
            return false;
        }
        if (basePath.endsWith(",value")) {
            return false;
        }
        return true;
    }

    private String getTokenSystemPath(String basePath) {
        if (isPrimitiveCodePath(basePath)) {
            return null;
        }
        if (basePath.endsWith(",code")) {
            return basePath.replace(",code", ",system");
        }
        if (basePath.contains(",coding,")) {
            return basePath.replace(",code", ",system");
        }
        if (basePath.endsWith(",value")) {
            return basePath.replace(",value", ",system");
        }
        return basePath + ",system";
    }

    private String getTokenCodePath(String basePath) {
        return basePath;
    }

    private String getCodeableConceptPath(String basePath) {
        if (basePath.contains(",coding,")) {
            return basePath;
        }
        if (!basePath.contains(",") || basePath.matches("^[a-zA-Z]+$")) {
            return basePath + ",coding,0,code";
        }
        if (basePath.matches(".*,\\d+$")) {
            return basePath + ",coding,0,code";
        }
        return basePath;
    }

    private String getTokenTextPath(String basePath) {
        if (basePath.contains(",coding,")) {
            return basePath.replace(",code", ",display");
        }
        return basePath.replace(",code", ",text").replace(",value", ",text");
    }

    private String getReferencePath(String basePath) {
        if (basePath.endsWith(",reference")) {
            return basePath;
        }
        return basePath + ",reference";
    }

    // ==================== Parsing Helpers ====================

    /**
     * Parses a token value in the format: [system]|[code]
     */
    private TokenValue parseTokenValue(String value) {
        if (value == null || value.isEmpty()) {
            return new TokenValue(null, null, false, false);
        }

        String processedValue = value.replace("\\|", "\u0000");

        if (processedValue.contains("|")) {
            int pipeIndex = processedValue.indexOf('|');
            String system = processedValue.substring(0, pipeIndex).replace("\u0000", "|");
            String code = processedValue.substring(pipeIndex + 1).replace("\u0000", "|");

            boolean codeOnly = system.isEmpty();
            boolean systemOnly = code.isEmpty();

            return new TokenValue(
                    system.isEmpty() ? null : system,
                    code.isEmpty() ? null : code,
                    codeOnly,
                    systemOnly
            );
        }

        return new TokenValue(null, processedValue.replace("\u0000", "|"), false, false);
    }

    /**
     * Parses a quantity value in the format: [prefix]value|system|code
     */
    private QuantityValue parseQuantityValue(String value) {
        if (value == null || value.isEmpty()) {
            return new QuantityValue("eq", null, null, null);
        }

        String prefix = "eq";
        String remaining = value;

        Matcher matcher = PREFIX_PATTERN.matcher(value);
        if (matcher.matches()) {
            prefix = matcher.group(1);
            remaining = matcher.group(2);
        }

        String[] parts = remaining.split("\\|", -1);

        Double numValue = null;
        String system = null;
        String code = null;

        if (parts.length >= 1 && !parts[0].isEmpty()) {
            try {
                numValue = Double.parseDouble(parts[0]);
            } catch (NumberFormatException e) {
                log.warn("Invalid quantity value: {}", parts[0]);
            }
        }

        if (parts.length >= 2) {
            system = parts[1].isEmpty() ? null : parts[1];
        }

        if (parts.length >= 3) {
            code = parts[2].isEmpty() ? null : parts[2];
        }

        return new QuantityValue(prefix, numValue, system, code);
    }

    /**
     * Parses a reference value in various formats.
     */
    private ReferenceValue parseReferenceValue(String value, String typeModifier) {
        if (value == null || value.isEmpty()) {
            return new ReferenceValue(null, null, null);
        }

        // Check if it's an absolute URL
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("urn:")) {
            String[] urlParts = value.split("/");
            if (urlParts.length >= 2) {
                String lastPart = urlParts[urlParts.length - 1];
                String secondLastPart = urlParts[urlParts.length - 2];
                if (Character.isUpperCase(secondLastPart.charAt(0))) {
                    return new ReferenceValue(secondLastPart, lastPart, value);
                }
            }
            return new ReferenceValue(null, null, value);
        }

        // Check if it's type/id format
        if (value.contains("/")) {
            String[] parts = value.split("/", 2);
            return new ReferenceValue(parts[0], parts[1], null);
        }

        // Just an ID - use type modifier if available
        return new ReferenceValue(typeModifier, value, null);
    }

    private ParsedValue parseValueWithPrefix(String value) {
        Matcher matcher = PREFIX_PATTERN.matcher(value);
        if (matcher.matches()) {
            return new ParsedValue(matcher.group(1), matcher.group(2));
        }
        return new ParsedValue("eq", value);
    }

    private String stripModifier(String paramName) {
        int colonIndex = paramName.indexOf(':');
        return colonIndex > 0 ? paramName.substring(0, colonIndex) : paramName;
    }

    private Instant parseDate(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            // Continue
        }

        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (DateTimeParseException e) {
            // Continue
        }

        try {
            return LocalDateTime.parse(dateStr).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // Continue
        }

        try {
            return LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // Continue
        }

        try {
            YearMonth ym = YearMonth.parse(dateStr);
            return ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // Continue
        }

        try {
            Year year = Year.parse(dateStr);
            return year.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Formats a comma-separated JSON path as SQL arguments.
     * Converts "name,0,family" to "'name','0','family'"
     */
    private String formatJsonPathArgs(String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            return "''";
        }
        String[] parts = jsonPath.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("'").append(parts[i].trim()).append("'");
        }
        return sb.toString();
    }

    // ==================== Validation ====================

    /**
     * Validates schema name to prevent SQL injection.
     */
    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
    }

    // ==================== Record Types ====================

    private record TokenValue(String system, String code, boolean codeOnly, boolean systemOnly) {}
    private record QuantityValue(String prefix, Double value, String system, String code) {}
    private record ReferenceValue(String type, String id, String url) {}
    private record ParsedValue(String prefix, String value) {}
    private record SqlPredicate(String sql, List<Object> params) {}

    // ==================== Row Mapper ====================

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
