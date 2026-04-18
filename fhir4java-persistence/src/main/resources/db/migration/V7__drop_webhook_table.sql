-- ============================================================================
-- FHIR4Java Database Migration V7 - Drop Webhook Table
-- ============================================================================
-- This migration drops the legacy webhook table as it has been replaced by
-- the unified event_subscription table (V5).
--
-- The event_subscription table supports all subscription types:
-- - SSE (Server-Sent Events)
-- - FHIR_SUBSCRIPTION (FHIR R5 Subscriptions)
-- - WEBHOOK (HTTP callbacks)
--
-- All webhook functionality is now handled through event_subscription with
-- subscription_type = 'WEBHOOK'.
-- ============================================================================

-- Drop indexes first
DROP INDEX IF EXISTS fhir.idx_webhook_tenant;
DROP INDEX IF EXISTS fhir.idx_webhook_enabled;
DROP INDEX IF EXISTS fhir.idx_webhook_tenant_enabled;

-- Drop the webhook table
DROP TABLE IF EXISTS fhir.webhook;
