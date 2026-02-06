# Add Custom FHIR Operation

Guide the user through defining a custom FHIR extended operation (`$operation`) with OperationDefinition and optional implementation scaffolding.

## Overview

FHIR extended operations allow you to:
- **Extend** FHIR capabilities beyond standard CRUD
- **Implement** complex business logic (e.g., `$merge`, `$validate`, `$everything`)
- **Create** organization-specific functionality
- **Expose** workflows as standard FHIR operations

## Operation Levels

| Level | URL Pattern | Example | Use Case |
|-------|-------------|---------|----------|
| **System** | `POST /fhir/$op` | `$export` | Server-wide operations |
| **Type** | `POST /fhir/Patient/$op` | `$match`, `$merge` | Operations on resource type |
| **Instance** | `POST /fhir/Patient/123/$op` | `$everything` | Operations on specific resource |

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Operation Identity

Gather basic information:

| Field | Description | Example |
|-------|-------------|---------|
| Operation Code | The operation name (without $) | `merge-duplicates` |
| Title | Human-readable title | "Merge Duplicate Patients" |
| Description | What the operation does | "Merges duplicate patient records into a single record" |
| Publisher | Organization name | "Example Healthcare" |

### Step 2: Operation Scope

Ask where the operation can be invoked:

| Scope | Description | Typical Use |
|-------|-------------|-------------|
| `system` | Server-level (`/fhir/$op`) | Server-wide operations (e.g., bulk export) |
| `type` | Resource type level (`/fhir/Patient/$op`) | Operations on all resources of a type |
| `instance` | Instance level (`/fhir/Patient/123/$op`) | Operations on a specific resource |

**Multiple scopes allowed** - an operation can be invoked at multiple levels.

### Step 3: Target Resources

Ask which resource types this operation applies to:

- For **system** operations: May not require resource types
- For **type/instance** operations: Specify which resources (e.g., `Patient`, `Observation`)

**Examples:**
- `Patient` only - for patient-specific operations
- `Patient`, `Practitioner` - for person-related operations
- `Resource` - for operations applicable to any resource
- Empty (system only) - for server-wide operations

### Step 4: Input Parameters

For each input parameter, gather:

| Property | Description | Required | Example |
|----------|-------------|----------|---------|
| Name | Parameter name (camelCase) | Yes | `sourcePatient` |
| Type | FHIR data type | Yes | `Reference(Patient)` |
| Cardinality | `0..1`, `1..1`, `0..*`, `1..*` | Yes | `1..1` |
| Description | What the parameter is for | Yes | "The patient to merge from" |

**Common Parameter Types:**

| Type | Description | Example Value |
|------|-------------|---------------|
| `string` | Text value | `"John"` |
| `boolean` | True/false | `true` |
| `integer` | Whole number | `42` |
| `dateTime` | Date and time | `"2024-01-15T10:30:00Z"` |
| `Reference(X)` | Reference to resource X | `"Patient/123"` |
| `Resource` | Any FHIR resource | Full Patient resource |
| `Identifier` | Business identifier | `{"system": "...", "value": "..."}` |
| `code` | Code from value set | `"active"` |
| `uri` | URI | `"http://example.org/profile"` |

Display summary:
```
Input Parameters for $merge-duplicates:
1. sourcePatient (Reference(Patient), 1..1) - The patient to merge from
2. targetPatient (Reference(Patient), 1..1) - The patient to merge into
3. preview (boolean, 0..1) - If true, return preview without merging
```

### Step 5: Output Parameters

For each output parameter, gather the same information as inputs.

**Common outputs:**
- `return` - Single result resource
- `result` - Result bundle or parameters
- `outcome` - OperationOutcome with messages

Display summary:
```
Output Parameters for $merge-duplicates:
1. return (Parameters, 1..1) - The merge result with outcome
```

### Step 6: Affects State

Ask: **Does this operation modify data?**

| Value | Meaning | Examples |
|-------|---------|----------|
| `true` | Operation changes server state | `$merge`, `$apply`, `$submit` |
| `false` | Operation is read-only | `$validate`, `$everything`, `$match` |

**Important**: This affects HTTP method - `affectsState: true` operations should use POST, while read-only operations can use GET.

### Step 7: Configuration Options

Ask about runtime configuration:

| Property | Description | Default |
|----------|-------------|---------|
| Enabled | Can be enabled/disabled | `true` |
| Timeout | Max execution time | `30s` |
| Async | Support async execution | `false` |

### Step 8: Generate Files

After gathering all information, generate:

#### 1. OperationDefinition JSON (FHIR standard format)
Location: `fhir4java-server/src/main/resources/fhir-config/r5/operations/OperationDefinition-{ResourceType}-{operation-code}.json`

#### 2. Operation Configuration YAML (project format)
Location: `fhir4java-server/src/main/resources/fhir-config/r5/operations/{operation-code}.yml`

#### 3. Operation Handler Class (optional)
Location: `fhir4java-persistence/src/main/java/org/fhirframework/persistence/operation/{OperationName}Handler.java`

---

## Templates

### Operation Configuration YAML (Simple Format)

This is the project's configuration format used to enable/configure operations:

```yaml
operationName: {operation-code}
enabled: true
description: "{Description}"
scopes:
  - TYPE      # /fhir/Patient/$op
  - INSTANCE  # /fhir/Patient/123/$op
  # - SYSTEM  # /fhir/$op
resourceTypes:
  - {ResourceType1}
  - {ResourceType2}
parameters:
  - name: {paramName}
    type: {Type}
    required: {true|false}
    description: "{Parameter description}"
```

### OperationDefinition JSON (Full FHIR Format)

```json
{
  "resourceType": "OperationDefinition",
  "id": "{ResourceType}-{operation-code}",
  "url": "{namespace}OperationDefinition/{ResourceType}-{operation-code}",
  "version": "1.0.0",
  "name": "{OperationName}",
  "title": "{Operation Title}",
  "status": "draft",
  "kind": "operation",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "affectsState": {true|false},
  "code": "{operation-code}",
  "resource": ["{ResourceType1}", "{ResourceType2}"],
  "system": {true|false},
  "type": {true|false},
  "instance": {true|false},
  "parameter": [
    {
      "name": "{inputParamName}",
      "use": "in",
      "min": {min},
      "max": "{max}",
      "documentation": "{Description}",
      "type": "{Type}"
    },
    {
      "name": "{outputParamName}",
      "use": "out",
      "min": {min},
      "max": "{max}",
      "documentation": "{Description}",
      "type": "{Type}"
    }
  ]
}
```

### OperationDefinition with Reference Parameters

For parameters that reference specific resource types:

```json
{
  "name": "patient",
  "use": "in",
  "min": 1,
  "max": "1",
  "documentation": "The patient to operate on",
  "type": "Reference",
  "targetProfile": ["http://hl7.org/fhir/StructureDefinition/Patient"]
}
```

### Operation Handler Class Template

```java
package org.fhirframework.persistence.operation;

import ca.uhn.fhir.context.FhirContext;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler for the ${operation-code} operation.
 * <p>
 * {Description}
 * </p>
 */
@Component
public class {OperationName}Handler implements OperationHandler {

    @Override
    public String getOperationCode() {
        return "{operation-code}";
    }

    @Override
    public boolean supportsSystemLevel() {
        return {system};
    }

    @Override
    public boolean supportsTypeLevel() {
        return {type};
    }

    @Override
    public boolean supportsInstanceLevel() {
        return {instance};
    }

    /**
     * Execute at system level: POST /fhir/${operation-code}
     */
    @Override
    public IBaseResource executeSystem(String body, Map<String, String> params,
                                        FhirVersion version, FhirContext context) {
        // Parse input parameters
        Parameters input = parseParameters(body, context);

        // Execute operation logic
        // ...

        // Return result
        return buildResult(/* ... */);
    }

    /**
     * Execute at type level: POST /fhir/{ResourceType}/${operation-code}
     */
    @Override
    public IBaseResource executeType(String resourceType, String body,
                                      Map<String, String> params,
                                      FhirVersion version, FhirContext context) {
        // Parse input parameters
        Parameters input = parseParameters(body, context);

        // Execute operation logic for resource type
        // ...

        // Return result
        return buildResult(/* ... */);
    }

    /**
     * Execute at instance level: POST /fhir/{ResourceType}/{id}/${operation-code}
     */
    @Override
    public IBaseResource executeInstance(String resourceType, String resourceId,
                                          String body, Map<String, String> params,
                                          FhirVersion version, FhirContext context) {
        // Parse input parameters
        Parameters input = parseParameters(body, context);

        // Load the target resource
        // IBaseResource targetResource = loadResource(resourceType, resourceId, version);

        // Execute operation logic
        // ...

        // Return result
        return buildResult(/* ... */);
    }

    private Parameters parseParameters(String body, FhirContext context) {
        if (body == null || body.isBlank()) {
            return new Parameters();
        }
        return context.newJsonParser().parseResource(Parameters.class, body);
    }

    private Parameters buildResult(OperationOutcome outcome) {
        Parameters result = new Parameters();
        result.addParameter()
            .setName("outcome")
            .setResource(outcome);
        return result;
    }

    private OperationOutcome successOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
            .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
            .setCode(OperationOutcome.IssueType.INFORMATIONAL)
            .setDiagnostics(message);
        return outcome;
    }

    private OperationOutcome errorOutcome(String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
            .setSeverity(OperationOutcome.IssueSeverity.ERROR)
            .setCode(OperationOutcome.IssueType.PROCESSING)
            .setDiagnostics(message);
        return outcome;
    }
}
```

---

## Examples

### Example 1: Patient Deduplication Check

**Operation**: `$find-duplicates`
- **Level**: Type (Patient)
- **Affects State**: false (read-only)
- **Purpose**: Find potential duplicate patients

**Configuration YAML:**
```yaml
operationName: find-duplicates
enabled: true
description: "Find potential duplicate patients based on matching criteria"
scopes:
  - TYPE
resourceTypes:
  - Patient
parameters:
  - name: matchCriteria
    type: code
    required: false
    description: "Matching algorithm: exact, fuzzy, or probabilistic"
  - name: threshold
    type: decimal
    required: false
    description: "Minimum match score (0.0 to 1.0)"
```

**OperationDefinition JSON:**
```json
{
  "resourceType": "OperationDefinition",
  "id": "Patient-find-duplicates",
  "url": "http://example.org/fhir/OperationDefinition/Patient-find-duplicates",
  "version": "1.0.0",
  "name": "FindDuplicates",
  "title": "Find Duplicate Patients",
  "status": "draft",
  "kind": "operation",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Healthcare",
  "description": "Find potential duplicate patients based on matching criteria",
  "affectsState": false,
  "code": "find-duplicates",
  "resource": ["Patient"],
  "system": false,
  "type": true,
  "instance": false,
  "parameter": [
    {
      "name": "matchCriteria",
      "use": "in",
      "min": 0,
      "max": "1",
      "documentation": "Matching algorithm: exact, fuzzy, or probabilistic",
      "type": "code",
      "binding": {
        "strength": "required",
        "valueSet": "http://example.org/fhir/ValueSet/match-criteria"
      }
    },
    {
      "name": "threshold",
      "use": "in",
      "min": 0,
      "max": "1",
      "documentation": "Minimum match score (0.0 to 1.0)",
      "type": "decimal"
    },
    {
      "name": "return",
      "use": "out",
      "min": 1,
      "max": "1",
      "documentation": "Bundle containing potential duplicate patient groups",
      "type": "Bundle"
    }
  ]
}
```

### Example 2: Device Calibration

**Operation**: `$calibrate`
- **Level**: Instance (Device)
- **Affects State**: true (modifies device)
- **Purpose**: Record device calibration

**Configuration YAML:**
```yaml
operationName: calibrate
enabled: true
description: "Record a device calibration event"
scopes:
  - INSTANCE
resourceTypes:
  - Device
parameters:
  - name: calibrationDate
    type: dateTime
    required: true
    description: "Date and time of calibration"
  - name: technician
    type: Reference(Practitioner)
    required: false
    description: "Person who performed calibration"
  - name: notes
    type: string
    required: false
    description: "Calibration notes"
```

**OperationDefinition JSON:**
```json
{
  "resourceType": "OperationDefinition",
  "id": "Device-calibrate",
  "url": "http://example.org/fhir/OperationDefinition/Device-calibrate",
  "version": "1.0.0",
  "name": "Calibrate",
  "title": "Calibrate Device",
  "status": "draft",
  "kind": "operation",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Healthcare",
  "description": "Record a device calibration event",
  "affectsState": true,
  "code": "calibrate",
  "resource": ["Device"],
  "system": false,
  "type": false,
  "instance": true,
  "parameter": [
    {
      "name": "calibrationDate",
      "use": "in",
      "min": 1,
      "max": "1",
      "documentation": "Date and time of calibration",
      "type": "dateTime"
    },
    {
      "name": "technician",
      "use": "in",
      "min": 0,
      "max": "1",
      "documentation": "Person who performed calibration",
      "type": "Reference",
      "targetProfile": ["http://hl7.org/fhir/StructureDefinition/Practitioner"]
    },
    {
      "name": "notes",
      "use": "in",
      "min": 0,
      "max": "1",
      "documentation": "Calibration notes",
      "type": "string"
    },
    {
      "name": "return",
      "use": "out",
      "min": 1,
      "max": "1",
      "documentation": "The updated Device resource",
      "type": "Device"
    }
  ]
}
```

### Example 3: Bulk Data Export

**Operation**: `$export`
- **Level**: System
- **Affects State**: false
- **Purpose**: Export all data (Bulk Data Access)

**Configuration YAML:**
```yaml
operationName: export
enabled: true
description: "Bulk data export operation"
scopes:
  - SYSTEM
  - TYPE
resourceTypes: []
parameters:
  - name: _outputFormat
    type: string
    required: false
    description: "Output format (application/fhir+ndjson)"
  - name: _since
    type: instant
    required: false
    description: "Only include resources modified since this time"
  - name: _type
    type: string
    required: false
    description: "Resource types to export (comma-separated)"
```

---

## Standard FHIR Operations Reference

These operations are defined by HL7 FHIR and may already be implemented:

| Operation | Level | Resource | Description |
|-----------|-------|----------|-------------|
| `$validate` | Type, Instance | All | Validate a resource |
| `$everything` | Instance | Patient, Encounter | Get all related resources |
| `$match` | Type | Patient | Find matching patients |
| `$merge` | Type | Patient | Merge patient records |
| `$expand` | Type, Instance | ValueSet | Expand a value set |
| `$lookup` | Type | CodeSystem | Look up a code |
| `$translate` | Type | ConceptMap | Translate a code |
| `$document` | Instance | Composition | Generate a document |
| `$meta` | Instance | All | Get/update resource metadata |

---

## After Creating the Operation

Remind the user to:

1. **Create/Verify Directory Structure**:
   ```bash
   mkdir -p fhir4java-server/src/main/resources/fhir-config/r5/operations
   ```

2. **Build the Project**:
   ```bash
   ./mvnw clean compile
   ```

3. **Restart the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

4. **Verify Operation in CapabilityStatement**:
   ```bash
   curl http://localhost:8080/fhir/r5/metadata | jq '.rest[0].operation'
   ```

5. **Test the Operation**:
   ```bash
   # Type-level operation
   curl -X POST http://localhost:8080/fhir/r5/Patient/\$operation-code \
     -H "Content-Type: application/fhir+json" \
     -d '{"resourceType": "Parameters", "parameter": [...]}'

   # Instance-level operation
   curl -X POST http://localhost:8080/fhir/r5/Patient/123/\$operation-code \
     -H "Content-Type: application/fhir+json" \
     -d '{"resourceType": "Parameters", "parameter": [...]}'
   ```

6. **Write BDD Tests** (recommended):
   - Create feature file in `fhir4java-server/src/test/resources/features/operations/`
   - Test success cases, validation errors, and edge cases

---

## Operation Handler Interface

If implementing custom logic, register a handler that implements:

```java
public interface OperationHandler {
    String getOperationCode();

    boolean supportsSystemLevel();
    boolean supportsTypeLevel();
    boolean supportsInstanceLevel();

    IBaseResource executeSystem(String body, Map<String, String> params,
                                 FhirVersion version, FhirContext context);

    IBaseResource executeType(String resourceType, String body,
                               Map<String, String> params,
                               FhirVersion version, FhirContext context);

    IBaseResource executeInstance(String resourceType, String resourceId,
                                   String body, Map<String, String> params,
                                   FhirVersion version, FhirContext context);
}
```

The `OperationService` will route requests to the appropriate handler based on the operation code.
