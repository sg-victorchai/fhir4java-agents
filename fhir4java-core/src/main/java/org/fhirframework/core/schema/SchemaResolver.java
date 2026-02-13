package org.fhirframework.core.schema;

import org.fhirframework.core.resource.ResourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves database schema for FHIR resources based on configuration.
 * <p>
 * This component determines whether a resource should be stored in the shared schema
 * or a dedicated schema based on the resource configuration.
 * </p>
 */
@Component
public class SchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(SchemaResolver.class);
    private static final String DEFAULT_SCHEMA = "fhir";

    private final ResourceRegistry resourceRegistry;

    public SchemaResolver(ResourceRegistry resourceRegistry) {
        this.resourceRegistry = resourceRegistry;
    }

    /**
     * Resolves the schema name for a given resource type.
     * <p>
     * Returns the dedicated schema name if the resource is configured with schema.type=dedicated,
     * otherwise returns the default shared schema ("fhir").
     * </p>
     *
     * @param resourceType the FHIR resource type (e.g., "Patient", "CarePlan")
     * @return the schema name to use for storage
     */
    public String resolveSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
                .map(config -> {
                    if (config.getSchema().isDedicated()) {
                        String schemaName = config.getSchema().getEffectiveSchemaName();
                        log.debug("Resource {} uses dedicated schema: {}", resourceType, schemaName);
                        return schemaName;
                    }
                    return DEFAULT_SCHEMA;
                })
                .orElse(DEFAULT_SCHEMA);
    }

    /**
     * Checks if a resource type uses a dedicated schema.
     *
     * @param resourceType the FHIR resource type
     * @return true if the resource uses a dedicated schema, false otherwise
     */
    public boolean isDedicatedSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
                .map(config -> config.getSchema().isDedicated())
                .orElse(false);
    }

    /**
     * Returns the default shared schema name.
     *
     * @return the default schema name ("fhir")
     */
    public String getDefaultSchema() {
        return DEFAULT_SCHEMA;
    }
}
