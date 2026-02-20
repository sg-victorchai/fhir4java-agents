# Task: Create New Skills for FHIR4Java

## Status: IN PROGRESS

## Date Created: 2026-02-20

## Priority: Medium

---

## Overview

Analysis of existing skills and recommendations for new skills to enhance the FHIR4Java project based on its vision as an enterprise FHIR server with multi-tenancy, plugin architecture, and configuration-driven design.

---

## Existing Skills (9 total)

### Resource Definition Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `add-resource` | Configure standard FHIR resource types via YAML | `.claude/skills/add-resource/` |
| `define-custom-resource` | Create completely new custom FHIR resource types (StructureDefinition, SearchParameters, ValueSets) | `.claude/skills/define-custom-resource/` |
| `define-fhir-profile` | Create FHIR profiles (constraints on existing resources) | `.claude/skills/define-fhir-profile/` |

### Terminology Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `define-valueset` | Create FHIR ValueSets for coded elements | `.claude/skills/define-valueset/` |

### Operations Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `add-custom-operation` | Define custom FHIR operations ($merge, $validate, etc.) | `.claude/skills/add-custom-operation/` |

### Logic/Behavior Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `create-business-plugin` | Create business logic plugins for validation, transformation, enrichment | `.claude/skills/create-business-plugin/` |

### Testing Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `generate-bdd-tests` | Create Cucumber BDD test features and step definitions | `.claude/skills/generate-bdd-tests/` |

### Development Utility Skills

| Skill | Purpose | Location |
|-------|---------|----------|
| `query-builder` | Build FHIR search queries interactively | `.claude/skills/query-builder/` |
| `validate-config` | Validate FHIR configuration files | `.claude/skills/validate-config/` |

---

## Gap Analysis

### Missing Capabilities by Category

| Category | Gap | Impact |
|----------|-----|--------|
| Multi-tenancy | No skill for tenant setup/configuration | Core feature lacks guided setup |
| Terminology | No CodeSystem skill (only ValueSet) | Incomplete terminology support |
| Extensions | No skill for FHIR extensions | Common customization need unmet |
| Database | No migration generation skill | Manual Flyway scripts error-prone |
| Real-time | No subscription/notification skill | Modern integration need unmet |
| Bulk Data | No bulk import/export configuration | Standard FHIR capability missing |
| Security | No SMART on FHIR setup | Critical for healthcare compliance |
| Audit | No audit-specific plugin template | Compliance requirement |
| Clients | No SDK/client generation | Consumer integration harder |
| Debugging | No search debugging assistance | Common developer pain point |

---

## Recommended New Skills

### Priority 1 - High Value (Implement First)

#### 1. `configure-tenant`
**Purpose:** Set up multi-tenant configurations

**Rationale:**
- Multi-tenancy is already implemented in the codebase
- Documented in CLAUDE.md as a core feature
- Involves multiple tables and configurations
- Error-prone without guidance

**Scope:**
- Create tenant entries in `fhir_tenant` table
- Configure tenant-specific settings (JSONB)
- Set up tenant isolation verification
- Generate test data for tenant

**Files to Generate:**
- SQL insert statements for tenant
- Test curl commands with X-Tenant-ID header
- Optional: tenant-specific resource configurations

---

#### 2. `define-extension`
**Purpose:** Create FHIR extensions for custom data elements

**Rationale:**
- Extensions are the standard way to add custom data to FHIR resources
- Complements existing `define-fhir-profile` skill
- Common need when standard FHIR elements insufficient

**Scope:**
- Simple extensions (single value)
- Complex extensions (nested structure)
- Modifier extensions
- Extension binding to ValueSets

**Files to Generate:**
- StructureDefinition for extension
- Example usage in resource
- Optional: Profile showing extension usage

---

#### 3. `define-codesystem`
**Purpose:** Create FHIR CodeSystems for custom coded values

**Rationale:**
- Complements existing `define-valueset` skill
- CodeSystems define the actual codes; ValueSets compose them
- Often needed together for custom terminology

**Scope:**
- Define code concepts with display and definition
- Hierarchical codes (parent/child)
- Code properties
- Version management

**Files to Generate:**
- CodeSystem JSON
- Related ValueSet (optional)
- Example usage in element binding

---

#### 4. `create-flyway-migration`
**Purpose:** Generate Flyway database migration scripts

**Rationale:**
- Database schema changes are common
- Migration versioning is error-prone (as seen with V3/V4 issue)
- Consistent naming and structure needed

**Scope:**
- Schema creation (dedicated schemas)
- Table modifications
- Index creation
- Data migrations
- Rollback scripts

**Files to Generate:**
- Versioned migration SQL file
- Naming: `V{N}__{description}.sql`
- Location: `fhir4java-persistence/src/main/resources/db/migration/`

---

### Priority 2 - Operational Value

#### 5. `configure-subscription`
**Purpose:** Set up FHIR Subscriptions for real-time notifications

**Rationale:**
- FHIR R5 has enhanced Subscription framework
- Real-time notifications increasingly important
- Complex setup with multiple components

**Scope:**
- Subscription resource creation
- Channel configuration (rest-hook, websocket, email)
- Filter criteria (FHIRPath)
- Topic definition

---

#### 6. `bulk-data-setup`
**Purpose:** Configure bulk import/export capabilities

**Rationale:**
- Bulk Data Access IG is standard
- $export operation is common requirement
- Complex configuration needed

**Scope:**
- Enable $export operation
- Configure output formats (ndjson)
- Set up storage backend
- Async processing configuration

---

#### 7. `create-audit-plugin`
**Purpose:** Create audit trail plugins for compliance

**Rationale:**
- Healthcare compliance requires audit trails
- Specialized form of business plugin
- FHIR AuditEvent resource integration

**Scope:**
- AuditEvent generation on operations
- Configurable audit levels
- External audit system integration
- ATNA/BALP compliance

---

### Priority 3 - Developer Experience

#### 8. `generate-api-client`
**Purpose:** Generate SDK/client code for FHIR API consumers

**Rationale:**
- Makes integration easier for consumers
- Reduces errors in client code
- Multiple language support beneficial

**Scope:**
- TypeScript/JavaScript client
- Java client
- Python client
- Based on CapabilityStatement

---

#### 9. `debug-search`
**Purpose:** Debug search query issues

**Rationale:**
- Search is complex (as seen with token search issue)
- Common developer pain point
- Could analyze queries and suggest fixes

**Scope:**
- Parse search URL and explain each part
- Identify potential issues
- Suggest corrections
- Test against actual data

---

#### 10. `setup-smart-auth`
**Purpose:** Configure SMART on FHIR authentication

**Rationale:**
- Security is critical for healthcare
- SMART is the standard authorization framework
- Complex OAuth2 configuration

**Scope:**
- OAuth2 server configuration
- SMART scopes setup
- Launch context
- Token introspection

---

## Implementation Order

### Phase 1 (Recommended to start)
1. [ ] `configure-tenant` - Immediate value, core feature support
2. [ ] `define-codesystem` - Quick win, complements existing skill

### Phase 2
3. [ ] `define-extension` - Common customization need
4. [ ] `create-flyway-migration` - Reduces operational errors

### Phase 3
5. [ ] `configure-subscription` - Modern integration capability
6. [ ] `create-audit-plugin` - Compliance support

### Phase 4
7. [ ] `bulk-data-setup` - Standard capability
8. [ ] `debug-search` - Developer experience

### Phase 5
9. [ ] `generate-api-client` - Consumer support
10. [ ] `setup-smart-auth` - Security enhancement

---

## Skill Template Structure

Each skill should follow the established pattern:

```
.claude/skills/{skill-name}/
└── SKILL.md
```

**SKILL.md Structure:**
1. Title and overview
2. Instructions (step-by-step interactive workflow)
3. Templates (code/config templates)
4. Examples (concrete usage examples)
5. After completion checklist

---

## References

- Existing skills location: `.claude/skills/`
- Project documentation: `CLAUDE.md`
- Implementation plan: `FHIR4JAVA-IMPLEMENTATION-PLAN.md`

---

## Notes

- Skills should be interactive and guide users step-by-step
- Generate all required files automatically
- Include validation and testing commands
- Follow established patterns from existing skills
