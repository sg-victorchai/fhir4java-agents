package org.fhirframework.server.bdd;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * TestContainers configuration for PostgreSQL-based BDD tests.
 * <p>
 * Only active when the {@code test-postgres} profile is enabled.
 * Uses Spring Boot's {@link ServiceConnection} to automatically configure
 * the datasource connection properties from the container.
 * </p>
 * <p>
 * Activate with: {@code ./mvnw test -pl fhir4java-server -Dtest=CucumberIT -Ptest-postgres}
 * </p>
 */
@TestConfiguration
@Profile("test-postgres")
public class PostgresTestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("fhir4java_test")
                .withUsername("fhir4java")
                .withPassword("fhir4java")
                .withInitScript("db/init-schemas.sql");
    }
}
