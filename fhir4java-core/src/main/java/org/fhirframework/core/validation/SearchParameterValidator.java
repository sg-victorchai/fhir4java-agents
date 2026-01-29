package org.fhirframework.core.validation;

import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validates search parameters against the SearchParameterRegistry and resource configuration.
 * <p>
 * Validation checks:
 * <ol>
 *   <li>Parameter existence - is it a valid FHIR search parameter for the resource type?</li>
 *   <li>Parameter allowance - is it allowed per resource configuration (allowlist/denylist)?</li>
 *   <li>Modifier validity - is the modifier supported for the parameter type?</li>
 * </ol>
 * </p>
 */
@Component
public class SearchParameterValidator {

    private static final Logger log = LoggerFactory.getLogger(SearchParameterValidator.class);

    /**
     * Special control parameters that don't need validation.
     * These are pagination, sorting, and result control parameters.
     */
    private static final Set<String> SPECIAL_PARAMETERS = Set.of(
            "_count",
            "_offset",
            "_sort",
            "_include",
            "_revinclude",
            "_summary",
            "_elements",
            "_format",
            "_pretty",
            "_total",
            "_contained",
            "_containedType",
            "_page",
            "_since",
            "_at",
            "_outputFormat"
    );

    /**
     * Valid modifiers for string-type parameters.
     */
    private static final Set<String> STRING_MODIFIERS = Set.of(
            "exact", "contains", "missing", "text"
    );

    /**
     * Valid modifiers for token-type parameters.
     */
    private static final Set<String> TOKEN_MODIFIERS = Set.of(
            "text", "not", "above", "below", "in", "not-in", "of-type", "missing"
    );

    /**
     * Valid modifiers for reference-type parameters.
     */
    private static final Set<String> REFERENCE_MODIFIERS = Set.of(
            "missing", "type", "identifier"
    );

    /**
     * Valid modifiers for date/number/quantity parameters.
     */
    private static final Set<String> COMPARABLE_MODIFIERS = Set.of(
            "missing"
    );

    /**
     * Valid modifiers for URI-type parameters.
     */
    private static final Set<String> URI_MODIFIERS = Set.of(
            "above", "below", "missing"
    );

    private final SearchParameterRegistry searchParameterRegistry;
    private final ResourceRegistry resourceRegistry;

    public SearchParameterValidator(
            SearchParameterRegistry searchParameterRegistry,
            ResourceRegistry resourceRegistry) {
        this.searchParameterRegistry = searchParameterRegistry;
        this.resourceRegistry = resourceRegistry;
    }

    /**
     * Validate all search parameters in a search request.
     *
     * @param version      FHIR version
     * @param resourceType Resource type being searched
     * @param params       Map of parameter names to values
     * @return ValidationResult with any issues found
     */
    public ValidationResult validateSearchParameters(
            FhirVersion version,
            String resourceType,
            Map<String, String[]> params) {

        ValidationResult result = new ValidationResult();

        if (params == null || params.isEmpty()) {
            return result;
        }

        for (String paramName : params.keySet()) {
            validateParameter(version, resourceType, paramName, result);
        }

        if (result.hasErrors()) {
            log.debug("Search parameter validation failed for {}: {} errors",
                    resourceType, result.getErrorCount());
        }

        return result;
    }

    /**
     * Validate a single search parameter.
     */
    private void validateParameter(
            FhirVersion version,
            String resourceType,
            String paramName,
            ValidationResult result) {

        // Skip special/control parameters
        if (isSpecialParameter(paramName)) {
            log.trace("Skipping validation for special parameter: {}", paramName);
            return;
        }

        // Extract base parameter name and modifier
        String baseParamName = extractBaseParamName(paramName);
        String modifier = extractModifier(paramName);

        // Check 1: Is this parameter defined in the SearchParameterRegistry?
        if (!searchParameterRegistry.isSearchParameterDefined(version, resourceType, baseParamName)) {
            result.addIssue(ValidationIssue.error(
                    IssueType.NOTFOUND,
                    String.format("Unknown search parameter '%s' for resource type '%s'",
                            baseParamName, resourceType),
                    "parameter:" + paramName,
                    "The search parameter is not defined for this resource type in FHIR " + version.getCode()
            ));
            return;
        }

        // Check 2: Is this parameter allowed per resource configuration?
        if (!searchParameterRegistry.isSearchParameterAllowed(
                version, resourceType, baseParamName, resourceRegistry)) {
            result.addIssue(ValidationIssue.error(
                    IssueType.BUSINESSRULE,
                    String.format("Search parameter '%s' is not allowed for resource type '%s'",
                            baseParamName, resourceType),
                    "parameter:" + paramName,
                    "The search parameter is restricted by server configuration. " +
                            "Check the resource configuration for allowed parameters."
            ));
            return;
        }

        // Check 3: If there's a modifier, validate it's appropriate for the parameter type
        if (modifier != null) {
            validateModifier(version, resourceType, baseParamName, modifier, result);
        }
    }

    /**
     * Validate that a modifier is valid for the parameter type.
     */
    private void validateModifier(
            FhirVersion version,
            String resourceType,
            String baseParamName,
            String modifier,
            ValidationResult result) {

        var paramType = searchParameterRegistry.getSearchParameterType(version, resourceType, baseParamName);
        if (paramType.isEmpty()) {
            return; // Can't validate modifier without knowing the type
        }

        Set<String> validModifiers = getValidModifiers(paramType.get());

        // Special handling: resource type modifiers for reference parameters
        // e.g., subject:Patient, performer:Practitioner
        if (paramType.get() == org.hl7.fhir.r5.model.Enumerations.SearchParamType.REFERENCE) {
            // Allow resource type names as modifiers (e.g., :Patient, :Practitioner)
            if (isResourceTypeName(modifier)) {
                return;
            }
        }

        if (!validModifiers.contains(modifier)) {
            result.addIssue(ValidationIssue.warning(
                    String.format("Modifier ':%s' may not be valid for parameter '%s' of type '%s'",
                            modifier, baseParamName, paramType.get().toCode()),
                    "parameter:" + baseParamName + ":" + modifier
            ));
        }
    }

    /**
     * Get valid modifiers for a search parameter type.
     */
    private Set<String> getValidModifiers(org.hl7.fhir.r5.model.Enumerations.SearchParamType type) {
        return switch (type) {
            case STRING -> STRING_MODIFIERS;
            case TOKEN -> TOKEN_MODIFIERS;
            case REFERENCE -> REFERENCE_MODIFIERS;
            case DATE, NUMBER, QUANTITY -> COMPARABLE_MODIFIERS;
            case URI -> URI_MODIFIERS;
            default -> Set.of("missing");
        };
    }

    /**
     * Check if a string is a FHIR resource type name.
     * This is used for reference parameter type modifiers.
     */
    private boolean isResourceTypeName(String name) {
        // Check if resource is known to the registry
        return resourceRegistry.isResourceConfigured(name);
    }

    /**
     * Extract the base parameter name (without modifier).
     * e.g., "name:exact" -> "name", "birthdate:missing" -> "birthdate"
     */
    private String extractBaseParamName(String paramName) {
        int colonIndex = paramName.indexOf(':');
        return colonIndex > 0 ? paramName.substring(0, colonIndex) : paramName;
    }

    /**
     * Extract the modifier from a parameter name.
     * e.g., "name:exact" -> "exact", "birthdate" -> null
     */
    private String extractModifier(String paramName) {
        int colonIndex = paramName.indexOf(':');
        return colonIndex > 0 ? paramName.substring(colonIndex + 1) : null;
    }

    /**
     * Check if a parameter is a special/control parameter that doesn't need validation.
     */
    private boolean isSpecialParameter(String paramName) {
        String baseName = extractBaseParamName(paramName);
        return SPECIAL_PARAMETERS.contains(baseName);
    }

    /**
     * Validate a single parameter name (convenience method for single parameter validation).
     */
    public ValidationResult validateParameter(
            FhirVersion version,
            String resourceType,
            String paramName) {

        ValidationResult result = new ValidationResult();
        validateParameter(version, resourceType, paramName, result);
        return result;
    }
}
