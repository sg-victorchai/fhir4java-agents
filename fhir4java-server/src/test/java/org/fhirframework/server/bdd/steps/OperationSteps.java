package org.fhirframework.server.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR extended operation BDD tests.
 */
public class OperationSteps {

    @LocalServerPort
    private int port;

    private Response response;
    private String createdPatientId;
    private String createdResourceId;
    private String requestBody;

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

        response = given()
                .contentType("application/fhir+json")
                .body(patientJson)
                .when()
                .post("/Patient");

        response.then().statusCode(201);
        createdPatientId = extractResourceId(response.body().asString());
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
        createdPatientId = extractResourceId(sourceResponse.body().asString());

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
        createdResourceId = extractResourceId(targetResponse.body().asString());
    }

    @Given("I have a valid Patient resource")
    public void iHaveAValidPatientResource() {
        requestBody = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Doe", "given": ["Jane"]}],
                    "gender": "female",
                    "birthDate": "1985-06-15"
                }
                """;
    }

    @Given("I have an invalid Patient resource")
    public void iHaveAnInvalidPatientResource() {
        requestBody = """
                {
                    "resourceType": "Patient",
                    "name": "not-valid-name-format"
                }
                """;
    }

    @Given("I have a JSON Patch document to update the patient name")
    public void iHaveAJsonPatchDocument() {
        requestBody = """
                [
                    {"op": "replace", "path": "/name/0/family", "value": "Johnson"}
                ]
                """;
    }

    @Given("I have an invalid JSON Patch document")
    public void iHaveAnInvalidJsonPatchDocument() {
        requestBody = """
                [
                    {"op": "replace", "path": "/nonexistent/deep/path/0/1/2/3", "value": "test"}
                ]
                """;
    }

    // ========== When Steps ==========

    @When("I validate the Patient resource at type level")
    public void iValidateThePatientResourceAtTypeLevel() {
        response = given()
                .contentType("application/fhir+json")
                .body(requestBody)
                .when()
                .post("/Patient/$validate");
    }

    @When("I validate the Patient resource at instance level")
    public void iValidateThePatientResourceAtInstanceLevel() {
        response = given()
                .contentType("application/fhir+json")
                .body(requestBody)
                .when()
                .post("/Patient/" + createdPatientId + "/$validate");
    }

    @When("I request $everything for the Patient")
    public void iRequestEverythingForThePatient() {
        response = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + createdPatientId + "/$everything");
    }

    @When("I request $everything for the Patient with _count {int}")
    public void iRequestEverythingWithCount(int count) {
        response = given()
                .accept("application/fhir+json")
                .queryParam("_count", count)
                .when()
                .get("/Patient/" + createdPatientId + "/$everything");
    }

    @When("I request type-level $everything for Patient")
    public void iRequestTypeLevelEverythingForPatient() {
        response = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/$everything");
    }

    @When("I apply the JSON Patch to the Patient")
    public void iApplyTheJsonPatchToThePatient() {
        response = given()
                .contentType("application/json-patch+json")
                .body(requestBody)
                .when()
                .patch("/Patient/" + createdPatientId);
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
                """, createdPatientId, createdResourceId);

        response = given()
                .contentType("application/fhir+json")
                .body(mergeParams)
                .when()
                .post("/Patient/$merge");
    }

    @When("I call $merge without required parameters")
    public void iCallMergeWithoutRequiredParameters() {
        String emptyParams = """
                {
                    "resourceType": "Parameters",
                    "parameter": []
                }
                """;

        response = given()
                .contentType("application/fhir+json")
                .body(emptyParams)
                .when()
                .post("/Patient/$merge");
    }

    // ========== Then Steps ==========

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int statusCode) {
        response.then().statusCode(statusCode);
    }

    @Then("the response should be an OperationOutcome")
    public void theResponseShouldBeAnOperationOutcome() {
        response.then()
                .body("resourceType", equalTo("OperationOutcome"));
    }

    @Then("the OperationOutcome should indicate success")
    public void theOperationOutcomeShouldIndicateSuccess() {
        response.then()
                .body("resourceType", equalTo("OperationOutcome"))
                .body("issue[0].severity", is(oneOf("information", "warning")));
    }

    @Then("the OperationOutcome should indicate an error")
    public void theOperationOutcomeShouldIndicateAnError() {
        response.then()
                .body("resourceType", equalTo("OperationOutcome"))
                .body("issue[0].severity", equalTo("error"));
    }

    @Then("the response should be a Bundle")
    public void theResponseShouldBeABundle() {
        response.then()
                .body("resourceType", equalTo("Bundle"));
    }

    @Then("the Bundle should be of type searchset")
    public void theBundleShouldBeOfTypeSearchset() {
        response.then()
                .body("type", equalTo("searchset"));
    }

    @Then("the Bundle should contain the Patient resource")
    public void theBundleShouldContainThePatientResource() {
        response.then()
                .body("entry.size()", greaterThanOrEqualTo(1))
                .body("entry.find { it.resource.resourceType == 'Patient' }", notNullValue());
    }

    @Then("the response should be a Patient resource")
    public void theResponseShouldBeAPatientResource() {
        response.then()
                .body("resourceType", equalTo("Patient"));
    }

    @Then("the Patient family name should be {string}")
    public void thePatientFamilyNameShouldBe(String familyName) {
        response.then()
                .body("name[0].family", equalTo(familyName));
    }

    @Then("the response should have an ETag header")
    public void theResponseShouldHaveAnETagHeader() {
        response.then()
                .header("ETag", notNullValue());
    }

    @Then("the response should have a Last-Modified header")
    public void theResponseShouldHaveALastModifiedHeader() {
        response.then()
                .header("Last-Modified", notNullValue());
    }

    @Then("the source patient should be inactive")
    public void theSourcePatientShouldBeInactive() {
        Response readResponse = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + createdPatientId);

        readResponse.then()
                .statusCode(200)
                .body("active", equalTo(false));
    }

    @Then("the source patient should have a replaced-by link")
    public void theSourcePatientShouldHaveAReplacedByLink() {
        Response readResponse = given()
                .accept("application/fhir+json")
                .when()
                .get("/Patient/" + createdPatientId);

        readResponse.then()
                .statusCode(200)
                .body("link.size()", greaterThanOrEqualTo(1))
                .body("link[0].type", equalTo("replaced-by"))
                .body("link[0].other.reference", containsString(createdResourceId));
    }

    // ========== Helper Methods ==========

    private String extractResourceId(String responseBody) {
        // Extract the ID from the resource JSON
        // The response body should contain "id": "some-uuid"
        int idStart = responseBody.indexOf("\"id\"");
        if (idStart == -1) return null;

        int valueStart = responseBody.indexOf("\"", idStart + 4) + 1;
        int valueEnd = responseBody.indexOf("\"", valueStart);
        String fullId = responseBody.substring(valueStart, valueEnd);

        // Handle "Patient/uuid" format
        if (fullId.contains("/")) {
            return fullId.substring(fullId.lastIndexOf("/") + 1);
        }
        return fullId;
    }
}
