package org.fhirframework.server.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR extended operation BDD tests.
 */
public class OperationSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private SharedTestContext ctx;

    @Before
    public void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/fhir";
    }

    // ========== Given Steps ==========

    @Given("the FHIR server is running")
    public void theFhirServerIsRunning() {
        // Server is started by Spring Boot test
    }

    @Given("a Patient resource exists")
    public void aPatientResourceExists() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Smith", "given": ["John"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        Response response = given()
                .contentType("application/fhir+json")
                .body(patientJson)
                .when()
                .post("/Patient");

        response.then().statusCode(201);
        ctx.setLastResponse(response);
        ctx.setLastCreatedPatientId(ctx.extractResourceId(response.body().asString()));
    }

    @Given("a Patient resource exists with id {string}")
    public void aPatientResourceExistsWithId(String id) {
        aPatientResourceExists();
        // The id is auto-generated; store for later reference
    }

    @Given("two Patient resources exist for merge")
    public void twoPatientResourcesExistForMerge() {
        // Create source patient
        String sourceJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "OldName", "given": ["Source"]}]
                }
                """;
        Response sourceResponse = given()
                .contentType("application/fhir+json")
                .body(sourceJson)
                .when()
                .post("/Patient");
        sourceResponse.then().statusCode(201);
        ctx.setLastCreatedPatientId(ctx.extractResourceId(sourceResponse.body().asString()));

        // Create target patient
        String targetJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "NewName", "given": ["Target"]}]
                }
                """;
        Response targetResponse = given()
                .contentType("application/fhir+json")
                .body(targetJson)
                .when()
                .post("/Patient");
        targetResponse.then().statusCode(201);
        ctx.setLastCreatedResourceId(ctx.extractResourceId(targetResponse.body().asString()));
    }

    @Given("I have a valid Patient resource")
    public void iHaveAValidPatientResource() {
        ctx.setRequestBody("""
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Doe", "given": ["Jane"]}],
                    "gender": "female",
                    "birthDate": "1985-06-15"
                }
                """);
    }

    @Given("I have an invalid Patient resource")
    public void iHaveAnInvalidPatientResource() {
        ctx.setRequestBody("""
                {
                    "resourceType": "Patient",
                    "name": "not-valid-name-format"
                }
                """);
    }

    @Given("I have a JSON Patch document to update the patient name")
    public void iHaveAJsonPatchDocument() {
        ctx.setRequestBody("""
                [
                    {"op": "replace", "path": "/name/0/family", "value": "Johnson"}
                ]
                """);
    }

    @Given("I have an invalid JSON Patch document")
    public void iHaveAnInvalidJsonPatchDocument() {
        ctx.setRequestBody("""
                [
                    {"op": "replace", "path": "/nonexistent/deep/path/0/1/2/3", "value": "test"}
                ]
                """);
    }

    // ========== When Steps ==========

    @When("I validate the Patient resource at type level")
    public void iValidateThePatientResourceAtTypeLevel() {
        ctx.setLastResponse(given()
                .contentType("application/fhir+json")
                .body(ctx.getRequestBody())
                .when()
                .post("/Patient/$validate"));
    }

    @When("I validate the Patient resource at instance level")
    public void iValidateThePatientResourceAtInstanceLevel() {
        ctx.setLastResponse(given()
                .contentType("application/fhir+json")
                .body(ctx.getRequestBody())
                .when()
                .post("/Patient/" + ctx.getLastCreatedPatientId() + "/$validate"));
    }

    @When("I request $everything for the Patient")
    public void iRequestEverythingForThePatient() {
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + ctx.getLastCreatedPatientId() + "/$everything"));
    }

    @When("I request $everything for the Patient with _count {int}")
    public void iRequestEverythingWithCount(int count) {
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .queryParam("_count", count)
                .when()
                .get("/Patient/" + ctx.getLastCreatedPatientId() + "/$everything"));
    }

    @When("I request type-level $everything for Patient")
    public void iRequestTypeLevelEverythingForPatient() {
        ctx.setLastResponse(given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/$everything"));
    }

    @When("I apply the JSON Patch to the Patient")
    public void iApplyTheJsonPatchToThePatient() {
        ctx.setLastResponse(given()
                .contentType("application/json-patch+json")
                .body(ctx.getRequestBody())
                .when()
                .patch("/Patient/" + ctx.getLastCreatedPatientId()));
    }

    @When("I merge the source patient into the target patient")
    public void iMergeTheSourcePatientIntoTheTargetPatient() {
        String mergeParams = String.format("""
                {
                    "resourceType": "Parameters",
                    "parameter": [
                        {
                            "name": "source-patient",
                            "valueReference": {"reference": "Patient/%s"}
                        },
                        {
                            "name": "target-patient",
                            "valueReference": {"reference": "Patient/%s"}
                        }
                    ]
                }
                """, ctx.getLastCreatedPatientId(), ctx.getLastCreatedResourceId());

        ctx.setLastResponse(given()
                .contentType("application/fhir+json")
                .body(mergeParams)
                .when()
                .post("/Patient/$merge"));
    }

    @When("I call $merge without required parameters")
    public void iCallMergeWithoutRequiredParameters() {
        String emptyParams = """
                {
                    "resourceType": "Parameters",
                    "parameter": []
                }
                """;

        ctx.setLastResponse(given()
                .contentType("application/fhir+json")
                .body(emptyParams)
                .when()
                .post("/Patient/$merge"));
    }

    // ========== Then Steps ==========

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int statusCode) {
        ctx.getLastResponse().then().statusCode(statusCode);
    }

    @Then("the response should be an OperationOutcome")
    public void theResponseShouldBeAnOperationOutcome() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("OperationOutcome"));
    }

    @Then("the OperationOutcome should indicate success")
    public void theOperationOutcomeShouldIndicateSuccess() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("OperationOutcome"))
                .body("issue[0].severity", is(oneOf("information", "warning")));
    }

    @Then("the OperationOutcome should indicate an error")
    public void theOperationOutcomeShouldIndicateAnError() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("OperationOutcome"))
                .body("issue[0].severity", equalTo("error"));
    }

    @Then("the response should be a Bundle")
    public void theResponseShouldBeABundle() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("Bundle"));
    }

    @Then("the Bundle should be of type searchset")
    public void theBundleShouldBeOfTypeSearchset() {
        ctx.getLastResponse().then()
                .body("type", equalTo("searchset"));
    }

    @Then("the Bundle should contain the Patient resource")
    public void theBundleShouldContainThePatientResource() {
        ctx.getLastResponse().then()
                .body("entry.size()", greaterThanOrEqualTo(1))
                .body("entry.find { it.resource.resourceType == 'Patient' }", notNullValue());
    }

    @Then("the response should be a Patient resource")
    public void theResponseShouldBeAPatientResource() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("Patient"));
    }

    @Then("the Patient family name should be {string}")
    public void thePatientFamilyNameShouldBe(String familyName) {
        ctx.getLastResponse().then()
                .body("name[0].family", equalTo(familyName));
    }

    @Then("the response should have an ETag header")
    public void theResponseShouldHaveAnETagHeader() {
        ctx.getLastResponse().then()
                .header("ETag", notNullValue());
    }

    @Then("the response should have a Last-Modified header")
    public void theResponseShouldHaveALastModifiedHeader() {
        ctx.getLastResponse().then()
                .header("Last-Modified", notNullValue());
    }

    @Then("the source patient should be inactive")
    public void theSourcePatientShouldBeInactive() {
        Response readResponse = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + ctx.getLastCreatedPatientId());

        readResponse.then()
                .statusCode(200)
                .body("active", equalTo(false));
    }

    @Then("the source patient should have a replaced-by link")
    public void theSourcePatientShouldHaveAReplacedByLink() {
        Response readResponse = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + ctx.getLastCreatedPatientId());

        readResponse.then()
                .statusCode(200)
                .body("link.size()", greaterThanOrEqualTo(1))
                .body("link[0].type", equalTo("replaced-by"))
                .body("link[0].other.reference", containsString(ctx.getLastCreatedResourceId()));
    }
}
