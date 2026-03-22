# Next-Generation Clinical UI Design: FHIR4Java

**Status:** DRAFT - Brainstorming In Progress
**Created:** 2026-03-22
**Last Updated:** 2026-03-22

---

## Executive Summary

This document captures the design for a next-generation clinical user interface for FHIR4Java, transforming it from a backend-only FHIR server into a complete end-to-end healthcare IT system. The UI introduces a revolutionary **two-pane paradigm** where clinicians interact primarily through an AI-powered command workspace while viewing patient context on a separate display area.

### Key Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Adoption Strategy | Power Users First | Tech-savvy clinicians become champions |
| MVP Scope | Scope 3: Full Ambulatory + Inpatient Lite | Compelling demo of full patient journey |
| Architecture | Approach A: Pure Web SPA | Fastest time-to-value, zero installation |
| UI Paradigm | Two-Pane Split (Chart + Command Workspace) | Context always visible, actions in workspace |
| Backend Integration | Command API (MCP-style abstraction) | Single round-trip, UI doesn't need FHIR knowledge |

---

## Vision: The Two-Pane Paradigm

### Core Concept

Traditional EHRs require navigating between 20+ screens with 4-8 clicks per action. This design eliminates navigation entirely after initial patient selection:

```
┌─────────────────────────────────────────────────────────────────────────┐
│  TRADITIONAL EHR                     THIS DESIGN                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Screen 1 → Screen 2 → Screen 3      ┌─────────────┐ ┌───────────────┐  │
│      ↓          ↓          ↓         │ PATIENT     │ │ COMMAND       │  │
│  Screen 4 → Screen 5 → ...           │ CHART       │ │ WORKSPACE     │  │
│                                      │ (Context)   │ │ (Action)      │  │
│  Many screens, many clicks           │             │ │               │  │
│                                      │ Read-mostly │ │ Read + Write  │  │
│                                      └─────────────┘ └───────────────┘  │
│                                                                          │
│                                      TWO panes, ZERO navigation          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Pane Responsibilities

| Aspect | Patient Chart Pane | Command Workspace Pane |
|--------|-------------------|------------------------|
| **Purpose** | Context / Reference | Interaction / Action |
| **Content** | Essential patient info, composable panels | Command input, results, forms, confirmations |
| **Mode** | Read-mostly | Read + Write |
| **Dual monitor** | Full screen on Monitor 1 | Full screen on Monitor 2 |
| **Single monitor** | Collapses to banner/summary | Expands to fill working area |

### Dual Monitor Layout

```
┌─────────────────────────┐    ┌─────────────────────────────────────┐
│      MONITOR 1          │    │           MONITOR 2                 │
│  ┌───────────────────┐  │    │  ┌───────────────────────────────┐ │
│  │                   │  │    │  │ 🎤 "Show me last 3 A1c"      │ │
│  │   PATIENT CHART   │  │    │  ├───────────────────────────────┤ │
│  │   (FULL SCREEN)   │  │    │  │ A1c Results:                 │ │
│  │                   │  │    │  │ • 2026-03-15: 8.1% ⚠️        │ │
│  │   All composable  │  │    │  │ • 2025-12-10: 7.8% ⚠️        │ │
│  │   panels visible  │  │    │  │ • 2025-09-05: 7.2%           │ │
│  │                   │  │    │  │                               │ │
│  │   Glanceable      │  │    │  │ 🤖 "Trend shows worsening..." │ │
│  │   reference       │  │    │  │ [Order A1c] [Adjust Meds]    │ │
│  │                   │  │    │  └───────────────────────────────┘ │
│  └───────────────────┘  │    └─────────────────────────────────────┘
└─────────────────────────┘

Clinician LOOKS at Monitor 1 for context
Clinician WORKS in Monitor 2 for actions
```

### Single Monitor Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PATIENT CHART (MINIMIZED/COLLAPSED)                     │   │
│  │ ┌─────┐ ┌─────────────────────────────────────────────┐ │   │
│  │ │Photo│ │ John Smith, 67M │ MRN: 12345 │ ⚠️ 3 Alerts │ │   │
│  │ └─────┘ └─────────────────────────────────────────────┘ │   │
│  │ [Expand Chart ▼]                                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ COMMAND WORKSPACE (EXPANDED)                            │   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ 🎤 "Show me last 3 A1c results"                     │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ A1c Results:                                        │ │   │
│  │ │ • 2026-03-15: 8.1% ⚠️ HIGH                         │ │   │
│  │ │ • 2025-12-10: 7.8% ⚠️ HIGH                         │ │   │
│  │ │ • 2025-09-05: 7.2%                                  │ │   │
│  │ │                                                     │ │   │
│  │ │ 🤖 AI: "A1c trending up 0.9% over 6 months."       │ │   │
│  │ │                                                     │ │   │
│  │ │ [Order A1c Today] [Adjust Medications] [Ask More]   │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

Patient context COLLAPSES to banner
Command Workspace EXPANDS to fill space
User can toggle [Expand Chart] to see full chart temporarily
```

---

## MVP Scope: Full Ambulatory + Inpatient Lite

### Included in MVP

- Patient lookup & chart viewing
- Appointment scheduling
- Visit registration / check-in
- Clinical documentation (notes)
- Orders (labs, imaging, referrals)
- Prescriptions (e-prescribe)
- Results review & acknowledgment
- Basic charge capture (CPT/ICD)
- Inpatient admission
- Inpatient discharge
- Medication administration (eMAR)
- Procedure documentation

### Deferred to Later Phases

- Claims submission
- Remittance processing
- Denial management
- Complex billing rules
- Insurance eligibility
- Prior authorization
- Population health dashboards
- Quality measures reporting
- Patient portal
- Telehealth integration

---

## System Architecture

### High-Level Overview

```
┌───────────────────────────────────────────────────────────────────┐
│                         USER DEVICES                               │
│   SINGLE MONITOR              DUAL MONITOR                        │
│   ┌─────────────────┐         ┌──────────┐ ┌──────────┐           │
│   │ Browser Window  │         │ Window 1 │ │ Window 2 │           │
│   │ ┌─────────────┐ │         │ Chart    │ │ Command  │           │
│   │ │ Chart       │ │         │ Canvas   │ │ Workspace│           │
│   │ │ (collapsed) │ │         │ (Full)   │ │ (Full)   │           │
│   │ ├─────────────┤ │         │          │ │          │           │
│   │ │ Command     │ │         │          │ │          │           │
│   │ │ Workspace   │ │         │          │ │          │           │
│   │ └─────────────┘ │         └──────────┘ └──────────┘           │
│   └─────────────────┘                                             │
└───────────────────────────────────────────────────────────────────┘
                                   │
                                   │ HTTPS / WebSocket
                                   ▼
┌───────────────────────────────────────────────────────────────────┐
│                    FHIR4JAVA BACKEND                               │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────┐  │
│  │ Command API     │ │ FHIR REST API   │ │ MCP Server          │  │
│  │ /api/command    │ │ /fhir/r5/*      │ │ /mcp                │  │
│  │                 │ │                 │ │                     │  │
│  │ For: Web UI     │ │ For: Direct     │ │ For: AI Agents      │  │
│  │ High-level cmds │ │ FHIR access     │ │ (Claude, GPT, etc.) │  │
│  └─────────────────┘ └─────────────────┘ └─────────────────────┘  │
│                              │                                     │
│  ┌───────────────────────────┴───────────────────────────────┐    │
│  │ Core Services                                              │    │
│  │ ResourceRegistry │ SearchParamRegistry │ PluginOrchestrator│    │
│  └────────────────────────────────────────────────────────────┘    │
│                              │                                     │
│                              ▼                                     │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │                    POSTGRESQL                               │    │
│  │  fhir_resource │ fhir_search_index │ fhir_audit_log        │    │
│  └────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────────────────┘
```

### Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Frontend Framework | React 19 + TypeScript | Largest ecosystem, best FHIR libraries |
| State Management | TanStack Query + Zustand | Server state caching + UI state |
| UI Components | shadcn/ui + Tailwind CSS | Beautiful, accessible, customizable |
| Voice Input | Web Speech API | Browser-native, no installation |
| Multi-Window | Dual browser windows | Single-monitor collapses chart pane |
| Backend Communication | Command API + WebSocket | Single round-trip + real-time updates |

---

## Command API Architecture

### Why Command API (Not Direct FHIR Calls)

The UI sends high-level natural language commands; the server handles FHIR complexity:

```
APPROACH: UI CALLS COMMAND API

Browser                              Server
   │                                    │
   │── POST /api/command ──────────────▶│  Round trip 1
   │   {                                │
   │     command: "Order CBC, BMP,      │  Server handles:
   │               and lipid panel",    │  • Interpret command
   │     context: { patient, enc }      │  • Create 3 ServiceRequests
   │   }                                │  • Return unified result
   │◀─ {                                │
   │     status: "completed",           │
   │     results: [...],                │
   │     suggestions: [...]             │
   │   } ───────────────────────────────│

TOTAL: 1-2 round trips, UI just sends natural language
```

### Simplified Command Execution Flow

```
┌─────────┐          ┌─────────────────────────────────┐
│ Browser │          │         FHIR4Java Server        │
└────┬────┘          └────────────────┬────────────────┘
     │                                │
     │  1. COMMAND                    │
     │  POST /api/command             │
     │  {                             │
     │    command: "Order CBC",       │
     │    patient: "Patient/123",     │
     │    mode: "execute"             │ ─── or "draft" for preview
     │  }                             │
     │───────────────────────────────▶│
     │                                │  2. INTERPRET + EXECUTE
     │                                │  (single server-side flow)
     │                                │
     │  3. RESPONSE                   │
     │◀───────────────────────────────│
     │  {                             │
     │    status: "completed",        │
     │    result: { ... },            │
     │    suggestions: [...]          │
     │  }                             │
     │                                │
     │  4. REAL-TIME UPDATE (async)   │
     │◀══════════════════════════════ │  WebSocket push
     │  { event: "order-created" }    │
```

### Risk-Based Execution Modes

| Auto-Execute (No Confirmation) | Draft (Requires Confirmation) |
|--------------------------------|-------------------------------|
| All queries/searches | Prescriptions (medications) |
| View patient data | High-risk orders (chemo) |
| Expand chart sections | Delete operations |
| Navigation commands | Status changes (discharge) |
| Low-risk orders (routine labs) | External sends (e-prescribe) |
| Documentation drafts | Billing/charges |

---

## Command Interpretation: Reducing LLM Calls

### Tiered Interpretation Pipeline

```
INPUT: "Order CBC"
         │
         ▼
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

### Cache Layers

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

### Pre-Seeded Command Templates

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
    requiresConfirmation: true
```

---

## Technology Stack

### Frontend

| Component | Technology | Purpose |
|-----------|------------|---------|
| Framework | React 19 + TypeScript | UI framework |
| Build Tool | Vite | Fast dev server and build |
| State (Server) | TanStack Query | FHIR data fetching/caching |
| State (UI) | Zustand | Panel layout, preferences |
| Components | shadcn/ui | Accessible UI components |
| Styling | Tailwind CSS | Utility-first CSS |
| Voice | Web Speech API | Browser-native STT |
| AI Streaming | Vercel AI SDK | Streaming AI responses |
| FHIR Types | @smile-cdr/fhirts | TypeScript FHIR types |
| Forms | react-hook-form | Form management |
| Data Grid | @tanstack/react-table | Results tables |
| Charts | recharts | Clinical data visualization |

### Backend (Existing + New)

| Component | Module | Status |
|-----------|--------|--------|
| FHIR REST API | fhir4java-api | Existing |
| MCP Server | fhir4java-mcp | Planned (AI-ready design) |
| Command API | fhir4java-api | New |
| Command Interpreter | fhir4java-core | New |
| WebSocket/SSE | fhir4java-api | New |

---

## Clinical Workflow Coverage

| Workflow | FHIR Resources | UI Panels |
|----------|---------------|-----------|
| **Scheduling** | Appointment, Schedule, Slot | Calendar View, Slot Picker |
| **Registration** | Patient, RelatedPerson, Coverage, Consent | Demographics Form, Insurance Panel |
| **Clinical Visit** | Encounter, Observation, Condition, CarePlan | Visit Dashboard, Vitals Entry, Problem List |
| **Orders** | ServiceRequest, Specimen | Order Entry Panel, Lab Order Form |
| **Medications** | MedicationRequest, MedicationAdministration | Rx Writer, eMAR |
| **Results** | DiagnosticReport, Observation | Results Inbox, Trending Charts |
| **Discharge** | Encounter, DocumentReference, Appointment | Discharge Summary, Instruction Builder |
| **Billing** | Claim (basic) | Charge Capture, Code Search |

---

## Remaining Design Sections (To Be Completed)

The following sections need to be designed in the next session:

1. **Frontend Architecture Details**
   - Component hierarchy
   - State management patterns
   - FHIR data fetching strategy

2. **Patient Chart Canvas Design**
   - Composable panel system
   - Information architecture
   - Responsive collapse behavior

3. **Command Workspace Design**
   - Input modes (voice/text)
   - Result display patterns
   - Form generation for data entry

4. **Voice Integration**
   - Web Speech API implementation
   - Medical vocabulary handling
   - Error recovery

5. **Backend Command Service**
   - CommandInterpreter implementation
   - CommandExecutor implementation
   - Cache implementation

6. **Real-Time Updates**
   - WebSocket event design
   - Optimistic UI updates

7. **Security & Audit**
   - Authentication flow
   - Command audit trail

8. **Project Structure**
   - New module: fhir4java-ui
   - Build and deployment

---

## Open Questions

1. **Voice wake word**: Should we support "Hey Chart" or similar, or push-to-talk only?
2. **Offline support**: Any requirements for offline capability (PWA)?
3. **Mobile**: Is mobile/tablet support in scope for MVP?
4. **Terminology server**: External SNOMED/ICD-10/RxNorm lookup, or bundled?
5. **Multi-language**: English-only for MVP, or internationalization needed?

---

## Next Steps

1. Resume brainstorming to complete remaining design sections
2. Review and finalize the design
3. Run spec review
4. Get user approval
5. Transition to implementation planning (writing-plans skill)

---

*This document is a work in progress. Last saved during active brainstorming session.*
