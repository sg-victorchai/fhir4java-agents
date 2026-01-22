-- FHIR4Java Database Schema V1
-- Creates the core schema for FHIR R5 resource storage
-- This migration is managed by Flyway

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================
-- Core Resource Storage Table
-- Uses JSONB for flexible FHIR resource storage
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_resource (
    -- Primary identifiers
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,

    -- Versioning
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- FHIR Resource Content (JSONB)
    content JSONB NOT NULL,

    -- Metadata
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Source tracking
    source_uri VARCHAR(2048),

    -- Tenant support (for multi-tenancy)
    tenant_id VARCHAR(64) DEFAULT 'default',

    -- Constraints
    CONSTRAINT uk_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

-- Partial unique index for current resources
CREATE UNIQUE INDEX IF NOT EXISTS uk_current_resource
    ON fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

-- ============================================
-- Indexes for Performance
-- ============================================

-- Resource lookup indexes
CREATE INDEX IF NOT EXISTS idx_resource_type ON fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_resource_id ON fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_resource_type_id ON fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_resource_current ON fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_resource_tenant ON fhir_resource(tenant_id);

-- Date indexes for _lastUpdated searches
CREATE INDEX IF NOT EXISTS idx_resource_last_updated ON fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_resource_type_last_updated ON fhir_resource(resource_type, last_updated);

-- JSONB indexes for common search paths
CREATE INDEX IF NOT EXISTS idx_resource_content ON fhir_resource USING GIN (content jsonb_path_ops);

-- ============================================
-- Resource History Table (for versioning)
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL, -- CREATE, UPDATE, DELETE
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),

    CONSTRAINT uk_resource_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_history_resource ON fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_history_changed_at ON fhir_resource_history(changed_at);

-- ============================================
-- Search Parameter Index Table
-- Pre-computed indexes for FHIR search parameters
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    -- Search parameter identification
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL, -- string, token, reference, date, number, quantity, uri, composite

    -- Indexed values (nullable based on param_type)
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048), -- for case-insensitive search
    value_token_system VARCHAR(2048),
    value_token_code VARCHAR(256),
    value_reference_type VARCHAR(100),
    value_reference_id VARCHAR(64),
    value_date_start TIMESTAMP WITH TIME ZONE,
    value_date_end TIMESTAMP WITH TIME ZONE,
    value_number DECIMAL(19, 6),
    value_quantity_value DECIMAL(19, 6),
    value_quantity_unit VARCHAR(100),
    value_quantity_system VARCHAR(2048),
    value_quantity_code VARCHAR(100),
    value_uri VARCHAR(2048),

    -- Composite support
    composite_part INTEGER
);

-- Search indexes
CREATE INDEX IF NOT EXISTS idx_search_resource ON fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_search_type_param ON fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_search_tenant_type ON fhir_search_index(tenant_id, resource_type);

-- String search indexes
CREATE INDEX IF NOT EXISTS idx_search_string ON fhir_search_index(resource_type, param_name, value_string_lower)
    WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_search_string_trgm ON fhir_search_index USING GIN (value_string_lower gin_trgm_ops)
    WHERE param_type = 'string';

-- Token search indexes
CREATE INDEX IF NOT EXISTS idx_search_token ON fhir_search_index(resource_type, param_name, value_token_system, value_token_code)
    WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_search_token_code ON fhir_search_index(resource_type, param_name, value_token_code)
    WHERE param_type = 'token';

-- Reference search indexes
CREATE INDEX IF NOT EXISTS idx_search_reference ON fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id)
    WHERE param_type = 'reference';

-- Date search indexes
CREATE INDEX IF NOT EXISTS idx_search_date ON fhir_search_index(resource_type, param_name, value_date_start, value_date_end)
    WHERE param_type = 'date';

-- Number/Quantity search indexes
CREATE INDEX IF NOT EXISTS idx_search_number ON fhir_search_index(resource_type, param_name, value_number)
    WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_search_quantity ON fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code)
    WHERE param_type = 'quantity';

-- URI search indexes
CREATE INDEX IF NOT EXISTS idx_search_uri ON fhir_search_index(resource_type, param_name, value_uri)
    WHERE param_type = 'uri';

-- ============================================
-- Tag/Security/Profile Storage
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL, -- tag, security, profile
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_tag_resource ON fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_tag_lookup ON fhir_resource_tag(tag_type, system_uri, code);

-- ============================================
-- Compartment Membership
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL, -- Patient, Encounter, Practitioner, etc.
    compartment_id VARCHAR(64) NOT NULL,

    CONSTRAINT uk_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_compartment_lookup ON fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_compartment_resource ON fhir_compartment(resource_id);

-- ============================================
-- Audit Log Table
-- ============================================

CREATE TABLE IF NOT EXISTS fhir_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Operation details
    operation VARCHAR(50) NOT NULL, -- read, vread, create, update, delete, search, etc.
    resource_type VARCHAR(100),
    resource_id VARCHAR(64),
    version_id INTEGER,

    -- Request details
    request_id VARCHAR(64),
    request_method VARCHAR(10),
    request_uri VARCHAR(2048),

    -- Actor
    user_id VARCHAR(256),
    user_name VARCHAR(256),
    client_ip VARCHAR(45),

    -- Outcome
    outcome VARCHAR(20) NOT NULL, -- success, minor_failure, serious_failure, major_failure
    outcome_description TEXT,

    -- Additional context
    tenant_id VARCHAR(64) DEFAULT 'default',
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON fhir_audit_log(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON fhir_audit_log(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON fhir_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant ON fhir_audit_log(tenant_id);

-- ============================================
-- Functions
-- ============================================

-- Function to update last_updated timestamp
CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for auto-updating last_updated
CREATE TRIGGER trg_resource_last_updated
    BEFORE UPDATE ON fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- Function to get next version
CREATE OR REPLACE FUNCTION get_next_version(
    p_tenant_id VARCHAR,
    p_resource_type VARCHAR,
    p_resource_id VARCHAR
)
RETURNS INTEGER AS $$
DECLARE
    v_max_version INTEGER;
BEGIN
    SELECT COALESCE(MAX(version_id), 0) INTO v_max_version
    FROM fhir_resource
    WHERE tenant_id = p_tenant_id
      AND resource_type = p_resource_type
      AND resource_id = p_resource_id;

    RETURN v_max_version + 1;
END;
$$ LANGUAGE plpgsql;
