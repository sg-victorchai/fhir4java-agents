package org.fhirframework.server.security;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.fhirframework.persistence.entity.AgentApiKeyEntity;
import org.fhirframework.persistence.repository.AgentApiKeyRepository;
import org.fhirframework.server.Fhir4JavaApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * API Key Authentication Integration Tests.
 *
 * Tests the API key authentication mechanism ensuring:
 * - Valid API keys allow access to protected endpoints
 * - Invalid API keys return 401
 * - Expired API keys return 401
 * - Disabled API keys return 401
 */
@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "fhir4java.security.oauth2.enabled=true",
        "fhir4java.security.api-key.enabled=true"
})
@DisplayName("API Key Authentication Tests")
class ApiKeyAuthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AgentApiKeyRepository apiKeyRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String TEST_API_KEY = "test-api-key-12345678901234567890";
    private static final String VALID_AGENT_NAME = "test-agent";
    private static final String TEST_TENANT_ID = "default";
    private static final String TEST_SCOPES = "fhir:read,fhir:write";

    private static final String PATIENT_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "ApiKeyTest", "given": ["Test"]}],
                "gender": "male"
            }
            """;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
        // Clean up any existing test data
        apiKeyRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        apiKeyRepository.deleteAll();
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private AgentApiKeyEntity createApiKey(String apiKey, boolean enabled, LocalDateTime expiresAt) {
        AgentApiKeyEntity entity = AgentApiKeyEntity.builder()
                .keyHash(hashApiKey(apiKey))
                .agentName(VALID_AGENT_NAME)
                .tenantId(TEST_TENANT_ID)
                .scopes(TEST_SCOPES)
                .enabled(enabled)
                .expiresAt(expiresAt)
                .build();
        return apiKeyRepository.save(entity);
    }

    @Nested
    @DisplayName("Valid API Key Authentication")
    class ValidApiKeyAuthentication {

        @Test
        @DisplayName("should allow access with valid API key")
        void shouldAllowAccessWithValidApiKey() {
            // Setup: Create a valid, non-expired, enabled API key
            createApiKey(TEST_API_KEY, true, LocalDateTime.now().plusDays(30));

            Response response = given()
                    .header("X-API-Key", TEST_API_KEY)
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            // Should not be 401 - API key auth should succeed
            assertNotEquals(401, response.statusCode(),
                    "Valid API key request should not return 401, got: " + response.statusCode());
        }

        @Test
        @DisplayName("should allow POST with valid API key")
        void shouldAllowPostWithValidApiKey() {
            // Setup: Create a valid, non-expired, enabled API key
            createApiKey(TEST_API_KEY, true, LocalDateTime.now().plusDays(30));

            Response response = given()
                    .header("X-API-Key", TEST_API_KEY)
                    .contentType("application/fhir+json")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/fhir/r5/Patient");

            // Should not be 401 - API key auth should succeed
            assertNotEquals(401, response.statusCode(),
                    "Valid API key POST request should not return 401, got: " + response.statusCode());
        }

        @Test
        @DisplayName("should update lastUsedAt on successful authentication")
        void shouldUpdateLastUsedAtOnSuccessfulAuth() throws InterruptedException {
            // Setup: Create a valid API key with no lastUsedAt
            AgentApiKeyEntity savedKey = createApiKey(TEST_API_KEY, true, LocalDateTime.now().plusDays(30));
            LocalDateTime initialLastUsedAt = savedKey.getLastUsedAt();

            // Wait a bit to ensure time difference
            Thread.sleep(100);

            // Make a request with the API key
            given()
                    .header("X-API-Key", TEST_API_KEY)
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            // Verify lastUsedAt was updated
            AgentApiKeyEntity updatedKey = apiKeyRepository.findByKeyHash(hashApiKey(TEST_API_KEY)).orElseThrow();
            assertNotNull(updatedKey.getLastUsedAt(), "lastUsedAt should be set after authentication");
            if (initialLastUsedAt != null) {
                assertTrue(updatedKey.getLastUsedAt().isAfter(initialLastUsedAt),
                        "lastUsedAt should be updated to a later time");
            }
        }
    }

    @Nested
    @DisplayName("Invalid API Key Authentication")
    class InvalidApiKeyAuthentication {

        @Test
        @DisplayName("should return 401 for invalid API key")
        void shouldReturn401ForInvalidApiKey() {
            // Setup: Create a valid key, but request with wrong key
            createApiKey(TEST_API_KEY, true, LocalDateTime.now().plusDays(30));

            Response response = given()
                    .header("X-API-Key", "invalid-api-key-wrong-value")
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Invalid API key should return 401");
        }

        @Test
        @DisplayName("should return 401 for non-existent API key")
        void shouldReturn401ForNonExistentApiKey() {
            // No API key in database

            Response response = given()
                    .header("X-API-Key", "non-existent-api-key-12345")
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Non-existent API key should return 401");
        }
    }

    @Nested
    @DisplayName("Expired API Key Authentication")
    class ExpiredApiKeyAuthentication {

        @Test
        @DisplayName("should return 401 for expired API key")
        void shouldReturn401ForExpiredApiKey() {
            // Setup: Create an expired API key
            createApiKey(TEST_API_KEY, true, LocalDateTime.now().minusDays(1));

            Response response = given()
                    .header("X-API-Key", TEST_API_KEY)
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Expired API key should return 401");
        }
    }

    @Nested
    @DisplayName("Disabled API Key Authentication")
    class DisabledApiKeyAuthentication {

        @Test
        @DisplayName("should return 401 for disabled API key")
        void shouldReturn401ForDisabledApiKey() {
            // Setup: Create a disabled API key
            createApiKey(TEST_API_KEY, false, LocalDateTime.now().plusDays(30));

            Response response = given()
                    .header("X-API-Key", TEST_API_KEY)
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Disabled API key should return 401");
        }
    }

    @Nested
    @DisplayName("API Key and JWT Coexistence")
    class ApiKeyAndJwtCoexistence {

        @Test
        @DisplayName("should allow access without any auth to public endpoints")
        void shouldAllowAccessToPublicEndpointsWithoutAuth() {
            Response response = given()
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/metadata");

            // Metadata endpoint should be public
            assertNotEquals(401, response.statusCode(),
                    "Public metadata endpoint should not require authentication");
        }

        @Test
        @DisplayName("should return 401 when neither API key nor JWT provided")
        void shouldReturn401WhenNoAuthProvided() {
            Response response = given()
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Request without any authentication should return 401");
        }
    }
}
