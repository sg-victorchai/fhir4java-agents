# Phase 8: Clinical UI Frontend Implementation Plan (Weeks 36-47)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** React-based clinical UI with patient chart, queue dashboard, and real-time updates.

**Architecture:** React + TypeScript SPA. Component library for clinical widgets. WebSocket for real-time events. Command bar for voice/text input.

**Tech Stack:** React 18, TypeScript, TanStack Query, Tailwind CSS, Radix UI

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillars 7, 8, 9

---

## File Structure

```
fhir4java-ui/                              # NEW MODULE (React app)
├── package.json
├── src/
│   ├── main.tsx                           # Entry point
│   ├── App.tsx                            # Router setup
│   │
│   ├── api/
│   │   ├── fhirClient.ts                  # FHIR API client
│   │   ├── mcpClient.ts                   # MCP tool client (optional)
│   │   └── hooks/
│   │       ├── usePatient.ts              # Patient queries
│   │       ├── useQueue.ts                # Queue queries
│   │       └── useEvents.ts               # SSE/WebSocket hooks
│   │
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppShell.tsx               # Main layout
│   │   │   ├── Sidebar.tsx                # Navigation
│   │   │   └── CommandBar.tsx             # Natural language input
│   │   │
│   │   ├── patient/
│   │   │   ├── PatientBanner.tsx          # Demographics header
│   │   │   ├── PatientChart.tsx           # Main chart view
│   │   │   ├── ProblemList.tsx            # Conditions
│   │   │   ├── MedicationList.tsx         # Medications
│   │   │   ├── VitalSigns.tsx             # Recent vitals
│   │   │   └── LabResults.tsx             # Lab observations
│   │   │
│   │   ├── queue/
│   │   │   ├── QueueDashboard.tsx         # Queue overview
│   │   │   ├── QueueCard.tsx              # Individual patient card
│   │   │   └── QueueFilters.tsx           # Status filters
│   │   │
│   │   ├── note/
│   │   │   ├── NoteEditor.tsx             # Rich text note editor
│   │   │   ├── NoteStatusBadge.tsx        # Draft/Pended/Signed
│   │   │   └── PendingResults.tsx         # Results tracker
│   │   │
│   │   └── common/
│   │       ├── FhirResource.tsx           # Generic resource display
│   │       ├── CodedValue.tsx             # Display coded values
│   │       └── DateDisplay.tsx            # Date formatting
│   │
│   ├── pages/
│   │   ├── DashboardPage.tsx              # Home/overview
│   │   ├── QueuePage.tsx                  # Queue management
│   │   ├── PatientPage.tsx                # Patient chart
│   │   └── SettingsPage.tsx               # User preferences
│   │
│   └── store/
│       ├── authStore.ts                   # Auth state
│       ├── patientStore.ts                # Selected patient
│       └── configStore.ts                 # UI config
```

---

## Tasks

### Task 1: Project Setup

- [ ] **Step 1:** Create React app with Vite + TypeScript
- [ ] **Step 2:** Configure Tailwind CSS, Radix UI
- [ ] **Step 3:** Set up TanStack Query for data fetching
- [ ] **Step 4:** Configure proxy to backend API
- [ ] **Step 5:** Commit `feat(ui): initialize React frontend`

### Task 2: FHIR API Client

**Files:** `fhir4java-ui/src/api/fhirClient.ts`

- [ ] **Step 1:** Implement typed FHIR client with fetch
- [ ] **Step 2:** Add auth header injection (OAuth2 token)
- [ ] **Step 3:** Create React Query hooks (usePatient, useObservations, etc.)
- [ ] **Step 4:** Commit `feat(ui): add FHIR API client`

### Task 3: Real-Time Events Hook

**Files:** `fhir4java-ui/src/api/hooks/useEvents.ts`

- [ ] **Step 1:** Implement SSE connection to `/api/events/stream`
- [ ] **Step 2:** Parse events and update TanStack Query cache
- [ ] **Step 3:** Handle reconnection on disconnect
- [ ] **Step 4:** Commit `feat(ui): add real-time events hook`

### Task 4: App Shell & Navigation

**Files:** `fhir4java-ui/src/components/layout/`

- [ ] **Step 1:** Implement AppShell with sidebar, header, main content
- [ ] **Step 2:** Add responsive sidebar navigation
- [ ] **Step 3:** Set up React Router routes
- [ ] **Step 4:** Commit `feat(ui): add app shell and navigation`

### Task 5: Queue Dashboard

**Files:** `fhir4java-ui/src/components/queue/`, `pages/QueuePage.tsx`

- [ ] **Step 1:** Implement QueueDashboard with status columns
- [ ] **Step 2:** Add QueueCard with patient info, wait time
- [ ] **Step 3:** Implement drag-and-drop for status transitions
- [ ] **Step 4:** Add real-time updates via useEvents
- [ ] **Step 5:** Commit `feat(ui): add queue dashboard`

### Task 6: Patient Banner & Chart

**Files:** `fhir4java-ui/src/components/patient/`

- [ ] **Step 1:** Implement PatientBanner with demographics, allergies
- [ ] **Step 2:** Implement PatientChart container with tabs
- [ ] **Step 3:** Add ProblemList, MedicationList, VitalSigns components
- [ ] **Step 4:** Commit `feat(ui): add patient chart components`

### Task 7: Lab Results Panel

**Files:** `fhir4java-ui/src/components/patient/LabResults.tsx`

- [ ] **Step 1:** Implement LabResults with grouping by panel
- [ ] **Step 2:** Add trend charts for numeric values
- [ ] **Step 3:** Highlight abnormal values
- [ ] **Step 4:** Commit `feat(ui): add lab results panel`

### Task 8: Command Bar

**Files:** `fhir4java-ui/src/components/layout/CommandBar.tsx`

- [ ] **Step 1:** Implement command input with autocomplete
- [ ] **Step 2:** Send commands to `/api/command` endpoint
- [ ] **Step 3:** Display command results inline
- [ ] **Step 4:** Add keyboard shortcut (Cmd+K / Ctrl+K)
- [ ] **Step 5:** Commit `feat(ui): add command bar`

### Task 9: Note Editor

**Files:** `fhir4java-ui/src/components/note/NoteEditor.tsx`

- [ ] **Step 1:** Implement rich text editor (TipTap or similar)
- [ ] **Step 2:** Add pend/sign buttons with state transitions
- [ ] **Step 3:** Show pending results tracker
- [ ] **Step 4:** Commit `feat(ui): add note editor`

### Task 10: UI Configuration Integration

**Files:** `fhir4java-ui/src/store/configStore.ts`

- [ ] **Step 1:** Fetch effective config from `/api/ui-config`
- [ ] **Step 2:** Apply config to panel visibility, shortcuts
- [ ] **Step 3:** Add user preferences page
- [ ] **Step 4:** Commit `feat(ui): integrate UI configuration`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1-2 | Project setup, FHIR client |
| 3 | Real-time events |
| 4 | App shell & navigation |
| 5 | Queue dashboard |
| 6-7 | Patient chart |
| 8 | Command bar |
| 9 | Note editor |
| 10 | UI configuration |

**Total: 10 tasks**
