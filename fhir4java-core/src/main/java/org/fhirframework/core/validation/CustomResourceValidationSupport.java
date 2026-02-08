package org.fhirframework.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.LookupCodeRequest;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides custom FHIR conformance resources for validation.
 * <p>
 * This implementation of {@link IValidationSupport} supplies custom
 * StructureDefinitions, CodeSystems, and ValueSets loaded from the
 * {@code fhir-config/} directories to the HAPI FHIR validation chain.
 * </p>
 * <p>
 * Use this in a {@code ValidationSupportChain} before the
 * {@code DefaultProfileValidationSupport} to allow custom resources
 * to be validated.
 * </p>
 */
public class CustomResourceValidationSupport implements IValidationSupport {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceValidationSupport.class);

    private final FhirContext fhirContext;
    private final CustomResourceBundle resourceBundle;

    /**
     * Create a new CustomResourceValidationSupport.
     *
     * @param fhirContext    The FHIR context for this version
     * @param resourceBundle The bundle of custom resources to provide
     */
    public CustomResourceValidationSupport(FhirContext fhirContext, CustomResourceBundle resourceBundle) {
        this.fhirContext = fhirContext;
        this.resourceBundle = resourceBundle;

        log.info("CustomResourceValidationSupport initialized with {} StructureDefinitions, " +
                 "{} CodeSystems, {} ValueSets",
                resourceBundle.getStructureDefinitions().size(),
                resourceBundle.getCodeSystems().size(),
                resourceBundle.getValueSets().size());
    }

    @Override
    public FhirContext getFhirContext() {
        return fhirContext;
    }

    /**
     * Fetch a StructureDefinition by URL.
     * <p>
     * This is called by the validator when it needs to resolve a profile URL.
     * </p>
     */
    @Override
    public IBaseResource fetchStructureDefinition(String url) {
        if (url == null) {
            return null;
        }

        return resourceBundle.getStructureDefinition(url)
                .map(sd -> {
                    log.debug("Resolved custom StructureDefinition: {}", url);
                    return (IBaseResource) sd;
                })
                .orElse(null);
    }

    /**
     * Fetch a CodeSystem by URL.
     * <p>
     * This is called by the validator when it needs to validate codes.
     * </p>
     */
    @Override
    public IBaseResource fetchCodeSystem(String url) {
        if (url == null) {
            return null;
        }

        return resourceBundle.getCodeSystem(url)
                .map(cs -> {
                    log.debug("Resolved custom CodeSystem: {}", url);
                    return (IBaseResource) cs;
                })
                .orElse(null);
    }

    /**
     * Fetch a ValueSet by URL.
     * <p>
     * This is called by the validator when it needs to validate coded values.
     * </p>
     */
    @Override
    public IBaseResource fetchValueSet(String url) {
        if (url == null) {
            return null;
        }

        return resourceBundle.getValueSet(url)
                .map(vs -> {
                    log.debug("Resolved custom ValueSet: {}", url);
                    return (IBaseResource) vs;
                })
                .orElse(null);
    }

    /**
     * Check if a code system is supported by this validation support.
     */
    @Override
    public boolean isCodeSystemSupported(ValidationSupportContext context, String system) {
        if (system == null) {
            return false;
        }
        return resourceBundle.getCodeSystem(system).isPresent();
    }

    /**
     * Check if a value set is supported by this validation support.
     */
    @Override
    public boolean isValueSetSupported(ValidationSupportContext context, String url) {
        if (url == null) {
            return false;
        }
        return resourceBundle.getValueSet(url).isPresent();
    }

    /**
     * Validate a code against a code system.
     */
    @Override
    public CodeValidationResult validateCode(
            ValidationSupportContext context,
            ConceptValidationOptions options,
            String codeSystem,
            String code,
            String display,
            String valueSetUrl) {

        // First, try to validate against the CodeSystem directly
        if (codeSystem != null) {
            CodeSystem cs = resourceBundle.getCodeSystem(codeSystem).orElse(null);
            if (cs != null) {
                return validateCodeAgainstCodeSystem(cs, code, display);
            }
        }

        // If no CodeSystem match, try the ValueSet
        if (valueSetUrl != null) {
            ValueSet vs = resourceBundle.getValueSet(valueSetUrl).orElse(null);
            if (vs != null) {
                return validateCodeAgainstValueSet(vs, codeSystem, code, display);
            }
        }

        // Not handled by this validation support
        return null;
    }

    /**
     * Validate a code against a CodeSystem.
     */
    private CodeValidationResult validateCodeAgainstCodeSystem(CodeSystem cs, String code, String display) {
        if (cs.getConcept() == null || cs.getConcept().isEmpty()) {
            return null;
        }

        // Search for the code in the CodeSystem
        CodeSystem.ConceptDefinitionComponent foundConcept = findConcept(cs.getConcept(), code);

        if (foundConcept != null) {
            String foundDisplay = foundConcept.getDisplay();

            // Code is valid
            CodeValidationResult result = new CodeValidationResult();
            result.setCode(code);
            result.setDisplay(foundDisplay);
            result.setCodeSystemName(cs.getName());
            result.setCodeSystemVersion(cs.getVersion());

            // Check display if provided
            if (display != null && foundDisplay != null && !display.equalsIgnoreCase(foundDisplay)) {
                result.setMessage("Display '" + display + "' does not match expected '" + foundDisplay + "'");
                result.setSeverity(IssueSeverity.WARNING);
            }

            log.debug("Validated code '{}' against CodeSystem '{}'", code, cs.getUrl());
            return result;
        }

        // Code not found
        CodeValidationResult result = new CodeValidationResult();
        result.setCode(code);
        result.setSeverity(IssueSeverity.ERROR);
        result.setMessage("Unknown code '" + code + "' in CodeSystem '" + cs.getUrl() + "'");
        return result;
    }

    /**
     * Recursively find a concept in a list of concepts (supports hierarchical CodeSystems).
     */
    private CodeSystem.ConceptDefinitionComponent findConcept(
            List<CodeSystem.ConceptDefinitionComponent> concepts, String code) {
        for (CodeSystem.ConceptDefinitionComponent concept : concepts) {
            if (code.equals(concept.getCode())) {
                return concept;
            }
            // Check nested concepts
            if (concept.hasConcept()) {
                CodeSystem.ConceptDefinitionComponent found = findConcept(concept.getConcept(), code);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Validate a code against a ValueSet.
     */
    private CodeValidationResult validateCodeAgainstValueSet(
            ValueSet vs, String codeSystem, String code, String display) {

        if (!vs.hasCompose()) {
            return null;
        }

        // Check includes
        for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
            // If codeSystem is specified, check it matches
            if (codeSystem != null && include.hasSystem() && !codeSystem.equals(include.getSystem())) {
                continue;
            }

            // If specific concepts are listed, check for match
            if (include.hasConcept()) {
                for (ValueSet.ConceptReferenceComponent conceptRef : include.getConcept()) {
                    if (code.equals(conceptRef.getCode())) {
                        CodeValidationResult result = new CodeValidationResult();
                        result.setCode(code);
                        result.setDisplay(conceptRef.getDisplay());
                        log.debug("Validated code '{}' against ValueSet '{}'", code, vs.getUrl());
                        return result;
                    }
                }
            } else if (include.hasSystem() && !include.hasFilter()) {
                // ValueSet includes all codes from a system - delegate to CodeSystem validation
                CodeSystem cs = resourceBundle.getCodeSystem(include.getSystem()).orElse(null);
                if (cs != null) {
                    CodeValidationResult csResult = validateCodeAgainstCodeSystem(cs, code, display);
                    if (csResult != null && csResult.getSeverity() != IssueSeverity.ERROR) {
                        return csResult;
                    }
                }
            }
        }

        // Check if code is in excludes (if it passed includes, we need to verify it's not excluded)
        // For simplicity, we don't implement excludes here, but could be added

        return null;
    }

    /**
     * Look up a code in a code system.
     */
    @Override
    public LookupCodeResult lookupCode(ValidationSupportContext context, LookupCodeRequest request) {
        if (request == null || request.getSystem() == null || request.getCode() == null) {
            return null;
        }

        CodeSystem cs = resourceBundle.getCodeSystem(request.getSystem()).orElse(null);
        if (cs == null) {
            return null;
        }

        CodeSystem.ConceptDefinitionComponent concept = findConcept(cs.getConcept(), request.getCode());
        if (concept == null) {
            return null;
        }

        LookupCodeResult result = new LookupCodeResult();
        result.setFound(true);
        result.setCodeDisplay(concept.getDisplay());
        result.setCodeSystemDisplayName(cs.getName());
        result.setCodeSystemVersion(cs.getVersion());
        result.setSearchedForCode(request.getCode());
        result.setSearchedForSystem(request.getSystem());

        log.debug("Looked up code '{}' in CodeSystem '{}'", request.getCode(), request.getSystem());
        return result;
    }

    /**
     * Get all StructureDefinitions known to this validation support.
     */
    @Override
    public List<IBaseResource> fetchAllStructureDefinitions() {
        return new ArrayList<>(resourceBundle.getStructureDefinitions().values());
    }

    /**
     * Get all conformance resources (StructureDefinitions, CodeSystems, ValueSets).
     */
    @Override
    public List<IBaseResource> fetchAllConformanceResources() {
        List<IBaseResource> allResources = new ArrayList<>();
        allResources.addAll(resourceBundle.getStructureDefinitions().values());
        allResources.addAll(resourceBundle.getCodeSystems().values());
        allResources.addAll(resourceBundle.getValueSets().values());
        return allResources;
    }

    @Override
    public String getName() {
        return "CustomResourceValidationSupport";
    }
}
