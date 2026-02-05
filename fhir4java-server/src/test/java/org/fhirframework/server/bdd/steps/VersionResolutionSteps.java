package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR version resolution BDD tests.
 * Uses absolute paths (including /fhir prefix), so basePath must be empty.
 */
public class VersionResolutionSteps {

    @Autowired
    private SharedTestContext ctx;

    /**
     * Build a request with empty basePath to avoid double-prefix issues
     * since version resolution paths already include /fhir/.
     */
    private RequestSpecification request() {
        return given().basePath("");
    }

    // ========== When Steps ==========

    @When("I read the Patient via versioned path {string}")
    public void iReadThePatientViaVersionedPath(String pathTemplate) {
        String path = pathTemplate.replace("{id}", ctx.getLastCreatedPatientId());
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I read the Patient via unversioned path {string}")
    public void iReadThePatientViaUnversionedPath(String pathTemplate) {
        String path = pathTemplate.replace("{id}", ctx.getLastCreatedPatientId());
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I request metadata via {string}")
    public void iRequestMetadataVia(String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I search for Patients via {string}")
    public void iSearchForPatientsVia(String path) {
        ctx.setLastResponse(request()
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
