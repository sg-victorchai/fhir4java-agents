# Define FHIR Profile

Guide the user through creating a FHIR profile that constrains or extends an existing resource type, generating all required configuration files (StructureDefinition, SearchParameters, and resource configuration YAML).

## Scope

This skill is for creating **profiles** that constrain or extend existing FHIR resources.

| Use This Skill For | Use `/define-custom-resource` Instead |
|--------------------|---------------------------------------|
| Constraining Patient, Observation, etc. | Creating entirely new resource types |
| Adding must-support flags | Concepts not in FHIR spec |
| Binding value sets to existing elements | `derivation: specialization` |
| Adding extensions to standard resources | Requires code generation |
| `derivation: constraint` | |

### What is a Profile?

A FHIR profile defines constraints and/or extensions on a base resource:

| Aspect | Base Resource | Profile |
|--------|---------------|---------|
| Cardinality | `0..*` | Can only narrow (e.g., `1..1`) |
| Value Sets | May be unbound | Can add required bindings |
| Must Support | Not specified | Can mark elements as must-support |
| Extensions | Not present | Can add custom extensions |
| Slicing | Not defined | Can slice repeating elements |

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

---

### Step 1: Profile Identity

Gather basic information about the profile:

| Field | Description | Example |
|-------|-------------|---------|
| Profile Name | PascalCase profile name | `USCorePatient` |
| Base Resource | Resource to profile | `Patient` |
| URL Namespace | Organization's FHIR URL | `http://example.org/fhir/StructureDefinition/` |
| Description | Purpose of the profile | "US Core Patient profile with required identifiers" |

**Ask the user:**
```
═══════════════════════════════════════════════════════════════════════════════
 STEP 1: Profile Identity
═══════════════════════════════════════════════════════════════════════════════

What is the name of your profile? (PascalCase, e.g., USCorePatient)
>

Which resource are you profiling?
  Examples: Patient, Observation, Condition, MedicationRequest, CarePlan
  (Can also profile custom resources like CustomMedicalDevice)
>

What is your organization's FHIR namespace URL?
  (e.g., http://example.org/fhir/StructureDefinition/)
>

Briefly describe the purpose of this profile:
>

═══════════════════════════════════════════════════════════════════════════════
```

---

### Step 2: Simple Constraint Entry Form

Collect profile requirements in plain language. Users do NOT need to know FHIR element paths.

**Present this form to the user:**

```
═══════════════════════════════════════════════════════════════════════════════
 STEP 2: Define Your Profile Constraints (Plain Language)
═══════════════════════════════════════════════════════════════════════════════

Describe what constraints or extensions you need. Use plain business language!

COMMON CONSTRAINT TYPES:

1. REQUIRED ELEMENTS - Elements that must be present
   Example: "Patient must have at least one identifier"
   Example: "Observation must have a performer"

2. MUST-SUPPORT - Elements that systems must be able to handle
   Example: "Systems must support patient's birth date"
   Example: "Must support observation value and interpretation"

3. VALUE SET BINDINGS - Restrict coded elements to specific values
   Example: "Gender must be from US Core gender codes"
   Example: "Observation category must use LOINC codes"

4. FIXED VALUES - Elements with a specific constant value
   Example: "Observation code must be LOINC 85354-9 (blood pressure)"
   Example: "Patient.active must always be true"

5. EXTENSIONS - Add custom data elements
   Example: "Add race and ethnicity extensions to Patient"
   Example: "Add a 'priority' extension to CarePlan"

6. SLICING - Define specific patterns for repeating elements
   Example: "Patient must have both MRN and SSN identifiers"
   Example: "Observation must have systolic and diastolic components"

───────────────────────────────────────────────────────────────────────────────
YOUR CONSTRAINTS:
(Describe your requirements below, one per line)



───────────────────────────────────────────────────────────────────────────────
ADDITIONAL INSTRUCTIONS (optional):
(Value set URLs, extension URLs, business rules, etc.)



═══════════════════════════════════════════════════════════════════════════════
```

**Example user input:**
```
1. Patient must have at least one identifier (required)
2. Patient must have a family name and given name (required)
3. Must support birthDate, gender, and address
4. Gender must use standard FHIR gender codes
5. Add US Core race extension
6. Add US Core ethnicity extension
7. Patient must have both MRN identifier (system: http://hospital.org/mrn)
   and SSN identifier (system: http://hl7.org/fhir/sid/us-ssn)
```

---

### Step 3: AI Analysis and Structure Generation

After receiving the user's input, analyze it to determine the appropriate constraints.

#### Constraint Type Detection

| Pattern in Description | Constraint Type |
|------------------------|-----------------|
| "must have", "required", "at least one" | Cardinality constraint (increase min) |
| "must support", "systems must handle" | Must-support flag |
| "must use", "must be from", "only allow" | Value set binding |
| "must be", "always", "fixed to" | Fixed value |
| "add extension", "extend with" | Extension |
| "must have both", "must have X and Y" | Slicing |
| "pattern", "specific values" | Pattern constraint |

#### Element Path Mapping

Map user's plain language to FHIR element paths:

| User Description | FHIR Element Path |
|------------------|-------------------|
| "identifier" | `Patient.identifier` |
| "family name", "last name" | `Patient.name.family` |
| "given name", "first name" | `Patient.name.given` |
| "birth date", "date of birth", "DOB" | `Patient.birthDate` |
| "gender", "sex" | `Patient.gender` |
| "address" | `Patient.address` |
| "phone", "telephone" | `Patient.telecom` (with system=phone) |
| "email" | `Patient.telecom` (with system=email) |
| "MRN", "medical record number" | `Patient.identifier` (sliced) |
| "SSN", "social security" | `Patient.identifier` (sliced) |
| "observation value" | `Observation.value[x]` |
| "observation code" | `Observation.code` |
| "performer" | `Observation.performer` |

#### Well-Known Extensions

| Extension Name | URL |
|----------------|-----|
| US Core Race | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-race` |
| US Core Ethnicity | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity` |
| US Core Birth Sex | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex` |
| Patient Religion | `http://hl7.org/fhir/StructureDefinition/patient-religion` |
| Patient Nationality | `http://hl7.org/fhir/StructureDefinition/patient-nationality` |

---

### Step 4: Visual Layout Display

After analysis, display the profile constraints in a visual table:

```
═══════════════════════════════════════════════════════════════════════════════════════════
 USCorePatient - Profile of Patient ({count} constraints)
═══════════════════════════════════════════════════════════════════════════════════════════
 Base Resource: Patient
 URL: http://example.org/fhir/StructureDefinition/USCorePatient
═══════════════════════════════════════════════════════════════════════════════════════════

CARDINALITY CONSTRAINTS
┌─────┬─────────────────────────┬───────────┬───────────┬──────────────────────────────────┐
│  #  │ Element                 │ Base Card │ New Card  │ Notes                            │
├─────┼─────────────────────────┼───────────┼───────────┼──────────────────────────────────┤
│  1  │ identifier              │ 0..*      │ 1..*      │ At least one identifier required │
│  2  │ name                    │ 0..*      │ 1..*      │ At least one name required       │
│  3  │ name.family             │ 0..1      │ 1..1      │ Family name required             │
│  4  │ name.given              │ 0..*      │ 1..*      │ At least one given name required │
└─────┴─────────────────────────┴───────────┴───────────┴──────────────────────────────────┘

MUST-SUPPORT ELEMENTS
┌─────┬─────────────────────────┬──────────────────────────────────────────────────────────┐
│  #  │ Element                 │ Notes                                                    │
├─────┼─────────────────────────┼──────────────────────────────────────────────────────────┤
│  5  │ birthDate               │ Must support patient's date of birth                     │
│  6  │ gender                  │ Must support patient's gender                            │
│  7  │ address                 │ Must support patient's address                           │
└─────┴─────────────────────────┴──────────────────────────────────────────────────────────┘

VALUE SET BINDINGS
┌─────┬─────────────────────────┬────────────┬───────────────────────────────────────────────┐
│  #  │ Element                 │ Strength   │ ValueSet                                      │
├─────┼─────────────────────────┼────────────┼───────────────────────────────────────────────┤
│  8  │ gender                  │ required   │ http://hl7.org/fhir/ValueSet/administrative-  │
│     │                         │            │ gender                                        │
└─────┴─────────────────────────┴────────────┴───────────────────────────────────────────────┘

EXTENSIONS
┌─────┬─────────────────────────┬────────────┬───────────────────────────────────────────────┐
│  #  │ Extension Name          │ Cardinality│ URL                                           │
├─────┼─────────────────────────┼────────────┼───────────────────────────────────────────────┤
│  9  │ race                    │ 0..1       │ http://hl7.org/fhir/us/core/.../us-core-race  │
│ 10  │ ethnicity               │ 0..1       │ http://hl7.org/fhir/us/core/.../us-core-      │
│     │                         │            │ ethnicity                                     │
└─────┴─────────────────────────┴────────────┴───────────────────────────────────────────────┘

SLICING (identifier)
┌─────┬─────────────────────────┬────────────┬───────────────────────────────────────────────┐
│  #  │ Slice Name              │ Cardinality│ Discriminator                                 │
├─────┼─────────────────────────┼────────────┼───────────────────────────────────────────────┤
│ 11  │ identifier:MRN          │ 1..1       │ system = http://hospital.org/mrn              │
│ 12  │ identifier:SSN          │ 0..1       │ system = http://hl7.org/fhir/sid/us-ssn       │
└─────┴─────────────────────────┴────────────┴───────────────────────────────────────────────┘

Legend: Base Card = Original cardinality, New Card = Constrained cardinality

───────────────────────────────────────────────────────────────────────────────────────────
 REVIEW & REFINE
───────────────────────────────────────────────────────────────────────────────────────────
 Commands:
   Edit <#> card <new-card>     │ Change cardinality       │ Edit 1 card 2..*
   Edit <#> binding <strength>  │ Change binding strength  │ Edit 8 binding extensible
   Add must-support <element>   │ Add must-support flag    │ Add must-support telecom
   Add extension <name> <url>   │ Add extension            │ Add extension birthsex ...
   Add slice <element> <name>   │ Add slice                │ Add slice identifier NPI
   Remove <#>                   │ Remove constraint        │ Remove 12
   Show <#>                     │ Show constraint details  │ Show 11
   Done                         │ Proceed to next step     │ Done
───────────────────────────────────────────────────────────────────────────────────────────

What would you like to adjust? (or type "Done" to proceed)
```

#### Interactive Refinement

**Edit cardinality example:**
```
User: Edit 1 card 2..*

Updated constraint #1:
  identifier: 1..* → 2..* (at least 2 identifiers required)

[Display updated table]
```

**Add must-support example:**
```
User: Add must-support telecom

Added must-support for: Patient.telecom

[Display updated table with new row in MUST-SUPPORT section]
```

**Add extension example:**
```
User: Add extension birthsex http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex

Added extension:
  Name: birthsex
  URL: http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex
  Cardinality: 0..1

[Display updated table]
```

**Add slice example:**
```
User: Add slice identifier NPI

Adding slice to 'identifier'...
What is the discriminator system URL? > http://hl7.org/fhir/sid/us-npi
Is this slice required? (y/n) > n
Can there be multiple? (y/n) > n

Added slice: identifier:NPI (0..1, system = http://hl7.org/fhir/sid/us-npi)

[Display updated table]
```

**Show constraint details:**
```
User: Show 11

Slice #11: identifier:MRN
  Element: Patient.identifier
  Slice Name: MRN
  Cardinality: 1..1
  Discriminator Type: value
  Discriminator Path: system
  Fixed Value: http://hospital.org/mrn
  Description: Medical Record Number identifier
```

---

### Step 5: Invariants (Optional)

Ask if the user wants to define custom business rules:

```
═══════════════════════════════════════════════════════════════════════════════════════════
 INVARIANTS (Optional)
═══════════════════════════════════════════════════════════════════════════════════════════

Do you want to define any custom validation rules (FHIRPath invariants)?

Examples:
  - "If deceased is true, deceasedDateTime must be present"
  - "Address must have either line or city"
  - "At least one telecom must be a phone number"

Options:
  Add         │ Define a new invariant
  Skip        │ No custom invariants needed

Your choice:
```

For each invariant, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Key | Unique identifier | `us-core-1` |
| Severity | error or warning | `error` |
| Human | Human-readable description | "Deceased date required if deceased" |
| Expression | FHIRPath expression | `deceased.exists() implies deceasedDateTime.exists()` |

---

### Step 6: Search Parameters

Determine if any new search parameters are needed:

```
═══════════════════════════════════════════════════════════════════════════════════════════
 SEARCH PARAMETERS
═══════════════════════════════════════════════════════════════════════════════════════════

Standard Patient search parameters are automatically available:
  - _id, identifier, name, family, given, birthdate, gender, address, phone, email, etc.

Do you need to define any ADDITIONAL search parameters?
(Usually only needed if you added extensions that should be searchable)

Examples:
  - Search by race extension
  - Search by custom extension value

Options:
  Add         │ Define a new search parameter
  Skip        │ Use standard search parameters only

Your choice:
```

---

### Step 7: FHIR Version & Interactions

```
═══════════════════════════════════════════════════════════════════════════════════════════
 FHIR VERSION & INTERACTIONS
═══════════════════════════════════════════════════════════════════════════════════════════

Which FHIR versions should this profile support?
  [x] R5 (recommended, default)
  [ ] R4B (backward compatibility)

Profile enforcement:
  [x] Required - Resources must validate against this profile
  [ ] Optional - Profile is informational only

Toggle any to change, or type "Done" to proceed.
```

---

### Step 8: Generate All Artifacts

After gathering all information, generate these files:

#### Files to Generate

| File | Location |
|------|----------|
| StructureDefinition | `fhir-config/r5/profiles/StructureDefinition-{ProfileName}.json` |
| SearchParameter(s) | `fhir-config/r5/searchparameters/SearchParameter-{ProfileName}-{param}.json` |
| Resource Config | `fhir-config/resources/{baseresource}.yml` (update if exists) |

**Confirm before generating:**

```
═══════════════════════════════════════════════════════════════════════════════════════════
 SUMMARY - Ready to Generate
═══════════════════════════════════════════════════════════════════════════════════════════

Profile: USCorePatient
Base Resource: Patient
Namespace: http://example.org/fhir/StructureDefinition/

Constraints:
  - Cardinality: 4 constraints
  - Must-Support: 3 elements
  - Value Set Bindings: 1 binding
  - Extensions: 2 extensions
  - Slicing: 2 slices
  - Invariants: 0

FHIR Version: R5
Profile Enforcement: Required

Files to create/update:
  1. StructureDefinition-USCorePatient.json (new)
  2. patient.yml (update - add profile reference)

Proceed with generation? (y/n)
```

---

## Templates

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
      }
      // Constraint elements follow...
    ]
  }
}
```

### Cardinality Constraint Element

```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "min": {newMin},
  "max": "{newMax}",
  "mustSupport": true
}
```

### Must-Support Element

```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "mustSupport": true
}
```

### Value Set Binding Element

```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "binding": {
    "strength": "{required|extensible|preferred|example}",
    "valueSet": "{valueSetUrl}"
  }
}
```

### Fixed Value Element

```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "fixedCode": "{value}"
}
```

Or for other types:
```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "fixedUri": "{value}"
}
```
```json
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "patternCodeableConcept": {
    "coding": [{
      "system": "{system}",
      "code": "{code}"
    }]
  }
}
```

### Extension Element

```json
{
  "id": "{BaseResourceType}.extension:{extensionName}",
  "path": "{BaseResourceType}.extension",
  "sliceName": "{extensionName}",
  "short": "{description}",
  "min": {min},
  "max": "{max}",
  "type": [{
    "code": "Extension",
    "profile": ["{extensionUrl}"]
  }],
  "mustSupport": true
}
```

### Slicing Definition

```json
// First, define the slicing on the base element
{
  "id": "{BaseResourceType}.{elementPath}",
  "path": "{BaseResourceType}.{elementPath}",
  "slicing": {
    "discriminator": [{
      "type": "value",
      "path": "{discriminatorPath}"
    }],
    "rules": "open"
  },
  "min": {minTotal}
}
```

### Slice Element

```json
{
  "id": "{BaseResourceType}.{elementPath}:{sliceName}",
  "path": "{BaseResourceType}.{elementPath}",
  "sliceName": "{sliceName}",
  "short": "{description}",
  "min": {min},
  "max": "{max}",
  "mustSupport": true
},
{
  "id": "{BaseResourceType}.{elementPath}:{sliceName}.{discriminatorPath}",
  "path": "{BaseResourceType}.{elementPath}.{discriminatorPath}",
  "min": 1,
  "fixedUri": "{discriminatorValue}"
}
```

### Invariant Element

```json
{
  "id": "{BaseResourceType}",
  "path": "{BaseResourceType}",
  "constraint": [{
    "key": "{key}",
    "severity": "{error|warning}",
    "human": "{human-readable description}",
    "expression": "{fhirpath-expression}"
  }]
}
```

### SearchParameter Template (for extensions)

```json
{
  "resourceType": "SearchParameter",
  "id": "{ProfileName}-{param-name}",
  "url": "{namespace}SearchParameter/{ProfileName}-{param-name}",
  "version": "1.0.0",
  "name": "{paramName}",
  "status": "draft",
  "experimental": true,
  "date": "{YYYY-MM-DD}",
  "publisher": "{Organization Name}",
  "description": "Search by {description}",
  "code": "{param-name}",
  "base": ["{BaseResourceType}"],
  "type": "{token|string|date|reference}",
  "expression": "{BaseResourceType}.extension.where(url='{extensionUrl}').value",
  "processingMode": "normal"
}
```

### Resource Configuration YAML Update

When a profile is created, update or create the resource configuration:

```yaml
# {baseresource}.yml
resourceType: {BaseResourceType}
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

# Profile configuration
profiles:
  - url: {namespace}{ProfileName}
    required: true  # or false for optional profiles
```

---

## Common Profile Patterns

### US Core Patient Profile Pattern

```
Constraints:
- identifier: 1..* (required)
- identifier:MRN: 1..1 (sliced, required)
- name: 1..* (required)
- name.family: 1..1 (required)
- gender: 1..1 (required, bound to AdministrativeGender)

Must-Support:
- birthDate
- address
- telecom
- communication

Extensions:
- us-core-race (0..1)
- us-core-ethnicity (0..1)
- us-core-birthsex (0..1)
```

### Vital Signs Observation Profile Pattern

```
Constraints:
- status: 1..1 (fixed to "final" or bound to subset)
- category: 1..* (must include vital-signs category)
- code: 1..1 (bound to vital signs LOINC codes)
- subject: 1..1 (required, reference to Patient)
- effective[x]: 1..1 (required)
- value[x]: 0..1 (must-support)

Slicing:
- category: sliced by coding.code
  - category:VSCat: 1..1 (vital-signs)
- component: sliced by code (for blood pressure)
  - component:systolic: 0..1
  - component:diastolic: 0..1
```

### Lab Result Observation Profile Pattern

```
Constraints:
- status: 1..1 (bound to final|amended|corrected)
- category: 1..* (must include laboratory)
- code: 1..1 (bound to lab LOINC codes)
- subject: 1..1 (required Patient reference)
- performer: 1..* (required)

Must-Support:
- value[x]
- interpretation
- referenceRange
- specimen
```

---

## FHIR Element Path Quick Reference

### Patient Elements

| Common Name | FHIR Path |
|-------------|-----------|
| Identifier | `Patient.identifier` |
| Name | `Patient.name` |
| Family name | `Patient.name.family` |
| Given name | `Patient.name.given` |
| Birth date | `Patient.birthDate` |
| Gender | `Patient.gender` |
| Address | `Patient.address` |
| Phone | `Patient.telecom` (system=phone) |
| Email | `Patient.telecom` (system=email) |
| Marital status | `Patient.maritalStatus` |
| Contact | `Patient.contact` |
| Communication | `Patient.communication` |
| General practitioner | `Patient.generalPractitioner` |
| Managing organization | `Patient.managingOrganization` |

### Observation Elements

| Common Name | FHIR Path |
|-------------|-----------|
| Status | `Observation.status` |
| Category | `Observation.category` |
| Code | `Observation.code` |
| Subject | `Observation.subject` |
| Encounter | `Observation.encounter` |
| Effective date/time | `Observation.effective[x]` |
| Performer | `Observation.performer` |
| Value | `Observation.value[x]` |
| Interpretation | `Observation.interpretation` |
| Reference range | `Observation.referenceRange` |
| Component | `Observation.component` |

### Condition Elements

| Common Name | FHIR Path |
|-------------|-----------|
| Clinical status | `Condition.clinicalStatus` |
| Verification status | `Condition.verificationStatus` |
| Category | `Condition.category` |
| Severity | `Condition.severity` |
| Code | `Condition.code` |
| Subject | `Condition.subject` |
| Encounter | `Condition.encounter` |
| Onset | `Condition.onset[x]` |
| Abatement | `Condition.abatement[x]` |
| Recorded date | `Condition.recordedDate` |

---

## Binding Strength Reference

| Strength | Description | When to Use |
|----------|-------------|-------------|
| `required` | Must be from value set | Codes with strict semantic meaning |
| `extensible` | Should be from value set, can extend | Standard codes with local additions |
| `preferred` | Recommended to use value set | Guidance without enforcement |
| `example` | Value set is illustrative | Documentation purposes |

---

## After Creating the Profile

Remind the user to:

1. **Rebuild the Project** (if resource config changed):
   ```bash
   ./mvnw clean install -pl fhir4java-core,fhir4java-server
   ```

2. **Restart the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

3. **Test Profile Validation**:
   ```bash
   # Create a resource that should validate
   curl -X POST http://localhost:8080/fhir/r5/{BaseResource} \
     -H "Content-Type: application/fhir+json" \
     -d '{
       "resourceType": "{BaseResource}",
       "meta": {
         "profile": ["{profileUrl}"]
       },
       ...
     }'

   # Validate against profile
   curl -X POST "http://localhost:8080/fhir/r5/{BaseResource}/\$validate?profile={profileUrl}" \
     -H "Content-Type: application/fhir+json" \
     -d '{...}'
   ```

4. **Verify CapabilityStatement**:
   ```bash
   curl http://localhost:8080/fhir/r5/metadata | \
     jq '.rest[0].resource[] | select(.type=="{BaseResource}") | .supportedProfile'
   ```
