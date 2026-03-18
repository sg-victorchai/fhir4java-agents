package org.fhirframework.core.config;

/**
 * Database schema configuration for a resource.
 * <p>
 * The schema configuration determines where FHIR resources are stored in the database.
 * Two properties control this behavior:
 * </p>
 * <ul>
 *   <li><b>type</b>: Semantic indicator of schema usage pattern
 *     <ul>
 *       <li>{@code shared} - Multiple resource types share this schema (e.g., "masterdata" for Patient, Practitioner)</li>
 *       <li>{@code dedicated} - Only this resource type uses this schema (e.g., "careplan" exclusively for CarePlan)</li>
 *     </ul>
 *   </li>
 *   <li><b>name</b>: The actual database schema name (e.g., "fhir", "masterdata", "careplan")</li>
 * </ul>
 * <p>
 * Note: The {@code type} field is primarily for documentation and validation purposes.
 * The actual routing is determined by the {@code name} field - any non-default schema name
 * triggers dynamic schema switching.
 * </p>
 * <p>
 * Examples:
 * <pre>
 * # Default schema (no switch needed)
 * schema:
 *   type: shared
 *   name: fhir
 *
 * # Shared custom schema (multiple resources share it)
 * schema:
 *   type: shared
 *   name: masterdata
 *
 * # Dedicated schema (exclusive to one resource type)
 * schema:
 *   type: dedicated
 *   name: careplan
 * </pre>
 * </p>
 */
public class SchemaConfig {

    private String type = "shared";
    private String name = "fhir";

    public SchemaConfig() {
    }

    public SchemaConfig(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns true if this is a shared schema (multiple resources in one schema).
     */
    public boolean isShared() {
        return "shared".equalsIgnoreCase(type);
    }

    /**
     * Returns true if this is a dedicated schema (one resource per schema).
     */
    public boolean isDedicated() {
        return "dedicated".equalsIgnoreCase(type);
    }

    /**
     * Returns the effective schema name to use for database operations.
     * <p>
     * Returns the configured schema name if set and not blank,
     * otherwise returns the provided default schema.
     * </p>
     * <p>
     * This applies to both shared and dedicated schema types - the schema name
     * configuration is always respected regardless of type.
     * </p>
     *
     * @param defaultSchema the default schema name from application configuration
     * @return the effective schema name
     */
    public String getEffectiveSchemaName(String defaultSchema) {
        return name != null && !name.isBlank() ? name : defaultSchema;
    }

    /**
     * Returns true if this configuration uses a non-default schema.
     * <p>
     * A non-default schema requires dynamic schema switching at runtime
     * since the JPA entity is bound to the default schema configured in Hibernate.
     * </p>
     *
     * @param defaultSchema the default schema name from application configuration
     * @return true if using a non-default schema
     */
    public boolean isNonDefaultSchema(String defaultSchema) {
        String effectiveName = getEffectiveSchemaName(defaultSchema);
        return !defaultSchema.equalsIgnoreCase(effectiveName);
    }

    @Override
    public String toString() {
        return "SchemaConfig{type='" + type + "', name='" + name + "'}";
    }
}
