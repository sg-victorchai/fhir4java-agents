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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step definitions for Patient CREATE plugin BDD tests.
 */
public class PluginSteps {
	
	 private static final Logger log = LoggerFactory.getLogger(PluginSteps.class);


    @LocalServerPort
    private int port;

    @Autowired
    private SharedTestContext ctx;

    private String patientJson;

    @Before
    public void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/fhir";
        patientJson = null;
    }

    // ========== Given Steps ==========

    @Given("I have a Patient resource with family name {string} and given name {string}")
    public void iHaveAPatientResourceWithNames(String family, String givenName) {
        patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "%s", "given": ["%s"]}]
                }
                """.formatted(family, givenName);
    }

    @Given("I have a Patient resource without a family name")
    public void iHaveAPatientResourceWithoutFamilyName() {
        patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"given": ["NoFamily"]}]
                }
                """;
    }

    @Given("the Patient has an identifier with system {string} and value {string}")
    public void thePatientHasAnIdentifier(String system, String value) {
        // Inject an identifier into the patient JSON before the closing }
        String identifierJson = """
                , "identifier": [{"system": "%s", "value": "%s"}]
                """.formatted(system, value);
        patientJson = patientJson.substring(0, patientJson.lastIndexOf('}')) + identifierJson + "}";
    }

    @Given("the Patient has a phone number {string}")
    public void thePatientHasAPhoneNumber(String phoneNumber) {
        // Inject telecom into the patient JSON before the closing }
        String telecomJson = """
                , "telecom": [{"system": "phone", "value": "%s", "use": "home"}]
                """.formatted(phoneNumber);
        patientJson = patientJson.substring(0, patientJson.lastIndexOf('}')) + telecomJson + "}";
    }

    // ========== When Steps ==========

    @When("I create the Patient resource")
    public void iCreateThePatientResource() {
    	log.debug("creating patient: " + patientJson);
        Response response = given()
                .contentType("application/fhir+json")
                .body(patientJson)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedPatientId(ctx.extractResourceId(response.body().asString()));
        }
    }

    // ========== Then Steps ==========

    @Then("the Patient should have an identifier with system {string}")
    public void thePatientShouldHaveIdentifierWithSystem(String system) {
        ctx.getLastResponse().then()
                .body("identifier.find { it.system == '" + system + "' }", notNullValue());
    }

    @Then("the MRN identifier value should match pattern {string}")
    public void theMrnIdentifierValueShouldMatchPattern(String pattern) {
        String body = ctx.getLastResponse().body().asString();
        // Extract the MRN value from identifiers
        String mrnValue = io.restassured.path.json.JsonPath.from(body)
                .getString("identifier.find { it.system == 'urn:fhir4java:patient:mrn' }.value");
        org.junit.jupiter.api.Assertions.assertNotNull(mrnValue, "MRN identifier value should not be null");
        org.junit.jupiter.api.Assertions.assertTrue(mrnValue.matches(pattern),
                "MRN value '" + mrnValue + "' should match pattern '" + pattern + "'");
    }

    @Then("the Patient should have an extension with URL {string}")
    public void thePatientShouldHaveExtensionWithUrl(String url) {
        ctx.getLastResponse().then()
                .body("extension.find { it.url == '" + url + "' }", notNullValue());
    }

    @Then("the OperationOutcome should contain message {string}")
    public void theOperationOutcomeShouldContainMessage(String message) {
        ctx.getLastResponse().then()
                .body("issue[0].diagnostics", containsStringIgnoringCase(message));
    }

    @Then("the Patient should have exactly {int} identifier with system {string}")
    public void thePatientShouldHaveExactlyNIdentifiersWithSystem(int count, String system) {
        String body = ctx.getLastResponse().body().asString();
        java.util.List<Object> matching = io.restassured.path.json.JsonPath.from(body)
                .getList("identifier.findAll { it.system == '" + system + "' }");
        org.junit.jupiter.api.Assertions.assertEquals(count, matching.size(),
                "Expected " + count + " identifier(s) with system '" + system + "' but found " + matching.size());
    }

    @Then("the Patient phone number should be {string}")
    public void thePatientPhoneNumberShouldBe(String expected) {
        ctx.getLastResponse().then()
                .body("telecom.find { it.system == 'phone' }.value", equalTo(expected));
    }
}
