package org.fhirframework.core.validation;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Container for custom FHIR conformance resources loaded from configuration.
 * <p>
 * Holds StructureDefinitions (custom resources and profiles), CodeSystems,
 * and ValueSets that are used by {@link CustomResourceValidationSupport}
 * to validate custom resources.
 * </p>
 */
public class CustomResourceBundle {

    private final Map<String, StructureDefinition> structureDefinitions;
    private final Map<String, CodeSystem> codeSystems;
    private final Map<String, ValueSet> valueSets;

    private CustomResourceBundle(Builder builder) {
        this.structureDefinitions = builder.structureDefinitions != null
            ? Collections.unmodifiableMap(builder.structureDefinitions)
            : Collections.emptyMap();
        this.codeSystems = builder.codeSystems != null
            ? Collections.unmodifiableMap(builder.codeSystems)
            : Collections.emptyMap();
        this.valueSets = builder.valueSets != null
            ? Collections.unmodifiableMap(builder.valueSets)
            : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get a StructureDefinition by URL.
     *
     * @param url The canonical URL of the StructureDefinition
     * @return Optional containing the StructureDefinition if found
     */
    public Optional<StructureDefinition> getStructureDefinition(String url) {
        return Optional.ofNullable(structureDefinitions.get(url));
    }

    /**
     * Get a CodeSystem by URL.
     *
     * @param url The canonical URL of the CodeSystem
     * @return Optional containing the CodeSystem if found
     */
    public Optional<CodeSystem> getCodeSystem(String url) {
        return Optional.ofNullable(codeSystems.get(url));
    }

    /**
     * Get a ValueSet by URL.
     *
     * @param url The canonical URL of the ValueSet
     * @return Optional containing the ValueSet if found
     */
    public Optional<ValueSet> getValueSet(String url) {
        return Optional.ofNullable(valueSets.get(url));
    }

    /**
     * Get all StructureDefinitions.
     */
    public Map<String, StructureDefinition> getStructureDefinitions() {
        return structureDefinitions;
    }

    /**
     * Get all CodeSystems.
     */
    public Map<String, CodeSystem> getCodeSystems() {
        return codeSystems;
    }

    /**
     * Get all ValueSets.
     */
    public Map<String, ValueSet> getValueSets() {
        return valueSets;
    }

    /**
     * Check if this bundle contains any resources.
     */
    public boolean isEmpty() {
        return structureDefinitions.isEmpty() && codeSystems.isEmpty() && valueSets.isEmpty();
    }

    /**
     * Get total count of all resources.
     */
    public int getTotalCount() {
        return structureDefinitions.size() + codeSystems.size() + valueSets.size();
    }

    @Override
    public String toString() {
        return String.format("CustomResourceBundle[structureDefinitions=%d, codeSystems=%d, valueSets=%d]",
                structureDefinitions.size(), codeSystems.size(), valueSets.size());
    }

    public static class Builder {
        private Map<String, StructureDefinition> structureDefinitions;
        private Map<String, CodeSystem> codeSystems;
        private Map<String, ValueSet> valueSets;

        public Builder structureDefinitions(Map<String, StructureDefinition> structureDefinitions) {
            this.structureDefinitions = structureDefinitions;
            return this;
        }

        public Builder codeSystems(Map<String, CodeSystem> codeSystems) {
            this.codeSystems = codeSystems;
            return this;
        }

        public Builder valueSets(Map<String, ValueSet> valueSets) {
            this.valueSets = valueSets;
            return this;
        }

        public CustomResourceBundle build() {
            return new CustomResourceBundle(this);
        }
    }
}
