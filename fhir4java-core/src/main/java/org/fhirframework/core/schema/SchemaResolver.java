package org.fhirframework.core.schema;

import org.fhirframework.core.resource.ResourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves database schema for FHIR resources based on configuration.
 * <p>
 * This component determines which database schema to use for resource storage
 * based on the resource configuration. It supports both:
 * <ul>
 *   <li>Default schema - configured via {@code spring.jpa.properties.hibernate.default_schema}</li>
 *   <li>Custom schemas - any configured schema name (e.g., "masterdata", "careplan")</li>
 * </ul>
 * </p>
 * <p>
 * The default schema name is read from application configuration to avoid hardcoding.
 * If not configured, falls back to "fhir".
 * </p>
 */
@Component
public class SchemaResolver {

    private static final Logger log = LoggerFactory.getLogger(SchemaResolver.class);

    private final ResourceRegistry resourceRegistry;
    private final String defaultSchema;

    public SchemaResolver(
            ResourceRegistry resourceRegistry,
            @Value("${spring.jpa.properties.hibernate.default_schema:fhir}") String defaultSchema) {
        this.resourceRegistry = resourceRegistry;
        this.defaultSchema = defaultSchema;
        log.info("SchemaResolver initialized with default schema: {}", defaultSchema);
    }

    /**
     * Resolves the schema name for a given resource type.
     * <p>
     * Returns the configured schema name from the resource configuration,
     * or the default schema if not configured.
     * </p>
     *
     * @param resourceType the FHIR resource type (e.g., "Patient", "CarePlan")
     * @return the schema name to use for storage
     */
    public String resolveSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
                .map(config -> {
                    String schemaName = config.getSchema().getEffectiveSchemaName(defaultSchema);
                    if (!defaultSchema.equalsIgnoreCase(schemaName)) {
                        log.debug("Resource {} uses custom schema: {}", resourceType, schemaName);
                    }
                    return schemaName;
                })
                .orElse(defaultSchema);
    }

    /**
     * Checks if a resource type requires dynamic schema switching.
     * <p>
     * Returns true if the resource is configured to use a non-default schema,
     * which requires runtime schema switching since the JPA entity is bound
     * to the default schema configured in Hibernate.
     * </p>
     *
     * @param resourceType the FHIR resource type
     * @return true if schema switching is required, false for default schema
     */
    public boolean requiresSchemaSwitch(String resourceType) {
        return resourceRegistry.getResource(resourceType)
                .map(config -> config.getSchema().isNonDefaultSchema(defaultSchema))
                .orElse(false);
    }

    /**
     * Checks if a resource type uses a dedicated schema.
     *
     * @param resourceType the FHIR resource type
     * @return true if the resource uses a dedicated schema, false otherwise
     * @deprecated Use {@link #requiresSchemaSwitch(String)} instead, which correctly
     *             handles all non-default schemas regardless of type (shared or dedicated).
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public boolean isDedicatedSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
                .map(config -> config.getSchema().isDedicated())
                .orElse(false);
    }

    /**
     * Returns the default schema name as configured in application properties.
     *
     * @return the default schema name
     */
    public String getDefaultSchema() {
        return defaultSchema;
    }
}
