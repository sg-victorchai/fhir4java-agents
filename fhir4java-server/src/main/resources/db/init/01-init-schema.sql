-- ============================================================================
-- FHIR4Java Database Initialization Script
-- ============================================================================
-- This script creates all database objects required for FHIR4Java.
-- It serves as the baseline schema - Flyway migrations will only be used
-- for future schema changes.
--
-- Schemas Created:
--   - fhir: Default schema for core tables and cross-cutting concerns
--   - careplan: Dedicated schema for CarePlan resources
--   - masterdata: Shared schema for Patient, Practitioner, Organization
--   - patientdata: Shared schema for Observation, Condition, Procedure
--   - operationdata: Shared schema for Course, MedicationInventory
-- ============================================================================

-- ============================================================================
-- PART 1: EXTENSIONS
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================================
-- PART 2: SCHEMAS
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS fhir;
CREATE SCHEMA IF NOT EXISTS careplan;
CREATE SCHEMA IF NOT EXISTS masterdata;
CREATE SCHEMA IF NOT EXISTS patientdata;
CREATE SCHEMA IF NOT EXISTS operationdata;

-- Set default search path
SET search_path TO fhir, public;

-- ============================================================================
-- PART 3: HELPER FUNCTIONS (in public schema for cross-schema access)
-- ============================================================================

-- Function to update last_updated timestamp
CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

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
    FROM fhir.fhir_resource
    WHERE tenant_id = p_tenant_id
      AND resource_type = p_resource_type
      AND resource_id = p_resource_id;

    RETURN v_max_version + 1;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- PART 4: FHIR SCHEMA TABLES (Default Schema)
-- ============================================================================

-- 4.1 Core Resource Storage Table
CREATE TABLE IF NOT EXISTS fhir.fhir_resource (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    content JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_uri VARCHAR(2048),
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_fhir_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fhir_current_resource
    ON fhir.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_fhir_resource_type ON fhir.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_id ON fhir.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_type_id ON fhir.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_current ON fhir.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_fhir_resource_tenant ON fhir.fhir_resource(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_fhir_version ON fhir.fhir_resource(fhir_version);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_type_version ON fhir.fhir_resource(resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_tenant_type_version ON fhir.fhir_resource(tenant_id, resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_last_updated ON fhir.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_fhir_resource_type_last_updated ON fhir.fhir_resource(resource_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_fhir_content ON fhir.fhir_resource USING GIN (content jsonb_path_ops);

DROP TRIGGER IF EXISTS trg_fhir_resource_last_updated ON fhir.fhir_resource;
CREATE TRIGGER trg_fhir_resource_last_updated
    BEFORE UPDATE ON fhir.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- 4.2 Resource History Table
CREATE TABLE IF NOT EXISTS fhir.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),
    CONSTRAINT uk_fhir_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_fhir_history_resource ON fhir.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_history_changed_at ON fhir.fhir_resource_history(changed_at);

-- 4.3 Search Parameter Index Table
CREATE TABLE IF NOT EXISTS fhir.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048),
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
    composite_part INTEGER
);

CREATE INDEX IF NOT EXISTS idx_fhir_search_resource ON fhir.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_search_type_param ON fhir.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_fhir_search_tenant_type ON fhir.fhir_search_index(tenant_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_fhir_search_fhir_version ON fhir.fhir_search_index(fhir_version);
CREATE INDEX IF NOT EXISTS idx_fhir_search_type_param_version ON fhir.fhir_search_index(resource_type, param_name, fhir_version);
CREATE INDEX IF NOT EXISTS idx_fhir_search_string ON fhir.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_fhir_search_string_trgm ON fhir.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_fhir_search_token ON fhir.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_fhir_search_token_code ON fhir.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_fhir_search_reference ON fhir.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = 'reference';
CREATE INDEX IF NOT EXISTS idx_fhir_search_date ON fhir.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = 'date';
CREATE INDEX IF NOT EXISTS idx_fhir_search_number ON fhir.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_fhir_search_quantity ON fhir.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = 'quantity';
CREATE INDEX IF NOT EXISTS idx_fhir_search_uri ON fhir.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = 'uri';

-- 4.4 Tag/Security/Profile Storage
CREATE TABLE IF NOT EXISTS fhir.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_fhir_tag_resource ON fhir.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_tag_lookup ON fhir.fhir_resource_tag(tag_type, system_uri, code);

-- 4.5 Compartment Membership
CREATE TABLE IF NOT EXISTS fhir.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES fhir.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,
    compartment_id VARCHAR(64) NOT NULL,
    CONSTRAINT uk_fhir_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_fhir_compartment_lookup ON fhir.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_fhir_compartment_resource ON fhir.fhir_compartment(resource_id);

-- 4.6 Audit Log Table (cross-cutting concern - only in fhir schema)
CREATE TABLE IF NOT EXISTS fhir.fhir_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(64),
    version_id INTEGER,
    request_id VARCHAR(64),
    request_method VARCHAR(10),
    request_uri VARCHAR(2048),
    user_id VARCHAR(256),
    user_name VARCHAR(256),
    client_ip VARCHAR(45),
    outcome VARCHAR(20) NOT NULL,
    outcome_description TEXT,
    tenant_id VARCHAR(64) DEFAULT 'default',
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_fhir_audit_timestamp ON fhir.fhir_audit_log(timestamp);
CREATE INDEX IF NOT EXISTS idx_fhir_audit_resource ON fhir.fhir_audit_log(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_fhir_audit_user ON fhir.fhir_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_fhir_audit_tenant ON fhir.fhir_audit_log(tenant_id);

-- 4.7 Tenant Mapping Table (multi-tenancy support - only in fhir schema)
CREATE TABLE IF NOT EXISTS fhir.fhir_tenant (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,
    internal_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_code VARCHAR(50),
    tenant_name VARCHAR(255),
    description TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    settings JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fhir_tenant_external_id ON fhir.fhir_tenant(external_id);
CREATE INDEX IF NOT EXISTS idx_fhir_tenant_code ON fhir.fhir_tenant(tenant_code);
CREATE INDEX IF NOT EXISTS idx_fhir_tenant_internal_id ON fhir.fhir_tenant(internal_id);

-- Seed default tenant
INSERT INTO fhir.fhir_tenant (external_id, internal_id, tenant_code, tenant_name, enabled)
VALUES ('00000000-0000-0000-0000-000000000000', 'default', 'DEFAULT', 'Default Tenant', true)
ON CONFLICT (internal_id) DO NOTHING;

-- Tenant updated_at trigger
CREATE OR REPLACE FUNCTION fhir.update_tenant_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fhir_tenant_updated_at ON fhir.fhir_tenant;
CREATE TRIGGER trg_fhir_tenant_updated_at
    BEFORE UPDATE ON fhir.fhir_tenant
    FOR EACH ROW
    EXECUTE FUNCTION fhir.update_tenant_updated_at();

-- ============================================================================
-- PART 5: CAREPLAN SCHEMA TABLES (Dedicated Schema)
-- ============================================================================

-- 5.1 Core Resource Storage Table
CREATE TABLE IF NOT EXISTS careplan.fhir_resource (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    content JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_uri VARCHAR(2048),
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_careplan_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_careplan_current_resource
    ON careplan.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_careplan_resource_type ON careplan.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_id ON careplan.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type_id ON careplan.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_current ON careplan.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_careplan_resource_tenant ON careplan.fhir_resource(tenant_id);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_fhir_version ON careplan.fhir_resource(fhir_version);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type_version ON careplan.fhir_resource(resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_tenant_type_version ON careplan.fhir_resource(tenant_id, resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_last_updated ON careplan.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type_last_updated ON careplan.fhir_resource(resource_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_careplan_content ON careplan.fhir_resource USING GIN (content jsonb_path_ops);

DROP TRIGGER IF EXISTS trg_careplan_resource_last_updated ON careplan.fhir_resource;
CREATE TRIGGER trg_careplan_resource_last_updated
    BEFORE UPDATE ON careplan.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- 5.2 Resource History Table
CREATE TABLE IF NOT EXISTS careplan.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),
    CONSTRAINT uk_careplan_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_careplan_history_resource ON careplan.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_history_changed_at ON careplan.fhir_resource_history(changed_at);

-- 5.3 Search Parameter Index Table
CREATE TABLE IF NOT EXISTS careplan.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048),
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
    composite_part INTEGER
);

CREATE INDEX IF NOT EXISTS idx_careplan_search_resource ON careplan.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_search_type_param ON careplan.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_careplan_search_tenant_type ON careplan.fhir_search_index(tenant_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_careplan_search_fhir_version ON careplan.fhir_search_index(fhir_version);
CREATE INDEX IF NOT EXISTS idx_careplan_search_type_param_version ON careplan.fhir_search_index(resource_type, param_name, fhir_version);
CREATE INDEX IF NOT EXISTS idx_careplan_search_string ON careplan.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_careplan_search_string_trgm ON careplan.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_careplan_search_token ON careplan.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_careplan_search_token_code ON careplan.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_careplan_search_reference ON careplan.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = 'reference';
CREATE INDEX IF NOT EXISTS idx_careplan_search_date ON careplan.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = 'date';
CREATE INDEX IF NOT EXISTS idx_careplan_search_number ON careplan.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_careplan_search_quantity ON careplan.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = 'quantity';
CREATE INDEX IF NOT EXISTS idx_careplan_search_uri ON careplan.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = 'uri';

-- 5.4 Tag/Security/Profile Storage
CREATE TABLE IF NOT EXISTS careplan.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_careplan_tag_resource ON careplan.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_tag_lookup ON careplan.fhir_resource_tag(tag_type, system_uri, code);

-- 5.5 Compartment Membership
CREATE TABLE IF NOT EXISTS careplan.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,
    compartment_id VARCHAR(64) NOT NULL,
    CONSTRAINT uk_careplan_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_careplan_compartment_lookup ON careplan.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_careplan_compartment_resource ON careplan.fhir_compartment(resource_id);

-- ============================================================================
-- PART 6: MASTERDATA SCHEMA TABLES (Patient, Practitioner, Organization)
-- ============================================================================

-- 6.1 Core Resource Storage Table
CREATE TABLE IF NOT EXISTS masterdata.fhir_resource (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    content JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_uri VARCHAR(2048),
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_masterdata_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_masterdata_current_resource
    ON masterdata.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_masterdata_resource_type ON masterdata.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_id ON masterdata.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_type_id ON masterdata.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_current ON masterdata.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_tenant ON masterdata.fhir_resource(tenant_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_fhir_version ON masterdata.fhir_resource(fhir_version);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_type_version ON masterdata.fhir_resource(resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_tenant_type_version ON masterdata.fhir_resource(tenant_id, resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_last_updated ON masterdata.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_masterdata_resource_type_last_updated ON masterdata.fhir_resource(resource_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_masterdata_content ON masterdata.fhir_resource USING GIN (content jsonb_path_ops);

DROP TRIGGER IF EXISTS trg_masterdata_resource_last_updated ON masterdata.fhir_resource;
CREATE TRIGGER trg_masterdata_resource_last_updated
    BEFORE UPDATE ON masterdata.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- 6.2 Resource History Table
CREATE TABLE IF NOT EXISTS masterdata.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES masterdata.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),
    CONSTRAINT uk_masterdata_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_masterdata_history_resource ON masterdata.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_history_changed_at ON masterdata.fhir_resource_history(changed_at);

-- 6.3 Search Parameter Index Table
CREATE TABLE IF NOT EXISTS masterdata.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES masterdata.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048),
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
    composite_part INTEGER
);

CREATE INDEX IF NOT EXISTS idx_masterdata_search_resource ON masterdata.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_search_type_param ON masterdata.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_masterdata_search_tenant_type ON masterdata.fhir_search_index(tenant_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_masterdata_search_fhir_version ON masterdata.fhir_search_index(fhir_version);
CREATE INDEX IF NOT EXISTS idx_masterdata_search_type_param_version ON masterdata.fhir_search_index(resource_type, param_name, fhir_version);
CREATE INDEX IF NOT EXISTS idx_masterdata_search_string ON masterdata.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_string_trgm ON masterdata.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_token ON masterdata.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_token_code ON masterdata.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_reference ON masterdata.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = 'reference';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_date ON masterdata.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = 'date';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_number ON masterdata.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_quantity ON masterdata.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = 'quantity';
CREATE INDEX IF NOT EXISTS idx_masterdata_search_uri ON masterdata.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = 'uri';

-- 6.4 Tag/Security/Profile Storage
CREATE TABLE IF NOT EXISTS masterdata.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES masterdata.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_masterdata_tag_resource ON masterdata.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_tag_lookup ON masterdata.fhir_resource_tag(tag_type, system_uri, code);

-- 6.5 Compartment Membership
CREATE TABLE IF NOT EXISTS masterdata.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES masterdata.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,
    compartment_id VARCHAR(64) NOT NULL,
    CONSTRAINT uk_masterdata_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_masterdata_compartment_lookup ON masterdata.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_masterdata_compartment_resource ON masterdata.fhir_compartment(resource_id);

-- ============================================================================
-- PART 7: PATIENTDATA SCHEMA TABLES (Observation, Condition, Procedure)
-- ============================================================================

-- 7.1 Core Resource Storage Table
CREATE TABLE IF NOT EXISTS patientdata.fhir_resource (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    content JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_uri VARCHAR(2048),
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_patientdata_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_patientdata_current_resource
    ON patientdata.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_patientdata_resource_type ON patientdata.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_id ON patientdata.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_type_id ON patientdata.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_current ON patientdata.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_tenant ON patientdata.fhir_resource(tenant_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_fhir_version ON patientdata.fhir_resource(fhir_version);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_type_version ON patientdata.fhir_resource(resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_tenant_type_version ON patientdata.fhir_resource(tenant_id, resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_last_updated ON patientdata.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_patientdata_resource_type_last_updated ON patientdata.fhir_resource(resource_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_patientdata_content ON patientdata.fhir_resource USING GIN (content jsonb_path_ops);

DROP TRIGGER IF EXISTS trg_patientdata_resource_last_updated ON patientdata.fhir_resource;
CREATE TRIGGER trg_patientdata_resource_last_updated
    BEFORE UPDATE ON patientdata.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- 7.2 Resource History Table
CREATE TABLE IF NOT EXISTS patientdata.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES patientdata.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),
    CONSTRAINT uk_patientdata_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_patientdata_history_resource ON patientdata.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_history_changed_at ON patientdata.fhir_resource_history(changed_at);

-- 7.3 Search Parameter Index Table
CREATE TABLE IF NOT EXISTS patientdata.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES patientdata.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048),
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
    composite_part INTEGER
);

CREATE INDEX IF NOT EXISTS idx_patientdata_search_resource ON patientdata.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_search_type_param ON patientdata.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_patientdata_search_tenant_type ON patientdata.fhir_search_index(tenant_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_patientdata_search_fhir_version ON patientdata.fhir_search_index(fhir_version);
CREATE INDEX IF NOT EXISTS idx_patientdata_search_type_param_version ON patientdata.fhir_search_index(resource_type, param_name, fhir_version);
CREATE INDEX IF NOT EXISTS idx_patientdata_search_string ON patientdata.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_string_trgm ON patientdata.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_token ON patientdata.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_token_code ON patientdata.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_reference ON patientdata.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = 'reference';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_date ON patientdata.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = 'date';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_number ON patientdata.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_quantity ON patientdata.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = 'quantity';
CREATE INDEX IF NOT EXISTS idx_patientdata_search_uri ON patientdata.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = 'uri';

-- 7.4 Tag/Security/Profile Storage
CREATE TABLE IF NOT EXISTS patientdata.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES patientdata.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_patientdata_tag_resource ON patientdata.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_tag_lookup ON patientdata.fhir_resource_tag(tag_type, system_uri, code);

-- 7.5 Compartment Membership
CREATE TABLE IF NOT EXISTS patientdata.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES patientdata.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,
    compartment_id VARCHAR(64) NOT NULL,
    CONSTRAINT uk_patientdata_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_patientdata_compartment_lookup ON patientdata.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_patientdata_compartment_resource ON patientdata.fhir_compartment(resource_id);

-- ============================================================================
-- PART 8: OPERATIONDATA SCHEMA TABLES (Course, MedicationInventory)
-- ============================================================================

-- 8.1 Core Resource Storage Table
CREATE TABLE IF NOT EXISTS operationdata.fhir_resource (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    version_id INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    content JSONB NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_uri VARCHAR(2048),
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_operationdata_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_operationdata_current_resource
    ON operationdata.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX IF NOT EXISTS idx_operationdata_resource_type ON operationdata.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_id ON operationdata.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_type_id ON operationdata.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_current ON operationdata.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_tenant ON operationdata.fhir_resource(tenant_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_fhir_version ON operationdata.fhir_resource(fhir_version);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_type_version ON operationdata.fhir_resource(resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_tenant_type_version ON operationdata.fhir_resource(tenant_id, resource_type, fhir_version);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_last_updated ON operationdata.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_operationdata_resource_type_last_updated ON operationdata.fhir_resource(resource_type, last_updated);
CREATE INDEX IF NOT EXISTS idx_operationdata_content ON operationdata.fhir_resource USING GIN (content jsonb_path_ops);

DROP TRIGGER IF EXISTS trg_operationdata_resource_last_updated ON operationdata.fhir_resource;
CREATE TRIGGER trg_operationdata_resource_last_updated
    BEFORE UPDATE ON operationdata.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- 8.2 Resource History Table
CREATE TABLE IF NOT EXISTS operationdata.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES operationdata.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),
    CONSTRAINT uk_operationdata_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_operationdata_history_resource ON operationdata.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_history_changed_at ON operationdata.fhir_resource_history(changed_at);

-- 8.3 Search Parameter Index Table
CREATE TABLE IF NOT EXISTS operationdata.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES operationdata.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5',
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    value_string VARCHAR(2048),
    value_string_lower VARCHAR(2048),
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
    composite_part INTEGER
);

CREATE INDEX IF NOT EXISTS idx_operationdata_search_resource ON operationdata.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_search_type_param ON operationdata.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_operationdata_search_tenant_type ON operationdata.fhir_search_index(tenant_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_operationdata_search_fhir_version ON operationdata.fhir_search_index(fhir_version);
CREATE INDEX IF NOT EXISTS idx_operationdata_search_type_param_version ON operationdata.fhir_search_index(resource_type, param_name, fhir_version);
CREATE INDEX IF NOT EXISTS idx_operationdata_search_string ON operationdata.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_string_trgm ON operationdata.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_token ON operationdata.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_token_code ON operationdata.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_reference ON operationdata.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = 'reference';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_date ON operationdata.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = 'date';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_number ON operationdata.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_quantity ON operationdata.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = 'quantity';
CREATE INDEX IF NOT EXISTS idx_operationdata_search_uri ON operationdata.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = 'uri';

-- 8.4 Tag/Security/Profile Storage
CREATE TABLE IF NOT EXISTS operationdata.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES operationdata.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_operationdata_tag_resource ON operationdata.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_tag_lookup ON operationdata.fhir_resource_tag(tag_type, system_uri, code);

-- 8.5 Compartment Membership
CREATE TABLE IF NOT EXISTS operationdata.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES operationdata.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,
    compartment_id VARCHAR(64) NOT NULL,
    CONSTRAINT uk_operationdata_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_operationdata_compartment_lookup ON operationdata.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_operationdata_compartment_resource ON operationdata.fhir_compartment(resource_id);

-- ============================================================================
-- PART 9: HELPER FUNCTION FOR FUTURE SCHEMA CREATION
-- ============================================================================

CREATE OR REPLACE FUNCTION create_fhir_schema(p_schema_name TEXT)
RETURNS VOID AS $$
BEGIN
    -- Validate schema name (alphanumeric and underscore only)
    IF p_schema_name !~ '^[a-zA-Z_][a-zA-Z0-9_]*$' THEN
        RAISE EXCEPTION 'Invalid schema name: %', p_schema_name;
    END IF;

    -- Create the schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);

    -- Create fhir_resource table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_resource (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_type VARCHAR(100) NOT NULL,
            resource_id VARCHAR(64) NOT NULL,
            fhir_version VARCHAR(10) NOT NULL DEFAULT ''R5'',
            version_id INTEGER NOT NULL DEFAULT 1,
            is_current BOOLEAN NOT NULL DEFAULT TRUE,
            is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
            content JSONB NOT NULL,
            last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            source_uri VARCHAR(2048),
            tenant_id VARCHAR(64) DEFAULT ''default'',
            CONSTRAINT uk_%s_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id)
        )', p_schema_name, p_schema_name);

    -- fhir_resource indexes
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS uk_%s_current_resource ON %I.fhir_resource(tenant_id, resource_type, resource_id) WHERE is_current = TRUE', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type ON %I.fhir_resource(resource_type)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_id ON %I.fhir_resource(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_tenant ON %I.fhir_resource(tenant_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_last_updated ON %I.fhir_resource(last_updated)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_content ON %I.fhir_resource USING GIN (content jsonb_path_ops)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type_last_updated ON %I.fhir_resource(resource_type, last_updated)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_fhir_version ON %I.fhir_resource(fhir_version)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type_version ON %I.fhir_resource(resource_type, fhir_version)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_tenant_type_version ON %I.fhir_resource(tenant_id, resource_type, fhir_version)', p_schema_name, p_schema_name);

    -- fhir_resource trigger
    EXECUTE format('
        CREATE OR REPLACE TRIGGER trg_%s_resource_last_updated
        BEFORE UPDATE ON %I.fhir_resource
        FOR EACH ROW
        EXECUTE FUNCTION update_last_updated()
    ', p_schema_name, p_schema_name);

    -- Create fhir_resource_history table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_resource_history (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            version_id INTEGER NOT NULL,
            fhir_version VARCHAR(10) NOT NULL DEFAULT ''R5'',
            content JSONB NOT NULL,
            operation VARCHAR(20) NOT NULL,
            changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            changed_by VARCHAR(256),
            CONSTRAINT uk_%s_history_version UNIQUE (resource_id, version_id)
        )', p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_history_resource ON %I.fhir_resource_history(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_history_changed_at ON %I.fhir_resource_history(changed_at)', p_schema_name, p_schema_name);

    -- Create fhir_search_index table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_search_index (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            resource_type VARCHAR(100) NOT NULL,
            fhir_version VARCHAR(10) NOT NULL DEFAULT ''R5'',
            tenant_id VARCHAR(64) NOT NULL DEFAULT ''default'',
            param_name VARCHAR(100) NOT NULL,
            param_type VARCHAR(20) NOT NULL,
            value_string VARCHAR(2048),
            value_string_lower VARCHAR(2048),
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
            composite_part INTEGER
        )', p_schema_name, p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_resource ON %I.fhir_search_index(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_type_param ON %I.fhir_search_index(resource_type, param_name)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_tenant_type ON %I.fhir_search_index(tenant_id, resource_type)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_fhir_version ON %I.fhir_search_index(fhir_version)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_type_param_version ON %I.fhir_search_index(resource_type, param_name, fhir_version)', p_schema_name, p_schema_name);
    -- Type-specific search indexes
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_string_trgm ON %I.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = ''string''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_token_code ON %I.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = ''token''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_number ON %I.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = ''number''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_quantity ON %I.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = ''quantity''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_uri ON %I.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = ''uri''', p_schema_name, p_schema_name);

    -- Create fhir_resource_tag table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_resource_tag (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            tag_type VARCHAR(20) NOT NULL,
            system_uri VARCHAR(2048),
            code VARCHAR(256),
            display VARCHAR(1024)
        )', p_schema_name, p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tag_resource ON %I.fhir_resource_tag(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tag_lookup ON %I.fhir_resource_tag(tag_type, system_uri, code)', p_schema_name, p_schema_name);

    -- Create fhir_compartment table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_compartment (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            compartment_type VARCHAR(100) NOT NULL,
            compartment_id VARCHAR(64) NOT NULL,
            CONSTRAINT uk_%s_compartment UNIQUE (resource_id, compartment_type, compartment_id)
        )', p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_compartment_lookup ON %I.fhir_compartment(compartment_type, compartment_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_compartment_resource ON %I.fhir_compartment(resource_id)', p_schema_name, p_schema_name);

    RAISE NOTICE 'Successfully created complete FHIR schema: %', p_schema_name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_fhir_schema(TEXT) IS
'Creates a complete FHIR schema with all required tables.
Usage: SELECT create_fhir_schema(''my_new_schema'');';

-- ============================================================================
-- PART 10: PERMISSIONS
-- ============================================================================

-- Grant permissions on all schemas
GRANT ALL PRIVILEGES ON SCHEMA fhir TO fhir4java;
GRANT ALL PRIVILEGES ON SCHEMA careplan TO fhir4java;
GRANT ALL PRIVILEGES ON SCHEMA masterdata TO fhir4java;
GRANT ALL PRIVILEGES ON SCHEMA patientdata TO fhir4java;
GRANT ALL PRIVILEGES ON SCHEMA operationdata TO fhir4java;
GRANT ALL PRIVILEGES ON SCHEMA public TO fhir4java;

-- Grant permissions on all existing tables
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA fhir TO fhir4java;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA careplan TO fhir4java;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA masterdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA patientdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA operationdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO fhir4java;

-- Grant permissions on all sequences
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA fhir TO fhir4java;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA careplan TO fhir4java;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA masterdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA patientdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA operationdata TO fhir4java;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO fhir4java;

-- Grant default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir GRANT ALL PRIVILEGES ON TABLES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir GRANT EXECUTE ON FUNCTIONS TO fhir4java;

ALTER DEFAULT PRIVILEGES IN SCHEMA careplan GRANT ALL PRIVILEGES ON TABLES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA careplan GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java;

ALTER DEFAULT PRIVILEGES IN SCHEMA masterdata GRANT ALL PRIVILEGES ON TABLES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA masterdata GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java;

ALTER DEFAULT PRIVILEGES IN SCHEMA patientdata GRANT ALL PRIVILEGES ON TABLES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA patientdata GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java;

ALTER DEFAULT PRIVILEGES IN SCHEMA operationdata GRANT ALL PRIVILEGES ON TABLES TO fhir4java;
ALTER DEFAULT PRIVILEGES IN SCHEMA operationdata GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java;

-- Set default search path for the application user
ALTER ROLE fhir4java SET search_path TO fhir, public;

-- ============================================================================
-- PART 11: SCHEMA DOCUMENTATION
-- ============================================================================

COMMENT ON SCHEMA fhir IS 'FHIR4Java default schema - core tables and cross-cutting concerns';
COMMENT ON SCHEMA careplan IS 'FHIR4Java dedicated schema for CarePlan resources';
COMMENT ON SCHEMA masterdata IS 'FHIR4Java shared schema for master data (Patient, Practitioner, Organization)';
COMMENT ON SCHEMA patientdata IS 'FHIR4Java shared schema for patient data (Observation, Condition, Procedure)';
COMMENT ON SCHEMA operationdata IS 'FHIR4Java shared schema for operational data (Course, MedicationInventory)';

-- ============================================================================
-- END OF INITIALIZATION SCRIPT
-- ============================================================================
