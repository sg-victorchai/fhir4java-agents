# Task: Fix Custom Resource Validation for MedicationInventory

## Status: In Progress (Blocked)

## Date Created: 2026-02-08

## Problem Summary

The custom resource validation implementation has two critical issues:

### Issue 1: HAPI Validator Rejects Unknown Resource Types

When profile validation is enabled, even valid JSON payloads conforming to the StructureDefinition are rejected with:

```
Custom resource validation failed: This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory')
```

**Root Cause**: HAPI's `FhirValidator.validateWithResult(String jsonString)` internally tries to parse the JSON into an `IBaseResource` before validation. Since HAPI doesn't know about `MedicationInventory`, parsing fails before validation even starts.

### Issue 2: Invalid Elements Are Accepted When Validation Disabled

When profile validation is disabled, the system accepts and persists resources with invalid element names that don't exist in the StructureDefinition.

**Root Cause**: `CustomResourceHelper.validateBasicStructure()` only validates:
- JSON is valid
- `resourceType` field matches

It does NOT validate:
- Element names against StructureDefinition
- Cardinality constraints
- Data types
- Terminology bindings

## Files Modified (Partial Implementation)

| File | Changes Made |
|------|--------------|
| `ProfileValidator.java` | Added `validateJsonString()` method (not working for custom resources) |
| `FhirResourceService.java` | Added `createCustomResource()`, `updateCustomResource()`, custom resource helper injection |
| `FhirResourceController.java` | Added `CustomResourceHelper` injection, custom resource handling in create/search/history |

## Required Solution

### Approach: Use HAPI's SchemaValidator Directly

Instead of using `FhirValidator.validateWithResult(jsonString)` which requires parsing, we need to:

1. **Load StructureDefinition as JSON Schema** - Extract validation rules from the StructureDefinition
2. **Use JSON Schema Validation** - Validate the JSON against the extracted rules without HAPI parsing
3. **Or Use HAPI's Internal Validator Differently** - Access the underlying `FhirInstanceValidator` with a pre-registered custom resource definition

### Option A: Register Custom Resource with HAPI (Preferred)

Create a dynamic resource registration that tells HAPI about custom resources:

```java
// In CustomResourceValidationSupport.java
@Override
public StructureDefinition fetchStructureDefinition(String url) {
    // Return StructureDefinition for custom resources
    // This tells HAPI how to validate them
}

@Override
public boolean isCodeSystemSupported(ValidationSupportContext ctx, String system) {
    // Support custom code systems
}
```

The key is ensuring `CustomResourceValidationSupport` is properly integrated into the validation chain AND that HAPI can use the StructureDefinition to validate without needing a Java class.

### Option B: JSON Schema Validation Library

Use a JSON Schema validation library (e.g., `everit-org/json-schema` or `networknt/json-schema-validator`):

1. Convert StructureDefinition to JSON Schema
2. Validate incoming JSON against the schema
3. Bypass HAPI entirely for validation

### Option C: Manual Element Validation

Implement custom validation in `CustomResourceHelper`:

1. Load StructureDefinition for the resource type
2. Parse incoming JSON as a tree
3. Walk the tree and validate each element against StructureDefinition
4. Check cardinality, types, bindings

## Implementation Tasks

### Phase 1: Investigate HAPI Validation Chain
- [ ] Debug why `CustomResourceValidationSupport.fetchStructureDefinition()` isn't being called during validation
- [ ] Check if StructureDefinition is properly loaded by `CustomResourceLoader`
- [ ] Verify validation support chain order in `ProfileValidator.initializeValidatorForVersion()`

### Phase 2: Fix StructureDefinition Registration
- [ ] Ensure StructureDefinition is returned with correct URL format
- [ ] Add logging to `CustomResourceValidationSupport` to trace calls
- [ ] Test with `generateSnapshot: true` on the StructureDefinition

### Phase 3: Alternative Validation Path
- [ ] If HAPI cannot validate unknown resource types, implement JSON Schema validation
- [ ] Create `JsonSchemaValidator` utility class
- [ ] Generate/maintain JSON Schema for each custom resource StructureDefinition

### Phase 4: Testing
- [ ] Test valid MedicationInventory is accepted
- [ ] Test invalid element names are rejected
- [ ] Test cardinality violations are caught
- [ ] Test terminology binding validation

## Error Logs

```
fhir4java-server    | 2026-02-08T04:33:53.147Z DEBUG 7 --- [fhir4java-server] [nio-8080-exec-8] o.f.core.validation.ProfileValidator     : Recorded metric: fhir.validation.attempts [version=r5, result=failure, resourceType=MedicationInventory]
fhir4java-server    | 2026-02-08T04:33:53.150Z  WARN 7 --- [fhir4java-server] [nio-8080-exec-8] o.f.api.exception.FhirExceptionHandler   : FHIR exception: Custom resource validation failed: This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory')
```

## Related Files

- `fhir4java-core/src/main/java/org/fhirframework/core/validation/ProfileValidator.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceValidationSupport.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceLoader.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/resource/CustomResourceHelper.java`
- `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/FhirResourceService.java`
- `fhir4java-server/src/main/resources/fhir-config/r5/profiles/StructureDefinition-MedicationInventory.json`

## References

- HAPI FHIR Custom Resources: https://hapifhir.io/hapi-fhir/docs/model/custom_structures.html
- FHIR Validation: https://www.hl7.org/fhir/validation.html
- JSON Schema for FHIR: https://www.hl7.org/fhir/json-schema/

## Notes

- Current workaround: Disable profile validation (`fhir4java.validation.profile-validation-enabled: false`)
- This allows CRUD operations but does NOT validate custom resources against their StructureDefinition
- Invalid elements will be accepted and persisted
