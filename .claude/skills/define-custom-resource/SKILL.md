# Define Custom FHIR Resource

Guide the user through defining a completely custom FHIR resource type, generating all required configuration files (StructureDefinition, SearchParameters, OperationDefinitions, and resource configuration YAML).

## Scope

This skill is for creating **new custom resource types** that extend DomainResource or Resource.

| Use This Skill For | Use `/define-fhir-profile` Instead |
|--------------------|-------------------------------------|
| New resource types (e.g., `CustomMedicalDevice`) | Profiles of existing resources |
| Concepts not in FHIR spec | Constraining Patient, Observation, etc. |
| `derivation: specialization` | `derivation: constraint` |
| Requires code generation | No code generation needed |

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

---

### Step 1: Resource Identity

Gather basic information about the custom resource:

| Field | Description | Example |
|-------|-------------|---------|
| Name | PascalCase resource name | `CustomMedicalDevice` |
| URL Namespace | Organization's FHIR URL | `http://example.org/fhir/StructureDefinition/` |
| Description | Purpose of the resource | "Tracks custom medical devices assigned to patients" |
| Base Type | `DomainResource` (recommended) or `Resource` | `DomainResource` |

**Ask the user:**
```
What is the name of your custom resource? (PascalCase, e.g., CustomMedicalDevice)

What is your organization's FHIR namespace URL?
(e.g., http://example.org/fhir/StructureDefinition/)

Briefly describe what this resource represents:
```

---

### Step 2: Simple Data Entry Form

Collect business requirements in plain language. Users do NOT need to know FHIR data types.

**Present this form to the user:**

```
═══════════════════════════════════════════════════════════════════════════════
 STEP 2: Define Your Data Elements (Plain Language)
═══════════════════════════════════════════════════════════════════════════════

Describe the data elements this resource needs to capture. Use plain business
language - I'll figure out the appropriate FHIR types!

FORMAT: element name - description (notes about required/multiple/allowed values)

EXAMPLE:
  1. Device identifier - unique ID for the device (required, can have multiple)
  2. Manufacturer - company that made the device
  3. Status - active, inactive, or entered-in-error (required)
  4. Patient - which patient is using this device
  5. Contact person - name, phone, email, and role (can have multiple contacts)

───────────────────────────────────────────────────────────────────────────────
YOUR ELEMENTS:
(List your data elements below, one per line)



───────────────────────────────────────────────────────────────────────────────
ADDITIONAL INSTRUCTIONS (optional):
(Any special requirements, allowed values for codes, business rules, etc.)



═══════════════════════════════════════════════════════════════════════════════
```

**Encourage the user to describe elements naturally:**
- "Device serial number (required, can have multiple)"
- "Status - active, inactive, or error (required)"
- "Contact info including name, phone, and role"
- "When the device was assigned"
- "Notes about the device"

---

### Step 3: AI Analysis and Structure Generation

After receiving the user's input, analyze it to infer FHIR types, cardinality, and bindings.

#### Data Type Inference Rules

| Pattern in Description | Inferred FHIR Type |
|------------------------|-------------------|
| "identifier", "ID", "serial number", "UDI" | `Identifier` |
| "name" (of a person) | `HumanName` |
| "name" (of a thing/organization) | `string` |
| "date" (date only, no time) | `date` |
| "date and time", "timestamp", "when" | `dateTime` |
| "status", "state" + specific values listed | `code` + binding |
| "type", "category", "kind" + values | `CodeableConcept` or `code` |
| "patient", "subject" | `Reference(Patient)` |
| "practitioner", "provider", "doctor", "clinician" | `Reference(Practitioner)` |
| "organization", "facility", "hospital" | `Reference(Organization)` |
| "encounter", "visit" | `Reference(Encounter)` |
| "phone", "telephone", "mobile", "fax" | `ContactPoint` |
| "email" | `ContactPoint` |
| "address", "location" (postal) | `Address` |
| "notes", "comments", "description" (long text) | `string` or `markdown` |
| "amount", "cost", "price", "charge" | `Money` |
| "quantity", "measurement" + unit | `Quantity` |
| "count", "number of" (integer) | `integer` |
| "percentage", "percent" | `integer` (0-100) or `decimal` |
| "yes/no", "true/false", "flag", "is..." | `boolean` |
| "URL", "link", "website" | `url` |
| "file", "document", "image", "attachment" | `Attachment` |
| "period", "from/to", "start/end dates" | `Period` |
| "range", "min/max", "low/high" | `Range` |
| nested elements with sub-items | `BackboneElement` |
| reference to other resource | `Reference(ResourceType)` |

#### Cardinality Inference Rules

| Pattern in Description | Inferred Cardinality |
|------------------------|---------------------|
| "required", "must have", "mandatory" | `1..1` or `1..*` |
| "can have multiple", "list of", "multiple" | `0..*` or `1..*` |
| "one or more" | `1..*` |
| "optional" or no qualifier | `0..1` |
| "at least one" | `1..*` |
| "exactly one" | `1..1` |
| "zero or more" | `0..*` |

#### Value Set Binding Detection

| Pattern | Binding Strength |
|---------|------------------|
| "only allow:", "must be:", "restricted to:" | `required` |
| "should be:", "preferred:" | `preferred` |
| "examples:", "such as:" | `example` |
| Specific values listed (e.g., "active, inactive, error") | `required` |

#### Modifier Element Detection

| Pattern | Mark as Modifier |
|---------|------------------|
| "status" with values affecting interpretation | Yes |
| "entered-in-error", "cancelled", "deleted" in values | Yes |
| "negation", "refuted", "not" semantics | Yes |

#### Summary Element Detection

| Pattern | Mark as Summary |
|---------|-----------------|
| "identifier", "ID" | Yes |
| "status" | Yes |
| Primary reference (e.g., "patient", "subject") | Yes |
| "name" (primary name) | Yes |

---

### Step 4: Visual Layout Display

After analysis, display the inferred structure in a visual table:

```
═══════════════════════════════════════════════════════════════════════════════════════════
 {ResourceName} - Data Elements ({count} defined)
═══════════════════════════════════════════════════════════════════════════════════════════

┌─────┬────────────────────────┬──────────────────┬────────┬──────────────────────────────┐
│  #  │ Element                │ Type             │ Card   │ Binding / Notes              │
├─────┼────────────────────────┼──────────────────┼────────┼──────────────────────────────┤
│  1  │ deviceIdentifier       │ Identifier       │ 1..*   │ [S]                          │
│  2  │ manufacturer           │ string           │ 0..1   │                              │
│  3  │ status                 │ code             │ 1..1   │ device-status [R][M]         │
│     │                        │                  │        │ → active|inactive|error      │
│  4  │ modelNumber            │ string           │ 0..1   │                              │
│  5  │ patient                │ Reference(Pat)   │ 0..1   │ [S]                          │
│  6  │ assignedDate           │ date             │ 0..1   │                              │
│  7  │ expirationDate         │ date             │ 0..1   │                              │
│  8  │ ├─ contact             │ BackboneElement  │ 0..*   │                              │
│  9  │ │  ├─ name             │ string           │ 1..1   │                              │
│ 10  │ │  ├─ phone            │ ContactPoint     │ 0..*   │                              │
│ 11  │ │  ├─ email            │ ContactPoint     │ 0..1   │                              │
│ 12  │ │  └─ role             │ code             │ 0..1   │ contact-role [R]             │
│     │                        │                  │        │ → manufacturer|tech|support  │
│ 13  │ notes                  │ string           │ 0..1   │                              │
└─────┴────────────────────────┴──────────────────┴────────┴──────────────────────────────┘

Legend: [S]=Summary  [M]=Modifier  [R]=Required binding  [E]=Extensible  [P]=Preferred

───────────────────────────────────────────────────────────────────────────────────────────
 REVIEW & REFINE
───────────────────────────────────────────────────────────────────────────────────────────
 Commands:
   Edit <#> type <new-type>     │ Change data type      │ Edit 13 type markdown
   Edit <#> card <new-card>     │ Change cardinality    │ Edit 2 card 0..*
   Edit <#> binding <name>      │ Add/change binding    │ Edit 4 binding model-codes
   Edit <#> summary             │ Toggle summary flag   │ Edit 6 summary
   Edit <#> modifier            │ Toggle modifier flag  │ Edit 3 modifier
   Add                          │ Add new element       │ Add
   Add child to <#>             │ Add to backbone       │ Add child to 8
   Remove <#>                   │ Remove element        │ Remove 4
   Show <#>                     │ Show element details  │ Show 3
   Done                         │ Proceed to next step  │ Done
───────────────────────────────────────────────────────────────────────────────────────────

What would you like to adjust? (or type "Done" to proceed)
```

#### Interactive Refinement

When user issues commands, update the visual display and confirm changes:

**Edit type example:**
```
User: Edit 13 type markdown

Updated element #13:
  notes: string → markdown

[Display updated table]
```

**Add element example:**
```
User: Add

What is the element name? > warrantyExpiration
Brief description? > When the device warranty expires
Is it required? (y/n) > n
Can there be multiple? (y/n) > n

Inferred: warrantyExpiration (date, 0..1)

[Display updated table with new element]
```

**Add child to backbone example:**
```
User: Add child to 8

Adding child element to 'contact' backbone...
What is the element name? > organization
Brief description? > Contact's organization
Is it required? (y/n) > n

Inferred: contact.organization (string, 0..1)

[Display updated table]
```

**Remove element example:**
```
User: Remove 4

Remove element #4 (modelNumber)? This cannot be undone. (y/n) > y

Removed: modelNumber

[Display updated table with renumbered elements]
```

**Show element details:**
```
User: Show 3

Element #3: status
  Path: {ResourceName}.status
  Type: code
  Cardinality: 1..1
  Summary: Yes
  Modifier: Yes
  Binding:
    Strength: required
    ValueSet: http://example.org/fhir/ValueSet/device-status
    Values: active | inactive | entered-in-error
  Description: Current status of the device
```

---

### Step 5: Search Parameters

After elements are finalized, determine which elements should be searchable.

**Display searchable elements suggestion:**

```
═══════════════════════════════════════════════════════════════════════════════════════════
 SEARCH PARAMETERS
═══════════════════════════════════════════════════════════════════════════════════════════

Based on your elements, I recommend these search parameters:

┌─────┬──────────────────┬────────────┬─────────────────────────────────────────────────┐
│ [x] │ Parameter        │ Type       │ Expression                                      │
├─────┼──────────────────┼────────────┼─────────────────────────────────────────────────┤
│ [x] │ identifier       │ token      │ {Resource}.deviceIdentifier                     │
│ [x] │ status           │ token      │ {Resource}.status                               │
│ [x] │ patient          │ reference  │ {Resource}.patient                              │
│ [ ] │ manufacturer     │ string     │ {Resource}.manufacturer                         │
│ [ ] │ assigned-date    │ date       │ {Resource}.assignedDate                         │
│ [ ] │ expiration-date  │ date       │ {Resource}.expirationDate                       │
│ [ ] │ contact-name     │ string     │ {Resource}.contact.name                         │
└─────┴──────────────────┴────────────┴─────────────────────────────────────────────────┘

[x] = Recommended (commonly searched)  [ ] = Optional

Commands:
  Toggle <param>    │ Enable/disable parameter  │ Toggle manufacturer
  Add <name>        │ Add custom parameter      │ Add contact-role
  Done              │ Proceed to next step      │ Done

Which search parameters do you want to enable?
```

#### Search Parameter Type Mapping

| Element Type | Search Type |
|--------------|-------------|
| `Identifier` | `token` |
| `code`, `Coding`, `CodeableConcept` | `token` |
| `boolean` | `token` |
| `string` | `string` |
| `date`, `dateTime`, `instant` | `date` |
| `Period` | `date` |
| `Reference` | `reference` |
| `Quantity` | `quantity` |
| `integer`, `decimal` | `number` |
| `uri`, `url`, `canonical` | `uri` |

---

### Step 6: Custom Operations (Optional)

Ask if the user wants to define custom operations:

```
═══════════════════════════════════════════════════════════════════════════════════════════
 CUSTOM OPERATIONS (Optional)
═══════════════════════════════════════════════════════════════════════════════════════════

Do you want to define any custom operations for this resource?

Examples:
  - $calibrate (for devices)
  - $assign (assign to patient)
  - $retire (mark as retired)

Options:
  Add         │ Define a new operation
  Skip        │ No custom operations needed

Your choice:
```

For each operation, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Name | Operation name (without $) | `calibrate` |
| Level | instance, type, or system | `instance` |
| Description | What it does | "Calibrate the device" |
| Affects State? | Does it modify data? | `true` |
| Input Parameters | Name, type, cardinality | `calibrationDate: dateTime, 1..1` |
| Output Parameters | Name, type, cardinality | `return: OperationOutcome, 1..1` |

---

### Step 7: FHIR Version & Interactions

```
═══════════════════════════════════════════════════════════════════════════════════════════
 FHIR VERSION & INTERACTIONS
═══════════════════════════════════════════════════════════════════════════════════════════

Which FHIR versions should this resource support?
  [x] R5 (recommended, default)
  [ ] R4B (backward compatibility)

Which interactions should be enabled?
  [x] read      GET /{Resource}/{id}
  [x] vread     GET /{Resource}/{id}/_history/{vid}
  [x] create    POST /{Resource}
  [x] update    PUT /{Resource}/{id}
  [ ] patch     PATCH /{Resource}/{id}
  [ ] delete    DELETE /{Resource}/{id}
  [x] search    GET /{Resource}?params
  [x] history   GET /{Resource}/{id}/_history

Toggle any to change, or type "Done" to proceed.
```

---

### Step 8: Generate All Artifacts

After gathering all information, generate these files:

#### Files to Generate

| File | Location |
|------|----------|
| StructureDefinition | `fhir-config/r5/profiles/StructureDefinition-{ResourceName}.json` |
| SearchParameter(s) | `fhir-config/r5/searchparameters/SearchParameter-{ResourceName}-{param}.json` |
| OperationDefinition(s) | `fhir-config/r5/operations/OperationDefinition-{ResourceName}-{op}.json` |
| Resource Config | `fhir-config/resources/{resourcename}.yml` |
| ValueSet(s) | `fhir-config/r5/valuesets/ValueSet-{name}.json` (if bindings defined) |

**Confirm before generating:**

```
═══════════════════════════════════════════════════════════════════════════════════════════
 SUMMARY - Ready to Generate
═══════════════════════════════════════════════════════════════════════════════════════════

Resource: CustomMedicalDevice
Namespace: http://example.org/fhir/StructureDefinition/
Elements: 13
Search Parameters: 6
Custom Operations: 1 ($calibrate)
FHIR Versions: R5

Files to create:
  1. StructureDefinition-CustomMedicalDevice.json
  2. SearchParameter-CustomMedicalDevice-identifier.json
  3. SearchParameter-CustomMedicalDevice-status.json
  4. SearchParameter-CustomMedicalDevice-patient.json
  5. SearchParameter-CustomMedicalDevice-manufacturer.json
  6. SearchParameter-CustomMedicalDevice-assigned-date.json
  7. SearchParameter-CustomMedicalDevice-contact-name.json
  8. OperationDefinition-CustomMedicalDevice-calibrate.json
  9. ValueSet-device-status.json
  10. ValueSet-contact-role.json
  11. custommedicaldevice.yml

Proceed with generation? (y/n)
```

---

## Templates

### CRITICAL: Snapshot vs Differential

**HAPI FHIR validators REQUIRE snapshot elements for proper validation.** Always generate both sections:

| Section | Purpose | Contents |
|---------|---------|----------|
| `snapshot` | Complete expanded view | ALL elements including inherited base elements |
| `differential` | Changes from base | ONLY custom/modified elements |

**For new resources extending DomainResource, the snapshot MUST include:**
1. Resource root element
2. DomainResource base elements: `id`, `meta`, `implicitRules`, `language`, `text`, `contained`, `extension`, `modifierExtension`
3. All custom elements
4. For each BackboneElement: its base elements (`id`, `extension`, `modifierExtension`) plus custom children

### StructureDefinition Template

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
  "snapshot": {
    "element": [
      {
        "id": "{ResourceName}",
        "path": "{ResourceName}",
        "short": "{Short description}",
        "definition": "{Full definition}",
        "min": 0,
        "max": "*"
      },
      // === DomainResource Base Elements (REQUIRED) ===
      {
        "id": "{ResourceName}.id",
        "path": "{ResourceName}.id",
        "short": "Logical id of this artifact",
        "definition": "The logical id of the resource.",
        "min": 0,
        "max": "1",
        "type": [{ "code": "http://hl7.org/fhirpath/System.String", "extension": [{ "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type", "valueUrl": "id" }] }],
        "isSummary": true
      },
      {
        "id": "{ResourceName}.meta",
        "path": "{ResourceName}.meta",
        "short": "Metadata about the resource",
        "min": 0,
        "max": "1",
        "type": [{ "code": "Meta" }],
        "isSummary": true
      },
      {
        "id": "{ResourceName}.implicitRules",
        "path": "{ResourceName}.implicitRules",
        "short": "A set of rules under which this content was created",
        "min": 0,
        "max": "1",
        "type": [{ "code": "uri" }],
        "isModifier": true,
        "isSummary": true
      },
      {
        "id": "{ResourceName}.language",
        "path": "{ResourceName}.language",
        "short": "Language of the resource content",
        "min": 0,
        "max": "1",
        "type": [{ "code": "code" }],
        "binding": { "strength": "required", "valueSet": "http://hl7.org/fhir/ValueSet/all-languages|5.0.0" }
      },
      {
        "id": "{ResourceName}.text",
        "path": "{ResourceName}.text",
        "short": "Text summary of the resource",
        "min": 0,
        "max": "1",
        "type": [{ "code": "Narrative" }]
      },
      {
        "id": "{ResourceName}.contained",
        "path": "{ResourceName}.contained",
        "short": "Contained, inline Resources",
        "min": 0,
        "max": "*",
        "type": [{ "code": "Resource" }]
      },
      {
        "id": "{ResourceName}.extension",
        "path": "{ResourceName}.extension",
        "short": "Additional content defined by implementations",
        "min": 0,
        "max": "*",
        "type": [{ "code": "Extension" }]
      },
      {
        "id": "{ResourceName}.modifierExtension",
        "path": "{ResourceName}.modifierExtension",
        "short": "Extensions that cannot be ignored",
        "min": 0,
        "max": "*",
        "type": [{ "code": "Extension" }],
        "isModifier": true
      },
      // === Custom Elements ===
      {
        "id": "{ResourceName}.{elementName}",
        "path": "{ResourceName}.{elementName}",
        "short": "{Element short description}",
        "definition": "{Element definition}",
        "min": {min},
        "max": "{max}",
        "type": [{ "code": "{dataType}" }],
        "isSummary": {true|false},
        "isModifier": {true|false}
      }
    ]
  },
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
      // === ONLY Custom Elements (no inherited base elements) ===
      {
        "id": "{ResourceName}.{elementName}",
        "path": "{ResourceName}.{elementName}",
        "short": "{Element short description}",
        "definition": "{Element definition}",
        "min": {min},
        "max": "{max}",
        "type": [{ "code": "{dataType}" }],
        "isSummary": {true|false},
        "isModifier": {true|false}
      }
    ]
  }
}
```

### BackboneElement in Snapshot

For each BackboneElement, include its base elements in the snapshot:

```json
// The backbone element itself
{
  "id": "{ResourceName}.{backbone}",
  "path": "{ResourceName}.{backbone}",
  "short": "{description}",
  "min": 0,
  "max": "*",
  "type": [{ "code": "BackboneElement" }]
},
// BackboneElement base elements (REQUIRED in snapshot)
{
  "id": "{ResourceName}.{backbone}.id",
  "path": "{ResourceName}.{backbone}.id",
  "short": "Unique id for inter-element referencing",
  "min": 0,
  "max": "1",
  "type": [{ "code": "http://hl7.org/fhirpath/System.String", "extension": [{ "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type", "valueUrl": "string" }] }]
},
{
  "id": "{ResourceName}.{backbone}.extension",
  "path": "{ResourceName}.{backbone}.extension",
  "short": "Additional content defined by implementations",
  "min": 0,
  "max": "*",
  "type": [{ "code": "Extension" }]
},
{
  "id": "{ResourceName}.{backbone}.modifierExtension",
  "path": "{ResourceName}.{backbone}.modifierExtension",
  "short": "Extensions that cannot be ignored",
  "min": 0,
  "max": "*",
  "type": [{ "code": "Extension" }],
  "isModifier": true
},
// Custom backbone children
{
  "id": "{ResourceName}.{backbone}.{child}",
  "path": "{ResourceName}.{backbone}.{child}",
  "short": "{description}",
  "min": {min},
  "max": "{max}",
  "type": [{ "code": "{dataType}" }]
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
  "target": ["{TargetResourceType}"]
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
  "system": false,
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

### ValueSet Template

```json
{
  "resourceType": "ValueSet",
  "id": "{valueset-name}",
  "url": "{namespace}ValueSet/{valueset-name}",
  "version": "1.0.0",
  "name": "{ValueSetName}",
  "title": "{ValueSet Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "{Description}",
  "compose": {
    "include": [
      {
        "system": "{namespace}CodeSystem/{codesystem-name}",
        "concept": [
          { "code": "{code1}", "display": "{Display 1}" },
          { "code": "{code2}", "display": "{Display 2}" }
        ]
      }
    ]
  }
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
    - {param-name-1}
    - {param-name-2}

# Required/optional profiles
profiles:
  - url: {namespace}{ResourceName}
    required: true
```

---

## Data Type Quick Reference

### Primitive Types

| Type | Description | Example |
|------|-------------|---------|
| `boolean` | true or false | `true` |
| `integer` | 32-bit signed integer | `42` |
| `string` | Unicode string | `"Hello"` |
| `decimal` | Decimal number | `3.14159` |
| `uri` | URI | `"http://example.org"` |
| `url` | URL | `"https://example.org/page"` |
| `date` | Date (YYYY-MM-DD) | `"2024-01-15"` |
| `dateTime` | Date and time | `"2024-01-15T10:30:00"` |
| `time` | Time of day | `"10:30:00"` |
| `instant` | Instant in time (with timezone) | `"2024-01-15T10:30:00Z"` |
| `code` | Code from a value set | `"active"` |
| `markdown` | Markdown text | `"**Bold** text"` |

### Complex Types

| Type | Description | Common Use |
|------|-------------|------------|
| `Identifier` | Business identifier | MRN, serial numbers |
| `HumanName` | Person's name | Patient, Practitioner names |
| `Address` | Physical/mailing address | Patient address |
| `ContactPoint` | Phone, email, etc. | Contact information |
| `Coding` | Code + system | Individual coded values |
| `CodeableConcept` | Code + text | Coded values with display |
| `Quantity` | Numeric value with unit | Measurements |
| `Money` | Currency amount | Costs |
| `Period` | Start/end time range | Date ranges |
| `Range` | Low/high range | Reference ranges |
| `Reference` | Reference to another resource | Patient reference |
| `Attachment` | Document/binary content | Images, PDFs |
| `Annotation` | Note with author/time | Clinical notes |

### Special Types

| Type | Description |
|------|-------------|
| `BackboneElement` | Nested element with children |
| `Reference(ResourceType)` | Reference to specific resource type |

---

## After Creating the Resource

Remind the user to:

1. **Rebuild the Project**:
   ```bash
   ./mvnw clean install -pl fhir4java-core,fhir4java-server
   ```

2. **Restart the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

3. **Test the New Resource**:
   ```bash
   # Create
   curl -X POST http://localhost:8080/fhir/r5/{ResourceName} \
     -H "Content-Type: application/fhir+json" \
     -d '{"resourceType":"{ResourceName}", ...}'

   # Read
   curl http://localhost:8080/fhir/r5/{ResourceName}/{id}

   # Search
   curl "http://localhost:8080/fhir/r5/{ResourceName}?{param}={value}"

   # Validate
   curl -X POST http://localhost:8080/fhir/r5/{ResourceName}/\$validate \
     -H "Content-Type: application/fhir+json" \
     -d '{"resourceType":"{ResourceName}", ...}'
   ```

4. **Verify CapabilityStatement**:
   ```bash
   curl http://localhost:8080/fhir/r5/metadata | jq '.rest[0].resource[] | select(.type=="{ResourceName}")'
   ```
