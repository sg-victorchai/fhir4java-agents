-- ============================================================================
-- FHIR4Java Database Migration V3 - Webhook Table
-- ============================================================================
-- This migration creates the webhook table for storing webhook registrations
-- used by AI agents and external systems to receive event notifications.
--
-- Webhooks allow external systems to subscribe to FHIR resource change events
-- and receive HTTP POST notifications with HMAC-signed payloads.
-- ============================================================================

-- Create webhook table
CREATE TABLE IF NOT EXISTS fhir.webhook (
    id BIGSERIAL PRIMARY KEY,

    -- URL to POST event notifications to
    callback_url VARCHAR(512) NOT NULL,

    -- Comma-separated list of topics (e.g., "Patient.create,Patient.update")
    topics VARCHAR(1024),

    -- HMAC secret for signing webhook payloads
    secret VARCHAR(256),

    -- Tenant ID this webhook is associated with
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- Whether this webhook is enabled
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit timestamp
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for finding webhooks by tenant
CREATE INDEX IF NOT EXISTS idx_webhook_tenant ON fhir.webhook(tenant_id);

-- Index for finding enabled webhooks
CREATE INDEX IF NOT EXISTS idx_webhook_enabled ON fhir.webhook(enabled) WHERE enabled = TRUE;

-- Index for combined tenant and enabled lookup
CREATE INDEX IF NOT EXISTS idx_webhook_tenant_enabled ON fhir.webhook(tenant_id, enabled) WHERE enabled = TRUE;

-- Comment on table and columns
COMMENT ON TABLE fhir.webhook IS 'Webhook registrations for event notifications';
COMMENT ON COLUMN fhir.webhook.callback_url IS 'URL to POST event notifications to';
COMMENT ON COLUMN fhir.webhook.topics IS 'Comma-separated list of topics (e.g., Patient.create,Patient.update)';
COMMENT ON COLUMN fhir.webhook.secret IS 'HMAC secret for signing webhook payloads';
COMMENT ON COLUMN fhir.webhook.tenant_id IS 'Tenant ID this webhook is associated with for multi-tenancy';
COMMENT ON COLUMN fhir.webhook.enabled IS 'Whether this webhook is enabled for receiving notifications';
