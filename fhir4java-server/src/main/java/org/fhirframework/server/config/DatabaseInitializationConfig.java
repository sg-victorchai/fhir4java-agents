package org.fhirframework.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Database initialization configuration for AWS deployments.
 *
 * This configuration handles initial schema creation for RDS PostgreSQL instances
 * where the docker-entrypoint-initdb.d mechanism is not available.
 *
 * The initialization only runs when:
 * 1. The 'aws' profile is active
 * 2. DB_AUTO_INIT is set to true
 * 3. The 'fhir' schema does not exist OR the 'fhir_resource' table does not exist
 */
@Configuration
@Profile("aws")
@ConditionalOnProperty(name = "DB_AUTO_INIT", havingValue = "true")
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Value("${fhir4java.db.init-script:db/init/01-init-schema.sql}")
    private String initScriptPath;

    /**
     * Custom Flyway migration strategy that runs the init script before Flyway migrations
     * if the database schema has not been initialized.
     *
     * Uses raw JDBC instead of JdbcTemplate to avoid circular dependency with Flyway.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            if (shouldInitializeSchema(dataSource)) {
                initializeSchema(dataSource);
            }
            // Run Flyway migrations after schema initialization
            flyway.migrate();
        };
    }

    /**
     * Check if schema initialization is required using raw JDBC.
     * This avoids circular dependency with JdbcTemplate/Flyway.
     */
    private boolean shouldInitializeSchema(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if the 'fhir' schema exists
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'fhir')")) {
                if (rs.next() && !rs.getBoolean(1)) {
                    log.info("Schema 'fhir' does not exist, initialization required");
                    return true;
                }
            }

            // Check if the main table exists (indicates schema was fully initialized)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = 'fhir' AND table_name = 'fhir_resource')")) {
                if (rs.next() && !rs.getBoolean(1)) {
                    log.info("Table 'fhir.fhir_resource' does not exist, initialization required");
                    return true;
                }
            }

            log.info("Database schema already initialized, skipping initialization");
            return false;

        } catch (Exception e) {
            log.warn("Error checking schema existence, will attempt initialization: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Initialize database schema using raw JDBC.
     *
     * Uses direct JDBC execution instead of ResourceDatabasePopulator to properly handle
     * PostgreSQL PL/pgSQL functions with dollar-quoted strings ($$...$$).
     * ResourceDatabasePopulator incorrectly splits on semicolons inside function bodies.
     */
    private void initializeSchema(DataSource dataSource) {
        log.info("Initializing database schema from: {}", initScriptPath);
        try {
            // Read the entire SQL script
            ClassPathResource resource = new ClassPathResource(initScriptPath);
            String sqlScript = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            log.info("Read SQL script: {}, length: {} characters", initScriptPath, sqlScript.length());

            // Execute the script using JDBC - PostgreSQL handles the parsing correctly
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // PostgreSQL can execute multiple statements in one call
                // This properly handles dollar-quoted PL/pgSQL functions
                stmt.execute(sqlScript);

                log.info("Database schema initialization completed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to initialize database schema: {}", e.getMessage());
            throw new RuntimeException("Database schema initialization failed", e);
        }
    }
}
