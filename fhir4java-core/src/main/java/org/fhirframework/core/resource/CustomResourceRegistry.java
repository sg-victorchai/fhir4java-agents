package org.fhirframework.core.resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers custom FHIR resource classes generated from StructureDefinitions with HAPI FHIR contexts.
 * <p>
 * This component automatically scans for generated resource classes in the
 * {@code org.fhirframework.generated.resources} package and registers them with
 * the appropriate FHIR version contexts, allowing HAPI to parse, validate, and
 * serialize these custom resources.
 * </p>
 */
@Component
public class CustomResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceRegistry.class);
    private static final String GENERATED_RESOURCES_PACKAGE = "org.fhirframework.generated.resources";

    private final FhirContextFactory contextFactory;
    private final List<String> registeredResources = new ArrayList<>();

    public CustomResourceRegistry(FhirContextFactory contextFactory) {
        this.contextFactory = contextFactory;
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
     * Discover generated custom resource classes.
     * <p>
     * In a real implementation, this could scan the classpath or read a generated manifest file.
     * For now, we manually list known custom resources.
     * </p>
     *
     * @return List of custom resource class names
     */
    private List<String> discoverGeneratedResources() {
        List<String> resources = new ArrayList<>();

        // Add known custom resources
        // This list should ideally be generated during the code generation phase
        resources.add("MedicationInventory");

        // Future: Could scan classpath or read from a generated manifest
        // resources.addAll(scanGeneratedResourcesFromClasspath());

        return resources;
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
