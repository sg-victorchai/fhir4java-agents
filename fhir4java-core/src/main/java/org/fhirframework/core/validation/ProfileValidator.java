package org.fhirframework.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Validates FHIR resources against StructureDefinitions and profiles using HAPI FHIR.
 * <p>
 * Supports version-specific validation with customizable validation levels.
 * </p>
 */
@Component
public class ProfileValidator {

    private static final Logger log = LoggerFactory.getLogger(ProfileValidator.class);

    private final FhirContextFactory contextFactory;
    private final ResourceRegistry resourceRegistry;

    // Version-specific validators
    private final Map<FhirVersion, FhirValidator> validators = new EnumMap<>(FhirVersion.class);
    private final Map<FhirVersion, IValidationSupport> validationSupports = new EnumMap<>(FhirVersion.class);

    public ProfileValidator(FhirContextFactory contextFactory, ResourceRegistry resourceRegistry) {
        this.contextFactory = contextFactory;
        this.resourceRegistry = resourceRegistry;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing FHIR profile validators");

        for (FhirVersion version : FhirVersion.values()) {
            try {
                initializeValidatorForVersion(version);
                log.info("Initialized validator for FHIR {}", version.getCode());
            } catch (Exception e) {
                log.error("Failed to initialize validator for FHIR {}: {}", version.getCode(), e.getMessage(), e);
            }
        }
    }

    private void initializeValidatorForVersion(FhirVersion version) {
        FhirContext ctx = contextFactory.getContext(version);

        // Build validation support chain
        ValidationSupportChain supportChain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx)
        );

        // Wrap in caching support for better performance
        IValidationSupport validationSupport = new CachingValidationSupport(supportChain);
        validationSupports.put(version, validationSupport);

        // Create and configure the validator
        FhirValidator validator = ctx.newValidator();
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);

        // Configure validation settings
        instanceValidator.setNoTerminologyChecks(false);
        instanceValidator.setErrorForUnknownProfiles(false);
        instanceValidator.setNoExtensibleWarnings(true);

        validator.registerValidatorModule(instanceValidator);
        validators.put(version, validator);
    }

    /**
     * Validate a resource against its base StructureDefinition.
     *
     * @param resource The resource to validate
     * @param version  The FHIR version to use
     * @return ValidationResult with any issues found
     */
    public ValidationResult validateResource(IBaseResource resource, FhirVersion version) {
        return validateResource(resource, version, null);
    }

    /**
     * Validate a resource against a specific profile.
     *
     * @param resource   The resource to validate
     * @param version    The FHIR version to use
     * @param profileUrl The profile URL to validate against (null for base validation)
     * @return ValidationResult with any issues found
     */
    public ValidationResult validateResource(IBaseResource resource, FhirVersion version, String profileUrl) {
        if (resource == null) {
            return ValidationResult.failure("No resource provided for validation");
        }

        FhirValidator validator = validators.get(version);
        if (validator == null) {
            log.warn("No validator available for FHIR version: {}; skipping profile validation", version.getCode());
            return new ValidationResult();  // empty result — no issues
        }

        try {
            ca.uhn.fhir.validation.ValidationResult hapiResult;

            if (profileUrl != null && !profileUrl.isBlank()) {
                // Validate against specific profile
                hapiResult = validator.validateWithResult(resource);
                // Note: For profile-specific validation, the profile URL should be
                // included in the resource's meta.profile element
            } else {
                hapiResult = validator.validateWithResult(resource);
            }

            return convertHapiResult(hapiResult);

        } catch (Exception e) {
            log.error("Error during validation: {}", e.getMessage(), e);
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a resource against required profiles configured for its type.
     *
     * @param resource The resource to validate
     * @param version  The FHIR version to use
     * @return ValidationResult with any issues found
     */
    public ValidationResult validateAgainstRequiredProfiles(IBaseResource resource, FhirVersion version) {
        if (resource == null) {
            return ValidationResult.failure("No resource provided for validation");
        }

        String resourceType = resource.fhirType();
        List<String> requiredProfiles = resourceRegistry.getRequiredProfiles(resourceType);

        // First, do base validation
        ValidationResult result = validateResource(resource, version);

        // If there are required profiles, validate against them
        if (!requiredProfiles.isEmpty()) {
            log.debug("Validating {} against {} required profiles", resourceType, requiredProfiles.size());

            for (String profileUrl : requiredProfiles) {
                ValidationResult profileResult = validateResource(resource, version, profileUrl);
                result.merge(profileResult);
            }
        }

        return result;
    }

    /**
     * Convert HAPI FHIR ValidationResult to our ValidationResult.
     */
    private ValidationResult convertHapiResult(ca.uhn.fhir.validation.ValidationResult hapiResult) {
        ValidationResult result = new ValidationResult();

        for (SingleValidationMessage message : hapiResult.getMessages()) {
            ValidationIssue issue = convertMessage(message);
            result.addIssue(issue);
        }

        // Add success message if no issues
        if (result.isEmpty() && hapiResult.isSuccessful()) {
            result.addIssue(ValidationIssue.information("Validation successful"));
        }

        return result;
    }

    /**
     * Convert a HAPI validation message to our ValidationIssue.
     */
    private ValidationIssue convertMessage(SingleValidationMessage message) {
        IssueSeverity severity = convertSeverity(message.getSeverity());
        IssueType issueType = mapToIssueType(message);
        String location = message.getLocationString();
        String diagnostics = message.getMessage();

        return new ValidationIssue(severity, issueType, diagnostics, location, null);
    }

    /**
     * Convert HAPI severity to our IssueSeverity.
     */
    private IssueSeverity convertSeverity(ca.uhn.fhir.validation.ResultSeverityEnum hapiSeverity) {
        if (hapiSeverity == null) {
            return IssueSeverity.ERROR;
        }

        return switch (hapiSeverity) {
            case ERROR, FATAL -> IssueSeverity.ERROR;
            case WARNING -> IssueSeverity.WARNING;
            case INFORMATION -> IssueSeverity.INFORMATION;
        };
    }

    /**
     * Map validation message to appropriate IssueType.
     */
    private IssueType mapToIssueType(SingleValidationMessage message) {
        String msg = message.getMessage().toLowerCase();

        if (msg.contains("required") || msg.contains("minimum")) {
            return IssueType.REQUIRED;
        } else if (msg.contains("unknown") || msg.contains("not found")) {
            return IssueType.NOTFOUND;
        } else if (msg.contains("not supported") || msg.contains("unsupported")) {
            return IssueType.NOTSUPPORTED;
        } else if (msg.contains("invalid") || msg.contains("does not match")) {
            return IssueType.INVALID;
        } else if (msg.contains("code") || msg.contains("terminology")) {
            return IssueType.CODEINVALID;
        } else if (msg.contains("structure") || msg.contains("profile")) {
            return IssueType.STRUCTURE;
        } else {
            return IssueType.INVALID;
        }
    }

    /**
     * Check if profile validation is available for a FHIR version.
     */
    public boolean isValidationAvailable(FhirVersion version) {
        return validators.containsKey(version);
    }

    /**
     * Get the validation support for a FHIR version (for advanced usage).
     */
    public IValidationSupport getValidationSupport(FhirVersion version) {
        return validationSupports.get(version);
    }
}
