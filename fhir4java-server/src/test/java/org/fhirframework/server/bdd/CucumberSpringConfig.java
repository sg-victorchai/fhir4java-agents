package org.fhirframework.server.bdd;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;

import org.fhirframework.server.Fhir4JavaApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
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
	
	
}
