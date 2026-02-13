-- FHIR4Java Database Schema V3
-- Creates dedicated schemas for resources configured with schema.type=dedicated
-- This migration creates the careplan schema with FULL TABLE REPLICATION
-- All resource-related tables are replicated in the dedicated schema

-- ============================================
-- Create CarePlan Dedicated Schema
-- ============================================

CREATE SCHEMA IF NOT EXISTS careplan;

-- ============================================
-- 1. Primary Resource Table
-- ============================================

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

-- Partial unique index for current resources
CREATE UNIQUE INDEX IF NOT EXISTS uk_careplan_current_resource
    ON careplan.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

-- Resource lookup indexes
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type ON careplan.fhir_resource(resource_type);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_id ON careplan.fhir_resource(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type_id ON careplan.fhir_resource(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_current ON careplan.fhir_resource(is_current) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_careplan_resource_tenant ON careplan.fhir_resource(tenant_id);

-- Date indexes for _lastUpdated searches
CREATE INDEX IF NOT EXISTS idx_careplan_resource_last_updated ON careplan.fhir_resource(last_updated);
CREATE INDEX IF NOT EXISTS idx_careplan_resource_type_last_updated ON careplan.fhir_resource(resource_type, last_updated);

-- JSONB indexes for content searches
CREATE INDEX IF NOT EXISTS idx_careplan_content ON careplan.fhir_resource USING GIN (content jsonb_path_ops);

-- Trigger for auto-updating last_updated
CREATE OR REPLACE TRIGGER trg_careplan_resource_last_updated
    BEFORE UPDATE ON careplan.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- ============================================
-- 2. Resource History Table
-- ============================================

CREATE TABLE IF NOT EXISTS careplan.fhir_resource_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL, -- CREATE, UPDATE, DELETE
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256),

    CONSTRAINT uk_careplan_history_version UNIQUE (resource_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_careplan_history_resource ON careplan.fhir_resource_history(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_history_changed_at ON careplan.fhir_resource_history(changed_at);

-- ============================================
-- 3. Search Parameter Index Table
-- ============================================

CREATE TABLE IF NOT EXISTS careplan.fhir_search_index (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

    -- Search parameter identification
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL, -- string, token, reference, date, number, quantity, uri, composite

    -- Indexed values (nullable based on param_type)
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

    -- Composite support
    composite_part INTEGER
);

-- Search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_resource ON careplan.fhir_search_index(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_search_type_param ON careplan.fhir_search_index(resource_type, param_name);
CREATE INDEX IF NOT EXISTS idx_careplan_search_tenant_type ON careplan.fhir_search_index(tenant_id, resource_type);

-- String search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_string ON careplan.fhir_search_index(resource_type, param_name, value_string_lower)
    WHERE param_type = 'string';
CREATE INDEX IF NOT EXISTS idx_careplan_search_string_trgm ON careplan.fhir_search_index USING GIN (value_string_lower gin_trgm_ops)
    WHERE param_type = 'string';

-- Token search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_token ON careplan.fhir_search_index(resource_type, param_name, value_token_system, value_token_code)
    WHERE param_type = 'token';
CREATE INDEX IF NOT EXISTS idx_careplan_search_token_code ON careplan.fhir_search_index(resource_type, param_name, value_token_code)
    WHERE param_type = 'token';

-- Reference search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_reference ON careplan.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id)
    WHERE param_type = 'reference';

-- Date search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_date ON careplan.fhir_search_index(resource_type, param_name, value_date_start, value_date_end)
    WHERE param_type = 'date';

-- Number/Quantity search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_number ON careplan.fhir_search_index(resource_type, param_name, value_number)
    WHERE param_type = 'number';
CREATE INDEX IF NOT EXISTS idx_careplan_search_quantity ON careplan.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code)
    WHERE param_type = 'quantity';

-- URI search indexes
CREATE INDEX IF NOT EXISTS idx_careplan_search_uri ON careplan.fhir_search_index(resource_type, param_name, value_uri)
    WHERE param_type = 'uri';

-- ============================================
-- 4. Tag/Security/Profile Storage
-- ============================================

CREATE TABLE IF NOT EXISTS careplan.fhir_resource_tag (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL, -- tag, security, profile
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_careplan_tag_resource ON careplan.fhir_resource_tag(resource_id);
CREATE INDEX IF NOT EXISTS idx_careplan_tag_lookup ON careplan.fhir_resource_tag(tag_type, system_uri, code);

-- ============================================
-- 5. Compartment Membership
-- ============================================

CREATE TABLE IF NOT EXISTS careplan.fhir_compartment (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_id UUID NOT NULL REFERENCES careplan.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL, -- Patient, Encounter, Practitioner, etc.
    compartment_id VARCHAR(64) NOT NULL,

    CONSTRAINT uk_careplan_compartment UNIQUE (resource_id, compartment_type, compartment_id)
);

CREATE INDEX IF NOT EXISTS idx_careplan_compartment_lookup ON careplan.fhir_compartment(compartment_type, compartment_id);
CREATE INDEX IF NOT EXISTS idx_careplan_compartment_resource ON careplan.fhir_compartment(resource_id);

-- ============================================
-- Helper Function for Future Dedicated Schemas
-- Creates ALL tables (full replication) in a new dedicated schema
-- ============================================

CREATE OR REPLACE FUNCTION create_dedicated_fhir_schema(p_schema_name TEXT)
RETURNS VOID AS $$
BEGIN
    -- Validate schema name (alphanumeric and underscore only)
    IF p_schema_name !~ '^[a-zA-Z_][a-zA-Z0-9_]*$' THEN
        RAISE EXCEPTION 'Invalid schema name: %', p_schema_name;
    END IF;

    -- Create the schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);

    -- ========================================
    -- 1. Create fhir_resource table
    -- ========================================
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
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type_id ON %I.fhir_resource(resource_type, resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_current ON %I.fhir_resource(is_current) WHERE is_current = TRUE', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_tenant ON %I.fhir_resource(tenant_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_last_updated ON %I.fhir_resource(last_updated)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type_last_updated ON %I.fhir_resource(resource_type, last_updated)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_content ON %I.fhir_resource USING GIN (content jsonb_path_ops)', p_schema_name, p_schema_name);

    -- fhir_resource trigger
    EXECUTE format('
        CREATE OR REPLACE TRIGGER trg_%s_resource_last_updated
        BEFORE UPDATE ON %I.fhir_resource
        FOR EACH ROW
        EXECUTE FUNCTION update_last_updated()
    ', p_schema_name, p_schema_name);

    -- ========================================
    -- 2. Create fhir_resource_history table
    -- ========================================
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_resource_history (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            version_id INTEGER NOT NULL,
            content JSONB NOT NULL,
            operation VARCHAR(20) NOT NULL,
            changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
            changed_by VARCHAR(256),
            CONSTRAINT uk_%s_history_version UNIQUE (resource_id, version_id)
        )', p_schema_name, p_schema_name, p_schema_name);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_history_resource ON %I.fhir_resource_history(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_history_changed_at ON %I.fhir_resource_history(changed_at)', p_schema_name, p_schema_name);

    -- ========================================
    -- 3. Create fhir_search_index table
    -- ========================================
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.fhir_search_index (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            resource_id UUID NOT NULL REFERENCES %I.fhir_resource(id) ON DELETE CASCADE,
            resource_type VARCHAR(100) NOT NULL,
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

    -- fhir_search_index indexes
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_resource ON %I.fhir_search_index(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_type_param ON %I.fhir_search_index(resource_type, param_name)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_tenant_type ON %I.fhir_search_index(tenant_id, resource_type)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_string ON %I.fhir_search_index(resource_type, param_name, value_string_lower) WHERE param_type = ''string''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_string_trgm ON %I.fhir_search_index USING GIN (value_string_lower gin_trgm_ops) WHERE param_type = ''string''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_token ON %I.fhir_search_index(resource_type, param_name, value_token_system, value_token_code) WHERE param_type = ''token''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_token_code ON %I.fhir_search_index(resource_type, param_name, value_token_code) WHERE param_type = ''token''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_reference ON %I.fhir_search_index(resource_type, param_name, value_reference_type, value_reference_id) WHERE param_type = ''reference''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_date ON %I.fhir_search_index(resource_type, param_name, value_date_start, value_date_end) WHERE param_type = ''date''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_number ON %I.fhir_search_index(resource_type, param_name, value_number) WHERE param_type = ''number''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_quantity ON %I.fhir_search_index(resource_type, param_name, value_quantity_value, value_quantity_system, value_quantity_code) WHERE param_type = ''quantity''', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search_uri ON %I.fhir_search_index(resource_type, param_name, value_uri) WHERE param_type = ''uri''', p_schema_name, p_schema_name);

    -- ========================================
    -- 4. Create fhir_resource_tag table
    -- ========================================
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

    -- ========================================
    -- 5. Create fhir_compartment table
    -- ========================================
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

    RAISE NOTICE 'Created dedicated schema with full table replication: %', p_schema_name;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Note: fhir_audit_log is NOT replicated
-- It remains in the shared 'fhir' schema as a
-- cross-cutting concern for all resources
-- ============================================
