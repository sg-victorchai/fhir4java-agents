# FHIR Query Builder

Guide the user through building FHIR search queries interactively, generating the correct URL syntax and curl commands.

## Overview

The FHIR search API is powerful but complex. This skill helps you:
- **Build** correct search queries without memorizing syntax
- **Combine** multiple parameters with AND/OR logic
- **Use** modifiers and prefixes correctly
- **Generate** curl commands for testing

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Select Resource Type

Ask which FHIR resource to search:

**Common Resources:**
- `Patient` - Demographics, identifiers
- `Observation` - Lab results, vital signs
- `Condition` - Diagnoses, problems
- `MedicationRequest` - Prescriptions
- `Encounter` - Visits, admissions
- `DiagnosticReport` - Reports with observations
- `Procedure` - Surgical and medical procedures
- `AllergyIntolerance` - Allergies and intolerances

### Step 2: Define Search Criteria

For each criterion, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Parameter | Which search parameter | `family`, `birthdate` |
| Value | What to search for | `Smith`, `1990-01-01` |
| Modifier | Optional modifier | `:exact`, `:contains` |
| Prefix | For dates/numbers | `gt`, `lt`, `ge`, `le` |

### Step 3: Build Query Incrementally

Present options based on the selected resource. For each parameter type:

#### String Parameters
```
How to search: [parameter]=[value]
Modifiers:
  - :exact    - Exact match (case-sensitive)
  - :contains - Contains substring
  - :missing  - Missing/present

Example: family=Smith
         family:exact=Smith
         family:contains=mit
```

#### Token Parameters (codes, identifiers)
```
How to search: [parameter]=[system]|[code]
              [parameter]=[code]      (any system)
              [parameter]=|[code]     (no system)
Modifiers:
  - :text     - Search display text
  - :not      - Not equal
  - :missing  - Missing/present

Example: identifier=http://hospital.org/mrn|12345
         gender=female
         code=http://loinc.org|8867-4
```

#### Date Parameters
```
How to search: [parameter]=[prefix][date]
Prefixes:
  - eq  - Equal (default)
  - ne  - Not equal
  - gt  - Greater than
  - lt  - Less than
  - ge  - Greater than or equal
  - le  - Less than or equal
  - sa  - Starts after
  - eb  - Ends before
  - ap  - Approximately

Example: birthdate=1990-01-01
         birthdate=ge1990-01-01
         date=gt2024-01-01&date=lt2024-12-31
```

#### Reference Parameters
```
How to search: [parameter]=[type]/[id]
              [parameter]=[id]        (if type implied)
              [parameter]:identifier=[system]|[value]
Modifiers:
  - :identifier - Search by reference's identifier

Example: subject=Patient/123
         patient=Patient/123
         subject:identifier=http://hospital.org/mrn|12345
```

#### Quantity Parameters
```
How to search: [parameter]=[prefix][value]|[system]|[code]
              [parameter]=[prefix][value]

Example: value-quantity=5.4|http://unitsofmeasure.org|mg
         value-quantity=gt100||mg
```

### Step 4: Combine Parameters

Ask how to combine multiple criteria:

#### AND Logic (comma or multiple params)
```
Find patients named Smith born in 1990:
  GET /Patient?family=Smith&birthdate=1990

Find observations with code AND status:
  GET /Observation?code=8867-4&status=final
```

#### OR Logic (comma-separated values)
```
Find patients with gender male OR female:
  GET /Patient?gender=male,female

Find observations with multiple codes:
  GET /Observation?code=8867-4,9279-1,8310-5
```

### Step 5: Add Result Control

Ask about result formatting:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `_count` | Max results per page | `_count=50` |
| `_sort` | Sort order | `_sort=date`, `_sort=-date` |
| `_include` | Include referenced resources | `_include=Observation:patient` |
| `_revinclude` | Include resources that reference | `_revinclude=Observation:patient` |
| `_summary` | Summarize results | `_summary=true`, `_summary=count` |
| `_elements` | Return only specific elements | `_elements=id,name,birthDate` |

### Step 6: Generate Output

After building the query, generate:

1. **Full URL**
2. **curl command**
3. **Explanation of what the query does**

---

## Search Parameter Reference by Resource

### Patient

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `_id` | token | Resource ID | `_id=123` |
| `identifier` | token | Business identifier (MRN, SSN) | `identifier=http://hospital.org/mrn\|12345` |
| `family` | string | Family name | `family=Smith` |
| `given` | string | Given name | `given=John` |
| `name` | string | Any name part | `name=John` |
| `birthdate` | date | Date of birth | `birthdate=1990-01-01` |
| `gender` | token | Gender | `gender=male` |
| `address` | string | Any address part | `address=123 Main` |
| `address-city` | string | City | `address-city=Boston` |
| `address-state` | string | State | `address-state=MA` |
| `address-postalcode` | string | Postal code | `address-postalcode=02101` |
| `phone` | token | Phone number | `phone=555-1234` |
| `email` | token | Email address | `email=john@example.com` |
| `active` | token | Active status | `active=true` |
| `deceased` | token | Deceased status | `deceased=false` |
| `general-practitioner` | reference | Primary care provider | `general-practitioner=Practitioner/123` |
| `organization` | reference | Managing organization | `organization=Organization/456` |

### Observation

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `_id` | token | Resource ID | `_id=123` |
| `identifier` | token | Business identifier | `identifier=http://lab.org\|ABC123` |
| `patient` | reference | Patient reference | `patient=Patient/123` |
| `subject` | reference | Subject reference | `subject=Patient/123` |
| `code` | token | Observation code (LOINC) | `code=http://loinc.org\|8867-4` |
| `category` | token | Category (laboratory, vital-signs) | `category=vital-signs` |
| `status` | token | Status | `status=final` |
| `date` | date | Effective date | `date=ge2024-01-01` |
| `value-quantity` | quantity | Numeric value | `value-quantity=gt100` |
| `value-concept` | token | Coded value | `value-concept=positive` |
| `encounter` | reference | Encounter reference | `encounter=Encounter/789` |
| `performer` | reference | Who performed | `performer=Practitioner/456` |
| `combo-code` | token | Component code | `combo-code=8480-6` |
| `combo-value-quantity` | quantity | Component value | `combo-value-quantity=gt120` |

### Condition

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `_id` | token | Resource ID | `_id=123` |
| `patient` | reference | Patient reference | `patient=Patient/123` |
| `subject` | reference | Subject reference | `subject=Patient/123` |
| `code` | token | Condition code (ICD-10, SNOMED) | `code=http://snomed.info/sct\|73211009` |
| `clinical-status` | token | Clinical status | `clinical-status=active` |
| `verification-status` | token | Verification status | `verification-status=confirmed` |
| `category` | token | Category | `category=problem-list-item` |
| `severity` | token | Severity | `severity=severe` |
| `onset-date` | date | Onset date | `onset-date=ge2024-01-01` |
| `recorded-date` | date | Recorded date | `recorded-date=2024-06-15` |
| `encounter` | reference | Encounter reference | `encounter=Encounter/789` |

### MedicationRequest

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `_id` | token | Resource ID | `_id=123` |
| `identifier` | token | Business identifier | `identifier=RX123456` |
| `patient` | reference | Patient reference | `patient=Patient/123` |
| `subject` | reference | Subject reference | `subject=Patient/123` |
| `medication` | reference | Medication reference | `medication=Medication/456` |
| `code` | token | Medication code (RxNorm) | `code=http://www.nlm.nih.gov/research/umls/rxnorm\|857005` |
| `status` | token | Status | `status=active` |
| `intent` | token | Intent | `intent=order` |
| `authoredon` | date | Authored date | `authoredon=ge2024-01-01` |
| `requester` | reference | Prescriber | `requester=Practitioner/789` |
| `encounter` | reference | Encounter reference | `encounter=Encounter/456` |

### Encounter

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `_id` | token | Resource ID | `_id=123` |
| `identifier` | token | Business identifier | `identifier=VISIT123` |
| `patient` | reference | Patient reference | `patient=Patient/123` |
| `subject` | reference | Subject reference | `subject=Patient/123` |
| `status` | token | Status | `status=finished` |
| `class` | token | Class (inpatient, outpatient) | `class=inpatient` |
| `type` | token | Encounter type | `type=http://snomed.info/sct\|183807002` |
| `date` | date | Encounter period | `date=ge2024-01-01` |
| `location` | reference | Location | `location=Location/456` |
| `participant` | reference | Participants | `participant=Practitioner/789` |
| `reason-code` | token | Reason code | `reason-code=http://snomed.info/sct\|386661006` |

---

## Example Queries

### Example 1: Find Active Diabetic Patients

**Natural language**: Find all active patients with diabetes

**Query**:
```
GET /fhir/r5/Patient?active=true&_has:Condition:patient:code=http://snomed.info/sct|73211009
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Patient?active=true&_has:Condition:patient:code=http://snomed.info/sct|73211009" \
  -H "Accept: application/fhir+json"
```

### Example 2: Find Recent Lab Results

**Natural language**: Find lab observations for patient 123 from the last 30 days

**Query**:
```
GET /fhir/r5/Observation?patient=Patient/123&category=laboratory&date=ge2024-01-01&_sort=-date
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Observation?patient=Patient/123&category=laboratory&date=ge2024-01-01&_sort=-date" \
  -H "Accept: application/fhir+json"
```

### Example 3: Find Patients with Specific MRN

**Natural language**: Find patient with MRN 12345 at our hospital

**Query**:
```
GET /fhir/r5/Patient?identifier=http://hospital.org/mrn|12345
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Patient?identifier=http://hospital.org/mrn|12345" \
  -H "Accept: application/fhir+json"
```

### Example 4: Find Active Medications with Includes

**Natural language**: Find active medications for patient 123, include the patient and prescriber

**Query**:
```
GET /fhir/r5/MedicationRequest?patient=Patient/123&status=active&_include=MedicationRequest:patient&_include=MedicationRequest:requester
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/MedicationRequest?patient=Patient/123&status=active&_include=MedicationRequest:patient&_include=MedicationRequest:requester" \
  -H "Accept: application/fhir+json"
```

### Example 5: Find Blood Pressure Readings in Range

**Natural language**: Find blood pressure observations with systolic > 140

**Query**:
```
GET /fhir/r5/Observation?code=http://loinc.org|85354-9&combo-code=http://loinc.org|8480-6&combo-value-quantity=gt140|http://unitsofmeasure.org|mm[Hg]
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Observation?code=http://loinc.org|85354-9&combo-code=http://loinc.org|8480-6&combo-value-quantity=gt140|http://unitsofmeasure.org|mm[Hg]" \
  -H "Accept: application/fhir+json"
```

### Example 6: Count Patients by State

**Natural language**: Count how many patients are in each state

**Query**:
```
GET /fhir/r5/Patient?_summary=count&address-state=MA
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Patient?_summary=count&address-state=MA" \
  -H "Accept: application/fhir+json"
```

### Example 7: Find Encounters with Reverse Include

**Natural language**: Find encounters for patient, include all observations from those encounters

**Query**:
```
GET /fhir/r5/Encounter?patient=Patient/123&_revinclude=Observation:encounter
```

**curl**:
```bash
curl -X GET "http://localhost:8080/fhir/r5/Encounter?patient=Patient/123&_revinclude=Observation:encounter" \
  -H "Accept: application/fhir+json"
```

---

## Advanced Search Features

### Chained Parameters

Search on properties of referenced resources:

```
# Find observations where patient's name is Smith
GET /Observation?patient.name=Smith

# Find medication requests where prescriber is in Cardiology
GET /MedicationRequest?requester.practitioner.organization.name=Cardiology
```

### Reverse Chained Parameters (_has)

Search resources based on resources that reference them:

```
# Find patients who have a diabetes diagnosis
GET /Patient?_has:Condition:patient:code=http://snomed.info/sct|73211009

# Find practitioners who have written prescriptions
GET /Practitioner?_has:MedicationRequest:requester:status=active
```

### Composite Parameters

Search on multiple related values together:

```
# Find observations with code 8867-4 AND value > 100
GET /Observation?code-value-quantity=http://loinc.org|8867-4$gt100
```

### Text Search

Full-text search on narrative:

```
GET /Patient?_text=diabetic
GET /Condition?_content=chest pain
```

---

## Output Format

When generating queries, provide:

1. **URL**: The complete search URL
2. **curl command**: Ready-to-use curl command
3. **Explanation**: What the query searches for
4. **Parameters breakdown**: Explanation of each parameter
5. **Expected results**: What kind of resources will be returned

### Output Template:

```
## Search Query

**Description**: {what the query does}

**URL**:
```
GET {base-url}/{resource-type}?{parameters}
```

**curl command**:
```bash
curl -X GET "{full-url}" \
  -H "Accept: application/fhir+json"
```

**Parameters**:
- `{param1}={value1}` - {explanation}
- `{param2}={value2}` - {explanation}

**Expected Results**:
- Returns a Bundle containing {resource-type} resources
- Results are {sorted/filtered by}
- {Any included resources}
```
