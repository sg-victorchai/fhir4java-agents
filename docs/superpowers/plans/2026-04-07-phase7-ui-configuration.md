# Phase 7: UI Configuration Implementation Plan (Weeks 32-35)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Multi-level UI configuration system (system → tenant → role → user) for clinical UI customization.

**Architecture:** Hierarchical config with merge logic. JSONB storage for flexible schema. REST API for config CRUD.

**Tech Stack:** PostgreSQL JSONB, Spring Boot

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillar 8

---

## File Structure

```
fhir4java-persistence/src/main/java/org/fhirframework/persistence/
├── entity/
│   └── UiConfigEntity.java                # UI configuration storage
├── repository/
│   └── UiConfigRepository.java            # Config CRUD

fhir4java-core/src/main/java/org/fhirframework/core/
├── uiconfig/
│   ├── UiConfigService.java               # Config facade
│   ├── ConfigLevel.java                   # Enum: SYSTEM, TENANT, ROLE, USER
│   ├── ConfigMerger.java                  # Hierarchical merge logic
│   └── ConfigSection.java                 # Enum: panels, views, shortcuts, etc.

fhir4java-api/src/main/java/org/fhirframework/api/
├── uiconfig/
│   └── UiConfigController.java            # Config REST API
```

---

## Tasks

### Task 1: UI Config Entity & Repository

**Files:** `fhir4java-persistence/.../UiConfigEntity.java`

- [ ] **Step 1:** Add Flyway migration for `ui_config` table

```sql
CREATE TABLE ui_config (
    id BIGSERIAL PRIMARY KEY,
    config_level VARCHAR(20) NOT NULL,  -- SYSTEM, TENANT, ROLE, USER
    tenant_id VARCHAR(64),
    role_name VARCHAR(100),
    user_id VARCHAR(100),
    section VARCHAR(50) NOT NULL,        -- panels, views, shortcuts
    config JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(config_level, tenant_id, role_name, user_id, section)
);
```

- [ ] **Step 2:** Implement UiConfigEntity with JSONB mapping
- [ ] **Step 3:** Implement UiConfigRepository
- [ ] **Step 4:** Commit `feat(persistence): add UI config entity`

### Task 2: Config Merge Logic

**Files:** `fhir4java-core/.../uiconfig/ConfigMerger.java`

- [ ] **Step 1:** Write test for hierarchical merge (system < tenant < role < user)
- [ ] **Step 2:** Implement deep merge for JSONB configs
- [ ] **Step 3:** Handle array merge strategies (replace vs append)
- [ ] **Step 4:** Commit `feat(core): add hierarchical config merge`

### Task 3: UI Config Service

**Files:** `fhir4java-core/.../uiconfig/UiConfigService.java`

- [ ] **Step 1:** Implement getEffectiveConfig(userId, tenantId, roles, section)
- [ ] **Step 2:** Query all applicable configs, merge in order
- [ ] **Step 3:** Cache merged config for performance
- [ ] **Step 4:** Commit `feat(core): add UI config service`

### Task 4: UI Config REST API

**Files:** `fhir4java-api/.../UiConfigController.java`

- [ ] **Step 1:** Write tests for config CRUD
- [ ] **Step 2:** Implement `GET /api/ui-config/{section}` (returns merged config)
- [ ] **Step 3:** Implement `PUT /api/ui-config/{level}/{section}` (update config)
- [ ] **Step 4:** Implement admin endpoints for tenant/system config
- [ ] **Step 5:** Commit `feat(api): add UI config REST API`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1 | Database schema for UI config |
| 2 | Hierarchical merge logic |
| 3 | Config service with caching |
| 4 | REST API |

**Total: 4 tasks**
