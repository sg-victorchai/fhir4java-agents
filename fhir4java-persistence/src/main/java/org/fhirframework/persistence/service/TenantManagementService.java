package org.fhirframework.persistence.service;

import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.persistence.entity.FhirTenantEntity;
import org.fhirframework.persistence.repository.FhirTenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for tenant CRUD management operations.
 * <p>
 * Handles creation, update, enable/disable, and deletion of tenants.
 * Delegates to {@link TenantService} for cache invalidation.
 * </p>
 */
@Service
public class TenantManagementService {

    private static final Logger log = LoggerFactory.getLogger(TenantManagementService.class);

    private final FhirTenantRepository tenantRepository;
    private final TenantService tenantService;

    public TenantManagementService(FhirTenantRepository tenantRepository,
                                   TenantService tenantService) {
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
    }

    public List<FhirTenantEntity> listAllTenants() {
        return tenantRepository.findAll();
    }

    public List<FhirTenantEntity> listEnabledTenants() {
        return tenantRepository.findByEnabledTrue();
    }

    public FhirTenantEntity getTenantByExternalId(UUID externalId) {
        return tenantRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TenantNotFoundException(externalId.toString()));
    }

    public FhirTenantEntity getTenantById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("id=" + id));
    }

    @Transactional
    public FhirTenantEntity createTenant(FhirTenantEntity tenant) {
        if (tenantRepository.existsByExternalId(tenant.getExternalId())) {
            throw new IllegalArgumentException(
                    "Tenant with external ID '" + tenant.getExternalId() + "' already exists");
        }
        if (tenantRepository.existsByInternalId(tenant.getInternalId())) {
            throw new IllegalArgumentException(
                    "Tenant with internal ID '" + tenant.getInternalId() + "' already exists");
        }

        FhirTenantEntity saved = tenantRepository.save(tenant);
        log.info("Created tenant: externalId={}, internalId={}, code={}",
                saved.getExternalId(), saved.getInternalId(), saved.getTenantCode());
        return saved;
    }

    @Transactional
    public FhirTenantEntity updateTenant(Long id, FhirTenantEntity updates) {
        FhirTenantEntity existing = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("id=" + id));

        // Validate uniqueness if external/internal IDs changed
        if (updates.getExternalId() != null && !updates.getExternalId().equals(existing.getExternalId())) {
            if (tenantRepository.existsByExternalId(updates.getExternalId())) {
                throw new IllegalArgumentException(
                        "Tenant with external ID '" + updates.getExternalId() + "' already exists");
            }
            // Invalidate old cache entry
            tenantService.invalidateCache(existing.getExternalId());
            existing.setExternalId(updates.getExternalId());
        }

        if (updates.getInternalId() != null && !updates.getInternalId().equals(existing.getInternalId())) {
            if (tenantRepository.existsByInternalId(updates.getInternalId())) {
                throw new IllegalArgumentException(
                        "Tenant with internal ID '" + updates.getInternalId() + "' already exists");
            }
            existing.setInternalId(updates.getInternalId());
        }

        if (updates.getTenantCode() != null) {
            existing.setTenantCode(updates.getTenantCode());
        }
        if (updates.getTenantName() != null) {
            existing.setTenantName(updates.getTenantName());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getEnabled() != null) {
            existing.setEnabled(updates.getEnabled());
        }
        if (updates.getSettings() != null) {
            existing.setSettings(updates.getSettings());
        }

        FhirTenantEntity saved = tenantRepository.save(existing);
        tenantService.invalidateCache(saved.getExternalId());
        log.info("Updated tenant: id={}, externalId={}", saved.getId(), saved.getExternalId());
        return saved;
    }

    @Transactional
    public FhirTenantEntity enableTenant(Long id) {
        FhirTenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("id=" + id));
        tenant.setEnabled(true);
        FhirTenantEntity saved = tenantRepository.save(tenant);
        tenantService.invalidateCache(saved.getExternalId());
        log.info("Enabled tenant: id={}, externalId={}", saved.getId(), saved.getExternalId());
        return saved;
    }

    @Transactional
    public FhirTenantEntity disableTenant(Long id) {
        FhirTenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("id=" + id));
        tenant.setEnabled(false);
        FhirTenantEntity saved = tenantRepository.save(tenant);
        tenantService.invalidateCache(saved.getExternalId());
        log.info("Disabled tenant: id={}, externalId={}", saved.getId(), saved.getExternalId());
        return saved;
    }

    @Transactional
    public void deleteTenant(Long id) {
        FhirTenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("id=" + id));
        tenantService.invalidateCache(tenant.getExternalId());
        tenantRepository.delete(tenant);
        log.info("Deleted tenant: id={}, externalId={}", tenant.getId(), tenant.getExternalId());
    }
}
