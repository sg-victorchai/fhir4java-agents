package org.fhirframework.persistence.operation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.operation.OperationContext;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.persistence.service.FhirResourceService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for the $merge operation on Patient resources.
 * <p>
 * Merges two patient records following the FHIR R5 specification:
 * <ul>
 *   <li>Accepts a Parameters resource with source-patient and target-patient references</li>
 *   <li>Marks the source patient as inactive with a replaced-by link to the target</li>
 *   <li>Returns an OperationOutcome indicating success or failure</li>
 * </ul>
 * </p>
 */
@Component
public class MergeOperationHandler implements OperationHandler {

    private static final Logger log = LoggerFactory.getLogger(MergeOperationHandler.class);

    private final FhirResourceService resourceService;
    private final FhirContextFactory contextFactory;

    public MergeOperationHandler(FhirResourceService resourceService,
                                 FhirContextFactory contextFactory) {
        this.resourceService = resourceService;
        this.contextFactory = contextFactory;
    }

    @Override
    public String getOperationName() {
        return "merge";
    }

    @Override
    public OperationScope[] getScopes() {
        return new OperationScope[]{OperationScope.TYPE};
    }

    @Override
    public String[] getResourceTypes() {
        return new String[]{"Patient"};
    }

    @Override
    public IBaseResource execute(OperationContext context) {
        log.debug("Executing $merge operation for Patient");

        IBaseResource input = context.getInputResource();
        if (input == null) {
            return createError("No input Parameters resource provided");
        }

        if (!(input instanceof Parameters params)) {
            return createError("Input must be a Parameters resource");
        }

        // Extract source-patient and target-patient references
        String sourceRef = extractReference(params, "source-patient");
        String targetRef = extractReference(params, "target-patient");

        if (sourceRef == null) {
            return createError("Missing required parameter: source-patient");
        }
        if (targetRef == null) {
            return createError("Missing required parameter: target-patient");
        }

        // Extract patient IDs from references (e.g., "Patient/123" -> "123")
        String sourceId = extractPatientId(sourceRef);
        String targetId = extractPatientId(targetRef);

        if (sourceId == null) {
            return createError("Invalid source-patient reference: " + sourceRef);
        }
        if (targetId == null) {
            return createError("Invalid target-patient reference: " + targetRef);
        }
        if (sourceId.equals(targetId)) {
            return createError("Source and target patient cannot be the same");
        }

        FhirContext fhirContext = contextFactory.getContext(context.getVersion());
        IParser parser = fhirContext.newJsonParser();

        // Read both patients
        FhirResourceService.ResourceResult sourceResult;
        FhirResourceService.ResourceResult targetResult;
        try {
            sourceResult = resourceService.read("Patient", sourceId, context.getVersion());
        } catch (Exception e) {
            return createError("Source patient not found: Patient/" + sourceId);
        }
        try {
            targetResult = resourceService.read("Patient", targetId, context.getVersion());
        } catch (Exception e) {
            return createError("Target patient not found: Patient/" + targetId);
        }

        // Parse source patient and update it
        Patient sourcePatient = (Patient) parser.parseResource(sourceResult.content());

        // Add replaced-by link to source patient
        Patient.PatientLinkComponent link = sourcePatient.addLink();
        link.setOther(new Reference("Patient/" + targetId));
        link.setType(Patient.LinkType.REPLACEDBY);

        // Mark source as inactive
        sourcePatient.setActive(false);

        // Update source patient
        String updatedSourceJson = parser.encodeResourceToString(sourcePatient);
        resourceService.update("Patient", sourceId, updatedSourceJson, context.getVersion());

        log.info("Merged Patient/{} into Patient/{} (source marked inactive with replaced-by link)",
                sourceId, targetId);

        // Return success OperationOutcome
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                .setDiagnostics(String.format(
                        "Patient/%s has been merged into Patient/%s. " +
                        "Source patient marked as inactive with replaced-by link.",
                        sourceId, targetId));

        return outcome;
    }

    private String extractReference(Parameters params, String paramName) {
        for (Parameters.ParametersParameterComponent param : params.getParameter()) {
            if (paramName.equals(param.getName())) {
                if (param.getValue() instanceof Reference ref) {
                    return ref.getReference();
                }
                // Also handle string values
                if (param.getValue() instanceof StringType str) {
                    return str.getValue();
                }
            }
        }
        return null;
    }

    private String extractPatientId(String reference) {
        if (reference == null) {
            return null;
        }
        if (reference.startsWith("Patient/")) {
            return reference.substring("Patient/".length());
        }
        // If it's just an ID without the type prefix
        if (!reference.contains("/")) {
            return reference;
        }
        return null;
    }

    private OperationOutcome createError(String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.INVALID)
                .setDiagnostics(message);
        return outcome;
    }
}
