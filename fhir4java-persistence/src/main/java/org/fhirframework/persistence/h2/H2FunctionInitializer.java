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
 * Registers custom H2 function aliases that emulate PostgreSQL functions.
 * <p>
 * This configuration only activates under the "test" profile (H2 database).
 * It registers {@code jsonb_extract_path_text} and {@code to_number} as H2
 * function aliases pointing to {@link H2JsonFunctions} static methods.
 * </p>
 * <p>
 * Functions are created in all schemas (PUBLIC, FHIR, and custom schemas like
 * careplan, masterdata, patientdata, operationdata) to ensure they are accessible
 * when queries run against schema-qualified tables.
 * </p>
 */
@Configuration
@Profile("test")
@Order(2) // Run after H2SchemaInitializer (Order 1)
public class H2FunctionInitializer {

    private static final Logger log = LoggerFactory.getLogger(H2FunctionInitializer.class);

    private final DataSource dataSource;

    public H2FunctionInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void registerH2Functions() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Register function aliases in PUBLIC, FHIR, and all custom schemas
            // This ensures functions are accessible regardless of search_path settings
            // Custom schemas: careplan, masterdata, patientdata, operationdata
            String[] schemas = {"", "FHIR.", "CAREPLAN.", "MASTERDATA.", "PATIENTDATA.", "OPERATIONDATA."};

            for (String schema : schemas) {
                stmt.execute("CREATE ALIAS IF NOT EXISTS " + schema + "jsonb_extract_path_text FOR "
                        + "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_extract_path_text\"");

                stmt.execute("CREATE ALIAS IF NOT EXISTS " + schema + "jsonb_extract_path FOR "
                        + "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_extract_path\"");

                stmt.execute("CREATE ALIAS IF NOT EXISTS " + schema + "jsonb_contains FOR "
                        + "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_contains\"");

                stmt.execute("CREATE ALIAS IF NOT EXISTS " + schema + "to_number FOR "
                        + "\"org.fhirframework.persistence.h2.H2JsonFunctions.to_number\"");
            }

            log.info("Registered H2 function aliases in PUBLIC, FHIR, and custom schemas: jsonb_extract_path_text, jsonb_extract_path, jsonb_contains, to_number");
        } catch (Exception e) {
            // Fail loudly in test mode so we can see the actual error
            log.error("Failed to register H2 function aliases: {}", e.getMessage(), e);
            throw new RuntimeException("H2 function alias registration failed - tests cannot proceed", e);
        }
    }
}
