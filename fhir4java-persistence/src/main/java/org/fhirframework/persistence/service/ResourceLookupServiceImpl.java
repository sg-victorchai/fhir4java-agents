package org.fhirframework.persistence.service;

import org.fhirframework.core.service.ResourceLookupService;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ResourceLookupService} that delegates to
 * {@link FhirResourceService#search} using standard FHIR token search
 * ({@code identifier=system|value}).
 */
@Service
public class ResourceLookupServiceImpl implements ResourceLookupService {

    private static final Logger log = LoggerFactory.getLogger(ResourceLookupServiceImpl.class);

    private final FhirResourceService fhirResourceService;

    public ResourceLookupServiceImpl(FhirResourceService fhirResourceService) {
        this.fhirResourceService = fhirResourceService;
    }

    @Override
    public boolean existsByIdentifier(String resourceType, FhirVersion version,
                                      String system, String value) {
        String token = new IdentifierToken(system, value).toSearchToken();
        if (token.isEmpty()) {
            return false;
        }

        log.debug("Checking existence of {} with identifier={}", resourceType, token);

        Bundle result = fhirResourceService.search(
                resourceType,
                Map.of("identifier", token),
                version,
                1  // We only need to know if at least one exists
        );

        return result.getTotal() > 0;
    }

    @Override
    public List<IdentifierToken> findExistingIdentifiers(String resourceType, FhirVersion version,
                                                          List<IdentifierToken> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return List.of();
        }

        List<IdentifierToken> existing = new ArrayList<>();

        for (IdentifierToken id : identifiers) {
            String token = id.toSearchToken();
            if (token.isEmpty()) {
                continue;
            }

            log.debug("Searching {} for identifier={}", resourceType, token);

            Bundle result = fhirResourceService.search(
                    resourceType,
                    Map.of("identifier", token),
                    version,
                    1
            );

            if (result.getTotal() > 0) {
                existing.add(id);
            }
        }

        if (!existing.isEmpty()) {
            log.info("Found {} existing identifiers for {}: {}", existing.size(), resourceType, existing);
        }

        return existing;
    }
}
