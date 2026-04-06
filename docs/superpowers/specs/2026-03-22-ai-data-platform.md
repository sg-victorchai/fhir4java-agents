# AI-Ready Platform Design: FHIR4Java as a Native AI Agent Data Platform

**Status:** DRAFT - Ready for Review
**Created:** 2026-03-22
**Last Updated:** 2026-04-06

## Executive Summary

This document proposes the enhancements needed to transform FHIR4Java from a traditional FHIR server into a **fully AI-ready data and API platform** that natively integrates within AI agent ecosystems and supports a next-generation clinical UI.

The design covers **ten pillars**:

| # | Pillar | Purpose |
|---|--------|---------|
| 1 | **MCP Server** | Expose FHIR as AI tools (3 unified tools: discover, query, mutate) + safety features (dry-run, risk-level) |
| 2 | **Event-Driven Architecture** | Real-time updates via FHIR Subscriptions, SSE, WebSocket |
| 3 | **Semantic Search** | Natural language queries + vector search with medical embeddings + hybrid search |
| 4 | **Agent-Friendly Auth** | OAuth2/SMART + API keys + Consent enforcement + Provenance tracking + data localization |
| 5 | **Bulk Data Processing** | $export + NDJSON streaming + de-identification profiles |
| 6 | **AI Orchestration** | Composite workflows, CDS Hooks, GraphQL |
| 7 | **Command API** | Natural language command interface for clinical UI |
| 8 | **UI Configuration** | Multi-level config system (system → tenant → role → user) |
| 9 | **Clinical Workflow** | Queue management, note lifecycle, pending results tracking |
| 10 | **Observability** | MCP audit logging, explainability metadata, immutable audit trail, metrics |

Pillars 1-6 and 10 serve **AI agents** (Claude, GPT, custom agents).
Pillars 7-9 serve the **clinical web UI** and its backend requirements.

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
| No event streaming (SSE/WebSocket) | Agents can't react to real-time data changes |
| No semantic/vector search | Agents can't do natural language queries over clinical data |
| No agent-scoped auth (OAuth2 + scopes) | No fine-grained agent authorization |
| No bulk data export ($export) | Agents can't efficiently process large datasets |
| No FHIR Subscriptions | No push-based notifications for agent workflows |
| No structured tool descriptions | Agents can't self-discover what tools/operations exist |

### What's Missing for Clinical UI Support
| Gap | Impact |
|-----|--------|
| No Command API | UI can't send natural language commands to backend |
| No command interpretation | No tiered pipeline (cache → pattern → semantic → LLM) |
| No UI configuration service | No multi-level panel/view customization |
| No queue management | No ambulatory workflow tracking |
| No note lifecycle service | No pended/signed/addendum state management |
| No pending results tracking | Can't coordinate note completion with result arrival |
| No frontend module | No React-based clinical UI |

---

## Pillar 1: MCP Server Interface (Expose FHIR as AI Tools)

### Goal
Expose FHIR4Java as an **MCP (Model Context Protocol) server** so that any MCP-compatible AI agent (Claude, GPT, custom agents) can discover and invoke FHIR operations as tools.

### Tool Design: Hybrid B2+ (3 Unified Tools)

#### Design Rationale

A "many tools" approach (auto-generating per-resource, per-interaction tools) would produce **50-90+ tools** for the current configuration (11 resources x 8 interaction types + 64 operations), growing linearly as resources are added. This causes:
- **Massive token overhead** — ~10,000-18,000 tokens of tool definitions injected into every LLM turn
- **Degraded tool selection accuracy** — LLM research shows accuracy drops significantly at 50+ tools
- **Poor scalability** — enabling all 453 FHIR resource types would explode to thousands of tools

Instead, we use a **Hybrid B2+** approach: **3 unified tools** that handle all FHIR interactions. This mirrors FHIR's own design — a unified REST API with `resourceType` as a parameter — and keeps tool definitions constant regardless of how many resources are configured.

| Approach | Tool Count | Tokens per Turn | Scales With Resources? |
|----------|-----------|-----------------|----------------------|
| Many tools (per-resource) | 50-90+ | ~10,000-18,000 | No (linear growth) |
| **Hybrid B2+ (chosen)** | **3** | **~600-900** | **Yes (constant)** |

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  AI Agent (Claude, GPT, Custom)                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  MCP Client                                            │  │
│  │  Uses 3 tools: fhir_discover → fhir_query → fhir_mutate│  │
│  └──────────────────────┬─────────────────────────────────┘  │
└─────────────────────────┼────────────────────────────────────┘
                          │ MCP Protocol (Streamable HTTP / SSE / stdio)
┌─────────────────────────┼────────────────────────────────────┐
│  FHIR4Java MCP Server Layer (NEW)                            │
│  ┌──────────────────────┴─────────────────────────────────┐  │
│  │  McpServerEndpoint                                     │  │
│  │  ├── 3 Tools (discover, query, mutate)                 │  │
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

#### 1.1 The Three MCP Tools

##### Tool 1: `fhir_discover` — Capability & Schema Discovery

Solves the main weakness of unified tools: agents can learn what resources, search parameters, and operations are available before making calls.

```json
{
  "name": "fhir_discover",
  "description": "Discover available FHIR resources, search parameters, operations, and their usage. Call this first to learn what the server supports before querying or mutating data.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "topic": {
        "type": "string",
        "enum": ["resources", "searchParams", "operations", "all"],
        "description": "What to discover. 'resources' lists all resource types and their enabled interactions. 'searchParams' lists search parameters for a resource type with types, modifiers, and examples. 'operations' lists available extended operations. 'all' returns a complete summary.",
        "default": "all"
      },
      "resourceType": {
        "type": "string",
        "description": "Filter discovery to a specific resource type (e.g., 'Patient', 'Observation'). If omitted, returns info for all resource types."
      },
      "fhirVersion": {
        "type": "string",
        "enum": ["R5", "R4B"],
        "description": "FHIR version context. Defaults to the server's default version.",
        "default": "R5"
      }
    }
  }
}
```

**Example interaction:**
```
Agent calls: fhir_discover(topic: "searchParams", resourceType: "Patient")

Response:
{
  "resourceType": "Patient",
  "fhirVersion": "R5",
  "searchParameters": [
    {
      "name": "family",
      "type": "string",
      "description": "A portion of the family name of the patient",
      "modifiers": [":exact", ":contains", ":missing"],
      "examples": ["family=Smith", "family:exact=O'Brien", "family:contains=smi"]
    },
    {
      "name": "birthdate",
      "type": "date",
      "description": "The patient's date of birth",
      "prefixes": ["eq", "ne", "lt", "gt", "le", "ge", "sa", "eb", "ap"],
      "examples": ["birthdate=1990-01-01", "birthdate=ge1980-01-01", "birthdate=le2000-12-31"]
    },
    {
      "name": "identifier",
      "type": "token",
      "description": "A patient identifier",
      "modifiers": [":exact", ":text", ":not", ":missing"],
      "examples": ["identifier=http://hospital.org|12345", "identifier=12345"]
    }
  ],
  "interactions": ["read", "vread", "create", "update", "patch", "search", "history"],
  "operations": [
    {
      "name": "$everything",
      "scope": ["type", "instance"],
      "description": "Return all resources related to this patient"
    },
    {
      "name": "$merge",
      "scope": ["type", "instance"],
      "description": "Merge duplicate patient records"
    }
  ],
  "hint": "Use fhir_query to search or read Patient resources. Use fhir_mutate to create or update."
}
```

##### Tool 2: `fhir_query` — Read-Only Operations

Handles all non-mutating FHIR interactions: read, vread, search, history, and read-only extended operations.

```json
{
  "name": "fhir_query",
  "description": "Query FHIR data. Supports read (by ID), vread (specific version), search (with parameters), history, and read-only operations like $everything. Use fhir_discover first to learn available search parameters and operations for a resource type.",
  "inputSchema": {
    "type": "object",
    "required": ["action", "resourceType"],
    "properties": {
      "action": {
        "type": "string",
        "enum": ["read", "vread", "search", "history", "operation"],
        "description": "The query action to perform"
      },
      "resourceType": {
        "type": "string",
        "description": "FHIR resource type (e.g., 'Patient', 'Observation', 'Condition')"
      },
      "id": {
        "type": "string",
        "description": "Resource ID. Required for read, vread, history, and instance-level operations."
      },
      "versionId": {
        "type": "string",
        "description": "Specific version to read (vread only)"
      },
      "searchParams": {
        "type": "object",
        "additionalProperties": { "type": "string" },
        "description": "Search parameters as key-value pairs. Use fhir_discover to learn valid parameters. Examples: {\"family\": \"Smith\", \"birthdate\": \"ge1980-01-01\"}"
      },
      "operation": {
        "type": "string",
        "description": "Extended operation name without $ prefix (e.g., 'everything', 'validate'). For read-only operations."
      },
      "operationParams": {
        "type": "object",
        "additionalProperties": true,
        "description": "Parameters for the extended operation"
      },
      "fhirVersion": {
        "type": "string",
        "enum": ["R5", "R4B"],
        "default": "R5"
      },
      "_count": {
        "type": "integer",
        "description": "Maximum number of results per page (search only)",
        "default": 20
      },
      "_offset": {
        "type": "integer",
        "description": "Starting offset for pagination (search only)",
        "default": 0
      }
    }
  }
}
```

**Example interactions:**
```
# Read a patient
fhir_query(action: "read", resourceType: "Patient", id: "123")

# Search for patients
fhir_query(action: "search", resourceType: "Patient",
           searchParams: {"family": "Smith", "birthdate": "ge1980-01-01"})

# Get patient's full record
fhir_query(action: "operation", resourceType: "Patient", id: "123",
           operation: "everything")

# Search observations with token search
fhir_query(action: "search", resourceType: "Observation",
           searchParams: {"patient": "Patient/123", "code": "http://loinc.org|2339-0"})
```

##### Tool 3: `fhir_mutate` — Write Operations

Handles all state-changing FHIR interactions: create, update, patch, delete, and write operations.

```json
{
  "name": "fhir_mutate",
  "description": "Create, update, patch, or delete FHIR resources, and execute write operations like $merge. Returns the resulting resource or operation outcome.",
  "inputSchema": {
    "type": "object",
    "required": ["action", "resourceType"],
    "properties": {
      "action": {
        "type": "string",
        "enum": ["create", "update", "patch", "delete", "operation"],
        "description": "The mutation to perform"
      },
      "resourceType": {
        "type": "string",
        "description": "FHIR resource type (e.g., 'Patient', 'Observation')"
      },
      "id": {
        "type": "string",
        "description": "Resource ID. Required for update, patch, delete, and instance-level operations."
      },
      "body": {
        "type": "object",
        "description": "The FHIR resource JSON body (for create, update) or JSON Patch array (for patch)"
      },
      "operation": {
        "type": "string",
        "description": "Extended operation name without $ prefix (e.g., 'merge', 'validate')"
      },
      "operationParams": {
        "type": "object",
        "additionalProperties": true,
        "description": "Parameters for the extended operation"
      },
      "fhirVersion": {
        "type": "string",
        "enum": ["R5", "R4B"],
        "default": "R5"
      }
    }
  }
}
```

**Example interactions:**
```
# Create a patient
fhir_mutate(action: "create", resourceType: "Patient",
            body: {"resourceType": "Patient", "name": [{"family": "Smith", "given": ["John"]}]})

# Update a patient
fhir_mutate(action: "update", resourceType: "Patient", id: "123",
            body: {"resourceType": "Patient", "id": "123", "name": [{"family": "Smith", "given": ["Jane"]}]})

# Delete a resource
fhir_mutate(action: "delete", resourceType: "Observation", id: "obs-456")

# Merge patients
fhir_mutate(action: "operation", resourceType: "Patient",
            operation: "merge",
            operationParams: {"source": "Patient/456", "target": "Patient/123"})
```

#### 1.2 Agent Workflow: Discover → Query → Mutate

A typical agent session follows this pattern:

```
Step 1: Agent connects via MCP, receives 3 tool definitions (~600 tokens)

Step 2: Agent calls fhir_discover(topic: "all")
        → Learns: 11 resource types, their search params, operations

Step 3: Agent calls fhir_discover(resourceType: "Patient", topic: "searchParams")
        → Learns: Patient has family, given, birthdate, identifier, etc.

Step 4: Agent calls fhir_query(action: "search", resourceType: "Patient",
                               searchParams: {"family": "Smith"})
        → Gets search results

Step 5: Agent calls fhir_query(action: "operation", resourceType: "Patient",
                               id: "123", operation: "everything")
        → Gets full patient record

Step 6: Agent calls fhir_mutate(action: "create", resourceType: "Observation",
                                body: {...})
        → Creates new observation
```

#### 1.3 Smart Response Enrichment

Tool responses include contextual hints to guide the agent's next action, reducing the need for repeated discovery calls:

```json
{
  "result": { "resourceType": "Patient", "id": "123", "..." : "..." },
  "_meta": {
    "action": "read",
    "fhirVersion": "R5",
    "versionId": 3,
    "lastUpdated": "2026-03-16T10:30:00Z"
  },
  "_hints": {
    "availableActions": ["update", "patch", "delete", "history"],
    "relatedOperations": ["$everything", "$merge"],
    "relatedSearches": {
      "observations": "fhir_query(action:'search', resourceType:'Observation', searchParams:{patient:'Patient/123'})",
      "conditions": "fhir_query(action:'search', resourceType:'Condition', searchParams:{patient:'Patient/123'})"
    }
  }
}
```

#### 1.4 Transport Support

| Transport | Use Case | Priority |
|-----------|----------|----------|
| **Streamable HTTP** | Production deployments, web-based agents | P0 |
| **SSE** | Legacy MCP clients, real-time streaming | P1 |
| **stdio** | Local development, CLI-based agents | P1 |

#### 1.5 Validation & Error Handling

Since unified tools have a broader input surface, the tool executor performs runtime validation and returns clear, actionable error messages:

```json
{
  "error": true,
  "code": "INVALID_SEARCH_PARAM",
  "message": "Search parameter 'family' is not valid for resource type 'Observation'.",
  "suggestion": "Valid search parameters for Observation include: patient, code, category, date, status, value-quantity. Use fhir_discover(resourceType: 'Observation', topic: 'searchParams') for the full list.",
  "validParams": ["patient", "code", "category", "date", "status", "value-quantity", "..."]
}
```

This compensates for the lack of per-resource schema validation by providing discovery-guided error recovery.

#### 1.6 Dry-Run Mode for Safe Mutations

The `fhir_mutate` tool supports a `dryRun` parameter that validates and simulates the mutation without persisting changes:

```json
{
  "name": "fhir_mutate",
  "inputSchema": {
    "properties": {
      "dryRun": {
        "type": "boolean",
        "description": "If true, validate the mutation and return what would happen without persisting. Use this to preview changes before committing.",
        "default": false
      }
      // ... other existing properties
    }
  }
}
```

**Dry-run response includes:**
```json
{
  "dryRun": true,
  "wouldCreate": {
    "resourceType": "MedicationRequest",
    "id": "(generated)",
    "status": "active",
    "...": "..."
  },
  "validation": {
    "valid": true,
    "warnings": [
      {
        "severity": "warning",
        "code": "informational",
        "details": "No allergy check performed - patient has no recorded allergies"
      }
    ]
  },
  "riskAssessment": {
    "level": "MEDIUM",
    "factors": ["medication-order", "no-prior-prescription"]
  },
  "terminologyValidation": {
    "valid": true,
    "checkedCodes": [
      { "system": "http://www.nlm.nih.gov/research/umls/rxnorm", "code": "313782", "display": "Lisinopril 10 MG", "status": "VALID" }
    ]
  },
  "_hints": {
    "toCommit": "Call fhir_mutate again with dryRun: false to persist this change"
  }
}
```

**Why dry-run matters for agents:**
- Prevents accidental mutations from misinterpretation
- Allows human review before committing high-risk changes
- Enables "what-if" exploration without side effects
- Provides validation feedback before persistence

#### 1.7 Risk-Level Tagging

All tool responses include a risk assessment to help agents and downstream systems understand the safety implications:

**Response header:**
```
X-MCP-Risk-Level: MEDIUM
```

**Response body:**
```json
{
  "result": { "..." },
  "_meta": {
    "riskLevel": "MEDIUM",
    "riskFactors": ["creates-order", "controlled-substance"],
    "requiresReview": true
  }
}
```

**Risk levels:**

| Level | Definition | Examples | Agent Behavior |
|-------|------------|----------|----------------|
| **NONE** | Read-only, no side effects | `fhir_discover`, `fhir_query` (search/read) | Proceed automatically |
| **LOW** | Routine data modification | Update demographics, add note | Proceed with audit |
| **MEDIUM** | Clinical data modification | Create observation, update condition | Consider human review |
| **HIGH** | Order entry, prescriptions | Create MedicationRequest, ServiceRequest | Recommend confirmation |
| **CRITICAL** | Irreversible or high-impact | Delete resources, administer controlled substance | Require explicit confirmation |

**Risk assessment factors:**

```java
public class RiskAssessor {

    public RiskAssessment assess(McpToolInvocation invocation) {
        List<String> factors = new ArrayList<>();
        RiskLevel level = RiskLevel.NONE;

        // Tool-based risk
        if ("fhir_mutate".equals(invocation.getTool())) {
            level = RiskLevel.LOW;
            factors.add("mutates-data");

            // Action-based escalation
            if ("delete".equals(invocation.getAction())) {
                level = RiskLevel.CRITICAL;
                factors.add("delete-operation");
            }
        }

        // Resource-based escalation
        String resourceType = invocation.getResourceType();
        if (HIGH_RISK_RESOURCES.contains(resourceType)) {
            level = level.escalate();
            factors.add("high-risk-resource:" + resourceType);
        }

        // Content-based escalation (e.g., controlled substances)
        if (involvesControlledSubstance(invocation)) {
            level = RiskLevel.HIGH;
            factors.add("controlled-substance");
        }

        return new RiskAssessment(level, factors, level.compareTo(RiskLevel.MEDIUM) >= 0);
    }

    private static final Set<String> HIGH_RISK_RESOURCES = Set.of(
        "MedicationRequest", "MedicationAdministration",
        "ServiceRequest", "Procedure", "Immunization"
    );
}
```

#### 1.8 Rate Limiting

> **Infrastructure Note:** Rate limiting for MCP endpoints is handled at the **API Gateway layer**, not within the application.

**Architecture:**
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   AI Agent      │────▶│  API Gateway    │────▶│  FHIR4Java      │
│                 │     │  (Rate Limit)   │     │  MCP Server     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                              │
                              ▼
                        ┌─────────────────┐
                        │ Rate limit by:  │
                        │ - API Key       │
                        │ - Agent ID      │
                        │ - Tenant        │
                        │ - IP Address    │
                        └─────────────────┘
```

**API Gateway handles:**
- Per-agent rate limits (configured via agent API key metadata)
- Per-tenant aggregate limits
- Burst handling with token bucket algorithm
- Distributed rate limiting across multiple gateway instances
- Standard 429 Too Many Requests response with `Retry-After` header

**Application-level configuration** (used by API Gateway for policy lookup):
```yaml
fhir4java:
  auth:
    api-keys:
      agents:
        - id: clinical-summary-agent
          rate-limit: 100/minute      # Enforced by API Gateway
          burst-limit: 20             # Max requests in burst window
        - id: bulk-export-agent
          rate-limit: 10/minute       # Lower rate for heavy operations
          burst-limit: 5
```

This separation ensures rate limiting scales horizontally without application-level coordination overhead.

#### 1.9 Mutation Safety & Data Integrity

> **Design Note:** The `fhir_mutate` tool does **not expose DELETE operations** at the application level. All data modifications follow an immutable, version-based pattern.

**Mutation Model:**

| Operation | Behavior | Data Impact |
|-----------|----------|-------------|
| `create` | Insert new resource | New row in `fhir_resource`, version 1 |
| `update` | Insert new version | Current row marked `is_current=false`, new row with incremented version |
| `patch` | Insert new version | Same as update, with JSON Patch applied |
| `delete` | **Not exposed via MCP** | Agents cannot delete resources |

**Why no DELETE for AI agents:**

1. **Clinical data integrity**: Medical records should never be permanently deleted; they require complete audit trails
2. **Regulatory compliance**: HIPAA, PDPA, and other regulations require data retention
3. **Agent safety**: Prevents accidental bulk deletion from misinterpreted commands

**Soft delete pattern (admin-only, not via MCP):**
```sql
-- Resources are never physically deleted
-- "Deletion" is a status change with full history preserved
UPDATE fhir_resource
SET is_deleted = true,
    deleted_at = NOW(),
    deleted_by = :admin_user
WHERE resource_id = :id;

-- History table preserves all versions indefinitely
INSERT INTO fhir_resource_history
SELECT * FROM fhir_resource WHERE resource_id = :id;
```

**Version history guarantees:**
- Every `update` and `patch` creates a new version in `fhir_resource_history`
- Previous versions remain accessible via `vread` (`fhir_query` with `action: "vread"`)
- Complete audit trail from creation through all modifications
- `_history` endpoint returns full version chain

This architecture ensures AI agents **cannot cause data loss** — they can only create new versions of existing data.

#### 1.10 Standard FHIR Discovery (Non-AI Consumers)

> **Note:** Discovery for non-MCP consumers is handled by **existing standard FHIR features**, not new AI-specific pillars.

| Consumer Type | Discovery Mechanism | Status |
|---------------|---------------------|--------|
| **AI Agents (MCP)** | `fhir_discover` tool | Covered by Pillar 1 |
| **FHIR Systems (EHRs)** | `CapabilityStatement` at `/metadata` | Existing FHIR4Java feature |
| **REST Developers** | OpenAPI via `springdoc-openapi` | Standard Spring dependency |

The `fhir_discover` tool is backed by an internal **DiscoveryService** that reads from `ResourceRegistry`, `SearchParameterRegistry`, and operation configs. This same data source can generate CapabilityStatement and OpenAPI output, but those are standard FHIR/REST features rather than AI-specific capabilities.

---

## Pillar 2: Event-Driven Architecture (Real-Time Agent Integration)

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

### 3.4 Clinical UI Event Types

Beyond generic FHIR resource events, the clinical UI requires specific event types for real-time workflow support:

| Event Type | Trigger | Payload | Use Case |
|------------|---------|---------|----------|
| `result-received` | DiagnosticReport/Observation created | Report, related orders, isCritical, interpretation | Notify clinician when lab/imaging results arrive |
| `order-status-changed` | ServiceRequest status update | Previous status, new status, order details | Track order progress in WIP panel |
| `critical-value` | Abnormal result detected | Observation, value, normal range, severity | Immediate alert for critical lab values |
| `note-updated` | Composition modified | Change type (draft/pend/sign), updatedBy | Real-time note collaboration awareness |
| `queue-updated` | Encounter subjectStatus changed | Encounter, previous/new status, wait time | Update queue dashboard |
| `results-complete` | All pending results for note received | Composition, completedOrders | Prompt for note completion |

#### WebSocket Event Format

```typescript
// WebSocket message structure
interface ClinicalEvent {
  type: 'result-received' | 'order-status-changed' | 'critical-value' |
        'note-updated' | 'queue-updated' | 'results-complete';
  timestamp: string;
  tenantId: string;
  data: ResultReceivedData | OrderStatusData | CriticalValueData |
        NoteUpdatedData | QueueUpdatedData | ResultsCompleteData;
}

// Example: result-received
interface ResultReceivedData {
  observationId: string;
  diagnosticReportId?: string;
  patientId: string;
  encounterId?: string;
  testName: string;
  value: string;
  unit?: string;
  interpretation: 'normal' | 'abnormal' | 'critical';
  isCritical: boolean;
  relatedOrders: string[];      // ServiceRequest references
  pendingNoteId?: string;       // Composition awaiting this result
}

// Example: queue-updated
interface QueueUpdatedData {
  encounterId: string;
  patientId: string;
  previousStatus: string;
  newStatus: string;
  roomAssignment?: string;
  waitTimeMinutes?: number;
  assignedProvider?: string;
}
```

#### Subscription Topics for Clinical UI

```
patient/{id}/results         # Results for specific patient
patient/{id}/orders          # Order status changes
encounter/{id}/events        # All events for encounter
department/{id}/queue        # Queue changes for department
provider/{id}/queue          # Queue for specific provider
alerts/critical              # All critical value alerts (tenant-wide)
notes/{id}/updates           # Real-time note updates
```

#### WebSocket Authentication

WebSocket connections cannot include OAuth2 tokens in headers after the initial handshake. Use **ticket-based authentication**:

**Authentication Flow:**
```
┌─────────────┐    1. Request ticket (with OAuth token)    ┌──────────────┐
│   Client    │ ─────────────────────────────────────────▶ │  REST API    │
│             │ ◀───────────────────────────────────────── │              │
│             │    2. Return one-time ticket (JWT, 30s)    │              │
└─────────────┘                                            └──────────────┘
       │
       │ 3. Connect WebSocket with ticket
       │    ws://server/ws/events?ticket=eyJhbG...
       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  WebSocket Server                                                        │
│  4. Validate ticket (signature, expiry, one-time use)                   │
│  5. Extract user context (tenant, user, scopes)                         │
│  6. Establish authenticated session                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

**Ticket endpoint:**
```
POST /api/auth/ws-ticket
Authorization: Bearer <oauth_token>

Response:
{
  "ticket": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 30,
  "websocketUrl": "wss://server/ws/events"
}
```

**Ticket validation:**
```java
@Component
public class WebSocketTicketValidator {

    private final Set<String> usedTickets = ConcurrentHashMap.newKeySet();

    public AuthContext validateTicket(String ticket) {
        // 1. Verify JWT signature
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(ticket)
            .getBody();

        // 2. Check expiry (30 second window)
        if (claims.getExpiration().before(new Date())) {
            throw new AuthenticationException("Ticket expired");
        }

        // 3. Ensure one-time use
        String ticketId = claims.getId();
        if (!usedTickets.add(ticketId)) {
            throw new AuthenticationException("Ticket already used");
        }

        // 4. Extract auth context
        return AuthContext.builder()
            .tenantId(claims.get("tenant_id", String.class))
            .userId(claims.getSubject())
            .scopes(claims.get("scopes", List.class))
            .build();
    }
}
```

**Configuration:**
```yaml
fhir4java:
  websocket:
    auth:
      ticket-expiry-seconds: 30
      require-https: true
    endpoints:
      events: /ws/events
      clinical: /ws/clinical
```

#### WebSocket Handler

```java
@Component
public class ClinicalWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Extract subscriptions from query params
        // e.g., /ws/events?patient=Patient/123&topics=results,orders
        String patientId = getQueryParam(session, "patient");
        List<String> topics = getQueryParams(session, "topics");

        subscriptionManager.subscribe(session, patientId, topics);
    }

    public void publishEvent(ClinicalEvent event) {
        // Find all sessions subscribed to this event's topics
        Set<WebSocketSession> sessions = subscriptionManager.getSubscribers(event);
        for (WebSocketSession session : sessions) {
            session.sendMessage(new TextMessage(serialize(event)));
        }
    }
}
```

---

## Pillar 3: Semantic Search & Natural Language Query

### Goal
Allow AI agents to query clinical data using natural language, not just structured FHIR search parameters.

### 3.1Natural Language to FHIR Search Translation

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
│  (uses DiscoveryService)│  per resource type
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

**LLM Invocation Decision Tree:**

The NL-to-FHIR translation uses a tiered approach to minimize LLM costs:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    QUERY INTERPRETATION PIPELINE                         │
└─────────────────────────────────────────────────────────────────────────┘

User Query: "Find diabetic patients over 65"
                    │
                    ▼
┌───────────────────────────────────────┐
│  TIER 1: Pattern Matching (No LLM)    │  Cost: $0, Latency: <10ms
│                                        │
│  • Regex patterns for common queries   │
│  • "patients with [condition]"         │
│  • "[resource] where [field] = [val]"  │
│  • Cached query templates              │
└───────────────────┬───────────────────┘
                    │ No match?
                    ▼
┌───────────────────────────────────────┐
│  TIER 2: Rule-Based NLP (No LLM)      │  Cost: $0, Latency: 20-50ms
│                                        │
│  • Clinical concept extraction         │
│  • SNOMED/LOINC code lookup            │
│  • SearchParameterRegistry mapping     │
│  • Handles 70-80% of clinical queries  │
└───────────────────┬───────────────────┘
                    │ Ambiguous or complex?
                    ▼
┌───────────────────────────────────────┐
│  TIER 3: LLM Fallback (Optional)      │  Cost: ~$0.01-0.05, Latency: 500-2000ms
│                                        │
│  • Complex multi-resource joins        │
│  • Temporal reasoning ("last 3 months")│
│  • Negation ("patients NOT on insulin")│
│  • Only invoked if enabled in config   │
└───────────────────────────────────────┘
```

**When LLM is invoked (Tier 3):**

| Query Type | Example | Why LLM needed |
|------------|---------|----------------|
| Complex temporal | "Patients with HbA1c trending upward over past year" | Requires reasoning about trends |
| Negation | "Diabetics not currently on metformin" | Negation logic |
| Implicit joins | "Patients whose doctor is in cardiology" | Multi-hop relationship |
| Ambiguous terms | "Patients with heart problems" | Needs clinical disambiguation |

**When LLM is NOT invoked (Tiers 1-2):**

| Query Type | Example | Rule-based handling |
|------------|---------|---------------------|
| Direct lookup | "Patient with ID 12345" | Regex pattern |
| Code-based | "Patients with ICD-10 E11.9" | Direct code search |
| Simple condition | "Diabetic patients" | SNOMED lookup (73211009) |
| Age filter | "Patients over 65" | Birthdate calculation |

**Configuration:**

```yaml
fhir4java:
  ai:
    nl-search:
      llm-fallback:
        enabled: false                    # Disable LLM by default (rule-based only)
        provider: openai                  # openai, azure-openai, aws-bedrock, anthropic
        model: gpt-4o-mini                # Use smaller model for query translation
        max-tokens: 500
        timeout-ms: 3000
      pattern-cache:
        enabled: true
        max-entries: 10000
      rule-engine:
        snomed-lookup: true
        loinc-lookup: true
```

**Important:** The LLM here is for **query translation** (understanding user intent), NOT for embedding generation. These are separate:

| Purpose | Model Type | Cost | When Called |
|---------|------------|------|-------------|
| Query translation | LLM (GPT-4, Claude) | ~$0.01-0.05/query | Only Tier 3 fallback |
| Embedding generation | Embedding model (text-embedding-3-small) | ~$0.00002/1K tokens | Every semantic search |

### 3.2Vector Search for Clinical Narratives

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

### 3.3Embedding Pipeline Plugin

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

### 3.4Medical-Domain Embedding Providers (SPI)

General-purpose embedding models (OpenAI, Ollama) may not capture clinical semantics as well as domain-specific models. The embedding system uses a **pluggable SPI** to support medical-domain models:

**Available providers:**

| Provider | Model | Dimensions | Strengths | Use Case |
|----------|-------|------------|-----------|----------|
| `openai` | text-embedding-3-small | 1536 | General purpose, high quality | Default, broad coverage |
| `openai` | text-embedding-3-large | 3072 | Higher accuracy, more expensive | High-precision search |
| `aws-bedrock` | amazon.titan-embed-text-v2 | 1024 | AWS-native, no data leaves AWS | AWS-hosted deployments |
| `aws-bedrock` | cohere.embed-english-v3 | 1024 | Medical-aware, multilingual | Enterprise healthcare |
| `azure-openai` | text-embedding-3-small | 1536 | Azure-native, HIPAA-compliant | Azure-hosted deployments |
| `gcp-vertex` | text-embedding-004 | 768 | GCP-native, low latency | GCP-hosted deployments |
| `clinical-bert` | ClinicalBERT | 768 | Trained on clinical notes (MIMIC-III) | Discharge summaries, narratives |
| `bio-bert` | BioBERT | 768 | Trained on PubMed, PMC | Research, medical literature |
| `ollama` | Any local model | varies | Privacy, no external API calls | On-premise deployments |
| `custom` | User-provided | varies | Organization-specific fine-tuning | Custom requirements |

**Embedding Provider SPI:**
```java
public interface EmbeddingProvider {

    /**
     * Provider identifier used in configuration
     */
    String getProviderId();

    /**
     * Generate embeddings for text
     */
    float[] embed(String text);

    /**
     * Batch embedding for efficiency
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Embedding dimensions (needed for table schema)
     */
    int getDimensions();

    /**
     * Whether this provider supports clinical text optimization
     */
    default boolean isClinicalOptimized() {
        return false;
    }
}

// Implementation example: ClinicalBERT via HuggingFace
@Component
@ConditionalOnProperty(name = "fhir4java.ai.embeddings.provider", havingValue = "clinical-bert")
public class ClinicalBertEmbeddingProvider implements EmbeddingProvider {

    private final HuggingFaceClient hfClient;

    @Override
    public String getProviderId() { return "clinical-bert"; }

    @Override
    public float[] embed(String text) {
        // Call HuggingFace Inference API or local model
        return hfClient.embed("emilyalsentzer/Bio_ClinicalBERT", text);
    }

    @Override
    public int getDimensions() { return 768; }

    @Override
    public boolean isClinicalOptimized() { return true; }
}
```

**Configuration for embedding providers:**

```yaml
fhir4java:
  ai:
    embeddings:
      enabled: true
      provider: aws-bedrock           # Active provider
      providers:
        # OpenAI (direct API)
        openai:
          api-key: ${OPENAI_API_KEY}
          model: text-embedding-3-small
          dimensions: 1536

        # AWS Bedrock (uses default AWS credential chain)
        aws-bedrock:
          region: ${AWS_REGION:us-east-1}
          model: amazon.titan-embed-text-v2:0
          dimensions: 1024
          # Optional: assume role for cross-account access
          # role-arn: arn:aws:iam::123456789:role/FhirEmbeddingRole

        # Azure OpenAI
        azure-openai:
          endpoint: https://${AZURE_OPENAI_RESOURCE}.openai.azure.com
          api-key: ${AZURE_OPENAI_KEY}
          deployment: text-embedding-3-small  # Your deployment name
          api-version: "2024-02-01"
          dimensions: 1536

        # GCP Vertex AI
        gcp-vertex:
          project-id: ${GCP_PROJECT_ID}
          location: ${GCP_LOCATION:us-central1}
          model: text-embedding-004
          dimensions: 768
          # Uses Application Default Credentials (ADC)

        # HuggingFace models (ClinicalBERT, BioBERT)
        clinical-bert:
          endpoint: https://api-inference.huggingface.co/models/emilyalsentzer/Bio_ClinicalBERT
          api-key: ${HF_API_KEY}
          dimensions: 768

        # Local Ollama (for on-premise/air-gapped)
        ollama:
          endpoint: http://localhost:11434
          model: nomic-embed-text
          dimensions: 768
```

**Cloud Provider Implementation Example (AWS Bedrock):**

```java
@Component
@ConditionalOnProperty(name = "fhir4java.ai.embeddings.provider", havingValue = "aws-bedrock")
public class AwsBedrockEmbeddingProvider implements EmbeddingProvider {

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;
    private final int dimensions;

    public AwsBedrockEmbeddingProvider(
            @Value("${fhir4java.ai.embeddings.providers.aws-bedrock.model}") String modelId,
            @Value("${fhir4java.ai.embeddings.providers.aws-bedrock.dimensions}") int dimensions,
            @Value("${fhir4java.ai.embeddings.providers.aws-bedrock.region}") String region) {
        this.modelId = modelId;
        this.dimensions = dimensions;
        this.bedrockClient = BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Override
    public float[] embed(String text) {
        var request = InvokeModelRequest.builder()
            .modelId(modelId)
            .contentType("application/json")
            .body(SdkBytes.fromUtf8String(
                "{\"inputText\": \"" + escapeJson(text) + "\"}"
            ))
            .build();

        var response = bedrockClient.invokeModel(request);
        return parseEmbeddingResponse(response.body().asUtf8String());
    }

    @Override
    public int getDimensions() { return dimensions; }

    @Override
    public String getProviderId() { return "aws-bedrock"; }
}
```

**Dimension Handling & Provider Migration:**

Different embedding providers produce vectors of different dimensions:

| Provider | Model | Dimensions |
|----------|-------|------------|
| OpenAI | text-embedding-3-small | 1536 |
| OpenAI | text-embedding-3-large | 3072 |
| AWS Bedrock | amazon.titan-embed-text-v2 | 1024 |
| AWS Bedrock | cohere.embed-english-v3 | 1024 |
| Azure OpenAI | text-embedding-3-small | 1536 |
| GCP Vertex AI | text-embedding-004 | 768 |
| ClinicalBERT | Bio_ClinicalBERT | 768 |
| BioBERT | biobert-base-cased-v1.2 | 768 |

The `fhir_resource_embedding` table stores `model_id` alongside embeddings to handle this:

```sql
-- Schema supports multiple embedding dimensions
CREATE TABLE fhir_resource_embedding (
    ...
    embedding vector,                    -- Dynamic dimension based on model
    model_id VARCHAR(100) NOT NULL,      -- Tracks which model generated this embedding
    dimensions INTEGER NOT NULL,         -- Actual dimension count
    ...
);

-- Index per model (different dimensions require separate indexes)
CREATE INDEX idx_embedding_openai ON fhir_resource_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)
    WHERE model_id = 'openai:text-embedding-3-small';

CREATE INDEX idx_embedding_clinical_bert ON fhir_resource_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)
    WHERE model_id = 'clinical-bert';
```

**Provider migration strategy:**

When switching embedding providers (e.g., from OpenAI to ClinicalBERT), existing embeddings become incompatible. Options:

1. **Re-embed all data** (Recommended for small datasets):
   ```bash
   # CLI command to trigger re-embedding
   ./fhir4java-cli embeddings migrate --from openai --to clinical-bert --batch-size 100
   ```

2. **Gradual migration** (For large datasets):
   - New/updated resources use new provider
   - Background job re-embeds existing resources
   - Queries check `model_id` and use appropriate index

3. **Dual embeddings** (For evaluation):
   - Store embeddings from both providers temporarily
   - Compare search quality before committing to migration

```yaml
fhir4java:
  ai:
    embeddings:
      migration:
        enabled: false
        source-provider: openai
        target-provider: clinical-bert
        batch-size: 100
        parallel-workers: 4
```

### 3.5Hybrid Search Architecture

Combine multiple search strategies for robust clinical queries:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         HYBRID SEARCH PIPELINE                           │
└─────────────────────────────────────────────────────────────────────────┘

User Query: "diabetic patient with poorly controlled blood sugar and neuropathy"
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  QUERY ANALYZER                                                          │
│  • Extract clinical concepts: diabetes, blood sugar, neuropathy          │
│  • Identify FHIR search params: condition code, observation code         │
│  • Generate vector embedding for semantic search                         │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ STRUCTURED      │ │ KEYWORD         │ │ VECTOR          │
│ FHIR SEARCH     │ │ SEARCH          │ │ SEARCH          │
│                 │ │                 │ │                 │
│ Condition?code= │ │ Full-text on    │ │ Semantic        │
│ 73211009        │ │ narrative       │ │ similarity on   │
│ (diabetes)      │ │ fields          │ │ embeddings      │
│                 │ │                 │ │                 │
│ Observation?    │ │ "poorly         │ │ cosine > 0.75   │
│ code=4548-4     │ │ controlled"     │ │                 │
│ (HbA1c)         │ │ "neuropathy"    │ │                 │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         └───────────────────┼───────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  RESULT FUSION (Reciprocal Rank Fusion)                                  │
│  • Combine results from all three search paths                          │
│  • Score: RRF(d) = Σ 1/(k + rank(d)) across all result lists            │
│  • Boost matches that appear in multiple search paths                   │
└────────────────────────────┬────────────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  RESULT RANKING                                                          │
│  • Apply clinical relevance boosting (recent results, severity, etc.)   │
│  • Filter by tenant, access control                                      │
│  • Return top-k with explanation of why each matched                    │
└─────────────────────────────────────────────────────────────────────────┘
```

**Hybrid search endpoint:**
```
POST /api/ai/hybrid-search
{
  "query": "diabetic patient with poorly controlled blood sugar",
  "searchModes": ["structured", "keyword", "vector"],  // Enable specific modes
  "resourceTypes": ["Patient", "Condition", "Observation"],
  "fusionMethod": "rrf",                              // reciprocal rank fusion
  "maxResults": 20,
  "explain": true                                     // Include match explanations
}

Response:
{
  "results": [
    {
      "resource": { "resourceType": "Patient", "id": "123", "..." },
      "score": 0.92,
      "matchSources": ["structured", "vector"],
      "explanation": {
        "structured": "Matched Condition with code 73211009 (Diabetes mellitus)",
        "vector": "Semantic similarity 0.87 to clinical note mentioning poor glycemic control"
      }
    }
  ],
  "searchMetrics": {
    "structuredMatches": 45,
    "keywordMatches": 23,
    "vectorMatches": 38,
    "fusedResults": 20,
    "executionTimeMs": 85
  }
}
```

**Implementation:**
```java
@Service
public class HybridSearchService {

    @Autowired private FhirResourceService fhirService;       // Structured FHIR search
    @Autowired private FullTextSearchService textService;     // Keyword search
    @Autowired private VectorSearchService vectorService;     // Embedding search

    public HybridSearchResult search(HybridSearchRequest request) {
        // Execute searches in parallel
        CompletableFuture<List<ScoredResult>> structuredFuture =
            CompletableFuture.supplyAsync(() -> executeStructuredSearch(request));

        CompletableFuture<List<ScoredResult>> keywordFuture =
            CompletableFuture.supplyAsync(() -> executeKeywordSearch(request));

        CompletableFuture<List<ScoredResult>> vectorFuture =
            CompletableFuture.supplyAsync(() -> executeVectorSearch(request));

        // Wait for all and fuse
        List<List<ScoredResult>> allResults = CompletableFuture.allOf(
            structuredFuture, keywordFuture, vectorFuture
        ).thenApply(v -> List.of(
            structuredFuture.join(),
            keywordFuture.join(),
            vectorFuture.join()
        )).join();

        // Apply Reciprocal Rank Fusion
        return fuseResults(allResults, request.getFusionMethod());
    }

    private List<ScoredResult> fuseResults(List<List<ScoredResult>> resultLists, String method) {
        if ("rrf".equals(method)) {
            return reciprocalRankFusion(resultLists, 60); // k=60 is standard RRF constant
        }
        // Other fusion methods...
        return resultLists.get(0);
    }
}
```

### 3.6Clinical Narrative Mode

Optimized search for clinical documents like discharge summaries, progress notes, and consult reports:

```yaml
fhir4java:
  ai:
    semantic-search:
      clinical-narrative-mode:
        enabled: true
        resource-types:
          - DocumentReference
          - DiagnosticReport
          - Composition
        preprocessing:
          - remove-html           # Strip HTML tags from text.div
          - normalize-whitespace  # Clean up formatting
          - expand-abbreviations  # "pt" → "patient", "hx" → "history"
        section-weighting:        # Boost certain sections in notes
          assessment: 1.5
          plan: 1.5
          history: 1.2
          physical-exam: 1.0
```

### 3.7 Performance Considerations

Semantic search adds latency and resource costs compared to structured FHIR search. This section provides guidance for production deployments.

**Latency Breakdown (typical):**

| Component | Latency | Notes |
|-----------|---------|-------|
| Query embedding generation | 50-200ms | External API call to embedding provider |
| Vector similarity search | 20-100ms | Depends on index type and dataset size |
| Structured FHIR search | 10-50ms | Well-indexed PostgreSQL |
| Keyword full-text search | 20-80ms | PostgreSQL tsvector |
| Result fusion (RRF) | 5-10ms | In-memory computation |
| **Total hybrid search** | **100-400ms** | All three paths in parallel |

**The dominant cost is the embedding API call.** For external providers (OpenAI, AWS Bedrock), network latency adds 50-150ms per request.

**Optimization Strategies:**

```yaml
fhir4java:
  ai:
    semantic-search:
      # 1. Query embedding cache - cache repeated queries
      query-cache:
        enabled: true
        ttl-minutes: 60
        max-entries: 10000

      # 2. Timeouts - prevent slow searches from blocking
      timeouts:
        embedding-generation-ms: 500    # Fail fast if embedding API slow
        vector-search-ms: 200           # Cap vector search time
        total-search-ms: 1000           # Total timeout for hybrid search

      # 3. Fallback behavior - if vector search times out, use structured only
      fallback-on-timeout: true

      # 4. Batch embedding - process multiple queries together
      batch:
        enabled: true
        max-batch-size: 10
        max-wait-ms: 50

    embeddings:
      # 5. Index tuning for pgvector
      index:
        type: hnsw                      # HNSW faster for reads (vs IVFFlat)
        m: 16                           # HNSW connections per node
        ef-construction: 64             # Build-time quality
        ef-search: 40                   # Query-time quality/speed tradeoff
```

**Vector Index Comparison:**

| Index Type | Build Time | Query Speed | Memory | Best For |
|------------|------------|-------------|--------|----------|
| IVFFlat | Fast | Moderate | Lower | Smaller datasets (<1M vectors) |
| HNSW | Slower | Fast | Higher | Production, frequent queries |
| None (exact) | N/A | Slow | N/A | Testing only, <10K vectors |

**Cost Estimation:**

| Provider | Model | Cost per 1M tokens | Cost per 1K queries* |
|----------|-------|-------------------|---------------------|
| OpenAI | text-embedding-3-small | $0.02 | ~$0.002 |
| OpenAI | text-embedding-3-large | $0.13 | ~$0.013 |
| AWS Bedrock | Titan Embed v2 | $0.02 | ~$0.002 |
| Azure OpenAI | text-embedding-3-small | $0.02 | ~$0.002 |
| Self-hosted | ClinicalBERT | $0 (infra only) | $0 |

*Assumes average query of 100 tokens

**Storage Requirements:**

| Vectors | Dimensions | Storage (uncompressed) |
|---------|------------|------------------------|
| 1M | 768 | ~3 GB |
| 1M | 1536 | ~6 GB |
| 10M | 1536 | ~60 GB |

**Production Recommendations:**

1. **Start with structured search** - Only enable semantic search for resources that truly benefit (DocumentReference, DiagnosticReport)
2. **Use HNSW index** for production workloads with frequent queries
3. **Enable query caching** - Clinical queries are often repetitive
4. **Set aggressive timeouts** - Fail fast and fall back to structured search
5. **Consider self-hosted embeddings** for high-volume deployments (ClinicalBERT via Ollama or HuggingFace TGI)
6. **Monitor embedding API costs** - Track usage to avoid surprise bills

---

## Pillar 4: Agent-Friendly Authentication & Authorization

### Goal
Enable fine-grained, scoped authorization for AI agents with proper identity, audit trail, and least-privilege access.

### 4.1OAuth2 / SMART on FHIR

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

### 4.2Agent Identity & API Keys

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

### 4.3Scoped Authorization Plugin

Replace the existing `NoOpAuthorizationPlugin` with a real implementation:

```java
public class ScopedAuthorizationPlugin implements AuthorizationPlugin {
    // BEFORE phase - validates agent scopes against requested operation
    // Checks: resource type access, interaction type (CRUD), tenant access
    // Returns ABORT with 403 if insufficient scopes
}
```

### 4.4Agent Audit Trail

Extend the existing audit log to capture agent-specific metadata:

```sql
ALTER TABLE fhir_audit_log ADD COLUMN agent_id VARCHAR(128);
ALTER TABLE fhir_audit_log ADD COLUMN agent_scopes TEXT[];
ALTER TABLE fhir_audit_log ADD COLUMN tool_invocation_id VARCHAR(256);
ALTER TABLE fhir_audit_log ADD COLUMN mcp_request_id VARCHAR(256);
```

### 4.5FHIR Consent Enforcement

Automatically enforce FHIR Consent resources before any data access. This ensures patient privacy preferences are respected by AI agents.

**Consent check flow:**
```
┌─────────────────────────────────────────────────────────────────┐
│  MCP Tool Call (e.g., fhir_query for Patient/123)               │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  ConsentEnforcementFilter                                        │
│  1. Find active Consent resources for target patient            │
│  2. Check if requesting agent/actor is permitted                │
│  3. Apply data use limitations (e.g., no research use)          │
│  4. Filter response to exclude restricted data categories       │
└────────────────────────────┬────────────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
   ┌──────────┐       ┌──────────┐       ┌──────────┐
   │ PERMIT   │       │ DENY     │       │ FILTER   │
   │          │       │          │       │          │
   │ Full     │       │ Return   │       │ Return   │
   │ access   │       │ 403      │       │ filtered │
   │ granted  │       │          │       │ data     │
   └──────────┘       └──────────┘       └──────────┘
```

**Consent categories supported:**

| Category | Code | Effect |
|----------|------|--------|
| Research use | `research` | Block agent access if purpose is research |
| Treatment | `treatment` | Allow clinical agents |
| Mental health | `MH` | Redact mental health records unless explicitly permitted |
| Substance use | `SUD` | Redact substance use records (42 CFR Part 2) |
| HIV | `HIV` | Redact HIV-related records |
| Genetic | `GENETIC` | Redact genetic information (GINA compliance) |

**Implementation:**
```java
@Component
public class ConsentEnforcementPlugin implements AuthorizationPlugin {

    @Autowired
    private ConsentService consentService;

    @Override
    public PluginResult authorize(PluginContext context, IBaseResource resource) {
        if (resource == null) return PluginResult.CONTINUE;

        // Get patient reference from resource
        String patientId = extractPatientReference(resource);
        if (patientId == null) return PluginResult.CONTINUE;

        // Find active consents
        List<Consent> consents = consentService.findActiveConsents(patientId);

        // Check if access is permitted for this agent/purpose
        ConsentDecision decision = consentService.evaluate(
            consents,
            context.getAgentId(),
            context.getAccessPurpose(),
            resource
        );

        switch (decision.getOutcome()) {
            case PERMIT:
                return PluginResult.CONTINUE;
            case DENY:
                return PluginResult.abort(403, "Access denied by patient consent");
            case FILTER:
                // Apply data filtering
                context.setResponseFilter(decision.getFilter());
                return PluginResult.CONTINUE;
        }
        return PluginResult.CONTINUE;
    }
}
```

**Configuration:**
```yaml
fhir4java:
  auth:
    consent:
      enabled: true
      default-policy: permit              # permit or deny when no consent exists
      sensitive-categories:
        - MH
        - SUD
        - HIV
        - GENETIC
      agent-purpose-mapping:
        clinical-summary-agent: treatment
        research-agent: research
```

### 4.6AI Provenance Tracking

Automatically create FHIR Provenance resources for all AI-generated or AI-modified content. This is **critical for regulatory compliance** and clinical accountability.

**Auto-generated Provenance:**
```json
{
  "resourceType": "Provenance",
  "target": [
    { "reference": "Observation/obs-789" }
  ],
  "recorded": "2026-03-25T10:30:00Z",
  "activity": {
    "coding": [{
      "system": "http://terminology.hl7.org/CodeSystem/v3-DataOperation",
      "code": "CREATE"
    }]
  },
  "agent": [
    {
      "type": {
        "coding": [{
          "system": "http://fhir4java.org/CodeSystem/provenance-agent-type",
          "code": "ai-agent",
          "display": "AI Agent"
        }]
      },
      "who": {
        "identifier": {
          "system": "http://fhir4java.org/agent-id",
          "value": "clinical-summary-agent"
        },
        "display": "Clinical Summary Agent"
      }
    },
    {
      "type": {
        "coding": [{
          "system": "http://terminology.hl7.org/CodeSystem/provenance-participant-type",
          "code": "author"
        }]
      },
      "who": {
        "reference": "Practitioner/dr-smith"
      }
    }
  ],
  "entity": [
    {
      "role": "source",
      "what": {
        "identifier": {
          "system": "http://fhir4java.org/llm-model",
          "value": "claude-3-opus"
        }
      }
    }
  ],
  "extension": [
    {
      "url": "http://fhir4java.org/StructureDefinition/ai-confidence-score",
      "valueDecimal": 0.92
    },
    {
      "url": "http://fhir4java.org/StructureDefinition/ai-prompt-version",
      "valueString": "patient-summary-v2.1"
    },
    {
      "url": "http://fhir4java.org/StructureDefinition/ai-reviewed-by-human",
      "valueBoolean": true
    }
  ]
}
```

**Implementation:**
```java
@Component
public class AiProvenancePlugin implements FhirPlugin {

    @Override
    public PluginPhase getPhase() { return PluginPhase.AFTER; }

    @Override
    public PluginResult execute(PluginContext context, IBaseResource resource) {
        if (!context.isFromAiAgent()) return PluginResult.CONTINUE;
        if (!isMutationOperation(context)) return PluginResult.CONTINUE;

        // Create Provenance resource
        Provenance provenance = new Provenance();
        provenance.addTarget(new Reference(resource.getIdElement()));
        provenance.setRecorded(new Date());

        // Add AI agent
        ProvenanceAgentComponent aiAgent = provenance.addAgent();
        aiAgent.setType(aiAgentType());
        aiAgent.setWho(new Reference()
            .setIdentifier(new Identifier()
                .setSystem("http://fhir4java.org/agent-id")
                .setValue(context.getAgentId())));

        // Add supervising human if present
        if (context.getSupervisingUser() != null) {
            ProvenanceAgentComponent human = provenance.addAgent();
            human.setType(authorType());
            human.setWho(new Reference(context.getSupervisingUser()));
        }

        // Add AI metadata extensions
        provenance.addExtension(confidenceExtension(context.getConfidenceScore()));
        provenance.addExtension(modelExtension(context.getLlmModel()));
        provenance.addExtension(promptVersionExtension(context.getPromptVersion()));

        // Persist provenance
        provenanceRepository.save(provenance);

        return PluginResult.CONTINUE;
    }
}
```

### 4.7Relationship-Based Access Control

Fine-grained scopes that consider the relationship between the requesting agent and the target data:

**Extended scope syntax:**
```
{context}/{resourceType}.{interaction}:{qualifier}
```

| Scope | Meaning |
|-------|---------|
| `patient/Patient.read` | Read Patient resources in patient context |
| `patient/Observation.read:own` | Read only observations authored by the patient themselves |
| `user/MedicationRequest.write:ai-suggested` | Create AI-suggested medication orders (requires human approval) |
| `system/Observation.read:anonymous` | Read anonymized/de-identified observations only |
| `patient/*.read:care-team` | Read all resources where agent is part of care team |

**Qualifier definitions:**

| Qualifier | Effect |
|-----------|--------|
| `:own` | Only resources created by/belonging to the authenticated user |
| `:care-team` | Resources for patients where agent/user is in the CareTeam |
| `:ai-suggested` | Resource is marked as AI-suggested, pending human review |
| `:anonymous` | Only de-identified data |
| `:emergency` | Break-the-glass access with elevated audit |

**Implementation:**
```java
@Service
public class RelationshipScopeValidator {

    public boolean validate(String scope, AuthContext auth, IBaseResource resource) {
        ScopeParts parts = parseScope(scope);

        if (parts.getQualifier() == null) {
            return validateBasicScope(parts, auth, resource);
        }

        switch (parts.getQualifier()) {
            case "own":
                return isOwnedBy(resource, auth.getUserId());
            case "care-team":
                return isInCareTeam(resource, auth.getUserId());
            case "ai-suggested":
                return isAiSuggested(resource) && pendingHumanReview(resource);
            case "anonymous":
                return isDeIdentified(resource);
            case "emergency":
                auditBreakTheGlass(auth, resource);
                return true;
            default:
                return false;
        }
    }
}
```

### 4.8Data Localization

Support regional data protection requirements by enforcing data residency and access controls:

**Supported regulations:**

| Region | Regulation | Key Requirements |
|--------|------------|------------------|
| Singapore | PDPA | Consent required, data transfer restrictions |
| China | PIPL / DSL | Data localization, cross-border transfer rules |
| EU | GDPR | Lawful basis, data minimization, right to erasure |
| USA | HIPAA | Minimum necessary, access controls |

**Configuration:**
```yaml
fhir4java:
  auth:
    data-localization:
      enabled: true
      default-region: sg                    # Singapore
      regions:
        sg:
          regulation: PDPA
          data-residency: required          # Data must stay in region
          cross-border-transfer: consent    # Requires explicit consent
        cn:
          regulation: PIPL
          data-residency: required
          cross-border-transfer: assessment # Requires security assessment
          sensitive-categories:             # Extra restrictions
            - biometric
            - health
            - financial
        us:
          regulation: HIPAA
          data-residency: optional
          cross-border-transfer: baa        # Business Associate Agreement
```

**Enforcement:**
```java
@Component
public class DataLocalizationFilter implements AuthorizationPlugin {

    @Override
    public PluginResult authorize(PluginContext context, IBaseResource resource) {
        String patientRegion = getPatientRegion(resource);
        String requestRegion = context.getRequestRegion();
        RegionConfig config = getRegionConfig(patientRegion);

        // Check data residency
        if (config.isDataResidencyRequired() && !requestRegion.equals(patientRegion)) {
            // Check if cross-border transfer is allowed
            CrossBorderDecision decision = evaluateCrossBorder(
                context, patientRegion, requestRegion
            );

            if (!decision.isAllowed()) {
                return PluginResult.abort(403,
                    "Cross-border data access not permitted for " + patientRegion + " data");
            }

            // Log cross-border access
            auditCrossBorderAccess(context, resource, decision);
        }

        return PluginResult.CONTINUE;
    }
}
```

**Tenant-specific region binding:**
```sql
-- Add region to tenant configuration
ALTER TABLE fhir.fhir_tenant ADD COLUMN data_region VARCHAR(10);
ALTER TABLE fhir.fhir_tenant ADD COLUMN localization_config JSONB;

-- Example: Bind Singapore hospital to SG region
UPDATE fhir.fhir_tenant
SET data_region = 'sg',
    localization_config = '{
      "cross_border_consent_required": true,
      "audit_all_access": true
    }'
WHERE tenant_code = 'HOSP-SG';
```

---

## Pillar 5: Bulk Data & Batch Processing for Agents

### Goal
Enable AI agents to efficiently process large volumes of clinical data.

### 5.1FHIR Bulk Data Export ($export)

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

### 5.2Enhanced Batch/Transaction Processing

The existing `BundleController` handles batch/transaction bundles. Enhance with:
- **Async batch processing** - Return 202 with status polling for large bundles
- **Progress tracking** - `GET /api/batch/status/{id}` with percent complete
- **Partial failure handling** - Continue on error with detailed per-entry results
- **Size limits configurable per agent** - Higher limits for trusted agents

**Timeout and Lifecycle Configuration:**

```yaml
fhir4java:
  bulk:
    export:
      max-duration: 4h                    # Maximum runtime for export job
      status-expiry: 24h                  # How long to keep completed job status
      file-expiry: 72h                    # How long to keep generated files
      max-concurrent-exports: 5           # Limit concurrent exports per tenant
      checkpoint-interval: 1000           # Save progress every N resources
    batch:
      sync-timeout: 30s                   # Timeout for synchronous batches
      async-threshold: 100                # Switch to async if > N entries
      max-entries: 10000                  # Maximum entries per batch
      retry-failed: false                 # Retry failed entries automatically
```

**Job Lifecycle:**

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  QUEUED     │───▶│  RUNNING    │───▶│  COMPLETED  │───▶│  EXPIRED    │
│             │    │             │    │             │    │  (cleaned)  │
│ Job created │    │ Processing  │    │ Files ready │    │             │
│ awaiting    │    │ resources   │    │ for 72h     │    │             │
│ worker      │    │             │    │             │    │             │
└─────────────┘    └──────┬──────┘    └─────────────┘    └─────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │  FAILED     │
                   │             │
                   │ Error after │
                   │ max retries │
                   └─────────────┘
```

**Handling Long-Running Export Failures:**

```java
@Service
public class BulkExportService {

    public void processExport(BulkExportJob job) {
        try {
            int processed = 0;
            int checkpoint = config.getCheckpointInterval();

            for (IBaseResource resource : resourceIterator(job)) {
                writeToNdjson(job, resource);
                processed++;

                // Periodic checkpoint to enable resume
                if (processed % checkpoint == 0) {
                    saveCheckpoint(job, processed);
                }

                // Check timeout
                if (job.isExpired()) {
                    job.setStatus(JobStatus.FAILED);
                    job.setError("Export exceeded maximum duration of " + config.getMaxDuration());
                    break;
                }
            }

            job.setStatus(JobStatus.COMPLETED);
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setLastCheckpoint(processed);  // Enable partial recovery
        }
    }
}
```

**Status Response with Progress:**

```json
GET /api/bulk/status/job-123

{
  "jobId": "job-123",
  "status": "RUNNING",
  "progress": {
    "resourcesProcessed": 45000,
    "resourcesTotal": 120000,
    "percentComplete": 37.5,
    "currentResourceType": "Observation"
  },
  "timing": {
    "startedAt": "2026-03-25T10:00:00Z",
    "estimatedCompletion": "2026-03-25T10:45:00Z",
    "maxDuration": "4h",
    "elapsed": "15m"
  },
  "output": []  // Populated when COMPLETED
}
```

### 5.3NDJSON Streaming

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

### 5.4De-Identification for Bulk Export

Support de-identification profiles in the `$export` operation for research, analytics, and AI training use cases:

```
POST /fhir/r5/$export
  ?_type=Patient,Observation,Condition
  &_since=2026-01-01
  &_deidentify=safe-harbor
  &_outputFormat=application/ndjson

→ 202 Accepted
  Content-Location: /api/bulk/status/job-123
```

**De-identification profiles:**

| Profile | Method | Removes/Masks |
|---------|--------|---------------|
| `safe-harbor` | HIPAA Safe Harbor | 18 identifiers (names, dates, locations, etc.) |
| `limited` | HIPAA Limited Data Set | Names, contact info, but keeps dates and zip codes |
| `expert` | Expert Determination | Custom rules based on statistical analysis |
| `k-anonymity` | k-Anonymity | Generalize quasi-identifiers to ensure k=5 |
| `custom` | Tenant-defined | Custom rules per tenant |

**Safe Harbor de-identification (18 HIPAA identifiers):**

```java
public class SafeHarborDeidentifier implements DeidentificationProfile {

    @Override
    public IBaseResource deidentify(IBaseResource resource) {
        // 1. Names
        removeNames(resource);

        // 2. Geographic data smaller than state
        generalizeAddress(resource);  // Keep state, remove city/zip

        // 3. Dates (except year) - shift or generalize
        shiftDates(resource);  // Random shift within year

        // 4. Phone numbers
        removePhones(resource);

        // 5. Fax numbers
        removeFax(resource);

        // 6. Email addresses
        removeEmails(resource);

        // 7. Social Security numbers
        removeSSN(resource);

        // 8. Medical record numbers
        hashMRN(resource);  // Replace with hashed value

        // 9. Health plan beneficiary numbers
        removeInsuranceIds(resource);

        // 10. Account numbers
        removeAccountNumbers(resource);

        // 11. Certificate/license numbers
        removeLicenses(resource);

        // 12. Vehicle identifiers
        removeVehicleIds(resource);

        // 13. Device identifiers
        removeDeviceIds(resource);

        // 14. Web URLs
        removeURLs(resource);

        // 15. IP addresses
        removeIPAddresses(resource);

        // 16. Biometric identifiers
        removeBiometrics(resource);

        // 17. Full-face photographs
        removePhotos(resource);

        // 18. Unique identifying numbers
        hashUniqueIds(resource);

        return resource;
    }
}
```

**Date shifting strategy:**
```java
public class DateShiftingService {

    /**
     * Shift all dates for a patient by a consistent random offset.
     * Preserves temporal relationships (intervals between events).
     */
    public void shiftDates(IBaseResource resource, String patientId) {
        // Get or generate consistent shift for this patient
        int shiftDays = getPatientShift(patientId);  // -365 to +365 days

        // Find and shift all date/dateTime fields
        FhirPath.evaluate(resource, "descendants().ofType(date) | descendants().ofType(dateTime)")
            .forEach(date -> shiftDate(date, shiftDays));
    }

    private int getPatientShift(String patientId) {
        // Consistent hash-based shift (same patient always gets same shift)
        return Math.abs(patientId.hashCode()) % 730 - 365;
    }
}
```

**Bulk export with de-identification response:**
```json
{
  "output": [
    {
      "type": "Patient",
      "url": "/api/bulk/download/job-123/Patient.ndjson",
      "count": 10000
    }
  ],
  "deidentification": {
    "profile": "safe-harbor",
    "recordsProcessed": 10000,
    "fieldsRemoved": 45230,
    "dateShiftApplied": true,
    "auditId": "audit-456"
  }
}
```

**Configuration:**
```yaml
fhir4java:
  bulk:
    deidentification:
      enabled: true
      default-profile: null             # Must be explicitly requested
      allowed-profiles:
        - safe-harbor
        - limited
        - custom
      date-shifting:
        enabled: true
        range-days: 365                 # +/- 365 days
        preserve-year: false
      custom-profiles:
        tenant-research:
          remove:
            - name
            - address
            - telecom
          mask:
            - identifier[type=MRN]      # Hash MRN
          generalize:
            - birthDate                 # Keep year only
```

**Access control for de-identified exports:**
```yaml
fhir4java:
  auth:
    agents:
      - id: research-agent
        scopes:
          - "system/*.read:anonymous"   # Can only access de-identified data
        bulk-export:
          allowed: true
          require-deidentify: true      # Must use de-identification
          allowed-profiles: ["safe-harbor", "limited"]
```

---

## Pillar 6: AI Orchestration Layer

### Goal
Provide higher-level abstractions that make it easy for AI agents to perform complex clinical workflows.

### 6.1Composite Operations (Agent Workflows)

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

**Workflow Execution & Failure Handling:**

Workflows support three failure modes, configurable per workflow:

| Mode | Behavior | Use Case |
|------|----------|----------|
| `transaction` | All-or-nothing; rollback on any failure | Critical workflows requiring atomicity |
| `compensating` | Auto-execute compensating actions on failure | Workflows with defined undo operations |
| `partial` | Continue on failure; return partial results | Best-effort workflows |

**Workflow configuration with failure handling:**

```yaml
workflow:
  name: patient-intake
  description: "Complete patient intake workflow"
  failureMode: compensating              # transaction | compensating | partial

  steps:
    - name: create-patient
      operation: create
      resourceType: Patient
      output: patientId
      compensate:                        # Undo action if later step fails
        operation: delete
        resourceType: Patient
        id: "${patientId}"

    - name: create-encounter
      operation: create
      resourceType: Encounter
      input:
        subject: "Patient/${patientId}"
      output: encounterId
      compensate:
        operation: delete
        resourceType: Encounter
        id: "${encounterId}"

    - name: record-vitals
      operation: create
      resourceType: Observation
      input:
        subject: "Patient/${patientId}"
        encounter: "Encounter/${encounterId}"
      # No compensate - if this fails, previous steps are rolled back
```

**Workflow execution engine:**

```java
@Service
public class WorkflowExecutor {

    public WorkflowResult execute(WorkflowDefinition workflow, Map<String, Object> inputs) {
        List<StepResult> completedSteps = new ArrayList<>();

        try {
            for (WorkflowStep step : workflow.getSteps()) {
                StepResult result = executeStep(step, inputs, completedSteps);
                completedSteps.add(result);

                // Update inputs with step outputs for next step
                inputs.putAll(result.getOutputs());
            }

            return WorkflowResult.success(completedSteps);

        } catch (StepExecutionException e) {
            return handleFailure(workflow, completedSteps, e);
        }
    }

    private WorkflowResult handleFailure(
            WorkflowDefinition workflow,
            List<StepResult> completedSteps,
            StepExecutionException error) {

        switch (workflow.getFailureMode()) {
            case TRANSACTION:
                // Rollback via database transaction (all in same tx)
                throw new WorkflowRollbackException(error);

            case COMPENSATING:
                // Execute compensating actions in reverse order
                List<CompensationResult> compensations = new ArrayList<>();
                for (int i = completedSteps.size() - 1; i >= 0; i--) {
                    StepResult step = completedSteps.get(i);
                    if (step.hasCompensation()) {
                        CompensationResult comp = executeCompensation(step);
                        compensations.add(comp);
                    }
                }
                return WorkflowResult.compensated(completedSteps, compensations, error);

            case PARTIAL:
                // Return what succeeded
                return WorkflowResult.partial(completedSteps, error);

            default:
                throw new IllegalStateException("Unknown failure mode");
        }
    }
}
```

**Workflow response with failure details:**

```json
{
  "workflowId": "wf-789",
  "status": "COMPENSATED",
  "completedSteps": [
    { "name": "create-patient", "status": "SUCCESS", "resourceId": "Patient/123" },
    { "name": "create-encounter", "status": "SUCCESS", "resourceId": "Encounter/456" },
    { "name": "record-vitals", "status": "FAILED", "error": "Invalid observation code" }
  ],
  "compensations": [
    { "step": "create-encounter", "status": "COMPENSATED", "action": "Deleted Encounter/456" },
    { "step": "create-patient", "status": "COMPENSATED", "action": "Deleted Patient/123" }
  ],
  "error": {
    "step": "record-vitals",
    "message": "Invalid observation code",
    "code": "INVALID_CODE"
  }
}
```

### 6.2Clinical Decision Support Hooks

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

### 6.3GraphQL Interface

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

## Pillar 7: Command API for Clinical UI

### Goal
Provide a high-level natural language command interface for the clinical web UI, abstracting FHIR complexity behind intuitive commands that clinicians can speak or type.

### Relationship to MCP (Pillar 1)

| Interface | Audience | Protocol | Use Case |
|-----------|----------|----------|----------|
| MCP (3 tools) | AI Agents (Claude, GPT) | MCP over HTTP/SSE/stdio | Agent-to-server FHIR operations |
| Command API | Clinical Web UI | REST + WebSocket | Clinician natural language commands |

The Command API is designed for **human clinicians using the web UI**, while MCP is for **AI agents**. Both share underlying interpretation logic but serve different interaction patterns:

- **Command API** adds: confirmation flows, risk assessment, activity streams, note integration, voice input support
- **MCP tools** focus on: structured tool calling, agent-friendly responses, MCP protocol compliance

**Clear Boundary Definition:**

| Aspect | Command API | MCP Tools |
|--------|-------------|-----------|
| **Authentication** | User session (OAuth2 user token) | Agent credentials (API key or OAuth2 client credentials) |
| **Authorization context** | Human user identity | Agent identity |
| **Confirmation flows** | Yes - UI shows previews, requires clicks | No - agent decides autonomously |
| **Risk assessment** | Displayed to human for decision | Returned in response metadata |
| **Activity stream** | Yes - shows in UI sidebar | No - agents don't need visual feedback |
| **Voice input** | Supported | Not applicable |
| **Audit attribution** | Logged as user action | Logged as agent action |

**Can AI agents use the Command API?**

**No.** The Command API is restricted to authenticated human users only. Reasons:

1. **Audit clarity**: Actions must be attributed to either a human or an agent, not ambiguously both
2. **Security model**: Command API assumes human-in-the-loop for risk decisions; agents should use dry-run mode via MCP instead
3. **Design intent**: Agents have structured tools (`fhir_query`, `fhir_mutate`) optimized for programmatic access; natural language interpretation is overhead for agents that already understand structured calls

**Shared components:**

The Command API and MCP tools share these internal services (but expose them differently):

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Shared Internal Services                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ InterpretationService - Pattern matching, semantic search, LLM   │   │
│  │ FhirResourceService - CRUD operations on FHIR resources          │   │
│  │ TerminologyService - Code validation, suggestions                │   │
│  │ RiskAssessmentService - Evaluate risk level of operations        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
          ▲                                           ▲
          │                                           │
┌─────────┴─────────┐                     ┌──────────┴──────────┐
│   Command API     │                     │    MCP Tools        │
│   /api/command    │                     │    fhir_*           │
│                   │                     │                     │
│ + Confirmation UI │                     │ + Structured I/O    │
│ + Activity stream │                     │ + Response hints    │
│ + Voice input     │                     │ + Dry-run mode      │
│ + User session    │                     │ + Agent session     │
└───────────────────┘                     └─────────────────────┘
        ▲                                           ▲
        │                                           │
   Human User                                  AI Agent
   (via Clinical UI)                           (via MCP Client)
```

### 7.1Command Endpoint

```
POST /api/command
{
  "command": "Order CBC, BMP, and lipid panel",
  "patient": "Patient/123",
  "encounter": "Encounter/456",
  "mode": "execute"    // or "draft" for preview
}

Response:
{
  "commandId": "cmd-789",
  "status": "completed",
  "interpretation": {
    "intent": "CREATE_ORDERS",
    "resources": ["ServiceRequest"],
    "source": "PATTERN_MATCH"
  },
  "result": {
    "created": [
      { "resourceType": "ServiceRequest", "id": "sr-1", "code": "CBC" },
      { "resourceType": "ServiceRequest", "id": "sr-2", "code": "BMP" },
      { "resourceType": "ServiceRequest", "id": "sr-3", "code": "Lipid Panel" }
    ]
  },
  "suggestions": [
    { "label": "Add A1c", "command": "order a1c" }
  ],
  "affectedResources": ["ServiceRequest/sr-1", "ServiceRequest/sr-2", "ServiceRequest/sr-3"]
}
```

### 7.2Tiered Interpretation Pipeline

To minimize LLM usage (cost and latency), commands flow through four tiers:

```
┌─────────────────────────────────────────────────────────────────┐
│  TIER 1: EXACT MATCH CACHE                          ~0ms       │
│  Key: hash("order cbc" + context)                              │
│  If found → Return cached interpretation                       │
└────────────────────────────┬────────────────────────────────────┘
                             │ MISS
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  TIER 2: TEMPLATE PATTERN MATCHING                  ~1-5ms     │
│  Pattern: "order|get|request {labTest}"                        │
│  Covers ~80% of commands without LLM                           │
└────────────────────────────┬────────────────────────────────────┘
                             │ NO MATCH
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  TIER 3: SEMANTIC SIMILARITY SEARCH                 ~10-50ms   │
│  Embed command, search past successful interpretations         │
│  Uses local embeddings (no LLM call)                           │
│  Threshold: similarity > 0.85 → use cached                     │
└────────────────────────────┬────────────────────────────────────┘
                             │ NO SIMILAR MATCH
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  TIER 4: LLM INTERPRETATION                         ~200-500ms │
│  Only for novel/complex commands                               │
│  After LLM interprets: cache result for future                 │
│  Expected: <5% of commands reach this tier after warmup        │
└─────────────────────────────────────────────────────────────────┘
```

### 7.3Command Cache Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 1: GLOBAL CACHE (Shared across all users)               │
│  • Standard medical commands understood universally            │
│  • "order cbc" → same everywhere                               │
│  • Pre-seeded with ~500 common commands at startup             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  LAYER 2: TENANT CACHE (Organization-specific)                 │
│  • Hospital-specific order sets                                │
│  • "order admission labs" → maps to that hospital's set        │
│  • Custom aliases ("the usual" = specific order combo)         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  LAYER 3: USER CACHE (Individual preferences)                  │
│  • "my standard workup" → Dr. Smith's preferred orders         │
│  • Recent commands for "do that again" functionality           │
│  • Learned corrections (user edited AI's interpretation)       │
└─────────────────────────────────────────────────────────────────┘

CACHE LOOKUP ORDER:
User Cache → Tenant Cache → Global Cache → Pattern Match → LLM
```

### 7.4Risk-Based Execution

| Risk Level | Examples | Behavior |
|------------|----------|----------|
| **NONE** | Queries, navigation, chart viewing | Auto-execute immediately |
| **LOW** | Routine labs, documentation drafts | Auto-execute with notification |
| **MEDIUM** | Orders with drug interactions | Show warning, allow proceed |
| **HIGH** | Prescriptions, high-alert meds, chemo | Require explicit confirmation |
| **CRITICAL** | Delete operations, status changes | Two-step confirmation |

### 7.5Pre-Seeded Command Templates

```yaml
# commands/queries.yml
queries:
  - patterns: ["show {type}", "get {type}", "display {type}"]
    intent: QUERY
    variables:
      type:
        labs: { resource: Observation, category: laboratory }
        vitals: { resource: Observation, category: vital-signs }
        meds: { resource: MedicationRequest, status: active }
        problems: { resource: Condition, clinical-status: active }
        allergies: { resource: AllergyIntolerance }
        a1c: { resource: Observation, code: "4548-4" }

# commands/orders.yml
orders:
  - patterns: ["order {test}", "get {test}", "request {test}"]
    intent: CREATE_ORDER
    resource: ServiceRequest
    variables:
      test:
        cbc: { code: "58410-2", display: "CBC" }
        bmp: { code: "24320-4", display: "BMP" }
        cmp: { code: "24323-8", display: "CMP" }

# commands/prescriptions.yml
prescriptions:
  - patterns: ["prescribe {drug}", "rx {drug}", "start {drug}"]
    intent: PRESCRIBE
    resource: MedicationRequest
    riskLevel: HIGH
    requiresConfirmation: true
```

### 7.6Implementation Components

```java
// New classes in fhir4java-api
@RestController
@RequestMapping("/api/command")
public class CommandController {
    @PostMapping
    public CommandResponse execute(@RequestBody CommandRequest request) { ... }
}

@Service
public class CommandService {
    private final CommandInterpreter interpreter;
    private final CommandExecutor executor;
    private final CommandCache cache;
}

@Service
public class CommandInterpreter {
    private final PatternMatcher patternMatcher;        // Tier 2
    private final SemanticSearcher semanticSearcher;    // Tier 3
    private final LlmInterpreter llmInterpreter;        // Tier 4

    public InterpretedCommand interpret(String command, CommandContext context) { ... }
}

@Service
public class CommandExecutor {
    private final QueryExecutor queryExecutor;
    private final OrderCreator orderCreator;
    private final PrescriptionWriter prescriptionWriter;
    private final NoteUpdater noteUpdater;

    public CommandResult execute(InterpretedCommand command) { ... }
}
```

### 7.7Command Audit Trail

All commands logged to `command_audit_log`:

```sql
CREATE TABLE command_audit_log (
    id                    BIGSERIAL PRIMARY KEY,
    command_id            UUID NOT NULL,
    tenant_id             VARCHAR(64) NOT NULL,
    user_id               VARCHAR(255) NOT NULL,
    patient_ref           VARCHAR(128),
    encounter_ref         VARCHAR(128),
    raw_command           TEXT NOT NULL,
    interpreted_intent    VARCHAR(100),
    interpretation_source VARCHAR(20),    -- CACHE, PATTERN, SEMANTIC, LLM
    execution_mode        VARCHAR(20),    -- EXECUTE, DRAFT
    execution_status      VARCHAR(20),    -- COMPLETED, FAILED, CANCELLED
    risk_level            VARCHAR(20),
    affected_resources    JSONB,
    error_message         TEXT,
    latency_ms            INTEGER,
    client_ip             INET,
    user_agent            TEXT,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_cmd_audit_tenant_user ON command_audit_log(tenant_id, user_id, created_at DESC);
CREATE INDEX idx_cmd_audit_patient ON command_audit_log(patient_ref, created_at DESC);
```

---

## Pillar 8: UI Configuration Service

### Goal
Provide a multi-level configuration system for the clinical UI, enabling organizations, roles, and individual users to customize panel layouts, columns, filters, and views.

### 8.1Configuration Hierarchy

```
SYSTEM DEFAULTS (shipped with application)
    │ overrides ▼
TENANT CONFIGURATION (organization-specific)
    │ overrides ▼
ROLE CONFIGURATION (role-specific defaults)
    │ overrides ▼
USER PREFERENCES (individual customization)
```

Each level can override the previous. Configuration merges use deep merge semantics.

### 8.2UI Configuration Tables

Tables are added to the `fhir` schema (consistent with tenant table location) with `ui_` prefix:

```sql
-- Tenant/role-level panel overrides
CREATE TABLE IF NOT EXISTS fhir.ui_panel_config (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES fhir.fhir_tenant(internal_id),
    role_code           VARCHAR(100),             -- NULL = tenant default, value = role-specific
    panel_id            VARCHAR(100) NOT NULL,
    config              JSONB NOT NULL,           -- Panel configuration JSON
    enabled             BOOLEAN DEFAULT TRUE,
    display_order       INTEGER DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT uk_ui_panel_config UNIQUE (tenant_id, role_code, panel_id)
);

-- User-level panel preferences
CREATE TABLE IF NOT EXISTS fhir.ui_user_panel_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES fhir.fhir_tenant(internal_id),
    user_id             VARCHAR(255) NOT NULL,
    panel_id            VARCHAR(100) NOT NULL,
    preferences         JSONB NOT NULL,
    collapsed           BOOLEAN DEFAULT FALSE,
    display_order       INTEGER DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_ui_user_panel UNIQUE (tenant_id, user_id, panel_id)
);

-- User saved views (named configurations)
CREATE TABLE IF NOT EXISTS fhir.ui_user_saved_views (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES fhir.fhir_tenant(internal_id),
    user_id             VARCHAR(255) NOT NULL,
    view_name           VARCHAR(100) NOT NULL,
    view_type           VARCHAR(50) NOT NULL,     -- 'chart', 'queue', 'workspace'
    config              JSONB NOT NULL,
    is_default          BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_ui_user_view UNIQUE (tenant_id, user_id, view_name, view_type)
);

-- Tenant command aliases
CREATE TABLE IF NOT EXISTS fhir.ui_command_aliases (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES fhir.fhir_tenant(internal_id),
    alias               VARCHAR(255) NOT NULL,
    expansion           TEXT NOT NULL,
    category            VARCHAR(50),
    enabled             BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_ui_tenant_alias UNIQUE (tenant_id, alias)
);

-- User command shortcuts
CREATE TABLE IF NOT EXISTS fhir.ui_user_command_shortcuts (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES fhir.fhir_tenant(internal_id),
    user_id             VARCHAR(255) NOT NULL,
    shortcut            VARCHAR(255) NOT NULL,
    expansion           TEXT NOT NULL,
    usage_count         INTEGER DEFAULT 0,
    last_used_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_ui_user_shortcut UNIQUE (tenant_id, user_id, shortcut)
);

-- Tenant branding
CREATE TABLE IF NOT EXISTS fhir.ui_tenant_branding (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL UNIQUE REFERENCES fhir.fhir_tenant(internal_id),
    logo_url            VARCHAR(500),
    primary_color       VARCHAR(7),
    secondary_color     VARCHAR(7),
    accent_color        VARCHAR(7),
    custom_css          TEXT,
    config              JSONB,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 8.3UI Configuration API

```
GET  /api/uiconfig/panels                           # List available panels
GET  /api/uiconfig/panels/{id}/config               # Get merged config for user
PUT  /api/uiconfig/panels/{id}/preferences          # Save user preferences
GET  /api/uiconfig/panels/{id}/data                 # Fetch panel data (FHIR query)

GET  /api/uiconfig/views                            # List user's saved views
POST /api/uiconfig/views                            # Create saved view
PUT  /api/uiconfig/views/{id}                       # Update saved view
DELETE /api/uiconfig/views/{id}                     # Delete saved view

GET  /api/uiconfig/layout/{context}                 # Get layout preferences
PUT  /api/uiconfig/layout/{context}                 # Save layout preferences

GET  /api/uiconfig/branding                         # Get tenant branding

# Admin endpoints
PUT  /api/uiconfig/admin/panels/{id}/tenant-config  # Tenant-level config
PUT  /api/uiconfig/admin/panels/{id}/role-config    # Role-level config
POST /api/uiconfig/admin/panels/custom              # Create custom panel
PUT  /api/uiconfig/admin/branding                   # Update branding
```

### 8.4Configuration Merge Logic

```java
@Service
public class UiConfigService {

    public PanelConfig getPanelConfig(String panelId, UserContext context) {
        // Load configurations from each level
        PanelConfig systemConfig = loadSystemConfig(panelId);           // YAML files
        PanelConfig tenantConfig = loadTenantConfig(panelId, context.getTenantId());
        PanelConfig roleConfig = loadRoleConfig(panelId, context.getTenantId(), context.getRoles());
        PanelConfig userConfig = loadUserConfig(panelId, context.getTenantId(), context.getUserId());

        // Deep merge: user > role > tenant > system
        return deepMerge(systemConfig, tenantConfig, roleConfig, userConfig);
    }
}
```

---

## Pillar 9: Clinical Workflow Support

### Goal
Provide backend services for clinical workflow management including patient queue management, note lifecycle tracking, and pending results coordination.

### 9.1Queue Management

#### Queue Status State Machine

```
┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐
│ SCHEDULED │───▶│  ARRIVED  │───▶│  ROOMED   │───▶│   READY   │
│           │    │           │    │           │    │           │
│ Appt only │    │ Checked in│    │ In exam   │    │ Vitals    │
│           │    │ at front  │    │ room      │    │ complete  │
└───────────┘    └───────────┘    └───────────┘    └─────┬─────┘
     │                                                    │
     │                                                    ▼
     │           ┌───────────┐    ┌───────────┐    ┌───────────┐
     │           │ COMPLETED │◀───│  CHECKOUT │◀───│    IN     │
     │           │           │    │   READY   │    │CONSULTATION│
     │           │ Visit done│    │           │    │           │
     │           │ & closed  │    │ Doc done  │    │ With      │
     │           └───────────┘    └───────────┘    │ provider  │
     │                                             └───────────┘
     ▼
┌───────────┐
│  NO SHOW  │
└───────────┘
```

#### FHIR Mapping

| Queue State | FHIR Encounter.status | FHIR subjectStatus |
|-------------|----------------------|-------------------|
| Scheduled | (no encounter yet) | N/A |
| Arrived | `in-progress` | `arrived` |
| Roomed | `in-progress` | `triaged` |
| Ready | `in-progress` | `receiving-care` |
| In Consultation | `in-progress` | `receiving-care` |
| Checkout Ready | `in-progress` | `departed` |
| Completed | `finished` | `departed` |
| No Show | `cancelled` | N/A |

#### Queue Extensions

```json
{
  "resourceType": "Encounter",
  "extension": [
    {
      "url": "http://fhir4java.org/StructureDefinition/queue-position",
      "valueInteger": 3
    },
    {
      "url": "http://fhir4java.org/StructureDefinition/assigned-provider",
      "valueReference": { "reference": "Practitioner/dr-smith" }
    },
    {
      "url": "http://fhir4java.org/StructureDefinition/room-assignment",
      "valueString": "Exam Room 2"
    },
    {
      "url": "http://fhir4java.org/StructureDefinition/wait-time-minutes",
      "valueInteger": 12
    }
  ]
}
```

#### Queue Service API

```
GET  /api/queue                                     # Get department queue
GET  /api/queue?provider={id}                       # Get provider's queue
GET  /api/queue?status=ready                        # Filter by status

POST /api/queue/check-in                            # Check in patient
{
  "appointmentId": "Appointment/appt-123",
  "patientId": "Patient/123"
}

POST /api/queue/room                                # Room patient
{
  "encounterId": "Encounter/enc-456",
  "room": "Exam Room 2"
}

POST /api/queue/ready                               # Mark ready for provider
POST /api/queue/start-visit                         # Start consultation
POST /api/queue/checkout-ready                      # Mark for checkout
POST /api/queue/complete                            # Complete checkout
POST /api/queue/no-show                             # Mark as no-show
```

### 9.2Note Lifecycle Management

#### Note States

| State | Composition.status | DocumentReference.docStatus | Description |
|-------|-------------------|---------------------------|-------------|
| Pended (draft) | `partial` | `preliminary` | Initial draft, incomplete |
| Pended (results pending) | `preliminary` | `preliminary` | Waiting for results |
| Signed | `final` | `final` | Complete, locked |
| Addendum | `appended` | `amended` | Addition to signed note |
| Corrected | `corrected` | `amended` | Error correction |

#### Pending Results Tracking

Extension to track pending orders for a note:

```json
{
  "resourceType": "Composition",
  "status": "preliminary",
  "extension": [
    {
      "url": "http://fhir4java.org/StructureDefinition/awaiting-results",
      "extension": [
        {
          "url": "order",
          "valueReference": { "reference": "ServiceRequest/sr-123" }
        },
        {
          "url": "expectedTime",
          "valueDateTime": "2026-03-25T10:30:00Z"
        },
        {
          "url": "resultReceived",
          "valueBoolean": false
        }
      ]
    }
  ]
}
```

#### Note Service API

```
POST /api/notes/pend                                # Save as pended
{
  "encounterId": "Encounter/enc-456",
  "sections": [...],
  "awaitingOrders": ["ServiceRequest/sr-123", "ServiceRequest/sr-124"]
}

POST /api/notes/sign                                # Sign note (finalize)
{
  "compositionId": "Composition/comp-789"
}

POST /api/notes/addendum                            # Create addendum
{
  "originalCompositionId": "Composition/comp-789",
  "addendumText": "Culture results returned..."
}

GET /api/notes/pended                               # Get user's pended notes
GET /api/notes/pended?awaitingResults=true          # Filter by pending results
```

### 9.3Result Notification Service

Coordinates between FHIR Subscriptions and the clinical UI to notify clinicians when results arrive:

```java
@Service
public class ResultNotificationService {

    @EventListener
    public void onObservationCreated(ObservationCreatedEvent event) {
        // Find pended notes awaiting this result
        List<Composition> pendedNotes = findNotesAwaitingOrder(event.getBasedOn());

        for (Composition note : pendedNotes) {
            // Update awaiting-results extension
            markResultReceived(note, event.getObservation());

            // Notify clinician via WebSocket
            notifyResultReady(note.getAuthor(), event.getObservation());

            // Check if all results complete
            if (allResultsReceived(note)) {
                notifyNoteReadyForCompletion(note);
            }
        }
    }
}
```

---

## Pillar 10: Observability & Explainability

### Goal
Provide comprehensive audit, tracing, and explainability infrastructure for AI agent interactions. This is **critical for healthcare deployments** where regulatory compliance (HIPAA, PDPA, etc.) requires complete audit trails of AI-assisted clinical decisions.

### 10.1MCP Interaction Logging

Every MCP tool call generates a structured audit record:

```sql
CREATE TABLE mcp_interaction_log (
    id                    BIGSERIAL PRIMARY KEY,
    interaction_id        UUID NOT NULL UNIQUE,        -- Unique ID for this interaction
    trace_id              VARCHAR(64),                 -- Correlation ID across multiple calls
    tenant_id             VARCHAR(64) NOT NULL,
    agent_id              VARCHAR(128) NOT NULL,       -- From auth context
    session_id            VARCHAR(128),                -- MCP session ID

    -- Tool invocation details
    tool_name             VARCHAR(50) NOT NULL,        -- fhir_discover, fhir_query, fhir_mutate
    action                VARCHAR(50),                 -- read, search, create, update, etc.
    resource_type         VARCHAR(64),
    resource_id           VARCHAR(128),

    -- Input/Output (for debugging and replay)
    input_params          JSONB,                       -- Tool input parameters
    output_summary        JSONB,                       -- Summary of output (not full payload)
    affected_resources    TEXT[],                      -- Resource references affected

    -- Risk and validation
    risk_level            VARCHAR(20),                 -- NONE, LOW, MEDIUM, HIGH, CRITICAL
    validation_errors     JSONB,                       -- Any validation issues
    terminology_checks    JSONB,                       -- LOINC/SNOMED validation results

    -- Performance
    latency_ms            INTEGER,
    token_usage           JSONB,                       -- {input: n, output: n} if LLM involved

    -- Status
    status                VARCHAR(20) NOT NULL,        -- SUCCESS, FAILED, REJECTED, DRY_RUN
    error_code            VARCHAR(50),
    error_message         TEXT,

    -- Metadata
    client_ip             INET,
    user_agent            TEXT,
    created_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT fk_mcp_log_tenant FOREIGN KEY (tenant_id)
        REFERENCES fhir.fhir_tenant(internal_id)
);

-- Indexes for common queries
CREATE INDEX idx_mcp_log_tenant_time ON mcp_interaction_log(tenant_id, created_at DESC);
CREATE INDEX idx_mcp_log_agent ON mcp_interaction_log(agent_id, created_at DESC);
CREATE INDEX idx_mcp_log_trace ON mcp_interaction_log(trace_id);
CREATE INDEX idx_mcp_log_resource ON mcp_interaction_log(resource_type, resource_id);
CREATE INDEX idx_mcp_log_status ON mcp_interaction_log(status, created_at DESC);
```

### 10.2Explainability Metadata

For mutations and clinical decisions, capture reasoning context:

```sql
CREATE TABLE mcp_decision_context (
    id                    BIGSERIAL PRIMARY KEY,
    interaction_id        UUID NOT NULL REFERENCES mcp_interaction_log(interaction_id),

    -- What prompted this action
    user_prompt           TEXT,                        -- Original natural language input (if any)
    interpreted_intent    VARCHAR(100),                -- Extracted intent
    interpretation_source VARCHAR(20),                 -- CACHE, PATTERN, SEMANTIC, LLM
    confidence_score      DECIMAL(3,2),                -- 0.00 to 1.00

    -- AI reasoning (when LLM involved)
    reasoning_steps       JSONB,                       -- Chain-of-thought if captured
    alternative_actions   JSONB,                       -- Other options considered

    -- Clinical context
    patient_context       JSONB,                       -- Relevant patient data considered
    clinical_guidelines   TEXT[],                      -- Referenced guidelines/protocols

    created_at            TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_decision_context_interaction ON mcp_decision_context(interaction_id);
```

### 10.3Audit API Endpoints

```
GET /mcp/audit                                        # Query MCP interaction logs
    ?agent_id={id}                                    # Filter by agent
    ?trace_id={id}                                    # Filter by trace
    ?resource_type={type}                             # Filter by resource type
    ?status={status}                                  # Filter by status
    ?from={datetime}&to={datetime}                    # Time range
    &include_context=true                             # Include decision context

GET /mcp/audit/{interaction_id}                       # Get single interaction detail
GET /mcp/audit/{interaction_id}/context               # Get decision context
GET /mcp/audit/trace/{trace_id}                       # Get all interactions in trace

GET /mcp/audit/stats                                  # Aggregated statistics
    ?agent_id={id}
    ?period=day|week|month

    Response:
    {
      "period": "2026-03-01 to 2026-03-31",
      "totalInteractions": 15420,
      "byTool": {
        "fhir_discover": 2100,
        "fhir_query": 11500,
        "fhir_mutate": 1820
      },
      "byStatus": {
        "SUCCESS": 15100,
        "FAILED": 180,
        "DRY_RUN": 140
      },
      "avgLatencyMs": 45,
      "riskDistribution": {
        "NONE": 12000,
        "LOW": 2500,
        "MEDIUM": 800,
        "HIGH": 120
      }
    }
```

### 10.4Logging Implementation

```java
@Component
public class McpAuditLogger {

    @Autowired
    private McpInteractionLogRepository logRepository;

    @Autowired
    private McpDecisionContextRepository contextRepository;

    /**
     * Log every MCP tool invocation
     */
    public void logInteraction(McpToolInvocation invocation, McpToolResult result) {
        McpInteractionLog log = McpInteractionLog.builder()
            .interactionId(UUID.randomUUID())
            .traceId(MDC.get("traceId"))
            .tenantId(invocation.getTenantId())
            .agentId(invocation.getAgentId())
            .sessionId(invocation.getSessionId())
            .toolName(invocation.getToolName())
            .action(invocation.getAction())
            .resourceType(invocation.getResourceType())
            .resourceId(invocation.getResourceId())
            .inputParams(sanitizeInput(invocation.getParams()))
            .outputSummary(summarizeOutput(result))
            .affectedResources(result.getAffectedResources())
            .riskLevel(result.getRiskLevel())
            .validationErrors(result.getValidationErrors())
            .terminologyChecks(result.getTerminologyChecks())
            .latencyMs(result.getLatencyMs())
            .status(result.getStatus())
            .errorCode(result.getErrorCode())
            .errorMessage(result.getErrorMessage())
            .clientIp(invocation.getClientIp())
            .userAgent(invocation.getUserAgent())
            .build();

        logRepository.save(log);

        // If decision context is available (e.g., from LLM interpretation)
        if (invocation.hasDecisionContext()) {
            logDecisionContext(log.getInteractionId(), invocation.getDecisionContext());
        }
    }

    private JsonNode sanitizeInput(JsonNode params) {
        // Remove PHI from logged params (keep structure, mask values)
        // This depends on your data retention policy
        return params;
    }

    private JsonNode summarizeOutput(McpToolResult result) {
        // Don't log full FHIR resources - just summary
        return Json.object()
            .put("resourceCount", result.getResourceCount())
            .put("bundleType", result.getBundleType())
            .put("totalMatches", result.getTotalMatches());
    }
}
```

### 10.5Trace Correlation

All MCP interactions within a logical workflow share a `trace_id`:

```java
@Component
public class TraceIdFilter implements McpRequestFilter {

    @Override
    public void filter(McpRequest request) {
        // Check for incoming trace ID
        String traceId = request.getHeader("X-Trace-ID");

        if (traceId == null) {
            // Generate new trace ID for this workflow
            traceId = "mcp-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Set in MDC for logging throughout the request
        MDC.put("traceId", traceId);

        // Include in response headers
        request.setResponseHeader("X-Trace-ID", traceId);
    }
}
```

### 10.6Immutable Audit Trail

For regulatory compliance, audit logs must be immutable:

```sql
-- Prevent updates and deletes on audit tables
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable and cannot be modified';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER no_update_mcp_log
    BEFORE UPDATE OR DELETE ON mcp_interaction_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

CREATE TRIGGER no_update_decision_context
    BEFORE UPDATE OR DELETE ON mcp_decision_context
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
```

### 10.7Monitoring Metrics

Key metrics exposed via Micrometer (Prometheus-compatible):

```java
@Component
public class McpMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter interactionsTotal;
    private final Counter interactionsByTool;
    private final Counter interactionsByStatus;
    private final Counter validationFailures;

    // Timers
    private final Timer toolLatency;

    // Gauges
    private final AtomicInteger activeConnections;

    public McpMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.interactionsTotal = Counter.builder("mcp.interactions.total")
            .description("Total MCP tool invocations")
            .register(registry);

        this.toolLatency = Timer.builder("mcp.tool.latency")
            .description("MCP tool execution time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.activeConnections = registry.gauge("mcp.connections.active",
            new AtomicInteger(0));
    }

    public void recordInteraction(String tool, String status, long latencyMs) {
        interactionsTotal.increment();

        registry.counter("mcp.interactions.by_tool", "tool", tool).increment();
        registry.counter("mcp.interactions.by_status", "status", status).increment();

        toolLatency.record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
```

### 10.8Grafana Dashboard Templates

Pre-built dashboard JSON for common monitoring scenarios:

```yaml
# monitoring/grafana/mcp-dashboard.json
# Panels:
# - MCP Interactions Over Time (by tool)
# - Error Rate by Agent
# - Latency Percentiles (p50, p95, p99)
# - Risk Level Distribution
# - Top Agents by Volume
# - Failed Interactions (with drill-down)
# - Terminology Validation Failures
```

Dashboard panels configuration shipped with the server, importable via Grafana provisioning.

### 10.9Future: OpenTelemetry Integration

The logging-based approach can be enhanced with full OpenTelemetry tracing in a future phase:

```yaml
# Future configuration
fhir4java:
  observability:
    mode: logging              # logging (current) or opentelemetry (future)
    opentelemetry:
      enabled: false
      endpoint: ""
      service-name: fhir4java-mcp
```

---

## Implementation Priority & Phasing

### Phase 1: Foundation (Weeks 1-3) - "Auth & Discovery Ready"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| OAuth2 resource server (SMART on FHIR) | fhir4java-server | P0 | 1 week |
| Agent API key auth | fhir4java-plugin | P0 | 1 week |
| DiscoveryService (internal, powers `fhir_discover`) | fhir4java-core | P0 | 1 week |

> **Note:** OpenAPI is available via standard `springdoc-openapi` dependency. CapabilityStatement already exists in FHIR4Java. These are standard features, not AI-specific deliverables.

**Outcome:** Auth infrastructure ready for agents. DiscoveryService ready to back the `fhir_discover` MCP tool in Phase 2.

### Phase 2: MCP Integration (Weeks 4-7) - "Agents Can Use Tools"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| MCP server module (Streamable HTTP transport) | fhir4java-mcp (NEW) | P0 | 1 week |
| `fhir_discover` tool (backed by DiscoveryService) | fhir4java-mcp | P0 | 3 days |
| `fhir_query` tool (read/search/history/ops) | fhir4java-mcp | P0 | 1 week |
| `fhir_mutate` tool (create/update/patch/delete/ops) | fhir4java-mcp | P0 | 1 week |
| Dry-run mode for mutations | fhir4java-mcp | P0 | 3 days |
| Smart response enrichment (hints) | fhir4java-mcp | P1 | 3 days |

**Outcome:** AI agents can connect via MCP and use 3 tools to discover, query, and mutate any FHIR resource.

### Phase 3: Real-Time (Weeks 8-11) - "Agents Can React"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| SSE event streaming | fhir4java-api | P0 | 1 week |
| FHIR Subscriptions | fhir4java-core | P1 | 2 weeks |
| Webhook registry | fhir4java-api | P1 | 1 week |

**Outcome:** Agents can subscribe to real-time clinical data changes.

### Phase 4: Intelligence (Weeks 12-17) - "Agents Can Understand"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| NL-to-FHIR search translation | fhir4java-core | P1 | 2 weeks |
| Vector search (pgvector) | fhir4java-persistence | P2 | 2 weeks |
| Embedding pipeline plugin | fhir4java-plugin | P2 | 1 week |
| Bulk data export ($export) | fhir4java-api | P1 | 2 weeks |

**Outcome:** Agents can query data using natural language and process bulk datasets.

### Phase 5: Orchestration (Weeks 18-23) - "Agents Can Orchestrate"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| Composite workflows | fhir4java-core | P2 | 2 weeks |
| CDS Hooks | fhir4java-api | P2 | 2 weeks |
| GraphQL interface | fhir4java-api | P2 | 2 weeks |
| NDJSON streaming | fhir4java-api | P2 | 1 week |

**Outcome:** Agents can execute complex clinical workflows and efficiently query data.

### Phase 6: Clinical UI Backend (Weeks 24-31) - "Clinicians Can Command"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| Command API endpoint | fhir4java-api | P0 | 1 week |
| Command interpretation pipeline | fhir4java-api | P0 | 2 weeks |
| Command caching (3-layer) | fhir4java-api | P0 | 1 week |
| Pre-seeded command templates | fhir4java-api | P1 | 1 week |
| Risk assessment engine | fhir4java-api | P0 | 1 week |
| Queue management service | fhir4java-api | P0 | 1 week |
| Note lifecycle service | fhir4java-api | P1 | 1 week |

**Outcome:** Backend services ready for clinical UI integration. Clinicians can execute commands, manage queues, and track note states.

### Phase 7: UI Configuration (Weeks 32-35) - "Organizations Can Customize"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| UI config database schema | fhir4java-persistence | P0 | 3 days |
| UI config service (merge logic) | fhir4java-api | P0 | 1 week |
| Panel configuration API | fhir4java-api | P1 | 1 week |
| Saved views API | fhir4java-api | P1 | 3 days |
| Tenant branding API | fhir4java-api | P2 | 3 days |
| Command alias service | fhir4java-api | P1 | 3 days |

**Outcome:** Multi-level UI configuration system operational. Tenants, roles, and users can customize panels and workflows.

### Phase 8: Clinical UI Frontend (Weeks 36-47) - "Clinicians Can Work"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| React project setup (Vite + shadcn) | fhir4java-ui | P0 | 1 week |
| Two-pane layout (Chart + Workspace) | fhir4java-ui | P0 | 2 weeks |
| Patient Chart Canvas + panels | fhir4java-ui | P0 | 3 weeks |
| Command Workspace + voice | fhir4java-ui | P0 | 2 weeks |
| Queue Dashboard (role-based) | fhir4java-ui | P0 | 2 weeks |
| WebSocket integration | fhir4java-ui | P0 | 1 week |
| Trending views (sparklines + charts) | fhir4java-ui | P1 | 1 week |

**Outcome:** Complete clinical UI operational. Clinicians can work with patients using voice/text commands, view charts, manage queues, and receive real-time updates.

---

## New Module Structure

```
fhir4java-agents/
├── fhir4java-core/              # (existing) + DiscoveryService, NL search, subscription topics
├── fhir4java-persistence/       # (existing) + vector embeddings, bulk export, UI config tables
├── fhir4java-api/               # (existing) + OpenAPI, SSE, webhooks, GraphQL, Command API
│   └── src/main/java/
│       └── org/fhirframework/api/
│           ├── controller/
│           │   ├── CommandController.java         # NEW - Command API endpoint
│           │   ├── QueueController.java           # NEW - Queue management
│           │   ├── NoteController.java            # NEW - Note lifecycle
│           │   └── UiConfigController.java        # NEW - UI configuration API
│           ├── service/
│           │   ├── CommandService.java            # NEW - Command orchestration
│           │   ├── CommandInterpreter.java        # NEW - Tiered interpretation
│           │   ├── CommandExecutor.java           # NEW - Command execution
│           │   ├── CommandCache.java              # NEW - Multi-layer caching
│           │   ├── QueueService.java              # NEW - Queue management
│           │   ├── NoteLifecycleService.java      # NEW - Note state machine
│           │   └── UiConfigService.java           # NEW - Config merge logic
│           └── websocket/
│               ├── ClinicalWebSocketHandler.java  # NEW - Clinical events
│               └── ClinicalEventPublisher.java    # NEW - Event publishing
├── fhir4java-plugin/            # (existing) + embedding plugin, subscription plugin
├── fhir4java-ui/                # NEW - React frontend application
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tailwind.config.js
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── components/
│       │   ├── ui/              # shadcn/ui components
│       │   ├── layout/          # AppShell, GlobalHeader, MainLayout
│       │   ├── queue/           # QueueDashboard, QueueCard, CheckInWizard
│       │   ├── patient/         # PatientSearch, PatientCard
│       │   ├── chart/           # PatientChartPane, panels/*
│       │   └── workspace/       # CommandWorkspacePane, ActivityStream
│       ├── hooks/
│       │   ├── useFhirQuery.ts
│       │   ├── useCommand.ts
│       │   ├── useVoiceInput.ts
│       │   ├── useWebSocket.ts
│       │   └── useQueue.ts
│       ├── stores/
│       │   ├── layoutStore.ts
│       │   ├── commandStore.ts
│       │   ├── sessionStore.ts
│       │   └── queueStore.ts
│       ├── contexts/
│       │   ├── AuthContext.tsx
│       │   ├── PatientContext.tsx
│       │   └── WebSocketContext.tsx
│       └── lib/
│           ├── fhirClient.ts
│           ├── commandApi.ts
│           └── voiceEnhancer.ts
├── fhir4java-mcp/               # NEW - MCP server implementation
│   ├── src/main/java/
│   │   └── org/fhirframework/mcp/
│   │       ├── McpServerAutoConfiguration.java
│   │       ├── server/
│   │       │   ├── FhirMcpServer.java              # Main MCP server
│   │       │   ├── McpTransportConfig.java          # Transport configuration
│   │       │   └── McpSessionManager.java           # Session lifecycle
│   │       ├── tools/
│   │       │   ├── FhirDiscoverTool.java            # fhir_discover implementation
│   │       │   ├── FhirQueryTool.java               # fhir_query implementation
│   │       │   ├── FhirMutateTool.java              # fhir_mutate implementation
│   │       │   ├── ToolResponseEnricher.java         # Smart hints in responses
│   │       │   └── ToolInputValidator.java           # Runtime input validation
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
      # Hybrid B2+ design: 3 unified tools (fhir_discover, fhir_query, fhir_mutate)
      # No per-resource tool generation needed
      response-hints: true           # Include contextual hints in tool responses
      max-results-per-query: 100     # Max resources returned per fhir_query call
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

2. **Minimal tool surface, maximum capability** - 3 MCP tools cover all FHIR interactions. The `fhir_discover` tool enables agents to learn capabilities at runtime, compensating for the generic schemas of `fhir_query` and `fhir_mutate`.

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
| Tool count | Exactly 3 | MCP tools/list response |
| Token overhead per turn | < 1,000 tokens | Tool definitions size in system prompt |
| Tool selection accuracy | > 99% | Agent picks correct tool on first try |
| Discovery-to-first-query | < 2 tool calls | Agent can query data after 1 discover + 1 query call |
| Agent onboarding | < 5 minutes | Time for a new agent to make its first FHIR query via MCP |
| Search accuracy (NL) | > 85% | NL queries correctly translated to FHIR search |
| Event delivery latency | < 500ms | Time from resource change to SSE/webhook delivery |
| Embedding throughput | > 100 resources/sec | Resources embedded per second |
| Scales with resources | Constant tools | Adding resources does not increase tool count |

---

## Testing Strategy

### Clinical Scenario Coverage

Testing AI-integrated healthcare systems requires coverage of clinical edge cases that go beyond typical software testing. The test suite should include:

**BDD Feature Categories:**

| Category | Coverage | Example Scenarios |
|----------|----------|-------------------|
| **Normal workflows** | Common clinical paths | Patient lookup, lab ordering, result viewing |
| **Edge cases** | Unusual but valid scenarios | 90+ year old patient, multiple allergies, complex conditions |
| **Error handling** | System failures and recovery | Database timeout during order, network failure mid-mutation |
| **Consent enforcement** | Privacy compliance | Patient with mental health consent restrictions |
| **AI safety** | Agent guardrails | Hallucinated LOINC code, high-risk medication order |
| **Multi-tenant isolation** | Data separation | Agent from Tenant A cannot access Tenant B data |

### Clinical Edge Case Test Scenarios

The BDD test suite should include 500+ scenarios covering:

**Patient Demographics:**
- Neonates (< 1 month old) - weight-based dosing
- Pediatric (1-18 years) - age-specific reference ranges
- Geriatric (65+ years) - polypharmacy risks
- Pregnant patients - medication contraindications
- Patients with no recorded allergies vs. unknown allergies

**Clinical Complexity:**
- Multiple active diagnoses (5+ conditions)
- Polypharmacy (10+ active medications)
- Drug-drug interactions (mild, moderate, severe)
- Drug-allergy cross-reactivity
- Renal/hepatic impairment affecting drug dosing
- Critical lab values requiring immediate action

**AI-Specific Edge Cases:**
```gherkin
@ai-safety
Scenario: Agent attempts to use hallucinated LOINC code
  Given a clinical-summary-agent is connected via MCP
  When the agent calls fhir_mutate to create an Observation
  And the Observation uses LOINC code "99999-0" which does not exist
  Then the response should include terminologyValidation.valid = false
  And the response should suggest valid alternatives
  And the mutation should be rejected if strict-mode is enabled

@ai-safety
Scenario: Agent attempts high-risk order without confirmation
  Given a clinical-summary-agent with standard scopes
  When the agent calls fhir_mutate to create a MedicationRequest
  And the medication is a controlled substance
  Then the response should include riskLevel = "HIGH"
  And the response should include requiresReview = true
  And the mutation should require explicit confirmation

@consent
Scenario: Agent queries patient with mental health consent restriction
  Given Patient/123 has a Consent restricting mental health records
  When a research-agent queries for Condition resources for Patient/123
  Then mental health conditions should be excluded from results
  And a consent-filter-applied audit entry should be created

@multi-tenant
Scenario: Agent cannot access data from other tenant
  Given clinical-summary-agent is authorized for tenant "hospital-a"
  When the agent queries for Patient resources
  Then only patients from "hospital-a" are returned
  And attempting to read Patient/999 from "hospital-b" returns 403
```

### Test Data Generation

**Synthetic patient generator:**
```java
@Component
public class ClinicalTestDataGenerator {

    /**
     * Generate realistic synthetic patients with various clinical profiles
     */
    public List<Patient> generatePatientCohort(CohortConfig config) {
        List<Patient> patients = new ArrayList<>();

        // Age distribution (pediatric, adult, geriatric)
        for (AgeGroup group : config.getAgeGroups()) {
            patients.addAll(generateForAgeGroup(group, config.getCountPerGroup()));
        }

        // Add clinical complexity
        patients.forEach(p -> {
            addConditions(p, randomize(1, 5));
            addMedications(p, randomize(0, 10));
            addAllergies(p, randomize(0, 3));
            addLabHistory(p, randomize(10, 50));
        });

        return patients;
    }

    /**
     * Generate edge case scenarios
     */
    public void generateEdgeCases() {
        // Neonatal patient with rare condition
        createNeonatalEdgeCase();

        // Geriatric with polypharmacy
        createPolypharmacyEdgeCase();

        // Patient with complex consent restrictions
        createConsentEdgeCase();

        // Patient with critical pending results
        createCriticalLabEdgeCase();
    }
}
```

### CI/CD Integration

```yaml
# .github/workflows/clinical-tests.yml
clinical-tests:
  runs-on: ubuntu-latest
  steps:
    - name: Run BDD Clinical Scenarios
      run: ./mvnw test -pl fhir4java-server -Dtest=ClinicalCucumberIT

    - name: Run AI Safety Tests
      run: ./mvnw test -pl fhir4java-mcp -Dtest=AiSafetyIT

    - name: Run Consent Enforcement Tests
      run: ./mvnw test -pl fhir4java-server -Dtest=ConsentEnforcementIT

    - name: Run Multi-Tenant Isolation Tests
      run: ./mvnw test -pl fhir4java-server -Dtest=TenantIsolationIT

    - name: Generate Clinical Test Report
      run: ./mvnw surefire-report:report
```

---

## MVP Definition

### MVP Scope (Phases 1-3)

The Minimum Viable Product includes the foundational capabilities for AI agent integration:

| Pillar | MVP Components | Deferred to Later |
|--------|----------------|-------------------|
| **1. MCP Server** | 3 tools (discover, query, mutate), Streamable HTTP transport, Basic response hints, Dry-run mode, Risk-level tagging | SSE/stdio transports |
| **2. Events** | SSE endpoint for resource changes | Full FHIR Subscriptions, Webhook registry |
| **3. Semantic Search** | NL-to-FHIR translation (rule-based) | Vector search, Medical embeddings, Hybrid search |
| **4. Auth** | OAuth2 resource server, API key auth, Basic scopes | Consent enforcement, Provenance tracking, Data localization |
| **5. Bulk Data** | Basic $export | De-identification, NDJSON streaming |
| **6. Orchestration** | - | Composite workflows, CDS Hooks, GraphQL |
| **7-9. Clinical UI** | - | All deferred to later phases |
| **10. Observability** | Basic MCP audit logging | Full metrics, Grafana dashboards, Decision context |

### MVP Dependency Map

```
Phase 1 (Foundation)                    Phase 2 (MCP)                      Phase 3 (Events)
─────────────────────                   ────────────────                   ────────────────

┌──────────────────┐                   ┌──────────────────┐               ┌──────────────────┐
│ DiscoveryService │──────────────────▶│ fhir_discover    │◀──────────────│ SSE Events       │
│ (reads registries│                   │ tool             │               │                  │
└────────┬─────────┘                   └────────┬─────────┘               └──────────────────┘
         │                                      │
         ��                                      │
         │                             ┌────────▼─────────┐
         │                             │ fhir_query tool  │
         │                             │                  │
         │                             └────────┬─────────┘
         │                                      │
         │                                      │
┌────────▼─────────┐                   ┌────────▼─────────┐
│ OAuth2 Resource  │──────────────────▶│ fhir_mutate tool │
│ Server           │                   │ + dry-run        │
└──────────────────┘                   └──────────────────┘

┌──────────────────┐                   ┌──────────────────┐
│ API Key Auth     │──────────────────▶│ MCP Audit        │
│                  │                   │ Logging          │
└──────────────────┘                   └──────────────────┘
```

### MVP Success Criteria

| Criterion | Target | Validation |
|-----------|--------|------------|
| Agent can discover capabilities | `fhir_discover(topic: "all")` returns valid response | Integration test |
| Agent can query data | `fhir_query(action: "search")` returns FHIR Bundle | Integration test |
| Agent can mutate data | `fhir_mutate(action: "create")` persists resource | Integration test |
| Dry-run works | `fhir_mutate(dryRun: true)` validates without persisting | Unit test |
| OAuth2 protects endpoints | Unauthenticated requests return 401 | Security test |
| API key auth works | Valid API key grants access | Integration test |
| SSE events stream | Resource changes appear on SSE stream | Integration test |
| Audit logs captured | All MCP calls logged to database | Query audit table |
| Multi-tenant isolation | Agent A cannot access Tenant B data | Security test |

### Post-MVP Roadmap

| Phase | Focus | Key Deliverables |
|-------|-------|------------------|
| **Phase 4** | Intelligence | Vector search, NL query with LLM, Medical embeddings |
| **Phase 5** | Orchestration | Composite workflows, CDS Hooks, GraphQL |
| **Phase 6** | Clinical UI Backend | Command API, Queue management, Note lifecycle |
| **Phase 7** | Configuration | UI config service, Multi-level preferences |
| **Phase 8** | Clinical UI Frontend | React app, Patient chart, Queue dashboard |
| **Phase 9** | Compliance | Full consent enforcement, Provenance, Data localization |
| **Phase 10** | Operations | Grafana dashboards, Advanced metrics, OpenTelemetry |

---

## Localization Considerations

### Multi-Language Support

For deployment in Asia (Singapore, China) and other regions, consider:

**Prompt Templates:**
```yaml
# mcp-prompts/patient-summary.yml
name: patient_summary
description:
  en: "Generate a clinical summary for a patient"
  zh: "为患者生成临床摘要"
messages:
  - role: user
    content:
      en: "Generate a clinical summary for this patient: ..."
      zh: "为此患者生成临床摘要：..."
```

**Terminology Mappings:**
```yaml
fhir4java:
  terminology:
    mappings:
      snomed-ct:
        edition: international        # or zh-cn for Chinese edition
        fallback: international
      icd-10:
        version: icd-10-cm           # or icd-10-zh for Chinese version
```

**Configuration:**
```yaml
fhir4java:
  localization:
    default-locale: en
    supported-locales:
      - en
      - zh-CN
      - zh-TW
    terminology:
      snomed-chinese-edition: true
      icd-10-chinese-version: true
```

---

## Summary

This design transforms FHIR4Java from a **FHIR server** into a **complete AI-ready clinical platform** serving two audiences:

**For AI Agents (Pillars 1-6, 10):**
- MCP integration with 3 unified tools (discover, query, mutate) keeps the interface lean and scalable
- Safety features: dry-run mode, risk-level tagging prevent accidental mutations; terminology validation handled by existing FHIR validation pipeline
- `fhir_discover` enables runtime capability learning (standard OpenAPI/CapabilityStatement for non-AI consumers)
- Event streaming (SSE, WebSocket, FHIR Subscriptions) enables real-time agent reactions
- Semantic search with medical-domain embeddings (ClinicalBERT, BioBERT) and hybrid search (structured + keyword + vector)
- Agent-friendly auth with OAuth2/SMART scopes, Consent enforcement, AI Provenance tracking, and data localization
- Bulk data export with de-identification profiles (Safe Harbor, k-anonymity) for research use cases
- Comprehensive observability with MCP audit logging, explainability metadata, and immutable audit trails for regulatory compliance

**For Clinical UI (Pillars 7-9):**
- Command API provides natural language interface for clinicians (voice/text)
- Tiered interpretation pipeline minimizes LLM usage through caching and pattern matching
- Multi-level UI configuration (system → tenant → role → user) enables organizational customization
- Queue management supports full ambulatory workflow (scheduled → arrived → roomed → ready → consultation → checkout)
- Note lifecycle service manages pended/signed/addendum states with pending results coordination

**Compliance & Security:**
- FHIR Consent resource enforcement ensures patient privacy preferences are respected
- AI Provenance tracking creates audit trail for all AI-generated content (model, confidence, human review status)
- Relationship-based access control with qualifiers (`:own`, `:care-team`, `:ai-suggested`)
- Data localization support for regional regulations (Singapore PDPA, China PIPL/DSL, EU GDPR, US HIPAA)

**Testing & Quality:**
- Comprehensive BDD test suite with 500+ clinical scenarios
- AI safety tests covering hallucinated codes, high-risk orders, consent enforcement
- Multi-tenant isolation verification
- Synthetic patient data generation for edge case testing

**Integration Points:**
- Command API reuses interpretation logic from MCP tools internally
- Clinical events (result-received, queue-updated, etc.) flow through the same event infrastructure as agent events
- UI configuration tables share the `fhir` schema with tenant configuration
- Observability (Pillar 10) captures all MCP interactions across all pillars

The 8-phase implementation ensures production stability while incrementally delivering value: Phases 1-3 deliver the MVP (MCP tools, basic auth, events), Phases 4-5 add intelligence and orchestration, Phases 6-8 add clinical UI support. See the MVP Definition section for scope details.
