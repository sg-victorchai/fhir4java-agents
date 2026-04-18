-- ============================================================================
-- FHIR4Java Database Migration V6 - Event Delivery Log Table
-- ============================================================================
-- This migration creates the event_delivery_log table for tracking event
-- delivery attempts, supporting the database-backed retry mechanism.
--
-- Features:
-- - Full audit trail of all delivery attempts
-- - Exponential backoff retry scheduling
-- - Dead-letter handling for failed deliveries
-- - Acknowledgement tracking
-- - Event payload storage for retry attempts
-- ============================================================================

-- Create event_delivery_log table
CREATE TABLE IF NOT EXISTS fhir.event_delivery_log (
    id BIGSERIAL PRIMARY KEY,

    -- Event identification
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(64),
    action VARCHAR(20),

    -- Tenant context
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- Subscription reference
    subscription_id VARCHAR(64) NOT NULL,
    subscription_type VARCHAR(20) NOT NULL,

    -- Delivery status: PENDING, DELIVERED, FAILED, DEAD_LETTER
    status VARCHAR(20) NOT NULL,

    -- Delivery result
    delivered_at TIMESTAMP WITH TIME ZONE,
    response_code INTEGER,
    response_body TEXT,
    error_message TEXT,

    -- Retry tracking
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,

    -- Acknowledgement tracking
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledgement_id VARCHAR(64),

    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Full event payload for retry (stored as JSONB for querying)
    event_payload JSONB NOT NULL
);

-- Index for finding retryable deliveries (primary query for retry scheduler)
CREATE INDEX IF NOT EXISTS idx_delivery_log_retryable ON fhir.event_delivery_log(status, next_retry_at)
    WHERE status = 'PENDING' OR status = 'FAILED';

-- Index for event lookup
CREATE INDEX IF NOT EXISTS idx_delivery_log_event_id ON fhir.event_delivery_log(event_id);

-- Index for subscription lookup
CREATE INDEX IF NOT EXISTS idx_delivery_log_subscription ON fhir.event_delivery_log(subscription_id);

-- Index for tenant-aware queries
CREATE INDEX IF NOT EXISTS idx_delivery_log_tenant ON fhir.event_delivery_log(tenant_id);

-- Composite index for finding specific event-subscription delivery
CREATE INDEX IF NOT EXISTS idx_delivery_log_event_subscription ON fhir.event_delivery_log(event_id, subscription_id);

-- Index for finding dead-letter items
CREATE INDEX IF NOT EXISTS idx_delivery_log_dead_letter ON fhir.event_delivery_log(status, created_at)
    WHERE status = 'DEAD_LETTER';

-- Index for acknowledgement tracking
CREATE INDEX IF NOT EXISTS idx_delivery_log_unacknowledged ON fhir.event_delivery_log(acknowledged, delivered_at)
    WHERE acknowledged = FALSE AND status = 'DELIVERED';

-- Index for resource-based queries
CREATE INDEX IF NOT EXISTS idx_delivery_log_resource ON fhir.event_delivery_log(resource_type, resource_id);

-- Index for status queries with timestamp ordering
CREATE INDEX IF NOT EXISTS idx_delivery_log_status_created ON fhir.event_delivery_log(status, created_at DESC);

-- Comments
COMMENT ON TABLE fhir.event_delivery_log IS 'Event delivery tracking for retry mechanism and audit trail';
COMMENT ON COLUMN fhir.event_delivery_log.event_id IS 'Unique identifier for the event';
COMMENT ON COLUMN fhir.event_delivery_log.event_type IS 'Type of event (e.g., resource-change)';
COMMENT ON COLUMN fhir.event_delivery_log.resource_type IS 'FHIR resource type that triggered the event';
COMMENT ON COLUMN fhir.event_delivery_log.resource_id IS 'ID of the resource that triggered the event';
COMMENT ON COLUMN fhir.event_delivery_log.action IS 'Action performed: create, update, delete';
COMMENT ON COLUMN fhir.event_delivery_log.subscription_id IS 'Reference to the subscription this delivery is for';
COMMENT ON COLUMN fhir.event_delivery_log.subscription_type IS 'Type of subscription: SSE, FHIR_SUBSCRIPTION, WEBHOOK';
COMMENT ON COLUMN fhir.event_delivery_log.status IS 'Delivery status: PENDING, DELIVERED, FAILED, DEAD_LETTER';
COMMENT ON COLUMN fhir.event_delivery_log.delivered_at IS 'Timestamp when delivery succeeded';
COMMENT ON COLUMN fhir.event_delivery_log.response_code IS 'HTTP response code for webhook deliveries';
COMMENT ON COLUMN fhir.event_delivery_log.response_body IS 'Response body for debugging (may be truncated)';
COMMENT ON COLUMN fhir.event_delivery_log.error_message IS 'Error message if delivery failed';
COMMENT ON COLUMN fhir.event_delivery_log.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN fhir.event_delivery_log.max_retries IS 'Maximum number of retries allowed';
COMMENT ON COLUMN fhir.event_delivery_log.next_retry_at IS 'Timestamp for next retry attempt (exponential backoff)';
COMMENT ON COLUMN fhir.event_delivery_log.acknowledged IS 'Whether the event was acknowledged by the subscriber';
COMMENT ON COLUMN fhir.event_delivery_log.acknowledged_at IS 'Timestamp when acknowledgement was received';
COMMENT ON COLUMN fhir.event_delivery_log.acknowledgement_id IS 'Client-provided acknowledgement ID';
COMMENT ON COLUMN fhir.event_delivery_log.event_payload IS 'Full event payload stored for retry attempts';
