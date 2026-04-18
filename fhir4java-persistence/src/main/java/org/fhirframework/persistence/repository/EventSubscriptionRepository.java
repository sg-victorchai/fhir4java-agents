package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for event subscription operations.
 * <p>
 * Provides methods for managing persistent subscriptions across all types:
 * SSE, FHIR Subscriptions, and Webhooks.
 * </p>
 */
@Repository
public interface EventSubscriptionRepository extends JpaRepository<EventSubscriptionEntity, Long> {

    /**
     * Find a subscription by its unique subscription ID.
     *
     * @param subscriptionId the unique subscription ID
     * @return the subscription if found
     */
    Optional<EventSubscriptionEntity> findBySubscriptionId(String subscriptionId);

    /**
     * Find all subscriptions of a specific type with a given status.
     *
     * @param subscriptionType the type (SSE, FHIR_SUBSCRIPTION, WEBHOOK)
     * @param status           the status (ACTIVE, PAUSED, EXPIRED, TERMINATED)
     * @return list of matching subscriptions
     */
    List<EventSubscriptionEntity> findBySubscriptionTypeAndStatus(String subscriptionType, String status);

    /**
     * Find all active and enabled subscriptions of a specific type.
     *
     * @param subscriptionType the subscription type
     * @return list of active subscriptions
     */
    @Query("SELECT s FROM EventSubscriptionEntity s " +
            "WHERE s.subscriptionType = :type " +
            "AND s.status = 'ACTIVE' " +
            "AND s.enabled = true")
    List<EventSubscriptionEntity> findActiveByType(@Param("type") String subscriptionType);

    /**
     * Find all active and enabled subscriptions for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of active subscriptions for the tenant
     */
    @Query("SELECT s FROM EventSubscriptionEntity s " +
            "WHERE s.tenantId = :tenantId " +
            "AND s.status = 'ACTIVE' " +
            "AND s.enabled = true")
    List<EventSubscriptionEntity> findActiveByTenant(@Param("tenantId") String tenantId);

    /**
     * Find all active subscriptions that match a given tenant and subscription type.
     *
     * @param tenantId         the tenant ID
     * @param subscriptionType the subscription type
     * @return list of matching active subscriptions
     */
    @Query("SELECT s FROM EventSubscriptionEntity s " +
            "WHERE s.tenantId = :tenantId " +
            "AND s.subscriptionType = :type " +
            "AND s.status = 'ACTIVE' " +
            "AND s.enabled = true")
    List<EventSubscriptionEntity> findActiveByTenantAndType(
            @Param("tenantId") String tenantId,
            @Param("type") String subscriptionType);

    /**
     * Find all expired subscriptions that need to be terminated.
     *
     * @param now the current timestamp
     * @return list of expired subscriptions
     */
    @Query("SELECT s FROM EventSubscriptionEntity s " +
            "WHERE s.expiresAt IS NOT NULL " +
            "AND s.expiresAt < :now " +
            "AND s.status = 'ACTIVE'")
    List<EventSubscriptionEntity> findExpiredSubscriptions(@Param("now") Instant now);

    /**
     * Find subscriptions by tenant ID.
     *
     * @param tenantId the tenant ID
     * @return list of subscriptions for the tenant
     */
    List<EventSubscriptionEntity> findByTenantId(String tenantId);

    /**
     * Find subscriptions by status.
     *
     * @param status the subscription status
     * @return list of subscriptions with the given status
     */
    List<EventSubscriptionEntity> findByStatus(String status);

    /**
     * Delete a subscription by its subscription ID.
     *
     * @param subscriptionId the subscription ID
     * @return the number of deleted records
     */
    @Modifying
    @Query("DELETE FROM EventSubscriptionEntity s WHERE s.subscriptionId = :subscriptionId")
    int deleteBySubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Update subscription status.
     *
     * @param subscriptionId the subscription ID
     * @param status         the new status
     * @return the number of updated records
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EventSubscriptionEntity s " +
            "SET s.status = :status, s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.subscriptionId = :subscriptionId")
    int updateStatus(@Param("subscriptionId") String subscriptionId, @Param("status") String status);

    /**
     * Update last event timestamp for a subscription.
     *
     * @param subscriptionId the subscription ID
     * @param lastEventAt    the timestamp of the last event
     * @return the number of updated records
     */
    @Modifying
    @Query("UPDATE EventSubscriptionEntity s " +
            "SET s.lastEventAt = :lastEventAt, s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.subscriptionId = :subscriptionId")
    int updateLastEventAt(@Param("subscriptionId") String subscriptionId, @Param("lastEventAt") Instant lastEventAt);

    /**
     * Check if a subscription exists by its subscription ID.
     *
     * @param subscriptionId the subscription ID
     * @return true if the subscription exists
     */
    boolean existsBySubscriptionId(String subscriptionId);

    /**
     * Count active subscriptions by type.
     *
     * @param subscriptionType the subscription type
     * @return count of active subscriptions
     */
    @Query("SELECT COUNT(s) FROM EventSubscriptionEntity s " +
            "WHERE s.subscriptionType = :type " +
            "AND s.status = 'ACTIVE' " +
            "AND s.enabled = true")
    long countActiveByType(@Param("type") String subscriptionType);
}
