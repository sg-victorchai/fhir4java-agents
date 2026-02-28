package org.fhirframework.server.bdd.steps;

import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.fhirframework.persistence.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for multi-tenancy BDD scenarios.
 */
public class MultiTenancySteps {

    @Autowired
    private SharedTestContext ctx;

    @Autowired
    private FhirTenantRepository tenantRepository;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private TenantService tenantService;

    private String lastCreatedPatientId;
    private final Map<String, String> storedResourceIds = new HashMap<>();

    private static final String PATIENT_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "Smith", "given": ["John"]}],
                "gender": "male",
                "birthDate": "1990-01-01"
            }
            """;

    private static final String PATIENT_UPDATE_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "Smith", "given": ["John", "Robert"]}],
                "gender": "male",
                "birthDate": "1990-01-01"
            }
            """;

    @After("@tenancy or @tenancy-disabled")
    public void resetTenancyState() {
        // Reset to default disabled state after each tenancy scenario
        tenantProperties.setEnabled(false);
        tenantService.clearCache();
        lastCreatedPatientId = null;
        storedResourceIds.clear();
    }

    // ========== Given Steps ==========

    @Given("multi-tenancy is enabled")
    public void multiTenancyIsEnabled() {
        tenantProperties.setEnabled(true);
    }

    @Given("multi-tenancy is disabled")
    public void multiTenancyIsDisabled() {
        tenantProperties.setEnabled(false);
    }

    @Given("tenant {string} exists with external ID {string}")
    public void tenantExistsWithExternalId(String tenantCode, String externalId) {
        UUID uuid = UUID.fromString(externalId);
        if (!tenantRepository.existsByExternalId(uuid)) {
            FhirTenantEntity tenant = FhirTenantEntity.builder()
                    .externalId(uuid)
                    .internalId(tenantCode.toLowerCase())
                    .tenantCode(tenantCode)
                    .tenantName("Test Tenant " + tenantCode)
                    .enabled(true)
                    .build();
            tenantRepository.save(tenant);
        }
        tenantService.clearCache();
    }

    @Given("tenant {string} exists but is disabled with external ID {string}")
    public void tenantExistsButIsDisabled(String tenantCode, String externalId) {
        UUID uuid = UUID.fromString(externalId);
        if (!tenantRepository.existsByExternalId(uuid)) {
            FhirTenantEntity tenant = FhirTenantEntity.builder()
                    .externalId(uuid)
                    .internalId(tenantCode.toLowerCase())
                    .tenantCode(tenantCode)
                    .tenantName("Test Tenant " + tenantCode)
                    .enabled(false)
                    .build();
            tenantRepository.save(tenant);
        }
        tenantService.clearCache();
    }

    // ========== When Steps ==========

    @When("I create a Patient with tenant header {string}")
    public void iCreateAPatientWithTenantHeader(String tenantId) {
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .body(PATIENT_JSON)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            lastCreatedPatientId = ctx.extractResourceId(response.body().asString());
        }
    }

    @When("I create a Patient without tenant header")
    public void iCreateAPatientWithoutTenantHeader() {
        Response response = given()
                .contentType("application/fhir+json")
                .body(PATIENT_JSON)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            lastCreatedPatientId = ctx.extractResourceId(response.body().asString());
        }
    }

    @When("I read the created Patient with tenant header {string}")
    public void iReadTheCreatedPatientWithTenantHeader(String tenantId) {
        assertNotNull(lastCreatedPatientId, "No Patient was created yet");
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/Patient/" + lastCreatedPatientId);

        ctx.setLastResponse(response);
    }

    @When("I read Patient {string} with tenant header {string}")
    public void iReadPatientWithTenantHeader(String storedIdKey, String tenantId) {
        String resourceId = storedResourceIds.get(storedIdKey);
        assertNotNull(resourceId, "No stored resource ID found for key: " + storedIdKey);
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/Patient/" + resourceId);

        ctx.setLastResponse(response);
    }

    @When("I create a Patient with family name {string} and tenant header {string}")
    public void iCreateAPatientWithFamilyNameAndTenantHeader(String familyName, String tenantId) {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "%s", "given": ["John"]}],
                    "gender": "male"
                }
                """.formatted(familyName);
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .body(patientJson)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            lastCreatedPatientId = ctx.extractResourceId(response.body().asString());
        }
    }

    @When("I search for Patients with family {string} and tenant header {string}")
    public void iSearchForPatientsWithFamilyAndTenantHeader(String familyName, String tenantId) {
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .queryParam("family", familyName)
                .when()
                .get("/Patient");

        ctx.setLastResponse(response);
    }

    @When("I search for Patients with tenant header {string}")
    public void iSearchForPatientsWithTenantHeader(String tenantId) {
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/Patient");

        ctx.setLastResponse(response);
    }

    @When("I update Patient {string} with tenant header {string}")
    public void iUpdatePatientWithTenantHeader(String storedIdKey, String tenantId) {
        String resourceId = storedResourceIds.get(storedIdKey);
        assertNotNull(resourceId, "No stored resource ID found for key: " + storedIdKey);
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .body(PATIENT_UPDATE_JSON)
                .when()
                .put("/Patient/" + resourceId);

        ctx.setLastResponse(response);
    }

    @When("I delete Patient {string} with tenant header {string}")
    public void iDeletePatientWithTenantHeader(String storedIdKey, String tenantId) {
        String resourceId = storedResourceIds.get(storedIdKey);
        assertNotNull(resourceId, "No stored resource ID found for key: " + storedIdKey);
        Response response = given()
                .header("X-Tenant-ID", tenantId)
                .when()
                .delete("/Patient/" + resourceId);

        ctx.setLastResponse(response);
    }

    @When("I send a FHIR request without X-Tenant-ID header")
    public void iSendAFhirRequestWithoutTenantHeader() {
        Response response = given()
                .contentType("application/fhir+json")
                .when()
                .get("/Patient");

        ctx.setLastResponse(response);
    }

    @When("I update the created Patient with tenant header {string}")
    public void iUpdateTheCreatedPatientWithTenantHeader(String tenantId) {
        assertNotNull(lastCreatedPatientId, "No Patient was created yet");
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .body(PATIENT_UPDATE_JSON)
                .when()
                .put("/Patient/" + lastCreatedPatientId);

        ctx.setLastResponse(response);
    }

    @When("I get history of the created Patient with tenant header {string}")
    public void iGetHistoryOfTheCreatedPatientWithTenantHeader(String tenantId) {
        assertNotNull(lastCreatedPatientId, "No Patient was created yet");
        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/Patient/" + lastCreatedPatientId + "/_history");

        ctx.setLastResponse(response);
    }

    // ========== Then Steps ==========

    @And("I store the created Patient ID as {string}")
    public void iStoreTheCreatedPatientIdAs(String key) {
        assertNotNull(lastCreatedPatientId, "No Patient was created yet");
        storedResourceIds.put(key, lastCreatedPatientId);
    }

    @Then("the response should contain a Patient resource")
    public void theResponseShouldContainAPatientResource() {
        ctx.getLastResponse().then()
                .body("resourceType", equalTo("Patient"));
    }

    @Then("the search bundle should have {int} entries")
    public void theSearchBundleShouldHaveEntries(int expectedCount) {
        if (expectedCount == 0) {
            ctx.getLastResponse().then()
                    .body("total", equalTo(0));
        } else {
            ctx.getLastResponse().then()
                    .body("total", equalTo(expectedCount))
                    .body("entry.size()", equalTo(expectedCount));
        }
    }

    @Then("the history bundle should have {int} entries")
    public void theHistoryBundleShouldHaveEntries(int expectedCount) {
        ctx.getLastResponse().then()
                .body("total", equalTo(expectedCount))
                .body("entry.size()", equalTo(expectedCount));
    }
}
