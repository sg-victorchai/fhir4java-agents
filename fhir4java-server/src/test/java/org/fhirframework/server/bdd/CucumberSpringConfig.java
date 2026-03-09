package org.fhirframework.server.bdd;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

import org.fhirframework.server.Fhir4JavaApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Spring configuration for Cucumber BDD tests.
 * <p>
 * Boots the full Spring Boot application on a random port.
 * Profile is determined by the spring.profiles.active system property:
 * </p>
 * <ul>
 *   <li>{@code test} (default) - H2 in-memory database</li>
 *   <li>{@code test-postgres} - PostgreSQL via TestContainers</li>
 * </ul>
 */
@CucumberContextConfiguration
@SpringBootTest(
		classes = Fhir4JavaApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
		)
@Import(PostgresTestContainersConfig.class)
@ActiveProfiles(resolver = CucumberSpringConfig.TestProfileResolver.class)
public class CucumberSpringConfig {

	@LocalServerPort
	private int port;
	
	public CucumberSpringConfig() {
        System.out.println("========================================");
        System.out.println("CucumberSpringConfig CONSTRUCTOR called");
        System.out.println("========================================");
 
	}

	
	@Before
	public void setUp() {

		io.restassured.RestAssured.port = port;
        io.restassured.RestAssured.basePath = "/fhir";

        System.out.println("========================================");
		System.out.println("Port set to: " + port);
	    System.out.println("BasePath set to: /fhir");
	    System.out.println("Current RestAssured port AFTER: " + RestAssured.port);
	    System.out.println("Current RestAssured basePath AFTER: " + RestAssured.basePath);
	    System.out.println("========================================");

	}

	/**
	 * Resolves active profiles from the spring.profiles.active system property.
	 * Defaults to "test" (H2) if no property is set.
	 */
	public static class TestProfileResolver implements ActiveProfilesResolver {
		@Override
		public String[] resolve(Class<?> testClass) {
			String profile = System.getProperty("spring.profiles.active", "test");
			System.out.println("========================================");
			System.out.println("TestProfileResolver: Using profile '" + profile + "'");
			System.out.println("========================================");
			return new String[] { profile };
		}
	}
}
