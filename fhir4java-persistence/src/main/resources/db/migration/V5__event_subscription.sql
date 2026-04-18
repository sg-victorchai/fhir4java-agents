-- ============================================================================
-- FHIR4Java Database Migration V5 - Event Subscription Table
-- ============================================================================
-- This migration creates the event_subscription table for persistent storage
-- of all subscription types: SSE, FHIR Subscriptions, and Webhooks.
--
-- This enables:
-- - Unified subscription management across all delivery channels
-- - Subscription persistence across server restarts
-- - Tenant-aware subscription filtering
-- - Subscription status tracking and lifecycle management
-- ============================================================================

-- Create event_subscription table
CREATE TABLE IF NOT EXISTS fhir.event_subscription (
    id BIGSERIAL PRIMARY KEY,

    -- Unique identifier for the subscription
    subscription_id VARCHAR(64) NOT NULL UNIQUE,

    -- Type of subscription: SSE, FHIR_SUBSCRIPTION, WEBHOOK
    subscription_type VARCHAR(20) NOT NULL,

    -- Tenant ID for multi-tenancy support
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- Human-readable name for the subscriber
    subscriber_name VARCHAR(255),

    -- Endpoint URL for WEBHOOK and FHIR_SUBSCRIPTION types
    subscriber_endpoint VARCHAR(512),

    -- Comma-separated list of topics (e.g., "Patient,Observation")
    topics TEXT,

    -- Comma-separated list of actions (e.g., "create,update,delete")
    actions TEXT,

    -- JSON filter criteria for advanced filtering
    filter_criteria JSONB,

    -- Subscription status: ACTIVE, PAUSED, EXPIRED, TERMINATED
    status VARCHAR(20) DEFAULT 'ACTIVE',

    -- Whether the subscription is enabled
    enabled BOOLEAN DEFAULT TRUE,

    -- HMAC secret for webhook signature verification
    secret VARCHAR(256),

    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Subscription expiration (optional)
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Last time an event was sent to this subscription
    last_event_at TIMESTAMP WITH TIME ZONE,

    -- Additional metadata as JSON
    metadata JSONB
);

-- Index for subscription lookup by ID
CREATE INDEX IF NOT EXISTS idx_event_subscription_id ON fhir.event_subscription(subscription_id);

-- Index for finding subscriptions by type and status
CREATE INDEX IF NOT EXISTS idx_event_subscription_type_status ON fhir.event_subscription(subscription_type, status);

-- Index for tenant-aware queries
CREATE INDEX IF NOT EXISTS idx_event_subscription_tenant ON fhir.event_subscription(tenant_id);

-- Index for finding active subscriptions
CREATE INDEX IF NOT EXISTS idx_event_subscription_active ON fhir.event_subscription(enabled, status)
    WHERE enabled = TRUE AND status = 'ACTIVE';

-- Index for expiration checks
CREATE INDEX IF NOT EXISTS idx_event_subscription_expires ON fhir.event_subscription(expires_at)
    WHERE expires_at IS NOT NULL;

-- Comments
COMMENT ON TABLE fhir.event_subscription IS 'Persistent storage for all subscription types (SSE, FHIR Subscriptions, Webhooks)';
COMMENT ON COLUMN fhir.event_subscription.subscription_id IS 'Unique identifier for the subscription';
COMMENT ON COLUMN fhir.event_subscription.subscription_type IS 'Type: SSE, FHIR_SUBSCRIPTION, or WEBHOOK';
COMMENT ON COLUMN fhir.event_subscription.tenant_id IS 'Tenant ID for multi-tenancy isolation';
COMMENT ON COLUMN fhir.event_subscription.subscriber_name IS 'Human-readable name for the subscriber';
COMMENT ON COLUMN fhir.event_subscription.subscriber_endpoint IS 'Callback URL for webhooks and FHIR subscriptions';
COMMENT ON COLUMN fhir.event_subscription.topics IS 'Comma-separated resource types to subscribe to';
COMMENT ON COLUMN fhir.event_subscription.actions IS 'Comma-separated actions to subscribe to (create, update, delete)';
COMMENT ON COLUMN fhir.event_subscription.filter_criteria IS 'JSON filter criteria for advanced filtering';
COMMENT ON COLUMN fhir.event_subscription.status IS 'Status: ACTIVE, PAUSED, EXPIRED, TERMINATED';
COMMENT ON COLUMN fhir.event_subscription.secret IS 'HMAC secret for webhook signature verification';
COMMENT ON COLUMN fhir.event_subscription.expires_at IS 'Optional subscription expiration timestamp';
COMMENT ON COLUMN fhir.event_subscription.last_event_at IS 'Last time an event was delivered to this subscription';
COMMENT ON COLUMN fhir.event_subscription.metadata IS 'Additional subscription metadata as JSON';
