package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.AgentApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Agent API key operations.
 * <p>
 * Provides methods for looking up API keys by their hash value
 * and managing key lifecycle (enable/disable, expiration).
 * </p>
 */
@Repository
public interface AgentApiKeyRepository extends JpaRepository<AgentApiKeyEntity, Long> {

    /**
     * Find an API key by its SHA-256 hash.
     *
     * @param keyHash the SHA-256 hash of the API key
     * @return the API key entity if found
     */
    Optional<AgentApiKeyEntity> findByKeyHash(String keyHash);

    /**
     * Find all API keys for a specific agent.
     *
     * @param agentName the name of the agent
     * @return list of API keys for the agent
     */
    List<AgentApiKeyEntity> findByAgentName(String agentName);

    /**
     * Find all API keys for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of API keys for the tenant
     */
    List<AgentApiKeyEntity> findByTenantId(String tenantId);

    /**
     * Find all enabled API keys.
     *
     * @return list of enabled API keys
     */
    List<AgentApiKeyEntity> findByEnabledTrue();

    /**
     * Update the last used timestamp for an API key.
     *
     * @param keyHash the SHA-256 hash of the API key
     * @param lastUsedAt the timestamp of last use
     */
    @Modifying
    @Query("UPDATE AgentApiKeyEntity a SET a.lastUsedAt = :lastUsedAt WHERE a.keyHash = :keyHash")
    void updateLastUsedAt(@Param("keyHash") String keyHash, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    /**
     * Check if an API key exists by its hash.
     *
     * @param keyHash the SHA-256 hash of the API key
     * @return true if the key exists
     */
    boolean existsByKeyHash(String keyHash);
}
