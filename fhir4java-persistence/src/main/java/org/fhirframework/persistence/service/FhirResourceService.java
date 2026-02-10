package org.fhirframework.persistence.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.exception.FhirException;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.validation.ProfileValidator;
import org.fhirframework.core.validation.SearchParameterValidator;
import org.fhirframework.core.validation.ValidationConfig;
import org.fhirframework.core.validation.ValidationResult;
import org.fhirframework.core.version.FhirVersion;
import org.fhirframework.persistence.entity.FhirResourceEntity;
import org.fhirframework.persistence.repository.FhirResourceRepository;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r5.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for FHIR resource CRUD operations.
 * <p>
 * Provides business logic for creating, reading, updating, and deleting
 * FHIR resources, with proper ID generation and versioning.
 * </p>
 */
@Service
public class FhirResourceService {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceService.class);
    private static final String DEFAULT_TENANT = "default";

    private final FhirResourceRepository repository;
    private final FhirContextFactory contextFactory;
    private final SearchParameterValidator searchParameterValidator;
    private final ProfileValidator profileValidator;
    private final ValidationConfig validationConfig;

    public FhirResourceService(FhirResourceRepository repository,
                               FhirContextFactory contextFactory,
                               SearchParameterValidator searchParameterValidator,
                               ProfileValidator profileValidator,
                               ValidationConfig validationConfig) {
        this.repository = repository;
        this.contextFactory = contextFactory;
        this.searchParameterValidator = searchParameterValidator;
        this.profileValidator = profileValidator;
        this.validationConfig = validationConfig;
    }

    /**
     * Create a new FHIR resource.
     *
     * @param resourceType the FHIR resource type
     * @param resourceJson the JSON representation of the resource
     * @param version the FHIR version
     * @return the created resource with generated ID and meta fields
     */
    @Transactional
    public ResourceResult create(String resourceType, String resourceJson, FhirVersion version) {
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser();

        // Parse the incoming resource (works for both standard and custom resources)
        IBaseResource resource = parser.parseResource(resourceJson);

        if (log.isDebugEnabled()) {
            log.debug("Parsed resource successfully: resourceType={}, class={}",
                    resourceType, resource.getClass().getSimpleName());
            // Log the actual parsed content to see if packaging is present
            String parsedJson = parser.setPrettyPrint(true).encodeResourceToString(resource);
            log.debug("Parsed resource content:\n{}", parsedJson);
            parser.setPrettyPrint(false); // Reset to non-pretty print
        }

        // Validate resource if validation is enabled
        if (validationConfig.isEnabled() && validationConfig.isProfileValidationEnabled()) {
            validateResourceOrThrow(resource, version);
        }

        // Generate a new resource ID
        String resourceId = UUID.randomUUID().toString();
        int versionId = 1;
        Instant now = Instant.now();

        // Set the ID on the resource
        setResourceId(resource, resourceType, resourceId);

        // Set meta fields
        setMeta(resource, versionId, now);

        // Serialize with updated fields
        String updatedJson = parser.encodeResourceToString(resource);

        if (log.isDebugEnabled()) {
            log.debug("Serialized resource for persistence: resourceType={}, resourceId={}, jsonLength={}",
                    resourceType, resourceId, updatedJson.length());
        }

        // Create entity and persist
        FhirResourceEntity entity = FhirResourceEntity.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .fhirVersion(version.getCode())
                .versionId(versionId)
                .isCurrent(true)
                .isDeleted(false)
                .content(updatedJson)
                .lastUpdated(now)
                .createdAt(now)
                .tenantId(DEFAULT_TENANT)
                .build();

        repository.save(entity);

        log.info("Created {}/{} version {}", resourceType, resourceId, versionId);

        return new ResourceResult(resourceId, versionId, updatedJson, now, false);
    }

    /**
     * Read a FHIR resource by ID.
     *
     * @param resourceType the FHIR resource type
     * @param resourceId the resource ID
     * @param version the FHIR version
     * @return the resource result
     */
    @Transactional(readOnly = true)
    public ResourceResult read(String resourceType, String resourceId, FhirVersion version) {
        FhirResourceEntity entity = repository
                .findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                        DEFAULT_TENANT, resourceType, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException(resourceType, resourceId));

        if (entity.getIsDeleted()) {
            throw new ResourceNotFoundException(resourceType, resourceId);
        }

        return new ResourceResult(
                entity.getResourceId(),
                entity.getVersionId(),
                entity.getContent(),
                entity.getLastUpdated(),
                false
        );
    }

    /**
     * Read a specific version of a FHIR resource.
     *
     * @param resourceType the FHIR resource type
     * @param resourceId the resource ID
     * @param versionId the version ID
     * @param version the FHIR version
     * @return the resource result
     */
    @Transactional(readOnly = true)
    public ResourceResult vread(String resourceType, String resourceId, int versionId, FhirVersion version) {
        FhirResourceEntity entity = repository
                .findByTenantIdAndResourceTypeAndResourceIdAndVersionId(
                        DEFAULT_TENANT, resourceType, resourceId, versionId)
                .orElseThrow(() -> new ResourceNotFoundException(resourceType, resourceId));

        return new ResourceResult(
                entity.getResourceId(),
                entity.getVersionId(),
                entity.getContent(),
                entity.getLastUpdated(),
                entity.getIsDeleted()
        );
    }

    /**
     * Update an existing FHIR resource.
     *
     * @param resourceType the FHIR resource type
     * @param resourceId the resource ID
     * @param resourceJson the JSON representation of the resource
     * @param version the FHIR version
     * @return the updated resource result
     */
    @Transactional
    public ResourceResult update(String resourceType, String resourceId, String resourceJson, FhirVersion version) {
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser();

        // Get current version to determine new version number
        Integer maxVersion = repository.findMaxVersionId(DEFAULT_TENANT, resourceType, resourceId);
        int newVersionId = (maxVersion != null ? maxVersion : 0) + 1;
        boolean isCreate = (maxVersion == null || maxVersion == 0);

        Instant now = Instant.now();

        // Parse the incoming resource
        IBaseResource resource = parser.parseResource(resourceJson);

        // Validate resource if validation is enabled
        if (validationConfig.isEnabled() && validationConfig.isProfileValidationEnabled()) {
            validateResourceOrThrow(resource, version);
        }

        // Set the ID on the resource
        setResourceId(resource, resourceType, resourceId);

        // Set meta fields
        setMeta(resource, newVersionId, now);

        // Serialize with updated fields
        String updatedJson = parser.encodeResourceToString(resource);

        // Mark all existing versions as not current
        if (!isCreate) {
            repository.markAllVersionsNotCurrent(DEFAULT_TENANT, resourceType, resourceId);
        }

        // Create new version entity
        FhirResourceEntity entity = FhirResourceEntity.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .fhirVersion(version.getCode())
                .versionId(newVersionId)
                .isCurrent(true)
                .isDeleted(false)
                .content(updatedJson)
                .lastUpdated(now)
                .createdAt(isCreate ? now : null)
                .tenantId(DEFAULT_TENANT)
                .build();

        repository.save(entity);

        log.info("Updated {}/{} to version {}", resourceType, resourceId, newVersionId);

        return new ResourceResult(resourceId, newVersionId, updatedJson, now, false);
    }

    /**
     * Delete a FHIR resource (soft delete).
     *
     * @param resourceType the FHIR resource type
     * @param resourceId the resource ID
     * @param version the FHIR version
     * @return the deleted resource result
     */
    @Transactional
    public ResourceResult delete(String resourceType, String resourceId, FhirVersion version) {
        // Check if resource exists
        boolean exists = repository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                DEFAULT_TENANT, resourceType, resourceId);

        if (!exists) {
            throw new ResourceNotFoundException(resourceType, resourceId);
        }

        // Get current version
        Integer maxVersion = repository.findMaxVersionId(DEFAULT_TENANT, resourceType, resourceId);

        // Soft delete
        repository.softDelete(DEFAULT_TENANT, resourceType, resourceId, Instant.now());

        log.info("Deleted {}/{}", resourceType, resourceId);

        return new ResourceResult(resourceId, maxVersion, null, Instant.now(), true);
    }

    /**
     * Search for FHIR resources.
     *
     * @param resourceType the FHIR resource type
     * @param params search parameters
     * @param version the FHIR version
     * @param count maximum number of results (default 20)
     * @return search result bundle
     */
    @Transactional(readOnly = true)
    public Bundle search(String resourceType, Map<String, String> params, FhirVersion version, int count) {
        // Use a simple default URL when not provided
        String defaultUrl = "/" + resourceType;
        return search(resourceType, params, version, count, defaultUrl);
    }

    /**
     * Search for FHIR resources.
     *
     * @param resourceType the FHIR resource type
     * @param params search parameters
     * @param version the FHIR version
     * @param count maximum number of results (default 20)
     * @param requestUrl the full request URL for building pagination links
     * @return search result bundle
     */
    @Transactional(readOnly = true)
    public Bundle search(String resourceType, Map<String, String> params, FhirVersion version, int count, String requestUrl) {
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser();

        // Validate search parameters if validation is enabled
        if (validationConfig.isEnabled() && validationConfig.isValidateSearchParameters()) {
            validateSearchParametersOrThrow(resourceType, params, version);
        }

        // Handle pagination parameters
        int offset = 0;
        if (params.containsKey("_offset")) {
            try {
                offset = Integer.parseInt(params.get("_offset"));
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        Pageable pageable = PageRequest.of(offset / Math.max(count, 1), Math.min(count, 1000));

        // Use the custom search method with parameters
        Page<FhirResourceEntity> page = repository.searchWithParams(
                DEFAULT_TENANT, resourceType, params, pageable);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal((int) page.getTotalElements());

        for (FhirResourceEntity entity : page.getContent()) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(buildFullUrl(resourceType, entity.getResourceId()));

            // Parse resource (works for both standard and generated custom resources)
            IBaseResource resource = parser.parseResource(entity.getContent());
            entry.setResource((org.hl7.fhir.r5.model.Resource) resource);
        }

        // Add pagination links
        addPaginationLinks(bundle, requestUrl, params, count, offset, page);

        log.debug("Search {} with params {} returned {} results (total: {})",
                resourceType, params.keySet(), page.getNumberOfElements(), page.getTotalElements());

        return bundle;
    }









    /**\n     * Get history for a specific resource.
     *
     * @param resourceType the FHIR resource type
     * @param resourceId the resource ID
     * @param version the FHIR version
     * @return history bundle
     */
    @Transactional(readOnly = true)
    public Bundle history(String resourceType, String resourceId, FhirVersion version) {
        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser();

        List<FhirResourceEntity> versions = repository
                .findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(
                        DEFAULT_TENANT, resourceType, resourceId);

        if (versions.isEmpty()) {
            throw new ResourceNotFoundException(resourceType, resourceId);
        }

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.HISTORY);
        bundle.setTotal(versions.size());

        for (FhirResourceEntity entity : versions) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setFullUrl(buildFullUrl(resourceType, entity.getResourceId()));

            Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
            if (entity.getIsDeleted()) {
                request.setMethod(Bundle.HTTPVerb.DELETE);
            } else if (entity.getVersionId() == 1) {
                request.setMethod(Bundle.HTTPVerb.POST);
            } else {
                request.setMethod(Bundle.HTTPVerb.PUT);
            }
            request.setUrl(resourceType + "/" + resourceId);
            entry.setRequest(request);

            Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent();
            response.setStatus("200");
            response.setEtag("W/\"" + entity.getVersionId() + "\"");
            response.setLastModified(Date.from(entity.getLastUpdated()));
            entry.setResponse(response);

            if (!entity.getIsDeleted()) {
                // Parse resource (works for both standard and generated custom resources)
                IBaseResource resource = parser.parseResource(entity.getContent());
                entry.setResource((org.hl7.fhir.r5.model.Resource) resource);
            }
        }

        log.debug("History {}/{} returned {} versions", resourceType, resourceId, versions.size());

        return bundle;
    }

    /**
     * Check if a resource exists.
     */
    @Transactional(readOnly = true)
    public boolean exists(String resourceType, String resourceId) {
        return repository.existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(
                DEFAULT_TENANT, resourceType, resourceId);
    }

    private void setResourceId(IBaseResource resource, String resourceType, String resourceId) {
        IIdType idType = resource.getIdElement();
        if (idType == null || idType.isEmpty()) {
            resource.setId(resourceType + "/" + resourceId);
        } else {
            resource.setId(resourceType + "/" + resourceId);
        }
    }

    private void setMeta(IBaseResource resource, int versionId, Instant lastUpdated) {
        IBaseMetaType meta = resource.getMeta();
        if (meta != null) {
            meta.setVersionId(String.valueOf(versionId));
            meta.setLastUpdated(Date.from(lastUpdated));
        }
    }

    private String buildFullUrl(String resourceType, String resourceId) {
        return "urn:uuid:" + resourceType + "/" + resourceId;
    }

    /**
     * Validate a resource and throw FhirException if validation fails.
     */
    private void validateResourceOrThrow(IBaseResource resource, FhirVersion version) {
        ValidationResult result = profileValidator.validateAgainstRequiredProfiles(resource, version);

        if (result.hasErrors()) {
            // In strict mode, throw on any errors
            if (validationConfig.isStrictProfileValidation()) {
                String errors = result.getErrors().stream()
                        .map(issue -> issue.message())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Validation failed");
                throw new FhirException("Resource validation failed: " + errors, "invalid");
            }
            // In lenient mode, log warnings but continue
            log.warn("Resource validation issues (lenient mode): {}", result);
        }
    }



    /**
     * Validate search parameters and throw FhirException if validation fails.
     */
    private void validateSearchParametersOrThrow(String resourceType, Map<String, String> params, FhirVersion version) {
        // Convert Map<String, String> to Map<String, String[]> for the validator
        Map<String, String[]> paramArrays = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            paramArrays.put(entry.getKey(), new String[]{entry.getValue()});
        }

        ValidationResult result = searchParameterValidator.validateSearchParameters(version, resourceType, paramArrays);

        if (result.hasErrors()) {
            if (validationConfig.isFailOnUnknownSearchParameters()) {
                String errors = result.getErrors().stream()
                        .map(issue -> issue.message())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Invalid search parameters");
                throw new FhirException(errors, "invalid");
            }
            // Log but continue - invalid parameters will be ignored by the repository
            log.warn("Search parameter validation issues: {}", result);
        }
    }

    /**
     * Add FHIR pagination links to the search Bundle.
     * According to FHIR spec: https://hl7.org/fhir/http.html#paging
     *
     * @param bundle the Bundle to add links to
     * @param requestUrl the original request URL
     * @param params the search parameters
     * @param count the page size
     * @param offset the current offset
     * @param page the Page result
     */
    private void addPaginationLinks(Bundle bundle, String requestUrl, Map<String, String> params, 
                                     int count, int offset, Page<FhirResourceEntity> page) {
        // Remove pagination parameters from params for building base URL
        Map<String, String> baseParams = new HashMap<>(params);
        baseParams.remove("_count");
        baseParams.remove("_offset");

        // Extract base URL (everything before query string)
        String baseUrl = requestUrl;
        int queryIndex = requestUrl.indexOf('?');
        if (queryIndex > 0) {
            baseUrl = requestUrl.substring(0, queryIndex);
        }

        // 1. Self link - current page
        String selfUrl = buildSearchUrl(baseUrl, baseParams, count, offset);
        Bundle.BundleLinkComponent selfLink = bundle.addLink();
        selfLink.setRelation(Bundle.LinkRelationTypes.SELF);
        selfLink.setUrl(selfUrl);

        // 2. First link - first page
        String firstUrl = buildSearchUrl(baseUrl, baseParams, count, 0);
        Bundle.BundleLinkComponent firstLink = bundle.addLink();
        firstLink.setRelation(Bundle.LinkRelationTypes.FIRST);
        firstLink.setUrl(firstUrl);

        // 3. Previous link - only if not on first page
        if (offset > 0) {
            int prevOffset = Math.max(0, offset - count);
            String prevUrl = buildSearchUrl(baseUrl, baseParams, count, prevOffset);
            Bundle.BundleLinkComponent prevLink = bundle.addLink();
            prevLink.setRelation(Bundle.LinkRelationTypes.PREVIOUS);
            prevLink.setUrl(prevUrl);
        }

        // 4. Next link - only if there are more results
        if (page.hasNext()) {
            int nextOffset = offset + count;
            String nextUrl = buildSearchUrl(baseUrl, baseParams, count, nextOffset);
            Bundle.BundleLinkComponent nextLink = bundle.addLink();
            nextLink.setRelation(Bundle.LinkRelationTypes.NEXT);
            nextLink.setUrl(nextUrl);
        }

        // 5. Last link - calculate last page offset
        long totalElements = page.getTotalElements();
        if (totalElements > 0) {
            int lastOffset = (int) ((totalElements - 1) / count) * count;
            String lastUrl = buildSearchUrl(baseUrl, baseParams, count, lastOffset);
            Bundle.BundleLinkComponent lastLink = bundle.addLink();
            lastLink.setRelation(Bundle.LinkRelationTypes.LAST);
            lastLink.setUrl(lastUrl);
        }
    }

    /**
     * Build a search URL with pagination parameters.
     *
     * @param baseUrl the base URL (without query string)
     * @param params the search parameters (without _count and _offset)
     * @param count the page size
     * @param offset the offset
     * @return the complete URL with query parameters
     */
    private String buildSearchUrl(String baseUrl, Map<String, String> params, int count, int offset) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        
        // Add all search parameters
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        
        // Add pagination parameters
        builder.queryParam("_count", count);
        if (offset > 0) {
            builder.queryParam("_offset", offset);
        }
        
        return builder.build().toUriString();
    }

    /**
     * Result of a resource operation.
     */
    public record ResourceResult(
            String resourceId,
            int versionId,
            String content,
            Instant lastUpdated,
            boolean deleted
    ) {}
}
