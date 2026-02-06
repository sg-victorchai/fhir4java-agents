# Define Value Set & Code System

Guide the user through creating custom value sets and code systems for coded elements in FHIR resources.

## Overview

Value sets and code systems are essential for:
- **Standardizing** coded data (statuses, categories, types)
- **Validating** that codes come from allowed sets
- **Providing** display names for UI dropdowns
- **Ensuring** interoperability across systems

## When to Use

- When you need a custom list of allowed codes for a resource element
- When creating organization-specific code systems
- When binding coded elements to specific value sets in profiles
- When you need to extend or restrict standard FHIR value sets

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Determine What to Create

Ask the user what they need:

| Option | Description | When to Use |
|--------|-------------|-------------|
| **CodeSystem only** | Define new codes | Creating organization-specific codes |
| **ValueSet only** | Compose from existing codes | Combining codes from standard systems |
| **Both** | New codes + ValueSet | New codes that need a value set wrapper |

**Typical combinations:**
- Custom codes: Create CodeSystem + ValueSet that includes all codes from it
- Standard codes subset: Create ValueSet that filters from HL7/SNOMED/LOINC
- Mixed: ValueSet that includes custom CodeSystem + standard codes

### Step 2: Code System Identity (if creating CodeSystem)

Gather information:

| Field | Description | Example |
|-------|-------------|---------|
| Name | PascalCase identifier | `DischargeDisposition` |
| URL | Unique canonical URL | `http://example.org/fhir/CodeSystem/discharge-disposition` |
| Title | Human-readable title | "Discharge Disposition Codes" |
| Description | What these codes represent | "Codes for patient discharge destinations" |
| Publisher | Organization name | "Example Healthcare System" |
| Version | Semantic version | `1.0.0` |

### Step 3: Define Codes

For each code, gather:

| Property | Description | Required | Example |
|----------|-------------|----------|---------|
| `code` | The code value (lowercase, hyphenated) | Yes | `home` |
| `display` | Human-readable name | Yes | "Home" |
| `definition` | Full definition | No | "Patient discharged to home" |
| `parent` | Parent code (for hierarchies) | No | `community` |

**Example codes:**
```
Discharge Disposition Codes:
1. code: home
   display: Home
   definition: Patient discharged to home setting

2. code: snf
   display: Skilled Nursing Facility
   definition: Patient transferred to skilled nursing facility

3. code: rehab
   display: Rehabilitation Facility
   definition: Patient transferred to rehabilitation center

4. code: expired
   display: Expired
   definition: Patient expired during hospital stay

5. code: ama
   display: Against Medical Advice
   definition: Patient left against medical advice
```

Keep looping until user indicates they're done adding codes.

Display a summary:
```
CodeSystem: DischargeDisposition (5 codes)
URL: http://example.org/fhir/CodeSystem/discharge-disposition

Codes:
  1. home - Home
  2. snf - Skilled Nursing Facility
  3. rehab - Rehabilitation Facility
  4. expired - Expired
  5. ama - Against Medical Advice
```

### Step 4: Value Set Identity (if creating ValueSet)

Gather information:

| Field | Description | Example |
|-------|-------------|---------|
| Name | PascalCase identifier | `DischargeDispositionValueSet` |
| URL | Unique canonical URL | `http://example.org/fhir/ValueSet/discharge-disposition` |
| Title | Human-readable title | "Discharge Disposition Value Set" |
| Description | What the value set contains | "Allowed discharge disposition codes" |

### Step 5: Value Set Composition

Ask how to compose the value set:

#### Option A: Include All Codes from CodeSystem
```yaml
compose:
  include:
    - system: http://example.org/fhir/CodeSystem/discharge-disposition
```

#### Option B: Include Specific Codes
```yaml
compose:
  include:
    - system: http://example.org/fhir/CodeSystem/discharge-disposition
      concept:
        - code: home
        - code: snf
        - code: rehab
```

#### Option C: Include from Multiple Systems
```yaml
compose:
  include:
    - system: http://example.org/fhir/CodeSystem/discharge-disposition
    - system: http://terminology.hl7.org/CodeSystem/discharge-disposition
      concept:
        - code: home
        - code: other-hcf
```

#### Option D: Filter by Properties
```yaml
compose:
  include:
    - system: http://snomed.info/sct
      filter:
        - property: concept
          op: is-a
          value: "182828003"  # Discharge to home
```

### Step 6: Binding Information

Ask about the intended use:

| Property | Description | Options |
|----------|-------------|---------|
| Target Resource | Which resource uses this | `Encounter`, `Patient`, etc. |
| Target Element | Which element to bind | `Encounter.hospitalization.dischargeDisposition` |
| Binding Strength | How strict the binding is | See below |

**Binding Strengths:**

| Strength | Description | Use When |
|----------|-------------|----------|
| `required` | Must be from value set, no exceptions | Critical coded elements |
| `extensible` | Prefer value set, but can use others if needed | Standard with flexibility |
| `preferred` | Recommended but not required | Suggestions |
| `example` | Just examples | Documentation only |

### Step 7: Generate Files

After gathering all information, generate:

#### 1. CodeSystem JSON (if applicable)
Location: `fhir4java-server/src/main/resources/fhir-config/r5/terminology/CodeSystem-{name}.json`

#### 2. ValueSet JSON
Location: `fhir4java-server/src/main/resources/fhir-config/r5/terminology/ValueSet-{name}.json`

#### 3. Update Profile (if binding to element)
Add binding to the relevant StructureDefinition

---

## Templates

### CodeSystem Template

```json
{
  "resourceType": "CodeSystem",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "caseSensitive": true,
  "valueSet": "{valueset-url}",
  "hierarchyMeaning": "is-a",
  "content": "complete",
  "count": {code-count},
  "concept": [
    {
      "code": "{code-1}",
      "display": "{Display 1}",
      "definition": "{Definition 1}"
    },
    {
      "code": "{code-2}",
      "display": "{Display 2}",
      "definition": "{Definition 2}"
    }
  ]
}
```

### CodeSystem with Hierarchy Template

```json
{
  "resourceType": "CodeSystem",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "caseSensitive": true,
  "hierarchyMeaning": "is-a",
  "content": "complete",
  "concept": [
    {
      "code": "parent-code",
      "display": "Parent Category",
      "definition": "Parent category definition",
      "concept": [
        {
          "code": "child-code-1",
          "display": "Child 1",
          "definition": "First child concept"
        },
        {
          "code": "child-code-2",
          "display": "Child 2",
          "definition": "Second child concept"
        }
      ]
    }
  ]
}
```

### ValueSet Template (Include All from CodeSystem)

```json
{
  "resourceType": "ValueSet",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "compose": {
    "include": [
      {
        "system": "{codesystem-url}"
      }
    ]
  }
}
```

### ValueSet Template (Specific Codes)

```json
{
  "resourceType": "ValueSet",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "compose": {
    "include": [
      {
        "system": "{codesystem-url}",
        "concept": [
          {
            "code": "{code-1}",
            "display": "{Display 1}"
          },
          {
            "code": "{code-2}",
            "display": "{Display 2}"
          }
        ]
      }
    ]
  }
}
```

### ValueSet Template (Multiple Systems)

```json
{
  "resourceType": "ValueSet",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "compose": {
    "include": [
      {
        "system": "http://example.org/fhir/CodeSystem/custom-codes"
      },
      {
        "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
        "concept": [
          {
            "code": "AMB",
            "display": "ambulatory"
          },
          {
            "code": "IMP",
            "display": "inpatient"
          }
        ]
      }
    ],
    "exclude": [
      {
        "system": "http://example.org/fhir/CodeSystem/custom-codes",
        "concept": [
          {
            "code": "deprecated-code"
          }
        ]
      }
    ]
  }
}
```

### ValueSet Template (Filter)

```json
{
  "resourceType": "ValueSet",
  "id": "{name}",
  "url": "{url}",
  "version": "{version}",
  "name": "{Name}",
  "title": "{Title}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Publisher}",
  "description": "{Description}",
  "compose": {
    "include": [
      {
        "system": "http://snomed.info/sct",
        "filter": [
          {
            "property": "concept",
            "op": "is-a",
            "value": "404684003"
          }
        ]
      }
    ]
  }
}
```

---

## Common Standard Code Systems

Reference these when creating value sets that include standard codes:

| Code System | URL | Description |
|-------------|-----|-------------|
| SNOMED CT | `http://snomed.info/sct` | Clinical terminology |
| LOINC | `http://loinc.org` | Lab tests, observations |
| ICD-10 | `http://hl7.org/fhir/sid/icd-10` | Diagnoses |
| RxNorm | `http://www.nlm.nih.gov/research/umls/rxnorm` | Medications |
| CPT | `http://www.ama-assn.org/go/cpt` | Procedures |
| HL7 v3 | `http://terminology.hl7.org/CodeSystem/v3-*` | Various HL7 codes |
| FHIR | `http://hl7.org/fhir/CodeSystem/*` | FHIR-specific codes |

---

## Examples

### Example 1: Custom Appointment Type

**CodeSystem:**
```json
{
  "resourceType": "CodeSystem",
  "id": "appointment-type",
  "url": "http://example.org/fhir/CodeSystem/appointment-type",
  "version": "1.0.0",
  "name": "AppointmentType",
  "title": "Appointment Type Codes",
  "status": "active",
  "date": "2024-01-15",
  "publisher": "Example Healthcare",
  "description": "Types of appointments in the scheduling system",
  "caseSensitive": true,
  "content": "complete",
  "count": 5,
  "concept": [
    {
      "code": "new-patient",
      "display": "New Patient Visit",
      "definition": "First visit for a new patient"
    },
    {
      "code": "follow-up",
      "display": "Follow-up Visit",
      "definition": "Follow-up visit for existing patient"
    },
    {
      "code": "annual-wellness",
      "display": "Annual Wellness Exam",
      "definition": "Yearly preventive care visit"
    },
    {
      "code": "urgent",
      "display": "Urgent Visit",
      "definition": "Same-day urgent care appointment"
    },
    {
      "code": "telehealth",
      "display": "Telehealth Visit",
      "definition": "Virtual video or phone appointment"
    }
  ]
}
```

**ValueSet:**
```json
{
  "resourceType": "ValueSet",
  "id": "appointment-type-vs",
  "url": "http://example.org/fhir/ValueSet/appointment-type",
  "version": "1.0.0",
  "name": "AppointmentTypeValueSet",
  "title": "Appointment Type Value Set",
  "status": "active",
  "date": "2024-01-15",
  "publisher": "Example Healthcare",
  "description": "Allowed appointment types for scheduling",
  "compose": {
    "include": [
      {
        "system": "http://example.org/fhir/CodeSystem/appointment-type"
      }
    ]
  }
}
```

### Example 2: Observation Category Subset

**ValueSet (filtering standard codes):**
```json
{
  "resourceType": "ValueSet",
  "id": "lab-observation-category",
  "url": "http://example.org/fhir/ValueSet/lab-observation-category",
  "version": "1.0.0",
  "name": "LabObservationCategory",
  "title": "Laboratory Observation Categories",
  "status": "active",
  "date": "2024-01-15",
  "publisher": "Example Healthcare",
  "description": "Subset of observation categories for laboratory results",
  "compose": {
    "include": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
        "concept": [
          {
            "code": "laboratory",
            "display": "Laboratory"
          }
        ]
      },
      {
        "system": "http://example.org/fhir/CodeSystem/observation-category-extension",
        "concept": [
          {
            "code": "microbiology",
            "display": "Microbiology"
          },
          {
            "code": "chemistry",
            "display": "Chemistry"
          },
          {
            "code": "hematology",
            "display": "Hematology"
          }
        ]
      }
    ]
  }
}
```

---

## Adding Binding to Profile

When binding a value set to an element in a profile, add this to the StructureDefinition:

```json
{
  "id": "{ResourceType}.{element}",
  "path": "{ResourceType}.{element}",
  "binding": {
    "strength": "required",
    "description": "{Description of the binding}",
    "valueSet": "{valueset-url}"
  }
}
```

**Example binding in Encounter profile:**
```json
{
  "id": "Encounter.hospitalization.dischargeDisposition",
  "path": "Encounter.hospitalization.dischargeDisposition",
  "min": 1,
  "binding": {
    "strength": "required",
    "description": "Discharge disposition for the encounter",
    "valueSet": "http://example.org/fhir/ValueSet/discharge-disposition"
  }
}
```

---

## Directory Structure

Create terminology resources in:

```
fhir4java-server/src/main/resources/fhir-config/
├── r5/
│   └── terminology/
│       ├── CodeSystem-discharge-disposition.json
│       ├── CodeSystem-appointment-type.json
│       ├── ValueSet-discharge-disposition.json
│       └── ValueSet-appointment-type.json
└── r4b/
    └── terminology/
        └── (same structure for R4B if needed)
```

---

## After Creating Terminology Resources

Remind the user to:

1. **Create the terminology directory** (if it doesn't exist):
   ```bash
   mkdir -p fhir4java-server/src/main/resources/fhir-config/r5/terminology
   ```

2. **Restart the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

3. **Verify Loading** (check logs for):
   ```
   Loaded CodeSystem: http://example.org/fhir/CodeSystem/...
   Loaded ValueSet: http://example.org/fhir/ValueSet/...
   ```

4. **Test Validation**:
   - Create a resource with a valid code from the value set
   - Create a resource with an invalid code and verify validation error

5. **Update Profile Bindings** (if needed):
   - Edit the relevant StructureDefinition to bind elements to the value set
