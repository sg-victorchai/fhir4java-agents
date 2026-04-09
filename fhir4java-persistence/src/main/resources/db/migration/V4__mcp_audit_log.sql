-- ============================================================================
-- FHIR4Java Database Migration V4 - MCP Audit Log Table
-- ============================================================================
-- This migration creates the mcp_audit_log table for recording all MCP
-- (Model Context Protocol) tool invocations made by AI agents.
--
-- Purpose:
-- - Audit trail of all AI agent interactions with the FHIR server
-- - Performance monitoring via latency tracking
-- - Error tracking and debugging support
-- - Usage analytics per agent, tenant, and tool
-- ============================================================================

-- Create MCP audit log table
CREATE TABLE IF NOT EXISTS fhir.mcp_audit_log (
    id BIGSERIAL PRIMARY KEY,

    -- Tenant ID for multi-tenancy support
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- AI agent identifier making the request
    agent_id VARCHAR(100) NOT NULL,

    -- MCP tool name: fhir_discover, fhir_query, fhir_mutate
    tool_name VARCHAR(50) NOT NULL,

    -- Action performed: read, search, create, update, delete
    action VARCHAR(50),

    -- FHIR resource type being operated on
    resource_type VARCHAR(50),

    -- Specific resource ID (null for search/create operations)
    resource_id VARCHAR(64),

    -- Request parameters as JSONB for efficient querying
    request_params JSONB,

    -- Summary of the response (truncated to 500 chars)
    response_summary VARCHAR(500),

    -- Whether the invocation was successful
    success BOOLEAN NOT NULL,

    -- Error message if the invocation failed
    error_message TEXT,

    -- Latency of the invocation in milliseconds
    latency_ms INTEGER,

    -- Timestamp when the invocation occurred
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Optional trace ID for distributed tracing
    trace_id VARCHAR(64)
);

-- ============================================================================
-- Indexes for efficient querying
-- ============================================================================

-- Primary query pattern: tenant + time (for tenant-scoped dashboards)
CREATE INDEX IF NOT EXISTS idx_mcp_audit_tenant_time
    ON fhir.mcp_audit_log(tenant_id, timestamp DESC);

-- Query by agent (for agent-specific audit trails)
CREATE INDEX IF NOT EXISTS idx_mcp_audit_agent
    ON fhir.mcp_audit_log(agent_id, timestamp DESC);

-- Query by tool (for tool usage analytics)
CREATE INDEX IF NOT EXISTS idx_mcp_audit_tool
    ON fhir.mcp_audit_log(tool_name, timestamp DESC);

-- Query for failures (for error monitoring dashboards)
CREATE INDEX IF NOT EXISTS idx_mcp_audit_failures
    ON fhir.mcp_audit_log(tenant_id, success, timestamp DESC)
    WHERE success = FALSE;

-- Query by trace ID (for distributed tracing correlation)
CREATE INDEX IF NOT EXISTS idx_mcp_audit_trace
    ON fhir.mcp_audit_log(trace_id)
    WHERE trace_id IS NOT NULL;

-- ============================================================================
-- Table and column comments
-- ============================================================================

COMMENT ON TABLE fhir.mcp_audit_log IS
    'Audit log for MCP (Model Context Protocol) tool invocations by AI agents';

COMMENT ON COLUMN fhir.mcp_audit_log.tenant_id IS
    'Tenant ID for multi-tenancy; defaults to ''default''';

COMMENT ON COLUMN fhir.mcp_audit_log.agent_id IS
    'Identifier of the AI agent making the MCP request';

COMMENT ON COLUMN fhir.mcp_audit_log.tool_name IS
    'MCP tool name: fhir_discover, fhir_query, or fhir_mutate';

COMMENT ON COLUMN fhir.mcp_audit_log.action IS
    'Specific action within the tool: read, search, create, update, delete';

COMMENT ON COLUMN fhir.mcp_audit_log.resource_type IS
    'FHIR resource type being operated on (e.g., Patient, Observation)';

COMMENT ON COLUMN fhir.mcp_audit_log.resource_id IS
    'Specific FHIR resource ID; null for search/create operations';

COMMENT ON COLUMN fhir.mcp_audit_log.request_params IS
    'Request parameters as JSONB for queryable audit data';

COMMENT ON COLUMN fhir.mcp_audit_log.response_summary IS
    'Brief summary of the response (max 500 chars)';

COMMENT ON COLUMN fhir.mcp_audit_log.success IS
    'Whether the tool invocation completed successfully';

COMMENT ON COLUMN fhir.mcp_audit_log.error_message IS
    'Full error message if the invocation failed';

COMMENT ON COLUMN fhir.mcp_audit_log.latency_ms IS
    'Time taken for the tool invocation in milliseconds';

COMMENT ON COLUMN fhir.mcp_audit_log.timestamp IS
    'When the tool invocation occurred';

COMMENT ON COLUMN fhir.mcp_audit_log.trace_id IS
    'Optional trace ID for correlating logs in distributed tracing';
