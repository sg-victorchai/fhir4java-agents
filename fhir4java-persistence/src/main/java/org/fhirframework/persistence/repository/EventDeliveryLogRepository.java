package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.EventDeliveryLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for event delivery log operations.
 * <p>
 * Provides methods for tracking event delivery, supporting the database-backed
 * retry mechanism with exponential backoff.
 * </p>
 */
@Repository
public interface EventDeliveryLogRepository extends JpaRepository<EventDeliveryLogEntity, Long> {

    /**
     * Find deliveries that are ready for retry.
     * <p>
     * Returns deliveries where:
     * <ul>
     *   <li>Status is PENDING or FAILED</li>
     *   <li>Retry count is less than max retries</li>
     *   <li>Next retry time has passed or is null</li>
     * </ul>
     * </p>
     *
     * @param now     the current timestamp
     * @param limit   maximum number of deliveries to return
     * @return list of retryable deliveries
     */
    @Query("SELECT d FROM EventDeliveryLogEntity d " +
            "WHERE (d.status = 'PENDING' OR d.status = 'FAILED') " +
            "AND d.retryCount < d.maxRetries " +
            "AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) " +
            "ORDER BY d.nextRetryAt ASC NULLS FIRST, d.createdAt ASC")
    List<EventDeliveryLogEntity> findRetryableDeliveries(@Param("now") Instant now, Pageable limit);

    /**
     * Find a delivery by event ID and subscription ID.
     *
     * @param eventId        the event ID
     * @param subscriptionId the subscription ID
     * @return the delivery if found
     */
    Optional<EventDeliveryLogEntity> findByEventIdAndSubscriptionId(String eventId, String subscriptionId);

    /**
     * Find all deliveries for a specific event.
     *
     * @param eventId the event ID
     * @return list of deliveries for the event
     */
    List<EventDeliveryLogEntity> findByEventId(String eventId);

    /**
     * Find all deliveries for a specific subscription.
     *
     * @param subscriptionId the subscription ID
     * @param pageable       pagination parameters
     * @return page of deliveries for the subscription
     */
    Page<EventDeliveryLogEntity> findBySubscriptionIdOrderByCreatedAtDesc(
            String subscriptionId, Pageable pageable);

    /**
     * Find deliveries by status.
     *
     * @param status   the delivery status
     * @param pageable pagination parameters
     * @return page of deliveries with the given status
     */
    Page<EventDeliveryLogEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * Find dead-letter deliveries.
     *
     * @param pageable pagination parameters
     * @return page of dead-letter deliveries
     */
    @Query("SELECT d FROM EventDeliveryLogEntity d " +
            "WHERE d.status = 'DEAD_LETTER' " +
            "ORDER BY d.createdAt DESC")
    Page<EventDeliveryLogEntity> findDeadLetterDeliveries(Pageable pageable);

    /**
     * Find unacknowledged deliveries that were delivered.
     *
     * @param since    only return deliveries after this timestamp
     * @param pageable pagination parameters
     * @return page of unacknowledged deliveries
     */
    @Query("SELECT d FROM EventDeliveryLogEntity d " +
            "WHERE d.status = 'DELIVERED' " +
            "AND d.acknowledged = false " +
            "AND d.deliveredAt > :since " +
            "ORDER BY d.deliveredAt DESC")
    Page<EventDeliveryLogEntity> findUnacknowledgedDeliveries(
            @Param("since") Instant since, Pageable pageable);

    /**
     * Find deliveries by resource type and resource ID.
     *
     * @param resourceType the resource type
     * @param resourceId   the resource ID
     * @param pageable     pagination parameters
     * @return page of deliveries for the resource
     */
    Page<EventDeliveryLogEntity> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType, String resourceId, Pageable pageable);

    /**
     * Find deliveries by tenant ID.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination parameters
     * @return page of deliveries for the tenant
     */
    Page<EventDeliveryLogEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Count deliveries by status.
     *
     * @param status the delivery status
     * @return count of deliveries with the given status
     */
    long countByStatus(String status);

    /**
     * Count deliveries by status and subscription ID.
     *
     * @param status         the delivery status
     * @param subscriptionId the subscription ID
     * @return count of matching deliveries
     */
    long countByStatusAndSubscriptionId(String status, String subscriptionId);

    /**
     * Delete deliveries older than a given timestamp.
     * <p>
     * Used for cleanup of old delivery logs.
     * </p>
     *
     * @param before timestamp to delete records before
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM EventDeliveryLogEntity d WHERE d.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);

    /**
     * Delete dead-letter deliveries older than a given timestamp.
     *
     * @param before timestamp to delete records before
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM EventDeliveryLogEntity d " +
            "WHERE d.status = 'DEAD_LETTER' AND d.createdAt < :before")
    int deleteDeadLetterOlderThan(@Param("before") Instant before);

    /**
     * Update delivery status to dead letter for deliveries that have exceeded max retries.
     *
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE EventDeliveryLogEntity d " +
            "SET d.status = 'DEAD_LETTER', d.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE d.status = 'FAILED' AND d.retryCount >= d.maxRetries")
    int markExceededRetriesAsDeadLetter();

    /**
     * Acknowledge a delivery by event ID and subscription ID.
     *
     * @param eventId          the event ID
     * @param subscriptionId   the subscription ID
     * @param acknowledgementId the acknowledgement ID
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE EventDeliveryLogEntity d " +
            "SET d.acknowledged = true, " +
            "    d.acknowledgedAt = CURRENT_TIMESTAMP, " +
            "    d.acknowledgementId = :ackId, " +
            "    d.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE d.eventId = :eventId AND d.subscriptionId = :subscriptionId")
    int acknowledgeDelivery(
            @Param("eventId") String eventId,
            @Param("subscriptionId") String subscriptionId,
            @Param("ackId") String acknowledgementId);
}
