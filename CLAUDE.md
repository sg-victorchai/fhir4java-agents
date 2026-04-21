# FHIR4Java - Project Context

## Overview
Enterprise FHIR server implementation supporting HL7 FHIR R4B and R5 with configuration-driven resource management, plugin-based extensibility, multi-version support, and multi-tenancy.

## Tech Stack
- **Java**: 25 LTS
- **Framework**: Spring Boot 3.4+
- **Persistence**: Spring Data JPA with Hibernate 6.x
- **Database**: PostgreSQL 16+ (H2 for tests)
- **Migrations**: Flyway
- **FHIR Libraries**: HAPI FHIR 7.x
- **Build**: Maven (multi-module)
- **Testing**: JUnit 5, Cucumber BDD, REST Assured

## CLAUDE Coding Principles

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---


## Project Structure
```
fhir4java-agents/
├── db/                      # Database scripts (init, migrations, indexes)
├── docker/                  # Docker and docker-compose files
├── infrastructure/          # AWS CDK deployment scripts
├── fhir4java-core/          # Core FHIR processing (config, validation, resource registry)
├── fhir4java-persistence/   # JPA entities, repositories, search implementation
├── fhir4java-api/           # FHIR RESTful APIs and real-time event APIs
│   ├── controller/          #   - FHIR CRUD, search, operations endpoints
│   ├── event/               #   - SSE streaming (EventStreamController), webhook delivery
│   └── subscription/        #   - Subscription management (WebhookRegistry, persistence)
├── fhir4java-plugin/        # Plugin SPI and implementations
├── fhir4java-mcp/           # MCP (Model Context Protocol) implementation
│   ├── tools/               #   - fhir_discover, fhir_query, fhir_mutate tools
│   └── agent/               #   - AI agent webhook handler, SSE client
├── fhir4java-codegen/       # Code generator for custom FHIR resources
├── fhir4java-server/        # Spring Boot application, configurations
│   └── src/main/resources/
│       ├── application.yml
│       └── fhir-config/     # FHIR configuration files
│           ├── resources/   # Resource configs (patient.yml, observation.yml)
│           ├── r5/          # R5-specific definitions
│           │   ├── searchparameters/  # SearchParameter JSON files
│           │   ├── operations/        # OperationDefinition YAML files
│           │   └── profiles/          # StructureDefinition files
│           └── r4b/         # R4B-specific definitions
└── scripts/                 # Utility scripts (datagen, etc.)
```

## Key Components

### Core Layer (fhir4java-core)
| Component | Location | Purpose |
|-----------|----------|---------|
| `ResourceRegistry` | `core/resource/` | Manages resource configurations, version support |
| `SearchParameterRegistry` | `core/search/` | Loads/provides search parameters per FHIR version |
| `FhirContextFactory` | `core/fhir/` | Creates HAPI FhirContext for R4B/R5 |
| `ResourceConfiguration` | `core/config/` | POJO for resource YAML config |
| `ProfileValidator` | `core/validation/` | HAPI FHIR profile validation |
| `SearchParameterValidator` | `core/validation/` | Validates search parameters |

### Persistence Layer (fhir4java-persistence)
| Component | Location | Purpose |
|-----------|----------|---------|
| `FhirResourceEntity` | `entity/` | JPA entity for FHIR resources (includes tenant_id) |
| `FhirResourceRepository` | `repository/` | Spring Data repository with custom search |
| `FhirResourceRepositoryImpl` | `repository/` | Search predicate builders for all param types |
| `BundleProcessorService` | `service/` | Batch/transaction bundle processing |

### API Layer (fhir4java-api)
| Component | Location | Purpose |
|-----------|----------|---------|
| `FhirResourceController` | `controller/` | CRUD endpoints for resources |
| `OperationController` | `controller/` | Extended operation endpoints ($validate, etc.) |
| `MetadataController` | `controller/` | CapabilityStatement endpoint |
| `BundleController` | `controller/` | Batch/transaction endpoint |
| `WebhookController` | `controller/` | Webhook registration and management |
| `FhirVersionFilter` | `filter/` | Resolves FHIR version from URL |
| `FhirResourceService` | `service/` | Business logic for CRUD operations |
| `EventStreamController` | `event/` | SSE streaming endpoint for real-time events |
| `EventPublisher` | `event/` | Publishes resource change events |
| `WebhookRegistry` | `subscription/` | Webhook dispatch and HMAC signing |
| `SubscriptionPersistenceService` | `subscription/` | Persistent subscription management |

### Plugin Layer (fhir4java-plugin)
| Component | Location | Purpose |
|-----------|----------|---------|
| `FhirPlugin` | `spi/` | Base plugin interface |
| `PluginOrchestrator` | `core/` | Plugin execution pipeline |
| `BusinessLogicPlugin` | `spi/` | Before/after hooks for operations |
| `AuditPlugin`, `TelemetryPlugin` | `spi/` | Cross-cutting concerns |
| `PluginContext` | `core/` | Carries tenantId, userId, requestId, etc. |

## Multi-Tenant Design

### Architecture
The server supports multi-tenancy with row-level tenant isolation using a tenant mapping table:

```yaml
# application.yml
fhir4java:
  tenant:
    enabled: false              # Enable/disable multi-tenancy
    default-tenant-id: default  # Default internal tenant ID
    header-name: X-Tenant-ID    # HTTP header containing external tenant GUID
```

### Tenant Mapping Table
Maps external tenant GUIDs (from request headers) to internal tenant IDs:

```sql
CREATE TABLE fhir_tenant (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,      -- GUID from X-Tenant-ID header
    internal_id VARCHAR(64) NOT NULL UNIQUE, -- Internal tenant identifier
    tenant_code VARCHAR(50),               -- Short code (e.g., "HOSP-A")
    tenant_name VARCHAR(255),              -- Display name
    description TEXT,                      -- Full description
    enabled BOOLEAN DEFAULT TRUE,          -- Active/inactive status
    settings JSONB,                        -- Tenant-specific settings
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_tenant_external_id ON fhir_tenant(external_id);
CREATE INDEX idx_tenant_code ON fhir_tenant(tenant_code);
```

### Tenant Resolution Flow
1. **Extract**: Get external GUID from `X-Tenant-ID` request header
2. **Lookup**: Query `fhir_tenant` table to map GUID → internal_id
3. **Validate**: Check tenant is enabled
4. **Propagate**: Pass internal_id through `PluginContext` and repository queries

### Resource Tables
All primary tables use `tenant_id` (internal ID) for isolation:

```sql
-- fhir_resource table
tenant_id VARCHAR(64) DEFAULT 'default',
CONSTRAINT uk_resource_version UNIQUE (tenant_id, resource_type, resource_id, version_id),
CONSTRAINT uk_current_resource UNIQUE (tenant_id, resource_type, resource_id) WHERE is_current = TRUE

-- fhir_search_index table
tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',

-- fhir_audit_log table
tenant_id VARCHAR(64) DEFAULT 'default',
```

### Tenant-Aware Indexes
```sql
idx_resource_tenant ON fhir_resource(tenant_id)
idx_resource_tenant_type_version ON fhir_resource(tenant_id, resource_type, fhir_version)
idx_search_tenant_type ON fhir_search_index(tenant_id, resource_type)
idx_audit_tenant ON fhir_audit_log(tenant_id)
```

## URL Patterns
| Pattern | Example | Behavior |
|---------|---------|----------|
| Versioned | `/fhir/r5/Patient/123` | Explicit R5 version |
| Unversioned | `/fhir/Patient/123` | Uses resource's default version |
| Operations | `/fhir/r5/Patient/$merge` | Extended operations |
| Metadata | `/fhir/r5/metadata` | CapabilityStatement |

## Configuration
Resource configs in `fhir-config/resources/*.yml`:
```yaml
resourceType: Patient
enabled: true
fhirVersions:
  - version: R5
    default: true
  - version: R4B
interactions:
  read: true
  create: true
  update: true
  search: true
searchParameters:
  mode: allowlist  # or denylist
  common: [_id, _lastUpdated]
  resourceSpecific: [identifier, family, given]
```

## Common Commands
```bash
# Build
./mvnw clean install

# Run tests
./mvnw test

# Run BDD tests only
./mvnw test -pl fhir4java-server -Dtest=CucumberIT

# Start server
./mvnw spring-boot:run -pl fhir4java-server

# Skip tests during build
./mvnw clean install -DskipTests

# Docker
docker-compose -f docker/docker-compose.yml up
```

## Implementation Status
All phases completed:
- **Phase 1**: Project Foundation
- **Phase 2**: Core Framework (ResourceRegistry, SearchParameterRegistry)
- **Phase 3**: API Layer (Controllers, Version Resolution)
- **Phase 4**: Validation Framework (Profile, SearchParameter validation)
- **Phase 5**: Plugin System (PluginOrchestrator, Business Logic Plugins)
- **Phase 6**: Extended Operations ($validate, $everything, $merge)
- **Phase 7**: Advanced Features (Search, Batch/Transaction, CapabilityStatement)

## Search Parameter Types Supported
| Type | Formats | Modifiers |
|------|---------|-----------|
| Token | `system\|code`, `\|code`, `code` | :exact, :text, :not, :missing |
| Quantity | `[prefix]value\|system\|code` | eq, ne, lt, gt, le, ge, ap |
| Reference | `[type]/[id]`, URL, `[id]` | :identifier, :missing |
| Date | ISO formats | eq, ne, lt, gt, le, ge, sa, eb, ap |
| String | text | :exact, :contains, :missing |

## Key Design Decisions
1. **Configuration-driven**: Resources defined via YAML, search params via JSON
2. **Multi-version**: Same resource can support R4B and R5 simultaneously
3. **Multi-tenant**: Row-level isolation with tenant mapping (external GUID → internal ID)
4. **Plugin architecture**: Technical (auth, audit) and business logic plugins
5. **Single table storage**: Resources stored as JSONB in PostgreSQL
6. **Search indexes**: Extracted search values in separate index table

## BDD Test Features
Located in `fhir4java-server/src/test/resources/features/`:
- `operations/*.feature` - Extended operations tests
- `plugins/*.feature` - Plugin behavior tests
- `versioning/*.feature` - Version resolution tests
- `bundle/*.feature` - Batch/transaction tests

## Full Documentation
See `FHIR4JAVA-IMPLEMENTATION-PLAN.md` for detailed specifications.
