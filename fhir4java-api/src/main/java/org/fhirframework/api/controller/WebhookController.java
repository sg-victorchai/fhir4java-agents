package org.fhirframework.api.controller;

import org.fhirframework.api.dto.WebhookRequest;
import org.fhirframework.api.dto.WebhookResponse;
import org.fhirframework.api.event.SubscriptionType;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing webhook registrations.
 * <p>
 * Provides endpoints to register, list, and delete webhooks for
 * receiving FHIR resource change event notifications.
 * </p>
 * <p>
 * Webhooks are stored in the unified event_subscription table with
 * subscription_type = 'WEBHOOK'.
 * </p>
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final EventSubscriptionRepository subscriptionRepository;

    public WebhookController(EventSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Register a new webhook.
     *
     * @param request The webhook registration request
     * @param tenantId The tenant ID from request header (optional)
     * @return The registered webhook details
     */
    @PostMapping
    public ResponseEntity<WebhookResponse> register(
            @RequestBody WebhookRequest request,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Registering webhook for tenant {} with callback URL: {}",
                tenantId, request.callbackUrl());

        // Validate request
        request.validate();

        // Convert topics list to comma-separated string
        String topics = null;
        if (request.topics() != null && !request.topics().isEmpty()) {
            topics = String.join(",", request.topics());
        }

        // Generate unique subscription ID
        String subscriptionId = "webhook-" + UUID.randomUUID().toString();

        // Create entity
        EventSubscriptionEntity entity = EventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .subscriptionType(SubscriptionType.WEBHOOK.name())
                .subscriberEndpoint(request.callbackUrl())
                .topics(topics)
                .secret(request.secret())
                .tenantId(tenantId)
                .enabled(true)
                .status("ACTIVE")
                .build();

        // Save
        EventSubscriptionEntity saved = subscriptionRepository.save(entity);
        log.info("Webhook registered with ID: {}", saved.getSubscriptionId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WebhookResponse.from(saved));
    }

    /**
     * List all webhooks for the current tenant.
     *
     * @param tenantId The tenant ID from request header (optional)
     * @return List of webhooks
     */
    @GetMapping
    public List<WebhookResponse> list(
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.debug("Listing webhooks for tenant {}", tenantId);

        List<EventSubscriptionEntity> entities = subscriptionRepository
                .findActiveByTenantAndType(tenantId, SubscriptionType.WEBHOOK.name());
        return entities.stream()
                .map(WebhookResponse::from)
                .toList();
    }

    /**
     * Get a specific webhook by subscription ID.
     *
     * @param subscriptionId The webhook subscription ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The webhook details or 404 if not found
     */
    @GetMapping("/{subscriptionId}")
    public ResponseEntity<WebhookResponse> get(
            @PathVariable String subscriptionId,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.debug("Getting webhook {} for tenant {}", subscriptionId, tenantId);

        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> SubscriptionType.WEBHOOK.name().equals(entity.getSubscriptionType()))
                .map(entity -> ResponseEntity.ok(WebhookResponse.from(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a webhook by subscription ID.
     *
     * @param subscriptionId The webhook subscription ID
     * @param tenantId The tenant ID from request header (optional)
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @PathVariable String subscriptionId,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Deleting webhook {} for tenant {}", subscriptionId, tenantId);

        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> SubscriptionType.WEBHOOK.name().equals(entity.getSubscriptionType()))
                .map(entity -> {
                    subscriptionRepository.delete(entity);
                    log.info("Webhook {} deleted", subscriptionId);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enable a webhook.
     *
     * @param subscriptionId The webhook subscription ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The updated webhook details or 404 if not found
     */
    @PostMapping("/{subscriptionId}/enable")
    public ResponseEntity<WebhookResponse> enable(
            @PathVariable String subscriptionId,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Enabling webhook {} for tenant {}", subscriptionId, tenantId);

        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> SubscriptionType.WEBHOOK.name().equals(entity.getSubscriptionType()))
                .map(entity -> {
                    entity.setEnabled(true);
                    entity.setStatus("ACTIVE");
                    EventSubscriptionEntity saved = subscriptionRepository.save(entity);
                    return ResponseEntity.ok(WebhookResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Disable a webhook.
     *
     * @param subscriptionId The webhook subscription ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The updated webhook details or 404 if not found
     */
    @PostMapping("/{subscriptionId}/disable")
    public ResponseEntity<WebhookResponse> disable(
            @PathVariable String subscriptionId,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Disabling webhook {} for tenant {}", subscriptionId, tenantId);

        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> SubscriptionType.WEBHOOK.name().equals(entity.getSubscriptionType()))
                .map(entity -> {
                    entity.setEnabled(false);
                    entity.setStatus("PAUSED");
                    EventSubscriptionEntity saved = subscriptionRepository.save(entity);
                    return ResponseEntity.ok(WebhookResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
