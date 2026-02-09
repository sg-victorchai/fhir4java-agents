# Task: Fix Custom Resource Validation for MedicationInventory

## Status: In Progress (Blocked)

## Date Created: 2026-02-08
## Last Updated: 2026-02-09

---

## Problem Summary

The custom resource validation implementation has two critical issues discovered after code generation is working:

### Issue 1: Backbone Elements Ignored (Validation Disabled)

When profile validation is disabled, the FHIR server ignores backbone elements and persists only root-level elements. Nested elements under backbone elements like `packaging.type`, `packaging.unitsPerPackage` are not recognized.

**Error Log:**
```
fhir4java-server    | 2026-02-09T13:53:51.848Z DEBUG 7 --- [nio-8080-exec-8] o.f.a.controller.FhirResourceController  : CREATE MedicationInventory (version=R5)
fhir4java-server    | 2026-02-09T13:53:51.849Z  WARN 7 --- [nio-8080-exec-8] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'type' found while parsing
fhir4java-server    | 2026-02-09T13:53:51.849Z  WARN 7 --- [nio-8080-exec-8] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'unitsPerPackage' found while parsing
fhir4java-server    | 2026-02-09T13:53:51.849Z  WARN 7 --- [nio-8080-exec-8] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'packageCount' found while parsing
fhir4java-server    | 2026-02-09T13:53:51.849Z  WARN 7 --- [nio-8080-exec-8] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'expiration2Date' found while parsing
fhir4java-server    | 2026-02-09T13:53:51.860Z  WARN 7 --- [nio-8080-exec-8] o.f.core.validation.ProfileValidator     : Profile validation is disabled - returning success without validation
```

**Root Cause**: The `JavaClassBuilder` in `fhir4java-codegen` only generates fields for direct children of the resource (path depth = 2). It does NOT generate:
- Backbone element inner classes (e.g., `MedicationInventory.Packaging`)
- Fields for nested elements under backbone elements (e.g., `packaging.type`, `packaging.unitsPerPackage`)

**Current Generated Code Issue**:
```java
// Current: packaging is just List<StringType> - wrong!
@Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
private List<StringType> packaging;

// Should be: packaging is a backbone element with its own class
@Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
private List<PackagingComponent> packaging;

@Block
public static class PackagingComponent extends BackboneElement {
    @Child(name = "type")
    private CodeableConcept type;

    @Child(name = "unitsPerPackage")
    private IntegerType unitsPerPackage;

    @Child(name = "packageCount")
    private IntegerType packageCount;
    // ...
}
```

### Issue 2: Profile Validation Fails (Validation Enabled)

When profile validation is enabled, the HAPI validator cannot process the custom resource because it doesn't recognize the resource type.

**Error Log:**
```
fhir4java-server    | 2026-02-09T14:05:49.220Z  WARN 7 --- [nio-8080-exec-7] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'type' found while parsing
fhir4java-server    | 2026-02-09T14:05:49.220Z  WARN 7 --- [nio-8080-exec-7] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'unitsPerPackage' found while parsing
fhir4java-server    | 2026-02-09T14:05:49.220Z  WARN 7 --- [nio-8080-exec-7] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'packageCount' found while parsing
fhir4java-server    | 2026-02-09T14:05:49.220Z  WARN 7 --- [nio-8080-exec-7] ca.uhn.fhir.parser.LenientErrorHandler   : Unknown element 'expiration2Date' found while parsing
fhir4java-server    | 2026-02-09T14:05:49.433Z DEBUG 7 --- [nio-8080-exec-7] o.f.core.validation.ProfileValidator     : Recorded metric: fhir.validation.attempts [version=r5, result=failure, resourceType=MedicationInventory]
fhir4java-server    | 2026-02-09T14:05:49.443Z DEBUG 7 --- [nio-8080-exec-7] .m.m.a.ExceptionHandlerExceptionResolver : Using @ExceptionHandler org.fhirframework.api.exception.FhirExceptionHandler#handleFhirException(FhirException, HttpServletRequest)
fhir4java-server    | 2026-02-09T14:05:49.444Z  WARN 7 --- [nio-8080-exec-7] o.f.api.exception.FhirExceptionHandler   : FHIR exception: Resource validation failed: This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory'); This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory')
fhir4java-server    | 2026-02-09T14:05:49.467Z DEBUG 7 --- [nio-8080-exec-7] .m.m.a.ExceptionHandlerExceptionResolver : Resolved [org.fhirframework.core.exception.FhirException: Resource validation failed: This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory'); This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory')]
```

**Root Cause**: HAPI's `FhirValidator.validateWithResult()` internally tries to parse the JSON into an `IBaseResource` before validation. The validator's parsing chain does not recognize the custom `MedicationInventory` resource type even though:
1. The generated class exists and is compiled
2. `CustomResourceRegistry` registers it with the FhirContext
3. `CustomResourceValidationSupport` provides the StructureDefinition

The issue is that the HAPI validator uses a separate context/parsing mechanism that isn't aware of the registered custom resources.

---

## Required Solutions

### TODO 1: Generate Backbone Element Classes

**Location**: `fhir4java-codegen/src/main/java/org/fhirframework/codegen/JavaClassBuilder.java`

**Changes Required**:

1. Identify backbone elements in the StructureDefinition (elements with path depth > 2 and `type[0].code == "BackboneElement"`)

2. Generate inner `@Block` classes for each backbone element:
   ```java
   @Block
   public static class PackagingComponent extends BackboneElement {
       // fields for nested elements

       @Override
       public PackagingComponent copy() {
           // copy implementation
       }
   }
   ```

3. Use the backbone component type for the parent field instead of `StringType`:
   ```java
   @Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
   private List<PackagingComponent> packaging;
   ```

4. Process nested elements (path depth > 2) and add them to the correct backbone class

**Reference Implementation**: Look at how HAPI generates classes like `Patient.ContactComponent` or `Observation.ComponentComponent`

### TODO 2: Fix Profile Validation for Custom Resources

**Location**: `fhir4java-core/src/main/java/org/fhirframework/core/validation/ProfileValidator.java`

**Possible Approaches**:

#### Option A: Pre-register Custom Resources with Validator Context

Ensure the HAPI validator's internal context knows about custom resources before validation:

```java
// In ProfileValidator or CustomResourceValidationSupport
FhirInstanceValidator validator = new FhirInstanceValidator(validationSupport);
// Ensure validationSupport chain includes CustomResourceValidationSupport
// with proper fetchStructureDefinition() implementation
```

#### Option B: Use Pre-parsed Resource for Validation

Instead of validating JSON string, parse it first with the custom-resource-aware context, then validate:

```java
// Parse with context that knows about MedicationInventory
IBaseResource resource = fhirContext.newJsonParser().parseResource(jsonString);
// Then validate the parsed resource
ValidationResult result = validator.validateWithResult(resource);
```

#### Option C: Bypass HAPI Validation for Custom Resources

Implement custom JSON Schema validation that doesn't rely on HAPI parsing:

1. Convert StructureDefinition to JSON Schema
2. Use a JSON Schema validation library (e.g., `networknt/json-schema-validator`)
3. Validate JSON directly without HAPI parsing

---

## Implementation Tasks

### Phase 1: Backbone Element Generation
- [ ] Identify backbone elements in StructureDefinitionParser
- [ ] Add `isBackboneElement()` check based on element type
- [ ] Generate inner `@Block` static classes for backbone elements
- [ ] Generate fields within backbone classes for nested elements
- [ ] Update parent field types to use backbone component classes
- [ ] Add `copy()` method to backbone classes
- [ ] Test parsing with backbone elements

### Phase 2: Profile Validation Fix
- [ ] Debug why `CustomResourceValidationSupport` isn't recognized during validation
- [ ] Ensure validation support chain properly includes custom resource support
- [ ] Test with `generateSnapshot: true` on StructureDefinition
- [ ] Consider pre-parsing approach if direct validation fails
- [ ] Implement fallback JSON Schema validation if needed

### Phase 3: Testing
- [ ] Test valid MedicationInventory with backbone elements is accepted
- [ ] Test backbone element nested fields are persisted correctly
- [ ] Test invalid element names are rejected
- [ ] Test cardinality violations are caught
- [ ] Test terminology binding validation

---

## Files to Modify

| File | Changes Needed |
|------|----------------|
| `JavaClassBuilder.java` | Add backbone element class generation |
| `StructureDefinitionParser.java` | Add backbone element detection |
| `ProfileValidator.java` | Fix validation chain for custom resources |
| `CustomResourceValidationSupport.java` | Ensure proper StructureDefinition delivery |

---

## Related Files

- `fhir4java-codegen/src/main/java/org/fhirframework/codegen/JavaClassBuilder.java`
- `fhir4java-codegen/src/main/java/org/fhirframework/codegen/StructureDefinitionParser.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/validation/ProfileValidator.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceValidationSupport.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceLoader.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/resource/CustomResourceRegistry.java`
- `fhir4java-server/src/main/resources/fhir-config/r5/profiles/StructureDefinition-MedicationInventory.json`

---

## References

- HAPI FHIR Custom Structures: https://hapifhir.io/hapi-fhir/docs/model/custom_structures.html
- HAPI FHIR BackboneElement: https://hapifhir.io/hapi-fhir/apidocs/hapi-fhir-structures-r5/org/hl7/fhir/r5/model/BackboneElement.html
- FHIR Validation: https://www.hl7.org/fhir/validation.html
- FHIR BackboneElement: https://www.hl7.org/fhir/backboneelement.html

---

## Notes

- **Current workaround**: Disable profile validation (`fhir4java.validation.profile-validation-enabled: false`)
- This allows CRUD operations but:
  - Does NOT validate custom resources against their StructureDefinition
  - Backbone element fields are IGNORED (data loss!)
  - Invalid elements will be accepted
- Backbone element support is critical for proper MedicationInventory handling since `packaging` contains multiple nested fields
