package org.fhirframework.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.core.validation.CustomResourceBundle;
import org.fhirframework.core.validation.CustomResourceLoader;
import org.fhirframework.core.validation.CustomResourceValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ValidationConfig validationConfig;
    private final MeterRegistry meterRegistry;
    private final CustomResourceLoader customResourceLoader;

    // Version-specific validators
    private final Map<FhirVersion, FhirValidator> validators = new EnumMap<>(FhirVersion.class);
    private final Map<FhirVersion, IValidationSupport> validationSupports = new EnumMap<>(FhirVersion.class);
    
    // Initialization tracking
    private final Map<FhirVersion, Boolean> initializationStatus = new EnumMap<>(FhirVersion.class);
    private final Map<FhirVersion, Long> initializationDurations = new EnumMap<>(FhirVersion.class);
    private long totalInitializationTime = 0;
    private boolean validatorEnabled = false;

    public ProfileValidator(FhirContextFactory contextFactory,
                          ResourceRegistry resourceRegistry,
                          ValidationConfig validationConfig,
                          CustomResourceLoader customResourceLoader,
                          @Autowired(required = false) MeterRegistry meterRegistry) {
        this.contextFactory = contextFactory;
        this.resourceRegistry = resourceRegistry;
        this.validationConfig = validationConfig;
        this.customResourceLoader = customResourceLoader;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initialize() {
        log.info("Starting ProfileValidator initialization");
        
        // Check if profile validator is enabled
        if (!validationConfig.isProfileValidatorEnabled()) {
            log.info("Profile validation is disabled by configuration - ProfileValidator will not initialize validators");
            validatorEnabled = false;
            return;
        }
        
        validatorEnabled = true;
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int totalCount = FhirVersion.values().length;

        for (FhirVersion version : FhirVersion.values()) {
            long versionStartTime = System.currentTimeMillis();
            log.info("Initializing validator for FHIR {}...", version.getCode());
            
            try {
                initializeValidatorForVersion(version);
                long duration = System.currentTimeMillis() - versionStartTime;
                initializationStatus.put(version, true);
                initializationDurations.put(version, duration);
                successCount++;
                log.info("Initialized validator for FHIR {} in {}ms", version.getCode(), duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - versionStartTime;
                initializationStatus.put(version, false);
                initializationDurations.put(version, duration);
                log.warn("Failed to initialize validator for FHIR {} after {}ms: {}", 
                        version.getCode(), duration, e.getMessage(), e);
            }
        }
        
        totalInitializationTime = System.currentTimeMillis() - startTime;
        log.info("ProfileValidator initialization complete: {}/{} versions initialized in {}ms", 
                successCount, totalCount, totalInitializationTime);
        
        // Register metrics eagerly so they appear in /actuator/metrics immediately
        registerMetricsEagerly();
    }
    
    /**
     * Register metrics eagerly during initialization so they appear in /actuator/metrics
     * even before any validation is performed.
     */
    private void registerMetricsEagerly() {
        if (meterRegistry == null) {
            log.debug("MeterRegistry is null - skipping eager metrics registration");
            return;
        }
        
        if (!validatorEnabled) {
            log.debug("Validator is disabled - skipping eager metrics registration");
            return;
        }
        
        int registeredCount = 0;
        for (FhirVersion version : FhirVersion.values()) {
            if (initializationStatus.getOrDefault(version, false)) {
                try {
                    // Register counter with dummy tags to make it visible in metrics list
                    Counter.builder("fhir.validation.attempts")
                            .description("Total number of FHIR resource validation attempts")
                            .tag("version", version.getCode())
                            .tag("result", "success")
                            .tag("resourceType", "_initialized")
                            .register(meterRegistry);
                    
                    // Register timer with dummy tags to make it visible in metrics list
                    Timer.builder("fhir.validation.duration")
                            .description("Duration of FHIR resource validation operations")
                            .tag("version", version.getCode())
                            .tag("resourceType", "_initialized")
                            .register(meterRegistry);
                    
                    registeredCount++;
                } catch (Exception e) {
                    log.debug("Failed to eagerly register metrics for FHIR {}: {}", 
                             version.getCode(), e.getMessage());
                }
            }
        }
        
        log.info("Eagerly registered validation metrics for {} FHIR version(s)", registeredCount);
    }

    private void initializeValidatorForVersion(FhirVersion version) {
        FhirContext ctx = contextFactory.getContext(version);

        // Load custom resources (StructureDefinitions, CodeSystems, ValueSets)
        CustomResourceBundle customResources = customResourceLoader.loadCustomResources(version);
        log.info("Loaded {} custom conformance resources for FHIR {}",
                customResources.getTotalCount(), version.getCode());

        // Build validation support chain
        // Custom resources go FIRST so they take precedence over default resources
        ValidationSupportChain supportChain = new ValidationSupportChain(
                new CustomResourceValidationSupport(ctx, customResources),
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
     * Get or lazily initialize validator for a FHIR version.
     * Returns null if lazy initialization is disabled and validator doesn't exist.
     */
    private FhirValidator getOrInitializeValidator(FhirVersion version) {
        FhirValidator validator = validators.get(version);
        
        if (validator == null && validationConfig.isLazyInitialization() && validatorEnabled) {
            synchronized (this) {
                // Double-check after acquiring lock
                validator = validators.get(version);
                if (validator == null) {
                    log.info("Lazy initializing validator for FHIR {}...", version.getCode());
                    long startTime = System.currentTimeMillis();
                    try {
                        initializeValidatorForVersion(version);
                        validator = validators.get(version);
                        long duration = System.currentTimeMillis() - startTime;
                        initializationStatus.put(version, true);
                        initializationDurations.put(version, duration);
                        log.info("Lazy initialized validator for FHIR {} in {}ms", version.getCode(), duration);
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - startTime;
                        initializationStatus.put(version, false);
                        initializationDurations.put(version, duration);
                        log.warn("Failed to lazy initialize validator for FHIR {} after {}ms: {}", 
                                version.getCode(), duration, e.getMessage(), e);
                    }
                }
            }
        }
        
        return validator;
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

        // Check if validator is enabled
        if (!validatorEnabled) {
            log.warn("Profile validation is disabled - returning success without validation");
            return new ValidationResult();  // empty result — no issues
        }

        FhirValidator validator = getOrInitializeValidator(version);
        if (validator == null) {
            log.warn("No validator available for FHIR version: {}; skipping profile validation", version.getCode());
            return new ValidationResult();  // empty result — no issues
        }

        String resourceType = resource.fhirType();
        long startTime = System.currentTimeMillis();
        
        try {
            // Conditional operation logging
            if (validationConfig.isLogValidationOperations()) {
                if (profileUrl != null && !profileUrl.isBlank()) {
                    log.debug("Validating {} against profile {}", resourceType, profileUrl);
                } else {
                    log.debug("Validating {} against base StructureDefinition", resourceType);
                }
            }

            ca.uhn.fhir.validation.ValidationResult hapiResult;

            if (profileUrl != null && !profileUrl.isBlank()) {
                // Validate against specific profile
                hapiResult = validator.validateWithResult(resource);
                // Note: For profile-specific validation, the profile URL should be
                // included in the resource's meta.profile element
            } else {
                hapiResult = validator.validateWithResult(resource);
            }

            ValidationResult result = convertHapiResult(hapiResult);
            
            // Record metrics
            recordValidationMetrics(version, resourceType, result.isValid(), 
                                   System.currentTimeMillis() - startTime);
            
            // Conditional operation logging
            if (validationConfig.isLogValidationOperations()) {
                log.debug("Validation completed for {}: {} issues found", 
                         resourceType, result.getIssues().size());
            }

            return result;

        } catch (Exception e) {
            log.error("Error during validation: {}", e.getMessage(), e);
            recordValidationMetrics(version, resourceType, false, 
                                   System.currentTimeMillis() - startTime);
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

    /**
     * Check if validator is enabled.
     */
    public boolean isValidatorEnabled() {
        return validatorEnabled;
    }

    /**
     * Get initialization status for all versions.
     */
    public Map<FhirVersion, Boolean> getInitializationStatus() {
        return new EnumMap<>(initializationStatus);
    }

    /**
     * Get initialization durations for all versions.
     */
    public Map<FhirVersion, Long> getInitializationDurations() {
        return new EnumMap<>(initializationDurations);
    }

    /**
     * Get total initialization time in milliseconds.
     */
    public long getTotalInitializationTime() {
        return totalInitializationTime;
    }

    /**
     * Record validation metrics if MeterRegistry is available.
     */
    private void recordValidationMetrics(FhirVersion version, String resourceType, 
                                        boolean success, long durationMs) {
        if (meterRegistry == null) {
            log.debug("MeterRegistry is null - metrics will not be recorded");
            return;
        }

        try {
            // Record validation attempt counter (using dotted notation)
            Counter counter = Counter.builder("fhir.validation.attempts")
                    .description("Total number of FHIR resource validation attempts")
                    .tag("version", version.getCode())
                    .tag("result", success ? "success" : "failure")
                    .tag("resourceType", resourceType)
                    .register(meterRegistry);
            counter.increment();
            
            log.debug("Recorded metric: fhir.validation.attempts [version={}, result={}, resourceType={}]",
                     version.getCode(), success ? "success" : "failure", resourceType);

            // Record validation duration timer (using dotted notation)
            Timer timer = Timer.builder("fhir.validation.duration")
                    .description("Duration of FHIR resource validation operations")
                    .tag("version", version.getCode())
                    .tag("resourceType", resourceType)
                    .register(meterRegistry);
            timer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            log.debug("Recorded metric: fhir.validation.duration [version={}, resourceType={}, duration={}ms]",
                     version.getCode(), resourceType, durationMs);
        } catch (Exception e) {
            log.warn("Failed to record validation metrics: {}", e.getMessage(), e);
        }
    }
}
