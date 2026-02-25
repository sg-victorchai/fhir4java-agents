# BDD Test Implementation Plan

**Status:** Refined (v7) — profile names standardised to minimum-set / common-set / maximum-set
**Last updated:** 2026-02-25
**Branch:** `claude/fhir-api-bdd-tests-w7LEW`

---

## 1. Existing Coverage Baseline

Before adding anything, these feature files already exist and must NOT be duplicated:

| File | What it covers | Moved in Phase 1a? |
|---|---|---|
| `features/conformance/conformance-resources.feature` | StructureDefinition, SearchParameter, OperationDefinition CRUD + search | no |
| `features/operations/batch-transaction.feature` | Batch + transaction bundles | yes (was root) |
| `features/operations/everything-operation.feature` | `$everything` instance + type level, `_count` param | yes (was root) |
| `features/operations/patch-operation.feature` | JSON Patch on Patient (success + invalid patch) | yes (was root) |
| `features/operations/validate-operation.feature` | `$validate` type + instance level for Patient | yes (was root) |
| `features/plugins/plugin-patient-create.feature` | Plugin hooks on Patient CREATE (MRN, timestamp, phone normalisation) | yes (was root) |
| `features/validation/http-422-validation-errors.feature` | 422 responses from profile validation | no |
| `features/validation/profile-validation-integration.feature` | Profile validation integration | no |
| `features/validation/profile-validation-modes.feature` | strict / lenient / off modes | no |
| `features/validation/profile-validator-health.feature` | Actuator health indicator | no |
| `features/validation/profile-validator-initialization.feature` | Startup behaviour | no |
| `features/validation/profile-validator-metrics.feature` | Metrics endpoint | no |
| `features/versioning/version-resolution.feature` | Versioned (`/r5/`) + unversioned paths, case-insensitive | yes (was root) |

**Notable gaps in existing coverage:**
- No dedicated CRUD lifecycle feature (create/read/vread/update/delete) for any resource
- No search feature files at all
- No `$merge` feature file (plan previously listed it as "extend existing" — that was wrong)
- No multi-version R4B path tests for CarePlan / Procedure
- No multi-tenancy tests
- No systematic HTTP protocol / conditional header tests

---

## 2. Corrected Resource Inventory

### 2a. Interaction Matrix (source of truth from YAML configs)

| Resource | Type | Versions | PATCH | DELETE | Search mode |
|---|---|---|---|---|---|
| Patient | Standard | R5 | **yes** | no | Allowlist (25 params) |
| Observation | Standard | R5 | no | no | Denylist (3 denied common + 2 denied resource-specific) |
| Condition | Standard | R5 | **yes** | no | All allowed |
| Encounter | Standard | R5 | **yes** | no | All allowed |
| CarePlan | Standard | R5 + R4B | no | no | All allowed (dedicated schema) |
| Procedure | Standard | R5 + R4B | no | no | All allowed |
| Organization | Standard | R5 | **yes** | no | All allowed |
| Practitioner | Standard | R5 | **yes** | no | All allowed |
| MedicationRequest | Standard | R5 | **yes** | no | All allowed |
| MedicationInventory | Custom | R5 | **yes** | **yes** | Allowlist (12 params) |
| Course | Custom | R5 | no | no | Allowlist (12 params) |

**Previous plan errors corrected:**
- Course: PATCH=false (not PATCH-able)
- CarePlan: PATCH=false (not PATCH-able)
- Procedure: PATCH=false (not PATCH-able)
- Patient allowlisted params = 25 (5 common + 20 resource-specific), not 23
- MedicationInventory resource-specific params = 7 (identifier, status, medication, location,
  expiration-date, lot-number, supplier), not 6
- Course resource-specific params = 7 (code, category, title, status, instructor,
  training-provider, start-date), not 8

### 2b. PATCH-enabled resources (7)
Patient, Condition, Encounter, Organization, Practitioner, MedicationRequest, MedicationInventory

### 2c. DELETE-enabled resources (1)
MedicationInventory only

---

## 3. Test Infrastructure Notes

### Spring / RestAssured setup
- `CucumberSpringConfig` boots full Spring Boot app on random port with `@ActiveProfiles("test")`
- `@Before` sets `RestAssured.basePath = "/fhir"` for every scenario
- All step definitions use **relative paths** (e.g. `POST /Patient` resolves to `http://host/fhir/Patient`)
- `VersionResolutionSteps` overrides with `.basePath("")` for absolute paths — new versioned-path
  tests must follow the same pattern

### Test profile (H2)
- `spring.datasource.url` is H2 in PostgreSQL-compatibility mode
- `flyway.enabled=false` — schema is created by `ddl-auto: create-drop`
- This means **the `fhir_tenant` table IS created automatically** by Hibernate at test startup
- `fhir4java.tenant.enabled=false` in `application.yml` by default

  → **For tenant tests**: add `@TestPropertySource(properties = "fhir4java.tenant.enabled=true")`
  on a separate `@CucumberContextConfiguration` class, or use a Spring `@Profile("tenant-test")`.
  Simplest: add a background step `Given multi-tenancy is enabled` that verifies the header is
  processed (server behaviour), without requiring a config override if the tenant filter degrades
  gracefully when disabled.

### SharedTestContext gaps
Current fields: `lastResponse`, `lastCreatedPatientId`, `lastCreatedResourceId`, `requestBody`

New fields needed for the new test phases:

| Field | Type | Used by |
|---|---|---|
| `lastVersionId` | `String` | VREAD tests |
| `lastResourceType` | `String` | Generic CRUD steps |
| `lastEtag` | `String` | Conditional update (If-Match) — note: `patch-operation.feature` already asserts ETag is present but does NOT store it for reuse in subsequent steps; new `CrudSteps` will store it |
| `tenantId` | `String` | Tenant isolation tests |
| `secondTenantId` | `String` | Tenant isolation (resource invisible cross-tenant) |

### Step definition package
All new step definitions: `org.fhirframework.server.bdd.steps`

`CucumberIT` declares glue path `org.fhirframework.server.bdd` (the parent package), which
recursively picks up all sub-packages. The `steps` sub-package is correct and already used by
all six existing step classes.

### basePath patterns in existing step classes

`CucumberSpringConfig.@Before` sets `RestAssured.basePath = "/fhir"` globally for every scenario.

Existing classes that **override with `basePath("")`** (for absolute paths such as `/actuator/*`):
- `ValidationSteps`, `ConformanceResourceSteps`

Existing classes that **use the default `/fhir` basePath**:
- `OperationSteps`, `BatchTransactionSteps`, `PluginSteps`, `VersionResolutionSteps`

**New step classes should use the default** — no `basePath` override needed. Relative paths like
`/Patient` and `/r5/Patient/{id}` automatically resolve to `/fhir/Patient` and `/fhir/r5/Patient/{id}`.

### Existing step reuse

New feature files can reference steps already implemented in existing classes. This avoids
duplicating implementations:

| Step text (Gherkin) | Defined in |
|---|---|
| `the FHIR server is running` | `OperationSteps` |
| `a Patient resource exists` | `OperationSteps` |
| `two Patient resources exist for the merge operation` | `OperationSteps` |
| `I merge the source patient into the target patient` | `OperationSteps` |
| `I call \$merge without the required parameters` | `OperationSteps` |
| `the response status should be {int}` | `OperationSteps` (and others) |
| Source patient inactive after merge assertion | `OperationSteps` |
| Source patient replaced-by link assertion | `OperationSteps` |
| `I apply the JSON Patch to the Patient` | `OperationSteps` |
| `I have a valid Patient resource` | `OperationSteps` |
| `I request \$everything for the Patient` | `OperationSteps` |
| `I validate the Patient resource at the type level` | `OperationSteps` |
| Version header assertions (`X-FHIR-Version`) | `VersionResolutionSteps` |

---

## 4. File Structure

### Feature files root
```
fhir4java-server/src/test/resources/features/
```

### Step definition root
```
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/
```

### Structural refactoring decision

**Feature files:** Yes — relocate the 6 root-level files into subdirectories matching the phase
structure. `CucumberIT` scans `classpath:features/` recursively, so any subdirectory works
automatically. Cucumber matches steps by text, not path — moves are zero-risk.

**Step definition classes:** No — leave all 6 existing classes in place. They are coherent,
working, and `ValidationSteps.java` (1,870 lines) carries meaningful regression risk if touched.
The 4 new step classes will be added alongside them.

### Complete target feature directory layout

```
features/
  conformance/                           ← unchanged (already organised)
    conformance-resources.feature

  crud/                                  ← new (Phase 1b)
    standard-resource-crud.feature
    multi-version-resource-crud.feature
    custom-resource-crud.feature

  http/                                  ← new (Phase 4)
    response-headers.feature
    conditional-operations.feature
    content-negotiation.feature
    error-responses.feature

  operations/                            ← new subdirectory; 4 existing files MOVED here
    batch-transaction.feature            ← MOVED from root (Phase 1a)
    everything-operation.feature         ← MOVED from root (Phase 1a); extended in Phase 3
    merge-operation.feature              ← new (Phase 3)
    operation-routing.feature            ← new (Phase 3)
    patch-operation.feature              ← MOVED from root (Phase 1a)
    validate-operation.feature           ← MOVED from root (Phase 1a); extended in Phase 3

  plugins/                               ← new subdirectory; 1 existing file MOVED here
    plugin-patient-create.feature        ← MOVED from root (Phase 1a)

  search/                                ← new (Phase 2)
    patient-search.feature
    observation-search.feature
    standard-resource-search.feature
    custom-resource-search.feature
    search-modifiers.feature
    search-pagination.feature
    search-post.feature

  tenant/                                ← new (Phase 6)
    tenant-isolation.feature
    tenant-unknown.feature

  validation/                            ← unchanged (already organised)
    http-422-validation-errors.feature
    profile-validation-integration.feature
    profile-validation-modes.feature
    profile-validator-health.feature
    profile-validator-initialization.feature
    profile-validator-metrics.feature

  versioning/                            ← new subdirectory; 1 existing file MOVED here
    multi-version-resources.feature      ← new (Phase 5)
    version-resolution.feature           ← MOVED from root (Phase 1a)
```

**Summary of moves (Phase 1a):**

| Current path | New path |
|---|---|
| `features/batch-transaction.feature` | `features/operations/batch-transaction.feature` |
| `features/everything-operation.feature` | `features/operations/everything-operation.feature` |
| `features/patch-operation.feature` | `features/operations/patch-operation.feature` |
| `features/validate-operation.feature` | `features/operations/validate-operation.feature` |
| `features/plugin-patient-create.feature` | `features/plugins/plugin-patient-create.feature` |
| `features/version-resolution.feature` | `features/versioning/version-resolution.feature` |

---

## 5. Implementation Phases

### Phase 1a — Structural Refactor (file moves only)

**Goal:** Bring the 6 loose root-level feature files into the target directory structure before any
new files are added. No scenario content changes, no step definition changes. This makes the
baseline tidy and consistent with everything added in Phases 1b–7.

**Actions:**
```
git mv features/batch-transaction.feature        features/operations/batch-transaction.feature
git mv features/everything-operation.feature     features/operations/everything-operation.feature
git mv features/patch-operation.feature          features/operations/patch-operation.feature
git mv features/validate-operation.feature       features/operations/validate-operation.feature
git mv features/plugin-patient-create.feature    features/plugins/plugin-patient-create.feature
git mv features/version-resolution.feature       features/versioning/version-resolution.feature
```

**Pre-conditions before committing:**
- Create subdirectories `operations/`, `plugins/`, `versioning/` (git mv creates them)
- Run the full Cucumber suite and confirm **zero scenario failures** before pushing
- No changes to any `.java` files, any `.feature` file content, or `CucumberIT.java`

**Also update in this document:**
- Section 1 (Existing Coverage Baseline) table — update all 6 paths to their new locations
- Section 5 Phase 3 references to `features/everything-operation.feature` and
  `features/validate-operation.feature` — update to the new paths under `operations/`

---

### Phase 1b — CRUD Lifecycle

**Strategy:** Three feature files using `Scenario Outline` to parameterise across resource groups.
This avoids 11 near-identical files while keeping readability. Each `Examples` row covers one
resource type.

```
features/crud/standard-resource-crud.feature
features/crud/multi-version-resource-crud.feature   # CarePlan + Procedure
features/crud/custom-resource-crud.feature          # MedicationInventory + Course
```

**New step definition:** `CrudSteps.java`

**Scenarios per resource group:**

| Scenario | Standard | CarePlan/Procedure | MedicationInventory | Course |
|---|---|---|---|---|
| CREATE → 201 + Location + ETag | yes | yes (R5) | yes | yes |
| READ → 200 + correct resourceType | yes | yes | yes | yes |
| VREAD `/_history/{vid}` → 200 | yes | yes | yes | yes |
| UPDATE with If-Match → 200 | yes | yes | yes | yes |
| PATCH → 200 | only PATCH-enabled¹ | no | yes | no |
| PATCH on non-PATCH resource → 405 | no | yes | no | yes |
| DELETE → 204 | no | no | yes | no |
| DELETE on non-DELETE resource → 405 | yes | yes | no | yes |
| READ non-existent → 404 | yes | yes | yes | yes |
| READ after DELETE → 410 | no | no | yes | no |

¹ Standard PATCH-enabled: Condition, Encounter, Organization, Practitioner, MedicationRequest.
  Patient PATCH already covered in `patch-operation.feature` — skip in CRUD outline to avoid
  duplication.

**Note on CarePlan schema:** Uses `schema.type: dedicated` (schema name `careplan`). The H2 URL
already initialises `CREATE SCHEMA IF NOT EXISTS CAREPLAN` — tests will work without modification.

---

### Phase 2 — Search

```
features/search/patient-search.feature            # Allowlist enforcement + key params
features/search/observation-search.feature        # Denylist enforcement (denied → 400)
features/search/standard-resource-search.feature  # Smoke test for all-allowed resources
features/search/custom-resource-search.feature    # MedicationInventory + Course custom params
features/search/search-modifiers.feature          # :exact, :contains, :missing, :not
features/search/search-pagination.feature         # _count, next/prev Bundle links
features/search/search-post.feature               # POST /{Resource}/_search
```

**New step definition:** `SearchSteps.java`

**Patient search scenarios (allowlist enforcement):**
- Allowed params work: `family`, `given`, `identifier`, `birthdate`, `gender`, `active`
- Blocked param returns 400: any param NOT in the 25-param allowlist
- Multiple params (AND combination) returns filtered results

**Observation denylist scenarios:**
- `_text` param → 400
- `_content` param → 400
- `_filter` param → 400
- `combo-code-value-concept` → 400
- `combo-code-value-quantity` → 400
- Normal param (e.g., `code`, `subject`, `date`) → 200

**Standard resource search (smoke):**
```gherkin
Scenario Outline: Search <resource> with no params returns Bundle
  When I GET "/r5/<resource>"
  Then the response status should be 200
  And the response should be a Bundle
  Examples:
    | resource          |
    | Condition         |
    | Encounter         |
    | Organization      |
    | Practitioner      |
    | MedicationRequest |
    | CarePlan          |
    | Procedure         |
```

**MedicationInventory custom search scenarios:**
```gherkin
Given a MedicationInventory resource exists with status "active"
When I search for MedicationInventory with status "active"
Then the result Bundle includes the MedicationInventory resource

When I search for MedicationInventory with expiration-date "lt2027-01-01"
When I search for MedicationInventory with lot-number "LOT-12345"
When I search for MedicationInventory with supplier "Organization/supplier-1"
When I search for MedicationInventory with medication "Medication/med-001"
When I search for MedicationInventory with location "Location/loc-001"
When I search for MedicationInventory with unknown-param "x"  # → 400 (allowlist)
```

**Course custom search scenarios:**
```gherkin
When I search for Course with category "clinical-training"
When I search for Course with instructor "Practitioner/p-123"
When I search for Course with start-date "ge2026-01-01"
When I search for Course with training-provider "Organization/org-001"
When I search for Course with status "active"
When I search for Course with code "training-101"
When I search for Course with unknown-param "x"  # → 400 (allowlist)
```

**Search modifiers** (tested on Patient as the most constrained resource):
- `family:exact` → case-sensitive exact match
- `name:contains` → substring match
- `gender:missing=true` → resources without a gender
- `gender:missing=false` → resources with a gender
- `identifier:not` → exclude matching identifier

---

### Phase 3 — Extended Operations (new + extensions)

```
features/operations/merge-operation.feature       ← NEW (does not exist yet)
```

**Extend existing files** (add scenarios, don't replace):
```
features/operations/everything-operation.feature  ← add _since + compartment types  (moved in Phase 1a)
features/operations/validate-operation.feature    ← add custom resource + profile param  (moved in Phase 1a)
```

**merge-operation.feature (new — full file):**

> **Note:** All `$merge` step implementations already exist in `OperationSteps.java`.
> This feature file only needs to be created — **no new step class is required for merge**.
> Verify that each step line below exactly matches the `@Given`/`@When`/`@Then` patterns
> in `OperationSteps.java` before writing the file.

```gherkin
Feature: $merge Operation
  ...
  Background:
    Given the FHIR server is running

  Scenario: Successfully merge source patient into target patient   # happy path
  Scenario: Merge fails with missing source-patient parameter       # validation
  Scenario: Merge fails with missing target-patient parameter
  Scenario: Merge fails when source patient does not exist          # 404 source
  Scenario: After successful merge, source patient is inactive      # state verification
  Scenario: After successful merge, source patient has replaced-by link
  Scenario: $merge on non-Patient resource type returns 404         # operation not registered
```

**New everything-operation.feature scenarios:**
```gherkin
Scenario: $everything with _since excludes older resources
  Given a Patient resource exists
  And an Observation linked to that Patient was created before today
  When I request $everything with _since set to today
  Then the Bundle should not include the old Observation

Scenario: $everything includes all compartment resource types
  Given a Patient resource exists with linked Condition, Observation, and Encounter
  When I request $everything for the Patient
  Then the Bundle should contain resources of type "Condition"
  And the Bundle should contain resources of type "Observation"
  And the Bundle should contain resources of type "Encounter"
```

**New validate-operation.feature scenarios:**
```gherkin
Scenario Outline: $validate accepts valid custom resources
  Given I have a valid <resource> resource body
  When I POST it to "/<resource>/$validate"
  Then the response status should be 200
  And the OperationOutcome should indicate success
  Examples:
    | resource            |
    | MedicationInventory |
    | Course              |

Scenario: $validate with optional profile parameter is accepted
  Given I have a valid Patient resource
  When I validate with profile "http://hl7.org/fhir/StructureDefinition/Patient"
  Then the response status should be 200
```

**operation-routing.feature (new — 404 for unregistered operations):**
```
features/operations/operation-routing.feature
```

```gherkin
Scenario Outline: Unregistered operation on resource returns 404
  When I POST to "/<resource>/$unknown-op" with empty Parameters
  Then the response status should be 404
  And the response should be an OperationOutcome
  Examples:
    | resource            |
    | Patient             |
    | Observation         |
    | MedicationInventory |

Scenario: $merge on Observation returns 404
  When I POST to "/Observation/$merge" with empty Parameters
  Then the response status should be 404

Scenario: $everything on Observation returns 404
  When I GET "/Observation/$everything"
  Then the response status should be 404
```

---

### Phase 4 — HTTP Protocol & Headers

```
features/http/response-headers.feature
features/http/conditional-operations.feature
features/http/content-negotiation.feature
features/http/error-responses.feature
```

**New step definition:** `HttpProtocolSteps.java`

**response-headers.feature:**
```gherkin
Scenario: CREATE returns Location header pointing to new resource
Scenario: CREATE returns ETag header
Scenario: READ returns ETag header
Scenario: READ returns Last-Modified header
Scenario: UPDATE returns updated ETag header
Scenario: Content-Location header matches resource URL
```

**conditional-operations.feature:**
```gherkin
Scenario: UPDATE with correct If-Match ETag succeeds
Scenario: UPDATE with wrong If-Match ETag returns 409 Conflict
Scenario: UPDATE without If-Match succeeds (non-strict mode)

# SharedTestContext.lastEtag used here — read after CREATE, supply in UPDATE
```

**content-negotiation.feature:**
```gherkin
Scenario: _format=json returns application/fhir+json
Scenario: _format=xml returns application/fhir+xml
Scenario: Accept: application/fhir+json returns JSON
Scenario: Accept: application/fhir+xml returns XML
Scenario: Unsupported Accept type returns 406 Not Acceptable
```

**error-responses.feature (systematic):**

| Code | Trigger |
|---|---|
| 404 | GET non-existent resource |
| 405 | DELETE on Patient (delete=false) |
| 405 | PATCH on Observation (patch=false) |
| 409 | UPDATE with stale ETag |
| 410 | GET previously deleted MedicationInventory |
| 422 | CREATE resource failing profile validation |

---

### Phase 5 — Multi-Version (CarePlan and Procedure)

```
features/versioning/multi-version-resources.feature
```

**Scenarios:**
```gherkin
Scenario Outline: Create <resource> via R5 path
  When I POST a <resource> to "/r5/<resource>"
  Then the response status should be 201
  And the response header "X-FHIR-Version" equals "5.0.0"

Scenario Outline: Create <resource> via R4B path
  When I POST a <resource> to "/r4b/<resource>"
  Then the response status should be 201
  And the response header "X-FHIR-Version" equals "4.3.0"

Scenario Outline: Read <resource> created under R5 via R5 path succeeds
Scenario Outline: Read <resource> created under R5 via R4B path also resolves
  # (unversioned default is R5; reading via /r4b/ should still return the resource)

Examples:
  | resource |
  | CarePlan |
  | Procedure |
```

---

### Phase 6 — Multi-Tenancy

```
features/tenant/tenant-isolation.feature
features/tenant/tenant-unknown.feature
```

**New step definition:** `TenantSteps.java`

**Infrastructure note:** `fhir4java.tenant.enabled=false` in the test profile. Two approaches:

- **Option A (recommended):** Use a `@SpringBootTest` variant with
  `properties = "fhir4java.tenant.enabled=true"` in a second `CucumberContextConfiguration`
  class (e.g., `CucumberTenantSpringConfig`). Tag tenant scenarios with `@tenant` and use
  a separate Cucumber suite or tag filter. This keeps the default suite fast.

- **Option B:** Test the tenant header plumbing without enabling the feature (verify that
  the header is ignored when disabled, rather than rejected). Simpler but less meaningful.

**Recommendation: Option A.** The `fhir_tenant` table is created by Hibernate at H2 startup
(confirmed via `ddl-auto: create-drop`), so there's no migration concern.

**tenant-isolation.feature scenarios:**
```gherkin
Background:
  Given multi-tenancy is enabled
  And tenant "HOSP-A" exists with header "tenant-a-guid"
  And tenant "HOSP-B" exists with header "tenant-b-guid"

Scenario: Resource created in tenant A is not visible to tenant B
  Given I create a Patient in tenant "HOSP-A"
  When I search for all Patients as tenant "HOSP-B"
  Then the Bundle should not include the Patient from tenant "HOSP-A"

Scenario: Resource created in tenant A is visible within tenant A
  Given I create a Patient in tenant "HOSP-A"
  When I read the Patient as tenant "HOSP-A"
  Then the response status should be 200

Scenario: Resource created without tenant header uses default tenant
  Given I create a Patient without an X-Tenant-ID header
  When I read the Patient without an X-Tenant-ID header
  Then the response status should be 200
```

**tenant-unknown.feature scenarios:**
```gherkin
Scenario: Unknown tenant header returns 403
  When I GET "/Patient" with X-Tenant-ID "unknown-guid-not-in-table"
  Then the response status should be 403
  And the response should be an OperationOutcome

Scenario: Disabled tenant returns 403
  Given tenant "DISABLED-TENANT" exists but is disabled
  When I GET "/Patient" with X-Tenant-ID for "DISABLED-TENANT"
  Then the response status should be 403
```

---

## 6. SharedTestContext — Required Extensions

Add these fields to the existing `SharedTestContext.java`:

```java
private String lastVersionId;      // for VREAD /_history/{vid} tests
private String lastResourceType;   // for generic CRUD steps
private String lastEtag;           // for conditional update tests
private String tenantId;           // for tenant isolation tests
private String secondTenantId;     // for cross-tenant visibility tests
```

Also needed:
- `extractVersionId(String responseBody)` utility — parses `meta.versionId` from JSON response body
  (analogous to existing `extractResourceId`, which parses `id`)
- `extractEtag(Response response)` helper — reads the `ETag` response header and strips surrounding
  `W/"..."` quotes, storing the raw version number

`extractResourceId` already exists and works for all resource types — **do not duplicate it**.

---

## 7. Files to Create

### New feature files (17 new files)
```
fhir4java-server/src/test/resources/features/crud/standard-resource-crud.feature
fhir4java-server/src/test/resources/features/crud/multi-version-resource-crud.feature
fhir4java-server/src/test/resources/features/crud/custom-resource-crud.feature
fhir4java-server/src/test/resources/features/search/patient-search.feature
fhir4java-server/src/test/resources/features/search/observation-search.feature
fhir4java-server/src/test/resources/features/search/standard-resource-search.feature
fhir4java-server/src/test/resources/features/search/custom-resource-search.feature
fhir4java-server/src/test/resources/features/search/search-modifiers.feature
fhir4java-server/src/test/resources/features/search/search-pagination.feature
fhir4java-server/src/test/resources/features/search/search-post.feature
fhir4java-server/src/test/resources/features/operations/merge-operation.feature
fhir4java-server/src/test/resources/features/operations/operation-routing.feature
fhir4java-server/src/test/resources/features/http/response-headers.feature
fhir4java-server/src/test/resources/features/http/conditional-operations.feature
fhir4java-server/src/test/resources/features/http/content-negotiation.feature
fhir4java-server/src/test/resources/features/http/error-responses.feature
fhir4java-server/src/test/resources/features/versioning/multi-version-resources.feature
fhir4java-server/src/test/resources/features/tenant/tenant-isolation.feature
fhir4java-server/src/test/resources/features/tenant/tenant-unknown.feature
```

### Existing feature files to extend (add scenarios only)
```
fhir4java-server/src/test/resources/features/operations/everything-operation.feature  (post Phase 1a path)
fhir4java-server/src/test/resources/features/operations/validate-operation.feature    (post Phase 1a path)
```

### New step definition classes
```
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/CrudSteps.java
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/SearchSteps.java
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/HttpProtocolSteps.java
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/TenantSteps.java
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/TestDataLoader.java  ← see §9 Q3
```

> **Not needed (steps already exist):** No new step class for `merge-operation.feature` — all
> merge step methods are already in `OperationSteps.java`. Similarly, `operation-routing.feature`
> can reuse generic HTTP steps from `OperationSteps` and `CrudSteps`.

### Modify existing
```
fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/SharedTestContext.java
  → add lastVersionId, lastResourceType, lastEtag, tenantId, secondTenantId
  → add extractVersionId() helper
```

### New test data files (external JSON — see §9 Q3)

File naming follows the variant conventions in §9 Q3.
Two filename patterns are used:
- `{resource}.json` — baseline payload used by the primary cross-resource-type Scenario Outline
- `{resource}-{profile}.json` — named variant used by per-resource data-variant Scenario Outline (Pattern 4)

```
fhir4java-server/src/test/resources/testdata/
  crud/
    patient.json                ← baseline (primary CRUD outline)
    patient-minimum-set.json    ← Profile variant: mandatory fields only
    patient-common-set.json     ← Profile variant: typical real-world payload
    patient-maximum-set.json    ← Profile variant: all supported fields populated
    patient-update.json         ← Pattern 1: update body (PUT)
    patient-patch.json          ← Pattern 1: patch document (RFC 6902)
    patient-invalid.json        ← Pattern 1: invalid body (422 tests)
    observation.json
    observation-minimum-set.json
    observation-common-set.json
    observation-maximum-set.json
    observation-update.json
    condition.json
    condition-update.json
    encounter.json
    encounter-update.json
    organization.json
    organization-update.json
    practitioner.json
    practitioner-update.json
    medicationrequest.json
    medicationrequest-update.json
    medicationrequest-patch.json
    procedure.json
    procedure-update.json
    careplan.json               ← multi-version group (R4B + R5)
    careplan-update.json
    medicationinventory.json    ← custom group; DELETE-enabled
    medicationinventory-update.json
    course.json
    course-update.json
  operations/
    merge-parameters.json       ← Pattern 2: FHIR Parameters (source + target Patient inline)
    validate-patient.json       ← Pattern 1: $validate input body
    validate-patient-profile.json
  bundle/
    batch-create.json           ← Pattern 2: FHIR Bundle (batch type)
    transaction-create.json     ← Pattern 2: FHIR Bundle (transaction type)
  setup/
    patient.json                ← Pattern 3 (loadAll seed): referenced alongside
    observation.json            ←   observation.json and condition.json for $everything setup
    condition.json              ←   (separate files, one path per loadAll() argument)
  tenant/
    tenant-a-patient.json       ← Pattern 1: POST to tenant A
    tenant-b-patient.json       ← Pattern 1: POST to tenant B
```

> Profile variants are added incrementally — start with only the variants needed for Phase 1b
> scenarios. Add more as new scenario rows are added to the Examples table.

### Auto-detection support (Phase 7)
```
.claude/skills/generate-bdd-tests/SKILL.md
scripts/detect-bdd-gaps.sh
```

---

## 8. Auto-Detection Architecture

### What counts as a coverage gap
1. A `fhir-config/resources/*.yml` with `enabled: true` and no scenario in
   `features/crud/` that creates a resource of that `resourceType`
2. A `fhir-config/r5/operations/*.yml` with no scenario in `features/operations/`
   that posts to `/{resource}/$<operation-name>`
3. A resource-specific search param in a YAML config with no scenario in
   `features/search/` that uses that param name

### Detection script: `scripts/detect-bdd-gaps.sh`

```bash
#!/usr/bin/env bash
# Scans resource configs and feature files, prints gap report.
# Exit code 1 if gaps found, 0 if fully covered.
RESOURCES_DIR="fhir4java-server/src/main/resources/fhir-config/resources"
FEATURES_DIR="fhir4java-server/src/test/resources/features"
gaps=0
for yml in "$RESOURCES_DIR"/*.yml; do
  rt=$(grep "^resourceType:" "$yml" | awk '{print $2}')
  enabled=$(grep "^enabled:" "$yml" | awk '{print $2}')
  [[ "$enabled" != "true" ]] && continue
  if ! grep -rl "\"$rt\"\|/$rt\b\|resourceType.*$rt" "$FEATURES_DIR" --include="*.feature" -q; then
    echo "GAP: No feature scenarios found for resource: $rt"
    gaps=$((gaps + 1))
  fi
done
exit $((gaps > 0))
```

### Skill: `/generate-bdd-tests`

```
.claude/skills/generate-bdd-tests/SKILL.md
```

Flow when invoked:
1. Run `scripts/detect-bdd-gaps.sh` to get gap list
2. For each gap: generate a minimal `.feature` file and stub step definitions
3. Report: "Generated N files covering X new resources / Y new operations"

---

## 9. Resolved Open Questions

| # | Question | Decision |
|---|---|---|
| 1 | Scenario Outline vs individual? | **Scenario Outline per group** — keeps files manageable |
| 2 | Step definition granularity? | **Per-domain files** — CrudSteps, SearchSteps, HttpProtocolSteps, TenantSteps |
| 3 | Test data strategy? | **External JSON files** under `src/test/resources/testdata/` loaded by `TestDataLoader` — see detail below |
| 4 | H2 supports fhir_tenant table? | **Yes** — Hibernate creates it via `ddl-auto: create-drop` |
| 5 | Gap detection threshold? | **0 scenarios = gap** for CRUD; 0 param scenarios = gap for custom search params |

### Q3 — Test data strategy (detail)

**Original approach:** embed JSON strings inline inside step class methods.

**Problem:** FHIR resource bodies are 20–40 lines each. With 11 resource types and multiple
operation variants, step classes accumulate hundreds of lines of JSON string literals that:
- Require a Java recompile for any data change (a clinical analyst or QA engineer editing
  a test payload must rebuild the project)
- Cannot be validated by a FHIR validator or JSON schema tooling in the IDE
- Cannot be reviewed by non-Java stakeholders without opening a Java file
- Break the "one step class, many resource types" Scenario Outline pattern — the step class
  must fork on resource type in code rather than simply loading the right file

**Decision: external JSON files + `TestDataLoader` utility**

Resource bodies live in `src/test/resources/testdata/` (see §7). A single, thin utility class
loads them by relative path:

```java
// TestDataLoader.java
public class TestDataLoader {

    /** Load a single test data file as a String. */
    public static String load(String relativePath) throws IOException {
        try (InputStream is = TestDataLoader.class.getClassLoader()
                .getResourceAsStream("testdata/" + relativePath)) {
            if (is == null) {
                throw new IllegalArgumentException(
                    "Test data not found: testdata/" + relativePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Load multiple test data files, returning them in the same order. */
    public static List<String> loadAll(String... relativePaths) throws IOException {
        List<String> results = new ArrayList<>(relativePaths.length);
        for (String path : relativePaths) {
            results.add(load(path));
        }
        return results;
    }
}
```

**Multi-file patterns — when a step needs more than one JSON file:**

A single step may legitimately need multiple files. There are three distinct patterns; each has
a different resolution:

**Pattern 1 — Sequential loads (step makes multiple HTTP calls)**

The step simply calls `load()` more than once. No API change needed; the step class is in
control of what it does with each body.

```java
// CRUD update test: POST with create body, then PUT with update body
String createBody = TestDataLoader.load("crud/" + resourceType.toLowerCase() + ".json");
String updateBody = TestDataLoader.load("crud/" + resourceType.toLowerCase() + "-update.json");
```

Applies to: CRUD update, PATCH (resource body + patch document), tenant isolation
(one POST per tenant).

**Pattern 2 — Composite single request (one HTTP call, body built from multiple logical parts)**

Do NOT merge files in Java. Instead create a dedicated composite file that *is* the full
request payload — typically a FHIR `Parameters` resource or a `Bundle`.

```
testdata/operations/merge-parameters.json   ← valid FHIR Parameters containing source + target
testdata/bundle/batch-create.json           ← valid FHIR Bundle with multiple entries
```

The step loads one file and POSTs it:
```java
String body = TestDataLoader.load("operations/merge-parameters.json");
```

Applies to: `$merge` (source + target), batch/transaction bundles, multi-part operation inputs.

**Pattern 3 — Setup/teardown data (background data for a later assertion)**

Use `loadAll()` to pre-populate resources before the scenario's actual step runs. Typically
called in a `@Before` hook or a `Background:` step.

```java
// Before $everything test: seed Patient + Observation + Condition
List<String> bodies = TestDataLoader.loadAll(
    "crud/patient.json",
    "crud/observation.json",
    "crud/condition.json"
);
bodies.forEach(body -> given().body(body).post("/fhir/r5/..."));
```

Applies to: `$everything`, search result count assertions, any scenario that requires
pre-existing linked resources.

**Pattern 4 — Data variants for the same resource type (multiple payloads, same operation)**

When the same operation (e.g., patient create) needs to be exercised with several different
payloads — minimum-set, common-set, maximum-set — the number of variants grows with
the number of scenarios being tested and must not require Java changes to add.

Three approaches were considered:

| Option | Gherkin shape | File path in feature file? | Large JSON in feature file? |
|---|---|---|---|
| A — profile label (recommended) | `"<profile>" patient` | no | no |
| B — explicit file path column | `"<dataFile>"` column | yes | no |
| C — DocString | inline `"""json ... """` | n/a | yes |

**Option A is recommended.** The label is readable to non-developers ("minimum-set patient",
"maximum-set patient"). The file path is hidden — implementers just follow the naming
convention. Option B leaks directory structure into Gherkin. Option C puts large JSON back in
the feature file, which was the original problem.

Feature file using Option A:
```gherkin
Scenario Outline: Patient create succeeds for various data profiles
  When I create a "<profile>" patient
  Then the response status is 201
  And the response contains resourceType "Patient"

Examples:
  | profile      | notes                                          |
  | minimum-set  | mandatory fields only (name + gender)          |
  | common-set   | typical real-world payload (+ identifier, DOB) |
  | maximum-set  | all supported fields populated                 |
```

Step class — single method, no branching:
```java
@When("I create a {string} patient")
public void createPatientVariant(String profile) throws IOException {
    String body = TestDataLoader.load("crud/patient-" + profile + ".json");
    // POST to /fhir/r5/Patient, store response in SharedTestContext
}
```

**Adding a new data variant** = one new `Examples` row + one new JSON file. Zero Java changes.

**Combined with `<resourceType>` column** — the same step can drive variant testing across
multiple resource types simultaneously:

```gherkin
Scenario Outline: Resource create - data variants
  When I create a "<profile>" <resourceType>
  Then the response status is 201

Examples:
  | resourceType | profile      | notes                                          |
  | patient      | minimum-set  | mandatory fields only                          |
  | patient      | common-set   | typical real-world payload                     |
  | patient      | maximum-set  | all supported fields populated                 |
  | observation  | minimum-set  | code + subject only                            |
  | observation  | common-set   | + valueQuantity and effectiveDateTime          |
  | observation  | maximum-set  | all supported fields populated                 |
```

Step class:
```java
@When("I create a {string} {word}")
public void createResourceVariant(String profile, String resourceType) throws IOException {
    String body = TestDataLoader.load(
        "crud/" + resourceType.toLowerCase() + "-" + profile + ".json");
}
```

File convention: `testdata/crud/{resourceType}-{profile}.json`

**Naming conventions for file variants:**

| Purpose | File name pattern | Example |
|---|---|---|
| Default / baseline create | `{resource}.json` | `patient.json` |
| Named data variant | `{resource}-{profile}.json` | `patient-minimum-set.json`, `patient-common-set.json`, `patient-maximum-set.json` |
| Update body | `{resource}-update.json` | `patient-update.json` |
| Patch document (RFC 6902) | `{resource}-patch.json` | `patient-patch.json` |
| Invalid body (422 tests) | `{resource}-invalid.json` | `patient-invalid.json` |
| Composite/multi-part | `{operation}-parameters.json` | `merge-parameters.json` |

The `{resource}.json` baseline (no profile suffix) is used by the primary CRUD Scenario Outline
that parameterises across resource types. The `{resource}-{profile}.json` variants are used by
scenario-specific outlines within the same resource type.

**Convention — primary CRUD Scenario Outline (across resource types):**
```gherkin
Examples:
  | resourceType |
  | Patient      |   → testdata/crud/patient.json
  | Observation  |   → testdata/crud/observation.json
```
Step: `TestDataLoader.load("crud/" + resourceType.toLowerCase() + ".json")`

**Convention — data variant Scenario Outline (within one resource type):**
```gherkin
Examples:
  | profile      |
  | minimum-set  |   → testdata/crud/patient-minimum-set.json
  | common-set   |   → testdata/crud/patient-common-set.json
  | maximum-set  |   → testdata/crud/patient-maximum-set.json
```
Step: `TestDataLoader.load("crud/patient-" + profile + ".json")`

No switch/if-else in Java. Adding a new resource type or variant means adding one JSON file —
no Java change, no recompile.

**Rule — when inline is still acceptable:**
| Use inline | Use external file |
|---|---|
| Short scalar values (`"active"`, `"true"`, `"Smith"`) | Any request body (FHIR resource JSON) |
| URL query parameter strings (`?family=Smith&gender=male`) | Operation input payloads (`$validate`, `$merge`, `$everything` params) |
| Response field assertions (`"resourceType": "Patient"`) | Tenant fixture payloads |
| Error message fragments | Anything > ~5 tokens or subject to change independently |

---

## 10. Implementation Order

```
Phase 1a (Refactor)    ← first; git mv 6 feature files; run suite; confirm 0 failures; commit
Phase 1b (CRUD)        ← creates CrudSteps + SharedTestContext extensions
Phase 2 (Search)       ← can run in parallel with Phase 1b; creates SearchSteps
Phase 3 (Operations)   ← create merge-operation.feature (no new step class!);
                          extend operations/everything-operation.feature + operations/validate-operation.feature;
                          create operation-routing.feature
Phase 4 (HTTP)         ← depends on SharedTestContext.lastEtag from Phase 1b; creates HttpProtocolSteps
Phase 5 (Versioning)   ← standalone; no new step class (reuse VersionResolutionSteps patterns)
Phase 6 (Tenancy)      ← needs CucumberTenantSpringConfig; creates TenantSteps; do last
Phase 7 (Auto-detect)  ← purely scripting; can do any time after Phase 1b
```

**Step class summary:**
| Phase | New step class | Notes |
|---|---|---|
| 1a (Refactor) | *none* | file moves only |
| 1b (CRUD) | `CrudSteps.java` | |
| 2 (Search) | `SearchSteps.java` | |
| 3 (Operations) | *none* | reuses `OperationSteps.java` |
| 4 (HTTP) | `HttpProtocolSteps.java` | |
| 5 (Versioning) | *none* | reuses `VersionResolutionSteps.java` |
| 6 (Tenancy) | `TenantSteps.java` | |
| 7 (Auto-detect) | *none* | shell script + skill |

**To continue:** Check out branch `claude/fhir-api-bdd-tests-w7LEW` and start with Phase 1a
(structural refactor — git mv 6 files, run suite, commit). Then proceed to Phase 1b (CRUD).
Reference this file at `tasks/TASK-bdd-test-implementation-plan.md`.
