package org.fhirframework.core.resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers custom FHIR resource classes generated from StructureDefinitions with HAPI FHIR contexts.
 * <p>
 * This component automatically discovers custom resource classes by scanning the
 * {@link ResourceRegistry} for configured resources and checking if a corresponding
 * generated class exists in the {@code org.fhirframework.generated.resources} package.
 * Custom resources are those that have a generated Java class with the {@code @ResourceDef}
 * annotation but are not standard FHIR resources known to HAPI.
 * </p>
 */
@Component
public class CustomResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceRegistry.class);
    private static final String GENERATED_RESOURCES_PACKAGE = "org.fhirframework.generated.resources";

    private final FhirContextFactory contextFactory;
    private final ResourceRegistry resourceRegistry;
    private final List<String> registeredResources = new ArrayList<>();

    public CustomResourceRegistry(FhirContextFactory contextFactory, ResourceRegistry resourceRegistry) {
        this.contextFactory = contextFactory;
        this.resourceRegistry = resourceRegistry;
    }

    @PostConstruct
    public void registerCustomResources() {
        log.info("Scanning for generated custom FHIR resources...");

        // Register custom resources for each FHIR version
        for (FhirVersion version : FhirVersion.values()) {
            try {
                registerResourcesForVersion(version);
            } catch (Exception e) {
                log.warn("Failed to register custom resources for FHIR {}: {}",
                        version.getCode(), e.getMessage());
            }
        }

        if (registeredResources.isEmpty()) {
            log.info("No custom resources found to register. " +
                    "Generate resources using 'mvn generate-sources' if you have StructureDefinitions defined.");
        } else {
            log.info("Successfully registered {} custom resource(s): {}",
                    registeredResources.size(), registeredResources);
        }
    }

    /**
     * Register custom resources for a specific FHIR version.
     */
    private void registerResourcesForVersion(FhirVersion version) {
        FhirContext ctx = contextFactory.getContext(version);

        // Try to load known custom resources
        // Note: This list should be generated or discovered dynamically
        List<String> resourceNames = discoverGeneratedResources();

        for (String resourceName : resourceNames) {
            try {
                String className = GENERATED_RESOURCES_PACKAGE + "." + resourceName;
                Class<?> resourceClass = Class.forName(className);

                // Verify it's a valid FHIR resource class
                if (!IBaseResource.class.isAssignableFrom(resourceClass)) {
                    log.warn("Class {} does not implement IBaseResource, skipping", className);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Class<? extends IBaseResource> typedResourceClass =
                        (Class<? extends IBaseResource>) resourceClass;

                // Check for @ResourceDef annotation
                ResourceDef resourceDef = typedResourceClass.getAnnotation(ResourceDef.class);
                if (resourceDef != null) {
                    log.debug("Registering custom resource: {} (name: {}, profile: {}) for FHIR {}",
                            resourceName, resourceDef.name(), resourceDef.profile(), version.getCode());
                } else {
                    log.debug("Registering custom resource: {} for FHIR {}", resourceName, version.getCode());
                }

                // IMPORTANT: Register inner @Block classes (backbone elements) BEFORE calling getResourceDefinition()
                // This ensures HAPI knows about them when it creates the RuntimeResourceDefinition
                registerInnerBlockClasses(typedResourceClass, ctx, version);

                // Register the custom resource class with HAPI (this will now include the backbone elements)
                RuntimeResourceDefinition def = ctx.getResourceDefinition(typedResourceClass);

                String fullName = resourceName + " (" + version.getCode() + ")";
                if (!registeredResources.contains(fullName)) {
                    registeredResources.add(fullName);
                }

                log.debug("Registered custom resource: {} for FHIR {}", resourceName, version.getCode());

            } catch (ClassNotFoundException e) {
                log.debug("Custom resource class not found for {}: {}", version.getCode(), resourceName);
            } catch (Exception e) {
                log.warn("Failed to register custom resource {} for FHIR {}: {}",
                        resourceName, version.getCode(), e.getMessage());
            }
        }
    }

    /**
     * Register inner @Block classes (backbone elements) with HAPI FhirContext.
     * This ensures HAPI can properly serialize/deserialize nested backbone elements.
     *
     * @param resourceClass The resource class to scan for inner classes
     * @param ctx The HAPI FhirContext to register with
     * @param version The FHIR version
     */
    private void registerInnerBlockClasses(Class<? extends IBaseResource> resourceClass,
                                          FhirContext ctx,
                                          FhirVersion version) {
        // Find all inner classes annotated with @Block
        Class<?>[] innerClasses = resourceClass.getDeclaredClasses();

        for (Class<?> innerClass : innerClasses) {
            if (innerClass.isAnnotationPresent(Block.class)) {
                try {
                    // Force HAPI to recognize this as a valid backbone element type
                    // Cast to IBase since @Block classes must extend BackboneElement (which extends IBase)
                    @SuppressWarnings("unchecked")
                    Class<? extends org.hl7.fhir.instance.model.api.IBase> blockClass =
                        (Class<? extends org.hl7.fhir.instance.model.api.IBase>) innerClass;
                    ctx.getElementDefinition(blockClass);
                    log.debug("Registered backbone element: {} for {} (FHIR {})",
                            innerClass.getSimpleName(), resourceClass.getSimpleName(), version.getCode());
                } catch (Exception e) {
                    log.warn("Failed to register backbone element {} for {} (FHIR {}): {}",
                            innerClass.getSimpleName(), resourceClass.getSimpleName(),
                            version.getCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * Discover generated custom resource classes by scanning the ResourceRegistry.
     * <p>
     * This method iterates through all configured resources and checks if a corresponding
     * generated Java class exists with the {@code @ResourceDef} annotation. Resources that
     * have a generated class are considered custom resources and will be registered with HAPI.
     * </p>
     *
     * @return List of custom resource class names that have generated classes
     */
    private List<String> discoverGeneratedResources() {
        List<String> customResources = new ArrayList<>();

        // Get all configured resource types from the registry
        Set<String> configuredResourceTypes = resourceRegistry.getAllResources().stream()
                .filter(ResourceConfiguration::isEnabled)
                .map(ResourceConfiguration::getResourceType)
                .collect(Collectors.toSet());

        log.debug("Scanning {} configured resources for custom resource classes", configuredResourceTypes.size());

        for (String resourceType : configuredResourceTypes) {
            // Try to load the generated class for this resource type
            String className = GENERATED_RESOURCES_PACKAGE + "." + resourceType;
            try {
                Class<?> resourceClass = Class.forName(className);

                // Check if it has @ResourceDef annotation (confirms it's a HAPI-compatible resource)
                if (resourceClass.isAnnotationPresent(ResourceDef.class)) {
                    // Verify it implements IBaseResource
                    if (IBaseResource.class.isAssignableFrom(resourceClass)) {
                        log.debug("Discovered custom resource class: {}", resourceType);
                        customResources.add(resourceType);
                    } else {
                        log.warn("Generated class {} does not implement IBaseResource, skipping", className);
                    }
                } else {
                    log.debug("Class {} exists but has no @ResourceDef annotation, skipping", className);
                }
            } catch (ClassNotFoundException e) {
                // No generated class for this resource type - it's a standard FHIR resource
                log.trace("No generated class found for resource type: {} (standard FHIR resource)", resourceType);
            }
        }

        log.info("Discovered {} custom resource(s) from configuration: {}",
                customResources.size(), customResources);

        return customResources;
    }

    /**
     * Get the list of registered custom resource names.
     *
     * @return List of registered resource names
     */
    public List<String> getRegisteredResources() {
        return new ArrayList<>(registeredResources);
    }

    /**
     * Check if a custom resource is registered.
     *
     * @param resourceName The resource name to check
     * @return true if the resource is registered
     */
    public boolean isResourceRegistered(String resourceName) {
        return registeredResources.stream()
                .anyMatch(r -> r.startsWith(resourceName + " ("));
    }
}
