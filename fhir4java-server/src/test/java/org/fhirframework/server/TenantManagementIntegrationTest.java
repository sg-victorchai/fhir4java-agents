package org.fhirframework.server;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.fhirframework.persistence.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@DisplayName("Tenant Management API Integration Test")
class TenantManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private FhirTenantRepository tenantRepository;

    @Autowired
    private TenantService tenantService;

    private static final String ADMIN_BASE = "/api/admin/tenants";

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        tenantService.clearCache();
    }

    @Nested
    @DisplayName("POST /api/admin/tenants - Create")
    class CreateTenant {

        @Test
        @DisplayName("should create a new tenant")
        void shouldCreateNewTenant() {
            UUID extId = UUID.randomUUID();
            String uniqueIntId = "create-test-" + System.nanoTime();

            String body = """
                    {
                        "externalId": "%s",
                        "internalId": "%s",
                        "tenantCode": "TEST-C",
                        "tenantName": "Test Create Tenant",
                        "description": "Created via API",
                        "enabled": true
                    }
                    """.formatted(extId, uniqueIntId);

            Response response = given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ADMIN_BASE);

            assertEquals(201, response.statusCode());
            response.then()
                    .body("externalId", equalTo(extId.toString()))
                    .body("internalId", equalTo(uniqueIntId))
                    .body("tenantCode", equalTo("TEST-C"))
                    .body("tenantName", equalTo("Test Create Tenant"))
                    .body("enabled", equalTo(true))
                    .body("id", notNullValue());
        }

        @Test
        @DisplayName("should reject duplicate external ID")
        void shouldRejectDuplicateExternalId() {
            UUID extId = UUID.randomUUID();
            String intId1 = "dup-ext-1-" + System.nanoTime();
            String intId2 = "dup-ext-2-" + System.nanoTime();

            // Create first tenant
            String body1 = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "DUP1"}
                    """.formatted(extId, intId1);

            given().contentType(ContentType.JSON).body(body1).when().post(ADMIN_BASE)
                    .then().statusCode(201);

            // Try duplicate external ID
            String body2 = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "DUP2"}
                    """.formatted(extId, intId2);

            Response response = given()
                    .contentType(ContentType.JSON)
                    .body(body2)
                    .when()
                    .post(ADMIN_BASE);

            assertEquals(400, response.statusCode());
        }

        @Test
        @DisplayName("should reject missing required fields")
        void shouldRejectMissingFields() {
            String body = """
                    {"tenantCode": "MISSING", "tenantName": "Missing Required"}
                    """;

            Response response = given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ADMIN_BASE);

            assertEquals(400, response.statusCode());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/tenants - List")
    class ListTenants {

        @Test
        @DisplayName("should list all tenants")
        void shouldListAllTenants() {
            Response response = given()
                    .when()
                    .get(ADMIN_BASE);

            assertEquals(200, response.statusCode());
            // At minimum, default tenant should exist
            response.then().body("size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("should filter by enabled only")
        void shouldFilterByEnabledOnly() {
            Response response = given()
                    .queryParam("enabledOnly", true)
                    .when()
                    .get(ADMIN_BASE);

            assertEquals(200, response.statusCode());
            // All returned tenants should be enabled
            response.then().body("enabled", everyItem(equalTo(true)));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/tenants/{id} - Get by ID")
    class GetTenantById {

        @Test
        @DisplayName("should get tenant by database ID")
        void shouldGetById() {
            UUID extId = UUID.randomUUID();
            String intId = "get-by-id-" + System.nanoTime();

            // Create tenant first
            String body = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "GETID"}
                    """.formatted(extId, intId);

            Long id = given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post(ADMIN_BASE)
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath().getLong("id");

            // Get by ID
            Response response = given()
                    .when()
                    .get(ADMIN_BASE + "/" + id);

            assertEquals(200, response.statusCode());
            response.then()
                    .body("internalId", equalTo(intId))
                    .body("externalId", equalTo(extId.toString()));
        }

        @Test
        @DisplayName("should return 400 for non-existent ID")
        void shouldReturn400ForNotFound() {
            Response response = given()
                    .when()
                    .get(ADMIN_BASE + "/999999");

            assertEquals(400, response.statusCode());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/tenants/by-external-id/{uuid} - Get by external ID")
    class GetByExternalId {

        @Test
        @DisplayName("should get tenant by external UUID")
        void shouldGetByExternalId() {
            UUID extId = UUID.randomUUID();
            String intId = "by-ext-" + System.nanoTime();

            String body = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "BYEXT"}
                    """.formatted(extId, intId);

            given().contentType(ContentType.JSON).body(body).when().post(ADMIN_BASE)
                    .then().statusCode(201);

            Response response = given()
                    .when()
                    .get(ADMIN_BASE + "/by-external-id/" + extId);

            assertEquals(200, response.statusCode());
            response.then().body("internalId", equalTo(intId));
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/tenants/{id} - Update")
    class UpdateTenant {

        @Test
        @DisplayName("should update tenant fields")
        void shouldUpdateFields() {
            UUID extId = UUID.randomUUID();
            String intId = "update-test-" + System.nanoTime();

            String createBody = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "UPD", "tenantName": "Before"}
                    """.formatted(extId, intId);

            Long id = given()
                    .contentType(ContentType.JSON)
                    .body(createBody)
                    .when()
                    .post(ADMIN_BASE)
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath().getLong("id");

            String updateBody = """
                    {"tenantName": "After Update", "description": "Updated description"}
                    """;

            Response response = given()
                    .contentType(ContentType.JSON)
                    .body(updateBody)
                    .when()
                    .put(ADMIN_BASE + "/" + id);

            assertEquals(200, response.statusCode());
            response.then()
                    .body("tenantName", equalTo("After Update"))
                    .body("description", equalTo("Updated description"));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/tenants/{id}/enable and /disable")
    class EnableDisable {

        @Test
        @DisplayName("should disable then re-enable a tenant")
        void shouldDisableAndEnable() {
            UUID extId = UUID.randomUUID();
            String intId = "enable-test-" + System.nanoTime();

            String createBody = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "END", "enabled": true}
                    """.formatted(extId, intId);

            Long id = given()
                    .contentType(ContentType.JSON)
                    .body(createBody)
                    .when()
                    .post(ADMIN_BASE)
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath().getLong("id");

            // Disable
            Response disableResponse = given()
                    .when()
                    .post(ADMIN_BASE + "/" + id + "/disable");

            assertEquals(200, disableResponse.statusCode());
            disableResponse.then().body("enabled", equalTo(false));

            // Re-enable
            Response enableResponse = given()
                    .when()
                    .post(ADMIN_BASE + "/" + id + "/enable");

            assertEquals(200, enableResponse.statusCode());
            enableResponse.then().body("enabled", equalTo(true));
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/tenants/{id}")
    class DeleteTenant {

        @Test
        @DisplayName("should delete a tenant")
        void shouldDeleteTenant() {
            UUID extId = UUID.randomUUID();
            String intId = "delete-test-" + System.nanoTime();

            String createBody = """
                    {"externalId": "%s", "internalId": "%s", "tenantCode": "DEL"}
                    """.formatted(extId, intId);

            Long id = given()
                    .contentType(ContentType.JSON)
                    .body(createBody)
                    .when()
                    .post(ADMIN_BASE)
                    .then()
                    .statusCode(201)
                    .extract()
                    .jsonPath().getLong("id");

            // Delete
            Response deleteResponse = given()
                    .when()
                    .delete(ADMIN_BASE + "/" + id);

            assertEquals(204, deleteResponse.statusCode());

            // Verify tenant is gone
            Response getResponse = given()
                    .when()
                    .get(ADMIN_BASE + "/" + id);

            assertEquals(400, getResponse.statusCode());
        }
    }

    @Nested
    @DisplayName("Admin API does not require X-Tenant-ID header")
    class NoTenantHeaderRequired {

        @Test
        @DisplayName("should access admin API without tenant header even when tenancy is enabled")
        void shouldAccessWithoutTenantHeader() {
            // This test verifies the TenantFilter skips /api/admin paths
            Response response = given()
                    .when()
                    .get(ADMIN_BASE);

            // Should not get 400 (missing tenant header)
            assertEquals(200, response.statusCode());
        }
    }
}
