package org.fhirframework.server.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.fhirframework.server.Fhir4JavaApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring configuration for Cucumber BDD tests.
 * <p>
 * Boots the full Spring Boot application on a random port
 * with the test profile (H2 in-memory database, no Redis).
 * </p>
 */
@CucumberContextConfiguration
@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
public class CucumberSpringConfig {
}
