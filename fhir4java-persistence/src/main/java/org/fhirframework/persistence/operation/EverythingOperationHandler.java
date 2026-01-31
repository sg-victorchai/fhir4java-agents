package org.fhirframework.persistence.operation;

import org.fhirframework.core.operation.OperationContext;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.service.FhirResourceService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for the $everything operation on Patient resources.
 * <p>
 * Returns all resources in the patient compartment including the Patient
 * resource itself and related Condition, Observation, Encounter,
 * MedicationRequest, Procedure, and CarePlan resources.
 * </p>
 */
@Component
public class EverythingOperationHandler implements OperationHandler {

    private static final Logger log = LoggerFactory.getLogger(EverythingOperationHandler.class);

    private static final List<String> COMPARTMENT_TYPES = List.of(
            "Condition", "Observation", "Encounter",
            "MedicationRequest", "Procedure", "CarePlan"
    );

    private static final int DEFAULT_COUNT = 100;

    private final FhirResourceService resourceService;

    public EverythingOperationHandler(FhirResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Override
    public String getOperationName() {
        return "everything";
    }

    @Override
    public OperationScope[] getScopes() {
        return new OperationScope[]{OperationScope.TYPE, OperationScope.INSTANCE};
    }

    @Override
    public String[] getResourceTypes() {
        return new String[]{"Patient"};
    }

    @Override
    public IBaseResource execute(OperationContext context) {
        log.debug("Executing $everything operation (scope={}, resourceId={})",
                context.getScope(), context.getResourceId());

        int count = parseCount(context);
        String since = context.getParameters().get("_since");

        if (context.getScope() == OperationScope.INSTANCE) {
            return executeInstance(context.getResourceId(), context.getVersion(), count, since);
        } else {
            return executeType(context.getVersion(), count, since);
        }
    }

    private IBaseResource executeInstance(String patientId, FhirVersion version, int count, String since) {
        log.debug("Instance-level $everything for Patient/{}", patientId);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.SEARCHSET);

        // Read the Patient resource itself
        try {
            FhirResourceService.ResourceResult patientResult = resourceService.read("Patient", patientId, version);
            addToBundle(resultBundle, "Patient", patientId, patientResult);
        } catch (Exception e) {
            log.warn("Patient/{} not found for $everything: {}", patientId, e.getMessage());
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.NOTFOUND)
                    .setDiagnostics("Patient/" + patientId + " not found");
            return outcome;
        }

        // Search each compartment type for related resources
        for (String compartmentType : COMPARTMENT_TYPES) {
            searchCompartment(resultBundle, compartmentType, patientId, version, count, since);
        }

        resultBundle.setTotal(resultBundle.getEntry().size());
        log.debug("$everything for Patient/{} returned {} resources", patientId, resultBundle.getEntry().size());

        return resultBundle;
    }

    private IBaseResource executeType(FhirVersion version, int count, String since) {
        log.debug("Type-level $everything for Patient");

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.SEARCHSET);

        // Search all patients (paginated)
        Map<String, String> patientParams = new HashMap<>();
        patientParams.put("_count", String.valueOf(count));
        if (since != null) {
            patientParams.put("_lastUpdated", "ge" + since);
        }

        Bundle patientBundle = resourceService.search("Patient", patientParams, version, count);

        // Add patients and their compartment resources
        for (Bundle.BundleEntryComponent patientEntry : patientBundle.getEntry()) {
            resultBundle.addEntry(patientEntry);

            if (patientEntry.getResource() != null && patientEntry.getResource().getIdPart() != null) {
                String patientId = patientEntry.getResource().getIdPart();
                for (String compartmentType : COMPARTMENT_TYPES) {
                    searchCompartment(resultBundle, compartmentType, patientId, version, count, since);
                }
            }
        }

        resultBundle.setTotal(resultBundle.getEntry().size());
        log.debug("Type-level $everything returned {} resources", resultBundle.getEntry().size());

        return resultBundle;
    }

    private void searchCompartment(Bundle resultBundle, String compartmentType, String patientId,
                                   FhirVersion version, int count, String since) {
        try {
            Map<String, String> searchParams = new HashMap<>();
            searchParams.put("patient", "Patient/" + patientId);
            searchParams.put("_count", String.valueOf(count));
            if (since != null) {
                searchParams.put("_lastUpdated", "ge" + since);
            }

            Bundle compartmentBundle = resourceService.search(compartmentType, searchParams, version, count);

            for (Bundle.BundleEntryComponent entry : compartmentBundle.getEntry()) {
                resultBundle.addEntry(entry);
            }
        } catch (Exception e) {
            log.debug("No {} resources found for Patient/{}: {}", compartmentType, patientId, e.getMessage());
        }
    }

    private void addToBundle(Bundle bundle, String resourceType, String resourceId,
                             FhirResourceService.ResourceResult result) {
        Bundle.BundleEntryComponent entry = bundle.addEntry();
        entry.setFullUrl("urn:uuid:" + resourceType + "/" + resourceId);

        // Parse the content to a Resource using the stored JSON
        ca.uhn.fhir.context.FhirContext fhirContext = ca.uhn.fhir.context.FhirContext.forR5Cached();
        IBaseResource resource = fhirContext.newJsonParser().parseResource(result.content());
        entry.setResource((org.hl7.fhir.r5.model.Resource) resource);
    }

    private int parseCount(OperationContext context) {
        String countStr = context.getParameters().get("_count");
        if (countStr != null) {
            try {
                return Math.min(Integer.parseInt(countStr), 1000);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        return DEFAULT_COUNT;
    }
}
