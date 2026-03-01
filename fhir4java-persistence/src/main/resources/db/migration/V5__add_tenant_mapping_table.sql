-- FHIR4Java Database Schema V5
-- Adds tenant mapping table for multi-tenancy support
-- Maps external tenant GUIDs (from X-Tenant-ID header) to internal tenant IDs

-- ============================================
-- 1. Tenant Mapping Table
-- ============================================

CREATE TABLE IF NOT EXISTS fhir.fhir_tenant (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,           -- GUID from X-Tenant-ID header
    internal_id VARCHAR(64) NOT NULL UNIQUE,     -- Internal tenant identifier used in resource tables
    tenant_code VARCHAR(50),                     -- Short code (e.g., "HOSP-A")
    tenant_name VARCHAR(255),                    -- Display name
    description TEXT,                            -- Full description
    enabled BOOLEAN DEFAULT TRUE,               -- Active/inactive status
    settings JSONB,                             -- Tenant-specific settings (overrides, feature flags)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_external_id ON fhir.fhir_tenant(external_id);
CREATE INDEX IF NOT EXISTS idx_tenant_code ON fhir.fhir_tenant(tenant_code);
CREATE INDEX IF NOT EXISTS idx_tenant_internal_id ON fhir.fhir_tenant(internal_id);

-- ============================================
-- 2. Seed Default Tenant
-- ============================================

INSERT INTO fhir.fhir_tenant (external_id, internal_id, tenant_code, tenant_name, enabled)
VALUES ('00000000-0000-0000-0000-000000000000', 'default', 'DEFAULT', 'Default Tenant', true)
ON CONFLICT (internal_id) DO NOTHING;

-- ============================================
-- 3. Auto-update updated_at trigger
-- ============================================

CREATE OR REPLACE FUNCTION fhir.update_tenant_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tenant_updated_at
    BEFORE UPDATE ON fhir.fhir_tenant
    FOR EACH ROW
    EXECUTE FUNCTION fhir.update_tenant_updated_at();
