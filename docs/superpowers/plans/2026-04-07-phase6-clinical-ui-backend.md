# Phase 6: Clinical UI Backend Implementation Plan (Weeks 24-31)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Command API for natural language clinical commands, queue management, and note lifecycle services.

**Architecture:** Tiered command interpretation (cache → pattern → LLM). Queue state machine for ambulatory workflow. Note lifecycle with pended/signed states.

**Tech Stack:** Spring State Machine, Redis (command cache), optional LLM integration

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillars 7, 9

---

## File Structure

```
fhir4java-core/src/main/java/org/fhirframework/core/
├── command/
│   ├── CommandInterpreter.java            # Tiered interpretation pipeline
│   ├── CommandCache.java                  # Redis/in-memory cache
│   ├── PatternMatcher.java                # Regex-based command matching
│   ├── CommandTemplate.java               # Pre-seeded command templates
│   └── CommandResult.java                 # Interpreted command result

├── queue/
│   ├── QueueService.java                  # Queue management facade
│   ├── QueueState.java                    # Enum: scheduled→arrived→roomed→ready→consultation→checkout
│   ├── QueueStateMachine.java             # State transitions
│   └── QueueEventPublisher.java           # Publish queue changes

├── note/
│   ├── NoteLifecycleService.java          # Note state management
│   ├── NoteState.java                     # Enum: draft→pended→signed→addendum
│   ├── PendingResultsTracker.java         # Track pending results for notes
│   └── NoteCompletionChecker.java         # Check if note ready to sign

fhir4java-api/src/main/java/org/fhirframework/api/
├── command/
│   └── CommandController.java             # POST /api/command

├── queue/
│   └── QueueController.java               # Queue management API

├── note/
│   └── NoteController.java                # Note lifecycle API
```

---

## Tasks

### Task 1: Command Cache & Templates

**Files:** `fhir4java-core/.../command/CommandCache.java`, `CommandTemplate.java`

- [ ] **Step 1:** Define CommandTemplate with pattern, FHIR operation mapping
- [ ] **Step 2:** Implement CommandCache (in-memory, Redis optional)
- [ ] **Step 3:** Pre-seed 20+ common clinical command templates
- [ ] **Step 4:** Commit `feat(core): add command cache and templates`

### Task 2: Command Interpreter Pipeline

**Files:** `fhir4java-core/.../command/CommandInterpreter.java`

- [ ] **Step 1:** Implement Tier 1: cache lookup for exact matches
- [ ] **Step 2:** Implement Tier 2: pattern matching with regex
- [ ] **Step 3:** Implement Tier 3: LLM fallback (optional, configurable)
- [ ] **Step 4:** Return CommandResult with FHIR operations to execute
- [ ] **Step 5:** Commit `feat(core): add tiered command interpreter`

### Task 3: Command API Endpoint

**Files:** `fhir4java-api/.../CommandController.java`

- [ ] **Step 1:** Write test for `POST /api/command`
- [ ] **Step 2:** Implement controller with risk-based execution
- [ ] **Step 3:** Return command result with audit trail
- [ ] **Step 4:** Commit `feat(api): add command interpretation endpoint`

### Task 4: Queue State Machine

**Files:** `fhir4java-core/.../queue/QueueStateMachine.java`

- [ ] **Step 1:** Define state transitions: scheduled→arrived→roomed→ready→consultation→checkout
- [ ] **Step 2:** Implement transition validation rules
- [ ] **Step 3:** Publish queue events on state change
- [ ] **Step 4:** Commit `feat(core): add queue state machine`

### Task 5: Queue Management API

**Files:** `fhir4java-api/.../QueueController.java`

- [ ] **Step 1:** Write tests for queue operations
- [ ] **Step 2:** Implement `GET /api/queue` (list patients in queue)
- [ ] **Step 3:** Implement `PUT /api/queue/{encounterId}/transition`
- [ ] **Step 4:** Commit `feat(api): add queue management endpoints`

### Task 6: Note Lifecycle Service

**Files:** `fhir4java-core/.../note/NoteLifecycleService.java`

- [ ] **Step 1:** Define note states: draft→pended→signed→addendum
- [ ] **Step 2:** Implement state transition logic
- [ ] **Step 3:** Add PendingResultsTracker for result-awaiting notes
- [ ] **Step 4:** Commit `feat(core): add note lifecycle service`

### Task 7: Note Lifecycle API

**Files:** `fhir4java-api/.../NoteController.java`

- [ ] **Step 1:** Write tests for note operations
- [ ] **Step 2:** Implement `POST /api/notes/{id}/pend`, `/sign`, `/addendum`
- [ ] **Step 3:** Implement pending results check before sign
- [ ] **Step 4:** Commit `feat(api): add note lifecycle endpoints`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1-3 | Command API with tiered interpretation |
| 4-5 | Queue management |
| 6-7 | Note lifecycle |

**Total: 7 tasks**
