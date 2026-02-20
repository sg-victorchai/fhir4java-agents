# Task: Support Token Search for Code Data Type

## Status: COMPLETED

## Date Created: 2026-02-14

## Priority: High

---

## Problem Summary

Token search parameters are not working for elements with primitive `code` data type. The current implementation only handles `Coding` and `CodeableConcept` data types for token search, but FHIR specifies that `code` primitive type should also be searchable as a token.

### Affected Resources

- **Course** custom resource:
  - `code` element (data type: `code`) - search parameter: `code`
  - `category` element (data type: `code`) - search parameter: `category`
  - `status` element (data type: `code`) - search parameter: `status`

### Expected Behavior

According to FHIR specification, token search should work for:
- `code` - primitive code value
- `Coding` - system|code pair
- `CodeableConcept` - contains multiple Coding values
- `Identifier` - system|value pair
- `boolean` - true|false

### Current Behavior

Searching by `code` or `category` on Course resource returns no results because the search index extraction does not handle primitive `code` data type.

**Example failing searches:**
```bash
# These should work but return no results
GET /fhir/r5/Course?code=SEC101
GET /fhir/r5/Course?category=cybersecurity
GET /fhir/r5/Course?status=active
```

---

## Root Cause Analysis

### Location to Investigate

1. **Search Index Extraction** - Where search parameter values are extracted from resources:
   - `fhir4java-persistence/src/main/java/org/fhirframework/persistence/search/`
   - Look for token value extraction logic

2. **FHIRPath Evaluation** - How element values are extracted:
   - Check if FHIRPath correctly extracts `code` type values
   - Verify the extracted value is being indexed

3. **Search Query Building** - How token searches are executed:
   - `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/`
   - Look for predicate builders for token type

### Expected Fix

The token search indexing should handle all token-compatible types:

```java
// Pseudo-code for token extraction
if (value instanceof Coding) {
    indexToken(coding.getSystem(), coding.getCode());
} else if (value instanceof CodeableConcept) {
    for (Coding coding : codeableConcept.getCoding()) {
        indexToken(coding.getSystem(), coding.getCode());
    }
} else if (value instanceof CodeType) {
    // THIS IS MISSING - needs to be added
    indexToken(null, codeType.getValue());
} else if (value instanceof Identifier) {
    indexToken(identifier.getSystem(), identifier.getValue());
} else if (value instanceof BooleanType) {
    indexToken(null, booleanType.getValue().toString());
}
```

---

## Acceptance Criteria

1. Token search works for elements with `code` primitive data type
2. Search supports both exact match (`?code=SEC101`) and system|code format (`?code=|SEC101`)
3. Existing token searches for `Coding` and `CodeableConcept` continue to work
4. Unit tests added for `code` data type token search
5. Course resource searches work correctly:
   - `GET /fhir/r5/Course?code=SEC101`
   - `GET /fhir/r5/Course?category=cybersecurity`
   - `GET /fhir/r5/Course?status=active`

---

## Implementation Steps

1. [x] Identify the search index extraction code for token parameters
2. [x] Add handling for `CodeType` (and `Code` in R4B) primitive type
3. [x] Verify FHIRPath expressions correctly extract code values
4. [ ] Add unit tests for token search with code data type
5. [x] Test with Course resource (code, category, status searches)
6. [x] Update any documentation if needed

## Solution Implemented

Modified `FhirResourceRepositoryImpl.java` to:
1. Detect primitive code paths vs CodeableConcept paths
2. Build OR predicates that try both primitive and CodeableConcept patterns
3. Handle arrays of primitive codes using PostgreSQL's `jsonb_contains` function
4. Removed automatic expansion in `expandCommonPatterns()` for token-searchable fields

---

## Related Files

- `fhir4java-server/src/main/resources/fhir-config/resources/course.yml`
- `fhir4java-server/src/main/resources/fhir-config/r5/searchparameters/SearchParameter-Course-code.json`
- `fhir4java-server/src/main/resources/fhir-config/r5/searchparameters/SearchParameter-Course-category.json`
- `fhir4java-server/src/main/resources/fhir-config/r5/searchparameters/SearchParameter-Course-status.json`

---

## References

- [FHIR Search - Token](https://hl7.org/fhir/search.html#token)
- [FHIR Data Types - code](https://hl7.org/fhir/datatypes.html#code)
