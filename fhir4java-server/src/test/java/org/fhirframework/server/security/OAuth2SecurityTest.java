package org.fhirframework.server.security;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.fhirframework.server.Fhir4JavaApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * OAuth2 Security Integration Tests.
 *
 * Tests the OAuth2 resource server configuration ensuring:
 * - Unauthenticated requests to protected endpoints return 401
 * - Metadata endpoint is publicly accessible
 * - Valid JWT tokens allow access to protected endpoints
 */
@SpringBootTest(
        classes = Fhir4JavaApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "fhir4java.security.oauth2.enabled=true"
})
@DisplayName("OAuth2 Security Tests")
class OAuth2SecurityTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String PATIENT_JSON = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "SecurityTest", "given": ["OAuth2"]}],
                "gender": "male"
            }
            """;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    @Nested
    @DisplayName("Unauthenticated Access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("should return 401 for unauthenticated request to protected FHIR endpoint")
        void shouldReturn401ForUnauthenticatedFhirRequest() {
            Response response = given()
                    .contentType("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient/123");

            assertEquals(401, response.statusCode(),
                    "Unauthenticated request to /fhir/r5/Patient/123 should return 401");
        }

        @Test
        @DisplayName("should return 401 for unauthenticated POST to FHIR endpoint")
        void shouldReturn401ForUnauthenticatedFhirPost() {
            Response response = given()
                    .contentType("application/fhir+json")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/fhir/r5/Patient");

            assertEquals(401, response.statusCode(),
                    "Unauthenticated POST to /fhir/r5/Patient should return 401");
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request to MCP endpoint")
        void shouldReturn401ForUnauthenticatedMcpRequest() {
            Response response = given()
                    .contentType("application/json")
                    .when()
                    .get("/api/mcp/tools");

            assertEquals(401, response.statusCode(),
                    "Unauthenticated request to /api/mcp/tools should return 401");
        }
    }

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("should allow unauthenticated access to R5 metadata endpoint")
        void shouldAllowAccessToR5Metadata() {
            Response response = given()
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/metadata");

            // Should return 200 (accessible) or content - not 401
            assertNotEquals(401, response.statusCode(),
                    "Metadata endpoint should be publicly accessible");
        }

        @Test
        @DisplayName("should allow unauthenticated access to unversioned metadata endpoint")
        void shouldAllowAccessToUnversionedMetadata() {
            // Test the unversioned metadata endpoint which defaults to R5
            Response response = given()
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/metadata");

            // Should return 200 (accessible) or content - not 401
            assertNotEquals(401, response.statusCode(),
                    "Metadata endpoint should be publicly accessible");
        }

        @Test
        @DisplayName("should allow unauthenticated access to actuator health endpoint")
        void shouldAllowAccessToActuatorHealth() {
            Response response = given()
                    .when()
                    .get("/actuator/health");

            // Should return 200 or 503 (service status) - not 401
            assertTrue(response.statusCode() == 200 || response.statusCode() == 503,
                    "Actuator health should be publicly accessible, got: " + response.statusCode());
        }
    }

    @Nested
    @DisplayName("Authenticated Access with Valid JWT")
    class AuthenticatedAccess {

        @BeforeEach
        void setupMockJwt() {
            // Mock the JWT decoder to accept any token and return a valid JWT
            when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
                String tokenValue = invocation.getArgument(0);
                return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(tokenValue)
                        .header("alg", "RS256")
                        .header("typ", "JWT")
                        .claim("sub", "test-user")
                        .claim("scope", "fhir:read fhir:write")
                        .claim("iss", "http://localhost:8080/realms/fhir4java")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build();
            });
        }

        @Test
        @DisplayName("should allow authenticated request to FHIR endpoint with valid JWT")
        void shouldAllowAuthenticatedFhirRequest() {
            Response response = given()
                    .header("Authorization", "Bearer valid-test-token")
                    .contentType("application/fhir+json")
                    .body(PATIENT_JSON)
                    .when()
                    .post("/fhir/r5/Patient");

            // Should not be 401 (unauthorized) - might be 201 (created) or other valid response
            assertNotEquals(401, response.statusCode(),
                    "Authenticated request should not return 401, got: " + response.statusCode());
        }

        @Test
        @DisplayName("should allow authenticated GET request to FHIR search endpoint")
        void shouldAllowAuthenticatedFhirSearch() {
            Response response = given()
                    .header("Authorization", "Bearer valid-test-token")
                    .accept("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient");

            // Should not be 401 - might be 200 with empty bundle or other valid response
            assertNotEquals(401, response.statusCode(),
                    "Authenticated search request should not return 401, got: " + response.statusCode());
        }
    }

    @Nested
    @DisplayName("Invalid JWT Token")
    class InvalidJwtToken {

        @BeforeEach
        void setupMockJwtToRejectInvalid() {
            // Mock the JWT decoder to throw exception for invalid tokens
            when(jwtDecoder.decode(anyString())).thenThrow(
                    new org.springframework.security.oauth2.jwt.JwtException("Invalid token"));
        }

        @Test
        @DisplayName("should return 401 for request with invalid JWT token")
        void shouldReturn401ForInvalidJwt() {
            Response response = given()
                    .header("Authorization", "Bearer invalid-token")
                    .contentType("application/fhir+json")
                    .when()
                    .get("/fhir/r5/Patient/123");

            assertEquals(401, response.statusCode(),
                    "Request with invalid JWT should return 401");
        }
    }
}
