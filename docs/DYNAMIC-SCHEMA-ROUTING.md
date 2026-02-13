# Dynamic Schema Routing

This document provides comprehensive documentation for the Dynamic Schema Routing feature in FHIR4Java, which enables storing FHIR resources in dedicated database schemas based on resource configuration.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Design Rationale](#design-rationale)
4. [Component Details](#component-details)
5. [Database Schema Design](#database-schema-design)
6. [Design Options Analysis](#design-options-analysis)
7. [Future Extensions](#future-extensions)
8. [Configuration Reference](#configuration-reference)
9. [Testing](#testing)
10. [Migration Guide](#migration-guide)

---

## Overview

Dynamic Schema Routing allows FHIR resources to be stored in either a shared schema (default) or dedicated per-resource-type schemas based on configuration. This feature is useful for:

- **Performance isolation**: High-volume resources can have dedicated indexes
- **Data partitioning**: Regulatory or organizational requirements for data separation
- **Maintenance flexibility**: Schema-level operations (backup, restore, archival) per resource type
- **Query optimization**: Smaller tables with focused indexes

### Key Benefits

| Aspect | Description |
|--------|-------------|
| **Configuration-Driven** | Schema assignment via YAML config, no code changes needed |
| **Transparent Routing** | Service layer unaware of schema differences |
| **Consistent API** | Same FHIR REST API regardless of storage schema |
| **Gradual Adoption** | Add dedicated schemas incrementally without affecting existing resources |
| **Test Compatibility** | Works with both PostgreSQL (production) and H2 (testing) |

### Current Implementation

| Resource Type | Schema Type | Schema Name |
|---------------|-------------|-------------|
| CarePlan | Dedicated | `careplan` |
| Patient | Shared | `fhir` |
| Observation | Shared | `fhir` |
| All others | Shared | `fhir` |

---

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Layer                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  FhirResourceController  →  FhirResourceService                         ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Routing Layer                                      │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                     SchemaRoutingRepository                              ││
│  │                              │                                           ││
│  │              ┌───────────────┴───────────────┐                          ││
│  │              ▼                               ▼                          ││
│  │     isDedicatedSchema?                SchemaResolver                    ││
│  │         /        \                          │                           ││
│  │        NO        YES                        ▼                           ││
│  │         │          │                 ResourceRegistry                   ││
│  │         ▼          ▼                        │                           ││
│  │    Shared      Dedicated                    ▼                           ││
│  │    Schema      Schema               ResourceConfiguration              ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                           /                    \
                          ▼                      ▼
┌────────────────────────────────────┐  ┌────────────────────────────────────┐
│       FhirResourceRepository       │  │    DedicatedSchemaRepository       │
│            (JPA/JPQL)              │  │         (Native SQL)               │
│                                    │  │                                    │
│  • Uses @Entity mapping            │  │  • Dynamic schema qualification    │
│  • Schema: fhir (default)          │  │  • Schema: careplan, etc.          │
│  • JPA Criteria API for search     │  │  • JDBC Template operations        │
└────────────────────────────────────┘  └────────────────────────────────────┘
                 │                                       │
                 ▼                                       ▼
┌────────────────────────────────────┐  ┌────────────────────────────────────┐
│       fhir.fhir_resource           │  │    careplan.fhir_resource          │
│  ┌──────────────────────────────┐  │  │  ┌──────────────────────────────┐  │
│  │ Patient, Observation, etc.   │  │  │  │ CarePlan resources only      │  │
│  └──────────────────────────────┘  │  │  └──────────────────────────────┘  │
└────────────────────────────────────┘  └────────────────────────────────────┘
```

### Component Interaction Sequence

```
┌──────────┐     ┌─────────────┐     ┌──────────────────┐     ┌───────────────┐
│Controller│     │FhirResource │     │SchemaRouting     │     │SchemaResolver │
│          │     │Service      │     │Repository        │     │               │
└────┬─────┘     └──────┬──────┘     └────────┬─────────┘     └───────┬───────┘
     │                  │                     │                       │
     │ create(CarePlan) │                     │                       │
     │─────────────────>│                     │                       │
     │                  │                     │                       │
     │                  │ save("CarePlan",    │                       │
     │                  │      entity)        │                       │
     │                  │────────────────────>│                       │
     │                  │                     │                       │
     │                  │                     │ isDedicatedSchema     │
     │                  │                     │ ("CarePlan")          │
     │                  │                     │──────────────────────>│
     │                  │                     │                       │
     │                  │                     │      true             │
     │                  │                     │<──────────────────────│
     │                  │                     │                       │
     │                  │                     │ resolveSchema         │
     │                  │                     │ ("CarePlan")          │
     │                  │                     │──────────────────────>│
     │                  │                     │                       │
     │                  │                     │    "careplan"         │
     │                  │                     │<──────────────────────│
     │                  │                     │                       │
     │                  │                     │                       │
     │                  │                     ├───────────────────┐   │
     │                  │                     │ DedicatedSchema   │   │
     │                  │                     │ Repository.save() │   │
     │                  │                     │<──────────────────┘   │
     │                  │                     │                       │
     │                  │     entity          │                       │
     │                  │<────────────────────│                       │
     │                  │                     │                       │
     │    response      │                     │                       │
     │<─────────────────│                     │                       │
     │                  │                     │                       │
```

---

## Design Rationale

### Why Schema-Level Separation?

| Approach | Pros | Cons |
|----------|------|------|
| **Table Partitioning** | Single table, automatic routing | Complex setup, PostgreSQL-specific, limited flexibility |
| **Separate Tables (same schema)** | Simple queries | Name collisions, no isolation benefits |
| **Separate Schemas** | Full isolation, clear boundaries, portable | Requires routing logic, more complex queries |
| **Separate Databases** | Maximum isolation | Connection management complexity, distributed transactions |

We chose **separate schemas** as the optimal balance between isolation benefits and operational complexity.

### Why Native SQL for Dedicated Schemas?

JPA entities are bound to a single schema via `@Table(schema = "...")`. Dynamic schema routing requires:

1. **JPA Repository** (`FhirResourceRepository`): For shared schema, leverages existing entity mappings
2. **Native SQL** (`DedicatedSchemaRepository`): For dedicated schemas, uses JDBC with dynamic schema qualification

This hybrid approach:
- Preserves JPA benefits for the common case (shared schema)
- Enables dynamic schema routing without multiple entity definitions
- Maintains consistent data model across all schemas

---

## Component Details

### SchemaResolver

**Location:** `fhir4java-core/src/main/java/org/fhirframework/core/schema/SchemaResolver.java`

Determines the schema for a resource type based on configuration.

```java
@Component
public class SchemaResolver {
    private static final String DEFAULT_SCHEMA = "fhir";

    public String resolveSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
            .map(config -> config.getSchema().isDedicated()
                ? config.getSchema().getEffectiveSchemaName()
                : DEFAULT_SCHEMA)
            .orElse(DEFAULT_SCHEMA);
    }

    public boolean isDedicatedSchema(String resourceType) {
        return resourceRegistry.getResource(resourceType)
            .map(config -> config.getSchema().isDedicated())
            .orElse(false);
    }
}
```

### SchemaRoutingRepository

**Location:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/SchemaRoutingRepository.java`

Routes operations to the appropriate repository based on schema type.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `save(resourceType, entity)` | Persists entity to appropriate schema |
| `findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(...)` | Finds current version |
| `findMaxVersionId(...)` | Gets latest version number |
| `markAllVersionsNotCurrent(...)` | Updates version flags |
| `softDelete(...)` | Marks resource as deleted |
| `searchWithParams(...)` | Executes search with parameters |

### DedicatedSchemaRepository

**Location:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/DedicatedSchemaRepository.java`

Executes native SQL operations against dedicated schemas.

**Key Features:**

- Schema name validation (prevents SQL injection)
- Dynamic SQL generation with schema qualification
- Row mapper for `FhirResourceEntity`
- Support for all CRUD operations
- Basic search parameter support

```java
public FhirResourceEntity save(String schemaName, FhirResourceEntity entity) {
    validateSchemaName(schemaName);  // Security: alphanumeric + underscore only

    String sql = String.format("""
        INSERT INTO %s.fhir_resource (...)
        VALUES (?, ?, ?, ...)
        """, schemaName);

    jdbcTemplate.update(sql, ...);
    return entity;
}
```

### SchemaConfig

**Location:** `fhir4java-core/src/main/java/org/fhirframework/core/config/SchemaConfig.java`

Configuration POJO for schema settings.

```java
public class SchemaConfig {
    private String type = "shared";  // "shared" or "dedicated"
    private String name = "fhir";    // Schema name

    public boolean isDedicated() {
        return "dedicated".equalsIgnoreCase(type);
    }

    public String getEffectiveSchemaName() {
        return isDedicated() && name != null && !name.isBlank() ? name : "fhir";
    }
}
```

---

## Database Schema Design

### Current Design: Full Table Replication

The dedicated schema contains **all resource-related tables**, providing complete feature parity with the shared schema:

```
fhir/ (shared schema)
├── fhir_resource           ← Primary table for shared resources
├── fhir_search_index       ← Search parameter indexes
├── fhir_resource_history   ← Version history tracking
├── fhir_resource_tag       ← Tags, security labels, profiles
├── fhir_compartment        ← Compartment membership
├── fhir_audit_log          ← Cross-cutting audit log (NOT replicated)
└── flyway_schema_history   ← Migration metadata (NOT replicated)

careplan/ (dedicated schema)
├── fhir_resource           ← Primary table for CarePlan
├── fhir_search_index       ← CarePlan search indexes
├── fhir_resource_history   ← CarePlan version history
├── fhir_resource_tag       ← CarePlan tags/security/profiles
└── fhir_compartment        ← CarePlan compartment membership
```

### Tables in Dedicated Schemas

| Table | Purpose | Foreign Keys |
|-------|---------|--------------|
| `fhir_resource` | Primary resource storage with JSONB content | None |
| `fhir_resource_history` | Tracks all version changes with operation type | References `fhir_resource(id)` |
| `fhir_search_index` | Pre-computed search parameter values | References `fhir_resource(id)` |
| `fhir_resource_tag` | Tags, security labels, and profile references | References `fhir_resource(id)` |
| `fhir_compartment` | Compartment membership for Patient, Encounter, etc. | References `fhir_resource(id)` |

### Tables NOT Replicated (Cross-Cutting)

| Table | Rationale |
|-------|-----------|
| `fhir_audit_log` | Audit trail spans all resources across all schemas |
| `flyway_schema_history` | Migration metadata managed by Flyway |

### Primary Table Structure

```sql
CREATE TABLE {schema}.fhir_resource (
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

    CONSTRAINT uk_{schema}_resource_version
        UNIQUE (tenant_id, resource_type, resource_id, version_id)
);
```

### Supporting Table Structures

```sql
-- Version History
CREATE TABLE {schema}.fhir_resource_history (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES {schema}.fhir_resource(id) ON DELETE CASCADE,
    version_id INTEGER NOT NULL,
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL,  -- CREATE, UPDATE, DELETE
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(256)
);

-- Search Parameter Index
CREATE TABLE {schema}.fhir_search_index (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES {schema}.fhir_resource(id) ON DELETE CASCADE,
    resource_type VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,  -- string, token, reference, date, etc.
    value_string VARCHAR(2048),
    value_token_system VARCHAR(2048),
    value_token_code VARCHAR(256),
    value_reference_type VARCHAR(100),
    value_reference_id VARCHAR(64),
    value_date_start TIMESTAMP WITH TIME ZONE,
    value_date_end TIMESTAMP WITH TIME ZONE,
    -- ... additional value columns
);

-- Tags/Security/Profiles
CREATE TABLE {schema}.fhir_resource_tag (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES {schema}.fhir_resource(id) ON DELETE CASCADE,
    tag_type VARCHAR(20) NOT NULL,  -- tag, security, profile
    system_uri VARCHAR(2048),
    code VARCHAR(256),
    display VARCHAR(1024)
);

-- Compartment Membership
CREATE TABLE {schema}.fhir_compartment (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES {schema}.fhir_resource(id) ON DELETE CASCADE,
    compartment_type VARCHAR(100) NOT NULL,  -- Patient, Encounter, etc.
    compartment_id VARCHAR(64) NOT NULL
);
```

### Why Full Table Replication?

| Benefit | Description |
|---------|-------------|
| **Complete Feature Parity** | All FHIR features work identically in dedicated schemas |
| **Future-Proof** | Ready for extracted search indexes, explicit history, etc. |
| **Data Isolation** | All resource data (including indexes) is schema-contained |
| **Independent Optimization** | Can tune indexes per resource type |
| **Simpler Cross-Table Queries** | Joins stay within schema boundaries |

### Trade-offs

| Aspect | Impact |
|--------|--------|
| Storage Overhead | More tables per schema (currently unused tables still created) |
| Migration Complexity | More DDL statements per new dedicated schema |
| Index Maintenance | More indexes to maintain per schema |

---

## Design Options Analysis

### Option 1: Primary Table Only

**Structure:**
```
dedicated_schema/
└── fhir_resource
```

**Pros:**
- Simplest implementation
- Fewer tables to maintain
- Matches current usage patterns

**Cons:**
- If supporting tables become needed, requires migration
- Limited optimization opportunities for specialized queries

**When to Use:**
- Search/validation/history patterns use JSONB directly
- No specialized indexing requirements
- Minimal storage footprint is critical

---

### Option 2: Full Table Replication (Current Implementation)

**Structure:**
```
dedicated_schema/
├── fhir_resource
├── fhir_search_index
├── fhir_resource_history
├── fhir_resource_tag
└── fhir_compartment
```

**Pros:**
- Complete feature parity with shared schema
- Future-proof for any feature additions
- Consistent schema structure
- Ready for extracted search indexes when implemented

**Cons:**
- Creates tables that may be unused initially
- More complex migrations
- Higher storage overhead
- More indexes to maintain

**When to Use:**
- Default recommendation for dedicated schemas
- Planning to implement extracted search indexes
- Need cross-table queries within dedicated schema
- Regulatory requirement for complete data isolation

---

### Option 3: Configurable Table Set

**Structure:**
```yaml
# Resource configuration
schema:
  type: dedicated
  name: careplan
  tables:
    - fhir_resource
    - fhir_search_index  # Optional
```

**Pros:**
- Maximum flexibility
- Pay-for-what-you-use approach
- Resource-specific optimization

**Cons:**
- Most complex to implement
- Configuration validation needed
- Potential inconsistencies

**When to Use:**
- Different resources have different feature requirements
- Fine-grained control over storage is critical

---

### Option 4: Schema Inheritance (PostgreSQL-Specific)

**Structure:**
```sql
-- Parent schema with table definitions
CREATE SCHEMA fhir_template;
CREATE TABLE fhir_template.fhir_resource (...);

-- Dedicated schemas inherit structure
CREATE TABLE careplan.fhir_resource () INHERITS (fhir_template.fhir_resource);
```

**Pros:**
- Single source of truth for table structure
- Automatic structure propagation
- PostgreSQL-native feature

**Cons:**
- PostgreSQL-specific (no H2 support)
- Inheritance has query planning implications
- More complex migrations

**When to Use:**
- PostgreSQL-only deployment
- Many dedicated schemas with identical structure
- Need for global queries across all schemas

---

## Future Extensions

### Adding New Dedicated Schemas

1. **Create resource configuration:**
```yaml
# fhir-config/resources/medicationrequest.yml
resourceType: MedicationRequest
schema:
  type: dedicated
  name: medication
```

2. **Create migration:**
```sql
-- V4__add_medication_schema.sql
SELECT create_dedicated_fhir_schema('medication');
```

3. **Update H2 initializer (for tests):**
```java
private static final String[] DEDICATED_SCHEMAS = {"careplan", "medication"};
```

### Activating Supporting Tables

The supporting tables are created in dedicated schemas but not currently used by the application. Here's how to activate each one when needed.

#### Current Status

| Table | Database | Application | Activation Required |
|-------|----------|-------------|---------------------|
| `fhir_resource` | Created | **Active** | None - already working |
| `fhir_search_index` | Created | **Inactive** | Entity + Repository + Service |
| `fhir_resource_history` | Created | **Inactive** | Entity + Repository + Service |
| `fhir_resource_tag` | Created | **Inactive** | Entity + Repository + Service |
| `fhir_compartment` | Created | **Inactive** | Entity + Repository + Service |

---

#### Implementing Extracted Search Indexes

**When to implement:** When JSONB search becomes a performance bottleneck and pre-computed indexes are needed.

**Step 1: Create Entity Class**
```java
// fhir4java-persistence/src/main/java/.../entity/SearchIndexEntity.java
@Entity
@Table(name = "fhir_search_index")
public class SearchIndexEntity {
    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "param_name", nullable = false)
    private String paramName;

    @Column(name = "param_type", nullable = false)
    private String paramType;

    // Value columns for different param types
    @Column(name = "value_string")
    private String valueString;

    @Column(name = "value_token_system")
    private String valueTokenSystem;

    @Column(name = "value_token_code")
    private String valueTokenCode;

    @Column(name = "value_reference_type")
    private String valueReferenceType;

    @Column(name = "value_reference_id")
    private String valueReferenceId;

    @Column(name = "value_date_start")
    private Instant valueDateStart;

    @Column(name = "value_date_end")
    private Instant valueDateEnd;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    // ... getters, setters, builder
}
```

**Step 2: Add to DedicatedSchemaRepository**
```java
// In DedicatedSchemaRepository.java

public void saveSearchIndexes(String schemaName, UUID resourceId,
                               List<SearchIndexEntity> indexes) {
    validateSchemaName(schemaName);

    // Delete existing indexes for this resource
    String deleteSql = String.format(
        "DELETE FROM %s.fhir_search_index WHERE resource_id = ?", schemaName);
    jdbcTemplate.update(deleteSql, resourceId);

    // Insert new indexes
    String insertSql = String.format("""
        INSERT INTO %s.fhir_search_index (
            id, resource_id, resource_type, tenant_id, param_name, param_type,
            value_string, value_token_system, value_token_code,
            value_reference_type, value_reference_id,
            value_date_start, value_date_end, value_number
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, schemaName);

    for (SearchIndexEntity index : indexes) {
        jdbcTemplate.update(insertSql,
            UUID.randomUUID(), resourceId, index.getResourceType(),
            index.getTenantId(), index.getParamName(), index.getParamType(),
            index.getValueString(), index.getValueTokenSystem(),
            index.getValueTokenCode(), index.getValueReferenceType(),
            index.getValueReferenceId(),
            index.getValueDateStart() != null ? Timestamp.from(index.getValueDateStart()) : null,
            index.getValueDateEnd() != null ? Timestamp.from(index.getValueDateEnd()) : null,
            index.getValueNumber()
        );
    }
}

public void deleteSearchIndexes(String schemaName, UUID resourceId) {
    validateSchemaName(schemaName);
    String sql = String.format(
        "DELETE FROM %s.fhir_search_index WHERE resource_id = ?", schemaName);
    jdbcTemplate.update(sql, resourceId);
}
```

**Step 3: Add to SchemaRoutingRepository**
```java
// In SchemaRoutingRepository.java

public void saveSearchIndexes(String resourceType, UUID resourceId,
                               List<SearchIndexEntity> indexes) {
    if (schemaResolver.isDedicatedSchema(resourceType)) {
        String schemaName = schemaResolver.resolveSchema(resourceType);
        dedicatedRepository.saveSearchIndexes(schemaName, resourceId, indexes);
    } else {
        // Use shared schema repository (JPA)
        searchIndexRepository.deleteByResourceId(resourceId);
        searchIndexRepository.saveAll(indexes);
    }
}
```

**Step 4: Create Search Index Extractor Service**
```java
@Service
public class SearchIndexExtractor {

    public List<SearchIndexEntity> extractIndexes(IBaseResource resource,
                                                   String resourceType,
                                                   String tenantId) {
        List<SearchIndexEntity> indexes = new ArrayList<>();

        // Extract search parameters based on SearchParameterRegistry
        // This involves parsing the resource and creating index entries
        // for each configured search parameter

        return indexes;
    }
}
```

**Step 5: Update FhirResourceService**
```java
// In FhirResourceService.java - modify create() and update() methods

@Transactional
public ResourceResult create(String resourceType, String resourceJson, FhirVersion version) {
    // ... existing create logic ...

    schemaRoutingRepository.save(resourceType, entity);

    // Extract and save search indexes
    if (searchIndexingEnabled) {
        IBaseResource resource = parser.parseResource(resourceJson);
        List<SearchIndexEntity> indexes = searchIndexExtractor.extractIndexes(
            resource, resourceType, DEFAULT_TENANT);
        schemaRoutingRepository.saveSearchIndexes(resourceType, entity.getId(), indexes);
    }

    return new ResourceResult(...);
}
```

---

#### Implementing Explicit History Tracking

**When to implement:** When audit requirements need detailed change tracking beyond the current version-in-main-table approach.

**Step 1: Create Entity Class**
```java
// fhir4java-persistence/src/main/java/.../entity/ResourceHistoryEntity.java
@Entity
@Table(name = "fhir_resource_history")
public class ResourceHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "version_id", nullable = false)
    private Integer versionId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "operation", nullable = false)
    private String operation;  // CREATE, UPDATE, DELETE

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private String changedBy;

    // ... getters, setters, builder
}
```

**Step 2: Add to DedicatedSchemaRepository**
```java
public void saveHistory(String schemaName, ResourceHistoryEntity history) {
    validateSchemaName(schemaName);

    String sql = String.format("""
        INSERT INTO %s.fhir_resource_history (
            id, resource_id, version_id, content, operation, changed_at, changed_by
        ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)
        """, schemaName);

    jdbcTemplate.update(sql,
        UUID.randomUUID(),
        history.getResourceId(),
        history.getVersionId(),
        history.getContent(),
        history.getOperation(),
        Timestamp.from(history.getChangedAt()),
        history.getChangedBy()
    );
}

public List<ResourceHistoryEntity> getHistory(String schemaName, UUID resourceId) {
    validateSchemaName(schemaName);

    String sql = String.format("""
        SELECT * FROM %s.fhir_resource_history
        WHERE resource_id = ?
        ORDER BY version_id DESC
        """, schemaName);

    return jdbcTemplate.query(sql, new ResourceHistoryRowMapper(), resourceId);
}
```

**Step 3: Update FhirResourceService**
```java
@Transactional
public ResourceResult update(String resourceType, String resourceId,
                              String resourceJson, FhirVersion version) {
    // ... existing update logic ...

    schemaRoutingRepository.save(resourceType, entity);

    // Record history
    if (historyTrackingEnabled) {
        ResourceHistoryEntity history = ResourceHistoryEntity.builder()
            .resourceId(entity.getId())
            .versionId(entity.getVersionId())
            .content(entity.getContent())
            .operation(isCreate ? "CREATE" : "UPDATE")
            .changedAt(Instant.now())
            .changedBy(getCurrentUser())
            .build();
        schemaRoutingRepository.saveHistory(resourceType, history);
    }

    return new ResourceResult(...);
}
```

---

#### Implementing Tag/Security/Profile Storage

**When to implement:** When you need efficient queries by tag, security label, or profile without JSONB parsing.

**Step 1: Create Entity Class**
```java
@Entity
@Table(name = "fhir_resource_tag")
public class ResourceTagEntity {
    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "tag_type", nullable = false)
    private String tagType;  // tag, security, profile

    @Column(name = "system_uri")
    private String systemUri;

    @Column(name = "code")
    private String code;

    @Column(name = "display")
    private String display;

    // ... getters, setters, builder
}
```

**Step 2: Add to DedicatedSchemaRepository**
```java
public void saveTags(String schemaName, UUID resourceId, List<ResourceTagEntity> tags) {
    validateSchemaName(schemaName);

    // Delete existing tags
    String deleteSql = String.format(
        "DELETE FROM %s.fhir_resource_tag WHERE resource_id = ?", schemaName);
    jdbcTemplate.update(deleteSql, resourceId);

    // Insert new tags
    String insertSql = String.format("""
        INSERT INTO %s.fhir_resource_tag (
            id, resource_id, tag_type, system_uri, code, display
        ) VALUES (?, ?, ?, ?, ?, ?)
        """, schemaName);

    for (ResourceTagEntity tag : tags) {
        jdbcTemplate.update(insertSql,
            UUID.randomUUID(), resourceId, tag.getTagType(),
            tag.getSystemUri(), tag.getCode(), tag.getDisplay()
        );
    }
}

public List<ResourceTagEntity> findResourcesByTag(String schemaName,
                                                    String tagType,
                                                    String system,
                                                    String code) {
    validateSchemaName(schemaName);

    String sql = String.format("""
        SELECT r.* FROM %s.fhir_resource r
        JOIN %s.fhir_resource_tag t ON r.id = t.resource_id
        WHERE t.tag_type = ? AND t.system_uri = ? AND t.code = ?
        AND r.is_current = TRUE AND r.is_deleted = FALSE
        """, schemaName, schemaName);

    return jdbcTemplate.query(sql, new FhirResourceEntityRowMapper(),
        tagType, system, code);
}
```

---

#### Implementing Compartment Membership

**When to implement:** When `$everything` operations need pre-computed compartment membership for performance.

**Step 1: Create Entity Class**
```java
@Entity
@Table(name = "fhir_compartment")
public class CompartmentEntity {
    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "compartment_type", nullable = false)
    private String compartmentType;  // Patient, Encounter, Practitioner, etc.

    @Column(name = "compartment_id", nullable = false)
    private String compartmentId;

    // ... getters, setters, builder
}
```

**Step 2: Add to DedicatedSchemaRepository**
```java
public void saveCompartmentMembership(String schemaName, UUID resourceId,
                                       List<CompartmentEntity> memberships) {
    validateSchemaName(schemaName);

    // Delete existing memberships
    String deleteSql = String.format(
        "DELETE FROM %s.fhir_compartment WHERE resource_id = ?", schemaName);
    jdbcTemplate.update(deleteSql, resourceId);

    // Insert new memberships
    String insertSql = String.format("""
        INSERT INTO %s.fhir_compartment (
            id, resource_id, compartment_type, compartment_id
        ) VALUES (?, ?, ?, ?)
        """, schemaName);

    for (CompartmentEntity membership : memberships) {
        jdbcTemplate.update(insertSql,
            UUID.randomUUID(), resourceId,
            membership.getCompartmentType(), membership.getCompartmentId()
        );
    }
}

public List<FhirResourceEntity> findByCompartment(String schemaName,
                                                    String compartmentType,
                                                    String compartmentId) {
    validateSchemaName(schemaName);

    String sql = String.format("""
        SELECT r.* FROM %s.fhir_resource r
        JOIN %s.fhir_compartment c ON r.id = c.resource_id
        WHERE c.compartment_type = ? AND c.compartment_id = ?
        AND r.is_current = TRUE AND r.is_deleted = FALSE
        """, schemaName, schemaName);

    return jdbcTemplate.query(sql, new FhirResourceEntityRowMapper(),
        compartmentType, compartmentId);
}
```

**Step 3: Create Compartment Calculator**
```java
@Service
public class CompartmentCalculator {

    public List<CompartmentEntity> calculateMemberships(IBaseResource resource,
                                                         String resourceType) {
        List<CompartmentEntity> memberships = new ArrayList<>();

        // Based on FHIR compartment definitions, calculate which compartments
        // this resource belongs to (e.g., Patient compartment via subject reference)

        // Example for CarePlan:
        // - Check subject reference -> Patient compartment
        // - Check encounter reference -> Encounter compartment
        // - Check author reference -> Practitioner compartment

        return memberships;
    }
}
```

---

### Cross-Schema Queries

For queries spanning multiple schemas (e.g., Patient references from CarePlan):

```java
// Option A: Application-level join
List<CarePlan> carePlans = schemaRoutingRepository.search("CarePlan", params);
Set<String> patientIds = extractPatientReferences(carePlans);
List<Patient> patients = schemaRoutingRepository.findByIds("Patient", patientIds);

// Option B: Database-level (native SQL)
String sql = """
    SELECT cp.*, p.content as patient_content
    FROM careplan.fhir_resource cp
    LEFT JOIN fhir.fhir_resource p
        ON p.resource_id = cp.content->>'subject'
    WHERE cp.resource_type = 'CarePlan'
    """;
```

### Schema-Level Operations

```java
@Service
public class SchemaMaintenanceService {

    public void archiveSchema(String schemaName, LocalDate before) {
        // Move old records to archive schema
    }

    public void reindexSchema(String schemaName) {
        // Rebuild JSONB indexes
    }

    public void backupSchema(String schemaName, Path destination) {
        // pg_dump specific schema
    }
}
```

### Multi-Database Extension

For extreme isolation requirements:

```java
public interface DataSourceResolver {
    DataSource resolveDataSource(String resourceType);
}

// Route to different databases based on resource type
@Component
public class MultiDatabaseRoutingRepository {
    private final Map<String, JdbcTemplate> templates;

    public FhirResourceEntity save(String resourceType, FhirResourceEntity entity) {
        JdbcTemplate template = templates.get(resolveDatabase(resourceType));
        // Execute on appropriate database
    }
}
```

---

## Configuration Reference

### Resource Configuration

```yaml
# fhir-config/resources/{resource}.yml
resourceType: CarePlan
enabled: true

# Schema configuration
schema:
  type: dedicated    # "shared" (default) or "dedicated"
  name: careplan     # Schema name (required if type=dedicated)

fhirVersions:
  - version: R5
    default: true

interactions:
  read: true
  create: true
  update: true
  search: true
```

### Schema Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `schema.type` | String | `"shared"` | Schema type: `"shared"` or `"dedicated"` |
| `schema.name` | String | `"fhir"` | Schema name for dedicated schemas |

### Application Configuration

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_schema: fhir  # Default schema for JPA entities

  flyway:
    schemas: fhir  # Primary schema for migrations
```

---

## Testing

### Unit Tests

**SchemaConfigTest** - Tests `SchemaConfig` behavior:
- `getEffectiveSchemaName()` returns correct schema
- `isDedicated()` / `isShared()` classification

**SchemaResolverTest** - Tests schema resolution:
- Returns dedicated schema for configured resources
- Returns default schema for unconfigured resources
- Returns default schema for unknown resources

### Integration Tests

**H2SchemaInitializer** creates all tables in dedicated schemas for H2 tests (full replication):

```java
@Configuration
@Profile("test")
public class H2SchemaInitializer {
    private static final String[] DEDICATED_SCHEMAS = {"careplan"};

    @PostConstruct
    void initializeDedicatedSchemas() {
        for (String schema : DEDICATED_SCHEMAS) {
            // Creates ALL tables: fhir_resource, fhir_resource_history,
            // fhir_search_index, fhir_resource_tag, fhir_compartment
            createDedicatedSchemaTables(schema);
        }
    }
}
```

**Tables Created Per Schema:**
- `fhir_resource` - Primary resource storage
- `fhir_resource_history` - Version history
- `fhir_search_index` - Search parameter indexes
- `fhir_resource_tag` - Tags, security, profiles
- `fhir_compartment` - Compartment membership

**H2 JDBC URL Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:fhir4java_test;MODE=PostgreSQL;
         INIT=CREATE SCHEMA IF NOT EXISTS FHIR\;
              CREATE SCHEMA IF NOT EXISTS CAREPLAN
```

### Manual Verification

```bash
# Start the server
./mvnw spring-boot:run -pl fhir4java-server

# Create a CarePlan (dedicated schema)
curl -X POST http://localhost:8080/fhir/r5/CarePlan \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "CarePlan",
    "status": "active",
    "intent": "plan",
    "subject": {"reference": "Patient/123"}
  }'

# Verify storage in PostgreSQL
psql -d fhir4java -c "SELECT COUNT(*) FROM careplan.fhir_resource;"
psql -d fhir4java -c "SELECT COUNT(*) FROM fhir.fhir_resource WHERE resource_type='CarePlan';"
# First should return 1, second should return 0
```

---

## Migration Guide

### Adding a New Dedicated Schema

1. **Create resource configuration file:**
   ```yaml
   # fhir-config/resources/procedure.yml
   resourceType: Procedure
   schema:
     type: dedicated
     name: procedure
   ```

2. **Create Flyway migration:**
   ```sql
   -- V4__add_procedure_schema.sql
   SELECT create_dedicated_fhir_schema('procedure');
   ```

3. **Update H2SchemaInitializer:**
   ```java
   private static final String[] DEDICATED_SCHEMAS = {"careplan", "procedure"};
   ```

4. **Update H2 JDBC URL:**
   ```yaml
   url: jdbc:h2:...;INIT=...CREATE SCHEMA IF NOT EXISTS PROCEDURE
   ```

5. **Run tests:**
   ```bash
   ./mvnw test -pl fhir4java-core,fhir4java-persistence
   ```

### Migrating Existing Data

To move existing resources from shared to dedicated schema:

```sql
-- Migration script
BEGIN;

-- Insert existing resources into dedicated schema
INSERT INTO procedure.fhir_resource
SELECT * FROM fhir.fhir_resource
WHERE resource_type = 'Procedure';

-- Verify counts match
DO $$
DECLARE
    source_count INTEGER;
    target_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO source_count
    FROM fhir.fhir_resource WHERE resource_type = 'Procedure';

    SELECT COUNT(*) INTO target_count
    FROM procedure.fhir_resource;

    IF source_count != target_count THEN
        RAISE EXCEPTION 'Count mismatch: source=%, target=%',
            source_count, target_count;
    END IF;
END $$;

-- Delete from source (only after verification)
DELETE FROM fhir.fhir_resource WHERE resource_type = 'Procedure';

COMMIT;
```

### Converting Back to Shared Schema

1. Update resource configuration:
   ```yaml
   schema:
     type: shared
   ```

2. Migrate data back:
   ```sql
   INSERT INTO fhir.fhir_resource
   SELECT * FROM procedure.fhir_resource;

   DROP TABLE procedure.fhir_resource;
   DROP SCHEMA procedure;
   ```

---

## Appendix

### SQL Injection Prevention

The `DedicatedSchemaRepository` validates schema names:

```java
private void validateSchemaName(String schemaName) {
    if (schemaName == null || schemaName.isBlank()) {
        throw new IllegalArgumentException("Schema name cannot be null or empty");
    }
    // Only allow alphanumeric and underscore
    if (!schemaName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
        throw new IllegalArgumentException("Invalid schema name: " + schemaName);
    }
}
```

### Performance Considerations

| Aspect | Shared Schema | Dedicated Schema |
|--------|---------------|------------------|
| Table Size | Larger (all resources) | Smaller (single type) |
| Index Efficiency | May degrade with scale | Optimized for resource type |
| Maintenance | Single VACUUM/ANALYZE | Per-schema operations |
| Backup/Restore | All-or-nothing | Granular per schema |
| Query Planning | More complex | Simpler, focused |

### Monitoring

```sql
-- Check table sizes by schema
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)) as size
FROM pg_tables
WHERE tablename = 'fhir_resource'
ORDER BY pg_total_relation_size(schemaname || '.' || tablename) DESC;

-- Check row counts
SELECT
    'fhir' as schema, COUNT(*) as count
FROM fhir.fhir_resource
UNION ALL
SELECT
    'careplan' as schema, COUNT(*) as count
FROM careplan.fhir_resource;
```
