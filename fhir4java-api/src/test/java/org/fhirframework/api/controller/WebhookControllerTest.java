package org.fhirframework.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fhirframework.api.dto.WebhookRequest;
import org.fhirframework.api.dto.WebhookResponse;
import org.fhirframework.api.event.SubscriptionType;
import org.fhirframework.api.subscription.WebhookRegistry;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
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
    private EventSubscriptionRepository subscriptionRepository;

    @Mock
    private RestTemplate restTemplate;

    private WebhookController controller;
    private WebhookRegistry registry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        controller = new WebhookController(subscriptionRepository);
        registry = new WebhookRegistry(subscriptionRepository, restTemplate);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("Webhook Registration")
    class WebhookRegistration {

        @Test
        @DisplayName("should register webhook and return subscription ID")
        void shouldRegisterWebhookAndReturnId() {
            // Given
            WebhookRequest request = new WebhookRequest(
                    "https://example.com/webhook",
                    List.of("Patient.create", "Patient.update"),
                    "mysecret"
            );

            when(subscriptionRepository.save(any(EventSubscriptionEntity.class))).thenAnswer(invocation -> {
                EventSubscriptionEntity entity = invocation.getArgument(0);
                entity.setCreatedAt(Instant.now());
                return entity;
            });

            // When
            ResponseEntity<WebhookResponse> response = controller.register(request, "default");

            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().subscriptionId().startsWith("webhook-"));
            assertEquals("https://example.com/webhook", response.getBody().callbackUrl());
            assertEquals(List.of("Patient.create", "Patient.update"), response.getBody().topics());
            assertTrue(response.getBody().enabled());

            // Verify save was called
            ArgumentCaptor<EventSubscriptionEntity> captor = ArgumentCaptor.forClass(EventSubscriptionEntity.class);
            verify(subscriptionRepository).save(captor.capture());
            assertEquals("https://example.com/webhook", captor.getValue().getSubscriberEndpoint());
            assertEquals("Patient.create,Patient.update", captor.getValue().getTopics());
            assertEquals("mysecret", captor.getValue().getSecret());
            assertEquals(SubscriptionType.WEBHOOK.name(), captor.getValue().getSubscriptionType());
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

            when(subscriptionRepository.save(any(EventSubscriptionEntity.class))).thenAnswer(invocation -> {
                EventSubscriptionEntity entity = invocation.getArgument(0);
                entity.setCreatedAt(Instant.now());
                return entity;
            });

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
            EventSubscriptionEntity entity1 = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example1.com/webhook")
                    .topics("Patient.create")
                    .tenantId("tenant1")
                    .enabled(true)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();

            EventSubscriptionEntity entity2 = EventSubscriptionEntity.builder()
                    .id(2L)
                    .subscriptionId("webhook-2")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example2.com/webhook")
                    .topics("Observation.create")
                    .tenantId("tenant1")
                    .enabled(true)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();

            when(subscriptionRepository.findActiveByTenantAndType("tenant1", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of(entity1, entity2));

            // When
            List<WebhookResponse> responses = controller.list("tenant1");

            // Then
            assertEquals(2, responses.size());
            assertEquals("webhook-1", responses.get(0).subscriptionId());
            assertEquals("webhook-2", responses.get(1).subscriptionId());
        }

        @Test
        @DisplayName("should return empty list when no webhooks registered")
        void shouldReturnEmptyListWhenNoWebhooks() {
            // Given
            when(subscriptionRepository.findActiveByTenantAndType("tenant1", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of());

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
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findBySubscriptionId("webhook-1")).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<Void> response = controller.delete("webhook-1", "default");

            // Then
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(subscriptionRepository).delete(entity);
        }

        @Test
        @DisplayName("should return 404 when webhook not found")
        void shouldReturn404WhenWebhookNotFound() {
            // Given
            when(subscriptionRepository.findBySubscriptionId("webhook-99")).thenReturn(Optional.empty());

            // When
            ResponseEntity<Void> response = controller.delete("webhook-99", "default");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(subscriptionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should return 404 when webhook belongs to different tenant")
        void shouldReturn404WhenWrongTenant() {
            // Given
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .tenantId("tenant1")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findBySubscriptionId("webhook-1")).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<Void> response = controller.delete("webhook-1", "tenant2");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(subscriptionRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Find Matching Webhooks")
    class FindMatchingWebhooks {

        @Test
        @DisplayName("should find webhooks matching by topic")
        void shouldFindWebhooksMatchingByTopic() {
            // Given
            EventSubscriptionEntity patientWebhook = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/patient")
                    .topics("Patient")
                    .actions("create,update")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            EventSubscriptionEntity observationWebhook = EventSubscriptionEntity.builder()
                    .id(2L)
                    .subscriptionId("webhook-2")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/observation")
                    .topics("Observation")
                    .actions("create")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findActiveByTenantAndType("default", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of(patientWebhook, observationWebhook));

            // When
            List<EventSubscriptionEntity> matching = registry.findMatchingSubscriptions("Patient", "create", "default");

            // Then
            assertEquals(1, matching.size());
            assertEquals("webhook-1", matching.get(0).getSubscriptionId());
        }

        @Test
        @DisplayName("should find webhooks with no topics (subscribed to all)")
        void shouldFindWebhooksWithNoTopics() {
            // Given
            EventSubscriptionEntity allEventsWebhook = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/all")
                    .topics(null)  // Subscribe to all
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findActiveByTenantAndType("default", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of(allEventsWebhook));

            // When
            List<EventSubscriptionEntity> matching = registry.findMatchingSubscriptions("Patient", "create", "default");

            // Then
            assertEquals(1, matching.size());
        }

        @Test
        @DisplayName("should find webhooks with wildcard topic")
        void shouldFindWebhooksWithWildcard() {
            // Given
            EventSubscriptionEntity wildcardWebhook = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/all-patient")
                    .topics("Patient,*")  // All events including Patient
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findActiveByTenantAndType("default", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of(wildcardWebhook));

            // When - test create
            List<EventSubscriptionEntity> matchingCreate = registry.findMatchingSubscriptions("Patient", "create", "default");
            // When - test update
            List<EventSubscriptionEntity> matchingUpdate = registry.findMatchingSubscriptions("Patient", "update", "default");
            // When - test Observation (should also match due to wildcard)
            List<EventSubscriptionEntity> matchingObservation = registry.findMatchingSubscriptions("Observation", "create", "default");

            // Then
            assertEquals(1, matchingCreate.size());
            assertEquals(1, matchingUpdate.size());
            assertEquals(1, matchingObservation.size());
        }

        @Test
        @DisplayName("should return empty when no matching webhooks")
        void shouldReturnEmptyWhenNoMatching() {
            // Given
            EventSubscriptionEntity observationWebhook = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/observation")
                    .topics("Observation")
                    .actions("create")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            when(subscriptionRepository.findActiveByTenantAndType("default", SubscriptionType.WEBHOOK.name()))
                    .thenReturn(List.of(observationWebhook));

            // When
            List<EventSubscriptionEntity> matching = registry.findMatchingSubscriptions("Patient", "create", "default");

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
            EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .secret("mysecret")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            ResourceChangeEvent event = ResourceChangeEvent.create("Patient", "123", "default");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("OK"));

            // When
            registry.dispatchEvent(subscription, event);

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
            EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .secret(null)
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            ResourceChangeEvent event = ResourceChangeEvent.create("Patient", "123", "default");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("OK"));

            // When
            registry.dispatchEvent(subscription, event);

            // Then
            ArgumentCaptor<HttpEntity<String>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq("https://example.com/webhook"), requestCaptor.capture(), eq(String.class));

            HttpEntity<String> request = requestCaptor.getValue();
            HttpHeaders headers = request.getHeaders();
            assertFalse(headers.containsKey("X-Webhook-Signature"));
        }

        @Test
        @DisplayName("should handle null subscription gracefully")
        void shouldHandleNullSubscriptionGracefully() {
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
            EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .enabled(true)
                    .status("ACTIVE")
                    .build();

            // When/Then - should not throw
            assertDoesNotThrow(() -> registry.dispatchEvent(subscription, null));
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
    @DisplayName("EventSubscriptionEntity Topic Matching")
    class TopicMatching {

        @Test
        @DisplayName("should match exact topic")
        void shouldMatchExactTopic() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics("Patient")
                    .actions("create")
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertFalse(entity.matchesEvent("Patient", "update"));
            assertFalse(entity.matchesEvent("Observation", "create"));
        }

        @Test
        @DisplayName("should match multiple topics")
        void shouldMatchMultipleTopics() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics("Patient,Observation")
                    .actions("create,update")
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertTrue(entity.matchesEvent("Patient", "update"));
            assertTrue(entity.matchesEvent("Observation", "create"));
            assertFalse(entity.matchesEvent("Patient", "delete"));
        }

        @Test
        @DisplayName("should match wildcard all topics")
        void shouldMatchWildcardAllTopics() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics("*")
                    .actions("create,update,delete")
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertTrue(entity.matchesEvent("Observation", "update"));
            assertTrue(entity.matchesEvent("Encounter", "delete"));
        }

        @Test
        @DisplayName("should match wildcard all actions")
        void shouldMatchWildcardAllActions() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics("Patient")
                    .actions("*")
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertTrue(entity.matchesEvent("Patient", "update"));
            assertTrue(entity.matchesEvent("Patient", "delete"));
            assertFalse(entity.matchesEvent("Observation", "create"));
        }

        @Test
        @DisplayName("should match all events when topics is null")
        void shouldMatchAllWhenTopicsNull() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics(null)
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertTrue(entity.matchesEvent("Observation", "update"));
        }

        @Test
        @DisplayName("should match all events when topics is blank")
        void shouldMatchAllWhenTopicsBlank() {
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .topics("   ")
                    .build();

            assertTrue(entity.matchesEvent("Patient", "create"));
            assertTrue(entity.matchesEvent("Observation", "update"));
        }
    }

    @Nested
    @DisplayName("Enable/Disable Webhook")
    class EnableDisableWebhook {

        @Test
        @DisplayName("should enable webhook")
        void shouldEnableWebhook() {
            // Given
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(false)
                    .status("PAUSED")
                    .createdAt(Instant.now())
                    .build();

            when(subscriptionRepository.findBySubscriptionId("webhook-1")).thenReturn(Optional.of(entity));
            when(subscriptionRepository.save(any(EventSubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ResponseEntity<WebhookResponse> response = controller.enable("webhook-1", "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().enabled());
            assertEquals("ACTIVE", response.getBody().status());
        }

        @Test
        @DisplayName("should disable webhook")
        void shouldDisableWebhook() {
            // Given
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();

            when(subscriptionRepository.findBySubscriptionId("webhook-1")).thenReturn(Optional.of(entity));
            when(subscriptionRepository.save(any(EventSubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            ResponseEntity<WebhookResponse> response = controller.disable("webhook-1", "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().enabled());
            assertEquals("PAUSED", response.getBody().status());
        }
    }

    @Nested
    @DisplayName("Get Webhook")
    class GetWebhook {

        @Test
        @DisplayName("should get webhook by subscription ID")
        void shouldGetWebhookById() {
            // Given
            EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                    .id(1L)
                    .subscriptionId("webhook-1")
                    .subscriptionType(SubscriptionType.WEBHOOK.name())
                    .subscriberEndpoint("https://example.com/webhook")
                    .topics("Patient")
                    .tenantId("default")
                    .enabled(true)
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .build();

            when(subscriptionRepository.findBySubscriptionId("webhook-1")).thenReturn(Optional.of(entity));

            // When
            ResponseEntity<WebhookResponse> response = controller.get("webhook-1", "default");

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("webhook-1", response.getBody().subscriptionId());
            assertEquals("https://example.com/webhook", response.getBody().callbackUrl());
        }

        @Test
        @DisplayName("should return 404 when webhook not found")
        void shouldReturn404WhenNotFound() {
            // Given
            when(subscriptionRepository.findBySubscriptionId("webhook-99")).thenReturn(Optional.empty());

            // When
            ResponseEntity<WebhookResponse> response = controller.get("webhook-99", "default");

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
}
