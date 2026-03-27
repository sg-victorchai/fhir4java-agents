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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Database initialization configuration for AWS deployments.
 *
 * This configuration handles initial schema creation for RDS PostgreSQL instances
 * where the docker-entrypoint-initdb.d mechanism is not available.
 *
 * The initialization only runs when:
 * 1. The 'aws' profile is active
 * 2. fhir4java.db.auto-init is set to true
 * 3. The 'fhir' schema does not exist OR the 'fhir_resource' table does not exist
 */
@Configuration
@Profile("aws")
@ConditionalOnProperty(name = "fhir4java.db.auto-init", havingValue = "true")
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Value("${fhir4java.db.init-script:db/init/01-init-schema.sql}")
    private String initScriptPath;

    /**
     * Custom Flyway migration strategy that runs the init script before Flyway migrations
     * if the database schema has not been initialized.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        return flyway -> {
            if (shouldInitializeSchema(jdbcTemplate)) {
                initializeSchema(dataSource);
            }
            // Run Flyway migrations after schema initialization
            flyway.migrate();
        };
    }

    private boolean shouldInitializeSchema(JdbcTemplate jdbcTemplate) {
        try {
            // Check if the 'fhir' schema exists
            Boolean schemaExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'fhir')",
                Boolean.class
            );

            if (!Boolean.TRUE.equals(schemaExists)) {
                log.info("Schema 'fhir' does not exist, initialization required");
                return true;
            }

            // Check if the main table exists (indicates schema was fully initialized)
            Boolean tableExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = 'fhir' AND table_name = 'fhir_resource')",
                Boolean.class
            );

            if (!Boolean.TRUE.equals(tableExists)) {
                log.info("Table 'fhir.fhir_resource' does not exist, initialization required");
                return true;
            }

            log.info("Database schema already initialized, skipping initialization");
            return false;

        } catch (Exception e) {
            log.warn("Error checking schema existence, will attempt initialization: {}", e.getMessage());
            return true;
        }
    }

    private void initializeSchema(DataSource dataSource) {
        log.info("Initializing database schema from: {}", initScriptPath);
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(initScriptPath));
            populator.setSeparator(";");
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("Database schema initialization completed successfully");
        } catch (Exception e) {
            log.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database schema initialization failed", e);
        }
    }
}
