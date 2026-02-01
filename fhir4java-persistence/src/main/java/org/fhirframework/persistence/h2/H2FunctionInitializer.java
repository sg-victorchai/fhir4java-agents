package org.fhirframework.persistence.h2;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
 */
@Configuration
@Profile("test")
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

            stmt.execute("CREATE ALIAS IF NOT EXISTS jsonb_extract_path_text FOR "
                    + "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_extract_path_text\"");

            stmt.execute("CREATE ALIAS IF NOT EXISTS to_number FOR "
                    + "\"org.fhirframework.persistence.h2.H2JsonFunctions.to_number\"");

            log.info("Registered H2 function aliases: jsonb_extract_path_text, to_number");
        } catch (Exception e) {
            log.warn("Could not register H2 function aliases (not H2?): {}", e.getMessage());
        }
    }
}
