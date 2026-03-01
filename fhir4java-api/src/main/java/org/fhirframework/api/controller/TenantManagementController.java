package org.fhirframework.api.controller;

import org.fhirframework.api.dto.TenantDTO;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.service.TenantManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for tenant administration.
 * <p>
 * Provides CRUD endpoints for managing tenant records. These endpoints
 * are outside the FHIR path and do not require the X-Tenant-ID header.
 * In production, they should be secured with admin-only access.
 * </p>
 */
@RestController
@RequestMapping(path = "/api/admin/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
public class TenantManagementController {

    private static final Logger log = LoggerFactory.getLogger(TenantManagementController.class);

    private final TenantManagementService tenantManagementService;

    public TenantManagementController(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    @GetMapping
    public ResponseEntity<List<TenantDTO>> listTenants(
            @RequestParam(name = "enabledOnly", required = false, defaultValue = "false") boolean enabledOnly) {
        log.debug("GET /api/admin/tenants (enabledOnly={})", enabledOnly);

        List<FhirTenantEntity> tenants = enabledOnly
                ? tenantManagementService.listEnabledTenants()
                : tenantManagementService.listAllTenants();

        List<TenantDTO> dtos = tenants.stream().map(this::toDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDTO> getTenant(@PathVariable Long id) {
        log.debug("GET /api/admin/tenants/{}", id);
        FhirTenantEntity tenant = tenantManagementService.getTenantById(id);
        return ResponseEntity.ok(toDTO(tenant));
    }

    @GetMapping("/by-external-id/{externalId}")
    public ResponseEntity<TenantDTO> getTenantByExternalId(@PathVariable UUID externalId) {
        log.debug("GET /api/admin/tenants/by-external-id/{}", externalId);
        FhirTenantEntity tenant = tenantManagementService.getTenantByExternalId(externalId);
        return ResponseEntity.ok(toDTO(tenant));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantDTO> createTenant(@RequestBody TenantDTO request) {
        log.debug("POST /api/admin/tenants (externalId={}, internalId={})",
                request.getExternalId(), request.getInternalId());

        validateCreateRequest(request);

        FhirTenantEntity entity = toEntity(request);
        FhirTenantEntity created = tenantManagementService.createTenant(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(created));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantDTO> updateTenant(@PathVariable Long id, @RequestBody TenantDTO request) {
        log.debug("PUT /api/admin/tenants/{}", id);

        FhirTenantEntity updates = toEntity(request);
        FhirTenantEntity updated = tenantManagementService.updateTenant(id, updates);
        return ResponseEntity.ok(toDTO(updated));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<TenantDTO> enableTenant(@PathVariable Long id) {
        log.debug("POST /api/admin/tenants/{}/enable", id);
        FhirTenantEntity enabled = tenantManagementService.enableTenant(id);
        return ResponseEntity.ok(toDTO(enabled));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<TenantDTO> disableTenant(@PathVariable Long id) {
        log.debug("POST /api/admin/tenants/{}/disable", id);
        FhirTenantEntity disabled = tenantManagementService.disableTenant(id);
        return ResponseEntity.ok(toDTO(disabled));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        log.debug("DELETE /api/admin/tenants/{}", id);
        tenantManagementService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    private void validateCreateRequest(TenantDTO request) {
        if (request.getExternalId() == null) {
            throw new IllegalArgumentException("externalId is required");
        }
        if (request.getInternalId() == null || request.getInternalId().isBlank()) {
            throw new IllegalArgumentException("internalId is required");
        }
    }

    private TenantDTO toDTO(FhirTenantEntity entity) {
        return new TenantDTO(
                entity.getId(),
                entity.getExternalId(),
                entity.getInternalId(),
                entity.getTenantCode(),
                entity.getTenantName(),
                entity.getDescription(),
                entity.getEnabled(),
                entity.getSettings(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FhirTenantEntity toEntity(TenantDTO dto) {
        return FhirTenantEntity.builder()
                .externalId(dto.getExternalId())
                .internalId(dto.getInternalId())
                .tenantCode(dto.getTenantCode())
                .tenantName(dto.getTenantName())
                .description(dto.getDescription())
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .settings(dto.getSettings())
                .build();
    }
}
