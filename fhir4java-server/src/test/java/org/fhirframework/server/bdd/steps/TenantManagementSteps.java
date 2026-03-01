package org.fhirframework.server.bdd.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.fhirframework.core.tenant.TenantProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for tenant management BDD scenarios.
 */
public class TenantManagementSteps {

    private static final String ADMIN_BASE = "/api/admin/tenants";

    @Autowired
    private SharedTestContext ctx;

    @Autowired
    private TenantProperties tenantProperties;

    private Long lastCreatedTenantId;

    @After("@tenant-management")
    public void resetState() {
        tenantProperties.setEnabled(false);
        lastCreatedTenantId = null;
    }

    @When("I create a tenant with external ID and internal ID {string}")
    public void iCreateATenantWithExternalIdAndInternalId(String internalId) {
        UUID extId = UUID.randomUUID();
        String body = """
                {
                    "externalId": "%s",
                    "internalId": "%s",
                    "tenantCode": "%s",
                    "tenantName": "BDD Tenant %s",
                    "enabled": true
                }
                """.formatted(extId, internalId, internalId.toUpperCase(), internalId);

        Response response = given()
                .basePath("")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(ADMIN_BASE);

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            lastCreatedTenantId = response.jsonPath().getLong("id");
        }
    }

    @When("I create a tenant with specific external ID {string} and internal ID {string}")
    public void iCreateATenantWithSpecificExternalIdAndInternalId(String externalId, String internalId) {
        String body = """
                {
                    "externalId": "%s",
                    "internalId": "%s",
                    "tenantCode": "%s",
                    "tenantName": "BDD Tenant %s",
                    "enabled": true
                }
                """.formatted(externalId, internalId, internalId.toUpperCase(), internalId);

        Response response = given()
                .basePath("")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(ADMIN_BASE);

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            lastCreatedTenantId = response.jsonPath().getLong("id");
        }
    }

    @When("I list all tenants via admin API")
    public void iListAllTenantsViaAdminApi() {
        Response response = given()
                .basePath("")
                .when()
                .get(ADMIN_BASE);

        ctx.setLastResponse(response);
    }

    @When("I get the created tenant by its database ID")
    public void iGetTheCreatedTenantByItsDatabaseId() {
        assertNotNull(lastCreatedTenantId, "No tenant was created yet");

        Response response = given()
                .basePath("")
                .when()
                .get(ADMIN_BASE + "/" + lastCreatedTenantId);

        ctx.setLastResponse(response);
    }

    @When("I update the created tenant with name {string}")
    public void iUpdateTheCreatedTenantWithName(String name) {
        assertNotNull(lastCreatedTenantId, "No tenant was created yet");

        String body = """
                {"tenantName": "%s"}
                """.formatted(name);

        Response response = given()
                .basePath("")
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put(ADMIN_BASE + "/" + lastCreatedTenantId);

        ctx.setLastResponse(response);
    }

    @When("I disable the created tenant")
    public void iDisableTheCreatedTenant() {
        assertNotNull(lastCreatedTenantId, "No tenant was created yet");

        Response response = given()
                .basePath("")
                .when()
                .post(ADMIN_BASE + "/" + lastCreatedTenantId + "/disable");

        ctx.setLastResponse(response);
    }

    @When("I enable the created tenant")
    public void iEnableTheCreatedTenant() {
        assertNotNull(lastCreatedTenantId, "No tenant was created yet");

        Response response = given()
                .basePath("")
                .when()
                .post(ADMIN_BASE + "/" + lastCreatedTenantId + "/enable");

        ctx.setLastResponse(response);
    }

    @When("I delete the created tenant")
    public void iDeleteTheCreatedTenant() {
        assertNotNull(lastCreatedTenantId, "No tenant was created yet");

        Response response = given()
                .basePath("")
                .when()
                .delete(ADMIN_BASE + "/" + lastCreatedTenantId);

        ctx.setLastResponse(response);
    }

    @Then("the response should contain tenant with internal ID {string}")
    public void theResponseShouldContainTenantWithInternalId(String internalId) {
        ctx.getLastResponse().then()
                .body("internalId", equalTo(internalId));
    }

    @Then("the response should contain at least {int} tenant")
    public void theResponseShouldContainAtLeastTenants(int count) {
        ctx.getLastResponse().then()
                .body("size()", greaterThanOrEqualTo(count));
    }

    @Then("the response should contain tenant name {string}")
    public void theResponseShouldContainTenantName(String name) {
        ctx.getLastResponse().then()
                .body("tenantName", equalTo(name));
    }

    @And("the tenant should be disabled")
    public void theTenantShouldBeDisabled() {
        ctx.getLastResponse().then()
                .body("enabled", equalTo(false));
    }

    @And("the tenant should be enabled")
    public void theTenantShouldBeEnabled() {
        ctx.getLastResponse().then()
                .body("enabled", equalTo(true));
    }
}
