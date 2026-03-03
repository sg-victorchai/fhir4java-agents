# Generate BDD Tests

Automatically detect BDD test coverage gaps and generate feature files and step definitions to fill them.

## Overview

This skill implements the auto-detection architecture from the BDD test implementation plan (Section 8). When invoked, it:

1. **Runs** `.claude/skills/generate-bdd-tests/detect-bdd-gaps.sh` to identify untested resources, operations, and search parameters
2. **Generates** minimal `.feature` files and stub step definitions for each gap
3. **Reports** what was generated: "Generated N files covering X new resources / Y new operations"

## What Counts as a Coverage Gap

1. A `fhir-config/resources/*.yml` with `enabled: true` and **no scenario** in `features/crud/` that creates a resource of that `resourceType`
2. A `fhir-config/r5/operations/*.yml` with **no scenario** in `features/operations/` that posts to `/{resource}/$<operation-name>`
3. A resource-specific search param in a YAML config with **no scenario** in `features/search/` that uses that param name

## Instructions

When this skill is invoked, follow these steps **automatically** (do not prompt the user interactively):

### Step 1: Run Gap Detection

```bash
./.claude/skills/generate-bdd-tests/detect-bdd-gaps.sh --verbose 2>&1
```

Parse the output to identify:
- Resources missing CRUD coverage (Section 1 output — look for `✗` marks)
- Search parameters without test scenarios (Section 2 output)
- Operations without test scenarios (Section 3 output)
- Missing test data files (Section 5 output)

### Step 2: Generate Feature Files for Gaps

For each gap found, generate files following the established patterns.

#### CRUD Gaps → Add to Scenario Outline Examples

If a resource type is missing from `features/crud/standard-resource-crud.feature` (or `custom-resource-crud.feature` / `multi-version-resource-crud.feature`), add it to the appropriate Scenario Outline Examples table.

Check which feature file the resource belongs in:
- **Standard resources** (R5-only, no custom StructureDefinition): `standard-resource-crud.feature`
- **Multi-version resources** (both R5 + R4B): `multi-version-resource-crud.feature`
- **Custom resources** (has generated class from codegen): `custom-resource-crud.feature`

Also create the corresponding test data files if missing:
```
testdata/crud/{resource}.json              # baseline
testdata/crud/{resource}-update.json       # update body
testdata/crud/{resource}-patch.json        # JSON Patch (if patch enabled)
testdata/crud/{resource}-minimum-set.json  # mandatory fields only
testdata/crud/{resource}-common-set.json   # typical real-world payload
testdata/crud/{resource}-maximum-set.json  # all supported fields
```

#### Search Gaps → Add to search feature files

For missing search parameters, add scenarios to the appropriate search feature file or create a new one:
- Patient params → `features/search/patient-search.feature`
- Observation params → `features/search/observation-search.feature`
- Custom resource params → `features/search/custom-resource-search.feature`
- New resources → `features/search/{resource}-search.feature`

Use this pattern:
```gherkin
Scenario: Search {ResourceType} by {param-name}
  When I search for {ResourceType} with parameter "{param-name}" value "{test-value}"
  Then the response status should be 200
  And the response should be a search Bundle
```

#### Operation Gaps → Create operation feature file

```gherkin
@operations
Feature: ${operation-name} Operation
  As a FHIR client
  I want to invoke the ${operation-name} operation
  So that I can {purpose}

  Background:
    Given the FHIR server is running

  Scenario: ${operation-name} at type level with valid parameters
    Given I have valid parameters for ${operation-name}
    When I invoke ${operation-name} on {ResourceType}
    Then the response status should be 200

  Scenario: ${operation-name} without required parameters returns error
    When I invoke ${operation-name} without parameters on {ResourceType}
    Then the response status should be 400
    And the response should be an OperationOutcome
```

### Step 3: Generate Step Definitions (if needed)

Only create new step definition classes when the generated scenarios use step text not covered by existing step classes:

| Existing Class | Steps Provided |
|---|---|
| `CrudSteps.java` | `a {word} resource exists`, `I create a {word} resource`, `I read the {word} resource by its ID`, `I update the {word} resource`, `I patch the {word} resource`, `I delete the {word} resource`, versioned CRUD |
| `SearchSteps.java` | `I search for {word} with parameter {string} value {string}`, `I search for {word} by _id`, `I search for {word} resources`, POST search, pagination |
| `OperationSteps.java` | `the FHIR server is running`, validate, everything, merge, patch operations, OperationOutcome assertions |
| `HttpProtocolSteps.java` | Content negotiation, conditional operations, Content-Type/Location/ETag assertions |

If new steps are needed, add them to the **most relevant existing class** rather than creating a new one.

### Step 4: Report

After generating all files, output a summary:

```
=== BDD Test Generation Report ===

Generated:
- N feature file(s) created/updated
- M test data file(s) created
- P step definition(s) added

Coverage:
- X new resource types covered
- Y new search parameters covered
- Z new operations covered

Files modified:
- features/crud/standard-resource-crud.feature (added N examples)
- features/search/patient-search.feature (added M scenarios)
- testdata/crud/newresource.json (created)
- ...
```

## Project Structure Reference

```
fhir4java-server/src/test/
├── java/org/fhirframework/server/bdd/
│   ├── CucumberIT.java             # Test runner (@SelectClasspathResource("features"))
│   ├── CucumberSpringConfig.java   # Spring config (basePath="/fhir")
│   └── steps/
│       ├── SharedTestContext.java   # Scenario-scoped shared state
│       ├── TestDataLoader.java     # Loads JSON from testdata/
│       ├── CrudSteps.java          # CRUD lifecycle steps
│       ├── SearchSteps.java        # Search steps
│       ├── OperationSteps.java     # Extended operation steps
│       ├── HttpProtocolSteps.java  # HTTP protocol steps
│       ├── VersionResolutionSteps.java     # Version path resolution
│       ├── PluginSteps.java                # Plugin hook steps
│       ├── ConformanceResourceSteps.java   # Conformance resource steps
│       ├── MultiTenancySteps.java          # Multi-tenancy steps
│       └── TenantManagementSteps.java      # Tenant management steps
└── resources/
    ├── features/
    │   ├── crud/           # CRUD lifecycle tests
    │   ├── search/         # Search parameter tests
    │   ├── operations/     # Extended operation tests
    │   ├── plugins/        # Plugin behavior tests
    │   ├── versioning/     # Version resolution tests
    │   ├── http/           # HTTP protocol tests
    │   ├── validation/     # Profile validation tests
    │   ├── conformance/    # Conformance resource tests
    │   └── tenancy/        # Multi-tenancy tests
    └── testdata/
        └── crud/           # JSON test data (62 files)
```

## Resource Configuration Reference

Resource configs at `fhir4java-server/src/main/resources/fhir-config/resources/*.yml`:

```yaml
resourceType: Patient
enabled: true
fhirVersions:
  - version: R5
    default: true
  - version: R4B       # optional
interactions:
  read: true
  create: true
  update: true
  search: true
  patch: true/false     # not all resources
  delete: true/false    # not all resources
searchParameters:
  mode: allowlist       # or denylist
  common: [_id, _lastUpdated]
  resourceSpecific: [identifier, family, given, ...]
```

## Running Tests After Generation

```bash
# Run all BDD tests
./mvnw test -pl fhir4java-server -Dtest=CucumberIT

# Run specific tag
./mvnw test -pl fhir4java-server -Dtest=CucumberIT -Dcucumber.filter.tags="@crud"

# Re-run gap detection to verify coverage
./.claude/skills/generate-bdd-tests/detect-bdd-gaps.sh
```
