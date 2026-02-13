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
 * This configuration creates the fhir_resource table in dedicated schemas
 * (like 'careplan') to mirror the PostgreSQL schema structure during tests.
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
                createDedicatedSchemaTable(stmt, schemaName);
            }

            log.info("Initialized H2 dedicated schemas: {}", (Object) DEDICATED_SCHEMAS);
        } catch (Exception e) {
            log.warn("Could not initialize H2 dedicated schemas (not H2?): {}", e.getMessage());
        }
    }

    /**
     * Creates the fhir_resource table in a dedicated schema.
     * Mirrors the structure from V1__init_schema.sql.
     */
    private void createDedicatedSchemaTable(Statement stmt, String schemaName) throws Exception {
        // Create schema if not exists (already done in JDBC URL but be safe)
        stmt.execute(String.format("CREATE SCHEMA IF NOT EXISTS %s", schemaName.toUpperCase()));

        // Create the fhir_resource table in the dedicated schema
        String createTableSql = String.format("""
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
            """, schemaName.toUpperCase(), schemaName.toLowerCase());
        stmt.execute(createTableSql);

        // Create indexes
        String[] indexStatements = {
                String.format("CREATE INDEX IF NOT EXISTS idx_%s_resource_type ON %s.fhir_resource(resource_type)",
                        schemaName.toLowerCase(), schemaName.toUpperCase()),
                String.format("CREATE INDEX IF NOT EXISTS idx_%s_resource_id ON %s.fhir_resource(resource_id)",
                        schemaName.toLowerCase(), schemaName.toUpperCase()),
                String.format("CREATE INDEX IF NOT EXISTS idx_%s_tenant ON %s.fhir_resource(tenant_id)",
                        schemaName.toLowerCase(), schemaName.toUpperCase()),
                String.format("CREATE INDEX IF NOT EXISTS idx_%s_current ON %s.fhir_resource(is_current)",
                        schemaName.toLowerCase(), schemaName.toUpperCase())
        };

        for (String indexSql : indexStatements) {
            try {
                stmt.execute(indexSql);
            } catch (Exception e) {
                // Index might already exist, continue
                log.debug("Index creation skipped for {}: {}", schemaName, e.getMessage());
            }
        }

        log.debug("Created fhir_resource table in schema: {}", schemaName);
    }
}
