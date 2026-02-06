package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for FHIR conformance resource BDD tests.
 * Tests StructureDefinition, SearchParameter, and OperationDefinition endpoints.
 */
public class ConformanceResourceSteps {

    @Autowired
    private SharedTestContext ctx;

    /**
     * Build a request with empty basePath to use absolute paths.
     */
    private RequestSpecification request() {
        return given().basePath("");
    }

    // ========== When Steps ==========

    @When("I read StructureDefinition {string} via {string}")
    public void iReadStructureDefinitionVia(String id, String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I search StructureDefinitions via {string}")
    public void iSearchStructureDefinitionsVia(String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I read SearchParameter {string} via {string}")
    public void iReadSearchParameterVia(String id, String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I search SearchParameters via {string}")
    public void iSearchSearchParametersVia(String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I read OperationDefinition {string} via {string}")
    public void iReadOperationDefinitionVia(String id, String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    @When("I search OperationDefinitions via {string}")
    public void iSearchOperationDefinitionsVia(String path) {
        ctx.setLastResponse(request()
                .accept("application/fhir+json")
                .when()
                .get(path));
    }

    // ========== Then Steps ==========

    @Then("the response should have resourceType {string}")
    public void theResponseShouldHaveResourceType(String resourceType) {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo(resourceType));
    }

    @Then("the response should have id {string}")
    public void theResponseShouldHaveId(String id) {
        ctx.getLastResponse().then()
                .body("id", equalTo(id));
    }

    @Then("the Bundle should have total greater than {int}")
    public void theBundleShouldHaveTotalGreaterThan(int total) {
        ctx.getLastResponse().then()
                .body("total", greaterThan(total));
    }

    @Then("the Bundle should contain entry with resource id {string}")
    public void theBundleShouldContainEntryWithResourceId(String id) {
        ctx.getLastResponse().then()
                .body("entry.find { it.resource.id == '" + id + "' }", notNullValue());
    }

    @Then("the Bundle should have at most {int} entries")
    public void theBundleShouldHaveAtMostEntries(int maxEntries) {
        ctx.getLastResponse().then()
                .body("entry.size()", lessThanOrEqualTo(maxEntries));
    }

    @Then("the Bundle should have pagination links")
    public void theBundleShouldHavePaginationLinks() {
        ctx.getLastResponse().then()
                .body("link", notNullValue())
                .body("link.find { it.relation == 'self' }", notNullValue())
                .body("link.find { it.relation == 'first' }", notNullValue());
    }

    @Then("the CapabilityStatement should include resource type {string}")
    public void theCapabilityStatementShouldIncludeResourceType(String resourceType) {
        ctx.getLastResponse().then()
                .body("rest[0].resource.find { it.type == '" + resourceType + "' }", notNullValue());
    }
}
