# AI-Ready Platform Design: FHIR4Java as a Native AI Agent Data Platform

## Executive Summary

This document proposes the enhancements needed to transform FHIR4Java from a traditional FHIR server into a **fully AI-ready data and API platform** that natively integrates within AI agent ecosystems. The design covers seven pillars: MCP Server exposure, AI-native API discovery, event-driven architecture, semantic search, agent-friendly auth, observability for agents, and an AI orchestration layer.

---

## Current State Assessment

### What Exists (Strengths to Build On)
- **Configuration-driven architecture** - Resources, search params, operations defined via YAML/JSON
- **Plugin system** - BEFORE/AFTER hooks with `PluginOrchestrator`, extensible via Spring beans
- **Multi-version support** - R4B/R5 simultaneously
- **Multi-tenancy** - Row-level isolation with tenant mapping
- **CapabilityStatement** - Version-aware metadata endpoint
- **Extended operations** - $validate, $everything, $merge, $patch
- **Full search** - Token, date, reference, quantity, string with modifiers

### What's Missing for AI-Readiness
| Gap | Impact |
|-----|--------|
| No MCP server interface | AI agents can't discover or call FHIR operations natively |
| No OpenAPI/machine-readable API spec | Agents can't auto-generate correct API calls |
| No event streaming (SSE/WebSocket) | Agents can't react to real-time data changes |
| No semantic/vector search | Agents can't do natural language queries over clinical data |
| No agent-scoped auth (OAuth2 + scopes) | No fine-grained agent authorization |
| No bulk data export ($export) | Agents can't efficiently process large datasets |
| No FHIR Subscriptions | No push-based notifications for agent workflows |
| No structured tool descriptions | Agents can't self-discover what tools/operations exist |

---

## Pillar 1: MCP Server Interface (Expose FHIR as AI Tools)

### Goal
Expose FHIR4Java as an **MCP (Model Context Protocol) server** so that any MCP-compatible AI agent (Claude, GPT, custom agents) can discover and invoke FHIR operations as tools.

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  AI Agent (Claude, GPT, Custom)                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  MCP Client                                            │  │
│  └──────────────────────┬─────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │ MCP Protocol (stdio / SSE / HTTP)
┌─────────────────────────┼────────────────────────────────────┐
│  FHIR4Java MCP Server Layer (NEW)                            │
│  ┌──────────────────────┴─────────────────────────────────┐  │
│  │  McpServerEndpoint                                     │  │
│  │  ├── Tool Registry (auto-generated from config)        │  │
│  │  ├── Resource Provider (FHIR resources as MCP resources)│  │
│  │  └── Prompt Templates (clinical query templates)       │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Existing FHIR4Java Core                               │  │
│  │  ResourceRegistry │ SearchParamRegistry │ PluginOrch.  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### New Module: `fhir4java-mcp`

#### 1.1 Auto-Generated Tool Definitions

Tools are generated dynamically from the existing `ResourceRegistry` and operation configurations:

```java
// Generates MCP tools from ResourceRegistry configuration
public class FhirToolGenerator {

    // For each enabled resource, generate CRUD tools:
    // - fhir_create_{resourceType}  (e.g., fhir_create_patient)
    // - fhir_read_{resourceType}
    // - fhir_search_{resourceType}
    // - fhir_update_{resourceType}
    // - fhir_delete_{resourceType}

    // For each enabled operation, generate operation tools:
    // - fhir_operation_{resourceType}_{operation}  (e.g., fhir_operation_patient_everything)

    // Tool input schemas derived from:
    // - Search parameters (from SearchParameterRegistry)
    // - Operation parameter definitions (from operation YAML configs)
    // - Resource structure definitions
}
```

**Example auto-generated tool:**
```json
{
  "name": "fhir_search_patient",
  "description": "Search for Patient resources in the FHIR server. Supports standard FHIR search parameters.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "family": { "type": "string", "description": "Patient family name" },
      "given": { "type": "string", "description": "Patient given name" },
      "identifier": { "type": "string", "description": "Patient identifier (system|value)" },
      "birthdate": { "type": "string", "description": "Patient birth date (YYYY-MM-DD, supports prefixes: eq, ne, lt, gt, le, ge)" },
      "_count": { "type": "integer", "description": "Number of results per page", "default": 20 },
      "fhirVersion": { "type": "string", "enum": ["R5", "R4B"], "default": "R5" }
    }
  }
}
```

#### 1.2 FHIR Resources as MCP Resources

Expose FHIR data as MCP resources that agents can read:

```json
{
  "uri": "fhir://Patient/123",
  "name": "Patient John Doe",
  "mimeType": "application/fhir+json",
  "description": "Patient resource with ID 123"
}
```

Support resource templates for parameterized access:
```json
{
  "uriTemplate": "fhir://{resourceType}/{id}",
  "name": "FHIR Resource by ID",
  "description": "Read any FHIR resource by type and ID"
}
```

#### 1.3 Prompt Templates for Clinical Workflows

Pre-built prompt templates for common healthcare AI tasks:

| Template | Description |
|----------|-------------|
| `patient_summary` | Generate a clinical summary for a patient |
| `lab_trends` | Analyze lab result trends over time |
| `medication_review` | Review active medications for interactions |
| `care_gap_analysis` | Identify gaps in preventive care |
| `clinical_timeline` | Build chronological timeline of encounters |

#### 1.4 Transport Support

| Transport | Use Case | Priority |
|-----------|----------|----------|
| **Streamable HTTP** | Production deployments, web-based agents | P0 |
| **SSE** | Legacy MCP clients, real-time streaming | P1 |
| **stdio** | Local development, CLI-based agents | P1 |

### Key Design Decision
Tools are **auto-generated from existing configuration** (resource YAMLs, search parameter JSONs, operation definitions). When a new resource is configured via YAML, its MCP tools appear automatically without code changes.

---

## Pillar 2: AI-Native API Discovery

### Goal
Enable AI agents to fully understand what the server can do at runtime, without prior knowledge.

### 2.1 OpenAPI 3.1 Specification

Add `springdoc-openapi` to auto-generate OpenAPI specs from controllers:

```yaml
# New dependency in fhir4java-api/pom.xml
springdoc-openapi-starter-webmvc-api: 2.8+
```

**Endpoints:**
- `GET /v3/api-docs` - Full OpenAPI JSON spec
- `GET /v3/api-docs.yaml` - Full OpenAPI YAML spec
- `GET /swagger-ui.html` - Interactive API documentation

**Enhancements beyond default Spring generation:**
- Custom `OpenApiCustomizer` that reads `ResourceRegistry` to generate per-resource path documentation
- Include search parameter descriptions, modifiers, and examples
- Document all custom operations with parameter schemas
- Include FHIR-specific media types (`application/fhir+json`)

### 2.2 Discovery API (`/api/discovery/*`)

A dedicated REST API for capability introspection, designed for programmatic consumption by AI agents:

```
GET /api/discovery/resources
  → List all enabled resources with their interactions, versions, search params

GET /api/discovery/resources/{type}
  → Detailed info for one resource type

GET /api/discovery/operations
  → All available operations with parameter schemas

GET /api/discovery/operations/{type}/{name}
  → Detailed operation definition

GET /api/discovery/search-parameters/{type}
  → Search parameters with types, modifiers, examples

GET /api/discovery/plugins
  → Loaded plugins, their hooks, priorities

GET /api/discovery/tenants
  → Available tenants (admin only)

GET /api/discovery/mcp-tools
  → MCP tool definitions (same as MCP tools/list but via REST)

GET /api/discovery/capabilities
  → Aggregated capability summary (superset of /metadata)
```

**Response format designed for LLM consumption:**
```json
{
  "resourceType": "Patient",
  "description": "Demographics and administrative information about a person receiving healthcare",
  "fhirVersions": ["R5", "R4B"],
  "defaultVersion": "R5",
  "interactions": {
    "create": { "enabled": true, "description": "Create a new Patient" },
    "read": { "enabled": true, "description": "Read Patient by ID" },
    "search": {
      "enabled": true,
      "parameters": [
        {
          "name": "family",
          "type": "string",
          "description": "Patient family/last name",
          "modifiers": [":exact", ":contains", ":missing"],
          "examples": ["family=Smith", "family:exact=O'Brien"]
        }
      ]
    }
  },
  "operations": [
    {
      "name": "$everything",
      "description": "Return all resources related to this patient",
      "method": "GET",
      "url": "/fhir/r5/Patient/{id}/$everything"
    }
  ]
}
```

### 2.3 FHIR-Native Discovery Enhancements

Enhance the existing `MetadataController` CapabilityStatement:
- Add `TerminologyCapabilities` endpoint (`/fhir/r5/metadata?mode=terminology`)
- Add operation definitions as contained resources
- Include security/auth capability declarations
- Add implementation guide references

---

## Pillar 3: Event-Driven Architecture (Real-Time Agent Integration)

### Goal
Enable AI agents to react to clinical data changes in real-time rather than polling.

### 3.1 FHIR Subscriptions (R5 Topic-Based)

Implement the [FHIR R5 Subscriptions framework](https://hl7.org/fhir/R5/subscriptions.html):

```
┌─────────────┐    Create Subscription    ┌──────────────────────┐
│  AI Agent    │ ───────────────────────→  │  SubscriptionManager │
│              │                          │  (NEW)               │
│              │  ←── SSE/WebSocket ────  │  ┌────────────────┐  │
│              │      Notifications       │  │ TopicRegistry   │  │
└─────────────┘                          │  │ ChannelManager  │  │
                                         │  │ FilterEngine    │  │
                                         │  └────────────────┘  │
                                         └──────────────────────┘
```

**Subscription Topics (pre-configured):**

| Topic | Trigger | Use Case |
|-------|---------|----------|
| `patient-admit` | Encounter.status = in-progress | Alert on new admissions |
| `lab-result-critical` | Observation with critical flag | Critical lab notification |
| `medication-change` | MedicationRequest create/update | Medication reconciliation |
| `resource-change` | Any resource CRUD | General audit/sync |

**Notification Channels:**

| Channel | Protocol | Best For |
|---------|----------|----------|
| `rest-hook` | HTTP POST callback | Server-to-server |
| `server-sent-events` | SSE stream | Browser/agent long-polling |
| `websocket` | WebSocket | Bidirectional real-time |

#### Implementation Components

```java
// New classes in fhir4java-core
public class SubscriptionManager {
    // Manages active subscriptions, matches events to subscribers
}

public class SubscriptionTopicRegistry {
    // Loads topic definitions from config, evaluates trigger criteria
}

public class NotificationChannelFactory {
    // Creates channel handlers (REST hook, SSE, WebSocket)
}

// New plugin to emit events
public class SubscriptionNotificationPlugin implements FhirPlugin {
    // AFTER phase plugin that checks all CRUD operations
    // against active subscription criteria and dispatches notifications
}
```

### 3.2 Server-Sent Events (SSE) Endpoint

A general-purpose SSE endpoint for agents to subscribe to server events:

```
GET /api/events/stream?topics=patient-change,observation-create
Accept: text/event-stream
```

Event format:
```
event: resource-change
data: {"resourceType":"Patient","id":"123","action":"update","timestamp":"2026-03-16T10:30:00Z"}

event: resource-change
data: {"resourceType":"Observation","id":"456","action":"create","timestamp":"2026-03-16T10:30:05Z"}
```

### 3.3 Webhook Registry

Allow agents to register HTTP webhook callbacks:

```
POST /api/webhooks
{
  "url": "https://agent.example.com/callback",
  "events": ["Patient.create", "Patient.update"],
  "secret": "hmac-secret-for-verification",
  "filters": {
    "resourceType": "Patient",
    "tenant": "hospital-a"
  }
}
```

---

## Pillar 4: Semantic Search & Natural Language Query

### Goal
Allow AI agents to query clinical data using natural language, not just structured FHIR search parameters.

### 4.1 Natural Language to FHIR Search Translation

A new endpoint that translates natural language queries into FHIR search API calls:

```
POST /api/ai/search
{
  "query": "Find all diabetic patients over 65 with recent HbA1c above 9%",
  "resourceTypes": ["Patient", "Condition", "Observation"],
  "maxResults": 50
}
```

**Translation Pipeline:**
```
Natural Language Query
        │
        ▼
┌─────────────────────────┐
│  Query Decomposer       │  Break into sub-queries
│  (uses Discovery API)   │  per resource type
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│  FHIR Search Builder    │  Map to FHIR search params
│  (uses SearchParamReg.) │  with correct modifiers
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│  Query Executor         │  Execute against FHIR API
│  (uses ResourceService) │  and aggregate results
└───────────┬─────────────┘
            ▼
     Structured Results
```

This component can work **without an LLM** by using rule-based NLP mapping against the SearchParameterRegistry, or can optionally integrate with an LLM for complex queries.

### 4.2 Vector Search for Clinical Narratives

Add embedding-based search for unstructured clinical text (narrative fields, notes):

```sql
-- New table for vector embeddings
CREATE TABLE fhir_resource_embedding (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    field_path VARCHAR(255) NOT NULL,       -- e.g., "text.div", "note[0].text"
    embedding vector(1536),                  -- pgvector extension
    text_content TEXT,                       -- Original text for display
    model_id VARCHAR(100),                   -- Which embedding model was used
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(tenant_id, resource_type, resource_id, field_path)
);

CREATE INDEX idx_embedding_vector ON fhir_resource_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

**Endpoint:**
```
POST /api/ai/semantic-search
{
  "query": "patient presenting with chest pain and shortness of breath",
  "resourceTypes": ["DiagnosticReport", "DocumentReference", "Condition"],
  "similarity_threshold": 0.75,
  "maxResults": 20
}
```

### 4.3 Embedding Pipeline Plugin

A new plugin that generates embeddings on resource create/update:

```java
public class EmbeddingPlugin implements FhirPlugin {
    // AFTER phase, ASYNC execution
    // Extracts narrative text from resources
    // Generates embeddings via configurable provider (OpenAI, local model, etc.)
    // Stores in fhir_resource_embedding table
}
```

Configuration:
```yaml
fhir4java:
  ai:
    embeddings:
      enabled: false
      provider: openai          # openai, ollama, custom
      model: text-embedding-3-small
      dimensions: 1536
      resource-types:            # Which resources to embed
        - DiagnosticReport
        - DocumentReference
        - Condition
      fields:                    # Which fields to extract text from
        - text.div
        - note[*].text
        - description
```

---

## Pillar 5: Agent-Friendly Authentication & Authorization

### Goal
Enable fine-grained, scoped authorization for AI agents with proper identity, audit trail, and least-privilege access.

### 5.1 OAuth2 / SMART on FHIR

Implement [SMART on FHIR](https://smarthealthit.org/) authorization:

```
┌──────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  AI Agent    │────→│  Auth Server       │────→│  FHIR4Java       │
│              │     │  (Keycloak/Custom) │     │  ResourceServer  │
│  client_id   │     │                   │     │                  │
│  scope=      │     │  Issues JWT with  │     │  Validates JWT   │
│  patient/*.r │     │  SMART scopes     │     │  Enforces scopes │
└──────────────┘     └───────────────────┘     └──────────────────┘
```

**SMART Scopes for agents:**
```
patient/Patient.read          - Read patient demographics
patient/Observation.read      - Read observations
patient/MedicationRequest.rs  - Read/search medications
system/Patient.cruds          - Full system-level access
user/*.read                   - Read all resources in user context
launch/patient                - Patient context launch
```

### 5.2 Agent Identity & API Keys

Support API key authentication for simpler agent integrations:

```yaml
fhir4java:
  auth:
    api-keys:
      enabled: true
      header: X-API-Key
    agents:
      - id: clinical-summary-agent
        api-key: "${AGENT_CLINICAL_SUMMARY_KEY}"
        scopes: ["patient/Patient.read", "patient/Observation.read", "patient/Condition.read"]
        rate-limit: 100/minute
        tenant-access: ["hospital-a"]
      - id: lab-monitoring-agent
        api-key: "${AGENT_LAB_MONITOR_KEY}"
        scopes: ["system/Observation.read"]
        rate-limit: 500/minute
        tenant-access: ["*"]
```

### 5.3 Scoped Authorization Plugin

Replace the existing `NoOpAuthorizationPlugin` with a real implementation:

```java
public class ScopedAuthorizationPlugin implements AuthorizationPlugin {
    // BEFORE phase - validates agent scopes against requested operation
    // Checks: resource type access, interaction type (CRUD), tenant access
    // Returns ABORT with 403 if insufficient scopes
}
```

### 5.4 Agent Audit Trail

Extend the existing audit log to capture agent-specific metadata:

```sql
ALTER TABLE fhir_audit_log ADD COLUMN agent_id VARCHAR(128);
ALTER TABLE fhir_audit_log ADD COLUMN agent_scopes TEXT[];
ALTER TABLE fhir_audit_log ADD COLUMN tool_invocation_id VARCHAR(256);
ALTER TABLE fhir_audit_log ADD COLUMN mcp_request_id VARCHAR(256);
```

---

## Pillar 6: Bulk Data & Batch Processing for Agents

### Goal
Enable AI agents to efficiently process large volumes of clinical data.

### 6.1 FHIR Bulk Data Export ($export)

Implement the [FHIR Bulk Data Access](https://hl7.org/fhir/uv/bulkdata/) specification:

```
POST /fhir/r5/$export
  ?_type=Patient,Observation,Condition
  &_since=2026-01-01
  &_outputFormat=application/ndjson

→ 202 Accepted
  Content-Location: /api/bulk/status/job-123

GET /api/bulk/status/job-123
→ 200 OK (complete)
{
  "output": [
    { "type": "Patient", "url": "/api/bulk/download/job-123/Patient.ndjson" },
    { "type": "Observation", "url": "/api/bulk/download/job-123/Observation.ndjson" }
  ]
}
```

**Why agents need this:** AI agents doing population health analysis, training data extraction, or cohort analysis need to process thousands to millions of resources efficiently. Individual REST calls are too slow.

### 6.2 Enhanced Batch/Transaction Processing

The existing `BundleController` handles batch/transaction bundles. Enhance with:
- **Async batch processing** - Return 202 with status polling for large bundles
- **Progress tracking** - `GET /api/batch/status/{id}` with percent complete
- **Partial failure handling** - Continue on error with detailed per-entry results
- **Size limits configurable per agent** - Higher limits for trusted agents

### 6.3 NDJSON Streaming

Support NDJSON (Newline Delimited JSON) for streaming large result sets:

```
GET /fhir/r5/Observation?patient=123&_count=10000
Accept: application/ndjson

→ 200 OK
Content-Type: application/ndjson
Transfer-Encoding: chunked

{"resourceType":"Observation","id":"obs-1",...}
{"resourceType":"Observation","id":"obs-2",...}
...
```

---

## Pillar 7: AI Orchestration Layer

### Goal
Provide higher-level abstractions that make it easy for AI agents to perform complex clinical workflows.

### 7.1 Composite Operations (Agent Workflows)

Pre-built composite operations that combine multiple FHIR operations:

```yaml
# fhir-config/workflows/patient-intake.yml
workflow:
  name: patient-intake
  description: "Complete patient intake workflow"
  steps:
    - name: create-patient
      operation: create
      resourceType: Patient
      output: patientId
    - name: create-encounter
      operation: create
      resourceType: Encounter
      input:
        subject: "Patient/${patientId}"
      output: encounterId
    - name: record-vitals
      operation: create
      resourceType: Observation
      input:
        subject: "Patient/${patientId}"
        encounter: "Encounter/${encounterId}"
```

**Endpoint:**
```
POST /api/ai/workflows/patient-intake
{
  "patient": { "name": [{"family": "Smith", "given": ["John"]}] },
  "vitals": { "systolic": 120, "diastolic": 80, "heartRate": 72 }
}
```

### 7.2 Clinical Decision Support Hooks

Implement [CDS Hooks](https://cds-hooks.hl7.org/) for AI-powered clinical decision support:

```
POST /cds-services/medication-check
{
  "hookInstance": "uuid",
  "hook": "medication-prescribe",
  "context": {
    "patientId": "123",
    "medications": [...]
  }
}
```

Agents can register as CDS services:
```
GET /cds-services
→ {
    "services": [
      {
        "id": "ai-medication-interaction",
        "hook": "medication-prescribe",
        "title": "AI Medication Interaction Checker",
        "description": "Uses AI to check for drug interactions"
      }
    ]
  }
```

### 7.3 GraphQL Interface

Add a FHIR GraphQL endpoint for efficient, agent-friendly data fetching:

```
POST /fhir/r5/$graphql
{
  "query": "{ Patient(id: \"123\") { name { family given } birthDate condition: ConditionList(_reference: patient) { code { text } clinicalStatus { coding { code } } } } }"
}
```

Benefits for agents:
- Fetch exactly the fields needed (no over-fetching)
- Traverse references in a single request
- Strongly typed schema that agents can introspect

---

## Implementation Priority & Phasing

### Phase 1: Foundation (Weeks 1-4) - "Agents Can Discover"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| OpenAPI 3.1 generation | fhir4java-api | P0 | 1 week |
| Discovery API endpoints | fhir4java-api | P0 | 1 week |
| OAuth2 resource server | fhir4java-server | P0 | 1 week |
| Agent API key auth | fhir4java-plugin | P1 | 1 week |

**Outcome:** Agents can discover all capabilities, authenticate, and make standard REST calls.

### Phase 2: MCP Integration (Weeks 5-8) - "Agents Can Use Tools"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| MCP server module | fhir4java-mcp (NEW) | P0 | 2 weeks |
| Auto-generated tool definitions | fhir4java-mcp | P0 | 1 week |
| MCP resource providers | fhir4java-mcp | P1 | 1 week |
| Prompt templates | fhir4java-mcp | P2 | 3 days |

**Outcome:** AI agents can connect via MCP and use FHIR operations as native tools.

### Phase 3: Real-Time (Weeks 9-12) - "Agents Can React"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| SSE event streaming | fhir4java-api | P0 | 1 week |
| FHIR Subscriptions | fhir4java-core | P1 | 2 weeks |
| Webhook registry | fhir4java-api | P1 | 1 week |

**Outcome:** Agents can subscribe to real-time clinical data changes.

### Phase 4: Intelligence (Weeks 13-18) - "Agents Can Understand"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| NL-to-FHIR search translation | fhir4java-core | P1 | 2 weeks |
| Vector search (pgvector) | fhir4java-persistence | P2 | 2 weeks |
| Embedding pipeline plugin | fhir4java-plugin | P2 | 1 week |
| Bulk data export ($export) | fhir4java-api | P1 | 2 weeks |

**Outcome:** Agents can query data using natural language and process bulk datasets.

### Phase 5: Orchestration (Weeks 19-24) - "Agents Can Orchestrate"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| Composite workflows | fhir4java-core | P2 | 2 weeks |
| CDS Hooks | fhir4java-api | P2 | 2 weeks |
| GraphQL interface | fhir4java-api | P2 | 2 weeks |
| NDJSON streaming | fhir4java-api | P2 | 1 week |

**Outcome:** Agents can execute complex clinical workflows and efficiently query data.

---

## New Module Structure

```
fhir4java-agents/
├── fhir4java-core/              # (existing) + NL search, subscription topics
├── fhir4java-persistence/       # (existing) + vector embeddings, bulk export
├── fhir4java-api/               # (existing) + discovery, SSE, webhooks, GraphQL
├── fhir4java-plugin/            # (existing) + embedding plugin, subscription plugin
├── fhir4java-mcp/               # NEW - MCP server implementation
│   ├── src/main/java/
│   │   └── org/fhirframework/mcp/
│   │       ├── McpServerAutoConfiguration.java
│   │       ├── server/
│   │       │   ├── FhirMcpServer.java              # Main MCP server
│   │       │   ├── McpTransportConfig.java          # Transport configuration
│   │       │   └── McpSessionManager.java           # Session lifecycle
│   │       ├── tools/
│   │       │   ├── FhirToolGenerator.java           # Auto-generates tools from config
│   │       │   ├── FhirToolExecutor.java            # Executes tools via services
│   │       │   ├── CrudToolProvider.java             # CRUD operation tools
│   │       │   ├── SearchToolProvider.java           # Search tools with params
│   │       │   └── OperationToolProvider.java        # Extended operation tools
│   │       ├── resources/
│   │       │   ├── FhirResourceProvider.java         # FHIR resources as MCP resources
│   │       │   └── ResourceTemplateProvider.java     # URI templates
│   │       └── prompts/
│   │           ├── ClinicalPromptProvider.java       # Clinical prompt templates
│   │           └── PromptTemplateRegistry.java       # Manages prompt templates
│   └── src/main/resources/
│       └── mcp-prompts/                             # Prompt template definitions
├── fhir4java-auth/              # NEW - Auth module (OAuth2, SMART, API keys)
│   └── src/main/java/
│       └── org/fhirframework/auth/
│           ├── AuthAutoConfiguration.java
│           ├── oauth2/
│           │   ├── SmartOnFhirConfig.java
│           │   └── ScopeValidator.java
│           ├── apikey/
│           │   ├── ApiKeyAuthFilter.java
│           │   └── AgentKeyStore.java
│           └── audit/
│               └── AgentAuditEnricher.java
├── fhir4java-server/            # (existing) + new configs
└── fhir4java-ai/                # NEW - AI integration utilities
    └── src/main/java/
        └── org/fhirframework/ai/
            ├── search/
            │   ├── NaturalLanguageSearchService.java
            │   └── QueryDecomposer.java
            ├── embeddings/
            │   ├── EmbeddingService.java
            │   ├── EmbeddingProvider.java         # SPI for embedding providers
            │   └── providers/
            │       ├── OpenAiEmbeddingProvider.java
            │       └── OllamaEmbeddingProvider.java
            └── workflow/
                ├── WorkflowEngine.java
                └── WorkflowDefinition.java
```

---

## Configuration

New configuration sections in `application.yml`:

```yaml
fhir4java:
  # Existing config...

  # NEW: MCP Server Configuration
  mcp:
    enabled: true
    server-name: "FHIR4Java Clinical Data Server"
    server-version: "1.0.0"
    transports:
      http:
        enabled: true
        path: /mcp
      sse:
        enabled: true
        path: /mcp/sse
    tools:
      auto-generate: true           # Generate tools from ResourceRegistry
      include-operations: true       # Include extended operations as tools
      include-search: true           # Include search as tools
    resources:
      expose-fhir-resources: true    # Expose FHIR resources as MCP resources
    prompts:
      enabled: true
      directory: classpath:mcp-prompts/

  # NEW: AI Features Configuration
  ai:
    natural-language-search:
      enabled: false
      provider: rule-based           # rule-based or llm
    embeddings:
      enabled: false
      provider: openai
      model: text-embedding-3-small
    semantic-search:
      enabled: false
      similarity-threshold: 0.75

  # NEW: Event/Subscription Configuration
  events:
    sse:
      enabled: true
      path: /api/events/stream
    subscriptions:
      enabled: false
      max-per-tenant: 100
      channels: [rest-hook, sse, websocket]
    webhooks:
      enabled: false
      max-per-tenant: 50
      retry-count: 3

  # NEW: Auth Configuration
  auth:
    mode: none                       # none, api-key, oauth2, smart-on-fhir
    oauth2:
      issuer-uri: ""
      audience: fhir4java
    api-keys:
      enabled: false
      header: X-API-Key
    smart:
      enabled: false
      scopes-supported:
        - patient/*.read
        - patient/*.write
        - system/*.read

  # NEW: Bulk Data Configuration
  bulk:
    enabled: false
    storage: local                   # local, s3
    output-format: ndjson
    max-concurrent-exports: 5
```

---

## Key Design Principles

1. **Everything is opt-in** - All AI features are disabled by default. Enable what you need via configuration. The server remains a standard FHIR server unless you turn on AI features.

2. **Auto-generation over manual wiring** - MCP tools, OpenAPI specs, and discovery responses are generated from the existing `ResourceRegistry` and config files. No duplication.

3. **Plugin-first extensibility** - New AI capabilities (embeddings, subscriptions, NL search) are implemented as plugins that hook into the existing `PluginOrchestrator` lifecycle.

4. **Security by default** - Agent access is scoped, audited, and rate-limited. No default open access to AI features.

5. **Standard protocols** - MCP for agent integration, OAuth2/SMART for auth, FHIR Subscriptions for events, OpenAPI for discovery. No proprietary protocols.

6. **Tenant-aware AI** - All AI features (embeddings, subscriptions, agent scopes) respect multi-tenancy. An agent authorized for tenant A cannot access tenant B's data.

---

## Dependencies to Add

| Dependency | Version | Purpose |
|------------|---------|---------|
| `spring-ai-core` | 1.0+ | AI abstractions (embedding models, etc.) |
| `mcp-spring-server` | 0.10+ | MCP server SDK for Java/Spring |
| `springdoc-openapi-starter-webmvc-api` | 2.8+ | OpenAPI generation |
| `spring-boot-starter-oauth2-resource-server` | 3.4+ | OAuth2 token validation |
| `pgvector-java` | 0.1+ | PostgreSQL vector operations |
| `spring-boot-starter-webflux` | 3.4+ | SSE support (reactive streams) |

---

## Success Metrics

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Tool discovery time | < 2 seconds | Time from MCP connect to tools/list response |
| Agent onboarding | < 5 minutes | Time for a new agent to make its first FHIR query via MCP |
| Search accuracy (NL) | > 85% | NL queries correctly translated to FHIR search |
| Event delivery latency | < 500ms | Time from resource change to SSE/webhook delivery |
| Embedding throughput | > 100 resources/sec | Resources embedded per second |
| Zero-config tools | 100% | Percentage of configured resources with auto-generated MCP tools |

---

## Summary

This design transforms FHIR4Java from a **FHIR server that agents can call** into a **FHIR platform that agents natively integrate with**. The key differentiator is that AI capabilities are **generated from the existing configuration** rather than manually coded — when you define a new resource in YAML, it automatically becomes an MCP tool, appears in OpenAPI docs, gets discovery endpoints, supports subscriptions, and can be semantically searched.

The phased approach ensures the server remains production-stable while incrementally adding AI capabilities, with each phase delivering standalone value.
