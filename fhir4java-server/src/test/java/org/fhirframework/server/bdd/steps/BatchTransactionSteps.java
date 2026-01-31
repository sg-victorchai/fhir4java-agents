package org.fhirframework.server.bdd.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for batch/transaction bundle BDD tests.
 */
public class BatchTransactionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private SharedTestContext ctx;

    private String bundleJson;

    @Before
    public void setUp() {
        RestAssured.port = port;
        bundleJson = null;
    }

    // ========== Given Steps ==========

    @Given("I have a batch bundle with a Patient POST and a Patient GET")
    public void iHaveABatchBundleWithPostAndGet() {
        // First create a patient to GET later
        var createResponse = given()
                .basePath("/fhir")
                .contentType("application/fhir+json")
                .body("""
                    {
                        "resourceType": "Patient",
                        "active": true,
                        "name": [{"family": "BatchGet", "given": ["Test"]}]
                    }
                    """)
                .when()
                .post("/Patient");
        createResponse.then().statusCode(201);
        String existingId = ctx.extractResourceId(createResponse.body().asString());

        bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "batch",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "active": true,
                                "name": [{"family": "BatchCreate", "given": ["Test"]}]
                            },
                            "request": {
                                "method": "POST",
                                "url": "Patient"
                            }
                        },
                        {
                            "request": {
                                "method": "GET",
                                "url": "Patient/%s"
                            }
                        }
                    ]
                }
                """.formatted(existingId);
    }

    @Given("I have a transaction bundle with 2 Patient creates")
    public void iHaveATransactionBundleWith2PatientCreates() {
        bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "transaction",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "active": true,
                                "name": [{"family": "Transaction1", "given": ["Test"]}]
                            },
                            "request": {
                                "method": "POST",
                                "url": "Patient"
                            }
                        },
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "active": true,
                                "name": [{"family": "Transaction2", "given": ["Test"]}]
                            },
                            "request": {
                                "method": "POST",
                                "url": "Patient"
                            }
                        }
                    ]
                }
                """;
    }

    @Given("I have a batch bundle with a valid POST and an invalid GET")
    public void iHaveABatchBundleWithValidPostAndInvalidGet() {
        bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "batch",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "active": true,
                                "name": [{"family": "BatchValid", "given": ["Test"]}]
                            },
                            "request": {
                                "method": "POST",
                                "url": "Patient"
                            }
                        },
                        {
                            "request": {
                                "method": "GET",
                                "url": "Patient/nonexistent-id-12345"
                            }
                        }
                    ]
                }
                """;
    }

    @Given("I have a searchset bundle")
    public void iHaveASearchsetBundle() {
        bundleJson = """
                {
                    "resourceType": "Bundle",
                    "type": "searchset",
                    "entry": []
                }
                """;
    }

    // ========== When Steps ==========

    @When("I submit the bundle")
    public void iSubmitTheBundle() {
        ctx.setLastResponse(given()
                .basePath("/fhir")
                .contentType("application/fhir+json")
                .body(bundleJson)
                .when()
                .post(""));
    }

    // ========== Then Steps ==========

    @Then("the Bundle should be of type {string}")
    public void theBundleShouldBeOfType(String bundleType) {
        ctx.getLastResponse().then()
                .body("type", equalTo(bundleType));
    }

    @Then("the Bundle should have {int} entries")
    public void theBundleShouldHaveNEntries(int count) {
        ctx.getLastResponse().then()
                .body("entry.size()", equalTo(count));
    }

    @Then("all entries should have status {string}")
    public void allEntriesShouldHaveStatus(String expectedStatusCode) {
        String body = ctx.getLastResponse().body().asString();
        List<String> statuses = JsonPath.from(body).getList("entry.response.status");
        assertNotNull(statuses, "Entry statuses should not be null");
        assertFalse(statuses.isEmpty(), "Entry statuses should not be empty");
        for (String status : statuses) {
            assertTrue(status.startsWith(expectedStatusCode),
                    "Expected status starting with '" + expectedStatusCode + "' but got '" + status + "'");
        }
    }

    @Then("the Bundle should have entries with mixed statuses")
    public void theBundleShouldHaveEntriesWithMixedStatuses() {
        String body = ctx.getLastResponse().body().asString();
        List<String> statuses = JsonPath.from(body).getList("entry.response.status");
        assertNotNull(statuses, "Entry statuses should not be null");
        assertTrue(statuses.size() >= 2, "Should have at least 2 entries");
        // At least one should be success (201) and at least one should be an error (4xx)
        boolean hasSuccess = statuses.stream().anyMatch(s -> s.startsWith("201") || s.startsWith("200"));
        boolean hasError = statuses.stream().anyMatch(s -> s.startsWith("4") || s.startsWith("5"));
        assertTrue(hasSuccess, "Should have at least one successful entry");
        assertTrue(hasError, "Should have at least one error entry");
    }
}
