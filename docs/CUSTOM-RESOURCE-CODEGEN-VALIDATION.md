# Custom Resource Code Generation and Validation

This document provides comprehensive documentation for implementing custom FHIR resources in FHIR4Java, including code generation, registration, validation, and search parameter support.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Code Generation Module](#code-generation-module)
4. [Custom Resource Registration](#custom-resource-registration)
5. [Validation Configuration](#validation-configuration)
6. [Search Parameters for Custom Resources](#search-parameters-for-custom-resources)
7. [Creating a Custom Resource](#creating-a-custom-resource)
8. [Configuration Reference](#configuration-reference)
9. [Troubleshooting](#troubleshooting)
10. [Known Limitations](#known-limitations)

---

## Overview

FHIR4Java supports custom FHIR resources through a code generation approach that creates HAPI FHIR-compatible Java classes from StructureDefinition JSON files. This enables custom resources to work seamlessly with HAPI's parsing, validation, and serialization infrastructure.

### Key Benefits

| Aspect | Description |
|--------|-------------|
| **Type Safety** | Generated classes provide compile-time checking |
| **Full HAPI Integration** | Automatic parsing, serialization, and validation |
| **Backbone Elements** | Support for nested complex types |
| **Search Parameters** | Custom search parameters work like standard resources |
| **Minimal Maintenance** | Add new resources by creating StructureDefinition files |

### Supported Features

- Custom resource types extending `DomainResource` or `Resource`
- Profiles constraining existing FHIR resources
- Backbone elements (nested complex types)
- Custom search parameters
- Custom operations
- Multi-version support (R5, R4B)

---

## Architecture

### Build-Time Flow

```
1. Maven build starts
   │
2. fhir-codegen:generate goal executes (generate-sources phase)
   │
3. Scans: fhir-config/r5/profiles/StructureDefinition-*.json
   │
4. Filters: Only custom resources (derivation=specialization, kind=resource, abstract=false)
   │
5. For each custom StructureDefinition:
   │── Parse JSON → ResourceMetadata
   │── Generate Java class with:
   │   ├── @ResourceDef annotation
   │   ├── @Child annotated fields
   │   ├── Backbone element inner @Block classes
   │   ├── copy(), getResourceType(), fhirType(), isEmpty() methods
   │── Write to: target/generated-sources/fhir/
   │
6. Generated sources added to compilation
   │
7. Application starts
   │
8. CustomResourceRegistry.registerCustomResources()
   │── Loads generated classes
   │── Registers with HAPI FhirContext for each version
   │
9. Custom resources work like standard FHIR resources
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Build Time                                      │
│  ┌─────────────────┐    ┌──────────────────┐    ┌───────────────────┐   │
│  │ StructureDefn   │───>│ JavaClassBuilder │───>│ Generated Classes │   │
│  │ JSON Files      │    │ (JavaPoet)       │    │ (.java files)     │   │
│  └─────────────────┘    └──────────────────┘    └───────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Runtime                                         │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │ CustomResource      │───>│ FhirContext         │                     │
│  │ Registry            │    │ (per version)       │                     │
│  └─────────────────────┘    └─────────────────────┘                     │
│           │                          │                                   │
│           │                          ▼                                   │
│           │                 ┌─────────────────────┐                     │
│           │                 │ HAPI Parser/        │                     │
│           └────────────────>│ Serializer/         │                     │
│                             │ Validator           │                     │
│                             └─────────────────────┘                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Code Generation Module

### Module: `fhir4java-codegen`

The code generation module provides a Maven plugin that generates Java classes from FHIR StructureDefinition files.

### Key Components

#### StructureDefinitionParser.java

Parses FHIR StructureDefinition JSON files and extracts:
- Resource metadata (name, URL, base definition)
- Element definitions (path, cardinality, types, bindings)
- Backbone element identification
- Custom resource detection (derivation, kind, abstract)

```java
// Key methods
public ResourceMetadata parse(Path structureDefinitionFile)
public boolean isCustomResource()  // derivation=specialization, kind=resource, abstract=false
public boolean isBackboneElement() // type[0].code == "BackboneElement"
```

#### JavaClassBuilder.java

Uses JavaPoet to generate HAPI FHIR-compatible Java classes:

```java
// Generated class structure
@ResourceDef(name = "MedicationInventory", profile = "http://...")
public class MedicationInventory extends DomainResource {

    @Child(name = "status", min = 1, max = 1)
    @Description(shortDefinition = "active | inactive | depleted")
    private CodeType status;

    @Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
    private List<PackagingComponent> packaging;

    // Getters and setters...

    @Override
    public String fhirType() { return "MedicationInventory"; }

    @Override
    public ResourceType getResourceType() { return null; }

    @Override
    public MedicationInventory copy() { /* ... */ }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && ElementUtil.isEmpty(status, packaging, /* ... */);
    }

    @Block
    public static class PackagingComponent extends BackboneElement {
        @Child(name = "type", min = 1, max = 1)
        private CodeableConcept type;

        // Getters, setters, copy(), isEmpty()...
    }
}
```

#### GenerateResourcesMojo.java

Maven plugin goal bound to `generate-sources` phase:

```xml
<plugin>
    <groupId>org.fhirframework</groupId>
    <artifactId>fhir4java-codegen</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <structureDefinitionDir>
                    ${project.basedir}/../fhir4java-server/src/main/resources/fhir-config/r5/profiles
                </structureDefinitionDir>
                <outputDirectory>${project.build.directory}/generated-sources/fhir</outputDirectory>
                <packageName>org.fhirframework.generated.resources</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Generated Code Location

```
fhir4java-core/target/generated-sources/fhir/
└── org/fhirframework/generated/resources/
    └── MedicationInventory.java
```

---

## Custom Resource Registration

### CustomResourceRegistry.java

Location: `fhir4java-core/src/main/java/org/fhirframework/core/resource/CustomResourceRegistry.java`

Automatically registers generated custom resource classes with HAPI FhirContext at application startup.

```java
@Component
public class CustomResourceRegistry {

    @PostConstruct
    public void registerCustomResources() {
        // Discover generated resource classes
        List<Class<?>> customResources = discoverGeneratedResources();

        // Register with each FHIR version context
        for (FhirVersion version : FhirVersion.values()) {
            FhirContext ctx = contextFactory.getContext(version);
            for (Class<?> resourceClass : customResources) {
                ctx.getResourceDefinition((Class<? extends IBaseResource>) resourceClass);
                log.info("Registered custom resource: {} for version {}",
                    resourceClass.getSimpleName(), version);
            }
        }
    }
}
```

### Registration Requirements

For HAPI to properly recognize custom resources:

1. **@ResourceDef annotation** - Defines resource name and profile URL
2. **@Child annotations** - Defines all fields with cardinality
3. **@Block annotation** - Marks backbone element inner classes
4. **isEmpty() override** - Required for proper serialization
5. **copy() override** - Required by HAPI for resource copying

---

## Validation Configuration

### Parser Error Handler

Controls how HAPI handles unknown or invalid elements during parsing.

```yaml
# application.yml
fhir4java:
  validation:
    # Parser error handler mode
    # - strict: Throws exceptions for unknown elements (recommended)
    # - lenient: Logs warnings but allows parsing to continue
    parser-error-handler: strict
```

**Implementation** (`FhirContextFactoryImpl.java`):

```java
private FhirContext createContext(FhirVersion version) {
    FhirContext context = FhirContext.forR5();

    if (validationConfig.getParserErrorHandler() == ParserErrorMode.STRICT) {
        context.setParserErrorHandler(new StrictErrorHandler());
    } else {
        context.setParserErrorHandler(new LenientErrorHandler());
    }

    return context;
}
```

### Profile Validation

```yaml
fhir4java:
  validation:
    enabled: true
    profile-validation: on  # Requires proper StructureDefinition with snapshot
```

**Important**: Profile validation requires StructureDefinitions to include a `snapshot` section with all elements (including inherited DomainResource base elements). Without a snapshot, validation will fail.

---

## Search Parameters for Custom Resources

### Challenge

HAPI FHIR's `SearchParameter.base` field uses an enum (`VersionIndependentResourceTypesAll`) that only includes standard FHIR resource types. Custom resource types like `MedicationInventory` are not in this enum.

### Solution

`SearchParameterRegistry` detects custom resource types and parses SearchParameter JSON files directly using Jackson, bypassing HAPI's enum validation.

**Implementation** (`SearchParameterRegistry.java`):

```java
private void loadSearchParameterFile(FhirVersion version, Resource file, IParser parser) {
    String jsonContent = new String(file.getInputStream().readAllBytes());

    // Extract base types from JSON
    List<String> baseTypes = extractBaseTypesFromJson(jsonContent);

    // Check if any base type is a custom resource
    boolean hasCustomResourceBase = baseTypes.stream().anyMatch(this::isCustomResourceType);

    SearchParameter sp;
    if (hasCustomResourceBase) {
        // Parse directly from JSON to avoid HAPI enum validation
        sp = parseSearchParameterFromJson(jsonContent);
    } else {
        // Standard resources use HAPI parser
        sp = parser.parseResource(SearchParameter.class, jsonContent);
    }

    // Register search parameter...
}

private boolean isCustomResourceType(String resourceType) {
    try {
        Enumerations.VersionIndependentResourceTypesAll.fromCode(resourceType);
        return false; // Standard resource
    } catch (Exception e) {
        return true;  // Custom resource
    }
}
```

### SearchParameter File Format

```json
{
  "resourceType": "SearchParameter",
  "id": "MedicationInventory-medication",
  "url": "http://fhir4java.org/SearchParameter/MedicationInventory-medication",
  "name": "medication",
  "status": "draft",
  "code": "medication",
  "base": ["MedicationInventory"],
  "type": "reference",
  "expression": "MedicationInventory.medication",
  "target": ["Medication"]
}
```

**File Location**: `fhir-config/r5/searchparameters/SearchParameter-MedicationInventory-medication.json`

---

## Creating a Custom Resource

### Step 1: Create StructureDefinition

Create a StructureDefinition JSON file with both `snapshot` and `differential` sections.

**Location**: `fhir-config/r5/profiles/StructureDefinition-{ResourceName}.json`

**Critical Requirements**:

| Section | Contents |
|---------|----------|
| `snapshot` | ALL elements including inherited DomainResource base elements |
| `differential` | ONLY custom/modified elements |

**DomainResource Base Elements (must be in snapshot)**:
- `{ResourceName}.id`
- `{ResourceName}.meta`
- `{ResourceName}.implicitRules`
- `{ResourceName}.language`
- `{ResourceName}.text`
- `{ResourceName}.contained`
- `{ResourceName}.extension`
- `{ResourceName}.modifierExtension`

**Backbone Element Base Elements (must be in snapshot)**:
- `{ResourceName}.{backbone}.id`
- `{ResourceName}.{backbone}.extension`
- `{ResourceName}.{backbone}.modifierExtension`

### Step 2: Create SearchParameter Files

Create a SearchParameter JSON file for each searchable element.

**Location**: `fhir-config/r5/searchparameters/SearchParameter-{ResourceName}-{param-name}.json`

**Type Mapping**:

| Element Type | Search Type |
|--------------|-------------|
| string | string |
| code, Coding, CodeableConcept, Identifier | token |
| date, dateTime, instant, Period | date |
| Reference | reference |
| Quantity | quantity |
| integer, decimal | number |

### Step 3: Create Resource Configuration

Create a YAML configuration file for the resource.

**Location**: `fhir-config/resources/{resourcename}.yml`

```yaml
resourceType: MedicationInventory
enabled: true

fhirVersions:
  - version: R5
    default: true

interactions:
  read: true
  create: true
  update: true
  search: true
  history: true

searchParameters:
  mode: allowlist
  common:
    - _id
    - _lastUpdated
  resourceSpecific:
    - medication
    - status
    - location
```

### Step 4: Rebuild Application

```bash
./mvnw clean install
./mvnw spring-boot:run -pl fhir4java-server
```

### Step 5: Test the Resource

```bash
# Create
curl -X POST http://localhost:8080/fhir/r5/MedicationInventory \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"MedicationInventory","status":"active",...}'

# Read
curl http://localhost:8080/fhir/r5/MedicationInventory/{id}

# Search
curl "http://localhost:8080/fhir/r5/MedicationInventory?status=active"
```

---

## Configuration Reference

### application.yml

```yaml
fhir4java:
  # Configuration base path
  config:
    base-path: classpath:fhir-config/

  # Validation settings
  validation:
    enabled: true
    profile-validation: on
    # Parser error handler: strict (throws) or lenient (warns)
    parser-error-handler: strict

  # Custom resource settings
  custom-resources:
    # Package containing generated classes
    package: org.fhirframework.generated.resources
    # Auto-register at startup
    auto-register: true
```

### Directory Structure

```
fhir4java-server/src/main/resources/fhir-config/
├── resources/                          # Resource configuration YAML
│   ├── patient.yml
│   ├── observation.yml
│   └── medicationinventory.yml         # Custom resource config
├── r5/
│   ├── profiles/                       # StructureDefinition JSON
│   │   └── StructureDefinition-MedicationInventory.json
│   ├── searchparameters/               # SearchParameter JSON
│   │   ├── SearchParameter-MedicationInventory-medication.json
│   │   ├── SearchParameter-MedicationInventory-status.json
│   │   └── SearchParameter-MedicationInventory-location.json
│   └── operations/                     # OperationDefinition JSON (optional)
└── r4b/                                # R4B-specific (if supporting R4B)
```

---

## Troubleshooting

### Issue: Unknown element during parsing

**Symptom**:
```
WARN: Unknown element 'packaging' found while parsing
```

**Cause**: The element is not recognized by HAPI's ModelScanner.

**Solution**: Ensure the generated class includes:
1. `@Child` annotation for the field
2. Proper `@Block` annotation on inner backbone classes
3. `isEmpty()` method that checks all custom fields

### Issue: Backbone element data lost after serialization

**Symptom**: Backbone elements parse correctly but are missing in serialized output.

**Cause**: `isEmpty()` method not overridden in backbone element class.

**Solution**: Generate `isEmpty()` method:
```java
@Override
public boolean isEmpty() {
    return super.isEmpty() && ElementUtil.isEmpty(type, unitsPerPackage, packageCount);
}
```

### Issue: SearchParameter not found for custom resource

**Symptom**:
```
No FHIRPath expression found for parameter 'medication'
```

**Cause**: SearchParameter file failed to load due to HAPI enum validation.

**Solution**: SearchParameterRegistry should use JSON parsing for custom resources. Check logs for:
```
Registered search parameter 'medication' for resource type 'MedicationInventory'
```

### Issue: Profile validation fails with "unknown resource name"

**Symptom**:
```
This content cannot be parsed (unknown or unrecognized resource name 'MedicationInventory')
```

**Cause**: StructureDefinition missing `snapshot` section.

**Solution**: Add complete snapshot with all DomainResource base elements to the StructureDefinition.

### Issue: DataFormatException for custom resource type

**Symptom**:
```
HAPI-1821: Unknown VersionIndependentResourceTypesAll code 'MedicationInventory'
```

**Cause**: HAPI parser trying to validate custom resource type against enum.

**Solution**: Ensure `SearchParameterRegistry.isCustomResourceType()` correctly identifies the custom resource and uses JSON parsing.

---

## Known Limitations

### 1. Profile Validation Requires Snapshot

HAPI FHIR validators require StructureDefinitions to include a complete `snapshot` section. The differential alone is not sufficient.

**Workaround**: Always generate both `snapshot` and `differential` sections when creating StructureDefinitions for custom resources.

### 2. ResourceType Enum

Custom resources return `null` from `getResourceType()` because HAPI's `ResourceType` enum only includes standard FHIR resources.

**Impact**: Some HAPI utilities that rely on `ResourceType` may not work with custom resources.

### 3. Custom Resource Type Validation

HAPI's built-in validation support chain may not fully recognize custom resource types during validation.

**Workaround**: Use `parser-error-handler: strict` for structural validation during parsing.

---

## Example: MedicationInventory

### StructureDefinition (snippet)

```json
{
  "resourceType": "StructureDefinition",
  "id": "MedicationInventory",
  "url": "http://fhir4java.org/StructureDefinition/MedicationInventory",
  "name": "MedicationInventory",
  "kind": "resource",
  "abstract": false,
  "type": "MedicationInventory",
  "baseDefinition": "http://hl7.org/fhir/StructureDefinition/DomainResource",
  "derivation": "specialization",
  "snapshot": {
    "element": [
      {"id": "MedicationInventory", "path": "MedicationInventory", ...},
      {"id": "MedicationInventory.id", "path": "MedicationInventory.id", ...},
      {"id": "MedicationInventory.meta", "path": "MedicationInventory.meta", ...},
      // ... other DomainResource base elements ...
      {"id": "MedicationInventory.status", "path": "MedicationInventory.status", ...},
      {"id": "MedicationInventory.medication", "path": "MedicationInventory.medication", ...},
      {"id": "MedicationInventory.packaging", "path": "MedicationInventory.packaging", "type": [{"code": "BackboneElement"}], ...},
      {"id": "MedicationInventory.packaging.id", "path": "MedicationInventory.packaging.id", ...},
      {"id": "MedicationInventory.packaging.extension", "path": "MedicationInventory.packaging.extension", ...},
      {"id": "MedicationInventory.packaging.modifierExtension", "path": "MedicationInventory.packaging.modifierExtension", ...},
      {"id": "MedicationInventory.packaging.type", "path": "MedicationInventory.packaging.type", ...},
      {"id": "MedicationInventory.packaging.unitsPerPackage", "path": "MedicationInventory.packaging.unitsPerPackage", ...}
    ]
  },
  "differential": {
    "element": [
      {"id": "MedicationInventory", "path": "MedicationInventory", ...},
      {"id": "MedicationInventory.status", "path": "MedicationInventory.status", ...},
      {"id": "MedicationInventory.medication", "path": "MedicationInventory.medication", ...},
      {"id": "MedicationInventory.packaging", "path": "MedicationInventory.packaging", ...},
      {"id": "MedicationInventory.packaging.type", "path": "MedicationInventory.packaging.type", ...},
      {"id": "MedicationInventory.packaging.unitsPerPackage", "path": "MedicationInventory.packaging.unitsPerPackage", ...}
    ]
  }
}
```

### Generated Java Class (simplified)

```java
@ResourceDef(name = "MedicationInventory", profile = "http://fhir4java.org/StructureDefinition/MedicationInventory")
public class MedicationInventory extends DomainResource {

    @Child(name = "status", min = 1, max = 1)
    private CodeType status;

    @Child(name = "medication", min = 1, max = 1)
    private Reference medication;

    @Child(name = "packaging", min = 0, max = Child.MAX_UNLIMITED)
    private List<PackagingComponent> packaging;

    // Getters/setters...

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && ElementUtil.isEmpty(status, medication, packaging);
    }

    @Block
    public static class PackagingComponent extends BackboneElement {
        @Child(name = "type", min = 1, max = 1)
        private CodeableConcept type;

        @Child(name = "unitsPerPackage", min = 1, max = 1)
        private PositiveIntType unitsPerPackage;

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && ElementUtil.isEmpty(type, unitsPerPackage);
        }
    }
}
```

### Test Request

```bash
curl -X POST http://localhost:8080/fhir/r5/MedicationInventory \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "MedicationInventory",
    "status": "active",
    "medication": {"reference": "Medication/aspirin"},
    "quantity": {"value": 100, "unit": "tablets"},
    "packaging": [{
      "type": {"coding": [{"code": "box"}]},
      "unitsPerPackage": 10,
      "packageCount": 10
    }]
  }'
```

---

## References

- [HAPI FHIR Custom Structures](https://hapifhir.io/hapi-fhir/docs/model/custom_structures.html)
- [FHIR StructureDefinition](https://www.hl7.org/fhir/structuredefinition.html)
- [FHIR BackboneElement](https://www.hl7.org/fhir/backboneelement.html)
- [FHIR SearchParameter](https://www.hl7.org/fhir/searchparameter.html)
- [JavaPoet](https://github.com/square/javapoet)
