# Define Custom FHIR Resource or Profile

Guide the user through defining a completely custom FHIR resource type OR creating a profile of an existing FHIR resource, generating all required configuration files (StructureDefinition, SearchParameters, OperationDefinitions, and resource configuration YAML).

## Key Differences from `/add-resource`

| Aspect | `/add-resource` | `/define-custom-resource` |
|--------|-----------------|---------------------------|
| Purpose | Configure existing FHIR resource | Define **new** custom resource OR profile |
| StructureDefinition | Not created (uses HL7 standard) | **Creates custom StructureDefinition** |
| SearchParameters | Assumes exist, just selects | **Creates custom SearchParameter files** |
| Data Elements | N/A | **Defines elements, types, cardinality** |
| Backbone Elements | N/A | **Supports nested structures** |
| Operations | N/A | **Optionally creates OperationDefinitions** |

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Definition Type Selection

Ask the user what they want to create:

1. **New Resource Type**: A completely custom resource that extends DomainResource or Resource
   - Example: `CustomMedicalDevice`, `ClinicalTrial`, `ResearchSubject`
   - Use when: The concept doesn't map to any existing FHIR resource

2. **Profile of Existing Resource**: A constrained/extended version of an existing FHIR resource
   - Example: `USCorePatient` (profile of Patient), `VitalSignsObservation` (profile of Observation)
   - Use when: You need to add constraints, extensions, or specific value set bindings to an existing resource

### Step 2: Resource/Profile Identity

Gather the following information:

| Field | Description | Example (New Resource) | Example (Profile) |
|-------|-------------|------------------------|-------------------|
| Name | PascalCase name | `CustomMedicalDevice` | `USCorePatient` |
| URL Namespace | Organization's FHIR URL | `http://example.org/fhir/StructureDefinition/` | `http://hl7.org/fhir/us/core/StructureDefinition/` |
| Description | Purpose of the resource/profile | "Tracks custom medical devices" | "US Core Patient profile" |
| Base Type | What it extends | `DomainResource` or `Resource` | `Patient`, `Observation`, etc. |

For **new resources**, also ask:
- Should it extend `DomainResource` (supports text, contained, extensions) or `Resource` (minimal)?

For **profiles**, also ask:
- Which existing FHIR resource type to constrain? (Patient, Observation, Condition, etc.)

### Step 3: Data Elements Definition

Walk through defining each element interactively. For each element, gather:

| Property | Description | Required |
|----------|-------------|----------|
| Path | Element path (e.g., `deviceId`, `contact.name`) | Yes |
| Type | FHIR data type | Yes |
| Cardinality | `0..1`, `1..1`, `0..*`, `1..*` | Yes |
| Description | Short description | Yes |
| Is Modifier? | Changes meaning of resource if present | No (default: false) |
| Is Summary? | Include in summary view | No (default: false) |
| Value Set Binding | For coded elements (code, Coding, CodeableConcept) | No |

**For BackboneElement (nested structures):**

```
Example backbone element 'contact':
  contact (BackboneElement, 0..*, "Device contacts")
    contact.name (string, 1..1, "Contact name")
    contact.phone (ContactPoint, 0..*, "Contact phone numbers")
    contact.role (code, 0..1, "Contact role", binding: http://example.org/ValueSet/contact-roles)
```

**For profiles (constraining existing elements):**
- Ask: "Constrain existing element or add extension?"
- For constraints: narrow cardinality, add must-support, bind to value set
- For extensions: define extension URL and type

Keep looping until user indicates they're done adding elements. Display a summary:

```
Elements defined for CustomMedicalDevice:
1. deviceIdentifier (Identifier, 1..*, "Unique device identifiers") [Summary]
2. manufacturer (string, 0..1, "Device manufacturer name")
3. status (code, 1..1, "active | inactive | entered-in-error") [Modifier]
4. patient (Reference(Patient), 0..1, "Patient using the device")
5. contact (BackboneElement, 0..*)
   5.1. contact.name (string, 1..1, "Contact name")
   5.2. contact.phone (ContactPoint, 0..*, "Contact phone numbers")
   5.3. contact.role (code, 0..1, "Contact role")
```

### Step 4: Search Parameters Selection

For each searchable element, define:

| Property | Description |
|----------|-------------|
| Name | Search parameter name (lowercase, hyphenated) |
| Type | token, string, date, reference, quantity, uri, number |
| Expression | FHIRPath expression (auto-suggest based on element path) |
| Modifiers | Supported modifiers (e.g., :exact, :contains, :missing) |

**Auto-suggest search parameter type based on element type:**

| Element Type | Suggested Search Type |
|--------------|----------------------|
| string | string |
| code, Coding, CodeableConcept, Identifier | token |
| boolean | token |
| date, dateTime, instant, Period | date |
| Reference | reference |
| Quantity | quantity |
| uri, url, canonical | uri |
| integer, decimal | number |

Display summary:
```
Search Parameters for CustomMedicalDevice:
1. identifier (token) -> CustomMedicalDevice.deviceIdentifier
2. manufacturer (string) -> CustomMedicalDevice.manufacturer
3. status (token) -> CustomMedicalDevice.status
4. patient (reference) -> CustomMedicalDevice.patient
5. contact-name (string) -> CustomMedicalDevice.contact.name
```

### Step 5: Custom Operations (Optional)

Ask if the user wants to define custom operations for this resource.

For each operation, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Name | Operation name (without $) | `calibrate` |
| Level | system, type, instance | `instance` |
| Description | What the operation does | "Calibrate the device" |
| Affects State? | Does it modify data? | `true` |

**Input Parameters:**
| Name | Type | Cardinality | Description |
|------|------|-------------|-------------|
| calibrationDate | dateTime | 1..1 | Date of calibration |
| technician | Reference(Practitioner) | 0..1 | Who performed calibration |

**Output Parameters:**
| Name | Type | Cardinality | Description |
|------|------|-------------|-------------|
| return | OperationOutcome | 1..1 | Result of calibration |

### Step 6: FHIR Version Support

Ask which FHIR versions to support:
- **R5** (recommended, current default)
- **R4B** (for backward compatibility)
- **Both** (with one marked as default)

### Step 7: Interactions Configuration

Ask which FHIR interactions to enable:

| Interaction | Description | Default |
|-------------|-------------|---------|
| `read` | GET /[resource]/[id] | true |
| `vread` | GET /[resource]/[id]/_history/[vid] | true |
| `create` | POST /[resource] | true |
| `update` | PUT /[resource]/[id] | true |
| `patch` | PATCH /[resource]/[id] | false |
| `delete` | DELETE /[resource]/[id] | false |
| `search` | GET /[resource]?params | true |
| `history` | GET /[resource]/[id]/_history | true |

### Step 8: Profile & Validation Rules

Ask about:

1. **Profile Enforcement**: Required or optional?
2. **Custom Invariants**: FHIRPath constraints for business rules

Example invariants:
```
- key: cmd-1
  severity: error
  human: "If contact exists, contact.name must be provided"
  expression: "contact.exists() implies contact.name.exists()"

- key: cmd-2
  severity: warning
  human: "Active devices should have a patient reference"
  expression: "status = 'active' implies patient.exists()"
```

### Step 9: Generate All Artifacts

After gathering all information, generate these files:

#### 1. StructureDefinition JSON
Location: `fhir4java-server/src/main/resources/fhir-config/r5/profiles/StructureDefinition-{ResourceName}.json`

#### 2. SearchParameter JSON files (one per searchable element)
Location: `fhir4java-server/src/main/resources/fhir-config/r5/searchparameters/SearchParameter-{ResourceName}-{param-name}.json`

#### 3. OperationDefinition JSON (if custom operations defined)
Location: `fhir4java-server/src/main/resources/fhir-config/r5/operations/OperationDefinition-{ResourceName}-{operation-name}.json`

#### 4. Resource Configuration YAML
Location: `fhir4java-server/src/main/resources/fhir-config/resources/{resourcename}.yml`

---

## Templates

### StructureDefinition Template (New Resource)

```json
{
  "resourceType": "StructureDefinition",
  "id": "{ResourceName}",
  "url": "{namespace}{ResourceName}",
  "version": "1.0.0",
  "name": "{ResourceName}",
  "title": "{Resource Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "fhirVersion": "5.0.0",
  "kind": "resource",
  "abstract": false,
  "type": "{ResourceName}",
  "baseDefinition": "http://hl7.org/fhir/StructureDefinition/DomainResource",
  "derivation": "specialization",
  "differential": {
    "element": [
      {
        "id": "{ResourceName}",
        "path": "{ResourceName}",
        "short": "{Short description}",
        "definition": "{Full definition}",
        "min": 0,
        "max": "*"
      },
      {
        "id": "{ResourceName}.{elementName}",
        "path": "{ResourceName}.{elementName}",
        "short": "{Element short description}",
        "definition": "{Element definition}",
        "min": {min},
        "max": "{max}",
        "type": [
          {
            "code": "{dataType}"
          }
        ],
        "isSummary": {true|false},
        "isModifier": {true|false}
      }
    ]
  }
}
```

### StructureDefinition Template (Profile)

```json
{
  "resourceType": "StructureDefinition",
  "id": "{ProfileName}",
  "url": "{namespace}{ProfileName}",
  "version": "1.0.0",
  "name": "{ProfileName}",
  "title": "{Profile Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "fhirVersion": "5.0.0",
  "kind": "resource",
  "abstract": false,
  "type": "{BaseResourceType}",
  "baseDefinition": "http://hl7.org/fhir/StructureDefinition/{BaseResourceType}",
  "derivation": "constraint",
  "differential": {
    "element": [
      {
        "id": "{BaseResourceType}",
        "path": "{BaseResourceType}",
        "short": "{Short description}",
        "definition": "{Full definition}"
      },
      {
        "id": "{BaseResourceType}.{elementPath}",
        "path": "{BaseResourceType}.{elementPath}",
        "min": {constrainedMin},
        "max": "{constrainedMax}",
        "mustSupport": true
      }
    ]
  }
}
```

### SearchParameter Template

```json
{
  "resourceType": "SearchParameter",
  "id": "{ResourceName}-{param-name}",
  "url": "{namespace}SearchParameter/{ResourceName}-{param-name}",
  "version": "1.0.0",
  "name": "{paramName}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "code": "{param-name}",
  "base": ["{ResourceName}"],
  "type": "{token|string|date|reference|quantity|uri|number}",
  "expression": "{ResourceName}.{elementPath}",
  "processingMode": "normal"
}
```

### SearchParameter Template (Reference Type)

```json
{
  "resourceType": "SearchParameter",
  "id": "{ResourceName}-{param-name}",
  "url": "{namespace}SearchParameter/{ResourceName}-{param-name}",
  "version": "1.0.0",
  "name": "{paramName}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "code": "{param-name}",
  "base": ["{ResourceName}"],
  "type": "reference",
  "expression": "{ResourceName}.{elementPath}",
  "processingMode": "normal",
  "target": ["{TargetResourceType1}", "{TargetResourceType2}"]
}
```

### OperationDefinition Template

```json
{
  "resourceType": "OperationDefinition",
  "id": "{ResourceName}-{operation-name}",
  "url": "{namespace}OperationDefinition/{ResourceName}-{operation-name}",
  "version": "1.0.0",
  "name": "{OperationName}",
  "title": "{Operation Title}",
  "status": "draft",
  "kind": "operation",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "affectsState": {true|false},
  "code": "{operation-name}",
  "resource": ["{ResourceName}"],
  "system": {true|false},
  "type": {true|false},
  "instance": {true|false},
  "parameter": [
    {
      "name": "{paramName}",
      "use": "in",
      "min": {min},
      "max": "{max}",
      "documentation": "{Description}",
      "type": "{dataType}"
    },
    {
      "name": "return",
      "use": "out",
      "min": 1,
      "max": "1",
      "documentation": "Operation result",
      "type": "OperationOutcome"
    }
  ]
}
```

### Resource Configuration YAML Template

```yaml
# {ResourceName} Resource Configuration
resourceType: {ResourceName}
enabled: true

# Multiple FHIR versions support
fhirVersions:
  - version: R5
    default: true
  # - version: R4B
  #   default: false

# Database schema configuration
schema:
  type: shared
  name: fhir

# Enabled interactions
interactions:
  read: true
  vread: true
  create: true
  update: true
  patch: false
  delete: false
  search: true
  history: true

# Search parameter restrictions (allowlist mode)
searchParameters:
  mode: allowlist
  common:
    - _id
    - _lastUpdated
    - _tag
    - _profile
    - _security
  resourceSpecific:
    # Add all custom search parameters defined for this resource
    - {param-name-1}
    - {param-name-2}

# Required/optional profiles
profiles:
  - url: {namespace}{ResourceName}
    required: true
```

---

## Data Type Reference

### Primitive Types

| Type | Description | Example |
|------|-------------|---------|
| `boolean` | true or false | `true` |
| `integer` | 32-bit signed integer | `42` |
| `string` | Unicode string | `"Hello"` |
| `decimal` | Decimal number | `3.14159` |
| `uri` | URI | `"http://example.org"` |
| `url` | URL | `"https://example.org/page"` |
| `canonical` | Canonical URL | `"http://hl7.org/fhir/StructureDefinition/Patient"` |
| `base64Binary` | Base64 encoded data | `"SGVsbG8="` |
| `instant` | Instant in time | `"2023-01-15T10:30:00Z"` |
| `date` | Date | `"2023-01-15"` |
| `dateTime` | Date and time | `"2023-01-15T10:30:00"` |
| `time` | Time of day | `"10:30:00"` |
| `code` | Code from a value set | `"active"` |
| `oid` | OID | `"urn:oid:1.2.3.4"` |
| `id` | FHIR ID | `"example-patient-1"` |
| `markdown` | Markdown text | `"**Bold** text"` |
| `unsignedInt` | Non-negative integer | `0` |
| `positiveInt` | Positive integer | `1` |
| `uuid` | UUID | `"urn:uuid:c757873d-ec9a-4326-a141-556f43239520"` |

### Complex Types

| Type | Description | Common Use |
|------|-------------|------------|
| `Identifier` | Business identifier | MRN, SSN, device serial numbers |
| `HumanName` | Person's name | Patient, Practitioner names |
| `Address` | Physical/mailing address | Patient address |
| `ContactPoint` | Phone, email, etc. | Contact information |
| `Coding` | Code + system | Individual coded values |
| `CodeableConcept` | Code + text | Coded values with display text |
| `Quantity` | Numeric value with unit | Lab values, measurements |
| `Money` | Currency amount | Costs, charges |
| `Period` | Start/end time range | Encounter period |
| `Range` | Low/high range | Reference ranges |
| `Ratio` | Numerator/denominator | Drug concentrations |
| `Reference` | Reference to another resource | Patient reference |
| `Attachment` | Document/binary content | Images, PDFs |
| `Annotation` | Note with author/time | Clinical notes |
| `Signature` | Digital signature | Signed documents |
| `Age` | Duration as age | Patient age |
| `Duration` | Length of time | Procedure duration |
| `Distance` | Physical distance | Travel distance |
| `Count` | Discrete count | Number of items |
| `SimpleQuantity` | Quantity without comparator | Simple measurements |
| `Timing` | Schedule/timing | Medication timing |
| `Dosage` | Medication dosing | Drug dosage instructions |

### Special Types

| Type | Description |
|------|-------------|
| `BackboneElement` | Nested element with children |
| `Reference(ResourceType)` | Reference to specific resource type |
| `Reference(Resource)` | Reference to any resource |

---

## Search Parameter Type Mapping

| FHIR Data Type | Recommended Search Type | Notes |
|----------------|------------------------|-------|
| `string` | `string` | Supports :exact, :contains |
| `code` | `token` | System optional |
| `Coding` | `token` | system\|code format |
| `CodeableConcept` | `token` | system\|code format |
| `Identifier` | `token` | system\|value format |
| `boolean` | `token` | true\|false |
| `date` | `date` | Supports prefixes (eq, lt, gt, etc.) |
| `dateTime` | `date` | Supports prefixes |
| `instant` | `date` | Supports prefixes |
| `Period` | `date` | Searches within period |
| `Reference` | `reference` | [type]/[id] format |
| `Quantity` | `quantity` | value\|system\|code format |
| `uri`, `url`, `canonical` | `uri` | Full URI match |
| `integer`, `decimal` | `number` | Supports prefixes |

---

## Backbone Element Example

When defining a backbone element, create the parent element first, then its children:

**Resource definition:**
```
CustomMedicalDevice
├── deviceIdentifier (Identifier, 1..*)
├── manufacturer (string, 0..1)
├── status (code, 1..1)
├── contact (BackboneElement, 0..*)     <- Parent backbone element
│   ├── contact.name (string, 1..1)     <- Child elements
│   ├── contact.phone (ContactPoint, 0..*)
│   └── contact.role (code, 0..1)
└── patient (Reference(Patient), 0..1)
```

**StructureDefinition elements for backbone:**
```json
{
  "id": "CustomMedicalDevice.contact",
  "path": "CustomMedicalDevice.contact",
  "short": "Device contacts",
  "min": 0,
  "max": "*",
  "type": [{ "code": "BackboneElement" }]
},
{
  "id": "CustomMedicalDevice.contact.name",
  "path": "CustomMedicalDevice.contact.name",
  "short": "Contact name",
  "min": 1,
  "max": "1",
  "type": [{ "code": "string" }]
},
{
  "id": "CustomMedicalDevice.contact.phone",
  "path": "CustomMedicalDevice.contact.phone",
  "short": "Contact phone numbers",
  "min": 0,
  "max": "*",
  "type": [{ "code": "ContactPoint" }]
},
{
  "id": "CustomMedicalDevice.contact.role",
  "path": "CustomMedicalDevice.contact.role",
  "short": "Contact role",
  "min": 0,
  "max": "1",
  "type": [{ "code": "code" }],
  "binding": {
    "strength": "required",
    "valueSet": "http://example.org/ValueSet/contact-roles"
  }
}
```

---

## Value Set Binding Strengths

| Strength | Description |
|----------|-------------|
| `required` | Must be from the value set (no exceptions) |
| `extensible` | Should be from value set, but can use other codes if needed |
| `preferred` | Recommended to use value set |
| `example` | Value set is just an example |

---

## After Creating the Resource

Remind the user to:

1. **Verify Generated Files**:
   - StructureDefinition in `fhir-config/r5/profiles/`
   - SearchParameter files in `fhir-config/r5/searchparameters/`
   - OperationDefinition files in `fhir-config/r5/operations/` (if applicable)
   - Resource configuration in `fhir-config/resources/`

2. **For R4B Support** (if enabled):
   - Copy relevant files to `fhir-config/r4b/` directories
   - Adjust fhirVersion to "4.3.0" in JSON files

3. **Restart the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

4. **Test the New Resource**:
   - Create: `POST /fhir/r5/{ResourceName}`
   - Read: `GET /fhir/r5/{ResourceName}/{id}`
   - Search: `GET /fhir/r5/{ResourceName}?{param}={value}`
   - Validate: `POST /fhir/r5/{ResourceName}/$validate`

5. **Test Custom Operations** (if defined):
   - Instance level: `POST /fhir/r5/{ResourceName}/{id}/${operationName}`
   - Type level: `POST /fhir/r5/{ResourceName}/${operationName}`

6. **Verify CapabilityStatement**:
   - `GET /fhir/r5/metadata` should include the new resource

---

## Complete Example: CustomMedicalDevice

### Generated Files

**1. StructureDefinition-CustomMedicalDevice.json**
```json
{
  "resourceType": "StructureDefinition",
  "id": "CustomMedicalDevice",
  "url": "http://example.org/fhir/StructureDefinition/CustomMedicalDevice",
  "version": "1.0.0",
  "name": "CustomMedicalDevice",
  "title": "Custom Medical Device",
  "status": "draft",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Organization",
  "description": "A custom resource for tracking medical devices with extended contact information",
  "fhirVersion": "5.0.0",
  "kind": "resource",
  "abstract": false,
  "type": "CustomMedicalDevice",
  "baseDefinition": "http://hl7.org/fhir/StructureDefinition/DomainResource",
  "derivation": "specialization",
  "differential": {
    "element": [
      {
        "id": "CustomMedicalDevice",
        "path": "CustomMedicalDevice",
        "short": "Custom medical device resource",
        "definition": "A custom resource for tracking medical devices with extended contact information",
        "min": 0,
        "max": "*"
      },
      {
        "id": "CustomMedicalDevice.deviceIdentifier",
        "path": "CustomMedicalDevice.deviceIdentifier",
        "short": "Unique device identifiers",
        "definition": "Business identifiers for the device",
        "min": 1,
        "max": "*",
        "type": [{ "code": "Identifier" }],
        "isSummary": true
      },
      {
        "id": "CustomMedicalDevice.manufacturer",
        "path": "CustomMedicalDevice.manufacturer",
        "short": "Device manufacturer name",
        "definition": "The name of the device manufacturer",
        "min": 0,
        "max": "1",
        "type": [{ "code": "string" }]
      },
      {
        "id": "CustomMedicalDevice.status",
        "path": "CustomMedicalDevice.status",
        "short": "active | inactive | entered-in-error",
        "definition": "The status of the device",
        "min": 1,
        "max": "1",
        "type": [{ "code": "code" }],
        "isModifier": true,
        "isSummary": true,
        "binding": {
          "strength": "required",
          "valueSet": "http://example.org/ValueSet/device-status"
        }
      },
      {
        "id": "CustomMedicalDevice.patient",
        "path": "CustomMedicalDevice.patient",
        "short": "Patient using the device",
        "definition": "Reference to the patient using this device",
        "min": 0,
        "max": "1",
        "type": [{
          "code": "Reference",
          "targetProfile": ["http://hl7.org/fhir/StructureDefinition/Patient"]
        }]
      },
      {
        "id": "CustomMedicalDevice.contact",
        "path": "CustomMedicalDevice.contact",
        "short": "Device contacts",
        "definition": "Contact information for the device",
        "min": 0,
        "max": "*",
        "type": [{ "code": "BackboneElement" }]
      },
      {
        "id": "CustomMedicalDevice.contact.name",
        "path": "CustomMedicalDevice.contact.name",
        "short": "Contact name",
        "min": 1,
        "max": "1",
        "type": [{ "code": "string" }]
      },
      {
        "id": "CustomMedicalDevice.contact.phone",
        "path": "CustomMedicalDevice.contact.phone",
        "short": "Contact phone numbers",
        "min": 0,
        "max": "*",
        "type": [{ "code": "ContactPoint" }]
      },
      {
        "id": "CustomMedicalDevice.contact.role",
        "path": "CustomMedicalDevice.contact.role",
        "short": "Contact role",
        "min": 0,
        "max": "1",
        "type": [{ "code": "code" }]
      }
    ]
  }
}
```

**2. SearchParameter-CustomMedicalDevice-identifier.json**
```json
{
  "resourceType": "SearchParameter",
  "id": "CustomMedicalDevice-identifier",
  "url": "http://example.org/fhir/SearchParameter/CustomMedicalDevice-identifier",
  "version": "1.0.0",
  "name": "identifier",
  "status": "draft",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Organization",
  "description": "Search by device identifier",
  "code": "identifier",
  "base": ["CustomMedicalDevice"],
  "type": "token",
  "expression": "CustomMedicalDevice.deviceIdentifier",
  "processingMode": "normal"
}
```

**3. SearchParameter-CustomMedicalDevice-status.json**
```json
{
  "resourceType": "SearchParameter",
  "id": "CustomMedicalDevice-status",
  "url": "http://example.org/fhir/SearchParameter/CustomMedicalDevice-status",
  "version": "1.0.0",
  "name": "status",
  "status": "draft",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Organization",
  "description": "Search by device status",
  "code": "status",
  "base": ["CustomMedicalDevice"],
  "type": "token",
  "expression": "CustomMedicalDevice.status",
  "processingMode": "normal"
}
```

**4. SearchParameter-CustomMedicalDevice-patient.json**
```json
{
  "resourceType": "SearchParameter",
  "id": "CustomMedicalDevice-patient",
  "url": "http://example.org/fhir/SearchParameter/CustomMedicalDevice-patient",
  "version": "1.0.0",
  "name": "patient",
  "status": "draft",
  "experimental": true,
  "date": "2024-01-15",
  "publisher": "Example Organization",
  "description": "Search by patient reference",
  "code": "patient",
  "base": ["CustomMedicalDevice"],
  "type": "reference",
  "expression": "CustomMedicalDevice.patient",
  "processingMode": "normal",
  "target": ["Patient"]
}
```

**5. custommedicaldevice.yml**
```yaml
# CustomMedicalDevice Resource Configuration
resourceType: CustomMedicalDevice
enabled: true

fhirVersions:
  - version: R5
    default: true

schema:
  type: shared
  name: fhir

interactions:
  read: true
  vread: true
  create: true
  update: true
  patch: false
  delete: false
  search: true
  history: true

searchParameters:
  mode: allowlist
  common:
    - _id
    - _lastUpdated
    - _tag
    - _profile
    - _security
  resourceSpecific:
    - identifier
    - manufacturer
    - status
    - patient
    - contact-name

profiles:
  - url: http://example.org/fhir/StructureDefinition/CustomMedicalDevice
    required: true
```
