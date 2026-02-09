package org.fhirframework.core.resource;

import ca.uhn.fhir.context.FhirContext;
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

                // Register the custom resource class with HAPI
                ctx.getResourceDefinition(typedResourceClass);

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
