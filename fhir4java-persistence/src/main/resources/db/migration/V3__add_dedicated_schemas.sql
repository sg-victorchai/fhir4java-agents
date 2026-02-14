-- FHIR4Java Database Schema V3
-- Creates dedicated schemas for resources configured with schema.type=dedicated
-- This migration creates the careplan schema as an example of dedicated schema routing

-- ============================================
-- Create CarePlan Dedicated Schema
-- ============================================

CREATE SCHEMA IF NOT EXISTS careplan;

-- Create the fhir_resource table in the careplan schema
-- Structure mirrors fhir.fhir_resource from V1
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

-- Create partial unique index for current resources
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

-- Create trigger for auto-updating last_updated
CREATE OR REPLACE TRIGGER trg_careplan_resource_last_updated
    BEFORE UPDATE ON careplan.fhir_resource
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated();

-- ============================================
-- Helper Function for Future Dedicated Schemas
-- Can be used to create additional dedicated schemas programmatically
-- ============================================

CREATE OR REPLACE FUNCTION create_dedicated_fhir_schema(p_schema_name TEXT)
RETURNS VOID AS $$
BEGIN
    -- Create the schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);

    -- Create the fhir_resource table
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

    -- Create indexes
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS uk_%s_current_resource ON %I.fhir_resource(tenant_id, resource_type, resource_id) WHERE is_current = TRUE', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_type ON %I.fhir_resource(resource_type)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_id ON %I.fhir_resource(resource_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_tenant ON %I.fhir_resource(tenant_id)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_resource_last_updated ON %I.fhir_resource(last_updated)', p_schema_name, p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_content ON %I.fhir_resource USING GIN (content jsonb_path_ops)', p_schema_name, p_schema_name);

    -- Create trigger
    EXECUTE format('
        CREATE OR REPLACE TRIGGER trg_%s_resource_last_updated
        BEFORE UPDATE ON %I.fhir_resource
        FOR EACH ROW
        EXECUTE FUNCTION update_last_updated()
    ', p_schema_name, p_schema_name);

    RAISE NOTICE 'Created dedicated schema: %', p_schema_name;
END;
$$ LANGUAGE plpgsql;
