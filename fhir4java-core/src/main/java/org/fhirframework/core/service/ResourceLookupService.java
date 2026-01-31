package org.fhirframework.core.service;

import org.fhirframework.core.version.FhirVersion;

import java.util.List;

/**
 * Abstraction for looking up existing resources via FHIR search interactions.
 * <p>
 * Defined in fhir4java-core so that modules like fhir4java-plugin
 * can depend on it without depending on the persistence layer directly.
 * The implementation is provided by fhir4java-persistence using
 * standard FHIR search (e.g., {@code identifier=system|value}).
 * </p>
 */
public interface ResourceLookupService {

    /**
     * Check whether any current resource of the given type has an identifier
     * matching the specified system and value, using FHIR search.
     *
     * @param resourceType the FHIR resource type (e.g., "Patient")
     * @param version      the FHIR version to use for the search
     * @param system       the identifier system URI
     * @param value        the identifier value
     * @return true if a matching resource exists
     */
    boolean existsByIdentifier(String resourceType, FhirVersion version,
                               String system, String value);

    /**
     * Check which of the specified identifier tokens already exist in the
     * store for the given resource type, using FHIR search.
     *
     * @param resourceType the FHIR resource type (e.g., "Patient")
     * @param version      the FHIR version to use for the search
     * @param identifiers  list of system|value pairs to check
     * @return list of identifier tokens that already exist
     */
    List<IdentifierToken> findExistingIdentifiers(String resourceType, FhirVersion version,
                                                   List<IdentifierToken> identifiers);

    /**
     * An identifier represented as system + value.
     */
    record IdentifierToken(String system, String value) {

        /**
         * Return the FHIR token search format: {@code system|value}.
         */
        public String toSearchToken() {
            if (system != null && value != null) {
                return system + "|" + value;
            } else if (system != null) {
                return system + "|";
            } else if (value != null) {
                return value;
            }
            return "";
        }

        @Override
        public String toString() {
            return toSearchToken();
        }
    }
}
