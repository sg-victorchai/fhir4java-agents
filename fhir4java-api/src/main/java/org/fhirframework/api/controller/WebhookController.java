package org.fhirframework.api.controller;

import org.fhirframework.api.dto.WebhookRequest;
import org.fhirframework.api.dto.WebhookResponse;
import org.fhirframework.persistence.entity.WebhookEntity;
import org.fhirframework.persistence.repository.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing webhook registrations.
 * <p>
 * Provides endpoints to register, list, and delete webhooks for
 * receiving FHIR resource change event notifications.
 * </p>
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookRepository webhookRepository;

    public WebhookController(WebhookRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
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

        // Create entity
        WebhookEntity entity = WebhookEntity.builder()
                .callbackUrl(request.callbackUrl())
                .topics(topics)
                .secret(request.secret())
                .tenantId(tenantId)
                .enabled(true)
                .build();

        // Save
        WebhookEntity saved = webhookRepository.save(entity);
        log.info("Webhook registered with ID: {}", saved.getId());

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

        List<WebhookEntity> entities = webhookRepository.findByTenantId(tenantId);
        return entities.stream()
                .map(WebhookResponse::from)
                .toList();
    }

    /**
     * Get a specific webhook by ID.
     *
     * @param id The webhook ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The webhook details or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<WebhookResponse> get(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.debug("Getting webhook {} for tenant {}", id, tenantId);

        return webhookRepository.findById(id)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .map(entity -> ResponseEntity.ok(WebhookResponse.from(entity)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a webhook by ID.
     *
     * @param id The webhook ID
     * @param tenantId The tenant ID from request header (optional)
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Deleting webhook {} for tenant {}", id, tenantId);

        return webhookRepository.findById(id)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .map(entity -> {
                    webhookRepository.delete(entity);
                    log.info("Webhook {} deleted", id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enable a webhook.
     *
     * @param id The webhook ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The updated webhook details or 404 if not found
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<WebhookResponse> enable(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Enabling webhook {} for tenant {}", id, tenantId);

        return webhookRepository.findById(id)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .map(entity -> {
                    entity.setEnabled(true);
                    WebhookEntity saved = webhookRepository.save(entity);
                    return ResponseEntity.ok(WebhookResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Disable a webhook.
     *
     * @param id The webhook ID
     * @param tenantId The tenant ID from request header (optional)
     * @return The updated webhook details or 404 if not found
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<WebhookResponse> disable(
            @PathVariable Long id,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "default") String tenantId) {

        log.info("Disabling webhook {} for tenant {}", id, tenantId);

        return webhookRepository.findById(id)
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .map(entity -> {
                    entity.setEnabled(false);
                    WebhookEntity saved = webhookRepository.save(entity);
                    return ResponseEntity.ok(WebhookResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
