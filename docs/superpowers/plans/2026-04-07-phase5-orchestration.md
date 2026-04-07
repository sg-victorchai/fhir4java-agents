# Phase 5: Orchestration Implementation Plan (Weeks 18-23)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Enable composite workflows, CDS Hooks integration, and GraphQL API for complex queries.

**Architecture:** Workflow engine with transaction/compensation support. CDS Hooks service for clinical decision support. GraphQL layer over FHIR resources.

**Tech Stack:** Spring State Machine (workflows), CDS Hooks SDK, GraphQL Java, graphql-fhir library

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillar 6

---

## File Structure

```
fhir4java-core/src/main/java/org/fhirframework/core/
├── workflow/
│   ├── WorkflowEngine.java                # Execute composite operations
│   ├── WorkflowDefinition.java            # Workflow step definitions
│   ├── WorkflowStep.java                  # Individual step
│   ├── RollbackStrategy.java              # Transaction/compensating/partial
│   └── WorkflowExecutionContext.java      # Execution state

├── cds/
│   ├── CdsHooksService.java               # CDS Hooks orchestration
│   ├── CdsHookRequest.java                # Hook request DTO
│   ├── CdsHookResponse.java               # Cards response DTO
│   └── CdsHookRegistry.java               # Registered hooks

fhir4java-api/src/main/java/org/fhirframework/api/
├── workflow/
│   └── WorkflowController.java            # POST /api/ai/workflow

├── cds/
│   └── CdsHooksController.java            # POST /cds-services/{id}

├── graphql/
│   ├── GraphQLConfig.java                 # GraphQL setup
│   ├── FhirDataFetcher.java               # Resolve FHIR resources
│   └── FhirTypeResolver.java              # Handle FHIR types
```

---

## Tasks

### Task 1: Workflow Engine Core

**Files:** `fhir4java-core/.../workflow/WorkflowEngine.java`

- [ ] **Step 1:** Define WorkflowDefinition and WorkflowStep models
- [ ] **Step 2:** Implement WorkflowEngine.execute() with step sequencing
- [ ] **Step 3:** Add transaction mode (all-or-nothing rollback)
- [ ] **Step 4:** Add compensating mode (reverse operations on failure)
- [ ] **Step 5:** Commit `feat(core): add workflow engine with rollback strategies`

### Task 2: Workflow API Endpoint

**Files:** `fhir4java-api/.../WorkflowController.java`

- [ ] **Step 1:** Write test for `POST /api/ai/workflow`
- [ ] **Step 2:** Implement controller accepting workflow definition
- [ ] **Step 3:** Return execution result with step outcomes
- [ ] **Step 4:** Commit `feat(api): add workflow execution endpoint`

### Task 3: CDS Hooks Service

**Files:** `fhir4java-core/.../cds/CdsHooksService.java`

- [ ] **Step 1:** Implement CDS Hooks discovery endpoint `GET /cds-services`
- [ ] **Step 2:** Implement hook invocation `POST /cds-services/{id}`
- [ ] **Step 3:** Return CDS Cards with suggestions, links, actions
- [ ] **Step 4:** Commit `feat(core): add CDS Hooks service`

### Task 4: CDS Hooks Controller

**Files:** `fhir4java-api/.../CdsHooksController.java`

- [ ] **Step 1:** Write test for `patient-view` hook
- [ ] **Step 2:** Implement prefetch data resolution
- [ ] **Step 3:** Return cards based on clinical rules
- [ ] **Step 4:** Commit `feat(api): add CDS Hooks controller`

### Task 5: GraphQL Schema Setup

**Files:** `fhir4java-api/.../graphql/GraphQLConfig.java`

- [ ] **Step 1:** Add GraphQL Java dependencies
- [ ] **Step 2:** Define FHIR resource types in GraphQL schema
- [ ] **Step 3:** Implement FhirDataFetcher for Patient, Observation, etc.
- [ ] **Step 4:** Commit `feat(api): add GraphQL schema for FHIR resources`

### Task 6: GraphQL Query Endpoint

**Files:** `fhir4java-api/.../graphql/` + tests

- [ ] **Step 1:** Write test for GraphQL patient query with includes
- [ ] **Step 2:** Implement nested resolution (Patient → Observations)
- [ ] **Step 3:** Add filtering and pagination
- [ ] **Step 4:** Commit `feat(api): add GraphQL query endpoint`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1-2 | Composite workflow engine |
| 3-4 | CDS Hooks integration |
| 5-6 | GraphQL API |

**Total: 6 tasks**
