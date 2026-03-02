package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR search BDD tests.
 * Covers allowlist/denylist enforcement, modifiers, pagination, and POST search.
 */
public class SearchSteps {

    @Autowired
    private SharedTestContext ctx;

    // ========== Given Steps ==========

    @Given("a Patient resource exists with search data")
    public void aPatientResourceExistsWithSearchData() throws IOException {
        String body = TestDataLoader.load("crud/patient-common-set.json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/Patient");

        response.then().statusCode(201);
        ctx.setLastResponse(response);
        ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
        ctx.setLastResourceType("Patient");
    }

    @Given("an Observation resource exists with search data")
    public void anObservationResourceExistsWithSearchData() throws IOException {
        String body = TestDataLoader.load("crud/observation-common-set.json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/Observation");

        response.then().statusCode(201);
        ctx.setLastResponse(response);
        ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
        ctx.setLastResourceType("Observation");
    }

    @Given("{int} Patient resources exist")
    public void multiplePatientResourcesExist(int count) throws IOException {
        String body = TestDataLoader.load("crud/patient.json");
        for (int i = 0; i < count; i++) {
            Response response = given()
                    .contentType("application/fhir+json")
                    .body(body)
                    .when()
                    .post("/Patient");
            response.then().statusCode(201);
            ctx.setLastResponse(response);
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
        }
        ctx.setLastResourceType("Patient");
    }

    // ========== When Steps ==========

    @When("I search for {word} with parameter {string} value {string}")
    public void iSearchForResourceWithParameter(String resourceType, String param, String value) {
        Response response = given()
                .accept("application/fhir+json")
                .queryParam(param, value)
                .when()
                .get("/" + resourceType);

        ctx.setLastResponse(response);
    }

    @When("I search for {word} by _id")
    public void iSearchForResourceById(String resourceType) {
        Response response = given()
                .accept("application/fhir+json")
                .queryParam("_id", ctx.getLastCreatedResourceId())
                .when()
                .get("/" + resourceType);

        ctx.setLastResponse(response);
    }

    @When("I search for {word} resources")
    public void iSearchForResources(String resourceType) {
        Response response = given()
                .accept("application/fhir+json")
                .when()
                .get("/" + resourceType);

        ctx.setLastResponse(response);
    }

    @When("I POST search for {word} with parameter {string} value {string}")
    public void iPostSearchForResourceWithParameter(String resourceType, String param, String value) {
        Response response = given()
                .accept("application/fhir+json")
                .queryParam(param, value)
                .when()
                .post("/" + resourceType + "/_search");

        ctx.setLastResponse(response);
    }

    @When("I POST search for {word} with parameters {string}")
    public void iPostSearchForResourceWithParameters(String resourceType, String queryString) {
        String[] pairs = queryString.split("&");
        var req = given().accept("application/fhir+json");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            req = req.queryParam(kv[0], kv.length > 1 ? kv[1] : "");
        }
        Response response = req.when().post("/" + resourceType + "/_search");
        ctx.setLastResponse(response);
    }

    // ========== Then Steps ==========

    @Then("the response should be a search Bundle")
    public void theResponseShouldBeASearchBundle() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("searchset"));
    }

    @Then("the Bundle total should be greater than {int}")
    public void theBundleTotalShouldBeGreaterThan(int count) {
        ctx.getLastResponse().then()
                .body("total", greaterThan(count));
    }

    @Then("the Bundle total should be {int}")
    public void theBundleTotalShouldBe(int count) {
        ctx.getLastResponse().then()
                .body("total", equalTo(count));
    }

    @Then("the Bundle should have at most {int} entries")
    public void theBundleShouldHaveAtMostEntries(int max) {
        ctx.getLastResponse().then()
                .body("entry.size()", lessThanOrEqualTo(max));
    }

    @Then("the Bundle should have a next link")
    public void theBundleShouldHaveANextLink() {
        ctx.getLastResponse().then()
                .body("link.find { it.relation == 'next' }", notNullValue());
    }
}
