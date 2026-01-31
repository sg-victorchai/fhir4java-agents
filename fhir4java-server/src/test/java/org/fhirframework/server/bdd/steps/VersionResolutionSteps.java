package org.fhirframework.server.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR version resolution BDD tests.
 */
public class VersionResolutionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private SharedTestContext ctx;

    @Before
    public void setUp() {
        RestAssured.port = port;
    }

    // ========== When Steps ==========

    @When("I read the Patient via versioned path {string}")
    public void iReadThePatientViaVersionedPath(String pathTemplate) {
        String path = pathTemplate.replace("{id}", ctx.getLastCreatedPatientId());
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I read the Patient via unversioned path {string}")
    public void iReadThePatientViaUnversionedPath(String pathTemplate) {
        String path = pathTemplate.replace("{id}", ctx.getLastCreatedPatientId());
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I request metadata via {string}")
    public void iRequestMetadataVia(String path) {
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I search for Patients via {string}")
    public void iSearchForPatientsVia(String path) {
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    // ========== Then Steps ==========

    @Then("the response should have header {string} with value {string}")
    public void theResponseShouldHaveHeaderWithValue(String header, String value) {
        ctx.getLastResponse().then()
                .header(header, equalTo(value));
    }

    @Then("the response should contain {string} with value {string}")
    public void theResponseShouldContainFieldWithValue(String field, String value) {
        ctx.getLastResponse().then()
                .body(field, equalTo(value));
    }

    @Then("the response body should contain {string}")
    public void theResponseBodyShouldContain(String field) {
        ctx.getLastResponse().then()
                .body(field, notNullValue());
    }
}
