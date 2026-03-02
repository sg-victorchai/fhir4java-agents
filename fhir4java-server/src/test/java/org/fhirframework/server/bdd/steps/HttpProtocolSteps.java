package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for HTTP protocol, headers, content negotiation,
 * conditional operations, and error response BDD tests.
 */
public class HttpProtocolSteps {

    @Autowired
    private SharedTestContext ctx;

    // ========== When Steps ==========

    @When("I read the Patient with Accept {string}")
    public void iReadThePatientWithAccept(String accept) {
        Response response = given()
                .accept(accept)
                .when()
                .get("/Patient/" + ctx.getLastCreatedResourceId());
        ctx.setLastResponse(response);
    }

    @When("I read the Patient without Accept header")
    public void iReadThePatientWithoutAcceptHeader() {
        Response response = given()
                .when()
                .get("/Patient/" + ctx.getLastCreatedResourceId());
        ctx.setLastResponse(response);
    }

    @When("I create a Patient with Content-Type {string}")
    public void iCreateAPatientWithContentType(String contentType) throws IOException {
        String body = TestDataLoader.load("crud/patient.json");
        Response response = given()
                .contentType(contentType)
                .body(body)
                .when()
                .post("/Patient");
        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.setLastCreatedPatientId(ctx.extractResourceId(response.body().asString()));
        }
    }

    @When("I create a Patient with invalid body {string}")
    public void iCreateAPatientWithInvalidBody(String body) {
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/Patient");
        ctx.setLastResponse(response);
    }

    @When("I read the Patient with If-None-Match matching the current ETag")
    public void iReadThePatientWithIfNoneMatchMatchingCurrentEtag() {
        Response response = given()
                .accept("application/fhir+json")
                .header("If-None-Match", ctx.getLastEtag())
                .when()
                .get("/Patient/" + ctx.getLastCreatedResourceId());
        ctx.setLastResponse(response);
    }

    @When("I read the Patient with If-None-Match {string}")
    public void iReadThePatientWithIfNoneMatch(String etag) {
        Response response = given()
                .accept("application/fhir+json")
                .header("If-None-Match", etag)
                .when()
                .get("/Patient/" + ctx.getLastCreatedResourceId());
        ctx.setLastResponse(response);
    }

    // ========== Then Steps ==========

    @Then("the response Content-Type should be {string}")
    public void theResponseContentTypeShouldBe(String expected) {
        ctx.getLastResponse().then()
                .contentType(containsString(expected));
    }

    @Then("the response should have a Content-Location header")
    public void theResponseShouldHaveAContentLocationHeader() {
        ctx.getLastResponse().then()
                .header("Content-Location", notNullValue());
    }
}
