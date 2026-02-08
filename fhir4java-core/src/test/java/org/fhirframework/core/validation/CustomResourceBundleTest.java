package org.fhirframework.core.validation;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomResourceBundle")
class CustomResourceBundleTest {

    private static final String SD_URL = "http://example.org/StructureDefinition/TestResource";
    private static final String CS_URL = "http://example.org/CodeSystem/test-codes";
    private static final String VS_URL = "http://example.org/ValueSet/test-values";

    @Nested
    @DisplayName("when built with resources")
    class WhenBuiltWithResources {

        private CustomResourceBundle bundle;
        private StructureDefinition testSD;
        private CodeSystem testCS;
        private ValueSet testVS;

        @BeforeEach
        void setUp() {
            testSD = new StructureDefinition();
            testSD.setUrl(SD_URL);
            testSD.setName("TestResource");

            testCS = new CodeSystem();
            testCS.setUrl(CS_URL);
            testCS.setName("TestCodeSystem");

            testVS = new ValueSet();
            testVS.setUrl(VS_URL);
            testVS.setName("TestValueSet");

            Map<String, StructureDefinition> structDefs = new HashMap<>();
            structDefs.put(SD_URL, testSD);

            Map<String, CodeSystem> codeSystems = new HashMap<>();
            codeSystems.put(CS_URL, testCS);

            Map<String, ValueSet> valueSets = new HashMap<>();
            valueSets.put(VS_URL, testVS);

            bundle = CustomResourceBundle.builder()
                    .structureDefinitions(structDefs)
                    .codeSystems(codeSystems)
                    .valueSets(valueSets)
                    .build();
        }

        @Test
        @DisplayName("should return StructureDefinition by URL")
        void shouldReturnStructureDefinitionByUrl() {
            Optional<StructureDefinition> result = bundle.getStructureDefinition(SD_URL);

            assertTrue(result.isPresent());
            assertEquals(testSD, result.get());
        }

        @Test
        @DisplayName("should return empty for unknown StructureDefinition URL")
        void shouldReturnEmptyForUnknownStructureDefinition() {
            Optional<StructureDefinition> result = bundle.getStructureDefinition("http://unknown.org/SD");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return CodeSystem by URL")
        void shouldReturnCodeSystemByUrl() {
            Optional<CodeSystem> result = bundle.getCodeSystem(CS_URL);

            assertTrue(result.isPresent());
            assertEquals(testCS, result.get());
        }

        @Test
        @DisplayName("should return empty for unknown CodeSystem URL")
        void shouldReturnEmptyForUnknownCodeSystem() {
            Optional<CodeSystem> result = bundle.getCodeSystem("http://unknown.org/CS");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return ValueSet by URL")
        void shouldReturnValueSetByUrl() {
            Optional<ValueSet> result = bundle.getValueSet(VS_URL);

            assertTrue(result.isPresent());
            assertEquals(testVS, result.get());
        }

        @Test
        @DisplayName("should return empty for unknown ValueSet URL")
        void shouldReturnEmptyForUnknownValueSet() {
            Optional<ValueSet> result = bundle.getValueSet("http://unknown.org/VS");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return correct total count")
        void shouldReturnCorrectTotalCount() {
            assertEquals(3, bundle.getTotalCount());
        }

        @Test
        @DisplayName("should not be empty when has resources")
        void shouldNotBeEmptyWhenHasResources() {
            assertFalse(bundle.isEmpty());
        }

        @Test
        @DisplayName("should return all StructureDefinitions")
        void shouldReturnAllStructureDefinitions() {
            Map<String, StructureDefinition> result = bundle.getStructureDefinitions();

            assertEquals(1, result.size());
            assertTrue(result.containsKey(SD_URL));
        }

        @Test
        @DisplayName("should return all CodeSystems")
        void shouldReturnAllCodeSystems() {
            Map<String, CodeSystem> result = bundle.getCodeSystems();

            assertEquals(1, result.size());
            assertTrue(result.containsKey(CS_URL));
        }

        @Test
        @DisplayName("should return all ValueSets")
        void shouldReturnAllValueSets() {
            Map<String, ValueSet> result = bundle.getValueSets();

            assertEquals(1, result.size());
            assertTrue(result.containsKey(VS_URL));
        }
    }

    @Nested
    @DisplayName("when built empty")
    class WhenBuiltEmpty {

        private CustomResourceBundle bundle;

        @BeforeEach
        void setUp() {
            bundle = CustomResourceBundle.builder().build();
        }

        @Test
        @DisplayName("should be empty")
        void shouldBeEmpty() {
            assertTrue(bundle.isEmpty());
        }

        @Test
        @DisplayName("should have zero total count")
        void shouldHaveZeroTotalCount() {
            assertEquals(0, bundle.getTotalCount());
        }

        @Test
        @DisplayName("should return empty maps")
        void shouldReturnEmptyMaps() {
            assertTrue(bundle.getStructureDefinitions().isEmpty());
            assertTrue(bundle.getCodeSystems().isEmpty());
            assertTrue(bundle.getValueSets().isEmpty());
        }

        @Test
        @DisplayName("should return empty Optional for lookups")
        void shouldReturnEmptyOptionalForLookups() {
            assertTrue(bundle.getStructureDefinition("any").isEmpty());
            assertTrue(bundle.getCodeSystem("any").isEmpty());
            assertTrue(bundle.getValueSet("any").isEmpty());
        }
    }

    @Test
    @DisplayName("toString should contain resource counts")
    void toStringShouldContainResourceCounts() {
        CustomResourceBundle bundle = CustomResourceBundle.builder().build();
        String result = bundle.toString();

        assertTrue(result.contains("structureDefinitions=0"));
        assertTrue(result.contains("codeSystems=0"));
        assertTrue(result.contains("valueSets=0"));
    }
}
