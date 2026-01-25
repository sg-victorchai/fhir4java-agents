package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Custom repository interface for FHIR resource search with dynamic query building.
 */
public interface FhirResourceRepositoryCustom {

    /**
     * Search for FHIR resources with dynamic search parameters.
     *
     * @param tenantId the tenant ID
     * @param resourceType the FHIR resource type
     * @param searchParams the search parameters map
     * @param pageable pagination information
     * @return page of matching resources
     */
    Page<FhirResourceEntity> searchWithParams(
            String tenantId,
            String resourceType,
            Map<String, String> searchParams,
            Pageable pageable
    );
}
