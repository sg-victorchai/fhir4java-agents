package org.fhirframework.api.subscription;

import org.fhirframework.api.event.SubscriptionStatus;
import org.fhirframework.api.event.SubscriptionType;
import org.fhirframework.persistence.entity.EventSubscriptionEntity;
import org.fhirframework.persistence.repository.EventSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing persistent event subscriptions.
 * <p>
 * Provides CRUD operations for subscriptions across all types:
 * SSE, FHIR Subscriptions, and Webhooks.
 * </p>
 */
@Service
public class SubscriptionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPersistenceService.class);

    private final EventSubscriptionRepository subscriptionRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public SubscriptionPersistenceService(EventSubscriptionRepository subscriptionRepository,
                                          @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.transactionTemplate = transactionManager != null
                ? new TransactionTemplate(transactionManager)
                : null;
    }

    /**
     * Create a new SSE subscription.
     *
     * @param tenantId The tenant ID
     * @param topics   Comma-separated list of topics (resource types)
     * @param actions  Comma-separated list of actions
     * @return The created subscription
     */
    @Transactional
    public EventSubscriptionEntity createSseSubscription(
            String tenantId, String topics, String actions) {

        String subscriptionId = UUID.randomUUID().toString();

        EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .subscriptionType(SubscriptionType.SSE.getValue())
                .tenantId(tenantId != null ? tenantId : "default")
                .subscriberName("SSE Client")
                .topics(topics)
                .actions(actions)
                .status(SubscriptionStatus.ACTIVE.getValue())
                .enabled(true)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created SSE subscription: {}", subscriptionId);
        return subscription;
    }

    /**
     * Create a new webhook subscription.
     *
     * @param tenantId    The tenant ID
     * @param endpoint    The webhook callback URL
     * @param topics      Comma-separated list of topics
     * @param actions     Comma-separated list of actions
     * @param secret      Optional HMAC secret for signature verification
     * @param expiresAt   Optional expiration timestamp
     * @param name        Optional subscriber name
     * @return The created subscription
     */
    @Transactional
    public EventSubscriptionEntity createWebhookSubscription(
            String tenantId,
            String endpoint,
            String topics,
            String actions,
            String secret,
            Instant expiresAt,
            String name) {

        String subscriptionId = UUID.randomUUID().toString();

        EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .subscriptionType(SubscriptionType.WEBHOOK.getValue())
                .tenantId(tenantId != null ? tenantId : "default")
                .subscriberName(name != null ? name : "Webhook Subscriber")
                .subscriberEndpoint(endpoint)
                .topics(topics)
                .actions(actions)
                .secret(secret)
                .expiresAt(expiresAt)
                .status(SubscriptionStatus.ACTIVE.getValue())
                .enabled(true)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created webhook subscription: {} for endpoint: {}", subscriptionId, endpoint);
        return subscription;
    }

    /**
     * Create a FHIR Subscription.
     *
     * @param subscriptionId The FHIR resource ID for the Subscription
     * @param tenantId       The tenant ID
     * @param endpoint       The rest-hook endpoint URL
     * @param topics         Comma-separated list of topics
     * @param filterCriteria JSON filter criteria
     * @param expiresAt      Optional expiration timestamp
     * @return The created subscription
     */
    @Transactional
    public EventSubscriptionEntity createFhirSubscription(
            String subscriptionId,
            String tenantId,
            String endpoint,
            String topics,
            Map<String, Object> filterCriteria,
            Instant expiresAt) {

        EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .subscriptionType(SubscriptionType.FHIR_SUBSCRIPTION.getValue())
                .tenantId(tenantId != null ? tenantId : "default")
                .subscriberName("FHIR Subscription")
                .subscriberEndpoint(endpoint)
                .topics(topics)
                .filterCriteria(filterCriteria)
                .expiresAt(expiresAt)
                .status(SubscriptionStatus.ACTIVE.getValue())
                .enabled(true)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created FHIR subscription: {} for endpoint: {}", subscriptionId, endpoint);
        return subscription;
    }

    /**
     * Find a subscription by its ID.
     *
     * @param subscriptionId The subscription ID
     * @return The subscription if found
     */
    public Optional<EventSubscriptionEntity> findBySubscriptionId(String subscriptionId) {
        return subscriptionRepository.findBySubscriptionId(subscriptionId);
    }

    /**
     * Find all active subscriptions of a specific type.
     *
     * @param type The subscription type
     * @return List of active subscriptions
     */
    public List<EventSubscriptionEntity> findActiveByType(SubscriptionType type) {
        return subscriptionRepository.findActiveByType(type.getValue());
    }

    /**
     * Find all active subscriptions for a tenant.
     *
     * @param tenantId The tenant ID
     * @return List of active subscriptions for the tenant
     */
    public List<EventSubscriptionEntity> findActiveByTenant(String tenantId) {
        return subscriptionRepository.findActiveByTenant(tenantId);
    }

    /**
     * Update subscription status.
     *
     * @param subscriptionId The subscription ID
     * @param status         The new status
     * @return true if updated
     */
    @Transactional
    public boolean updateStatus(String subscriptionId, SubscriptionStatus status) {
        int updated = subscriptionRepository.updateStatus(subscriptionId, status.getValue());
        if (updated > 0) {
            log.info("Updated subscription {} status to {}", subscriptionId, status);
            return true;
        }
        return false;
    }

    /**
     * Pause a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return true if paused
     */
    @Transactional
    public boolean pauseSubscription(String subscriptionId) {
        return updateStatus(subscriptionId, SubscriptionStatus.PAUSED);
    }

    /**
     * Resume a paused subscription.
     *
     * @param subscriptionId The subscription ID
     * @return true if resumed
     */
    @Transactional
    public boolean resumeSubscription(String subscriptionId) {
        return updateStatus(subscriptionId, SubscriptionStatus.ACTIVE);
    }

    /**
     * Terminate a subscription.
     * <p>
     * Uses programmatic transaction management to ensure proper transaction
     * handling when called from reactive contexts (e.g., SSE disconnect callbacks).
     * </p>
     *
     * @param subscriptionId The subscription ID
     * @return true if terminated
     */
    public boolean terminateSubscription(String subscriptionId) {
        if (subscriptionId == null) {
            return false;
        }

        // Use programmatic transaction for reactive context compatibility
        if (transactionTemplate != null) {
            Boolean result = transactionTemplate.execute(status -> {
                int updated = subscriptionRepository.updateStatus(
                        subscriptionId, SubscriptionStatus.TERMINATED.getValue());
                if (updated > 0) {
                    log.info("Terminated subscription: {}", subscriptionId);
                    return true;
                }
                return false;
            });
            return Boolean.TRUE.equals(result);
        }

        // Fallback to direct call (for testing without transaction manager)
        return updateStatus(subscriptionId, SubscriptionStatus.TERMINATED);
    }

    /**
     * Delete a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return true if deleted
     */
    @Transactional
    public boolean deleteSubscription(String subscriptionId) {
        int deleted = subscriptionRepository.deleteBySubscriptionId(subscriptionId);
        if (deleted > 0) {
            log.info("Deleted subscription: {}", subscriptionId);
            return true;
        }
        return false;
    }

    /**
     * Update subscription topics and actions.
     *
     * @param subscriptionId The subscription ID
     * @param topics         New topics (comma-separated)
     * @param actions        New actions (comma-separated)
     * @return The updated subscription if found
     */
    @Transactional
    public Optional<EventSubscriptionEntity> updateSubscription(
            String subscriptionId, String topics, String actions) {

        return subscriptionRepository.findBySubscriptionId(subscriptionId)
                .map(subscription -> {
                    if (topics != null) {
                        subscription.setTopics(topics);
                    }
                    if (actions != null) {
                        subscription.setActions(actions);
                    }
                    return subscriptionRepository.save(subscription);
                });
    }

    /**
     * Record that an event was delivered to a subscription.
     *
     * @param subscriptionId The subscription ID
     */
    @Transactional
    public void recordEventDelivery(String subscriptionId) {
        subscriptionRepository.updateLastEventAt(subscriptionId, Instant.now());
    }

    /**
     * Get count of active subscriptions by type.
     *
     * @param type The subscription type
     * @return Count of active subscriptions
     */
    public long countActiveByType(SubscriptionType type) {
        return subscriptionRepository.countActiveByType(type.getValue());
    }

    /**
     * Check if a subscription exists.
     *
     * @param subscriptionId The subscription ID
     * @return true if exists
     */
    public boolean exists(String subscriptionId) {
        return subscriptionRepository.existsBySubscriptionId(subscriptionId);
    }

    /**
     * Create a subscription from an existing WebhookEntity for migration.
     *
     * @param webhookId   The webhook entity ID
     * @param callbackUrl The callback URL
     * @param topics      The topics
     * @param secret      The HMAC secret
     * @param tenantId    The tenant ID
     * @return The created subscription
     */
    @Transactional
    public EventSubscriptionEntity migrateFromWebhook(
            Long webhookId,
            String callbackUrl,
            String topics,
            String secret,
            String tenantId) {

        // Use webhook ID as part of subscription ID for traceability
        String subscriptionId = "webhook-" + webhookId;

        // Check if already migrated
        if (exists(subscriptionId)) {
            log.debug("Webhook {} already migrated to subscription {}", webhookId, subscriptionId);
            return subscriptionRepository.findBySubscriptionId(subscriptionId).orElse(null);
        }

        EventSubscriptionEntity subscription = EventSubscriptionEntity.builder()
                .subscriptionId(subscriptionId)
                .subscriptionType(SubscriptionType.WEBHOOK.getValue())
                .tenantId(tenantId != null ? tenantId : "default")
                .subscriberName("Migrated Webhook " + webhookId)
                .subscriberEndpoint(callbackUrl)
                .topics(topics)
                .secret(secret)
                .status(SubscriptionStatus.ACTIVE.getValue())
                .enabled(true)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Migrated webhook {} to subscription {}", webhookId, subscriptionId);
        return subscription;
    }
}
