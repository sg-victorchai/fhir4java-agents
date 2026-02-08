package org.fhirframework.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport.CodeValidationResult;
import ca.uhn.fhir.context.support.IValidationSupport.IssueSeverity;
import ca.uhn.fhir.context.support.IValidationSupport.LookupCodeResult;
import ca.uhn.fhir.context.support.LookupCodeRequest;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomResourceValidationSupport")
class CustomResourceValidationSupportTest {

    private static final String SD_URL = "http://fhir4java.org/StructureDefinition/TestResource";
    private static final String CS_URL = "http://fhir4java.org/CodeSystem/test-status";
    private static final String VS_URL = "http://fhir4java.org/ValueSet/test-status";

    private FhirContext fhirContext;
    private CustomResourceBundle resourceBundle;
    private CustomResourceValidationSupport validationSupport;
    private ValidationSupportContext validationSupportContext;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR5();

        // Create test StructureDefinition
        StructureDefinition testSD = new StructureDefinition();
        testSD.setUrl(SD_URL);
        testSD.setName("TestResource");
        testSD.setKind(StructureDefinition.StructureDefinitionKind.RESOURCE);

        // Create test CodeSystem with concepts
        CodeSystem testCS = new CodeSystem();
        testCS.setUrl(CS_URL);
        testCS.setName("TestStatusCodeSystem");
        testCS.setVersion("1.0.0");
        testCS.addConcept()
                .setCode("active")
                .setDisplay("Active");
        testCS.addConcept()
                .setCode("inactive")
                .setDisplay("Inactive");

        // Create test ValueSet
        ValueSet testVS = new ValueSet();
        testVS.setUrl(VS_URL);
        testVS.setName("TestStatusValueSet");
        testVS.getCompose().addInclude()
                .setSystem(CS_URL)
                .addConcept().setCode("active").setDisplay("Active");

        // Build the bundle
        Map<String, StructureDefinition> structDefs = new HashMap<>();
        structDefs.put(SD_URL, testSD);

        Map<String, CodeSystem> codeSystems = new HashMap<>();
        codeSystems.put(CS_URL, testCS);

        Map<String, ValueSet> valueSets = new HashMap<>();
        valueSets.put(VS_URL, testVS);

        resourceBundle = CustomResourceBundle.builder()
                .structureDefinitions(structDefs)
                .codeSystems(codeSystems)
                .valueSets(valueSets)
                .build();

        validationSupport = new CustomResourceValidationSupport(fhirContext, resourceBundle);
        validationSupportContext = new ValidationSupportContext(validationSupport);
    }

    @Nested
    @DisplayName("fetchStructureDefinition")
    class FetchStructureDefinition {

        @Test
        @DisplayName("should return StructureDefinition for known URL")
        void shouldReturnStructureDefinitionForKnownUrl() {
            IBaseResource result = validationSupport.fetchStructureDefinition(SD_URL);

            assertNotNull(result);
            assertTrue(result instanceof StructureDefinition);
            assertEquals("TestResource", ((StructureDefinition) result).getName());
        }

        @Test
        @DisplayName("should return null for unknown URL")
        void shouldReturnNullForUnknownUrl() {
            IBaseResource result = validationSupport.fetchStructureDefinition("http://unknown.org/SD");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null URL")
        void shouldReturnNullForNullUrl() {
            IBaseResource result = validationSupport.fetchStructureDefinition(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("fetchCodeSystem")
    class FetchCodeSystem {

        @Test
        @DisplayName("should return CodeSystem for known URL")
        void shouldReturnCodeSystemForKnownUrl() {
            IBaseResource result = validationSupport.fetchCodeSystem(CS_URL);

            assertNotNull(result);
            assertTrue(result instanceof CodeSystem);
            assertEquals("TestStatusCodeSystem", ((CodeSystem) result).getName());
        }

        @Test
        @DisplayName("should return null for unknown URL")
        void shouldReturnNullForUnknownUrl() {
            IBaseResource result = validationSupport.fetchCodeSystem("http://unknown.org/CS");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null URL")
        void shouldReturnNullForNullUrl() {
            IBaseResource result = validationSupport.fetchCodeSystem(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("fetchValueSet")
    class FetchValueSet {

        @Test
        @DisplayName("should return ValueSet for known URL")
        void shouldReturnValueSetForKnownUrl() {
            IBaseResource result = validationSupport.fetchValueSet(VS_URL);

            assertNotNull(result);
            assertTrue(result instanceof ValueSet);
            assertEquals("TestStatusValueSet", ((ValueSet) result).getName());
        }

        @Test
        @DisplayName("should return null for unknown URL")
        void shouldReturnNullForUnknownUrl() {
            IBaseResource result = validationSupport.fetchValueSet("http://unknown.org/VS");

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null URL")
        void shouldReturnNullForNullUrl() {
            IBaseResource result = validationSupport.fetchValueSet(null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("isCodeSystemSupported")
    class IsCodeSystemSupported {

        @Test
        @DisplayName("should return true for known CodeSystem")
        void shouldReturnTrueForKnownCodeSystem() {
            boolean result = validationSupport.isCodeSystemSupported(validationSupportContext, CS_URL);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false for unknown CodeSystem")
        void shouldReturnFalseForUnknownCodeSystem() {
            boolean result = validationSupport.isCodeSystemSupported(validationSupportContext, "http://unknown.org/CS");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for null URL")
        void shouldReturnFalseForNullUrl() {
            boolean result = validationSupport.isCodeSystemSupported(validationSupportContext, null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isValueSetSupported")
    class IsValueSetSupported {

        @Test
        @DisplayName("should return true for known ValueSet")
        void shouldReturnTrueForKnownValueSet() {
            boolean result = validationSupport.isValueSetSupported(validationSupportContext, VS_URL);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false for unknown ValueSet")
        void shouldReturnFalseForUnknownValueSet() {
            boolean result = validationSupport.isValueSetSupported(validationSupportContext, "http://unknown.org/VS");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for null URL")
        void shouldReturnFalseForNullUrl() {
            boolean result = validationSupport.isValueSetSupported(validationSupportContext, null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("validateCode")
    class ValidateCode {

        @Test
        @DisplayName("should validate known code in CodeSystem")
        void shouldValidateKnownCodeInCodeSystem() {
            CodeValidationResult result = validationSupport.validateCode(
                    validationSupportContext,
                    new ConceptValidationOptions(),
                    CS_URL,
                    "active",
                    "Active",
                    null);

            assertNotNull(result);
            assertEquals("active", result.getCode());
            assertEquals("Active", result.getDisplay());
        }

        @Test
        @DisplayName("should return error for unknown code in CodeSystem")
        void shouldReturnErrorForUnknownCodeInCodeSystem() {
            CodeValidationResult result = validationSupport.validateCode(
                    validationSupportContext,
                    new ConceptValidationOptions(),
                    CS_URL,
                    "unknown",
                    null,
                    null);

            assertNotNull(result);
            assertEquals(IssueSeverity.ERROR, result.getSeverity());
            assertTrue(result.getMessage().contains("Unknown code"));
        }

        @Test
        @DisplayName("should return warning for mismatched display")
        void shouldReturnWarningForMismatchedDisplay() {
            CodeValidationResult result = validationSupport.validateCode(
                    validationSupportContext,
                    new ConceptValidationOptions(),
                    CS_URL,
                    "active",
                    "Wrong Display",
                    null);

            assertNotNull(result);
            assertEquals("active", result.getCode());
            assertEquals(IssueSeverity.WARNING, result.getSeverity());
            assertTrue(result.getMessage().contains("does not match"));
        }

        @Test
        @DisplayName("should return null for unknown CodeSystem")
        void shouldReturnNullForUnknownCodeSystem() {
            CodeValidationResult result = validationSupport.validateCode(
                    validationSupportContext,
                    new ConceptValidationOptions(),
                    "http://unknown.org/CS",
                    "any",
                    null,
                    null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("lookupCode")
    class LookupCode {

        @Test
        @DisplayName("should lookup known code")
        void shouldLookupKnownCode() {
            LookupCodeRequest request = new LookupCodeRequest(CS_URL, "active", null, null);
            LookupCodeResult result = validationSupport.lookupCode(validationSupportContext, request);

            assertNotNull(result);
            assertTrue(result.isFound());
            assertEquals("Active", result.getCodeDisplay());
            assertEquals("TestStatusCodeSystem", result.getCodeSystemDisplayName());
        }

        @Test
        @DisplayName("should return null for unknown code")
        void shouldReturnNullForUnknownCode() {
            LookupCodeRequest request = new LookupCodeRequest(CS_URL, "unknown", null, null);
            LookupCodeResult result = validationSupport.lookupCode(validationSupportContext, request);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for unknown CodeSystem")
        void shouldReturnNullForUnknownCodeSystem() {
            LookupCodeRequest request = new LookupCodeRequest("http://unknown.org/CS", "any", null, null);
            LookupCodeResult result = validationSupport.lookupCode(validationSupportContext, request);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null for null request")
        void shouldReturnNullForNullRequest() {
            LookupCodeResult result = validationSupport.lookupCode(validationSupportContext, null);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("fetchAllStructureDefinitions")
    class FetchAllStructureDefinitions {

        @Test
        @DisplayName("should return all StructureDefinitions")
        void shouldReturnAllStructureDefinitions() {
            List<IBaseResource> result = validationSupport.fetchAllStructureDefinitions();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0) instanceof StructureDefinition);
        }
    }

    @Nested
    @DisplayName("fetchAllConformanceResources")
    class FetchAllConformanceResources {

        @Test
        @DisplayName("should return all conformance resources")
        void shouldReturnAllConformanceResources() {
            List<IBaseResource> result = validationSupport.fetchAllConformanceResources();

            assertNotNull(result);
            assertEquals(3, result.size()); // 1 SD + 1 CS + 1 VS
        }
    }

    @Test
    @DisplayName("getFhirContext should return the context")
    void getFhirContextShouldReturnTheContext() {
        assertEquals(fhirContext, validationSupport.getFhirContext());
    }

    @Test
    @DisplayName("getName should return correct name")
    void getNameShouldReturnCorrectName() {
        assertEquals("CustomResourceValidationSupport", validationSupport.getName());
    }
}
