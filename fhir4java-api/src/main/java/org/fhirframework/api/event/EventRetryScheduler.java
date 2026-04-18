package org.fhirframework.api.event;

import org.fhirframework.persistence.entity.EventDeliveryLogEntity;
import org.fhirframework.persistence.repository.EventDeliveryLogRepository;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job for processing event delivery retries.
 * <p>
 * Polls the database for retryable deliveries and attempts redelivery
 * using exponential backoff.
 * </p>
 * <p>
 * This component is only active when:
 * <ul>
 *   <li>{@code fhir4java.events.persistence.enabled=true}</li>
 *   <li>{@code fhir4java.events.retry.enabled=true}</li>
 * </ul>
 * </p>
 */
@Component
@ConditionalOnProperty(name = "fhir4java.events.persistence.enabled", havingValue = "true")
public class EventRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventRetryScheduler.class);

    private final EventDeliveryLogRepository deliveryLogRepository;
    private final EventSubscriptionRepository subscriptionRepository;
    private final EventDeliveryService deliveryService;
    private final EventsConfig eventsConfig;

    public EventRetryScheduler(
            EventDeliveryLogRepository deliveryLogRepository,
            EventSubscriptionRepository subscriptionRepository,
            EventDeliveryService deliveryService,
            EventsConfig eventsConfig) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryService = deliveryService;
        this.eventsConfig = eventsConfig;
        log.info("EventRetryScheduler initialized");
    }

    /**
     * Process retryable deliveries at the configured poll interval.
     * <p>
     * This method is called periodically by Spring's scheduling framework.
     * The interval is configured via {@code fhir4java.events.retry.poll-interval-ms}.
     * </p>
     */
    @Scheduled(fixedRateString = "${fhir4java.events.retry.poll-interval-ms:5000}")
    public void processRetries() {
        if (!eventsConfig.isRetryEnabled()) {
            return;
        }

        try {
            List<EventDeliveryLogEntity> retryableDeliveries = findRetryableDeliveries();

            if (retryableDeliveries.isEmpty()) {
                log.trace("No retryable deliveries found");
                return;
            }

            log.debug("Found {} retryable deliveries", retryableDeliveries.size());

            for (EventDeliveryLogEntity delivery : retryableDeliveries) {
                processRetry(delivery);
            }

        } catch (Exception e) {
            log.error("Error processing retries: {}", e.getMessage(), e);
        }
    }

    /**
     * Find deliveries that are ready for retry.
     *
     * @return List of retryable delivery logs
     */
    private List<EventDeliveryLogEntity> findRetryableDeliveries() {
        return deliveryLogRepository.findRetryableDeliveries(
                Instant.now(),
                PageRequest.of(0, eventsConfig.getRetryBatchSize()));
    }

    /**
     * Process a single retry attempt.
     *
     * @param delivery The delivery log to retry
     */
    private void processRetry(EventDeliveryLogEntity delivery) {
        log.debug("Processing retry {} for event {} subscription {} (attempt {})",
                delivery.getId(), delivery.getEventId(),
                delivery.getSubscriptionId(), delivery.getRetryCount() + 1);

        try {
            boolean success = deliveryService.retryDelivery(delivery);

            if (success) {
                log.info("Retry successful for event {} to subscription {}",
                        delivery.getEventId(), delivery.getSubscriptionId());
            } else {
                log.debug("Retry failed for event {} to subscription {} (attempt {} of {})",
                        delivery.getEventId(), delivery.getSubscriptionId(),
                        delivery.getRetryCount(), delivery.getMaxRetries());
            }

        } catch (Exception e) {
            log.error("Exception during retry for event {}: {}",
                    delivery.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Mark expired deliveries as dead letter.
     * <p>
     * Called periodically to clean up deliveries that have exceeded their retry limit
     * but weren't properly marked.
     * </p>
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void markExpiredDeliveries() {
        if (!eventsConfig.isRetryEnabled()) {
            return;
        }

        try {
            int updated = deliveryLogRepository.markExceededRetriesAsDeadLetter();
            if (updated > 0) {
                log.info("Marked {} deliveries as dead letter (exceeded max retries)", updated);
            }
        } catch (Exception e) {
            log.error("Error marking expired deliveries: {}", e.getMessage(), e);
        }
    }

    /**
     * Expire subscriptions that have passed their expiration time.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void expireSubscriptions() {
        try {
            var expiredSubscriptions = subscriptionRepository.findExpiredSubscriptions(Instant.now());
            for (var subscription : expiredSubscriptions) {
                subscription.setStatus(SubscriptionStatus.EXPIRED.getValue());
                subscriptionRepository.save(subscription);
                log.info("Expired subscription: {}", subscription.getSubscriptionId());
            }
        } catch (Exception e) {
            log.error("Error expiring subscriptions: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup old delivery logs (runs daily).
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    public void cleanupOldDeliveryLogs() {
        try {
            // Delete delivery logs older than 30 days
            int deleted = deliveryService.cleanupOldDeliveryLogs(30);
            if (deleted > 0) {
                log.info("Cleaned up {} old delivery logs", deleted);
            }
        } catch (Exception e) {
            log.error("Error cleaning up old delivery logs: {}", e.getMessage(), e);
        }
    }

    /**
     * Get retry statistics for monitoring.
     *
     * @return Retry statistics including pending, failed, and dead letter counts
     */
    public RetryStats getRetryStats() {
        long pending = deliveryLogRepository.countByStatus(DeliveryStatus.PENDING.getValue());
        long failed = deliveryLogRepository.countByStatus(DeliveryStatus.FAILED.getValue());
        long deadLetter = deliveryLogRepository.countByStatus(DeliveryStatus.DEAD_LETTER.getValue());
        long delivered = deliveryLogRepository.countByStatus(DeliveryStatus.DELIVERED.getValue());

        return new RetryStats(pending, failed, deadLetter, delivered);
    }

    /**
     * Statistics for retry monitoring.
     *
     * @param pending    Count of pending deliveries
     * @param failed     Count of failed deliveries awaiting retry
     * @param deadLetter Count of dead letter deliveries
     * @param delivered  Count of successfully delivered events
     */
    public record RetryStats(long pending, long failed, long deadLetter, long delivered) {
        /**
         * Get total deliveries in progress (pending + failed).
         */
        public long inProgress() {
            return pending + failed;
        }

        /**
         * Get total completed deliveries (delivered + dead letter).
         */
        public long completed() {
            return delivered + deadLetter;
        }
    }
}
