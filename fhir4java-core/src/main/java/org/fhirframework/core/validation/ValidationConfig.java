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

    @Value("${fhir4java.validation.profile-validation:lenient}")
    private String profileValidationStr;

    @Value("${fhir4java.validation.validate-search-parameters:true}")
    private boolean validateSearchParameters;

    @Value("${fhir4java.validation.fail-on-unknown-search-parameters:false}")
    private boolean failOnUnknownSearchParameters;

    public boolean isEnabled() {
        return enabled;
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
}
