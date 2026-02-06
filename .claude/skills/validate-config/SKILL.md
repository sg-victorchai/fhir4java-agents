# Validate FHIR Configuration

Validate FHIR4Java configuration files before deployment to catch errors, inconsistencies, and potential issues.

## Overview

This skill helps you:
- **Validate** YAML and JSON configuration syntax
- **Check** references between configurations
- **Detect** missing dependencies
- **Verify** FHIRPath expressions
- **Identify** potential issues before deployment

## What Gets Validated

| Configuration Type | Location | Checks |
|-------------------|----------|--------|
| Resource configs | `fhir-config/resources/*.yml` | Schema, interactions, search params |
| SearchParameters | `fhir-config/r5/searchparameters/*.json` | FHIRPath, type, base resource |
| StructureDefinitions | `fhir-config/r5/profiles/*.json` | Elements, types, cardinality |
| OperationDefinitions | `fhir-config/r5/operations/*.json` | Parameters, scope, code |
| ValueSets | `fhir-config/r5/terminology/*.json` | Compose, includes, systems |
| CodeSystems | `fhir-config/r5/terminology/*.json` | Concepts, hierarchy |

## Instructions

When the user invokes this skill, perform the following validations:

### Step 1: Scan Configuration Files

Locate all configuration files in:
```
fhir4java-server/src/main/resources/fhir-config/
├── resources/           # Resource YAML configs
├── r5/
│   ├── searchparameters/  # SearchParameter JSONs
│   ├── profiles/          # StructureDefinition JSONs
│   ├── operations/        # OperationDefinition JSONs/YAMLs
│   └── terminology/       # ValueSet/CodeSystem JSONs
└── r4b/                   # R4B versions (similar structure)
```

### Step 2: Validate Each File Type

#### Resource Configuration YAML

**Schema validation:**
```yaml
# Required fields
resourceType: string  # Must be valid FHIR resource type
enabled: boolean

# Optional fields
fhirVersions:
  - version: R5|R4B
    default: boolean  # Exactly one must be true

schema:
  type: shared|dedicated
  name: string

interactions:
  read: boolean
  vread: boolean
  create: boolean
  update: boolean
  patch: boolean
  delete: boolean
  search: boolean
  history: boolean

searchParameters:
  mode: allowlist|denylist
  common: [string]
  resourceSpecific: [string]

profiles:
  - url: string (valid URL)
    required: boolean
```

**Validation rules:**
1. `resourceType` must be a valid FHIR resource name
2. If multiple `fhirVersions`, exactly one must have `default: true`
3. `searchParameters.common` values must be valid common search params
4. `searchParameters.resourceSpecific` values must match SearchParameter files
5. `profiles.url` must be a valid canonical URL

#### SearchParameter JSON

**Schema validation:**
```json
{
  "resourceType": "SearchParameter",  // Required, must be "SearchParameter"
  "id": "string",                     // Required
  "url": "string",                    // Required, valid URL
  "name": "string",                   // Required
  "status": "draft|active|retired",   // Required
  "code": "string",                   // Required, search param code
  "base": ["ResourceType"],           // Required, array of resource types
  "type": "token|string|date|...",    // Required
  "expression": "FHIRPath"            // Required for non-composite
}
```

**Validation rules:**
1. `resourceType` must be "SearchParameter"
2. `base` must contain valid FHIR resource types
3. `type` must be: `number`, `date`, `string`, `token`, `reference`, `composite`, `quantity`, `uri`, `special`
4. `expression` must be valid FHIRPath (basic syntax check)
5. If `type` is `reference`, should have `target` array

#### StructureDefinition JSON

**Schema validation:**
```json
{
  "resourceType": "StructureDefinition",
  "id": "string",
  "url": "string",
  "name": "string",
  "status": "draft|active|retired",
  "kind": "primitive-type|complex-type|resource|logical",
  "abstract": boolean,
  "type": "string",           // Resource type name
  "baseDefinition": "string", // URL of base definition
  "derivation": "specialization|constraint",
  "differential": {
    "element": [...]
  }
}
```

**Validation rules:**
1. `resourceType` must be "StructureDefinition"
2. For profiles (`derivation: constraint`), `baseDefinition` must reference valid base
3. Elements must have valid paths
4. Element types must be valid FHIR types
5. Cardinality (`min`/`max`) must be valid

#### OperationDefinition JSON

**Schema validation:**
```json
{
  "resourceType": "OperationDefinition",
  "id": "string",
  "url": "string",
  "name": "string",
  "status": "draft|active|retired",
  "kind": "operation|query",
  "code": "string",           // Operation code (without $)
  "system": boolean,
  "type": boolean,
  "instance": boolean,
  "parameter": [...]
}
```

**Validation rules:**
1. At least one of `system`, `type`, `instance` must be `true`
2. If `type` or `instance` is true, must have `resource` array
3. Parameters must have valid `name`, `use` (in/out), `min`, `max`, `type`

#### ValueSet/CodeSystem JSON

**Schema validation for ValueSet:**
```json
{
  "resourceType": "ValueSet",
  "id": "string",
  "url": "string",
  "name": "string",
  "status": "draft|active|retired",
  "compose": {
    "include": [
      {
        "system": "string",     // CodeSystem URL
        "concept": [...],       // Optional specific codes
        "filter": [...]         // Optional filters
      }
    ]
  }
}
```

**Validation rules:**
1. `compose.include[].system` should reference known CodeSystems
2. Concept codes should exist in referenced CodeSystem (if local)
3. Filter properties should be valid for the CodeSystem

### Step 3: Cross-Reference Validation

Check references between configuration files:

1. **Resource config → SearchParameter**
   - Each `searchParameters.resourceSpecific` value should have a corresponding SearchParameter JSON

2. **Resource config → Profile**
   - Each `profiles.url` should have a corresponding StructureDefinition

3. **SearchParameter → Resource**
   - SearchParameter `base` should match a configured resource

4. **StructureDefinition → BaseDefinition**
   - Profile's base definition should exist

5. **ValueSet → CodeSystem**
   - ValueSet compose includes should reference valid CodeSystems

### Step 4: Generate Report

After validation, generate a report:

```
# Configuration Validation Report

## Summary
- Total files scanned: {count}
- Errors: {error-count}
- Warnings: {warning-count}
- Info: {info-count}

## Errors (must fix)
❌ resources/patient.yml:15 - Invalid searchParameter 'custom-param' not found in searchparameters/
❌ profiles/MyPatient.json:42 - Invalid element type 'Strin' (did you mean 'string'?)

## Warnings (should review)
⚠️ resources/observation.yml - searchParameters.mode is 'allowlist' but no resourceSpecific params defined
⚠️ operations/my-operation.yml - Operation has no resource types but type=true

## Info
ℹ️ Loaded 15 resource configurations
ℹ️ Loaded 45 SearchParameter definitions
ℹ️ Loaded 3 StructureDefinition profiles
ℹ️ Loaded 5 OperationDefinition files
```

---

## Validation Rules Reference

### Resource Configuration Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| RC001 | Error | `resourceType` is required |
| RC002 | Error | `resourceType` must be valid FHIR resource |
| RC003 | Error | `enabled` must be boolean |
| RC004 | Error | Multiple `fhirVersions` but none is default |
| RC005 | Error | Multiple `fhirVersions` with multiple defaults |
| RC006 | Warning | `searchParameters.mode` is allowlist with empty list |
| RC007 | Error | SearchParameter in list not found in definitions |
| RC008 | Error | Profile URL is invalid |
| RC009 | Warning | Profile URL references unknown StructureDefinition |
| RC010 | Info | Resource uses shared schema (default) |

### SearchParameter Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| SP001 | Error | Missing required field: `id`, `url`, `code`, `base`, `type` |
| SP002 | Error | Invalid `type` value |
| SP003 | Error | Invalid `base` resource type |
| SP004 | Error | Missing `expression` for non-composite type |
| SP005 | Warning | FHIRPath expression may be invalid |
| SP006 | Warning | Reference type without `target` specification |
| SP007 | Error | Composite type without `component` |

### StructureDefinition Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| SD001 | Error | Missing required field |
| SD002 | Error | Invalid `kind` value |
| SD003 | Error | Profile without `baseDefinition` |
| SD004 | Error | Element with invalid path |
| SD005 | Error | Element with invalid type |
| SD006 | Warning | Element cardinality may be too restrictive |
| SD007 | Error | Binding references unknown ValueSet |
| SD008 | Warning | Element missing `short` description |

### OperationDefinition Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| OD001 | Error | Missing required field |
| OD002 | Error | No scope defined (system/type/instance all false) |
| OD003 | Error | Type/instance scope without resource types |
| OD004 | Error | Parameter missing required field |
| OD005 | Error | Parameter with invalid type |
| OD006 | Warning | Output parameter with min > 0 may cause issues |
| OD007 | Warning | Operation code contains invalid characters |

### ValueSet/CodeSystem Rules

| Rule ID | Severity | Description |
|---------|----------|-------------|
| VS001 | Error | Missing required field |
| VS002 | Error | Empty compose (no includes) |
| VS003 | Warning | Include references unknown CodeSystem |
| VS004 | Warning | Concept code not found in CodeSystem |
| CS001 | Error | CodeSystem missing concept definitions |
| CS002 | Warning | Concept missing display text |

---

## Common Issues and Fixes

### Issue: SearchParameter not found

```
❌ RC007: SearchParameter 'custom-identifier' not found
```

**Fix**: Create the SearchParameter file:
```bash
# Create file: fhir-config/r5/searchparameters/SearchParameter-Patient-custom-identifier.json
```

### Issue: Invalid FHIRPath expression

```
⚠️ SP005: FHIRPath expression may be invalid: Patient.name.family
```

**Fix**: Verify the FHIRPath:
- Check property names match FHIR spec
- Ensure collection handling is correct
- Test expression in a FHIRPath evaluator

### Issue: Profile base not found

```
⚠️ SD003: baseDefinition references unknown StructureDefinition
```

**Fix**: Either:
1. Add the base StructureDefinition to profiles/
2. Use a standard FHIR URL (http://hl7.org/fhir/StructureDefinition/...)

### Issue: Missing default version

```
❌ RC004: Multiple fhirVersions but none marked as default
```

**Fix**: Mark exactly one version as default:
```yaml
fhirVersions:
  - version: R5
    default: true  # Add this
  - version: R4B
```

---

## Validation Commands

After generating the report, suggest running:

```bash
# Build to catch compile-time errors
./mvnw clean compile -pl fhir4java-server

# Start server to test runtime loading
./mvnw spring-boot:run -pl fhir4java-server

# Check logs for loading errors
grep -i "error\|warn\|failed" logs/application.log

# Test CapabilityStatement includes resources
curl http://localhost:8080/fhir/r5/metadata | jq '.rest[0].resource[].type'
```

---

## Interactive Validation

When the user invokes this skill:

1. **Scan** all configuration directories
2. **Parse** each file and validate against rules
3. **Cross-reference** between files
4. **Generate** categorized report
5. **Suggest** fixes for common issues
6. **Offer** to create missing files if appropriate

Example interaction:
```
User: /validate-config