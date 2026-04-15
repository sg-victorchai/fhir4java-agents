package org.fhirframework.mcp.agent;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentWebhookHandler.
 */
@DisplayName("AgentWebhookHandler Tests")
class AgentWebhookHandlerTest {

    private AgentWebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AgentWebhookHandler("http://localhost:8080", "test-tenant");
    }

    @Test
    @DisplayName("should create handler with server URL and tenant ID")
    void createHandlerWithServerUrl() {
        AgentWebhookHandler handler = new AgentWebhookHandler("http://localhost:8080", "tenant-1");
        assertNotNull(handler);
        assertEquals("tenant-1", handler.getTenantId());
    }

    @Test
    @DisplayName("should create handler with WebClient")
    void createHandlerWithWebClient() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
        AgentWebhookHandler handler = new AgentWebhookHandler(webClient, "tenant-2");
        assertNotNull(handler);
        assertEquals("tenant-2", handler.getTenantId());
    }

    @Nested
    @DisplayName("HMAC Signature Tests")
    class HmacSignatureTests {

        @Test
        @DisplayName("should compute consistent HMAC signature")
        void computeConsistentSignature() {
            String payload = "{\"resourceType\":\"Patient\",\"resourceId\":\"123\",\"action\":\"create\"}";
            String secret = "my-secret-key";

            String sig1 = handler.computeSignature(payload, secret);
            String sig2 = handler.computeSignature(payload, secret);

            assertNotNull(sig1);
            assertNotNull(sig2);
            assertEquals(sig1, sig2, "Same payload and secret should produce same signature");
        }

        @Test
        @DisplayName("should produce different signatures for different payloads")
        void differentPayloadsProduceDifferentSignatures() {
            String secret = "my-secret-key";
            String payload1 = "{\"resourceType\":\"Patient\",\"resourceId\":\"123\"}";
            String payload2 = "{\"resourceType\":\"Patient\",\"resourceId\":\"456\"}";

            String sig1 = handler.computeSignature(payload1, secret);
            String sig2 = handler.computeSignature(payload2, secret);

            assertNotEquals(sig1, sig2, "Different payloads should produce different signatures");
        }

        @Test
        @DisplayName("should produce different signatures for different secrets")
        void differentSecretsProduceDifferentSignatures() {
            String payload = "{\"resourceType\":\"Patient\",\"resourceId\":\"123\"}";

            String sig1 = handler.computeSignature(payload, "secret-1");
            String sig2 = handler.computeSignature(payload, "secret-2");

            assertNotEquals(sig1, sig2, "Different secrets should produce different signatures");
        }

        @Test
        @DisplayName("should validate correct signature")
        void validateCorrectSignature() {
            String payload = "{\"resourceType\":\"Patient\",\"resourceId\":\"123\",\"action\":\"create\"}";
            String secret = "test-secret";

            String computed = handler.computeSignature(payload, secret);
            String signature = "sha256=" + computed;

            assertTrue(handler.validateSignature(signature, payload, secret));
        }

        @Test
        @DisplayName("should reject incorrect signature")
        void rejectIncorrectSignature() {
            String payload = "{\"resourceType\":\"Patient\",\"resourceId\":\"123\"}";
            String secret = "test-secret";

            String wrongSignature = "sha256=incorrect-signature";

            assertFalse(handler.validateSignature(wrongSignature, payload, secret));
        }

        @Test
        @DisplayName("should reject signature without prefix")
        void rejectSignatureWithoutPrefix() {
            String payload = "{\"resourceType\":\"Patient\"}";
            String secret = "test-secret";

            String signatureWithoutPrefix = handler.computeSignature(payload, secret);

            assertFalse(handler.validateSignature(signatureWithoutPrefix, payload, secret));
        }

        @Test
        @DisplayName("should reject null signature")
        void rejectNullSignature() {
            assertFalse(handler.validateSignature(null, "{}", "secret"));
        }
    }

    @Nested
    @DisplayName("Webhook Callback Handling Tests")
    class CallbackHandlingTests {

        @Test
        @DisplayName("should process valid callback without signature")
        void processValidCallbackWithoutSignature() {
            String payload = """
                    {
                        "resourceType": "Patient",
                        "resourceId": "123",
                        "action": "create",
                        "tenantId": "default",
                        "timestamp": "2026-04-15T10:00:00Z"
                    }
                    """;

            AtomicReference<ResourceChangeEvent> receivedEvent = new AtomicReference<>();

            ResponseEntity<Void> response = handler.handleCallback(
                    "",
                    payload,
                    null,
                    receivedEvent::set
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(receivedEvent.get());
            assertEquals("Patient", receivedEvent.get().resourceType());
            assertEquals("123", receivedEvent.get().resourceId());
            assertEquals("create", receivedEvent.get().action());
        }

        @Test
        @DisplayName("should reject invalid JSON payload")
        void rejectInvalidJsonPayload() {
            String invalidPayload = "not valid json";

            ResponseEntity<Void> response = handler.handleCallback(
                    "",
                    invalidPayload,
                    null,
                    event -> {}
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("should reject callback with invalid signature")
        void rejectCallbackWithInvalidSignature() {
            String payload = """
                    {
                        "resourceType": "Patient",
                        "resourceId": "123",
                        "action": "create",
                        "tenantId": "default",
                        "timestamp": "2026-04-15T10:00:00Z"
                    }
                    """;

            ResponseEntity<Void> response = handler.handleCallback(
                    "sha256=invalid-signature",
                    payload,
                    "my-secret",
                    event -> {}
            );

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("should accept callback with valid signature")
        void acceptCallbackWithValidSignature() {
            String payload = """
                    {"resourceType":"Patient","resourceId":"123","action":"create","tenantId":"default","timestamp":"2026-04-15T10:00:00Z"}""";
            String secret = "my-secret";
            String signature = "sha256=" + handler.computeSignature(payload, secret);

            AtomicReference<ResourceChangeEvent> receivedEvent = new AtomicReference<>();

            ResponseEntity<Void> response = handler.handleCallback(
                    signature,
                    payload,
                    secret,
                    receivedEvent::set
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(receivedEvent.get());
        }
    }

    @Nested
    @DisplayName("Action Handler Tests")
    class ActionHandlerTests {

        @Test
        @DisplayName("should register and invoke action handler")
        void registerAndInvokeActionHandler() {
            List<ResourceChangeEvent> createEvents = new ArrayList<>();
            List<ResourceChangeEvent> updateEvents = new ArrayList<>();

            handler.registerActionHandler("create", createEvents::add);
            handler.registerActionHandler("update", updateEvents::add);

            ResourceChangeEvent createEvent = new ResourceChangeEvent(
                    "Patient", "123", "create", "default", Instant.now());
            ResourceChangeEvent updateEvent = new ResourceChangeEvent(
                    "Patient", "456", "update", "default", Instant.now());

            handler.processEvent(createEvent);
            handler.processEvent(updateEvent);

            assertEquals(1, createEvents.size());
            assertEquals(1, updateEvents.size());
            assertEquals("123", createEvents.get(0).resourceId());
            assertEquals("456", updateEvents.get(0).resourceId());
        }

        @Test
        @DisplayName("should handle event with no registered handler")
        void handleEventWithNoHandler() {
            ResourceChangeEvent event = new ResourceChangeEvent(
                    "Patient", "123", "delete", "default", Instant.now());

            // Should not throw
            assertDoesNotThrow(() -> handler.processEvent(event));
        }
    }
}
