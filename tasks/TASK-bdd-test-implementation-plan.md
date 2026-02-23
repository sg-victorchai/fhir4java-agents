# BDD Test Implementation Plan

**Status:** In progress — awaiting approval to begin implementation
**Last updated:** 2026-02-23
**Branch:** `claude/fhir-api-bdd-tests-w7LEW`

---

## Complete Server Inventory

### 11 Configured Resources

| Resource | Type | Versions | Search Mode | Notable |
|---|---|---|---|---|
| Patient | Standard | R5 | Allowlist (23 params) | Full PATCH, DELETE disabled |
| Observation | Standard | R5 | Denylist (5 excluded) | |
| Condition | Standard | R5 | All allowed | |
| Encounter | Standard | R5 | All allowed | |
| CarePlan | Standard | R5 + R4B | All allowed | Multi-version |
| Procedure | Standard | R5 + R4B | All allowed | Multi-version |
| Organization | Standard | R5 | All allowed | |
| Practitioner | Standard | R5 | All allowed | |
| MedicationRequest | Standard | R5 | All allowed | |
| **MedicationInventory** | **Custom** | R5 | Allowlist (6 custom params) | DELETE enabled |
| **Course** | **Custom** | R5 | Allowlist (8 custom params) | Training domain |

### 3 Implemented Extended Operations

| Operation | Scopes | Resources | Parameters |
|---|---|---|---|
| `$validate` | TYPE + INSTANCE | All resources | `resource` (required), `profile` (optional) |
| `$merge` | TYPE | Patient only | `source-patient`, `target-patient` |
| `$everything` | TYPE + INSTANCE | Patient only | `_count`, `_since` |

**Key source files:**
- Resource configs: `fhir4java-server/src/main/resources/fhir-config/resources/*.yml`
- Operation configs: `fhir4java-server/src/main/resources/fhir-config/r5/operations/*.yml`
- Operation handlers: `fhir4java-core/.../operation/handlers/ValidateOperationHandler.java`
- Operation handlers: `fhir4java-persistence/.../operation/MergeOperationHandler.java`
- Operation handlers: `fhir4java-persistence/.../operation/EverythingOperationHandler.java`
- Operation controller: `fhir4java-api/.../controller/OperationController.java`

---

## Implementation Phases

### Phase 1 — CRUD Lifecycle (all 11 resources)

**Strategy:** Parameterized `Scenario Outline` per resource group (avoids 11 near-identical files).

```
features/crud/
  standard-resource-crud.feature     # Patient, Observation, Condition, Encounter,
                                     # Organization, Practitioner, MedicationRequest
  careplan-procedure-crud.feature    # R5 + R4B multi-version lifecycle
  custom-resource-crud.feature       # MedicationInventory (DELETE enabled), Course
```

**Scenarios per resource:**
- CREATE → 201 + `Location` header + `ETag`
- READ → 200 with correct `resourceType`
- VREAD (`/_history/{vid}`) → 200 with correct version
- UPDATE → 200/201 with `If-Match`
- PATCH (Patient, Condition, Encounter, Organization, Practitioner, MedicationRequest, MedicationInventory) → 200
- DELETE — only `MedicationInventory` → 204; all others → 405 Method Not Allowed
- READ after DELETE → 410 Gone
- READ non-existent → 404

### Phase 2 — Search (per resource, per param type)

```
features/search/
  patient-search.feature             # 23 allowlisted params
  observation-search.feature         # Denylist validation (denied params return 400)
  custom-resource-search.feature     # MedicationInventory (6 params), Course (8 params)
  search-modifiers.feature           # :exact, :contains, :missing, :not across resources
  search-pagination.feature          # _count, Bundle next/prev links
  search-post.feature                # POST /Patient/_search, POST /Observation/_search
```

**MedicationInventory search scenarios:**
```gherkin
When I search for MedicationInventory with status "active"
When I search for MedicationInventory with expiration-date "lt2026-01-01"
When I search for MedicationInventory with lot-number "LOT-12345"
When I search for MedicationInventory with supplier "Organization/supplier-1"
```

**Course search scenarios:**
```gherkin
When I search for Course with category "clinical-training"
When I search for Course with instructor "Practitioner/123"
When I search for Course with start-date "ge2026-01-01"
When I search for Course with training-provider "Organization/hosp-1"
```

**Observation denylist scenarios:**
```gherkin
When I search for Observation with "_text" parameter
Then the response should be 400 Bad Request
```

### Phase 3 — Extended Operations

```
features/operations/
  validate-operation.feature         # All resource types (extend existing)
  everything-operation.feature       # _since param, compartment resource types (extend existing)
  merge-operation.feature            # Edge cases (extend existing):
                                     #   missing source, already merged, non-Patient resource
  operation-routing.feature          # 404 for unregistered operations per resource
```

**New edge cases to add to merge-operation.feature:**
```gherkin
Scenario: Merge fails for unknown source patient
Scenario: Merge fails when target already merged
Scenario: Merge fails for non-Patient resource type
```

**New edge cases to add to everything-operation.feature:**
```gherkin
Scenario: $everything excludes resources before _since date
Scenario: $everything includes all compartment resource types
          (Condition, Observation, Encounter, MedicationRequest, Procedure, CarePlan)
```

**New validate scenarios:**
```gherkin
Scenario Outline: $validate succeeds for valid resources
  Examples: Patient, Observation, MedicationInventory, Course

Scenario: $validate rejects MedicationInventory with invalid status code
Scenario: $validate accepts optional profile parameter
```

### Phase 4 — HTTP Protocol & Headers

```
features/http/
  response-headers.feature           # ETag, Location, Content-Location
  conditional-operations.feature     # If-Match (update), If-None-Match, If-None-Exist
  content-negotiation.feature        # _format param, Accept header
  error-responses.feature            # 404, 405, 409, 410, 422 systematically
```

### Phase 5 — Multi-version (CarePlan, Procedure)

```
features/versioning/
  multi-version-resources.feature    # CarePlan and Procedure under /r4b/ and /r5/ paths
```

### Phase 6 — Multi-tenancy

```
features/tenant/
  tenant-isolation.feature           # Resources created in tenant A invisible to tenant B
  tenant-unknown.feature             # Unknown X-Tenant-ID → 403/404
```

---

## Auto-Detection of New Resources & Operations (Requirement 3)

### What to Detect

- **New resource:** a new `*.yml` added to `fhir-config/resources/` with no matching `features/crud/*.feature` scenario
- **New operation:** a new `*.yml` added to `fhir-config/r5/operations/` with no matching `features/operations/*.feature` scenario
- **New custom search param:** a new `SearchParameter-*.json` for a resource whose search feature file doesn't cover it

### Recommended Architecture: Sub-agent + SessionStart Hook

**Why sub-agent?** Gap detection requires autonomously reading many config files, diffing against
feature coverage, computing gaps, and writing structured output — exactly what sub-agents are
designed for.

**Flow:**

```
User runs: /generate-bdd-tests
         │
         ▼
  Skill (interactive prompt)
  "Full generation or gap-only?"
         │
         ▼
  Launches sub-agent: DetectAndGenerateAgent
         │
         ├─ Step 1: Scan fhir-config/resources/*.yml  →  resource inventory
         ├─ Step 2: Scan fhir-config/r5/operations/*.yml  →  operation inventory
         ├─ Step 3: Grep features/ for covered resources/operations
         ├─ Step 4: Compute gap list
         ├─ Step 5: Generate .feature files for each gap
         ├─ Step 6: Generate/update step definition Java classes
         └─ Step 7: Report: "Generated N new scenarios covering X resources, Y operations"
```

**For passive always-on detection:** `SessionStart` hook runs a fast shell script that warns if
coverage gaps exist, then user runs `/generate-bdd-tests` to fill them.

### Option Comparison

| Option | Detection? | File generation? | Automation? | Best for |
|---|---|---|---|---|
| Skill only | No (static) | Via Claude | Manual | Interactive guided flow |
| Sub-agent | **Yes** | **Yes** | Via skill/hook | Multi-step autonomous work |
| SessionStart hook | **Yes** (fast shell) | No (alerts only) | **Fully automatic** | Always-on awareness |
| **Recommended: Both** | Yes | Yes | Yes | Full coverage |

---

## Files to Create / Modify

### New Feature Files
```
features/crud/standard-resource-crud.feature
features/crud/careplan-procedure-crud.feature
features/crud/custom-resource-crud.feature
features/search/patient-search.feature
features/search/observation-search.feature
features/search/custom-resource-search.feature
features/search/search-modifiers.feature
features/search/search-pagination.feature
features/search/search-post.feature
features/operations/operation-routing.feature
features/http/response-headers.feature
features/http/conditional-operations.feature
features/http/content-negotiation.feature
features/http/error-responses.feature
features/versioning/multi-version-resources.feature
features/tenant/tenant-isolation.feature
features/tenant/tenant-unknown.feature
```

### Existing Feature Files to Extend
```
features/operations/validate-operation.feature
features/operations/everything-operation.feature
features/operations/merge-operation.feature
```

### New Step Definitions
```
fhir4java-server/src/test/java/.../bdd/steps/CrudSteps.java
fhir4java-server/src/test/java/.../bdd/steps/SearchSteps.java
fhir4java-server/src/test/java/.../bdd/steps/HttpProtocolSteps.java
fhir4java-server/src/test/java/.../bdd/steps/TenantSteps.java
```

### Auto-detection Support
```
.claude/skills/generate-bdd-tests/SKILL.md    # Rewrite to invoke sub-agent
.claude/settings.json                          # SessionStart hook config
scripts/detect-bdd-gaps.sh                    # Gap detection shell script
```

---

## Open Questions / Decisions Needed

1. **Scenario Outline vs individual scenarios:** Use `Scenario Outline` for CRUD across standard
   resources, or keep separate files per resource for readability?
2. **Step definition granularity:** One monolithic `CommonSteps.java` or per-domain step files
   (preferred above)?
3. **Test data strategy:** Inline JSON in feature files vs external fixture files in
   `src/test/resources/fixtures/`?
4. **Multi-tenancy setup:** Does the H2 test database support the `fhir_tenant` mapping table?
   Needs verification.
5. **Gap detection threshold:** Warn when 0 scenarios cover a resource, or use a minimum
   (e.g., at least 3 scenarios per resource)?

---

## Next Steps

- [ ] Review and approve phases above
- [ ] Answer open questions (or accept defaults)
- [ ] Implement Phase 1 (CRUD) — can start immediately
- [ ] Implement Phase 2 (Search) — parallel with Phase 1
- [ ] Extend Phase 3 (Operations) — extend existing feature files
- [ ] Implement Phases 4–6 (HTTP, Versioning, Tenancy)
- [ ] Build auto-detection skill + hook

**To continue:** Resume on branch `claude/fhir-api-bdd-tests-w7LEW` and reference this file at
`tasks/TASK-bdd-test-implementation-plan.md`.
