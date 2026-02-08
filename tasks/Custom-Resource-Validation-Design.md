# Custom Resource Validation Design

## Overview

This document describes the design and implementation of custom FHIR resource support (e.g., `MedicationInventory`) that enables handling resource types not natively known to HAPI FHIR.

## Problem Statement

HAPI FHIR only recognizes standard FHIR resource types that have corresponding Java classes. Custom resources like `MedicationInventory` (defined via StructureDefinition) cause failures in:

1. **Parsing**: `ctx.newJsonParser().parseResource(body)` throws `DataFormatException`
2. **Validation**: `FhirValidator.validateWithResult()` requires parsing first
3. **Bundle Building**: Search/history results need parsed `Resource` objects for Bundle entries

## Architecture

### Two-Pronged Approach

```
┌─────────────────────────────────────────────────────────────────┐
│                     FhirResourceController                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  isCustomResource(resourceType, version)?                 │   │
│  │     YES → Use JSON manipulation (CustomResourceHelper)    │   │
│  │     NO  → Use HAPI parsing (standard path)                │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     FhirResourceService                          │
│  ┌────────────────────┐    ┌────────────────────────────────┐   │
│  │ Standard Resources │    │ Custom Resources               │   │
│  │ - HAPI parsing     │    │ - JSON manipulation            │   │
│  │ - HAPI validation  │    │ - JSON string validation       │   │
│  │ - HAPI serializing │    │ - Raw JSON storage/retrieval   │   │
│  └────────────────────┘    └────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     ProfileValidator                             │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ validateResource(IBaseResource) - for standard resources   │ │
│  │ validateJsonString(String) - for custom resources          │ │
│  │                                                            │ │
│  │ ValidationSupportChain:                                    │ │
│  │   1. CustomResourceValidationSupport (custom SD/VS/CS)     │ │
│  │   2. DefaultProfileValidationSupport                       │ │
│  │   3. InMemoryTerminologyServerValidationSupport            │ │
│  │   4. CommonCodeSystemsTerminologyService                   │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. CustomResourceHelper

**Location**: `fhir4java-core/src/main/java/org/fhirframework/core/resource/CustomResourceHelper.java`

**Purpose**: Detects custom resources and provides JSON manipulation utilities.

**Key Methods**:

```java
// Check if resource type is custom (configured but unknown to HAPI)
public boolean isCustomResource(String resourceType, FhirVersion version) {
    if (!resourceRegistry.isResourceConfigured(resourceType)) {
        return false;
    }
    return !isKnownToHapi(resourceType, version);
}

// Check if HAPI knows about this resource type
public boolean isKnownToHapi(String resourceType, FhirVersion version) {
    try {
        FhirContext ctx = contextFactory.getContext(version);
        RuntimeResourceDefinition def = ctx.getResourceDefinition(resourceType);
        return def != null;
    } catch (Exception e) {
        return false;  // HAPI throws DataFormatException for unknown resources
    }
}

// JSON manipulation methods
public String extractResourceType(String jsonBody);
public String extractId(String jsonBody);
public String setId(String jsonBody, String id);
public String updateMeta(String jsonBody, int versionId, String lastUpdated);
public boolean validateBasicStructure(String jsonBody, String resourceType);
```

### 2. CustomResourceValidationSupport

**Location**: `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceValidationSupport.java`

**Purpose**: Provides custom StructureDefinitions, CodeSystems, and ValueSets to HAPI's validation chain.

**Key Methods**:

```java
@Override
public IBaseResource fetchStructureDefinition(String url) {
    // Returns StructureDefinition for custom resources
    return customResources.getStructureDefinition(url);
}

@Override
public IBaseResource fetchCodeSystem(String system) {
    return customResources.getCodeSystem(system);
}

@Override
public IBaseResource fetchValueSet(String url) {
    return customResources.getValueSet(url);
}
```

### 3. CustomResourceLoader

**Location**: `fhir4java-core/src/main/java/org/fhirframework/core/validation/CustomResourceLoader.java`

**Purpose**: Loads custom conformance resources from `fhir-config/r5/profiles/`, `fhir-config/r5/terminology/`.

**Loaded Resources**:
- `StructureDefinition-MedicationInventory.json`
- Custom CodeSystems and ValueSets

### 4. ProfileValidator (Enhanced)

**Location**: `fhir4java-core/src/main/java/org/fhirframework/core/validation/ProfileValidator.java`

**New Method for Custom Resources**:

```java
/**
 * Validate a JSON string directly against profiles.
 * Used for custom resources that cannot be parsed by HAPI.
 */
public ValidationResult validateJsonString(String jsonString, FhirVersion version) {
    if (!validatorEnabled) {
        return new ValidationResult();  // Skip if disabled
    }

    FhirValidator validator = getOrInitializeValidator(version);
    if (validator == null) {
        return new ValidationResult();  // No validator available
    }

    // HAPI validator can validate JSON strings directly
    ca.uhn.fhir.validation.ValidationResult hapiResult =
        validator.validateWithResult(jsonString);

    return convertHapiResult(hapiResult);
}
```

**Issue**: This approach fails because HAPI's `validateWithResult(String)` internally parses the JSON first, which fails for unknown resource types.

### 5. FhirResourceService (Enhanced)

**Location**: `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/FhirResourceService.java`

**Changes**:

1. **Constructor Injection**:
```java
private final CustomResourceHelper customResourceHelper;

public FhirResourceService(..., CustomResourceHelper customResourceHelper) {
    this.customResourceHelper = customResourceHelper;
}
```

2. **Create Method Branching**:
```java
@Transactional
public ResourceResult create(String resourceType, String resourceJson, FhirVersion version) {
    // Branch for custom resources
    if (customResourceHelper.isCustomResource(resourceType, version)) {
        return createCustomResource(resourceType, resourceJson, version);
    }
    // ... existing code for standard resources
}
```

3. **Custom Resource Create**:
```java
private ResourceResult createCustomResource(String resourceType, String resourceJson, FhirVersion version) {
    // Validate basic structure
    if (!customResourceHelper.validateBasicStructure(resourceJson, resourceType)) {
        throw new FhirException("Invalid resource structure", "invalid");
    }

    // Full profile validation (currently broken for custom resources)
    if (validationConfig.isEnabled() && validationConfig.isProfileValidationEnabled()) {
        validateCustomResourceOrThrow(resourceJson, version);
    }

    // Generate/extract ID
    String resourceId = customResourceHelper.extractId(resourceJson);
    if (resourceId == null || resourceId.isBlank()) {
        resourceId = UUID.randomUUID().toString();
    }

    // Update JSON with ID and meta
    String updatedJson = customResourceHelper.setId(resourceJson, resourceId);
    updatedJson = customResourceHelper.updateMeta(updatedJson, versionId, timestamp);

    // Persist entity
    FhirResourceEntity entity = FhirResourceEntity.builder()
            .resourceType(resourceType)
            .resourceId(resourceId)
            .content(updatedJson)
            // ... other fields
            .build();
    repository.save(entity);

    return new ResourceResult(resourceId, versionId, updatedJson, now, false);
}
```

4. **Search for Custom Resources**:
```java
public String searchCustomResourceAsJson(String resourceType, Map<String, String> params,
                                          FhirVersion version, int count, String requestUrl) {
    // Query database
    Page<FhirResourceEntity> page = repository.searchWithParams(...);

    // Build JSON Bundle manually (bypass HAPI Bundle class)
    StringBuilder json = new StringBuilder();
    json.append("{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":");
    json.append(page.getTotalElements());

    // Add entries with raw JSON content
    json.append(",\"entry\":[");
    for (FhirResourceEntity entity : page.getContent()) {
        json.append("{\"fullUrl\":\"...\",\"resource\":");
        json.append(entity.getContent());  // Raw JSON, no parsing needed
        json.append("}");
    }
    json.append("]}");

    return json.toString();
}
```

5. **History for Custom Resources**:
```java
public String historyCustomResourceAsJson(String resourceType, String resourceId, FhirVersion version) {
    // Similar pattern - build JSON Bundle manually with raw resource content
}
```

### 6. FhirResourceController (Enhanced)

**Location**: `fhir4java-api/src/main/java/org/fhirframework/api/controller/FhirResourceController.java`

**Changes**:

1. **Constructor Injection**:
```java
private final CustomResourceHelper customResourceHelper;
```

2. **Create Method**:
```java
private ResponseEntity<String> create(String resourceType, String body, HttpServletRequest request) {
    FhirVersion version = FhirVersionFilter.getVersion(request);
    FhirContext ctx = contextFactory.getContext(version);

    // Check if custom resource
    boolean isCustomResource = customResourceHelper.isCustomResource(resourceType, version);

    // For custom resources, parsedResource will be null
    IBaseResource parsedResource = null;
    if (!isCustomResource) {
        parsedResource = ctx.newJsonParser().parseResource(body);
    }

    // Build plugin context with raw JSON for custom resources
    PluginContext pluginContext = PluginContext.builder()
            .operationType(OperationType.CREATE)
            .resourceType(resourceType)
            .fhirVersion(version)
            .inputResource(parsedResource)  // null for custom resources
            .build();

    pluginContext.setAttribute("rawJson", body);
    pluginContext.setAttribute("isCustomResource", isCustomResource);

    // Execute plugins and service call...
}
```

3. **Search Method**:
```java
private ResponseEntity<String> search(String resourceType, Map<String, String> params, HttpServletRequest request) {
    boolean isCustomResource = customResourceHelper.isCustomResource(resourceType, version);

    if (isCustomResource) {
        // Return raw JSON Bundle directly
        String jsonBundle = resourceService.searchCustomResourceAsJson(...);
        return ResponseEntity.ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(jsonBundle);
    }

    // Standard path using HAPI Bundle
    Bundle bundle = resourceService.search(...);
    return ResponseEntity.ok()
            .body(parser.encodeResourceToString(bundle));
}
```

## Data Flow

### CREATE Custom Resource

```
1. POST /fhir/r5/MedicationInventory
   │
2. FhirResourceController.create()
   │── isCustomResource("MedicationInventory", R5) → true
   │── parsedResource = null (skip HAPI parsing)
   │── pluginContext.setAttribute("rawJson", body)
   │
3. FhirResourceService.create()
   │── isCustomResource() → true
   │── createCustomResource()
   │   │── validateBasicStructure()     ✓ Works
   │   │── validateCustomResourceOrThrow()  ✗ Fails (HAPI can't parse)
   │   │── customResourceHelper.setId()
   │   │── customResourceHelper.updateMeta()
   │   │── repository.save(entity)
   │
4. Return ResourceResult with raw JSON
```

### SEARCH Custom Resource

```
1. GET /fhir/r5/MedicationInventory?status=active
   │
2. FhirResourceController.search()
   │── isCustomResource() → true
   │── resourceService.searchCustomResourceAsJson()
   │
3. FhirResourceService.searchCustomResourceAsJson()
   │── repository.searchWithParams()
   │── Build JSON Bundle manually
   │   │── Embed raw JSON content in entries
   │── Return JSON string
   │
4. Return ResponseEntity with JSON Bundle
```

## Configuration

### Resource Configuration

```yaml
# fhir-config/resources/medicationinventory.yml
resourceType: MedicationInventory
enabled: true
fhirVersions:
  - version: R5
    default: true
interactions:
  read: true
  create: true
  update: true
  delete: true
  search: true
  history: true
```

### StructureDefinition

```
fhir-config/r5/profiles/StructureDefinition-MedicationInventory.json
```

### Validation Configuration

```yaml
# application.yml
fhir4java:
  validation:
    enabled: true
    profile-validation-enabled: false  # Must be false until validation is fixed
    strict-profile-validation: true
```

## What Works

| Feature | Status | Notes |
|---------|--------|-------|
| CREATE custom resource | Partial | Works when validation disabled |
| READ custom resource | Works | Returns raw JSON |
| UPDATE custom resource | Partial | Works when validation disabled |
| DELETE custom resource | Works | Standard soft delete |
| SEARCH custom resource | Works | Returns JSON Bundle with raw entries |
| HISTORY custom resource | Works | Returns JSON Bundle with raw entries |
| Basic structure validation | Works | Checks resourceType matches |
| Profile validation | Broken | HAPI can't validate unknown types |

## What Doesn't Work

| Feature | Issue | Root Cause |
|---------|-------|------------|
| Profile validation | Fails with "unknown resource name" | HAPI parses JSON before validating |
| Element validation | Not implemented | No validation against StructureDefinition |
| Cardinality checking | Not implemented | Only basic structure is checked |
| Terminology validation | Not working | Depends on profile validation |

## Key Files Modified

```
fhir4java-core/
├── src/main/java/org/fhirframework/core/
│   ├── resource/CustomResourceHelper.java        # Existing, used for detection
│   └── validation/
│       ├── ProfileValidator.java                 # Added validateJsonString()
│       ├── CustomResourceValidationSupport.java  # Existing, provides SD/CS/VS
│       └── CustomResourceLoader.java             # Existing, loads conformance resources

fhir4java-persistence/
├── src/main/java/org/fhirframework/persistence/service/
│   └── FhirResourceService.java                  # Added custom resource methods

fhir4java-api/
├── src/main/java/org/fhirframework/api/controller/
│   └── FhirResourceController.java               # Added custom resource handling
```

## Next Steps

See `TASK-custom-resource-validation-fix.md` for the validation fix implementation plan.
