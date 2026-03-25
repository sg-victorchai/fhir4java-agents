# AI-Ready Platform Design: FHIR4Java as a Native AI Agent Data Platform

**Status:** DRAFT - Ready for Review
**Created:** 2026-03-22
**Last Updated:** 2026-03-25

## Executive Summary

This document proposes the enhancements needed to transform FHIR4Java from a traditional FHIR server into a **fully AI-ready data and API platform** that natively integrates within AI agent ecosystems and supports a next-generation clinical UI.

The design covers **ten pillars**:

| # | Pillar | Purpose |
|---|--------|---------|
| 1 | **MCP Server** | Expose FHIR as AI tools (3 unified tools: discover, query, mutate) |
| 2 | **API Discovery** | OpenAPI + enhanced CapabilityStatement for all consumers |
| 3 | **Event-Driven Architecture** | Real-time updates via FHIR Subscriptions, SSE, WebSocket |
| 4 | **Semantic Search** | Natural language queries + vector search for clinical narratives |
| 5 | **Agent-Friendly Auth** | OAuth2/SMART on FHIR + API keys with scoped authorization |
| 6 | **Bulk Data Processing** | $export + NDJSON streaming for large datasets |
| 7 | **AI Orchestration** | Composite workflows, CDS Hooks, GraphQL |
| 8 | **Command API** | Natural language command interface for clinical UI |
| 9 | **UI Configuration** | Multi-level config system (system → tenant → role → user) |
| 10 | **Clinical Workflow** | Queue management, note lifecycle, pending results tracking |

Pillars 1-7 serve **AI agents** (Claude, GPT, custom agents).
Pillars 8-10 serve the **clinical web UI** and its backend requirements.

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

#### 1.4 FHIR Resources as MCP Resources

Expose FHIR data as native MCP "resources" — a read-only data primitive in the MCP protocol separate from tools. This allows host applications to pre-load clinical context before the agent starts reasoning.

**Resource declaration:**
```json
{
  "uri": "fhir://Patient/123",
  "name": "Patient John Doe",
  "mimeType": "application/fhir+json",
  "description": "Patient resource with ID 123"
}
```

**Resource templates for parameterized access:**
```json
{
  "uriTemplate": "fhir://{resourceType}/{id}",
  "name": "FHIR Resource by ID",
  "description": "Read any FHIR resource by type and ID"
}
```

**Why this matters for agents:**

| Use Case | Without MCP Resources | With MCP Resources |
|----------|----------------------|-------------------|
| Context loading | Agent must call `fhir_query` tool | Agent reads `fhir://Patient/123` directly into context |
| Multi-resource context | Multiple sequential tool calls | Host attaches multiple resources in one request |
| LLM context window | Agent decides what to fetch | Host application pre-loads relevant resources |

**Example workflow:**
```
User: "Summarize this patient's recent care"

Host app (e.g., Claude Desktop) can automatically:
1. Attach fhir://Patient/123 as context
2. Attach fhir://Patient/123/$everything as context
3. Agent sees full patient record WITHOUT making tool calls
4. Agent focuses on reasoning, not data fetching
```

**Key benefit:** Reduces round-trips. The host application can pre-populate the agent's context with relevant FHIR data before the agent starts reasoning — no tool calls needed for read-only context.

#### 1.5 Prompt Templates for Clinical Workflows

Pre-built, parameterized prompt templates that agents can invoke for common clinical tasks. The server fetches relevant FHIR data and embeds it into a structured prompt, so the agent doesn't need to understand FHIR queries.

**Available templates:**

| Template | Description | Parameters |
|----------|-------------|------------|
| `patient_summary` | Generate a clinical summary for a patient | `patientId` |
| `lab_trends` | Analyze lab result trends over time | `patientId`, `labCode`, `timeRange` |
| `medication_review` | Review active medications for interactions | `patientId` |
| `care_gap_analysis` | Identify gaps in preventive care | `patientId`, `guidelineSet` |
| `clinical_timeline` | Build chronological timeline of encounters | `patientId`, `startDate`, `endDate` |

**How agents use templates:**

```
Agent calls: getPrompt("patient_summary", { patientId: "123" })

Server returns (with FHIR data already embedded):
{
  "name": "patient_summary",
  "description": "Generate clinical summary for patient",
  "messages": [
    {
      "role": "user",
      "content": "Generate a clinical summary for this patient:\n\n
        **Patient:** John Smith, 67M, MRN 12345\n
        **Problems:** Type 2 DM (2019), HTN (2018), HLD (2020)\n
        **Medications:** Metformin 1000mg BID, Lisinopril 20mg daily, Atorvastatin 40mg QHS\n
        **Allergies:** Penicillin (rash), Sulfa (anaphylaxis)\n
        **Recent Labs:** A1c 8.1% (Mar 15), Cr 1.2 (Mar 15), LDL 142 (Mar 15)\n
        **Recent Visits:** 3 encounters in past 6 months\n\n
        Summarize current health status, key concerns, and care gaps."
    }
  ]
}
```

**Why this matters for agents:**

| Benefit | Description |
|---------|-------------|
| **Standardization** | Consistent clinical analysis across all agents using the server |
| **Data embedding** | Server fetches and formats FHIR data; agent doesn't need FHIR knowledge |
| **Domain expertise** | Templates encode clinical best practices (what data to include, how to analyze) |
| **Reduced token usage** | Agent doesn't spend tokens figuring out what data to fetch or how to structure queries |

**When to use each MCP primitive:**

| MCP Primitive | Best For | Token Impact |
|---------------|----------|--------------|
| **Resources** | Pre-loading context, read-only data access | Reduces tool call overhead |
| **Prompt Templates** | Standardized clinical workflows, consistent analysis | Reduces reasoning tokens |
| **Tools** (`fhir_query`, etc.) | Dynamic queries, mutations, complex operations | Full flexibility |

Together, resources and prompts let agents focus on **clinical reasoning** rather than **data fetching** — the server handles FHIR complexity while the agent handles clinical intelligence.

#### 1.6 Transport Support

| Transport | Use Case | Priority |
|-----------|----------|----------|
| **Streamable HTTP** | Production deployments, web-based agents | P0 |
| **SSE** | Legacy MCP clients, real-time streaming | P1 |
| **stdio** | Local development, CLI-based agents | P1 |

#### 1.7 Validation & Error Handling

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

---

## Pillar 2: API Discovery (OpenAPI + Enhanced CapabilityStatement)

### Goal
Ensure all consumers — FHIR-aware systems, human developers, and non-MCP agents — can discover and understand the server's capabilities through standard protocols.

### Relationship to `fhir_discover` (Pillar 1)

The Hybrid B2+ design in Pillar 1 introduces `fhir_discover` as a dedicated MCP tool for AI agent discovery. This raises a valid question: **does Pillar 2 still need a separate Discovery API?**

The short answer is: **no, not as originally designed.** The `fhir_discover` tool absorbs the core agent-discovery use case. Here's the overlap analysis:

#### What `fhir_discover` Already Covers

| Original Discovery API Endpoint | `fhir_discover` Equivalent | Status |
|--------------------------------|---------------------------|--------|
| `GET /api/discovery/resources` | `fhir_discover(topic: "resources")` | **Absorbed** |
| `GET /api/discovery/resources/{type}` | `fhir_discover(topic: "all", resourceType: "Patient")` | **Absorbed** |
| `GET /api/discovery/operations` | `fhir_discover(topic: "operations")` | **Absorbed** |
| `GET /api/discovery/operations/{type}/{name}` | `fhir_discover(topic: "operations", resourceType: "Patient")` | **Absorbed** |
| `GET /api/discovery/search-parameters/{type}` | `fhir_discover(topic: "searchParams", resourceType: "...")` | **Absorbed** |
| `GET /api/discovery/capabilities` | `fhir_discover(topic: "all")` | **Absorbed** |
| `GET /api/discovery/plugins` | N/A — platform internals, not agent-facing | **Dropped** |
| `GET /api/discovery/tenants` | N/A — admin concern, not discovery | **Dropped** |

For MCP-connected agents, `fhir_discover` provides everything the Discovery API would have — with the advantage of being a native MCP tool that doesn't require the agent to know REST endpoints.

#### What CapabilityStatement Already Covers

FHIR's `CapabilityStatement` (served at `/metadata`) is the standard mechanism for FHIR-to-FHIR interoperability. It declares:

- Supported resource types and their interactions (CRUD)
- Search parameters per resource type (name, type, documentation)
- Extended operations (as canonical URL references)
- Supported FHIR versions
- Security/auth declarations

For **FHIR-aware consumers** (EHR systems, FHIR clients, interoperability engines), CapabilityStatement is the correct and sufficient discovery mechanism. These systems already understand FHIR's interaction model, search parameter types, and operation definitions.

#### Remaining Gaps: What Neither Covers for Non-MCP Consumers

There is one consumer category not served by `fhir_discover` or CapabilityStatement:

**Non-MCP programmatic consumers** — REST API clients, integration scripts, human developers, and AI agents that connect via HTTP rather than MCP. These consumers need:

1. **Machine-readable API specification** — What endpoints exist? What are the request/response schemas?
2. **Interactive documentation** — How do I try out an API call?
3. **Code generation support** — Can I auto-generate a client SDK?

This is the standard role of **OpenAPI**, not a custom Discovery API.

### Revised Pillar 2 Scope

Given the above analysis, Pillar 2 is **reduced and refocused** to two components:

| Component | Audience | Purpose |
|-----------|----------|---------|
| **OpenAPI 3.1** | Human developers, REST clients, non-MCP agents | Standard machine-readable API spec with interactive docs |
| **Enhanced CapabilityStatement** | FHIR-aware systems, EHR integrations | Richer conformance declaration (the spec requires it) |

The custom Discovery REST API (`/api/discovery/*`) is **removed as a standalone pillar**. Its functionality is either:
- Absorbed by `fhir_discover` MCP tool (for AI agents)
- Already covered by CapabilityStatement (for FHIR systems)
- Better served by OpenAPI (for REST developers)

What remains is a **DiscoveryService** — an internal service class that powers the `fhir_discover` tool's responses by reading from `ResourceRegistry`, `SearchParameterRegistry`, and operation configs. This is an implementation detail of Pillar 1, not a separate API surface.

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

**Why OpenAPI and not the custom Discovery API?**

OpenAPI is the industry standard for REST API documentation with a massive ecosystem:
- Clients can auto-generate SDKs in any language
- Tools like Swagger UI provide interactive try-it-out documentation
- CI/CD pipelines can validate API contract conformance
- Non-MCP AI agents (e.g., OpenAI function calling, LangChain) can ingest OpenAPI specs directly

A custom `/api/discovery/*` API would require every consumer to learn a proprietary schema. OpenAPI gives us all the same benefits with zero adoption friction.

**Enhancements beyond default Spring generation:**

The default `springdoc-openapi` output describes Spring controllers generically. We enhance it with FHIR-specific detail by reading from existing configuration:

```java
@Component
public class FhirOpenApiCustomizer implements OpenApiCustomizer {

    @Autowired
    private ResourceRegistry resourceRegistry;

    @Autowired
    private SearchParameterRegistry searchParameterRegistry;

    @Override
    public void customise(OpenAPI openApi) {
        // For each enabled resource in ResourceRegistry:
        // 1. Generate per-resource path docs (/fhir/r5/Patient, /fhir/r5/Observation, etc.)
        // 2. Add search parameters with types, modifiers, and examples to query params
        // 3. Document extended operations with parameter schemas
        // 4. Include FHIR-specific media types (application/fhir+json)
        // 5. Add resource JSON schemas from StructureDefinitions
    }
}
```

This means OpenAPI documentation stays in sync with `ResourceRegistry` configuration automatically — same principle as `fhir_discover`, just a different output format.

**Example generated OpenAPI path (for Patient search):**
```yaml
/fhir/r5/Patient:
  get:
    summary: Search Patient resources
    operationId: searchPatient
    tags: [Patient]
    parameters:
      - name: family
        in: query
        description: "A portion of the family name. Modifiers: :exact, :contains, :missing"
        schema:
          type: string
        examples:
          default: { value: "Smith" }
          exact: { value: "family:exact=O'Brien" }
      - name: birthdate
        in: query
        description: "Patient birth date. Prefixes: eq, ne, lt, gt, le, ge, sa, eb, ap"
        schema:
          type: string
        examples:
          exact: { value: "1990-01-01" }
          range: { value: "ge1980-01-01" }
      - name: identifier
        in: query
        description: "Patient identifier (system|value or value). Modifiers: :exact, :text, :not"
        schema:
          type: string
        examples:
          with_system: { value: "http://hospital.org|12345" }
          value_only: { value: "12345" }
    responses:
      200:
        description: Search results as FHIR Bundle
        content:
          application/fhir+json:
            schema:
              $ref: '#/components/schemas/Bundle'
```

### 2.2 Enhanced CapabilityStatement

The existing `MetadataController` already generates a CapabilityStatement. We enhance it to be more complete, which benefits both FHIR-aware systems and serves as the authoritative conformance declaration:

- **Add `TerminologyCapabilities`** endpoint (`/fhir/r5/metadata?mode=terminology`)
- **Inline operation definitions** as contained resources (so consumers don't need to resolve canonical URLs)
- **Include security/auth declarations** — advertise OAuth2/SMART endpoints, supported scopes
- **Declare MCP support** via extension element (non-standard but useful):
  ```json
  {
    "url": "http://fhir4java.org/StructureDefinition/mcp-support",
    "valueBoolean": true
  }
  ```
- **Add implementation guide references** for supported profiles

This ensures CapabilityStatement remains the single source of truth for FHIR conformance, while OpenAPI serves REST developers and `fhir_discover` serves MCP agents.

### 2.3 Discovery Architecture Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                    Discovery Sources (Internal)                   │
│  ResourceRegistry │ SearchParameterRegistry │ OperationConfigs   │
└────────┬─────────────────────┬──────────────────────┬────────────┘
         │                     │                      │
         ▼                     ▼                      ▼
┌─────────────────┐  ┌─────────────────┐  ┌───────────────────────┐
│ fhir_discover   │  │ OpenAPI 3.1     │  │ CapabilityStatement   │
│ (MCP Tool)      │  │ (/v3/api-docs)  │  │ (/fhir/r5/metadata)  │
│                 │  │                 │  │                       │
│ For: AI agents  │  │ For: REST devs, │  │ For: FHIR systems,   │
│ via MCP         │  │ non-MCP agents, │  │ EHR integrations,    │
│                 │  │ code generators │  │ conformance testing   │
│ Format: MCP     │  │ Format: OpenAPI │  │ Format: FHIR JSON    │
│ tool response   │  │ 3.1 JSON/YAML  │  │ (CapabilityStatement)│
└─────────────────┘  └─────────────────┘  └───────────────────────┘
```

Three discovery surfaces, each serving a distinct audience, all generated from the same source configuration. No custom `/api/discovery/*` REST API needed.

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

## Pillar 8: Command API for Clinical UI

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

### 8.1 Command Endpoint

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

### 8.2 Tiered Interpretation Pipeline

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

### 8.3 Command Cache Layers

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

### 8.4 Risk-Based Execution

| Risk Level | Examples | Behavior |
|------------|----------|----------|
| **NONE** | Queries, navigation, chart viewing | Auto-execute immediately |
| **LOW** | Routine labs, documentation drafts | Auto-execute with notification |
| **MEDIUM** | Orders with drug interactions | Show warning, allow proceed |
| **HIGH** | Prescriptions, high-alert meds, chemo | Require explicit confirmation |
| **CRITICAL** | Delete operations, status changes | Two-step confirmation |

### 8.5 Pre-Seeded Command Templates

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

### 8.6 Implementation Components

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

### 8.7 Command Audit Trail

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

## Pillar 9: UI Configuration Service

### Goal
Provide a multi-level configuration system for the clinical UI, enabling organizations, roles, and individual users to customize panel layouts, columns, filters, and views.

### 9.1 Configuration Hierarchy

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

### 9.2 UI Configuration Tables

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

### 9.3 UI Configuration API

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

### 9.4 Configuration Merge Logic

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

## Pillar 10: Clinical Workflow Support

### Goal
Provide backend services for clinical workflow management including patient queue management, note lifecycle tracking, and pending results coordination.

### 10.1 Queue Management

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

### 10.2 Note Lifecycle Management

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

### 10.3 Result Notification Service

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

## Implementation Priority & Phasing

### Phase 1: Foundation (Weeks 1-4) - "Agents Can Discover"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| OpenAPI 3.1 generation (with FHIR customizer) | fhir4java-api | P0 | 1 week |
| DiscoveryService (internal, powers `fhir_discover`) | fhir4java-core | P0 | 1 week |
| Enhanced CapabilityStatement | fhir4java-api | P1 | 3 days |
| OAuth2 resource server | fhir4java-server | P0 | 1 week |
| Agent API key auth | fhir4java-plugin | P1 | 1 week |

**Outcome:** REST developers have OpenAPI docs, FHIR systems have enhanced CapabilityStatement, and DiscoveryService is ready to back the `fhir_discover` MCP tool in Phase 2.

### Phase 2: MCP Integration (Weeks 5-8) - "Agents Can Use Tools"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| MCP server module (3 unified tools) | fhir4java-mcp (NEW) | P0 | 2 weeks |
| `fhir_discover` tool (backed by Discovery API) | fhir4java-mcp | P0 | 3 days |
| `fhir_query` tool (read/search/history/ops) | fhir4java-mcp | P0 | 1 week |
| `fhir_mutate` tool (create/update/patch/delete/ops) | fhir4java-mcp | P0 | 1 week |
| Smart response enrichment (hints) | fhir4java-mcp | P1 | 3 days |
| MCP resource providers | fhir4java-mcp | P1 | 3 days |
| Prompt templates | fhir4java-mcp | P2 | 3 days |

**Outcome:** AI agents can connect via MCP and use 3 tools to discover, query, and mutate any FHIR resource.

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

### Phase 6: Clinical UI Backend (Weeks 25-32) - "Clinicians Can Command"

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

### Phase 7: UI Configuration (Weeks 33-36) - "Organizations Can Customize"

| Component | Module | Priority | Effort |
|-----------|--------|----------|--------|
| UI config database schema | fhir4java-persistence | P0 | 3 days |
| UI config service (merge logic) | fhir4java-api | P0 | 1 week |
| Panel configuration API | fhir4java-api | P1 | 1 week |
| Saved views API | fhir4java-api | P1 | 3 days |
| Tenant branding API | fhir4java-api | P2 | 3 days |
| Command alias service | fhir4java-api | P1 | 3 days |

**Outcome:** Multi-level UI configuration system operational. Tenants, roles, and users can customize panels and workflows.

### Phase 8: Clinical UI Frontend (Weeks 37-48) - "Clinicians Can Work"

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

## Summary

This design transforms FHIR4Java from a **FHIR server** into a **complete AI-ready clinical platform** serving two audiences:

**For AI Agents (Pillars 1-7):**
- MCP integration with 3 unified tools (discover, query, mutate) keeps the interface lean and scalable
- `fhir_discover` enables runtime capability learning
- OpenAPI serves REST consumers, enhanced CapabilityStatement serves FHIR systems
- Event streaming (SSE, WebSocket, FHIR Subscriptions) enables real-time agent reactions
- Semantic search allows natural language queries over clinical narratives
- Agent-friendly auth with OAuth2/SMART scopes ensures secure, fine-grained access

**For Clinical UI (Pillars 8-10):**
- Command API provides natural language interface for clinicians (voice/text)
- Tiered interpretation pipeline minimizes LLM usage through caching and pattern matching
- Multi-level UI configuration (system → tenant → role → user) enables organizational customization
- Queue management supports full ambulatory workflow (scheduled → arrived → roomed → ready → consultation → checkout)
- Note lifecycle service manages pended/signed/addendum states with pending results coordination

**Integration Points:**
- Command API reuses interpretation logic from MCP tools internally
- Clinical events (result-received, queue-updated, etc.) flow through the same event infrastructure as agent events
- UI configuration tables share the `fhir` schema with tenant configuration

The 8-phase implementation ensures production stability while incrementally delivering value: Phases 1-5 focus on AI agent capabilities, Phases 6-8 add clinical UI support.
