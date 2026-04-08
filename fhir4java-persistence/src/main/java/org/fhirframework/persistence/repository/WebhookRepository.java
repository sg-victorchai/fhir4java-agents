package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.WebhookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for webhook operations.
 * <p>
 * Provides methods for finding webhooks by tenant and enabled status
 * for dispatching event notifications.
 * </p>
 */
@Repository
public interface WebhookRepository extends JpaRepository<WebhookEntity, Long> {

    /**
     * Find all webhooks for a specific tenant that are enabled.
     *
     * @param tenantId the tenant ID
     * @param enabled whether the webhook is enabled
     * @return list of matching webhooks
     */
    List<WebhookEntity> findByTenantIdAndEnabled(String tenantId, boolean enabled);

    /**
     * Find all webhooks with a specific enabled status.
     *
     * @param enabled whether the webhook is enabled
     * @return list of matching webhooks
     */
    List<WebhookEntity> findByEnabled(boolean enabled);

    /**
     * Find all webhooks for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of webhooks for the tenant
     */
    List<WebhookEntity> findByTenantId(String tenantId);
}
