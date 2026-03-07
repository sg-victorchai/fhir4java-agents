# FIX: BDD Test H2 Compatibility Issues

**Status:** In Progress
**Created:** 2026-03-07
**Priority:** High

---

## 1. Problem Summary

BDD tests are failing due to H2 database compatibility issues when running tests against PostgreSQL-specific functions. The test profile uses H2 in PostgreSQL compatibility mode, but certain PostgreSQL-specific features are not available.

### Test Results Summary
```
Tests run: 268, Failures: 60, Errors: 6, Skipped: 7
```

---

## 2. Root Cause Analysis

### 2.1 Primary Issues

| Issue | Description | Affected Tests |
|-------|-------------|----------------|
| **H2 Function Registration** | H2 function aliases (`jsonb_extract_path`, `jsonb_contains`) need proper registration timing | All search-related tests |
| **PostgreSQL JSONB Cast** | `?::jsonb` PostgreSQL-specific cast not compatible with H2 | CarePlan dedicated schema tests |
| **Missing Test Data Files** | `procedure-patch.json`, `observation-patch.json` not found | Patch operation tests |
| **Delete Status Code** | Expected 410 but got 404 for deleted resources | MedicationInventory delete tests |

### 2.2 Failure Categories

| Category | Count | Examples |
|----------|-------|----------|
| Multi-version CRUD (500 errors) | ~15 | CarePlan, Procedure via R5/R4B paths |
| Search failures (assertion errors) | ~25 | Patient search, Observation search, Custom resource search |
| Validation failures | ~8 | Profile validation, HTTP 422 errors |
| Operation failures | ~6 | $everything, $merge, $validate |
| CRUD lifecycle | ~6 | Delete returns 404 instead of 410 |

---

## 3. R4B/R5 Tagging Documentation

### 3.1 Tagging Strategy Implemented

All 32 feature files have been tagged with FHIR version tags:

| Tag | Purpose | Usage |
|-----|---------|-------|
| `@R5` | Tests for R5-only resources or R5 version paths | Feature-level or scenario-level |
| `@R4B` | Tests for R4B-specific scenarios | Scenario-level only |
| `@multi-version` | Tests covering both R5 and R4B | Feature-level for multi-version features |

### 3.2 Feature Files by Version Tag

#### R5-Only Features (30 files)
All features in these directories have `@R5` at feature level:
- `features/conformance/` - 1 file
- `features/crud/` - 2 files (standard-resource-crud.feature, custom-resource-crud.feature)
- `features/http/` - 4 files
- `features/operations/` - 6 files
- `features/plugins/` - 1 file
- `features/search/` - 7 files
- `features/tenancy/` - 2 files
- `features/validation/` - 6 files
- `features/versioning/version-resolution.feature` - 1 file

#### Multi-Version Features (2 files)
| File | R5 Scenarios | R4B Scenarios |
|------|--------------|---------------|
| `features/versioning/multi-version-resources.feature` | 6 | 4 |
| `features/crud/multi-version-resource-crud.feature` | 7 | 2 |

### 3.3 Running Version-Specific Tests

```bash
# Run only R5 tests
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT -Dcucumber.filter.tags="@R5"

# Run only R4B tests
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT -Dcucumber.filter.tags="@R4B"

# Run both versions
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT -Dcucumber.filter.tags="@R5 or @R4B"

# Exclude R4B tests (R5 only, excluding multi-version)
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT -Dcucumber.filter.tags="@R5 and not @R4B"
```

---

## 4. Files Modified

### 4.1 H2 Compatibility Fixes

| File | Change |
|------|--------|
| `fhir4java-persistence/src/main/java/org/fhirframework/persistence/h2/H2JsonFunctions.java` | Added `jsonb_extract_path()` and `jsonb_contains()` functions |
| `fhir4java-persistence/src/main/java/org/fhirframework/persistence/h2/H2FunctionInitializer.java` | Register new functions via `@PostConstruct` |
| `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/DedicatedSchemaRepository.java` | Removed `?::jsonb` PostgreSQL cast (line 54) |
| `fhir4java-server/src/main/resources/application.yml` | Simplified INIT clause - removed function registrations, kept schema creation only |

### 4.2 Application.yml Test Profile (Updated)

```yaml
# Test Profile - H2 Database
spring:
  config:
    activate:
      on-profile: test

  datasource:
    # H2 INIT: Only create schemas. Function aliases are registered by H2FunctionInitializer @PostConstruct
    url: jdbc:h2:mem:fhir4java_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS FHIR\;CREATE SCHEMA IF NOT EXISTS CAREPLAN
    driver-class-name: org.h2.Driver
```

**Key Change:** H2 function aliases are now registered by `H2FunctionInitializer.java` via `@PostConstruct` instead of in the JDBC URL's INIT clause. This ensures Java classes are loaded before function registration.

---

## 5. Implementation Steps

### Phase 1: Verify H2 Function Registration (Current)

- [x] Add `jsonb_extract_path` and `jsonb_contains` to `H2JsonFunctions.java`
- [x] Register functions in `H2FunctionInitializer.java`
- [x] Remove `?::jsonb` cast from `DedicatedSchemaRepository.java`
- [x] Simplify `application.yml` INIT clause
- [ ] Rebuild project and verify no startup errors
- [ ] Run BDD tests to verify fix

### Phase 2: Fix Remaining Test Failures

| Task | Priority | Status |
|------|----------|--------|
| Add missing test data files (`procedure-patch.json`, `observation-patch.json`) | High | Pending |
| Fix delete status code (410 vs 404) for MedicationInventory | Medium | Pending |
| Investigate search failures (assertion errors) | Medium | Pending |
| Fix validation mode tests | Low | Pending |

### Phase 3: Verify All Tests Pass

- [ ] Run full BDD test suite
- [ ] Document any remaining failures
- [ ] Update test data as needed

---

## 6. Technical Details

### 6.1 H2 Function Implementation Pattern

The `H2JsonFunctions` class provides static methods that emulate PostgreSQL JSON functions:

```java
// jsonb_extract_path - returns JSON representation at path
public static String jsonb_extract_path(String... args) {
    // args[0] = JSON string, args[1..n] = path segments
    // Returns JSON node.toString() at the path
}

// jsonb_contains - emulates @> operator
public static Boolean jsonb_contains(String container, String contained) {
    // Returns true if container JSON contains contained JSON
    // Supports arrays, objects, and primitives
}
```

### 6.2 H2FunctionInitializer Registration

Functions are registered after Spring context loads:

```java
@Configuration
@Profile("test")
public class H2FunctionInitializer {
    @PostConstruct
    void registerH2Functions() {
        stmt.execute("CREATE ALIAS IF NOT EXISTS jsonb_extract_path FOR " +
            "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_extract_path\"");
        stmt.execute("CREATE ALIAS IF NOT EXISTS jsonb_contains FOR " +
            "\"org.fhirframework.persistence.h2.H2JsonFunctions.jsonb_contains\"");
    }
}
```

### 6.3 DedicatedSchemaRepository Fix

Removed PostgreSQL-specific JSONB cast for H2 compatibility:

```java
// Before (PostgreSQL-only)
VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)

// After (H2 compatible)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
// Note: PostgreSQL auto-casts text to JSONB, H2 stores as CLOB
```

---

## 7. Verification Commands

```bash
# Step 1: Clean and compile
./mvnw clean compile -DskipTests

# Step 2: Run BDD tests
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT -Dfailsafe.useFile=false

# Step 3: Check specific test category
./mvnw verify -f fhir4java-server/pom.xml -Dtest=CucumberIT \
  -Dcucumber.filter.tags="@crud" -Dfailsafe.useFile=false
```

---

## 8. Related Documentation

- [CLAUDE.md](/CLAUDE.md) - Project context and configuration
- [TASK-bdd-test-implementation-plan.md](/tasks/TASK-bdd-test-implementation-plan.md) - BDD test structure
- [docs/DYNAMIC-SCHEMA-ROUTING.md](/docs/DYNAMIC-SCHEMA-ROUTING.md) - Dedicated schema architecture

---

## 9. Changelog

| Date | Change |
|------|--------|
| 2026-03-07 | Initial document creation with analysis of 60 failures, 6 errors |
| 2026-03-07 | Documented R4B/R5 tagging implementation across 32 feature files |
| 2026-03-07 | Updated application.yml to use H2FunctionInitializer instead of INIT clause |
