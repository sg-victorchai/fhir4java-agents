# Next-Generation Clinical UI Design: FHIR4Java

**Status:** DRAFT - Ready for Review
**Created:** 2026-03-22
**Last Updated:** 2026-03-24

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

### Cross-Window State Synchronization (Dual Monitor)

When operating in dual-window mode, state must be synchronized between browser windows using the BroadcastChannel API (React Context cannot share state across windows):

```typescript
// Shared channel for clinical session state
const clinicalChannel = new BroadcastChannel('fhir4java-clinical-session');

// Publish state changes
clinicalChannel.postMessage({
  type: 'PATIENT_CONTEXT_CHANGED',
  patientId: 'Patient/123',
  encounterId: 'Encounter/456'
});

// Subscribe to state changes from other window
clinicalChannel.onmessage = (event) => {
  if (event.data.type === 'PATIENT_CONTEXT_CHANGED') {
    syncPatientContext(event.data.patientId, event.data.encounterId);
  }
};
```

The `PatientContext` provider wraps both windows and uses BroadcastChannel to keep them synchronized. Events synchronized:
- Patient selection/deselection
- Encounter start/end
- Note state changes (draft saved, signed)
- Real-time alerts and notifications

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

## Clinical Note Workflow & Documentation States

### Terminology Alignment (Epic Standard)

This design uses industry-standard terminology aligned with Epic EHR:

| Term | Definition | When Used |
|------|------------|-----------|
| **Pended** | Draft saved but not complete; visible in "Incomplete" tab | Note in progress, may be awaiting results |
| **Signed** | Finalized and locked; triggers automated distribution | Note complete, no further edits allowed |
| **Cosign Needed** | Awaiting supervisor validation/attestation | Trainee notes requiring attending review |
| **Addendum** | Addition to previously signed note | Late results, corrections, supplemental info |

### FHIR Resource Mapping

Clinical notes are represented using **Composition** (structured content) and **DocumentReference** (metadata/binary):

**Important**: `Encounter.location.status` tracks patient physical location (planned/active/reserved/completed), NOT note status.

#### Composition.status (Document Maturity)

| Status | Definition | Epic Equivalent |
|--------|------------|-----------------|
| `partial` | Initial/interim/preliminary; data incomplete | Pended (draft) |
| `preliminary` | Early verified results, not all final | Pended (some content ready) |
| `final` | Complete and verified, no further work planned | Signed |
| `amended` | Modified after release as final | Addendum (content change) |
| `corrected` | Modified to correct an error | Addendum (error correction) |
| `appended` | New content added after final | Addendum (addition) |

#### DocumentReference.docStatus

| Status | Definition |
|--------|------------|
| `preliminary` | Document draft/incomplete |
| `final` | Document is complete |
| `amended` | Document has been revised |

### Note Lifecycle State Machine

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CLINICAL NOTE LIFECYCLE                           │
│            (Epic terminology + FHIR Composition.status)              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐       │
│  │ PENDED   │───▶│ PENDED   │───▶│  SIGNED  │───▶│ ADDENDUM │       │
│  │ (partial)│    │(prelim.) │    │ (final)  │    │(amended/ │       │
│  │          │    │          │    │          │    │ appended)│       │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘       │
│       │               │               │                              │
│       ▼               ▼               ▼                              │
│  Initial draft   Results ready,  Locked, no     Late results        │
│  Awaiting labs   A&P drafted     further edits  require addendum    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ COSIGN NEEDED                                                │    │
│  │ (Applies to Pended or Signed notes requiring attestation)   │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### State Mapping Summary

| Workflow State | Epic Term | Composition.status | DocumentReference.docStatus | Encounter.status |
|----------------|-----------|-------------------|---------------------------|------------------|
| Visit started, note begun | Pended | `partial` | `preliminary` | `in-progress` |
| Awaiting lab results | Pended | `partial` or `preliminary` | `preliminary` | `in-progress` |
| Results ready, completing | Pended | `preliminary` | `preliminary` | `in-progress` |
| Note finalized | Signed | `final` | `final` | `finished` |
| Late result arrives | Addendum | `appended` | `amended` | `finished` |
| Error correction | Addendum | `corrected` | `amended` | `finished` |

---

## Pending Results & Note Completion Workflow

### Clinical Reality

Notes often cannot be finalized immediately because test results are pending:

| Result Type | Typical Turnaround | Workflow Impact |
|-------------|-------------------|-----------------|
| STAT labs | 15-60 minutes | Keep session warm, notify when ready |
| Routine labs | 2-24 hours | Save as Pended, resume when results arrive |
| Specialized tests | Days to weeks | May require Addendum after note signed |
| Imaging | Minutes to hours | Depends on modality and radiologist |

### Workflow During Encounter

When clinician orders tests and needs to wait for results:

```
┌────────────────────────────────────────────────────────────────┐
│ COMMAND WORKSPACE                                               │
├────────────────────────────────────────────────────────────────┤
│ ▼ Activity Stream                                               │
│                                                                 │
│ 09:15 [Clinician] Patient reports chest pain for 2 days        │
│ 09:16 [Patient] It gets worse when I breathe deeply            │
│ 09:18 [Order] Troponin, D-dimer, Chest X-ray ✓ Sent            │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ ⏳ AWAITING RESULTS                                          ││
│ │ • Troponin I (STAT) - Est. 30 min                           ││
│ │ • D-dimer (STAT) - Est. 30 min                              ││
│ │ • Chest X-ray - Est. 1 hour                                  ││
│ │                                                              ││
│ │ [Continue with other documentation]  [Save & Close for Now] ││
│ └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│ ────── NOTE PREVIEW (Pended) ──────                             │
│ Chief Complaint: Chest pain x 2 days                           │
│ HPI: 58yo male presents with...                                │
│ Assessment & Plan: ⚠️ PENDING - Awaiting cardiac workup        │
└────────────────────────────────────────────────────────────────┘
```

### Save & Close Behavior

When clinician selects "Save & Close for Now":
1. Note saved with `Composition.status = partial`
2. System tracks pending orders via extension
3. Subscriptions created for result notifications
4. Clinician can see other patients while waiting

### Result Notification

When results arrive, notification appears regardless of current context:

```
┌────────────────────────────────────────────────────────────────┐
│ 🔔 NOTIFICATION BANNER                                         │
├────────────────────────────────────────────────────────────────┤
│ Results Ready: John Smith (MRN: 12345)                         │
│ • Troponin I: 0.02 ng/mL (Normal)                              │
│ • D-dimer: 0.45 (Normal)                                       │
│                                                                 │
│ [Resume Note]  [View Results Only]  [Remind in 15 min]         │
└────────────────────────────────────────────────────────────────┘
```

### Resume with AI-Assisted Completion

When clinician resumes a pended note:

```
┌────────────────────────────────────────────────────────────────┐
│ COMMAND WORKSPACE - Resuming Note for John Smith               │
├────────────────────────────────────────────────────────────────┤
│ ▼ Activity Stream (continued)                                   │
│                                                                 │
│ 09:18 [Order] Troponin, D-dimer, Chest X-ray ✓ Sent            │
│ ─────── SESSION PAUSED: 09:25 ───────                          │
│ ─────── SESSION RESUMED: 10:02 ──────                          │
│ 10:02 [Results] Troponin I: 0.02 ng/mL ✓ Normal                │
│ 10:02 [Results] D-dimer: 0.45 µg/mL ✓ Normal                   │
│ 10:15 [Results] CXR: No acute cardiopulmonary process          │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ 🤖 AI-SUGGESTED ASSESSMENT & PLAN                           ││
│ │                                                              ││
│ │ Assessment:                                                  ││
│ │ Chest pain, likely musculoskeletal. Cardiac workup negative ││
│ │ (troponin 0.02, D-dimer 0.45, CXR unremarkable). No evidence││
│ │ of ACS or PE.                                                ││
│ │                                                              ││
│ │ Plan:                                                        ││
│ │ 1. Supportive care with NSAIDs PRN                          ││
│ │ 2. Return precautions discussed                              ││
│ │ 3. Follow up with PCP in 1 week if symptoms persist         ││
│ │                                                              ││
│ │ [Accept]  [Edit]  [Regenerate]  [Write My Own]              ││
│ └─────────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────┘
```

### Pending Results Tracking Panel

Clinicians can view all their pending notes:

```
┌─────────────────────────────────┐
│ 📋 MY PENDED NOTES              │
├─────────────────────────────────┤
│ John Smith (09:15 visit)        │
│   ⏳ CXR - Est. 45 min remaining│
│   ✓ Troponin - READY            │
│   ✓ D-dimer - READY             │
│                                 │
│ Mary Johnson (08:30 visit)      │
│   ⏳ UA Culture - Est. 48 hours │
│   ✓ Urinalysis - READY          │
│                                 │
│ [2 notes ready for completion]  │
└─────────────────────────────────┘
```

### Addendum Workflow (Late Results)

For results arriving after note is signed:

```
┌────────────────────────────────────────────────────────────────┐
│ 🔔 LATE RESULT NOTIFICATION                                    │
├────────────────────────────────────────────────────────────────┤
│ Culture result ready for Mary Johnson                          │
│ Original visit: 3 days ago (note signed)                       │
│                                                                 │
│ Result: Urine culture - E. coli > 100,000 CFU/mL               │
│         Sensitive to: Cipro, Bactrim, Nitrofurantoin           │
│                                                                 │
│ AI Suggestion: Patient was prescribed Bactrim empirically.     │
│ Culture confirms sensitivity. No action needed unless          │
│ patient reports ongoing symptoms.                               │
│                                                                 │
│ [Create Addendum]  [Acknowledge Only]  [Call Patient]          │
└────────────────────────────────────────────────────────────────┘
```

### Workflow Decision Matrix

| Scenario | Note State | Action | Result |
|----------|------------|--------|--------|
| STAT results (< 2 hours) | Pended (partial) | Keep session warm, auto-notify | One-click resume |
| Same-day routine (2-8 hours) | Pended (partial) | Save, notify when ready | Context preserved on resume |
| Multi-day results | Pended (preliminary) | Save, notify when ready | AI summarizes intervening events |
| Results after signing | Signed (final) | Create Addendum | New Composition with `appended` status |

### FHIR Data Model

```typescript
// Clinical Note as FHIR Composition
interface ClinicalNoteComposition {
  resourceType: 'Composition';
  status: 'partial' | 'preliminary' | 'final' | 'amended' | 'corrected' | 'appended';
  type: CodeableConcept;  // LOINC code: 11506-3 (Progress Note), 34117-2 (H&P), etc.
  encounter: Reference<Encounter>;
  date: string;  // dateTime
  author: Reference<Practitioner>[];
  title: string;

  // Cosign/attestation workflow
  attester?: {
    mode: 'personal' | 'professional' | 'legal' | 'official';
    time?: string;
    party?: Reference<Practitioner>;
  }[];

  // Note sections (Chief Complaint, HPI, Exam, A&P, etc.)
  section: {
    title: string;
    code: CodeableConcept;
    text: Narrative;
    entry?: Reference<Resource>[];  // Links to orders, results, conditions
  }[];

  // Extension: Pending results tracking
  extension?: {
    url: 'http://fhir4java.org/StructureDefinition/awaiting-results';
    extension: {
      url: 'order';
      valueReference: Reference<ServiceRequest>;
    } | {
      url: 'expectedTime';
      valueDateTime: string;
    } | {
      url: 'resultReceived';
      valueBoolean: boolean;
    }[];
  }[];
}

// DocumentReference for note metadata
interface ClinicalNoteDocumentReference {
  resourceType: 'DocumentReference';
  status: 'current' | 'superseded';
  docStatus: 'preliminary' | 'final' | 'amended';
  type: CodeableConcept;
  subject: Reference<Patient>;
  context: {
    encounter: Reference<Encounter>[];
    period: Period;
  };
  content: {
    attachment: Attachment;  // Rendered PDF or HTML
  }[];
}

// Encounter tracks patient location, NOT note status
interface ClinicalEncounter {
  resourceType: 'Encounter';
  status: 'planned' | 'arrived' | 'triaged' | 'in-progress' |
          'onleave' | 'finished' | 'cancelled';

  // Location status = where patient IS, not note state
  location?: {
    location: Reference<Location>;
    status: 'planned' | 'active' | 'reserved' | 'completed';
    period: Period;
  }[];
}
```

### AI Features for Note Completion

| Feature | Trigger | Behavior |
|---------|---------|----------|
| **Result Interpretation** | Results arrive | AI interprets in context of chief complaint |
| **A&P Drafting** | All results ready | AI drafts assessment incorporating findings |
| **Critical Value Alert** | Abnormal result | Immediate notification with clinical guidance |
| **Additional Test Suggestion** | Results inconclusive | AI suggests follow-up tests if warranted |
| **Addendum Drafting** | Late result on signed note | AI drafts addendum text for review |

---

## Patient Search & Queue Management

### Ambulatory Visit Workflow

The system supports a complete patient flow from check-in to checkout, with role-based access control:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AMBULATORY VISIT WORKFLOW                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│  │ SCHEDULED │───▶│  ARRIVED  │───▶│  ROOMED   │───▶│   READY   │          │
│  │           │    │           │    │           │    │           │          │
│  │ Appt only │    │ Checked in│    │ In exam   │    │ Vitals    │          │
│  │           │    │ at front  │    │ room      │    │ complete  │          │
│  └───────────┘    └───────────┘    └───────────┘    └─────┬─────┘          │
│       │                                                    │                │
│       │                                                    ▼                │
│       │           ┌───────────┐    ┌───────────┐    ┌───────────┐          │
│       │           │ COMPLETED │◀───│  CHECKOUT │◀───│    IN     │          │
│       │           │           │    │   READY   │    │CONSULTATION│         │
│       │           │ Visit done│    │           │    │           │          │
│       │           │ & closed  │    │ Doc done  │    │ With      │          │
│       │           └───────────┘    └───────────┘    │ provider  │          │
│       │                                             └───────────┘          │
│       ▼                                                                     │
│  ┌───────────┐                                                              │
│  │  NO SHOW  │   (Patient did not arrive)                                  │
│  └───────────┘                                                              │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════   │
│  ROLE PERMISSIONS:                                                          │
│  • Patient Service Assistant: SCHEDULED → ARRIVED → ROOMED                  │
│  •                            CHECKOUT READY → COMPLETED                    │
│  • Nurse/MA:                  ROOMED → READY                                │
│  • Clinician:                 READY → IN CONSULTATION → CHECKOUT READY      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Terminology Alignment (Epic Standard)

| Epic Term | Our Term | FHIR Mapping | Description |
|-----------|----------|--------------|-------------|
| Scheduled | Scheduled | `Appointment.status = booked` (Encounter not yet created) | Appointment exists, patient not arrived |
| Arrived | Arrived | `subjectStatus = arrived` | Patient checked in at front desk |
| Roomed | Roomed | `subjectStatus = triaged` | Patient moved to exam room |
| Ready for Provider | Ready | `subjectStatus = receiving-care` | Vitals done, ready for clinician |
| With Provider | In Consultation | `Encounter.status = in-progress` | Clinician actively seeing patient |
| Checkout Ready | Checkout Ready | `subjectStatus = departed` | Visit complete, pending checkout |
| Checked Out | Completed | `Encounter.status = finished` | Patient left, visit finalized |
| No Show | No Show | `Encounter.status = cancelled` | Patient did not arrive |

### Role-Based Access Control

| Role | Can View Queue | Can Check In/Out | Can Access Chart | Can Document | Can Sign Notes |
|------|---------------|------------------|------------------|--------------|----------------|
| Patient Service Assistant | ✓ | ✓ | ✗ | ✗ | ✗ |
| Medical Assistant | ✓ | ✗ | Limited | ✗ | ✗ |
| Nurse | ✓ | ✗ | ✓ | Nursing notes | ✗ |
| Physician/NP/PA | ✓ | ✗ | ✓ | ✓ | ✓ |
| Resident | ✓ | ✗ | ✓ | ✓ | Needs cosign |

### Queue Dashboard (Patient Service Assistant View)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ FHIR4Java │ Family Medicine Clinic          Today: Mar 24, 2026   Jane Doe │
├─────────────────────────────────────────────────────────────────────────────┤
│ [🔍 Search Patient]  [+ Walk-in]  [📅 Schedule]           [⚙️] [🔔 3] [👤] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ DEPARTMENT QUEUE                                      Filter: [All ▼]  │ │
│ ├─────────────────────────────────────────────────────────────────────────┤ │
│ │                                                                         │ │
│ │ ══════════ WAITING TO BE ROOMED (3) ══════════                         │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐│ │
│ │ │ ⬆️⬇️ │ 🟡 Johnson, Mary      │ 09:00 │ Dr. Smith │ Follow-up    │ 25m ││ │
│ │ │     │ DOB: 1965-03-15 │ F   │ Arrived 08:35                        ││ │
│ │ │     │                       │ [Room Patient]  [Cancel Check-in]    ││ │
│ │ └─────────────────────────────────────────────────────────────────────┘│ │
│ │                                                                         │ │
│ │ ══════════ SCHEDULED - NOT YET ARRIVED (4) ══════════                  │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐│ │
│ │ │     │ ⚪ Williams, James    │ 09:45 │ Dr. Smith │ Follow-up    │     ││ │
│ │ │     │                       │ [Check In]  [No Show]  [Reschedule]  ││ │
│ │ └─────────────────────────────────────────────────────────────────────┘│ │
│ │                                                                         │ │
│ │ ══════════ CHECKOUT READY (1) ══════════                               │ │
│ │ ┌─────────────────────────────────────────────────────────────────────┐│ │
│ │ │     │ 🟢 Thompson, Michael  │ 08:30 │ Dr. Smith │ Follow-up    │     ││ │
│ │ │     │                       │ [Complete Checkout]  [Print AVS]     ││ │
│ │ └─────────────────────────────────────────────────────────────────────┘│ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Queue Dashboard (Clinician View)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ MY PATIENTS                                             [View: Board ▼]    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│ ══════════ IN CONSULTATION (1) ══════════                                   │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 🔵 Adams, Sarah          │ Room 3  │ Started: 09:10 │ 20 min            │ │
│ │ 45F │ Hypertension follow-up                                            │ │
│ │ ⚠️ Allergies: Penicillin │ Last A1c: 7.2%                               │ │
│ │ [Continue Documentation]                                 [Mark Done ➜] │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│ ══════════ READY FOR PROVIDER (2) ══════════                                │
│ ┌─────────────────────────────────────────────────────────────────────────┐ │
│ │ 🟢 Johnson, Mary         │ Room 1  │ Waiting: 12 min                    │ │
│ │ 59F │ Diabetes follow-up                                                │ │
│ │ Vitals: BP 138/84, HR 72 │ Chief: "Here for lab review"                 │ │
│ │ [Start Visit ▶]                                          [View Chart]  │ │
│ └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│ ══════════ UPCOMING (3) ══════════                                          │
│   10:00  Brown, Patricia    │ Annual Exam                                   │
│   10:30  Lee, David         │ Follow-up                                     │
│   11:00  Martinez, Ana      │ Sick Visit                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Navigation Flow by Role

```
PATIENT SERVICE ASSISTANT:
Queue Dashboard → Search Patient → Check-In Wizard → Queue (Updated)
       ↑                                                    │
       └────────────────── Checkout Wizard ◀────────────────┘

CLINICIAN:
Provider Queue → Select Patient → Patient Chart → Command Workspace
       ↑                                              │
       │                                              │ [Mark Done]
       └──────────────── Auto-return ◀────────────────┘
```

### FHIR Data Model for Queue

```typescript
interface QueuedEncounter {
  resourceType: 'Encounter';
  status: 'planned' | 'in-progress' | 'finished' | 'cancelled';

  // Granular patient tracking (FHIR R5)
  subjectStatus: {
    coding: [{
      system: 'http://hl7.org/fhir/encounter-subject-status';
      code: 'arrived' | 'triaged' | 'receiving-care' | 'departed';
    }];
  };

  // Queue-specific extensions
  extension: [{
    url: 'http://fhir4java.org/StructureDefinition/queue-position';
    valueInteger: number;
  }, {
    url: 'http://fhir4java.org/StructureDefinition/assigned-provider';
    valueReference: Reference<Practitioner>;
  }, {
    url: 'http://fhir4java.org/StructureDefinition/room-assignment';
    valueString: string;
  }];

  subject: Reference<Patient>;
  appointment: Reference<Appointment>;
  location: {
    location: Reference<Location>;
    status: 'planned' | 'active' | 'completed';
  }[];
}
```

---

## Frontend Architecture

### Component Hierarchy

```
App
├── AuthProvider
├── PatientProvider
├── WebSocketProvider
│
└── AppShell
    ├── GlobalHeader
    │   ├── Logo
    │   ├── GlobalSearch (patient lookup)
    │   ├── NotificationBell
    │   └── UserMenu
    │
    ├── RoleBasedNav (routes based on user role)
    │
    ├── QueueDashboard (for queue views)
    │   ├── QueueFilters
    │   ├── QueueList (grouped by status)
    │   │   └── QueueCard (per patient)
    │   └── CheckInWizard / CheckOutWizard
    │
    └── ClinicalView (for chart + documentation)
        ├── PatientSafetyBanner
        │
        └── MainLayout (split-pane)
            ├── PatientChartPane (collapsible left)
            │   ├── ChartNavigation
            │   └── ChartCanvas
            │       ├── ProblemsPanel
            │       ├── MedicationsPanel
            │       ├── AllergiesPanel
            │       ├── VitalsPanel
            │       └── ResultsPanel
            │
            └── CommandWorkspacePane
                ├── CommandInput (voice/text)
                ├── ActivityStream
                ├── NotePreviewPanel
                └── ConfirmationModal
```

### State Management

```typescript
// TanStack Query for server state (FHIR data)
// - Automatic caching & deduplication
// - Background refetching
// - Optimistic updates

// Zustand for UI state
interface LayoutStore {
  chartPaneCollapsed: boolean;
  chartPaneWidth: number;
  activePanels: string[];
}

interface CommandStore {
  inputMode: 'voice' | 'text';
  isListening: boolean;
  pendingCommand: Command | null;
  commandHistory: Command[];
}

interface SessionStore {
  currentEncounterId: string | null;
  activityStream: ActivityEntry[];
  noteState: 'idle' | 'active' | 'pended';
  pendingResults: PendingResult[];
}

interface QueueStore {
  encounters: QueuedEncounter[];
  filters: QueueFilters;
  viewMode: 'board' | 'list';
  groupBy: 'status' | 'provider' | 'time';
}

// React Context for cross-cutting concerns
// - AuthContext: user, tenant, permissions
// - PatientContext: selected patient
// - WebSocketContext: real-time subscriptions
```

### FHIR Data Fetching Strategy

```typescript
// Custom hooks for FHIR resources
function usePatientObservations(patientId: string, category?: string) {
  return useQuery({
    queryKey: ['fhir', 'Observation', { patient: patientId, category }],
    queryFn: () => fhirClient.search('Observation', { patient: patientId, category }),
    staleTime: 30_000,
  });
}

// Command API mutation
function useCommandMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (command: CommandRequest) => commandApi.execute(command),
    onSuccess: (result) => {
      result.affectedResources?.forEach(ref => {
        queryClient.invalidateQueries({ queryKey: ['fhir', ref.resourceType] });
      });
    },
  });
}
```

---

## Patient Chart Canvas

### Expanded View (Dual Monitor / Full Width)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ PATIENT CHART CANVAS                                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ [Problems] [Meds] [Allergies] [Vitals] [Results] [Visits] [Docs]        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ PROBLEMS (Active)                                        [+ Add] [▼]│ │
│ │ • Type 2 Diabetes Mellitus (E11.9)           Dx: 2019    Chronic   │ │
│ │ • Essential Hypertension (I10)               Dx: 2018    Chronic   │ │
│ │ • Hyperlipidemia (E78.5)                     Dx: 2020    Chronic   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ MEDICATIONS (Active)                                     [+ Add] [▼]│ │
│ │ • Metformin 1000mg         PO BID          Last filled: 2026-03-01 │ │
│ │ • Lisinopril 20mg          PO Daily        Last filled: 2026-03-01 │ │
│ │ • Atorvastatin 40mg        PO QHS          Last filled: 2026-02-15 │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ ALLERGIES                                                    [+ Add]│ │
│ │ ⚠️ Penicillin              Rash, Hives               Confirmed      │ │
│ │ ⚠️ Sulfa drugs             Anaphylaxis               Confirmed      │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ VITALS (Last 3)                                          [Trend] [▼]│ │
│ │ Today 09:15    BP: 142/88 ⚠️  HR: 78   Temp: 98.6°F   SpO2: 98%   │ │
│ │ 2026-03-10     BP: 138/84     HR: 72   Temp: 98.4°F   SpO2: 99%   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### Collapsed View (Single Monitor - ~250px width)

```
┌────────────────────────────────┐
│ PROBLEMS (4)              [▶] │
│ • Diabetes                     │
│ • Hypertension                 │
│ • Hyperlipidemia               │
│ • Chest pain (today)           │
├────────────────────────────────┤
│ MEDS (4)                  [▶] │
│ • Metformin 1000mg             │
│ • Lisinopril 20mg              │
│ • Atorvastatin 40mg            │
├────────────────────────────────┤
│ ⚠️ ALLERGIES (2)          [▶] │
│ • Penicillin                   │
│ • Sulfa                        │
├────────────────────────────────┤
│ VITALS (Today)            [▶] │
│ BP: 142/88 ⚠️                 │
│ HR: 78  SpO2: 98%              │
├────────────────────────────────┤
│ RESULTS                   [▶] │
│ 🔴 2 abnormal                  │
│ ⏳ 3 pending                   │
└────────────────────────────────┘
```

---

## Command Workspace

### Full Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│ COMMAND WORKSPACE                                                        │
├─────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ INPUT BAR                                                           │ │
│ │ ┌─────┐ ┌─────────────────────────────────────────────────┐ ┌─────┐│ │
│ │ │ 🎤  │ │ Type or speak a command...                      │ │ ⏎   ││ │
│ │ └─────┘ └─────────────────────────────────────────────────┘ └─────┘│ │
│ │ [Voice: ON]  [Mode: Ambient Documentation]  Encounter: Today 09:15 │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ ACTIVITY STREAM                                                     │ │
│ ├─────────────────────────────────────────────────────────────────────┤ │
│ │ 09:15 [👤 Patient]                                                  │ │
│ │ "I've been having this chest pain for about two days now."         │ │
│ │                                                                     │ │
│ │ 09:16 [🩺 Clinician]                                                │ │
│ │ "Can you describe the pain? Is it sharp, dull, pressure-like?"     │ │
│ │                                                                     │ │
│ │ 09:18 [⚡ Command] "Order troponin, d-dimer, and chest x-ray"       │ │
│ │ ┌─────────────────────────────────────────────────────────────┐   │ │
│ │ │ ✓ Orders placed:                                             │   │ │
│ │ │ • Troponin I (STAT)   • D-dimer (STAT)   • Chest X-ray      │   │ │
│ │ └─────────────────────────────────────────────────────────────┘   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ NOTE PREVIEW (Pended)                                    [Expand]  │ │
│ │ Chief Complaint: Chest pain x 2 days                               │ │
│ │ HPI: 58yo male presents with chest pain described as an ache...    │ │
│ │ Assessment & Plan: ⏳ Awaiting cardiac workup results              │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### Activity Stream Entry Types

| Type | Icon | Description |
|------|------|-------------|
| `transcript-patient` | 👤 | Patient speech (ambient mode) |
| `transcript-clinician` | 🩺 | Clinician speech (ambient mode) |
| `command` | ⚡ | Voice/text command |
| `command-result` | ✓/✗ | Command execution result |
| `result-received` | 📊 | Lab/imaging result arrived |
| `ai-suggestion` | 🤖 | AI-generated suggestion |
| `session-marker` | ─── | Session start/pause/resume |

### Selection & Confirmation Patterns

**Multiple Options:**
```
┌─────────────────────────────────────────────────────────────────────────┐
│ Multiple options found. Select one:                                     │
│                                                                         │
│ ○ Metoprolol Succinate ER 25mg   (once daily)      [Common]            │
│ ○ Metoprolol Succinate ER 50mg   (once daily)                          │
│ ○ Metoprolol Tartrate 25mg       (twice daily)                         │
│                                                                         │
│ [Cancel]                                              [Select: 1-3]    │
└─────────────────────────────────────────────────────────────────────────┘
```

**Confirmation Form (High-Risk):**
```
┌─────────────────────────────────────────────────────────────────────────┐
│ 📋 PRESCRIPTION CONFIRMATION                                            │
│                                                                         │
│ Medication:  Metoprolol Succinate ER 25mg                              │
│ Sig:         Take 1 tablet by mouth once daily         [Edit]          │
│ Quantity:    30 tablets                                [Edit]          │
│ Refills:     3                                         [Edit]          │
│ Pharmacy:    CVS - 123 Main St                         [Change]        │
│                                                                         │
│ ⚠️ Interactions: None detected                                          │
│ ✓ Allergies: No known allergies to beta-blockers                       │
│                                                                         │
│ [Cancel]                                    [Sign & Send to Pharmacy]  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Voice Integration

### Web Speech API Implementation

```typescript
function useVoiceInput(config: VoiceConfig) {
  const [state, setState] = useState<VoiceState>({
    isListening: false,
    transcript: '',
    interimTranscript: '',
    confidence: 0,
  });

  // Browser-native speech recognition
  const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)();
  recognition.continuous = config.continuous;
  recognition.interimResults = true;
  recognition.lang = 'en-US';

  recognition.onresult = (event) => {
    // Process results, apply medical vocabulary enhancement
    const processed = enhanceMedicalTerms(transcript);
    setState(s => ({ ...s, transcript: processed }));
  };

  return { ...state, startListening, stopListening };
}
```

### Medical Vocabulary Enhancement

Common misrecognitions corrected automatically:

| Heard | Corrected |
|-------|-----------|
| "met foreman" | metformin |
| "lies in a pearl" | lisinopril |
| "a one c" | A1c |
| "see bc" | CBC |
| "bee mp" | BMP |
| "cabbage" | CABG |

### Speaker Diarization (Ambient Mode)

- Heuristic-based classification of clinician vs patient speech
- Medical terminology density → likely clinician
- Symptom language ("I feel", "It hurts") → likely patient
- Voice profile matching when calibrated

---

## Backend Command Service

### Architecture

```
CommandController (POST /api/command)
         │
         ▼
CommandService
         │
    ┌────┼────┐
    ▼    ▼    ▼
CommandInterpreter  CommandExecutor  CommandCache
    │                    │
    │    ┌───────────────┤
    ▼    ▼               ▼
PatternMatcher      QueryExecutor    OrderCreator
SemanticSearcher    PrescriptionWriter
LlmInterpreter      NoteUpdater
```

### Command Request/Response

```java
public record CommandRequest(
    String command,           // Natural language
    Reference patient,        // Patient reference
    Reference encounter,      // Optional encounter
    CommandMode mode          // EXECUTE or DRAFT
) {}

public record CommandResponse(
    String commandId,
    CommandStatus status,     // COMPLETED, AWAITING_CONFIRMATION, FAILED
    InterpretedCommand interpretation,
    Object result,
    List<Suggestion> suggestions,
    List<Reference> affectedResources
) {}
```

### Interpretation Pipeline

1. **Tier 1: Exact Cache** (~0ms) - Hash lookup
2. **Tier 2: Pattern Match** (~1-5ms) - YAML templates
3. **Tier 3: Semantic Search** (~10-50ms) - Embeddings
4. **Tier 4: LLM** (~200-500ms) - Novel commands only

### Risk Assessment

| Risk Level | Examples | Behavior |
|------------|----------|----------|
| NONE | Queries, navigation | Auto-execute |
| LOW | Routine labs, documentation | Auto-execute |
| MEDIUM | Orders with alerts | Show warning |
| HIGH | Prescriptions, high-alert meds | Require confirmation |

---

## Real-Time Updates

### WebSocket Architecture

```
Browser ◀════════════▶ WebSocketHandler (/ws/events)
   │                          │
   │ subscribe(topic)         │
   │─────────────────────────▶│
   │                          │
   │ event(data)              │ EventPublisher
   │◀─────────────────────────│◀──── Domain Events
```

### Event Types

| Event | Trigger | Data |
|-------|---------|------|
| `result-received` | DiagnosticReport created | Report, related orders, isCritical |
| `order-status-changed` | ServiceRequest updated | Previous/new status |
| `note-updated` | Composition modified | Change type, updatedBy |
| `critical-value` | Abnormal result detected | Observation, value, normal range |
| `queue-updated` | Encounter status changed | Encounter, new subjectStatus |

### Subscription Topics

- `patient/{id}/results` - Results for specific patient
- `patient/{id}/orders` - Order updates
- `department/{id}/queue` - Queue changes
- `alerts/critical` - All critical value alerts

---

## Security & Audit

### Authentication Flow

```
Browser → FHIR4Java → Identity Provider (Keycloak/Okta/Azure AD)
   │          │              │
   │ Login    │              │
   │─────────▶│─────────────▶│ OAuth2/OIDC
   │          │              │
   │          │◀─────────────│ JWT Tokens
   │◀─────────│              │
   │ Session  │              │
```

### JWT Claims

```typescript
interface JwtClaims {
  sub: string;              // User ID
  tenant_id: string;        // Tenant
  practitioner_id: string;  // FHIR Practitioner reference
  roles: string[];          // ['physician', 'nurse', etc.]
  permissions: string[];    // ['patient:read', 'order:write', etc.]
}
```

### Permission Model

| Permission | Description |
|------------|-------------|
| `patient:read` | View patient data |
| `patient:write` | Modify patient data |
| `order:write` | Create orders |
| `rx:sign` | Sign prescriptions (e-prescribe) |
| `note:write` | Create/edit notes |
| `note:sign` | Sign notes |
| `note:cosign` | Cosign trainee notes |

### Command Audit Trail

All commands logged to `command_audit_log`:

| Field | Description |
|-------|-------------|
| command_id | UUID |
| user_id | Who executed |
| patient_ref | Affected patient |
| raw_command | Original input |
| interpreted_intent | Parsed intent |
| interpretation_source | CACHE/PATTERN/SEMANTIC/LLM |
| execution_status | COMPLETED/FAILED |
| affected_resources | Created/modified resources |
| client_ip | Source IP |
| created_at | Timestamp |

---

## Project Structure

### New Module: fhir4java-ui

```
fhir4java-ui/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
│
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   │
│   ├── components/
│   │   ├── ui/              # shadcn/ui components
│   │   ├── layout/          # AppShell, GlobalHeader, MainLayout
│   │   ├── queue/           # QueueDashboard, QueueCard, CheckInWizard
│   │   ├── patient/         # PatientSearch, PatientCard
│   │   ├── chart/           # PatientChartPane, panels/*
│   │   └── workspace/       # CommandWorkspacePane, ActivityStream
│   │
│   ├── hooks/
│   │   ├── useFhirQuery.ts
│   │   ├── useCommand.ts
│   │   ├── useVoiceInput.ts
│   │   ├── useWebSocket.ts
│   │   └── useQueue.ts
│   │
│   ├── stores/
│   │   ├── layoutStore.ts
│   │   ├── commandStore.ts
│   │   ├── sessionStore.ts
│   │   └── queueStore.ts
│   │
│   ├── contexts/
│   │   ├── AuthContext.tsx
│   │   ├── PatientContext.tsx
│   │   └── WebSocketContext.tsx
│   │
│   ├── lib/
│   │   ├── fhirClient.ts
│   │   ├── commandApi.ts
│   │   └── voiceEnhancer.ts
│   │
│   └── types/
│       ├── fhir.d.ts
│       ├── command.d.ts
│       └── queue.d.ts
│
└── tests/
```

### Backend Additions (fhir4java-api)

```
fhir4java-api/src/main/java/com/fhir4java/api/
├── controller/
│   ├── CommandController.java      # NEW
│   └── WebSocketController.java    # NEW
├── service/
│   ├── CommandService.java         # NEW
│   ├── CommandInterpreter.java     # NEW
│   └── CommandExecutor.java        # NEW
└── websocket/
    ├── ClinicalWebSocketHandler.java  # NEW
    └── ClinicalEventPublisher.java    # NEW
```

### Build Configuration

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    outDir: '../fhir4java-server/src/main/resources/static',
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/fhir': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
```

---

## Open Questions

1. **Voice wake word**: Should we support "Hey Chart" or similar, or push-to-talk only?
2. **Offline support**: Any requirements for offline capability (PWA)?
3. **Mobile**: Is mobile/tablet support in scope for MVP?
4. **Terminology server**: External SNOMED/ICD-10/RxNorm lookup, or bundled?
5. **Multi-language**: English-only for MVP, or internationalization needed?

---

## Next Steps

1. Review and approve this design specification
2. Run spec review
3. Transition to implementation planning (writing-plans skill)

---

*Document complete. Ready for review.*
