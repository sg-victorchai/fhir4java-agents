package org.fhirframework.plugin.sample;

import org.fhirframework.core.service.ResourceLookupService;
import org.fhirframework.core.service.ResourceLookupService.IdentifierToken;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.plugin.OperationDescriptor;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.business.BusinessContext;
import org.fhirframework.plugin.business.BusinessLogicPlugin;
import org.fhirframework.plugin.business.OperationResult;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.HumanName;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.r5.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sample business logic plugin for Patient CREATE operations.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>Custom validation (require family name)</li>
 *   <li>Resource enrichment (add system identifier, creation timestamp)</li>
 *   <li>Duplicate identifier check via FHIR search</li>
 *   <li>Post-creation logging</li>
 * </ul>
 * </p>
 */
@Component
public class PatientCreatePlugin implements BusinessLogicPlugin {

    private static final Logger log = LoggerFactory.getLogger(PatientCreatePlugin.class);

    private final ResourceLookupService resourceLookupService;

    @Value("${fhir4java.plugins.patient-create.enabled:true}")
    private boolean enabled;

    @Value("${fhir4java.plugins.patient-create.require-family-name:true}")
    private boolean requireFamilyName;

    @Value("${fhir4java.plugins.patient-create.auto-generate-mrn:true}")
    private boolean autoGenerateMrn;

    @Value("${fhir4java.plugins.patient-create.mrn-system:urn:fhir4java:patient:mrn}")
    private String mrnSystem;

    @Value("${fhir4java.plugins.patient-create.add-creation-timestamp:true}")
    private boolean addCreationTimestamp;

    @Value("${fhir4java.plugins.patient-create.creation-timestamp-url:http://fhir4java.org/StructureDefinition/creation-timestamp}")
    private String creationTimestampUrl;

    @Value("${fhir4java.plugins.patient-create.duplicate-check-enabled:true}")
    private boolean duplicateCheckEnabled;

    @Value("${fhir4java.plugins.patient-create.duplicate-check-systems:}")
    private List<String> duplicateCheckSystems;

    @Autowired(required = false)
    public PatientCreatePlugin(ResourceLookupService resourceLookupService) {
        this.resourceLookupService = resourceLookupService;
    }

    public PatientCreatePlugin() {
        this.resourceLookupService = null;
    }

    @Override
    public String getName() {
        return "patient-create-plugin";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        // Run after standard validation but before persistence
        return 60;
    }

    @Override
    public List<OperationDescriptor> getSupportedOperations() {
        // Only handle Patient CREATE operations
        return List.of(
                OperationDescriptor.forCrud("Patient", OperationType.CREATE, null)
        );
    }

    @Override
    public BusinessResult beforeOperation(BusinessContext context) {
        log.debug("PatientCreatePlugin: beforeOperation for request {}", context.getRequestId());

        // Get the input resource
        Patient patient = context.getCurrentResource()
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .orElse(null);

        if (patient == null) {
            return BusinessResult.abort("Expected Patient resource but got null or different type");
        }

        // 1. Custom Validation: Require family name if configured
        if (requireFamilyName) {
            BusinessResult validationResult = validateFamilyName(patient);
            if (validationResult.isAborted()) {
                return validationResult;
            }
        }

        // 2. Validation: Check SSN format if present
        BusinessResult ssnCheck = validateSsnFormat(patient);
        if (ssnCheck.isAborted()) {
            return ssnCheck;
        }

        // 3. Validation: Check for duplicate identifiers via FHIR search
        if (duplicateCheckEnabled) {
            BusinessResult duplicateCheck = checkForDuplicateIdentifiers(patient, context);
            if (duplicateCheck.isAborted()) {
                return duplicateCheck;
            }
        }

        // 4. Enrichment: Auto-generate MRN if configured and not present
        if (autoGenerateMrn) {
            enrichWithMrn(patient);
        }

        // 5. Enrichment: Add creation timestamp extension if configured
        if (addCreationTimestamp) {
            enrichWithCreationTimestamp(patient);
        }

        // 6. Normalize contact information
        normalizeContactInfo(patient);

        log.info("PatientCreatePlugin: Validated and enriched Patient for request {}", context.getRequestId());

        // Return modified resource
        return BusinessResult.proceedWithResource(patient);
    }

    @Override
    public BusinessResult afterOperation(BusinessContext context, OperationResult result) {
        if (result.isSuccess()) {
            // Log successful patient creation
            result.getResource().ifPresent(resource -> {
                if (resource instanceof Patient patient) {
                    String patientId = patient.getIdElement().getIdPart();
                    String familyName = getFamilyName(patient);

                    log.info("PatientCreatePlugin: Successfully created Patient/{} (name: {})",
                            patientId, familyName);
                }
            });
        }

        return BusinessResult.proceed();
    }

    /**
     * Validate that the patient has at least one name with a family component.
     */
    private BusinessResult validateFamilyName(Patient patient) {
        boolean hasFamilyName = patient.getName().stream()
                .anyMatch(name -> name.hasFamily() && !name.getFamily().isBlank());

        if (!hasFamilyName) {
            log.warn("Patient validation failed: missing family name");
            return BusinessResult.unprocessable(
                    "Patient must have at least one name with a family name component"
            );
        }

        return BusinessResult.proceed();
    }

    /**
     * Validate SSN format if an SSN identifier is present.
     */
    private BusinessResult validateSsnFormat(Patient patient) {
        for (Identifier identifier : patient.getIdentifier()) {
            if ("http://hl7.org/fhir/sid/us-ssn".equals(identifier.getSystem())) {
                String ssn = identifier.getValue();
                if (ssn != null && !ssn.matches("\\d{3}-\\d{2}-\\d{4}")) {
                    return BusinessResult.abort(
                            "Invalid SSN format. Expected XXX-XX-XXXX"
                    );
                }
            }
        }

        return BusinessResult.proceed();
    }

    /**
     * Check for duplicate identifiers using FHIR search via {@link ResourceLookupService}.
     * <p>
     * Collects identifiers filtered by {@code duplicate-check-systems} (or all identifiers
     * if the list is empty) and searches for existing resources with matching identifiers.
     * Returns a conflict result if any duplicates are found.
     * </p>
     */
    private BusinessResult checkForDuplicateIdentifiers(Patient patient, BusinessContext context) {
        if (resourceLookupService == null) {
            log.warn("ResourceLookupService not available; skipping duplicate identifier check");
            return BusinessResult.proceed();
        }

        List<Identifier> identifiersToCheck = patient.getIdentifier();
        if (identifiersToCheck.isEmpty()) {
            return BusinessResult.proceed();
        }

        // Filter by configured systems if specified
        if (duplicateCheckSystems != null && !duplicateCheckSystems.isEmpty()) {
            identifiersToCheck = identifiersToCheck.stream()
                    .filter(id -> id.hasSystem() && duplicateCheckSystems.contains(id.getSystem()))
                    .collect(Collectors.toList());
        }

        if (identifiersToCheck.isEmpty()) {
            return BusinessResult.proceed();
        }

        // Convert to IdentifierTokens for the lookup service
        List<IdentifierToken> tokens = identifiersToCheck.stream()
                .filter(id -> id.hasSystem() && id.hasValue())
                .map(id -> new IdentifierToken(id.getSystem(), id.getValue()))
                .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            return BusinessResult.proceed();
        }

        FhirVersion version = context.getFhirVersion();
        String resourceType = context.getResourceType();

        List<IdentifierToken> existing = resourceLookupService.findExistingIdentifiers(
                resourceType, version, tokens);

        if (!existing.isEmpty()) {
            String duplicates = existing.stream()
                    .map(IdentifierToken::toSearchToken)
                    .collect(Collectors.joining(", "));

            log.warn("Duplicate identifiers found for Patient: {}", duplicates);

            return BusinessResult.conflict(
                    "Patient with duplicate identifier(s) already exists: " + duplicates
            );
        }

        return BusinessResult.proceed();
    }

    /**
     * Add a system-generated MRN (Medical Record Number) if not present.
     */
    private void enrichWithMrn(Patient patient) {
        boolean hasMrn = patient.getIdentifier().stream()
                .anyMatch(id -> mrnSystem.equals(id.getSystem()));

        if (!hasMrn) {
            String mrn = generateMrn();
            Identifier mrnIdentifier = new Identifier()
                    .setSystem(mrnSystem)
                    .setValue(mrn)
                    .setUse(Identifier.IdentifierUse.OFFICIAL);

            patient.addIdentifier(mrnIdentifier);
            log.debug("Added auto-generated MRN: {} (system: {})", mrn, mrnSystem);
        }
    }

    /**
     * Generate a unique MRN.
     */
    private String generateMrn() {
        // Format: MRN-YYYYMMDD-XXXXX (year/month/day + random)
        String datePart = java.time.LocalDate.now().toString().replace("-", "");
        String randomPart = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        return "MRN-" + datePart + "-" + randomPart;
    }

    /**
     * Add a creation timestamp extension.
     */
    private void enrichWithCreationTimestamp(Patient patient) {
        boolean hasTimestamp = patient.getExtension().stream()
                .anyMatch(ext -> creationTimestampUrl.equals(ext.getUrl()));

        if (!hasTimestamp) {
            Extension timestampExt = new Extension()
                    .setUrl(creationTimestampUrl)
                    .setValue(new StringType(Instant.now().toString()));

            patient.addExtension(timestampExt);
            log.debug("Added creation timestamp extension (url: {})", creationTimestampUrl);
        }
    }

    /**
     * Normalize contact information (example transformation).
     */
    private void normalizeContactInfo(Patient patient) {
        for (ContactPoint telecom : patient.getTelecom()) {
            // Normalize phone numbers (remove non-digits except +)
            if (telecom.getSystem() == ContactPoint.ContactPointSystem.PHONE && telecom.hasValue()) {
                String normalized = telecom.getValue().replaceAll("[^\\d+]", "");
                if (!normalized.equals(telecom.getValue())) {
                    telecom.setValue(normalized);
                    log.debug("Normalized phone number");
                }
            }

            // Lowercase email addresses
            if (telecom.getSystem() == ContactPoint.ContactPointSystem.EMAIL && telecom.hasValue()) {
                telecom.setValue(telecom.getValue().toLowerCase());
            }
        }
    }

    /**
     * Get the family name for logging.
     */
    private String getFamilyName(Patient patient) {
        return patient.getName().stream()
                .filter(HumanName::hasFamily)
                .map(HumanName::getFamily)
                .findFirst()
                .orElse("Unknown");
    }
}
