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

## TODO: Phase 4 Follow-up - Backbone Element Parser Integration for issue 1: Backbone Elements Ignored (Validation Disabled)

**Created**: 2026-02-10
**Priority**: High
**Status**: Not Started

### Problem Statement

Code generation for backbone elements (nested complex types) is complete and compiles successfully. However, HAPI's JSON parser does not recognize these inner `@Block` classes and silently drops backbone element fields during parsing.

**Example**: `MedicationInventory.PackagingComponent` is generated correctly with proper annotations, but when parsing JSON with a `packaging` array, the parser ignores it completely.

### Root Cause Analysis

1. **ModelScanner Limitation**: HAPI's `ModelScanner` builds the resource structure model at `FhirContext` initialization time by scanning resource classes
2. **No Recursive Inner Class Discovery**: ModelScanner doesn't automatically discover inner static classes annotated with `@Block`
3. **Parser Behavior**: The parser only recognizes fields that ModelScanner has registered in the resource definition
4. **Silent Failure**: Unknown fields are dropped silently (strict mode only validates the structure of _known_ fields)

### Evidence

```java
// Generated code is correct
@ResourceDef(name = "MedicationInventory", ...)
public class MedicationInventory extends DomainResource {

    @Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
    private List<PackagingComponent> packaging;  // Field defined correctly

    @Block
    public static class PackagingComponent extends BackboneElement {
        @Child(name = "type", min = 1, max = 1)
        private CodeableConcept type;
        // ... more fields
    }
}
```

```
// But parser drops the data
Input JSON:  {"packaging": [{"type": {...}, "unitsPerPackage": 20}]}
Parsed JSON: {"status": "active", "quantity": {...}}  // packaging missing!
```

### Investigation Tasks

#### Task 1: Study HAPI ModelScanner API

**Goal**: Understand how HAPI discovers resource structure and if we can extend it

**Research Questions**:

1. How does ModelScanner determine which classes to scan?
2. Is there an API to trigger rescanning after custom resources are registered?
3. Can we explicitly register child element definitions?
4. How do standard FHIR resources with backbone elements work?

**Files to Study**:

- `ca.uhn.fhir.context.ModelScanner` (HAPI FHIR library)
- `ca.uhn.fhir.context.RuntimeResourceDefinition` (HAPI FHIR library)
- `ca.uhn.fhir.context.RuntimeChildCompositeBoundDatatypeDefinition` (HAPI FHIR library)
- Look at how standard resources like `Patient.contact` (backbone element) are handled

#### Task 2: Test Alternative Registration Approaches

**Approach A**: Force rescan after registering custom resources

```java
// In CustomResourceRegistry, after getResourceDefinition()
RuntimeResourceDefinition def = ctx.getResourceDefinition(typedResourceClass);

// Try to force rescan of inner classes
for (Class<?> innerClass : typedResourceClass.getDeclaredClasses()) {
    if (innerClass.isAnnotationPresent(Block.class)) {
        // Try registering as composite definition
        ctx.getElementDefinition((Class<? extends IBase>) innerClass);
    }
}
```

**Approach B**: Register during FhirContext creation

```java
// In FhirContextFactoryImpl.createContext()
FhirContext context = FhirContext.forR5();

// Register custom resources BEFORE any parsing happens
for (CustomResource resource : customResources) {
    context.registerCustomType(resource.getClass());
    // Also register inner blocks?
}
```

**Approach C**: Use HAPI's custom type registration

```java
// Research if HAPI has an API like:
context.registerCustomCompositeType(PackagingComponent.class, "packaging");
```

#### Task 3: Consider Alternative Code Generation Strategies

If ModelScanner can't be extended, explore alternative code structures:

**Option 1**: Generate separate top-level classes

```java
// Instead of inner class
package org.fhirframework.generated.resources;

@Block
public class MedicationInventoryPackaging extends BackboneElement {
    // Same fields as before
}

// In MedicationInventory.java
@Child(name = "packaging", ...)
private List<MedicationInventoryPackaging> packaging;
```

**Pros**: Might be easier for ModelScanner to discover
**Cons**: More files, naming collisions possible

**Option 2**: Generate as extensions

```java
// Use HAPI's Extension mechanism
@Child(name = "packaging")
@Extension(url = "http://fhir4java.org/StructureDefinition/packaging", ...)
private List<IBaseExtension> packaging;
```

**Pros**: Extensions are well-supported by HAPI
**Cons**: Not semantically correct, loses type safety

**Option 3**: Don't use code generation for backbone elements (fallback)

- Keep backbone elements as raw JSON
- Only generate top-level resource fields
- Manually validate backbone elements

**Pros**: Simple, would work immediately
**Cons**: Loses type safety, defeats purpose of code generation

#### Task 4: Test with Standard FHIR Resource

**Goal**: Verify our understanding by examining how standard resources work

**Steps**:

1. Find a standard FHIR R5 resource with backbone elements (e.g., `Patient.contact`)
2. Trace parser execution to see how ModelScanner discovers the inner class
3. Compare with our generated code to identify differences
4. Use debugger to step through `ModelScanner.scan()` for both cases

#### Task 5: Reach Out to HAPI Community

If internal APIs don't provide a solution:

1. Check HAPI FHIR GitHub issues for similar problems
2. Post question on HAPI FHIR Google Group
3. Consider contributing a patch to HAPI if ModelScanner needs enhancement

### Implementation Plan (Once Solution Found)

1. **Update CustomResourceRegistry** with working backbone registration approach
2. **Update documentation** with how backbone elements are discovered
3. **Add integration tests** verifying backbone element parsing
4. **Update code generator** if alternative structure is needed
5. **Re-enable profile validation** once parsing works

### Acceptance Criteria

- [ ] Backbone element fields are recognized by HAPI parser
- [ ] JSON with backbone elements parses correctly (e.g., `packaging` array)
- [ ] Parsed backbone elements are accessible via getters
- [ ] Serialization round-trip preserves backbone element data
- [ ] Integration test: Create → Read → Verify backbone data present

---

## Files to Modify

| File                                   | Changes Needed                             |
| -------------------------------------- | ------------------------------------------ |
| `JavaClassBuilder.java`                | Add backbone element class generation      |
| `StructureDefinitionParser.java`       | Add backbone element detection             |
| `ProfileValidator.java`                | Fix validation chain for custom resources  |
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
