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

### Current Design: Primary Table Only

The dedicated schema contains **only the `fhir_resource` table**:

```
fhir/ (shared schema)
├── fhir_resource           ← Primary table for shared resources
├── fhir_search_index       ← Unused (search uses JSONB)
├── fhir_resource_history   ← Unused (versioning in main table)
├── fhir_resource_tag       ← Unused (tags in JSONB content)
├── fhir_compartment        ← Unused (hardcoded in operations)
├── fhir_audit_log          ← Cross-cutting audit log
└── flyway_schema_history   ← Migration metadata

careplan/ (dedicated schema)
└── fhir_resource           ← Primary table for CarePlan only
```

### Table Structure (Identical Across Schemas)

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

-- Indexes
CREATE UNIQUE INDEX uk_{schema}_current_resource
    ON {schema}.fhir_resource(tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE;

CREATE INDEX idx_{schema}_content
    ON {schema}.fhir_resource USING GIN (content jsonb_path_ops);
```

### Why Only the Primary Table?

| Supporting Table | Status | Rationale |
|------------------|--------|-----------|
| `fhir_search_index` | **Not created** | Search uses JSONB `content` column directly via `jsonb_extract_path_text()` |
| `fhir_resource_history` | **Not created** | Versioning uses `fhir_resource` with `is_current` flag and `version_id` |
| `fhir_resource_tag` | **Not created** | Tags/security labels stored in JSONB `content` column |
| `fhir_compartment` | **Not created** | Compartment membership uses hardcoded search parameters in `$everything` |

This minimizes complexity while supporting all current functionality.

---

## Design Options Analysis

### Option 1: Primary Table Only (Current Implementation)

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
- Current search/validation/history patterns remain unchanged
- No specialized indexing requirements

---

### Option 2: Full Table Replication

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

**Cons:**
- Creates unused tables
- More complex migrations
- Higher storage overhead
- More indexes to maintain

**When to Use:**
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

### Enabling Supporting Tables (Future)

If extracted search indexes become needed:

1. **Create migration:**
```sql
-- V5__add_dedicated_schema_search_index.sql
CREATE TABLE careplan.fhir_search_index (
    -- Same structure as fhir.fhir_search_index
);
```

2. **Extend DedicatedSchemaRepository:**
```java
public void saveSearchIndex(String schemaName, SearchIndexEntity entity) {
    validateSchemaName(schemaName);
    // Native SQL insert
}
```

3. **Update routing logic:**
```java
// Add method to SchemaRoutingRepository
public void saveSearchIndex(String resourceType, SearchIndexEntity entity) {
    if (schemaResolver.isDedicatedSchema(resourceType)) {
        dedicatedRepository.saveSearchIndex(
            schemaResolver.resolveSchema(resourceType), entity);
    } else {
        searchIndexRepository.save(entity);
    }
}
```

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

**H2SchemaInitializer** creates dedicated schemas in H2:

```java
@Configuration
@Profile("test")
public class H2SchemaInitializer {
    private static final String[] DEDICATED_SCHEMAS = {"careplan"};

    @PostConstruct
    void initializeDedicatedSchemas() {
        for (String schema : DEDICATED_SCHEMAS) {
            createDedicatedSchemaTable(schema);
        }
    }
}
```

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
