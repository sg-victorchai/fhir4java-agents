package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR resource CRUD lifecycle BDD tests.
 * <p>
 * Uses {@link TestDataLoader} to load resource JSON from external files.
 * Supports both standard and custom FHIR resources via parameterized steps.
 * </p>
 */
public class CrudSteps {

    @Autowired
    private SharedTestContext ctx;

    // ========== Given Steps ==========

    @Given("a {word} resource exists")
    public void aResourceExists(String resourceType) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + ".json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/" + resourceType);

        response.then().statusCode(201);
        ctx.setLastResponse(response);
        String resourceId = ctx.extractResourceId(response.body().asString());
        ctx.setLastCreatedResourceId(resourceId);
        ctx.setLastResourceType(resourceType);
        ctx.captureEtag();
        // Also set Patient-specific ID for backward compatibility with OperationSteps
        if ("Patient".equals(resourceType)) {
            ctx.setLastCreatedPatientId(resourceId);
        }
    }

    @Given("the {word} resource is deleted")
    public void theResourceIsDeleted(String resourceType) {
        Response response = given()
                .when()
                .delete("/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        response.then().statusCode(204);
    }

    // ========== When Steps ==========

    @When("I create a {word} resource")
    public void iCreateAResource(String resourceType) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + ".json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/" + resourceType);

        ctx.setLastResponse(response);
        ctx.setLastResourceType(resourceType);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.captureEtag();
        }
    }

    @When("I create a {string} {word} resource")
    public void iCreateAProfileResource(String profile, String resourceType) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + "-" + profile + ".json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/" + resourceType);

        ctx.setLastResponse(response);
        ctx.setLastResourceType(resourceType);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.captureEtag();
        }
    }

    @When("I create a {word} resource via version {string}")
    public void iCreateAResourceViaVersion(String resourceType, String version) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + ".json");
        Response response = given()
                .basePath("")
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .post("/fhir/" + version + "/" + resourceType);

        ctx.setLastResponse(response);
        ctx.setLastResourceType(resourceType);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.captureEtag();
        }
    }

    @When("I read the {word} resource by its ID")
    public void iReadTheResourceByItsId(String resourceType) {
        Response response = given()
                .accept("application/fhir+json")
                .when()
                .get("/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
        ctx.captureEtag();
    }

    @When("I read a {word} resource with ID {string}")
    public void iReadAResourceWithId(String resourceType, String id) {
        Response response = given()
                .accept("application/fhir+json")
                .when()
                .get("/" + resourceType + "/" + id);

        ctx.setLastResponse(response);
    }

    @When("I vread the {word} resource at version {int}")
    public void iVreadTheResourceAtVersion(String resourceType, int versionId) {
        Response response = given()
                .accept("application/fhir+json")
                .when()
                .get("/" + resourceType + "/" + ctx.getLastCreatedResourceId()
                        + "/_history/" + versionId);

        ctx.setLastResponse(response);
    }

    @When("I update the {word} resource")
    public void iUpdateTheResource(String resourceType) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + "-update.json");
        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .put("/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
        ctx.captureEtag();
    }

    @When("I patch the {word} resource")
    public void iPatchTheResource(String resourceType) throws IOException {
        String body = TestDataLoader.load("crud/" + resourceType.toLowerCase() + "-patch.json");
        Response response = given()
                .contentType("application/json-patch+json")
                .body(body)
                .when()
                .patch("/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
        ctx.captureEtag();
    }

    @When("I delete the {word} resource")
    public void iDeleteTheResource(String resourceType) {
        Response response = given()
                .when()
                .delete("/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
    }

    @When("I read the created resource via version {string} for {string}")
    public void iReadTheCreatedResourceViaVersion(String version, String resourceType) {
        Response response = given()
                .basePath("")
                .accept("application/fhir+json")
                .when()
                .get("/fhir/" + version + "/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
        ctx.captureEtag();
    }

    @When("I read the created resource via unversioned path for {string}")
    public void iReadTheCreatedResourceViaUnversionedPath(String resourceType) {
        Response response = given()
                .basePath("")
                .accept("application/fhir+json")
                .when()
                .get("/fhir/" + resourceType + "/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
        ctx.captureEtag();
    }

    @When("I search via version {string} for {string}")
    public void iSearchViaVersionFor(String version, String resourceType) {
        Response response = given()
                .basePath("")
                .accept("application/fhir+json")
                .when()
                .get("/fhir/" + version + "/" + resourceType);

        ctx.setLastResponse(response);
    }

    // ========== Then Steps ==========

    @Then("the response should contain resourceType {string}")
    public void theResponseShouldContainResourceType(String expectedType) {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo(expectedType));
    }

    @Then("the response should have a Location header")
    public void theResponseShouldHaveALocationHeader() {
        ctx.getLastResponse().then()
                .header("Location", notNullValue());
    }
}
