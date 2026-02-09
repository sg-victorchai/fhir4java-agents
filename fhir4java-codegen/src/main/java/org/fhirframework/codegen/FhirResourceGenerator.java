package org.fhirframework.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main orchestrator for generating Java classes from FHIR StructureDefinitions.
 */
public class FhirResourceGenerator {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceGenerator.class);

    private final StructureDefinitionParser parser;
    private final JavaClassBuilder classBuilder;

    public FhirResourceGenerator() {
        this.parser = new StructureDefinitionParser();
        this.classBuilder = new JavaClassBuilder();
    }

    /**
     * Generate Java classes from all StructureDefinition files in a directory.
     *
     * @param structureDefinitionDir Directory containing StructureDefinition JSON files
     * @param outputDirectory        Output directory for generated Java sources
     * @param packageName            Package name for generated classes
     * @return List of generated class names
     * @throws IOException If files cannot be read or written
     */
    public List<String> generateClasses(File structureDefinitionDir, File outputDirectory, String packageName)
            throws IOException {
        
        if (!structureDefinitionDir.exists() || !structureDefinitionDir.isDirectory()) {
            log.warn("StructureDefinition directory does not exist or is not a directory: {}",
                    structureDefinitionDir.getAbsolutePath());
            return new ArrayList<>();
        }

        log.info("Scanning for StructureDefinitions in: {}", structureDefinitionDir.getAbsolutePath());
        log.info("Output directory: {}", outputDirectory.getAbsolutePath());
        log.info("Package name: {}", packageName);

        List<String> generatedClasses = new ArrayList<>();
        File[] files = structureDefinitionDir.listFiles((dir, name) ->
                name.startsWith("StructureDefinition-") && name.endsWith(".json"));

        if (files == null || files.length == 0) {
            log.warn("No StructureDefinition files found matching pattern: StructureDefinition-*.json");
            return generatedClasses;
        }

        log.info("Found {} StructureDefinition file(s)", files.length);

        for (File file : files) {
            try {
                log.info("Processing: {}", file.getName());

                // Parse the StructureDefinition
                StructureDefinitionParser.StructureDefinitionMetadata metadata = parser.parse(file);

                // Only generate classes for custom resources (specializations, not constraints/profiles)
                if (!metadata.isCustomResource()) {
                    log.debug("Skipping {} - not a custom resource (derivation={}, kind={}, abstract={})",
                            metadata.getName(), metadata.getDerivation(), metadata.getKind(), metadata.isAbstractResource());
                    continue;
                }

                // Skip standard FHIR resources that already exist in HAPI library
                if (isStandardFhirResource(metadata.getName())) {
                    log.debug("Skipping {} - standard FHIR resource already in HAPI", metadata.getName());
                    continue;
                }

                // Generate Java class
                classBuilder.build(metadata, packageName, outputDirectory);

                String generatedClassName = packageName + "." + metadata.getName();
                generatedClasses.add(generatedClassName);

                log.info("Successfully generated class: {}", generatedClassName);

            } catch (Exception e) {
                log.error("Failed to generate class from {}: {}", file.getName(), e.getMessage(), e);
                // Continue processing other files
            }
        }

        log.info("Code generation complete. Generated {} class(es)", generatedClasses.size());
        return generatedClasses;
    }

    /**
     * Generate a single Java class from a StructureDefinition file.
     *
     * @param structureDefinitionFile The StructureDefinition JSON file
     * @param outputDirectory         Output directory for generated Java source
     * @param packageName             Package name for generated class
     * @return The generated class name, or null if generation failed
     * @throws IOException If file cannot be read or written
     */
    public String generateClass(File structureDefinitionFile, File outputDirectory, String packageName)
            throws IOException {
        
        log.info("Generating class from: {}", structureDefinitionFile.getName());

        // Parse the StructureDefinition
        StructureDefinitionParser.StructureDefinitionMetadata metadata = parser.parse(structureDefinitionFile);

        // Generate Java class
        classBuilder.build(metadata, packageName, outputDirectory);

        String generatedClassName = packageName + "." + metadata.getName();
        log.info("Successfully generated class: {}", generatedClassName);

        return generatedClassName;
    }

    /**
     * Check if a resource name is a standard FHIR resource that already exists in the HAPI library.
     * These should not be regenerated as they're already provided by HAPI FHIR.
     */
    private boolean isStandardFhirResource(String resourceName) {
        return STANDARD_FHIR_RESOURCES.contains(resourceName);
    }

    /**
     * Set of standard FHIR R5 resources that exist in HAPI FHIR library.
     * Custom resources should NOT be in this list.
     */
    private static final Set<String> STANDARD_FHIR_RESOURCES = Set.of(
            "Account", "ActivityDefinition", "ActorDefinition", "AdministrableProductDefinition",
            "AdverseEvent", "AllergyIntolerance", "Appointment", "AppointmentResponse",
            "ArtifactAssessment", "AuditEvent", "Basic", "Binary", "BiologicallyDerivedProduct",
            "BiologicallyDerivedProductDispense", "BodyStructure", "Bundle", "CapabilityStatement",
            "CarePlan", "CareTeam", "ChargeItem", "ChargeItemDefinition", "Citation", "Claim",
            "ClaimResponse", "ClinicalImpression", "ClinicalUseDefinition", "CodeSystem",
            "Communication", "CommunicationRequest", "CompartmentDefinition", "Composition",
            "ConceptMap", "Condition", "ConditionDefinition", "Consent", "Contract", "Coverage",
            "CoverageEligibilityRequest", "CoverageEligibilityResponse", "DetectedIssue", "Device",
            "DeviceAssociation", "DeviceDefinition", "DeviceDispense", "DeviceMetric", "DeviceRequest",
            "DeviceUsage", "DiagnosticReport", "DocumentReference", "Encounter", "EncounterHistory",
            "Endpoint", "EnrollmentRequest", "EnrollmentResponse", "EpisodeOfCare", "EventDefinition",
            "Evidence", "EvidenceReport", "EvidenceVariable", "ExampleScenario", "ExplanationOfBenefit",
            "FamilyMemberHistory", "Flag", "FormularyItem", "GenomicStudy", "Goal", "GraphDefinition",
            "Group", "GuidanceResponse", "HealthcareService", "ImagingSelection", "ImagingStudy",
            "Immunization", "ImmunizationEvaluation", "ImmunizationRecommendation", "ImplementationGuide",
            "Ingredient", "InsurancePlan", "InventoryItem", "InventoryReport", "Invoice", "Library",
            "Linkage", "List", "Location", "ManufacturedItemDefinition", "Measure", "MeasureReport",
            "Medication", "MedicationAdministration", "MedicationDispense", "MedicationKnowledge",
            "MedicationRequest", "MedicationStatement", "MedicinalProductDefinition", "MessageDefinition",
            "MessageHeader", "MolecularSequence", "NamingSystem", "NutritionIntake", "NutritionOrder",
            "NutritionProduct", "Observation", "ObservationDefinition", "OperationDefinition",
            "OperationOutcome", "Organization", "OrganizationAffiliation", "PackagedProductDefinition",
            "Parameters", "Patient", "PaymentNotice", "PaymentReconciliation", "Permission", "Person",
            "PlanDefinition", "Practitioner", "PractitionerRole", "Procedure", "Provenance",
            "Questionnaire", "QuestionnaireResponse", "RegulatedAuthorization", "RelatedPerson",
            "RequestOrchestration", "Requirements", "ResearchStudy", "ResearchSubject", "RiskAssessment",
            "Schedule", "SearchParameter", "ServiceRequest", "Slot", "Specimen", "SpecimenDefinition",
            "StructureDefinition", "StructureMap", "Subscription", "SubscriptionStatus", "SubscriptionTopic",
            "Substance", "SubstanceDefinition", "SubstanceNucleicAcid", "SubstancePolymer", "SubstanceProtein",
            "SubstanceReferenceInformation", "SubstanceSourceMaterial", "SupplyDelivery", "SupplyRequest",
            "Task", "TerminologyCapabilities", "TestPlan", "TestReport", "TestScript", "Transport",
            "ValueSet", "VerificationResult", "VisionPrescription"
    );
}
