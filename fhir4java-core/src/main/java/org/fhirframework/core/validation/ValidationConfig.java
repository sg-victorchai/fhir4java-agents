package org.fhirframework.core.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for FHIR validation.
 */
@Component
public class ValidationConfig {

    @Value("${fhir4java.validation.enabled:true}")
    private boolean enabled;

    @Value("${fhir4java.validation.profile-validator-enabled:true}")
    private boolean profileValidatorEnabled;

    @Value("${fhir4java.validation.profile-validation:lenient}")
    private String profileValidationStr;

    @Value("${fhir4java.validation.log-validation-operations:false}")
    private boolean logValidationOperations;

    @Value("${fhir4java.validation.lazy-initialization:false}")
    private boolean lazyInitialization;

    @Value("${fhir4java.validation.health-check-mode:strict}")
    private String healthCheckModeStr;

    @Value("${fhir4java.validation.validate-search-parameters:true}")
    private boolean validateSearchParameters;

    @Value("${fhir4java.validation.fail-on-unknown-search-parameters:false}")
    private boolean failOnUnknownSearchParameters;

    @Value("${fhir4java.validation.parser-error-handler:strict}")
    private String parserErrorHandlerStr;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isProfileValidatorEnabled() {
        return profileValidatorEnabled;
    }

    public boolean isLogValidationOperations() {
        return logValidationOperations;
    }

    public boolean isLazyInitialization() {
        return lazyInitialization;
    }

    public HealthCheckMode getHealthCheckMode() {
        if (healthCheckModeStr == null) {
            return HealthCheckMode.STRICT;
        }
        return switch (healthCheckModeStr.toLowerCase()) {
            case "warn" -> HealthCheckMode.WARN;
            case "disabled" -> HealthCheckMode.DISABLED;
            default -> HealthCheckMode.STRICT;
        };
    }

    public ProfileValidationMode getProfileValidation() {
        if (profileValidationStr == null) {
            return ProfileValidationMode.LENIENT;
        }
        return switch (profileValidationStr.toLowerCase()) {
            case "strict" -> ProfileValidationMode.STRICT;
            case "off" -> ProfileValidationMode.OFF;
            default -> ProfileValidationMode.LENIENT;
        };
    }

    public boolean isValidateSearchParameters() {
        return validateSearchParameters;
    }

    public boolean isFailOnUnknownSearchParameters() {
        return failOnUnknownSearchParameters;
    }

    /**
     * Check if profile validation is enabled (not OFF).
     */
    public boolean isProfileValidationEnabled() {
        return getProfileValidation() != ProfileValidationMode.OFF;
    }

    /**
     * Check if strict profile validation is enabled.
     */
    public boolean isStrictProfileValidation() {
        return getProfileValidation() == ProfileValidationMode.STRICT;
    }

    /**
     * Get the parser error handler mode.
     */
    public ParserErrorMode getParserErrorHandler() {
        if (parserErrorHandlerStr == null) {
            return ParserErrorMode.STRICT;
        }
        return switch (parserErrorHandlerStr.toLowerCase()) {
            case "lenient" -> ParserErrorMode.LENIENT;
            default -> ParserErrorMode.STRICT;
        };
    }

    /**
     * Parser error handler modes.
     */
    public enum ParserErrorMode {
        /**
         * Throws exceptions for unknown elements.
         */
        STRICT,

        /**
         * Logs warnings for unknown elements but allows parsing to continue.
         */
        LENIENT
    }

    /**
     * Profile validation modes.
     */
    public enum ProfileValidationMode {
        /**
         * Full validation, errors cause rejection.
         */
        STRICT,

        /**
         * Validation is performed but warnings don't cause rejection.
         */
        LENIENT,

        /**
         * No profile validation is performed.
         */
        OFF
    }

    /**
     * Health check sensitivity modes.
     */
    public enum HealthCheckMode {
        /**
         * Report DOWN if no validators initialized successfully.
         */
        STRICT,

        /**
         * Report UP with warning details if no validators initialized.
         */
        WARN,

        /**
         * Always report UP regardless of validator status.
         */
        DISABLED
    }
}
