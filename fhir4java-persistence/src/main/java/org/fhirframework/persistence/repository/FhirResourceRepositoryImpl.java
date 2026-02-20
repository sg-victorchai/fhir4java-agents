package org.fhirframework.persistence.repository;

import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.entity.FhirResourceEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.hl7.fhir.r5.model.Enumerations.SearchParamType;
import org.hl7.fhir.r5.model.SearchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom repository implementation for FHIR resource search with dynamic query building.
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
@Repository
public class FhirResourceRepositoryImpl implements FhirResourceRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceRepositoryImpl.class);

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

    @PersistenceContext
    private EntityManager entityManager;

    private final SearchParameterRegistry searchParameterRegistry;

    public FhirResourceRepositoryImpl(SearchParameterRegistry searchParameterRegistry) {
        this.searchParameterRegistry = searchParameterRegistry;
    }

    @Override
    public Page<FhirResourceEntity> searchWithParams(
            String tenantId,
            String resourceType,
            Map<String, String> searchParams,
            Pageable pageable) {

        log.debug("Searching {} with params: {}", resourceType, searchParams);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Build count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<FhirResourceEntity> countRoot = countQuery.from(FhirResourceEntity.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(cb, countRoot, tenantId, resourceType, searchParams));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        // Build data query
        CriteriaQuery<FhirResourceEntity> dataQuery = cb.createQuery(FhirResourceEntity.class);
        Root<FhirResourceEntity> dataRoot = dataQuery.from(FhirResourceEntity.class);
        dataQuery.select(dataRoot);
        dataQuery.where(buildPredicates(cb, dataRoot, tenantId, resourceType, searchParams));
        dataQuery.orderBy(cb.desc(dataRoot.get("lastUpdated")));

        TypedQuery<FhirResourceEntity> typedQuery = entityManager.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<FhirResourceEntity> results = typedQuery.getResultList();

        log.debug("Search {} found {} results (total: {})", resourceType, results.size(), total);

        return new PageImpl<>(results, pageable, total);
    }

    private Predicate[] buildPredicates(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String tenantId,
            String resourceType,
            Map<String, String> searchParams) {

        List<Predicate> predicates = new ArrayList<>();

        // Always filter by tenant, resource type, current, and not deleted
        predicates.add(cb.equal(root.get("tenantId"), tenantId));
        predicates.add(cb.equal(root.get("resourceType"), resourceType));
        predicates.add(cb.isTrue(root.get("isCurrent")));
        predicates.add(cb.isFalse(root.get("isDeleted")));

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

            Predicate predicate = buildParamPredicate(cb, root, resourceType, paramName, paramValue);
            if (predicate != null) {
                predicates.add(predicate);
            }
        }

        return predicates.toArray(new Predicate[0]);
    }

    private Predicate buildParamPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String resourceType,
            String paramName,
            String paramValue) {

        // Handle _id specially
        if ("_id".equals(paramName)) {
            return buildIdPredicate(cb, root, paramValue);
        }

        // Handle _lastUpdated specially (uses entity field, not JSONB)
        if ("_lastUpdated".equals(paramName)) {
            return buildLastUpdatedPredicate(cb, root, paramValue);
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
            case TOKEN -> buildTokenPredicate(cb, root, paramName, paramValue, expression);
            case QUANTITY -> buildQuantityPredicate(cb, root, paramName, paramValue, expression);
            case REFERENCE -> buildReferencePredicate(cb, root, paramName, paramValue, expression);
            case COMPOSITE -> buildCompositePredicate(cb, root, resourceType, paramName, paramValue);
            case DATE -> buildJsonbDatePredicate(cb, root, paramName, paramValue,expression);
            case NUMBER -> buildJsonbNumberPredicate(cb, root, paramName, paramValue,expression);
            case STRING -> buildJsonbStringPredicate(cb, root, paramName, paramValue,expression);
            case URI -> buildUriPredicate(cb, root, paramName, paramValue, expression);
            default -> buildJsonbStringPredicate(cb, root, paramName, paramValue,expression);
        };
    }

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

    // ==================== Token Search ====================

    /**
     * Builds a predicate for token search parameters.
     * Supports formats: system|code, |code, system|, code
     */
    private Predicate buildTokenPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {
    	
    	//log.debug("Building token predicate for param: {}, value: {}, expression: {}", paramName, paramValue, expression);

        // Handle modifiers
        boolean exactMatch = paramName.endsWith(":exact");
        boolean textMatch = paramName.endsWith(":text");
        boolean notMatch = paramName.endsWith(":not");
        boolean ofTypeMatch = paramName.endsWith(":of-type");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        Expression<String> contentExpr = root.get("content");

        // Handle :missing modifier
        if (missingMatch) {
            Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, jsonPath);
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            return shouldBeMissing ? cb.isNull(jsonValue) : cb.isNotNull(jsonValue);
        }

        // Parse token value: system|code
        TokenValue token = parseTokenValue(paramValue);
        log.debug("Parsed token: system='{}', code='{}'", token.system, token.code);

        // Handle :text modifier - search on display/text fields
        if (textMatch) {
            String textPath = getTokenTextPath(jsonPath);
            Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, textPath);
            return cb.like(cb.lower(jsonValue), "%" + token.code.toLowerCase() + "%");
        }

        // Build predicates based on what's provided
        // We need to handle both primitive code types and CodeableConcept/Coding types
        List<Predicate> orPredicates = new ArrayList<>();

        // Check if this is potentially a primitive code path
        boolean isPrimitive = isPrimitiveCodePath(jsonPath);

        if (isPrimitive) {
            // Try primitive code matching first (direct value match)
            Predicate primitivePredicate = buildPrimitiveCodePredicate(cb, contentExpr, jsonPath, token, exactMatch);
            if (primitivePredicate != null) {
                orPredicates.add(primitivePredicate);
            }

            // Also try CodeableConcept pattern in case the element is actually a CodeableConcept
            String codeableConceptPath = getCodeableConceptPath(jsonPath);
            if (!codeableConceptPath.equals(jsonPath)) {
                Predicate codeableConceptPredicate = buildCodeableConceptPredicate(cb, contentExpr, codeableConceptPath, token, exactMatch);
                if (codeableConceptPredicate != null) {
                    orPredicates.add(codeableConceptPredicate);
                }
            }
        } else {
            // Path already points to CodeableConcept/Coding structure
            Predicate complexPredicate = buildCodeableConceptPredicate(cb, contentExpr, jsonPath, token, exactMatch);
            if (complexPredicate != null) {
                orPredicates.add(complexPredicate);
            }
        }

        if (orPredicates.isEmpty()) {
            return null;
        }

        // Use OR to match either primitive code or CodeableConcept
        Predicate result = orPredicates.size() == 1
            ? orPredicates.get(0)
            : cb.or(orPredicates.toArray(new Predicate[0]));

        // Handle :not modifier
        if (notMatch) {
            return cb.not(result);
        }

        return result;
    }

    /**
     * Parses a token value in the format: [system]|[code]
     */
    private TokenValue parseTokenValue(String value) {
        if (value == null || value.isEmpty()) {
            return new TokenValue(null, null, false, false);
        }

        // Handle escaped pipe
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

        // No pipe - code only
        return new TokenValue(null, processedValue.replace("\u0000", "|"), false, false);
    }

    private record TokenValue(String system, String code, boolean codeOnly, boolean systemOnly) {}

    /**
     * Builds a predicate for primitive code types (direct string value).
     * Primitive codes don't have system - they're just simple string values.
     * Handles both scalar values and arrays of primitive codes.
     */
    private Predicate buildPrimitiveCodePredicate(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String jsonPath,
            TokenValue token,
            boolean exactMatch) {

        // For primitive codes, we can only match the code value (no system)
        if (token.code == null || token.systemOnly) {
            // If only system is specified, primitive codes can't match
            return null;
        }

        // If system is specified in the query (system|code format), primitive codes
        // only match if system is empty (|code format meaning "no system")
        if (token.system != null && !token.codeOnly) {
            // System was explicitly specified - primitive codes don't have systems
            // so they can only match if using |code format (codeOnly=true)
            return null;
        }

        // Try multiple matching strategies for primitive codes:
        // 1. Direct scalar match: jsonPath = 'value'
        // 2. First array element match: jsonPath,0 = 'value'
        // 3. JSONB array contains: jsonPath @> '["value"]' (for arrays)

        List<Predicate> matchStrategies = new ArrayList<>();

        // Strategy 1: Direct scalar match
        Expression<String> scalarExpr = buildJsonPathExpression(cb, contentExpr, jsonPath);
        matchStrategies.add(cb.equal(scalarExpr, token.code));

        // Strategy 2: First array element match (for arrays of primitive codes)
        String arrayPath = jsonPath + ",0";
        Expression<String> arrayFirstExpr = buildJsonPathExpression(cb, contentExpr, arrayPath);
        matchStrategies.add(cb.equal(arrayFirstExpr, token.code));

        // Strategy 3: JSONB array contains (handles any position in the array)
        // Using PostgreSQL's @> operator: content->'path' @> '"value"'
        Expression<Boolean> arrayContains = buildJsonArrayContainsPredicate(cb, contentExpr, jsonPath, token.code);
        if (arrayContains != null) {
            matchStrategies.add(cb.isTrue(arrayContains));
        }

        return cb.or(matchStrategies.toArray(new Predicate[0]));
    }

    /**
     * Builds a predicate that checks if a JSONB array contains a specific value.
     * Uses PostgreSQL's @> operator.
     */
    private Expression<Boolean> buildJsonArrayContainsPredicate(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String jsonPath,
            String value) {
        try {
            // Build: content->'path' @> '["value"]'::jsonb
            // We use the jsonb_contains function which is equivalent to @>
            String[] pathParts = jsonPath.split(",");

            // Build the path navigation: content->'part1'->'part2'...
            // We need to use -> (not ->>) to keep it as JSONB for the @> operator
            Expression<?>[] pathArgs = new Expression<?>[pathParts.length + 1];
            pathArgs[0] = contentExpr;
            for (int i = 0; i < pathParts.length; i++) {
                pathArgs[i + 1] = cb.literal(pathParts[i].trim());
            }

            // Use jsonb_extract_path to get the array as JSONB
            Expression<String> jsonbPath = cb.function("jsonb_extract_path", String.class, pathArgs);

            // Create the value to search for as a JSONB array element
            // We check both as a direct element and as a quoted string in the array
            String jsonValue = "\"" + value.replace("\"", "\\\"") + "\"";

            // Use the @> operator via the jsonb_contains function
            // jsonb_contains(array, '"value"') returns true if array contains the value
            return cb.function("jsonb_contains",
                    Boolean.class,
                    cb.function("coalesce", String.class, jsonbPath, cb.literal("[]")),
                    cb.literal(jsonValue));
        } catch (Exception e) {
            log.debug("Failed to build array contains predicate: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds a predicate for CodeableConcept/Coding types.
     * These have .coding array with system and code fields.
     * Handles both single CodeableConcept and arrays of CodeableConcept.
     */
    private Predicate buildCodeableConceptPredicate(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String jsonPath,
            TokenValue token,
            boolean exactMatch) {

        // Try both direct path and array path (for arrays of CodeableConcept)
        List<Predicate> orPredicates = new ArrayList<>();

        // Try direct path (single CodeableConcept or already array-indexed path)
        Predicate directMatch = buildSingleCodeableConceptPredicate(cb, contentExpr, jsonPath, token);
        if (directMatch != null) {
            orPredicates.add(directMatch);
        }

        // If path doesn't already have array index, also try with array index
        if (!jsonPath.matches(".*,\\d+,.*")) {
            String arrayPath = getCodeableConceptArrayPath(jsonPath.replace(",coding,0,code", ""));
            if (!arrayPath.equals(jsonPath)) {
                Predicate arrayMatch = buildSingleCodeableConceptPredicate(cb, contentExpr, arrayPath, token);
                if (arrayMatch != null) {
                    orPredicates.add(arrayMatch);
                }
            }
        }

        if (orPredicates.isEmpty()) {
            return null;
        }

        return orPredicates.size() == 1
            ? orPredicates.get(0)
            : cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Builds a predicate for a single CodeableConcept path (not handling arrays).
     */
    private Predicate buildSingleCodeableConceptPredicate(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String jsonPath,
            TokenValue token) {

        List<Predicate> predicates = new ArrayList<>();

        if (token.system != null) {
            // System was specified (either "system|code" or "system|")
            String systemPath = getTokenSystemPath(jsonPath);
            if (systemPath != null) {
                Expression<String> systemExpr = buildJsonPathExpression(cb, contentExpr, systemPath);
                if (token.systemOnly) {
                    // Only system specified: system|
                    predicates.add(cb.equal(systemExpr, token.system));
                } else {
                    // Both system and code specified
                    predicates.add(cb.equal(systemExpr, token.system));
                }
            }
        }

        if (token.code != null && !token.systemOnly) {
            // Code was specified
            String codePath = getTokenCodePath(jsonPath);
            Expression<String> codeExpr = buildJsonPathExpression(cb, contentExpr, codePath);
            predicates.add(cb.equal(codeExpr, token.code));
        }

        if (predicates.isEmpty()) {
            return null;
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    // ==================== Quantity Search ====================

    /**
     * Builds a predicate for quantity search parameters.
     * Supports format: [prefix]value|system|code
     */
    private Predicate buildQuantityPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

    	log.debug("Building quantity predicate for param: {}, value: {}, expression: {}", paramName, paramValue, expression);
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

        Expression<String> contentExpr = root.get("content");
        List<Predicate> predicates = new ArrayList<>();

        // Build value comparison
        if (quantity.value != null) {
            String valuePath = jsonPath + ",value";
            Expression<String> valueStringExpr = buildJsonPathExpression(cb, contentExpr, valuePath);

            Expression<Double> valueExpr = toNumericExpression(cb, valueStringExpr);
           
            
            Predicate valuePredicate = switch (quantity.prefix) {
                case "eq" -> cb.equal(valueExpr, quantity.value);
                case "ne" -> cb.notEqual(valueExpr, quantity.value);
                case "lt" -> cb.lessThan(valueExpr, quantity.value);
                case "gt" -> cb.greaterThan(valueExpr, quantity.value);
                case "le" -> cb.lessThanOrEqualTo(valueExpr, quantity.value);
                case "ge" -> cb.greaterThanOrEqualTo(valueExpr, quantity.value);
                case "ap" -> {
                    // Approximate: within 10%
                    double lower = quantity.value * 0.9;
                    double upper = quantity.value * 1.1;
                    yield cb.between(valueExpr, lower, upper);
                }
                default -> cb.equal(valueExpr, quantity.value);
            };
            predicates.add(valuePredicate);
        }

        // Build system comparison if specified
        if (quantity.system != null && !quantity.system.isEmpty()) {
            String systemPath = jsonPath + ",system";
            Expression<String> systemExpr = buildJsonPathExpression(cb, contentExpr, systemPath);
            predicates.add(cb.equal(systemExpr, quantity.system));
        }

        // Build code/unit comparison if specified
        if (quantity.code != null && !quantity.code.isEmpty()) {
            // Try both 'code' and 'unit' fields
            String codePath = jsonPath + ",code";
            String unitPath = jsonPath + ",unit";
            Expression<String> codeExpr = buildJsonPathExpression(cb, contentExpr, codePath);
            Expression<String> unitExpr = buildJsonPathExpression(cb, contentExpr, unitPath);

            predicates.add(cb.or(
                    cb.equal(codeExpr, quantity.code),
                    cb.equal(unitExpr, quantity.code)
            ));
        }

        if (predicates.isEmpty()) {
            return null;
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * Parses a quantity value in the format: [prefix]value|system|code
     */
    private QuantityValue parseQuantityValue(String value) {
        if (value == null || value.isEmpty()) {
            return new QuantityValue("eq", null, null, null);
        }

        // First extract the prefix
        String prefix = "eq";
        String remaining = value;

        Matcher matcher = PREFIX_PATTERN.matcher(value);
        if (matcher.matches()) {
            prefix = matcher.group(1);
            remaining = matcher.group(2);
        }

        // Split by pipe: value|system|code
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

    private record QuantityValue(String prefix, Double value, String system, String code) {}

    // ==================== Reference Search ====================

    /**
     * Builds a predicate for reference search parameters.
     * Supports formats: [type]/[id], absolute URL, [id]
     */
    private Predicate buildReferencePredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

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
            if (Character.isUpperCase(modifier.charAt(0))) {
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

        Expression<String> contentExpr = root.get("content");

        // Handle :missing modifier
        if (missingMatch) {
            Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, jsonPath);
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            return shouldBeMissing ? cb.isNull(jsonValue) : cb.isNotNull(jsonValue);
        }

        // Handle :identifier modifier - search on reference's identifier
        if (identifierMatch) {
            String identifierPath = jsonPath.replace(",reference", ",identifier");
            return buildTokenPredicate(cb, root, baseParamName, paramValue, expression);
        }

        // Parse reference value
        ReferenceValue ref = parseReferenceValue(paramValue, typeModifier);
        log.debug("Parsed reference: type='{}', id='{}', url='{}'", ref.type, ref.id, ref.url);

        String refPath = getReferencePath(jsonPath);
        Expression<String> refExpr = buildJsonPathExpression(cb, contentExpr, refPath);

        List<Predicate> orPredicates = new ArrayList<>();

        // Match by full reference (type/id)
        if (ref.type != null && ref.id != null) {
            orPredicates.add(cb.equal(refExpr, ref.type + "/" + ref.id));
            // Also match absolute URL patterns
            orPredicates.add(cb.like(refExpr, "%/" + ref.type + "/" + ref.id));
        } else if (ref.id != null) {
            // Match by ID only - could be at the end of any URL
            orPredicates.add(cb.like(refExpr, "%/" + ref.id));
            orPredicates.add(cb.equal(refExpr, ref.id));
        } else if (ref.url != null) {
            // Match absolute URL
            orPredicates.add(cb.equal(refExpr, ref.url));
        }

        if (orPredicates.isEmpty()) {
            return null;
        }

        return cb.or(orPredicates.toArray(new Predicate[0]));
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
            // Try to extract type/id from URL
            String[] urlParts = value.split("/");
            if (urlParts.length >= 2) {
                String lastPart = urlParts[urlParts.length - 1];
                String secondLastPart = urlParts[urlParts.length - 2];
                // Check if second-last part looks like a resource type (starts with uppercase)
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

    private record ReferenceValue(String type, String id, String url) {}

    // ==================== Composite Search ====================

    /**
     * Builds a predicate for composite search parameters.
     * Supports format: value1$value2
     */
    private Predicate buildCompositePredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String resourceType,
            String paramName,
            String paramValue) {

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
        List<Predicate> componentPredicates = new ArrayList<>();

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
            Predicate componentPred = buildParamPredicate(cb, root, resourceType, componentParamName, componentValue);
            if (componentPred != null) {
                componentPredicates.add(componentPred);
            }
        }

        if (componentPredicates.isEmpty()) {
            return null;
        }

        return cb.and(componentPredicates.toArray(new Predicate[0]));
    }

    // ==================== URI Search ====================

    /**
     * Builds a predicate for URI search parameters.
     */
    private Predicate buildUriPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

        boolean aboveMatch = paramName.endsWith(":above");
        boolean belowMatch = paramName.endsWith(":below");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            return null;
        }

        Expression<String> contentExpr = root.get("content");
        Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, jsonPath);

        if (missingMatch) {
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            return shouldBeMissing ? cb.isNull(jsonValue) : cb.isNotNull(jsonValue);
        }

        if (belowMatch) {
            // Match URIs that start with the parameter value
            return cb.like(jsonValue, paramValue + "%");
        }

        if (aboveMatch) {
            // Match URIs that are prefixes of the parameter value
            return cb.literal(paramValue).in(jsonValue);
        }

        // Exact match
        return cb.equal(jsonValue, paramValue);
    }

    // ==================== Existing Methods (Date, Number, String, ID, LastUpdated) ====================

    private Predicate buildIdPredicate(CriteriaBuilder cb, Root<FhirResourceEntity> root, String value) {
        // Handle comma-separated IDs (OR)
        if (value.contains(",")) {
            String[] ids = value.split(",");
            List<Predicate> orPredicates = new ArrayList<>();
            for (String id : ids) {
                orPredicates.add(cb.equal(root.get("resourceId"), id.trim()));
            }
            return cb.or(orPredicates.toArray(new Predicate[0]));
        }
        return cb.equal(root.get("resourceId"), value);
    }

    private Predicate buildLastUpdatedPredicate(CriteriaBuilder cb, Root<FhirResourceEntity> root, String value) {
        ParsedValue parsed = parseValueWithPrefix(value);

        Instant instant = parseDate(parsed.value);
        if (instant == null) {
            log.warn("Invalid date format for _lastUpdated: {}", value);
            return null;
        }

        Expression<Instant> lastUpdated = root.get("lastUpdated");
        return buildComparisonPredicate(cb, lastUpdated, instant, parsed.prefix);
    }

    private Predicate buildJsonbDatePredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

        ParsedValue parsed = parseValueWithPrefix(paramValue);
        String jsonPath = mapParamToJsonPath(stripModifier(paramName), expression);

        log.debug("Building date predicate: param={}, prefix={}, value={}, jsonPath={}",
                paramName, parsed.prefix, parsed.value, jsonPath);

        if (jsonPath == null) {
            return null;
        }

        Expression<String> jsonValue = buildJsonPathExpression(cb, root.get("content"), jsonPath);
        String dateStr = parsed.value;

        Predicate result = switch (parsed.prefix) {
            case "eq" -> cb.equal(jsonValue, dateStr);
            case "ne" -> cb.notEqual(jsonValue, dateStr);
            case "lt", "eb" -> cb.lessThan(jsonValue, dateStr);
            case "gt", "sa" -> cb.greaterThan(jsonValue, dateStr);
            case "le" -> cb.lessThanOrEqualTo(jsonValue, dateStr);
            case "ge" -> cb.greaterThanOrEqualTo(jsonValue, dateStr);
            case "ap" -> cb.like(jsonValue, dateStr + "%");
            default -> cb.equal(jsonValue, dateStr);
        };

        log.debug("Built date predicate for prefix '{}': comparing jsonPath '{}' with value '{}'",
                parsed.prefix, jsonPath, dateStr);

        return result;
    }

    private Predicate buildJsonbNumberPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

        ParsedValue parsed = parseValueWithPrefix(paramValue);
        String jsonPath = mapParamToJsonPath(stripModifier(paramName), expression);

        if (jsonPath == null) {
            return null;
        }

        try {
            double numValue = Double.parseDouble(parsed.value);

            Expression<String> stringValue = buildJsonPathExpression(cb, root.get("content"), jsonPath);
            Expression<Double> jsonValue = toNumericExpression(cb, stringValue);

            return switch (parsed.prefix) {
                case "eq" -> cb.equal(jsonValue, numValue);
                case "ne" -> cb.notEqual(jsonValue, numValue);
                case "lt" -> cb.lessThan(jsonValue, numValue);
                case "gt" -> cb.greaterThan(jsonValue, numValue);
                case "le" -> cb.lessThanOrEqualTo(jsonValue, numValue);
                case "ge" -> cb.greaterThanOrEqualTo(jsonValue, numValue);
                default -> cb.equal(jsonValue, numValue);
            };
        } catch (NumberFormatException e) {
            log.warn("Invalid number format for {}: {}", paramName, paramValue);
            return null;
        }
    }

    private Predicate buildJsonbStringPredicate(
            CriteriaBuilder cb,
            Root<FhirResourceEntity> root,
            String paramName,
            String paramValue,
            String expression) {

        boolean exactMatch = paramName.endsWith(":exact");
        boolean containsMatch = paramName.endsWith(":contains");
        boolean missingMatch = paramName.endsWith(":missing");

        String baseParamName = stripModifier(paramName);
        String jsonPath = mapParamToJsonPath(baseParamName, expression);

        if (jsonPath == null) {
            log.debug("Unknown search parameter: {}", paramName);
            return null;
        }

        Expression<String> contentExpr = root.get("content");

        if (missingMatch) {
            Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, jsonPath);
            boolean shouldBeMissing = "true".equalsIgnoreCase(paramValue);
            return shouldBeMissing ? cb.isNull(jsonValue) : cb.isNotNull(jsonValue);
        }

        // Copilot suggested -Special handling for HumanName searches (name, family, given)
        if (isHumanNameSearch(baseParamName)) {
            return buildHumanNamePredicateAllElements(cb, contentExpr, baseParamName, paramValue, exactMatch, containsMatch);
        }

        
        Expression<String> jsonValue = buildJsonPathExpression(cb, contentExpr, jsonPath);

        if (containsMatch) {
            return cb.like(cb.lower(jsonValue), "%" + paramValue.toLowerCase() + "%");
        }

        if (exactMatch) {
            return cb.equal(jsonValue, paramValue);
        }

        return cb.like(cb.lower(jsonValue), paramValue.toLowerCase() + "%");
    }

    // ==================== Helper Methods ====================

    private Expression<String> buildJsonPathExpression(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String jsonPath) {

        String[] pathParts = jsonPath.split(",");

        Expression<?>[] args = new Expression<?>[pathParts.length + 1];
        args[0] = contentExpr;
        for (int i = 0; i < pathParts.length; i++) {
            args[i + 1] = cb.literal(pathParts[i].trim());
        }

        return cb.function("jsonb_extract_path_text", String.class, args);
    }

    /**
     * Convert a string expression to a numeric (Double) expression.
     */
    private Expression<Double> toNumericExpression(CriteriaBuilder cb, Expression<String> stringExpr) {
        return cb.function("to_number", Double.class,
                cb.coalesce(stringExpr, cb.literal("0")),
                cb.literal("999999999999999D9999999999"));
    }

    private <T extends Comparable<T>> Predicate buildComparisonPredicate(
            CriteriaBuilder cb,
            Expression<T> expression,
            T value,
            String prefix) {

        return switch (prefix) {
            case "eq" -> cb.equal(expression, value);
            case "ne" -> cb.notEqual(expression, value);
            case "lt", "eb" -> cb.lessThan(expression, value);
            case "gt", "sa" -> cb.greaterThan(expression, value);
            case "le" -> cb.lessThanOrEqualTo(expression, value);
            case "ge" -> cb.greaterThanOrEqualTo(expression, value);
            default -> cb.equal(expression, value);
        };
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

    // ==================== Token Path Helpers ====================

    /**
     * Checks if a path represents a primitive code type (not CodeableConcept or Coding).
     * Primitive code paths don't have .coding structure.
     */
    private boolean isPrimitiveCodePath(String basePath) {
        // If path already contains ,coding, it's expanded for CodeableConcept/Coding
        if (basePath.contains(",coding,")) {
            return false;
        }
        // If path ends with ,value it's an Identifier
        if (basePath.endsWith(",value")) {
            return false;
        }
        // Otherwise it could be a primitive code (simple field like "status", "code", "category")
        return true;
    }

    /**
     * Gets the system path for token search.
     * Returns null for primitive code types (they have no system).
     */
    private String getTokenSystemPath(String basePath) {
        // For primitive code types, there is no system - return null
        if (isPrimitiveCodePath(basePath)) {
            return null;
        }
        // For CodeableConcept: category,0,coding,0,code -> category,0,coding,0,system
        // For Coding: code,coding,0,code -> code,coding,0,system
        if (basePath.endsWith(",code")) {
            return basePath.replace(",code", ",system");
        }
        if (basePath.contains(",coding,")) {
            return basePath.replace(",code", ",system");
        }
        // For Identifier: identifier,0,value -> identifier,0,system
        if (basePath.endsWith(",value")) {
            return basePath.replace(",value", ",system");
        }
        return basePath + ",system";
    }

    private String getTokenCodePath(String basePath) {
        // The base path should already point to the code field
        return basePath;
    }

    /**
     * Gets the expanded CodeableConcept path for a given base path.
     * Used for trying CodeableConcept structure when primitive code doesn't match.
     * Handles both single CodeableConcept and arrays of CodeableConcept.
     */
    private String getCodeableConceptPath(String basePath) {
        // Already expanded
        if (basePath.contains(",coding,")) {
            return basePath;
        }
        // Check if it's a simple field name that could be a CodeableConcept
        if (!basePath.contains(",") || basePath.matches("^[a-zA-Z]+$")) {
            // Single element like "code" -> "code,coding,0,code"
            // Or array like "category" -> "category,0,coding,0,code" (access first element)
            // We return the single element pattern; buildCodeableConceptPredicate handles arrays
            return basePath + ",coding,0,code";
        }
        // Array element like "category,0" -> "category,0,coding,0,code"
        if (basePath.matches(".*,\\d+$")) {
            return basePath + ",coding,0,code";
        }
        return basePath;
    }

    /**
     * Gets an array-aware CodeableConcept path (for arrays of CodeableConcept).
     */
    private String getCodeableConceptArrayPath(String basePath) {
        if (basePath.contains(",coding,")) {
            return basePath;
        }
        // For arrays like "category" -> "category,0,coding,0,code"
        if (!basePath.contains(",") || basePath.matches("^[a-zA-Z]+$")) {
            return basePath + ",0,coding,0,code";
        }
        return basePath + ",coding,0,code";
    }

    private String getTokenTextPath(String basePath) {
        // Get display/text field for :text modifier
        if (basePath.contains(",coding,")) {
            return basePath.replace(",code", ",display");
        }
        return basePath.replace(",code", ",text").replace(",value", ",text");
    }

    private String getReferencePath(String basePath) {
        // Ensure we're looking at the reference field
        if (basePath.endsWith(",reference")) {
            return basePath;
        }
        return basePath + ",reference";
    }

    // ==================== Parameter to JSON Path Mapping ====================

    /**
     * Maps a search parameter to its JSON path.
     * Uses FHIRPath expression if available, otherwise falls back to hardcoded mappings.
     */
    private String mapParamToJsonPath(String paramName, String expression) {
        // If we have an expression, try to convert it to a JSON path
    	log.debug("Mapping parameter '{}' with expression '{}'", paramName, expression);
        if (expression != null && !expression.isEmpty()) {
            //String jsonPath = convertExpressionToJsonPath(expression);
            //if (jsonPath != null) {
            //    return jsonPath;
            //}
        	
            log.debug("Mapping parameter '{}' with expression '{}'", paramName, expression);

            // Split by pipe (|) to get individual paths for different resource types
            String[] pathOptions = expression.split("\\|");

            // Use the first path option (simplification - you may need resource-type-specific logic)
            String firstPath = pathOptions[0].trim();

            // Remove the resource type prefix (e.g., "Account." -> "")
            String pathWithoutResourceType = removeResourceTypePrefix(firstPath);

            // Resolve FHIRPath expression to JSON path
            String resolvedPath = resolveFhirExpression(pathWithoutResourceType);

            // For reference types, append '.reference' to get the reference URL
            if (isReferenceParameter(paramName, resolvedPath)) {
                resolvedPath = resolvedPath + ",reference";
            }

            // Path is already in comma-separated format from resolveFhirExpression
            return resolvedPath;
        }

        log.warn("No FHIRPath expression found for parameter '{}', using hardcoded mapping", paramName);

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

    /**
     * Converts a FHIRPath expression to a JSON path.
     * This is a simplified conversion that handles common patterns.
     * 
     *  @deprecated Use resolveFhirExpression instead for better extensibility
     */
    private String convertExpressionToJsonPath(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        //added trim to handle leading/trailing spaces
        String path = expression.trim();

        // Remove outer parentheses if present: "(expression)" -> "expression"
        if (path.startsWith("(") && path.endsWith(")")) {
            path = path.substring(1, path.length() - 1).trim();
        }

        // Handle union expressions - extract just the first alternative
        // For "AdverseEvent.code | AllergyIntolerance.code | ... | Observation.code | ...",
        // we take the first one and assume they all have similar structure
        if (path.contains(" | ")) {
            String[] alternatives = path.split(" \\| ");
            path = alternatives[0].trim();
        }

        // Handle FHIRPath "as" operator for polymorphic elements before stripping resource prefix
        // Convert "(Observation.value as Quantity)" or "Observation.value as Quantity" to proper path
        Pattern asPattern = Pattern.compile("([\\w.]+)\\s+as\\s+(\\w+)");
        Matcher asMatcher = asPattern.matcher(path);
        if (asMatcher.find()) {
            String fullPath = asMatcher.group(1);  // e.g., "Observation.value" or "value"
            String typeName = asMatcher.group(2);  // e.g., "Quantity"

            // Get just the element name (last part after dot)
            String elementName = fullPath.contains(".")
                    ? fullPath.substring(fullPath.lastIndexOf('.') + 1)
                    : fullPath;

            // Capitalize first letter of type name for camelCase concatenation
            String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
            String polymorphicName = elementName + capitalizedType;
            log.debug("Converted 'as' expression: {} as {} -> {}", fullPath, typeName, polymorphicName);
            return polymorphicName;
        }

        // Remove the resource type prefix (e.g., "Patient.name" -> "name")
        if (path.contains(".")) {
            int dotIndex = path.indexOf('.');
            path = path.substring(dotIndex + 1);
        }

        // Handle common FHIRPath patterns
        // Remove .where(), .exists(), .first(), etc.
        path = path.replaceAll("\\.where\\([^)]*\\)", "");
        path = path.replaceAll("\\.exists\\(\\)", "");
        path = path.replaceAll("\\.first\\(\\)", "");
        path = path.replaceAll(" \\| [^,]+", ""); // Remove union alternatives

        // Handle .ofType() for polymorphic elements (value[x], effective[x], etc.)
        // Convert "value.ofType(Quantity)" to "valueQuantity"
        // Convert "effective.ofType(dateTime)" to "effectiveDateTime"
        Pattern ofTypePattern = Pattern.compile("(\\w+)\\.ofType\\(([^)]+)\\)");
        Matcher ofTypeMatcher = ofTypePattern.matcher(path);
        if (ofTypeMatcher.find()) {
            String elementName = ofTypeMatcher.group(1);
            String typeName = ofTypeMatcher.group(2).trim();
            // Capitalize first letter of type name for camelCase concatenation
            String capitalizedType = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
            String polymorphicName = elementName + capitalizedType;
            path = ofTypeMatcher.replaceFirst(polymorphicName);
            log.debug("Converted ofType expression: {}.ofType({}) -> {}", elementName, typeName, polymorphicName);
        }

        // Convert dots to commas (except for type functions)
        path = path.replace(".", ",");

        // Copilot suggested changes start here
        // Special handling for "code" - expand to full CodeableConcept path
        if (path.equals("code")) {
            return "code,coding,0,code";
        }

        // Handle other common patterns that need expansion
        if (path.equals("category")) {
            return "category,0,coding,0,code";
        }

        if (path.equals("type")) {
            return "type,coding,0,code";
        }

        // Handle nested code paths like "code,concept" -> "code,concept,coding,0,code"
        if (path.endsWith(",concept") && !path.contains(",coding,")) {
            return path.replace(",concept", ",concept,coding,0,code");
        }

        // Handle name paths
        if (path.equals("name")) {
            return "name,0,text";
        }

        // Handle identifier paths
        if (path.equals("identifier")) {
            return "identifier,0,value";
        }
        //end of Copilot suggested changes
        
        // Handle array access - add ,0 for first element
        // This is a simplification; real implementation would need to handle all elements
        if (!path.contains(",0,") && !path.endsWith(",0")) {
            // Check if this is a field that's typically an array
            if (path.startsWith("name") || path.startsWith("identifier") ||
                    path.startsWith("address") || path.startsWith("telecom") ||
                    path.startsWith("category") || path.startsWith("code,coding")) {

                if (path.equals("name")) {
                    path = "name,0,text";
                } else if (path.equals("identifier")) {
                    path = "identifier,0,value";
                } else if (path.equals("address")) {
                    path = "address,0";
                } else if (path.equals("telecom")) {
                    path = "telecom,0,value";
                } else if (path.equals("category")) {
                    path = "category,0,coding,0,code";
                }
            }
        }

        return path.isEmpty() ? null : path;
    }

    /**
     * Parsed value with prefix and actual value separated.
     */
    private record ParsedValue(String prefix, String value) {}
    
    //copilot suggested
    /**
     * Checks if a parameter is a HumanName search.
     */
    private boolean isHumanNameSearch(String paramName) {
        // Parameters that search across HumanName fields
        return "name".equals(paramName) || 
               "family".equals(paramName) || 
               "given".equals(paramName);
    }

    /**
     * Builds a predicate that searches across all HumanName fields.
     * Matches any of: text, family, given (array), prefix (array), suffix (array)
     */
    private Predicate buildHumanNamePredicate(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String paramName,
            String paramValue,
            boolean exactMatch,
            boolean containsMatch) {

        List<Predicate> orPredicates = new ArrayList<>();
        String lowerValue = paramValue.toLowerCase();
        String pattern = containsMatch ? "%" + lowerValue + "%" : lowerValue + "%";

        // For "name" parameter, search across all fields
        if ("name".equals(paramName)) {
            // Search in text field
            Expression<String> textExpr = buildJsonPathExpression(cb, contentExpr, "name,0,text");
            orPredicates.add(exactMatch 
                ? cb.equal(textExpr, paramValue)
                : cb.like(cb.lower(textExpr), pattern));

            // Search in family field
            Expression<String> familyExpr = buildJsonPathExpression(cb, contentExpr, "name,0,family");
            orPredicates.add(exactMatch 
                ? cb.equal(familyExpr, paramValue)
                : cb.like(cb.lower(familyExpr), pattern));

            // Search in given array (first element)
            Expression<String> givenExpr = buildJsonPathExpression(cb, contentExpr, "name,0,given,0");
            orPredicates.add(exactMatch 
                ? cb.equal(givenExpr, paramValue)
                : cb.like(cb.lower(givenExpr), pattern));

            // Search in prefix array (first element)
            Expression<String> prefixExpr = buildJsonPathExpression(cb, contentExpr, "name,0,prefix,0");
            orPredicates.add(exactMatch 
                ? cb.equal(prefixExpr, paramValue)
                : cb.like(cb.lower(prefixExpr), pattern));

            // Search in suffix array (first element)
            Expression<String> suffixExpr = buildJsonPathExpression(cb, contentExpr, "name,0,suffix,0");
            orPredicates.add(exactMatch 
                ? cb.equal(suffixExpr, paramValue)
                : cb.like(cb.lower(suffixExpr), pattern));
        } else if ("family".equals(paramName)) {
            // Search only in family field
            Expression<String> familyExpr = buildJsonPathExpression(cb, contentExpr, "name,0,family");
            orPredicates.add(exactMatch 
                ? cb.equal(familyExpr, paramValue)
                : cb.like(cb.lower(familyExpr), pattern));
        } else if ("given".equals(paramName)) {
            // Search only in given field (first element)
            Expression<String> givenExpr = buildJsonPathExpression(cb, contentExpr, "name,0,given,0");
            orPredicates.add(exactMatch 
                ? cb.equal(givenExpr, paramValue)
                : cb.like(cb.lower(givenExpr), pattern));
        }

        return orPredicates.isEmpty() ? null : cb.or(orPredicates.toArray(new Predicate[0]));
    }

    /**
     * Builds a predicate that searches across HumanName arrays (simplified version).
     * Searches first 3 elements of array fields.
     */
    private Predicate buildHumanNamePredicateAllElements(
            CriteriaBuilder cb,
            Expression<String> contentExpr,
            String paramName,
            String paramValue,
            boolean exactMatch,
            boolean containsMatch) {

        List<Predicate> orPredicates = new ArrayList<>();
        String lowerValue = paramValue.toLowerCase();
        String pattern = containsMatch ? "%" + lowerValue + "%" : lowerValue + "%";

        if ("name".equals(paramName)) {
            // Search in scalar fields
            Expression<String> textExpr = buildJsonPathExpression(cb, contentExpr, "name,0,text");
            orPredicates.add(exactMatch 
                ? cb.equal(textExpr, paramValue)
                : cb.like(cb.lower(textExpr), pattern));

            Expression<String> familyExpr = buildJsonPathExpression(cb, contentExpr, "name,0,family");
            orPredicates.add(exactMatch 
                ? cb.equal(familyExpr, paramValue)
                : cb.like(cb.lower(familyExpr), pattern));

            // Search first 2 elements of array fields (covers most use cases)
            for (String arrayField : List.of("given", "prefix", "suffix")) {
                for (int i = 0; i < 2; i++) {
                    String arrayPath = String.format("name,0,%s,%d", arrayField, i);
                    Expression<String> arrayExpr = buildJsonPathExpression(cb, contentExpr, arrayPath);
                    
                    Predicate arrayPredicate = exactMatch 
                        ? cb.equal(arrayExpr, paramValue)
                        : cb.like(cb.lower(arrayExpr), pattern);
                        
                    orPredicates.add(arrayPredicate);
                }
            }
        } else if ("family".equals(paramName)) {
            Expression<String> familyExpr = buildJsonPathExpression(cb, contentExpr, "name,0,family");
            orPredicates.add(exactMatch 
                ? cb.equal(familyExpr, paramValue)
                : cb.like(cb.lower(familyExpr), pattern));
        } else if ("given".equals(paramName)) {
            // Search first 2 given names
            for (int i = 0; i < 2; i++) {
                Expression<String> givenExpr = buildJsonPathExpression(cb, contentExpr, "name,0,given," + i);
                orPredicates.add(exactMatch 
                    ? cb.equal(givenExpr, paramValue)
                    : cb.like(cb.lower(givenExpr), pattern));
            }
        }

        return orPredicates.isEmpty() ? null : cb.or(orPredicates.toArray(new Predicate[0]));
    }

    //Copilot suggested changes started here for resolving fhirexpression specified 
    // in search parameter definition eg the following one
    // expression":"Account.subject.where(resolve() is Patient) | AdverseEvent.subject.where(resolve() is Patient) | AllergyIntolerance.patient | Appointment.participant.actor.where(resolve() is Patient) | Appointment.subject.where(resolve() is Patient) | AppointmentResponse.actor.where(resolve() is Patient) | AuditEvent.patient | Basic.subject.where(resolve() is Patient) | BodyStructure.patient | CarePlan.subject.where(resolve() is Patient) | CareTeam.subject.where(resolve() is Patient) | ChargeItem.subject.where(resolve() is Patient) | Claim.patient | ClaimResponse.patient | ClinicalImpression.subject.where(resolve() is Patient) | Communication.subject.where(resolve() is Patient) | CommunicationRequest.subject.where(resolve() is Patient) | Composition.subject.where(resolve() is Patient) | Condition.subject.where(resolve() is Patient) | Consent.subject.where(resolve() is Patient) | Contract.subject.where(resolve() is Patient) | Coverage.beneficiary | CoverageEligibilityRequest.patient | CoverageEligibilityResponse.patient | DetectedIssue.subject.where(resolve() is Patient) | DeviceRequest.subject.where(resolve() is Patient) | DeviceUsage.patient | DiagnosticReport.subject.where(resolve() is Patient) | DocumentReference.subject.where(resolve() is Patient) | Encounter.subject.where(resolve() is Patient) | EnrollmentRequest.candidate | EpisodeOfCare.patient | ExplanationOfBenefit.patient | FamilyMemberHistory.patient | Flag.subject.where(resolve() is Patient) | Goal.subject.where(resolve() is Patient) | GuidanceResponse.subject.where(resolve() is Patient) | ImagingSelection.subject.where(resolve() is Patient) | ImagingStudy.subject.where(resolve() is Patient) | Immunization.patient | ImmunizationEvaluation.patient | ImmunizationRecommendation.patient | Invoice.subject.where(resolve() is Patient) | List.subject.where(resolve() is Patient) | MeasureReport.subject.where(resolve() is Patient) | MedicationAdministration.subject.where(resolve() is Patient) | MedicationDispense.subject.where(resolve() is Patient) | MedicationRequest.subject.where(resolve() is Patient) | MedicationStatement.subject.where(resolve() is Patient) | MolecularSequence.subject.where(resolve() is Patient) | NutritionIntake.subject.where(resolve() is Patient) | NutritionOrder.subject.where(resolve() is Patient) | Observation.subject.where(resolve() is Patient) | Person.link.target.where(resolve() is Patient) | Procedure.subject.where(resolve() is Patient) | Provenance.patient | QuestionnaireResponse.subject.where(resolve() is Patient) | RelatedPerson.patient | RequestOrchestration.subject.where(resolve() is Patient) | ResearchSubject.subject.where(resolve() is Patient) | RiskAssessment.subject.where(resolve() is Patient) | ServiceRequest.subject.where(resolve() is Patient) | Specimen.subject.where(resolve() is Patient) | SupplyDelivery.patient | SupplyRequest.deliverFor | Task.for.where(resolve() is Patient) | VisionPrescription.patient","processingMode":"normal","target":["Patient"]}
    /**
     * Removes resource type prefix from FHIRPath expression.
     * E.g., "Account.subject.where(...)" -> "subject.where(...)"
     */
    private String removeResourceTypePrefix(String fhirPath) {
        if (fhirPath == null || fhirPath.isEmpty()) {
            return fhirPath;
        }

        // Match pattern: ResourceType.fieldPath
        String[] parts = fhirPath.split("\\.", 2);

        if (parts.length == 2) {
            String firstPart = parts[0].trim();
            // Check if first part is a resource type (starts with uppercase)
            if (!firstPart.isEmpty() && Character.isUpperCase(firstPart.charAt(0))) {
                return parts[1];
            }
        }

        return fhirPath;
    }

    
    /*
    * Resolves a FHIRPath expression to a JSON path format.
    * This method handles various FHIRPath patterns and converts them to database-queryable paths.
    * 
    * @TODO: This is a simplified implementation. A full FHIRPath parser would be needed for complete support.
    * 
    * Supported patterns:
    * - .where(resolve() is Patient) - removes type filters
    * - .where(condition) - removes general filters
    * - .resolve() - removes resolve calls
    * - is ResourceType - removes type checking
    * - .ofType(Type) - converts polymorphic elements (value.ofType(Quantity) -> valueQuantity)
    * - as Type - converts type casting (value as Quantity -> valueQuantity)
    * - .exists(), .first(), etc. - removes function calls
    * 
    * @param fhirPath The FHIRPath expression to resolve
    * @return JSON path in comma-separated format, or null if invalid
    */
   private String resolveFhirExpression(String fhirPath) {
       if (fhirPath == null || fhirPath.isEmpty()) {
           return null;
       }

       String path = fhirPath.trim();

       // Remove outer parentheses if present: "(expression)" -> "expression"
       path = stripOuterParentheses(path);

       // Handle FHIRPath "as" operator for polymorphic elements
       // Convert "(value as Quantity)" or "value as Quantity" to "valueQuantity"
       path = resolveAsOperator(path);

       // Handle .ofType() for polymorphic elements
       // Convert "value.ofType(Quantity)" to "valueQuantity"
       path = resolveOfType(path);

      
       // Remove FHIRPath filters like .where(...)
       path = removeWhereFilters(path);

       // Remove .resolve() calls
       path = path.replaceAll("\\.resolve\\(\\)", "");
       
 
       // Remove type checking: "is ResourceType"
       path = path.replaceAll("\\s+is\\s+\\w+", "");

       // Remove function calls like .exists(), .first(), etc.
       path = removeFunctionCalls(path);

       // Remove any trailing dots
       path = path.replaceAll("\\.+$", "");

       // Trim whitespace
       path = path.trim();

       // Convert dot notation to comma-separated for JSONB path
       path = path.replace(".", ",");

       // Expand common patterns that need array access
       path = expandCommonPatterns(path);

       return path.isEmpty() ? null : path;
   }
   
   /**
    * Strips outer parentheses from an expression.
    */
   private String stripOuterParentheses(String expression) {
       String trimmed = expression.trim();
       if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
           return trimmed.substring(1, trimmed.length() - 1).trim();
       }
       return trimmed;
   }

   /**
    * Resolves FHIRPath "as" operator for polymorphic elements.
    * E.g., "value as Quantity" -> "valueQuantity"
    */
   private String resolveAsOperator(String path) {
       Pattern asPattern = Pattern.compile("([\\w.]+)\\s+as\\s+(\\w+)");
       Matcher asMatcher = asPattern.matcher(path);
       
       if (asMatcher.find()) {
           String fullPath = asMatcher.group(1);  // e.g., "value"
           String typeName = asMatcher.group(2);  // e.g., "Quantity"

           // Get just the element name (last part after dot)
           String elementName = fullPath.contains(".")
                   ? fullPath.substring(fullPath.lastIndexOf('.') + 1)
                   : fullPath;

           // Capitalize first letter of type name for camelCase concatenation
           String capitalizedType = capitalizeFirstLetter(typeName);
           String polymorphicName = elementName + capitalizedType;
           
           log.debug("Resolved 'as' operator: {} as {} -> {}", fullPath, typeName, polymorphicName);
           return asMatcher.replaceFirst(polymorphicName);
       }
       
       return path;
   }

   /**
    * Resolves .ofType() for polymorphic elements.
    * E.g., "value.ofType(Quantity)" -> "valueQuantity"
    */
   private String resolveOfType(String path) {
       Pattern ofTypePattern = Pattern.compile("(\\w+)\\.ofType\\(([^)]+)\\)");
       Matcher ofTypeMatcher = ofTypePattern.matcher(path);
       
       if (ofTypeMatcher.find()) {
           String elementName = ofTypeMatcher.group(1);
           String typeName = ofTypeMatcher.group(2).trim();
           
           // Capitalize first letter of type name for camelCase concatenation
           String capitalizedType = capitalizeFirstLetter(typeName);
           String polymorphicName = elementName + capitalizedType;
           
           log.debug("Resolved ofType: {}.ofType({}) -> {}", elementName, typeName, polymorphicName);
           return ofTypeMatcher.replaceFirst(polymorphicName);
       }
       
       return path;
   }

   /**
    * Removes .where(...) filters from FHIRPath expression.
    * Handles nested parentheses.
    */
   private String removeWhereFilters(String path) {
       // Simple regex for non-nested where clauses
	   //TODO: improve to handle nested parentheses - .where(a.where(b.where(c)))
	   //but I think the above nested where is not likely in search parameter definitions
       String result = path.replaceAll("\\.where\\([^()]*(?:\\([^()]*\\)[^()]*)*\\)", "");
        
            
       // Handle nested parentheses in where clauses (more complex)
       while (result.contains(".where(")) {
           int whereIndex = result.indexOf(".where(");
           if (whereIndex == -1) break;
           
           int parenCount = 0;
           int endIndex = whereIndex + 7; // Start after ".where("
           
           for (; endIndex < result.length(); endIndex++) {
               char c = result.charAt(endIndex);
               if (c == '(') parenCount++;
               else if (c == ')') {
                   if (parenCount == 0) {
                       // Found matching closing parenthesis
                       result = result.substring(0, whereIndex) + result.substring(endIndex + 1);
                       break;
                   }
                   parenCount--;
               }
           }
           
           // Safety check to avoid infinite loop
           if (endIndex >= result.length()) break;
       }
       
       
       return result;
   }

   /**
    * Removes function calls from FHIRPath expression.
    * E.g., .exists(), .first(), .empty(), etc.
    */
   private String removeFunctionCalls(String path) {
       // Remove common FHIRPath functions
       return path.replaceAll("\\.(?:exists|first|last|tail|skip|take|single|empty|count|distinct|allTrue|anyTrue|allFalse|anyFalse)\\([^)]*\\)", "");
   }

   /**
    * Expands common patterns that need array access or CodeableConcept navigation.
    */
   private String expandCommonPatterns(String path) {
       // NOTE: We intentionally do NOT expand token-searchable fields like 'code', 'category', 'type', 'status'
       // to CodeableConcept paths here. The buildTokenPredicate() method handles both primitive code
       // and CodeableConcept structures dynamically using OR predicates.
       // This allows the same search parameter to work for:
       // - Resources with primitive 'code' type (e.g., Course.code, Course.status)
       // - Resources with CodeableConcept type (e.g., Observation.code, Condition.category)

       // Handle nested code paths like "code,concept" -> "code,concept,coding,0,code"
       // These are explicitly nested structures, not simple fields
       if (path.endsWith(",concept") && !path.contains(",coding,")) {
           return path.replace(",concept", ",concept,coding,0,code");
       }

       // Handle name paths - array of HumanName
       if (path.equals("name")) {
           return "name,0,text";
       }

       // Handle identifier paths - array of Identifier
       if (path.equals("identifier")) {
           return "identifier,0,value";
       }

       // Handle address paths - array of Address
       if (path.equals("address")) {
           return "address,0";
       }

       // Handle telecom paths - array of ContactPoint
       if (path.equals("telecom")) {
           return "telecom,0,value";
       }

       return path;
   }

   /**
    * Capitalizes the first letter of a string.
    */
   private String capitalizeFirstLetter(String str) {
       if (str == null || str.isEmpty()) {
           return str;
       }
       return str.substring(0, 1).toUpperCase() + str.substring(1);
   }

   /**
    * Checks if a parameter represents a reference type that needs ',reference' appended.
    */
   private boolean isReferenceParameter(String paramName, String path) {
       // Common reference parameters
       Set<String> referenceParams = Set.of(
           "patient", "subject", "encounter", "performer",
           "requester", "participant", "actor", "beneficiary",
           "candidate", "link", "target", "location", "organization",
           "practitioner", "recorder", "asserter", "managingOrganization"
       );

       return referenceParams.contains(paramName);
   }



}
