package org.fhirframework.server;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.fhirframework.persistence.service.TenantService;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Integration test for multi-tenancy — tests the complete request flow:
 * HTTP → TenantFilter → Controller → Service → Repository.
 */
@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@DisplayName("Multi-Tenancy Integration Test")
class TenantIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private FhirTenantRepository tenantRepository;

    @Autowired
    private TenantService tenantService;

    private static final UUID TENANT_A_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B_UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DISABLED_UUID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private static final String PATIENT_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "IntegrationTest", "given": ["Tenant"]}],
                "gender": "male"
            }
            """;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/fhir";
        tenantService.clearCache();
    }

    @AfterEach
    void tearDown() {
        tenantProperties.setEnabled(false);
        tenantService.clearCache();
    }

    private void enableTenancyAndSeedTenants() {
        tenantProperties.setEnabled(true);

        if (!tenantRepository.existsByExternalId(TENANT_A_UUID)) {
            tenantRepository.save(FhirTenantEntity.builder()
                    .externalId(TENANT_A_UUID)
                    .internalId("int-hosp-a")
                    .tenantCode("HOSP-A")
                    .tenantName("Hospital A")
                    .enabled(true)
                    .build());
        }
        if (!tenantRepository.existsByExternalId(TENANT_B_UUID)) {
            tenantRepository.save(FhirTenantEntity.builder()
                    .externalId(TENANT_B_UUID)
                    .internalId("int-hosp-b")
                    .tenantCode("HOSP-B")
                    .tenantName("Hospital B")
                    .enabled(true)
                    .build());
        }
        if (!tenantRepository.existsByExternalId(DISABLED_UUID)) {
            tenantRepository.save(FhirTenantEntity.builder()
                    .externalId(DISABLED_UUID)
                    .internalId("int-disabled")
                    .tenantCode("DISABLED")
                    .tenantName("Disabled Tenant")
                    .enabled(false)
                    .build());
        }
        tenantService.clearCache();
    }

    @Nested
    @DisplayName("Tenancy Disabled (default)")
    class TenancyDisabledTests {

        @Test
        @DisplayName("should allow requests without tenant header when tenancy is disabled")
        void shouldAllowRequestsWithoutHeader() {
            tenantProperties.setEnabled(false);

            Response response = given()
                    .contentType("application/fhir+json")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(201, response.statusCode());
        }
    }

    @Nested
    @DisplayName("Tenancy Enabled - Tenant Resolution")
    class TenantResolution {

        @BeforeEach
        void setUp() {
            enableTenancyAndSeedTenants();
        }

        @Test
        @DisplayName("should reject requests without tenant header (400)")
        void shouldRejectMissingHeader() {
            Response response = given()
                    .contentType("application/fhir+json")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(400, response.statusCode());
        }

        @Test
        @DisplayName("should reject unknown tenant UUID (400)")
        void shouldRejectUnknownTenant() {
            String unknownUuid = "cccccccc-cccc-cccc-cccc-cccccccccccc";
            Response response = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", unknownUuid)
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(400, response.statusCode());
        }

        @Test
        @DisplayName("should reject disabled tenant (403)")
        void shouldRejectDisabledTenant() {
            Response response = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", DISABLED_UUID.toString())
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(403, response.statusCode());
        }

        @Test
        @DisplayName("should reject invalid UUID format (400)")
        void shouldRejectInvalidUuid() {
            Response response = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", "not-a-uuid")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(400, response.statusCode());
        }
    }

    @Nested
    @DisplayName("Tenancy Enabled - Resource Isolation")
    class ResourceIsolation {

        @BeforeEach
        void setUp() {
            enableTenancyAndSeedTenants();
        }

        @Test
        @DisplayName("should create and read resource within same tenant")
        void shouldCreateAndReadWithSameTenant() {
            // Create
            Response createResponse = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(201, createResponse.statusCode());
            String resourceId = extractResourceId(createResponse.body().asString());

            // Read with same tenant
            Response readResponse = given()
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .when()
                    .get("/Patient/" + resourceId);

            assertEquals(200, readResponse.statusCode());
            readResponse.then().body("resourceType", equalTo("Patient"));
        }

        @Test
        @DisplayName("should not allow reading resource from different tenant (404)")
        void shouldNotReadFromDifferentTenant() {
            // Create with tenant A
            Response createResponse = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(201, createResponse.statusCode());
            String resourceId = extractResourceId(createResponse.body().asString());

            // Try to read with tenant B
            Response readResponse = given()
                    .header("X-Tenant-ID", TENANT_B_UUID.toString())
                    .when()
                    .get("/Patient/" + resourceId);

            assertEquals(404, readResponse.statusCode());
        }

        @Test
        @DisplayName("should isolate search results by tenant")
        void shouldIsolateSearchByTenant() {
            String uniqueFamily = "SearchIsolation" + System.nanoTime();
            String searchPatient = """
                    {
                        "resourceType": "Patient",
                        "active": true,
                        "name": [{"family": "%s", "given": ["Tenant"]}],
                        "gender": "male"
                    }
                    """.formatted(uniqueFamily);

            // Create patient under tenant A
            given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .body(searchPatient)
                    .when()
                    .post("/Patient")
                    .then()
                    .statusCode(201);

            // Search under tenant B with the unique family name — should not find it
            Response searchResponse = given()
                    .header("X-Tenant-ID", TENANT_B_UUID.toString())
                    .queryParam("family", uniqueFamily)
                    .when()
                    .get("/Patient");

            assertEquals(200, searchResponse.statusCode());
            searchResponse.then().body("total", equalTo(0));

            // Search under tenant A — should find it
            Response searchA = given()
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .queryParam("family", uniqueFamily)
                    .when()
                    .get("/Patient");

            assertEquals(200, searchA.statusCode());
            searchA.then().body("total", equalTo(1));
        }

        @Test
        @DisplayName("should isolate updates between tenants (PUT creates separate resource per tenant)")
        void shouldIsolateUpdatesBetweenTenants() {
            // Create with tenant A
            Response createResponse = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .body(PATIENT_JSON)
                    .when()
                    .post("/Patient");

            assertEquals(201, createResponse.statusCode());
            String resourceId = extractResourceId(createResponse.body().asString());

            // PUT same ID with tenant B creates a separate resource under B's scope (FHIR upsert semantics)
            String updatedPatient = """
                    {
                        "resourceType": "Patient",
                        "id": "%s",
                        "active": false,
                        "name": [{"family": "TenantB", "given": ["Patient"]}],
                        "gender": "female"
                    }
                    """.formatted(resourceId);

            Response updateResponse = given()
                    .contentType("application/fhir+json")
                    .header("X-Tenant-ID", TENANT_B_UUID.toString())
                    .body(updatedPatient)
                    .when()
                    .put("/Patient/" + resourceId);

            assertEquals(201, updateResponse.statusCode());

            // Verify tenant A's resource is unchanged (still "IntegrationTest")
            Response readA = given()
                    .header("X-Tenant-ID", TENANT_A_UUID.toString())
                    .when()
                    .get("/Patient/" + resourceId);

            assertEquals(200, readA.statusCode());
            assertTrue(readA.body().asString().contains("IntegrationTest"),
                    "Tenant A's resource should be unchanged");

            // Verify tenant B has their own version
            Response readB = given()
                    .header("X-Tenant-ID", TENANT_B_UUID.toString())
                    .when()
                    .get("/Patient/" + resourceId);

            assertEquals(200, readB.statusCode());
            assertTrue(readB.body().asString().contains("TenantB"),
                    "Tenant B should have their own version");
        }
    }

    @Nested
    @DisplayName("Filter Ordering")
    class FilterOrdering {

        @BeforeEach
        void setUp() {
            enableTenancyAndSeedTenants();
        }

        @Test
        @DisplayName("should skip tenant filter for actuator endpoints")
        void shouldSkipActuatorEndpoints() {
            RestAssured.basePath = "";
            Response response = given()
                    .when()
                    .get("/actuator/health");

            // Actuator should respond without tenant header
            assertTrue(response.statusCode() == 200 || response.statusCode() == 503,
                    "Actuator should respond without tenant header, got: " + response.statusCode());
            RestAssured.basePath = "/fhir";
        }
    }

    private String extractResourceId(String responseBody) {
        int idStart = responseBody.indexOf("\"id\"");
        if (idStart == -1) return null;
        int valueStart = responseBody.indexOf("\"", idStart + 4) + 1;
        int valueEnd = responseBody.indexOf("\"", valueStart);
        String fullId = responseBody.substring(valueStart, valueEnd);
        if (fullId.contains("/")) {
            return fullId.substring(fullId.lastIndexOf("/") + 1);
        }
        return fullId;
    }
}
