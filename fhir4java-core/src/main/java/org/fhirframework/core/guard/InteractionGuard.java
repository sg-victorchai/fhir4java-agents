package org.fhirframework.core.guard;

import org.fhirframework.core.exception.InteractionDisabledException;
import org.fhirframework.core.exception.ResourceNotFoundException;
import org.fhirframework.core.exception.VersionNotSupportedException;
import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.version.FhirVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guards access to FHIR resources based on configuration.
 * <p>
 * Validates that the requested resource type, FHIR version, and interaction
 * are all enabled before allowing the operation to proceed.
 * </p>
 */
@Component
public class InteractionGuard {

    private static final Logger log = LoggerFactory.getLogger(InteractionGuard.class);

    private final ResourceRegistry registry;

    public InteractionGuard(ResourceRegistry registry) {
        this.registry = registry;
    }

    /**
     * Validates that an interaction is allowed for the given resource type and version.
     *
     * @param resourceType the FHIR resource type
     * @param version      the FHIR version
     * @param interaction  the interaction type
     * @throws ResourceNotFoundException       if the resource type is not supported
     * @throws VersionNotSupportedException    if the version is not supported for this resource
     * @throws InteractionDisabledException    if the interaction is disabled for this resource
     */
    public void validateInteraction(String resourceType, FhirVersion version, InteractionType interaction) {
        log.debug("Validating interaction: {} {} for version {}",
                interaction.getCode(), resourceType, version.getCode());

        // Check if resource type is supported
        if (!registry.isResourceSupported(resourceType)) {
            log.warn("Resource type not supported: {}", resourceType);
            throw new ResourceNotFoundException(resourceType);
        }

        // Check if version is supported for this resource
        if (!registry.supportsVersion(resourceType, version)) {
            log.warn("Version {} not supported for resource type: {}", version.getCode(), resourceType);
            throw new VersionNotSupportedException(resourceType, version);
        }

        // Check if interaction is enabled
        if (!registry.isInteractionEnabled(resourceType, version, interaction)) {
            log.warn("Interaction {} disabled for resource type: {}", interaction.getCode(), resourceType);
            throw new InteractionDisabledException(resourceType, interaction);
        }

        log.debug("Interaction validated successfully: {} {} for version {}",
                interaction.getCode(), resourceType, version.getCode());
    }

    /**
     * Validates that an interaction is allowed for the given resource type.
     * Uses the default FHIR version for the resource.
     *
     * @param resourceType the FHIR resource type
     * @param interaction  the interaction type
     */
    public void validateInteraction(String resourceType, InteractionType interaction) {
        FhirVersion version = registry.getDefaultVersion(resourceType);
        validateInteraction(resourceType, version, interaction);
    }

    /**
     * Checks if an interaction is allowed without throwing exceptions.
     *
     * @return true if the interaction is allowed
     */
    public boolean isInteractionAllowed(String resourceType, FhirVersion version, InteractionType interaction) {
        return registry.isResourceSupported(resourceType)
                && registry.supportsVersion(resourceType, version)
                && registry.isInteractionEnabled(resourceType, version, interaction);
    }

    /**
     * Validates that the resource type is supported for any operation.
     *
     * @param resourceType the FHIR resource type
     * @throws ResourceNotFoundException if the resource type is not supported
     */
    public void validateResourceType(String resourceType) {
        if (!registry.isResourceSupported(resourceType)) {
            throw new ResourceNotFoundException(resourceType);
        }
    }

    /**
     * Validates that the resource type supports the given FHIR version.
     *
     * @param resourceType the FHIR resource type
     * @param version      the FHIR version
     * @throws ResourceNotFoundException      if the resource type is not supported
     * @throws VersionNotSupportedException   if the version is not supported
     */
    public void validateVersion(String resourceType, FhirVersion version) {
        validateResourceType(resourceType);
        if (!registry.supportsVersion(resourceType, version)) {
            throw new VersionNotSupportedException(resourceType, version);
        }
    }
}
