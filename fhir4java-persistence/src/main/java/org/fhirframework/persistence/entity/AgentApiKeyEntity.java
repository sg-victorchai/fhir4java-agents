package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA Entity for API key storage for AI agents.
 * <p>
 * Stores hashed API keys that can be used by AI agents as an alternative
 * to OAuth2 JWT-based authentication. Keys are stored as SHA-256 hashes
 * for security.
 * </p>
 */
@Entity
@Table(name = "agent_api_key", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hash of the API key.
     * Raw API keys are never stored.
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * Name/identifier of the AI agent using this key.
     */
    @Column(name = "agent_name", nullable = false, length = 255)
    private String agentName;

    /**
     * Tenant ID this API key is associated with.
     */
    @Column(name = "tenant_id", length = 64)
    @Builder.Default
    private String tenantId = "default";

    /**
     * Comma-separated SMART on FHIR scopes granted to this key.
     * Example: "fhir:read,fhir:write,patient/*.read"
     */
    @Column(name = "scopes", length = 1024)
    private String scopes;

    /**
     * Whether this API key is enabled.
     * Disabled keys are rejected during authentication.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Expiration timestamp for this API key.
     * Null means the key never expires.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Timestamp when this API key was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this API key was last used for authentication.
     * Updated on each successful authentication.
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Check if this API key is currently valid (enabled and not expired).
     *
     * @return true if the key is valid for authentication
     */
    public boolean isValid() {
        if (!Boolean.TRUE.equals(enabled)) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }
}
