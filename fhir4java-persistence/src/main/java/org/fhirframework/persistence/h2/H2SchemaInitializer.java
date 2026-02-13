package org.fhirframework.persistence.h2;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Initializes dedicated schemas in H2 for testing.
 * <p>
 * This configuration creates ALL resource-related tables in dedicated schemas
 * (like 'careplan') to mirror the PostgreSQL schema structure during tests.
 * This implements the "Full Table Replication" design pattern.
 * </p>
 * <p>
 * Tables created in each dedicated schema:
 * <ul>
 *   <li>fhir_resource - Primary resource storage</li>
 *   <li>fhir_resource_history - Version history</li>
 *   <li>fhir_search_index - Search parameter indexes</li>
 *   <li>fhir_resource_tag - Tags, security labels, profiles</li>
 *   <li>fhir_compartment - Compartment membership</li>
 * </ul>
 * </p>
 * <p>
 * Note: fhir_audit_log is NOT replicated as it's a cross-cutting concern
 * that remains in the shared 'fhir' schema.
 * </p>
 */
@Configuration
@Profile("test")
@Order(1) // Run before other initializers
public class H2SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(H2SchemaInitializer.class);

    private final DataSource dataSource;

    // List of dedicated schemas to create tables for
    private static final String[] DEDICATED_SCHEMAS = {"careplan"};

    public H2SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void initializeDedicatedSchemas() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String schemaName : DEDICATED_SCHEMAS) {
                createDedicatedSchemaTables(stmt, schemaName);
            }

            log.info("Initialized H2 dedicated schemas with full table replication: {}", (Object) DEDICATED_SCHEMAS);
        } catch (Exception e) {
            log.warn("Could not initialize H2 dedicated schemas (not H2?): {}", e.getMessage());
        }
    }

    /**
     * Creates all resource-related tables in a dedicated schema.
     * Mirrors the structure from V3__add_dedicated_schemas.sql.
     */
    private void createDedicatedSchemaTables(Statement stmt, String schemaName) throws Exception {
        String upperSchema = schemaName.toUpperCase();
        String lowerSchema = schemaName.toLowerCase();

        // Create schema if not exists (already done in JDBC URL but be safe)
        stmt.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", upperSchema));

        // ========================================
        // 1. Create fhir_resource table
        // ========================================
        String createResourceTable = String.format("""
            CREATE TABLE IF NOT EXISTS %s.fhir_resource (
                id UUID PRIMARY KEY,
                resource_type VARCHAR(100) NOT NULL,
                resource_id VARCHAR(64) NOT NULL,
                fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
                version_id INTEGER NOT NULL DEFAULT 1,
                is_current BOOLEAN NOT NULL DEFAULT TRUE,
                is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                content CLOB NOT NULL,
                last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                source_uri VARCHAR(2048),
                tenant_id VARCHAR(64) DEFAULT 'default',
                CONSTRAINT uk_%s_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
            )
            """, upperSchema, lowerSchema);
        stmt.execute(createResourceTable);

        // fhir_resource indexes
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_resource_type ON %s.fhir_resource(resource_type)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_resource_id ON %s.fhir_resource(resource_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_tenant ON %s.fhir_resource(tenant_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_current ON %s.fhir_resource(is_current)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_last_updated ON %s.fhir_resource(last_updated)",
            lowerSchema, upperSchema));

        log.debug("Created fhir_resource table in schema: {}", schemaName);

        // ========================================
        // 2. Create fhir_resource_history table
        // ========================================
        String createHistoryTable = String.format("""
            CREATE TABLE IF NOT EXISTS %s.fhir_resource_history (
                id UUID PRIMARY KEY,
                resource_id UUID NOT NULL,
                version_id INTEGER NOT NULL,
                content CLOB NOT NULL,
                operation VARCHAR(20) NOT NULL,
                changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                changed_by VARCHAR(256),
                CONSTRAINT uk_%s_history_version UNIQUE (resource_id, version_id),
                CONSTRAINT fk_%s_history_resource FOREIGN KEY (resource_id)
                    REFERENCES %s.fhir_resource(id) ON DELETE CASCADE
            )
            """, upperSchema, lowerSchema, lowerSchema, upperSchema);
        stmt.execute(createHistoryTable);

        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_history_resource ON %s.fhir_resource_history(resource_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_history_changed_at ON %s.fhir_resource_history(changed_at)",
            lowerSchema, upperSchema));

        log.debug("Created fhir_resource_history table in schema: {}", schemaName);

        // ========================================
        // 3. Create fhir_search_index table
        // ========================================
        String createSearchIndexTable = String.format("""
            CREATE TABLE IF NOT EXISTS %s.fhir_search_index (
                id UUID PRIMARY KEY,
                resource_id UUID NOT NULL,
                resource_type VARCHAR(100) NOT NULL,
                tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                param_name VARCHAR(100) NOT NULL,
                param_type VARCHAR(20) NOT NULL,
                value_string VARCHAR(2048),
                value_string_lower VARCHAR(2048),
                value_token_system VARCHAR(2048),
                value_token_code VARCHAR(256),
                value_reference_type VARCHAR(100),
                value_reference_id VARCHAR(64),
                value_date_start TIMESTAMP WITH TIME ZONE,
                value_date_end TIMESTAMP WITH TIME ZONE,
                value_number DECIMAL(19, 6),
                value_quantity_value DECIMAL(19, 6),
                value_quantity_unit VARCHAR(100),
                value_quantity_system VARCHAR(2048),
                value_quantity_code VARCHAR(100),
                value_uri VARCHAR(2048),
                composite_part INTEGER,
                CONSTRAINT fk_%s_search_resource FOREIGN KEY (resource_id)
                    REFERENCES %s.fhir_resource(id) ON DELETE CASCADE
            )
            """, upperSchema, lowerSchema, upperSchema);
        stmt.execute(createSearchIndexTable);

        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_search_resource ON %s.fhir_search_index(resource_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_search_type_param ON %s.fhir_search_index(resource_type, param_name)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_search_tenant_type ON %s.fhir_search_index(tenant_id, resource_type)",
            lowerSchema, upperSchema));

        log.debug("Created fhir_search_index table in schema: {}", schemaName);

        // ========================================
        // 4. Create fhir_resource_tag table
        // ========================================
        String createTagTable = String.format("""
            CREATE TABLE IF NOT EXISTS %s.fhir_resource_tag (
                id UUID PRIMARY KEY,
                resource_id UUID NOT NULL,
                tag_type VARCHAR(20) NOT NULL,
                system_uri VARCHAR(2048),
                code VARCHAR(256),
                display VARCHAR(1024),
                CONSTRAINT fk_%s_tag_resource FOREIGN KEY (resource_id)
                    REFERENCES %s.fhir_resource(id) ON DELETE CASCADE
            )
            """, upperSchema, lowerSchema, upperSchema);
        stmt.execute(createTagTable);

        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_tag_resource ON %s.fhir_resource_tag(resource_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_tag_lookup ON %s.fhir_resource_tag(tag_type, system_uri, code)",
            lowerSchema, upperSchema));

        log.debug("Created fhir_resource_tag table in schema: {}", schemaName);

        // ========================================
        // 5. Create fhir_compartment table
        // ========================================
        String createCompartmentTable = String.format("""
            CREATE TABLE IF NOT EXISTS %s.fhir_compartment (
                id UUID PRIMARY KEY,
                resource_id UUID NOT NULL,
                compartment_type VARCHAR(100) NOT NULL,
                compartment_id VARCHAR(64) NOT NULL,
                CONSTRAINT uk_%s_compartment UNIQUE (resource_id, compartment_type, compartment_id),
                CONSTRAINT fk_%s_compartment_resource FOREIGN KEY (resource_id)
                    REFERENCES %s.fhir_resource(id) ON DELETE CASCADE
            )
            """, upperSchema, lowerSchema, lowerSchema, upperSchema);
        stmt.execute(createCompartmentTable);

        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_compartment_lookup ON %s.fhir_compartment(compartment_type, compartment_id)",
            lowerSchema, upperSchema));
        createIndexSafe(stmt, String.format(
            "CREATE INDEX IF NOT EXISTS idx_%s_compartment_resource ON %s.fhir_compartment(resource_id)",
            lowerSchema, upperSchema));

        log.debug("Created fhir_compartment table in schema: {}", schemaName);

        log.info("Created all tables in dedicated schema: {}", schemaName);
    }

    /**
     * Safely creates an index, ignoring errors if it already exists.
     */
    private void createIndexSafe(Statement stmt, String indexSql) {
        try {
            stmt.execute(indexSql);
        } catch (Exception e) {
            // Index might already exist, continue
            log.debug("Index creation skipped: {}", e.getMessage());
        }
    }
}
