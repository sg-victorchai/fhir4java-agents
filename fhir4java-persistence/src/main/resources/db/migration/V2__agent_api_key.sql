-- ============================================================================
-- FHIR4Java Database Migration V2 - Agent API Key Table
-- ============================================================================
-- This migration creates the agent_api_key table for storing API keys
-- used by AI agents as an alternative to OAuth2 JWT-based authentication.
--
-- Security Note: API keys are stored as SHA-256 hashes, never in plain text.
-- ============================================================================

-- Create agent_api_key table
CREATE TABLE IF NOT EXISTS fhir.agent_api_key (
    id BIGSERIAL PRIMARY KEY,

    -- SHA-256 hash of the API key (64 hex characters)
    key_hash VARCHAR(64) NOT NULL UNIQUE,

    -- Name/identifier of the AI agent
    agent_name VARCHAR(255) NOT NULL,

    -- Tenant ID this key is associated with
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- Comma-separated SMART on FHIR scopes
    scopes VARCHAR(1024),

    -- Whether this key is enabled
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Expiration timestamp (NULL = never expires)
    expires_at TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP
);

-- Index for fast lookup by key hash (primary authentication lookup)
CREATE INDEX IF NOT EXISTS idx_agent_api_key_hash ON fhir.agent_api_key(key_hash);

-- Index for finding keys by agent name
CREATE INDEX IF NOT EXISTS idx_agent_api_key_agent_name ON fhir.agent_api_key(agent_name);

-- Index for finding keys by tenant
CREATE INDEX IF NOT EXISTS idx_agent_api_key_tenant ON fhir.agent_api_key(tenant_id);

-- Index for finding enabled keys
CREATE INDEX IF NOT EXISTS idx_agent_api_key_enabled ON fhir.agent_api_key(enabled) WHERE enabled = TRUE;

-- Comment on table and columns
COMMENT ON TABLE fhir.agent_api_key IS 'API keys for AI agent authentication as an alternative to OAuth2 JWT';
COMMENT ON COLUMN fhir.agent_api_key.key_hash IS 'SHA-256 hash of the API key (raw keys are never stored)';
COMMENT ON COLUMN fhir.agent_api_key.agent_name IS 'Name/identifier of the AI agent using this key';
COMMENT ON COLUMN fhir.agent_api_key.tenant_id IS 'Tenant ID this key is associated with for multi-tenancy';
COMMENT ON COLUMN fhir.agent_api_key.scopes IS 'Comma-separated SMART on FHIR scopes granted to this key';
COMMENT ON COLUMN fhir.agent_api_key.enabled IS 'Whether this API key is enabled for authentication';
COMMENT ON COLUMN fhir.agent_api_key.expires_at IS 'Expiration timestamp; NULL means key never expires';
COMMENT ON COLUMN fhir.agent_api_key.last_used_at IS 'Timestamp when this key was last used for authentication';
