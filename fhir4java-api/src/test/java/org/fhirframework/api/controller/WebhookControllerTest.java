package org.fhirframework.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirframework.api.dto.WebhookRequest;
import org.fhirframework.api.dto.WebhookResponse;
import org.fhirframework.api.subscription.WebhookRegistry;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.persistence.entity.WebhookEntity;
import org.fhirframework.persistence.repository.WebhookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebhookController} and {@link WebhookRegistry}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Registry")
class WebhookControllerTest {

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private RestTemplate restTemplate;

    private WebhookController controller;
    private WebhookRegistry registry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(webhookRepository);
        registry = new WebhookRegistry(webhookRepository, restTemplate);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("Webhook Registration")
    class WebhookRegistration {

        @Test
        @DisplayName("should register webhook and return ID")
        void shouldRegisterWebhookAndReturnId() {
            // Given
            WebhookRequest request = new WebhookRequest(
                    "https://example.com/webhook",
                    List.of("Patient.create", "Patient.update"),
                    "mysecret"
            );

            WebhookEntity savedEntity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .topics("Patient.create,Patient.update")
                    .secret("mysecret")
                    .tenantId("default")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.save(any(WebhookEntity.class))).thenReturn(savedEntity);

            // When
            ResponseEntity<WebhookResponse> response = controller.register(request, "default");

            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1L, response.getBody().id());
            assertEquals("https://example.com/webhook", response.getBody().callbackUrl());
            assertEquals(List.of("Patient.create", "Patient.update"), response.getBody().topics());
            assertTrue(response.getBody().enabled());

            // Verify save was called
            ArgumentCaptor<WebhookEntity> captor = ArgumentCaptor.forClass(WebhookEntity.class);
            verify(webhookRepository).save(captor.capture());
            assertEquals("https://example.com/webhook", captor.getValue().getCallbackUrl());
            assertEquals("Patient.create,Patient.update", captor.getValue().getTopics());
            assertEquals("mysecret", captor.getValue().getSecret());
        }

        @Test
        @DisplayName("should reject invalid callback URL")
        void shouldRejectInvalidCallbackUrl() {
            // Given
            WebhookRequest request = new WebhookRequest(
                    "invalid-url",
                    List.of("Patient.create"),
                    null
            );

            // When/Then
            assertThrows(IllegalArgumentException.class, () -> {
                controller.register(request, "default");
            });
        }

        @Test
        @DisplayName("should reject null callback URL")
        void shouldRejectNullCallbackUrl() {
            // Given
            WebhookRequest request = new WebhookRequest(
                    null,
                    List.of("Patient.create"),
                    null
            );

            // When/Then
            assertThrows(IllegalArgumentException.class, () -> {
                controller.register(request, "default");
            });
        }

        @Test
        @DisplayName("should register webhook without topics (subscribe to all)")
        void shouldRegisterWebhookWithoutTopics() {
            // Given
            WebhookRequest request = new WebhookRequest(
                    "https://example.com/webhook",
                    null,
                    null
            );

            WebhookEntity savedEntity = WebhookEntity.builder()
                    .id(2L)
                    .callbackUrl("https://example.com/webhook")
                    .topics(null)
                    .tenantId("default")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.save(any(WebhookEntity.class))).thenReturn(savedEntity);

            // When
            ResponseEntity<WebhookResponse> response = controller.register(request, "default");

            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().topics().isEmpty());
        }
    }

    @Nested
    @DisplayName("List Webhooks")
    class ListWebhooks {

        @Test
        @DisplayName("should return registered webhooks for tenant")
        void shouldReturnRegisteredWebhooks() {
            // Given
            WebhookEntity entity1 = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example1.com/webhook")
                    .topics("Patient.create")
                    .tenantId("tenant1")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            WebhookEntity entity2 = WebhookEntity.builder()
                    .id(2L)
                    .callbackUrl("https://example2.com/webhook")
                    .topics("Observation.create")
                    .tenantId("tenant1")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.findByTenantId("tenant1")).thenReturn(List.of(entity1, entity2));

            // When
            List<WebhookResponse> responses = controller.list("tenant1");

            // Then
            assertEquals(2, responses.size());
            assertEquals(1L, responses.get(0).id());
            assertEquals(2L, responses.get(1).id());
        }

        @Test
        @DisplayName("should return empty list when no webhooks registered")
        void shouldReturnEmptyListWhenNoWebhooks() {
            // Given
            when(webhookRepository.findByTenantId("tenant1")).thenReturn(List.of());

            // When
            List<WebhookResponse> responses = controller.list("tenant1");

            // Then
            assertTrue(responses.isEmpty());
        }
    }

    @Nested
    @DisplayName("Delete Webhook")
    class DeleteWebhook {

        @Test
        @DisplayName("should delete webhook and return 204")
        void shouldDeleteWebhookAndReturn204() {
            // Given
            WebhookEntity entity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(true)
                    .build();

            when(webhookRepository.findById(1L)).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<Void> response = controller.delete(1L, "default");

            // Then
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(webhookRepository).delete(entity);
        }

        @Test
        @DisplayName("should return 404 when webhook not found")
        void shouldReturn404WhenWebhookNotFound() {
            // Given
            when(webhookRepository.findById(99L)).thenReturn(Optional.empty());

            // When
            ResponseEntity<Void> response = controller.delete(99L, "default");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(webhookRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should return 404 when webhook belongs to different tenant")
        void shouldReturn404WhenWrongTenant() {
            // Given
            WebhookEntity entity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .tenantId("tenant1")
                    .enabled(true)
                    .build();

            when(webhookRepository.findById(1L)).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<Void> response = controller.delete(1L, "tenant2");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(webhookRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Find Matching Webhooks")
    class FindMatchingWebhooks {

        @Test
        @DisplayName("should find webhooks matching by topic")
        void shouldFindWebhooksMatchingByTopic() {
            // Given
            WebhookEntity patientWebhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/patient")
                    .topics("Patient.create,Patient.update")
                    .tenantId("default")
                    .enabled(true)
                    .build();

            WebhookEntity observationWebhook = WebhookEntity.builder()
                    .id(2L)
                    .callbackUrl("https://example.com/observation")
                    .topics("Observation.create")
                    .tenantId("default")
                    .enabled(true)
                    .build();

            when(webhookRepository.findByTenantIdAndEnabled("default", true))
                    .thenReturn(List.of(patientWebhook, observationWebhook));

            // When
            List<WebhookEntity> matching = registry.findMatchingWebhooks("Patient", "create", "default");

            // Then
            assertEquals(1, matching.size());
            assertEquals(1L, matching.get(0).getId());
        }

        @Test
        @DisplayName("should find webhooks with no topics (subscribed to all)")
        void shouldFindWebhooksWithNoTopics() {
            // Given
            WebhookEntity allEventsWebhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/all")
                    .topics(null)  // Subscribe to all
                    .tenantId("default")
                    .enabled(true)
                    .build();

            when(webhookRepository.findByTenantIdAndEnabled("default", true))
                    .thenReturn(List.of(allEventsWebhook));

            // When
            List<WebhookEntity> matching = registry.findMatchingWebhooks("Patient", "create", "default");

            // Then
            assertEquals(1, matching.size());
        }

        @Test
        @DisplayName("should find webhooks with wildcard topic")
        void shouldFindWebhooksWithWildcard() {
            // Given
            WebhookEntity wildcardWebhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/all-patient")
                    .topics("Patient.*")  // All Patient events
                    .tenantId("default")
                    .enabled(true)
                    .build();

            when(webhookRepository.findByTenantIdAndEnabled("default", true))
                    .thenReturn(List.of(wildcardWebhook));

            // When - test create
            List<WebhookEntity> matchingCreate = registry.findMatchingWebhooks("Patient", "create", "default");
            // When - test update
            List<WebhookEntity> matchingUpdate = registry.findMatchingWebhooks("Patient", "update", "default");
            // When - test Observation (should not match)
            List<WebhookEntity> matchingObservation = registry.findMatchingWebhooks("Observation", "create", "default");

            // Then
            assertEquals(1, matchingCreate.size());
            assertEquals(1, matchingUpdate.size());
            assertEquals(0, matchingObservation.size());
        }

        @Test
        @DisplayName("should return empty when no matching webhooks")
        void shouldReturnEmptyWhenNoMatching() {
            // Given
            WebhookEntity observationWebhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/observation")
                    .topics("Observation.create")
                    .tenantId("default")
                    .enabled(true)
                    .build();

            when(webhookRepository.findByTenantIdAndEnabled("default", true))
                    .thenReturn(List.of(observationWebhook));

            // When
            List<WebhookEntity> matching = registry.findMatchingWebhooks("Patient", "create", "default");

            // Then
            assertTrue(matching.isEmpty());
        }
    }

    @Nested
    @DisplayName("Dispatch Event")
    class DispatchEvent {

        @Test
        @DisplayName("should send HTTP POST with HMAC signature header")
        void shouldSendHttpPostWithHmacSignature() {
            // Given
            WebhookEntity webhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .secret("mysecret")
                    .enabled(true)
                    .build();

            ResourceChangeEvent event = ResourceChangeEvent.create("Patient", "123", "default");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("OK"));

            // When
            registry.dispatchEvent(webhook, event);

            // Then
            ArgumentCaptor<HttpEntity<String>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq("https://example.com/webhook"), requestCaptor.capture(), eq(String.class));

            HttpEntity<String> request = requestCaptor.getValue();
            assertNotNull(request.getBody());
            assertTrue(request.getBody().contains("Patient"));
            assertTrue(request.getBody().contains("123"));
            assertTrue(request.getBody().contains("create"));

            // Verify signature header is present
            HttpHeaders headers = request.getHeaders();
            assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
            assertTrue(headers.containsKey("X-Webhook-Signature"));
            assertTrue(headers.getFirst("X-Webhook-Signature").startsWith("sha256="));
        }

        @Test
        @DisplayName("should send HTTP POST without signature when no secret")
        void shouldSendHttpPostWithoutSignatureWhenNoSecret() {
            // Given
            WebhookEntity webhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .secret(null)
                    .enabled(true)
                    .build();

            ResourceChangeEvent event = ResourceChangeEvent.create("Patient", "123", "default");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("OK"));

            // When
            registry.dispatchEvent(webhook, event);

            // Then
            ArgumentCaptor<HttpEntity<String>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq("https://example.com/webhook"), requestCaptor.capture(), eq(String.class));

            HttpEntity<String> request = requestCaptor.getValue();
            HttpHeaders headers = request.getHeaders();
            assertFalse(headers.containsKey("X-Webhook-Signature"));
        }

        @Test
        @DisplayName("should handle null webhook gracefully")
        void shouldHandleNullWebhookGracefully() {
            // Given
            ResourceChangeEvent event = ResourceChangeEvent.create("Patient", "123", "default");

            // When/Then - should not throw
            assertDoesNotThrow(() -> registry.dispatchEvent(null, event));
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("should handle null event gracefully")
        void shouldHandleNullEventGracefully() {
            // Given
            WebhookEntity webhook = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .enabled(true)
                    .build();

            // When/Then - should not throw
            assertDoesNotThrow(() -> registry.dispatchEvent(webhook, null));
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("HMAC Signature")
    class HmacSignature {

        @Test
        @DisplayName("should compute consistent HMAC signature")
        void shouldComputeConsistentHmacSignature() {
            // Given
            String payload = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";
            String secret = "mysecret";

            // When
            String signature1 = registry.computeHmacSignature(payload, secret);
            String signature2 = registry.computeHmacSignature(payload, secret);

            // Then
            assertEquals(signature1, signature2);
            assertTrue(signature1.startsWith("sha256="));
        }

        @Test
        @DisplayName("should produce different signatures for different secrets")
        void shouldProduceDifferentSignaturesForDifferentSecrets() {
            // Given
            String payload = "{\"resourceType\":\"Patient\",\"id\":\"123\"}";

            // When
            String signature1 = registry.computeHmacSignature(payload, "secret1");
            String signature2 = registry.computeHmacSignature(payload, "secret2");

            // Then
            assertNotEquals(signature1, signature2);
        }

        @Test
        @DisplayName("should produce different signatures for different payloads")
        void shouldProduceDifferentSignaturesForDifferentPayloads() {
            // Given
            String secret = "mysecret";

            // When
            String signature1 = registry.computeHmacSignature("{\"id\":\"1\"}", secret);
            String signature2 = registry.computeHmacSignature("{\"id\":\"2\"}", secret);

            // Then
            assertNotEquals(signature1, signature2);
        }
    }

    @Nested
    @DisplayName("WebhookEntity Topic Matching")
    class TopicMatching {

        @Test
        @DisplayName("should match exact topic")
        void shouldMatchExactTopic() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics("Patient.create")
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertFalse(entity.matchesTopic("Patient", "update"));
            assertFalse(entity.matchesTopic("Observation", "create"));
        }

        @Test
        @DisplayName("should match multiple topics")
        void shouldMatchMultipleTopics() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics("Patient.create,Patient.update,Observation.create")
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertTrue(entity.matchesTopic("Patient", "update"));
            assertTrue(entity.matchesTopic("Observation", "create"));
            assertFalse(entity.matchesTopic("Patient", "delete"));
        }

        @Test
        @DisplayName("should match wildcard all-events for resource type")
        void shouldMatchWildcardForResourceType() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics("Patient.*")
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertTrue(entity.matchesTopic("Patient", "update"));
            assertTrue(entity.matchesTopic("Patient", "delete"));
            assertFalse(entity.matchesTopic("Observation", "create"));
        }

        @Test
        @DisplayName("should match global wildcard")
        void shouldMatchGlobalWildcard() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics("*.*")
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertTrue(entity.matchesTopic("Observation", "update"));
            assertTrue(entity.matchesTopic("Encounter", "delete"));
        }

        @Test
        @DisplayName("should match all events when topics is null")
        void shouldMatchAllWhenTopicsNull() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics(null)
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertTrue(entity.matchesTopic("Observation", "update"));
        }

        @Test
        @DisplayName("should match all events when topics is blank")
        void shouldMatchAllWhenTopicsBlank() {
            WebhookEntity entity = WebhookEntity.builder()
                    .topics("   ")
                    .build();

            assertTrue(entity.matchesTopic("Patient", "create"));
            assertTrue(entity.matchesTopic("Observation", "update"));
        }
    }

    @Nested
    @DisplayName("Enable/Disable Webhook")
    class EnableDisableWebhook {

        @Test
        @DisplayName("should enable webhook")
        void shouldEnableWebhook() {
            // Given
            WebhookEntity entity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(false)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(webhookRepository.save(any(WebhookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ResponseEntity<WebhookResponse> response = controller.enable(1L, "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().enabled());
        }

        @Test
        @DisplayName("should disable webhook")
        void shouldDisableWebhook() {
            // Given
            WebhookEntity entity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(webhookRepository.save(any(WebhookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ResponseEntity<WebhookResponse> response = controller.disable(1L, "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().enabled());
        }
    }

    @Nested
    @DisplayName("Get Webhook")
    class GetWebhook {

        @Test
        @DisplayName("should get webhook by ID")
        void shouldGetWebhookById() {
            // Given
            WebhookEntity entity = WebhookEntity.builder()
                    .id(1L)
                    .callbackUrl("https://example.com/webhook")
                    .topics("Patient.create")
                    .tenantId("default")
                    .enabled(true)
                    .createdAt(Instant.now())
                    .build();

            when(webhookRepository.findById(1L)).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<WebhookResponse> response = controller.get(1L, "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1L, response.getBody().id());
            assertEquals("https://example.com/webhook", response.getBody().callbackUrl());
        }

        @Test
        @DisplayName("should return 404 when webhook not found")
        void shouldReturn404WhenNotFound() {
            // Given
            when(webhookRepository.findById(99L)).thenReturn(Optional.empty());

            // When
            ResponseEntity<WebhookResponse> response = controller.get(99L, "default");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
}
