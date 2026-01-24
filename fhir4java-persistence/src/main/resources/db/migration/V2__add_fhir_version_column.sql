-- FHIR4Java Database Schema V2
-- Adds FHIR version column to support multi-version FHIR (R4B, R5)
-- This migration is managed by Flyway

-- ============================================
-- Add FHIR Version Column to Core Tables
-- ============================================

-- Add fhir_version column to fhir_resource table
ALTER TABLE fhir_resource
    ADD COLUMN IF NOT EXISTS fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5';

-- Add fhir_version column to fhir_resource_history table
ALTER TABLE fhir_resource_history
    ADD COLUMN IF NOT EXISTS fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5';

-- Add fhir_version column to fhir_search_index table
ALTER TABLE fhir_search_index
    ADD COLUMN IF NOT EXISTS fhir_version VARCHAR(10) NOT NULL DEFAULT 'R5';

-- ============================================
-- Create Indexes for FHIR Version Queries
-- ============================================

-- Index on fhir_resource for version-specific queries
CREATE INDEX IF NOT EXISTS idx_resource_fhir_version
    ON fhir_resource(fhir_version);

-- Composite index for resource type + version lookups
CREATE INDEX IF NOT EXISTS idx_resource_type_version
    ON fhir_resource(resource_type, fhir_version);

-- Composite index for tenant + type + version lookups
CREATE INDEX IF NOT EXISTS idx_resource_tenant_type_version
    ON fhir_resource(tenant_id, resource_type, fhir_version);

-- Index on fhir_search_index for version-specific searches
CREATE INDEX IF NOT EXISTS idx_search_fhir_version
    ON fhir_search_index(fhir_version);

-- Composite index for search by type + param + version
CREATE INDEX IF NOT EXISTS idx_search_type_param_version
    ON fhir_search_index(resource_type, param_name, fhir_version);

-- ============================================
-- Update Unique Constraints (if needed)
-- ============================================

-- Note: The existing unique constraint (uk_resource_version) may need to be
-- updated in a future migration if we want to store the same resource ID
-- with different FHIR versions. For now, we keep the existing constraint
-- as resources are typically stored in a single version.

-- ============================================
-- Comments
-- ============================================

COMMENT ON COLUMN fhir_resource.fhir_version IS 'FHIR version of the resource (R4B, R5)';
COMMENT ON COLUMN fhir_resource_history.fhir_version IS 'FHIR version of the resource at this version';
COMMENT ON COLUMN fhir_search_index.fhir_version IS 'FHIR version for version-specific search parameter indexing';
