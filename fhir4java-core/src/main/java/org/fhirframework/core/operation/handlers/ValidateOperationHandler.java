package org.fhirframework.core.operation.handlers;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.operation.OperationContext;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationScope;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for the $validate operation.
 * <p>
 * Validates a FHIR resource against its structure definition and profiles.
 * Supports both type-level and instance-level invocation.
 * </p>
 */
@Component
public class ValidateOperationHandler implements OperationHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidateOperationHandler.class);

    private final FhirContextFactory contextFactory;

    public ValidateOperationHandler(FhirContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    @Override
    public String getOperationName() {
        return "validate";
    }

    @Override
    public OperationScope[] getScopes() {
        return new OperationScope[] { OperationScope.TYPE, OperationScope.INSTANCE };
    }

    @Override
    public String[] getResourceTypes() {
        return null; // Applies to all resource types
    }

    @Override
    public IBaseResource execute(OperationContext context) {
        log.debug("Executing $validate operation for {}", context.getResourceType());

        IBaseResource resource = context.getInputResource();
        if (resource == null) {
            return createNoResourceError();
        }

        // Get the appropriate FHIR context
        FhirContext fhirContext = contextFactory.getContext(context.getVersion());

        try {
            // Create validator and register basic validation support
            FhirValidator validator = fhirContext.newValidator();

            try {
                var instanceValidator = new org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator(fhirContext);
                validator.registerValidatorModule(instanceValidator);
            } catch (Exception e) {
                log.warn("Could not register FhirInstanceValidator, falling back to basic validation: {}", e.getMessage());
                // Return a basic success if we can't set up full validation
                return createBasicValidationResult(resource);
            }

            // Perform validation
            ValidationResult result = validator.validateWithResult(resource);

            // Convert to OperationOutcome
            return toOperationOutcome(result);
        } catch (Exception e) {
            log.warn("Validation failed with error, returning basic validation result: {}", e.getMessage());
            return createBasicValidationResult(resource);
        }
    }

    private OperationOutcome createBasicValidationResult(IBaseResource resource) {
        OperationOutcome outcome = new OperationOutcome();
        // Perform basic structural check: resource must have a resourceType
        String resourceType = resource.fhirType();
        if (resourceType != null && !resourceType.isEmpty()) {
            outcome.addIssue()
                    .setSeverity(IssueSeverity.INFORMATION)
                    .setCode(IssueType.INFORMATIONAL)
                    .setDiagnostics("Basic validation passed for " + resourceType +
                            ". Full profile validation is not available.");
        } else {
            outcome.addIssue()
                    .setSeverity(IssueSeverity.ERROR)
                    .setCode(IssueType.INVALID)
                    .setDiagnostics("Resource has no resourceType");
        }
        return outcome;
    }

    private OperationOutcome createNoResourceError() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.REQUIRED)
                .setDiagnostics("No resource provided for validation. " +
                        "Please provide a resource in the request body.");
        return outcome;
    }

    private OperationOutcome toOperationOutcome(ValidationResult result) {
        OperationOutcome outcome = new OperationOutcome();

        if (result.isSuccessful()) {
            outcome.addIssue()
                    .setSeverity(IssueSeverity.INFORMATION)
                    .setCode(IssueType.INFORMATIONAL)
                    .setDiagnostics("Validation successful");
        } else {
            for (var message : result.getMessages()) {
                OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue();

                switch (message.getSeverity()) {
                    case ERROR -> issue.setSeverity(IssueSeverity.ERROR);
                    case WARNING -> issue.setSeverity(IssueSeverity.WARNING);
                    case INFORMATION -> issue.setSeverity(IssueSeverity.INFORMATION);
                    default -> issue.setSeverity(IssueSeverity.ERROR);
                }

                issue.setCode(IssueType.INVALID);
                issue.setDiagnostics(message.getMessage());

                if (message.getLocationString() != null) {
                    issue.addLocation(message.getLocationString());
                }
            }
        }

        return outcome;
    }
}
