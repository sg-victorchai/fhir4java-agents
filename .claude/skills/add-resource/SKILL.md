# Add New FHIR Resource Configuration

Guide the user through creating a new FHIR resource type configuration file.

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Resource Type Selection

Ask the user which FHIR resource type they want to configure. Common examples include:
- AllergyIntolerance
- Appointment
- CarePlan
- CareTeam
- Claim
- Coverage
- DiagnosticReport
- DocumentReference
- Goal
- Immunization
- Location
- Medication
- MedicationAdministration
- MedicationDispense
- Procedure
- ServiceRequest

### Step 2: FHIR Version Support

Ask which FHIR versions to support:
- **R5** (recommended, current default)
- **R4B** (for backward compatibility)
- Both R5 and R4B

One version must be marked as `default: true`.

### Step 3: Database Schema Configuration

Ask about the database schema:
- **shared** (default): Resource stored in the shared `fhir` schema alongside other resources
- **dedicated**: Resource gets its own schema (for high-volume or special isolation needs)

Default schema name is `fhir`.

### Step 4: Interactions Configuration

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

### Step 5: Search Parameters Configuration

Ask about search parameter restrictions:

1. **No restrictions** (default): All search parameters defined for the resource are allowed
2. **Allowlist mode**: Only explicitly listed parameters are allowed
3. **Denylist mode**: All parameters allowed except those explicitly denied

For allowlist/denylist, parameters are divided into:
- **common**: Parameters available to all resources (_id, _lastUpdated, _tag, _profile, _security, _text, _content, _filter)
- **resourceSpecific**: Parameters specific to this resource type

Reference the FHIR specification for available search parameters:
- https://hl7.org/fhir/R5/[resourcetype].html#search

### Step 6: Profile Configuration

Ask about required FHIR profiles:
- Base profile URL (e.g., `http://hl7.org/fhir/StructureDefinition/[ResourceType]`)
- Whether the profile is required or optional
- Any additional custom profiles

### Step 7: Generate Configuration File

After gathering all information, create the YAML configuration file at:
```
fhir4java-server/src/main/resources/fhir-config/resources/[resourcetype].yml
```

Use lowercase for the filename (e.g., `allergyintolerance.yml`).

## Configuration Template

```yaml
# [ResourceType] Resource Configuration
resourceType: [ResourceType]
enabled: true

# Multiple FHIR versions support
fhirVersions:
  - version: R5
    default: true
  # - version: R4B
  #   default: false

# Database schema configuration
schema:
  type: shared  # or 'dedicated'
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

# Search parameter restrictions (optional - omit for no restrictions)
# Option 1: Allowlist mode - only listed parameters allowed
# searchParameters:
#   mode: allowlist
#   common:
#     - _id
#     - _lastUpdated
#   resourceSpecific:
#     - identifier
#     - patient
#     - status

# Option 2: Denylist mode - all parameters allowed except listed
# searchParameters:
#   mode: denylist
#   common:
#     - _text
#     - _content
#     - _filter
#   resourceSpecific:
#     - complex-param

# Required/optional profiles
profiles:
  - url: http://hl7.org/fhir/StructureDefinition/[ResourceType]
    required: true
```

## Example Configurations

### Minimal Configuration (All Defaults)
```yaml
resourceType: Procedure
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

profiles:
  - url: http://hl7.org/fhir/StructureDefinition/Procedure
    required: true
```

### Allowlist Configuration
```yaml
resourceType: DiagnosticReport
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
  patch: true
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
  resourceSpecific:
    - identifier
    - patient
    - encounter
    - category
    - code
    - date
    - status
    - result

profiles:
  - url: http://hl7.org/fhir/StructureDefinition/DiagnosticReport
    required: true
```

### Denylist Configuration
```yaml
resourceType: Immunization
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
  mode: denylist
  common:
    - _text
    - _content
    - _filter
  resourceSpecific: []

profiles:
  - url: http://hl7.org/fhir/StructureDefinition/Immunization
    required: true
```

## After Creating the Configuration

Remind the user to:

1. **Add Search Parameter Definitions** (if not already present):
   - Check `fhir4java-server/src/main/resources/fhir-config/r5/searchparameters/` for the resource's search parameters
   - Download from HL7 if needed: `https://hl7.org/fhir/R5/search-parameters.json`

2. **Create Database Migration** (if using dedicated schema):
   - Add Flyway migration in `fhir4java-server/src/main/resources/db/migration/`

3. **Restart the Server**:
   - The resource configuration is loaded at startup
   - Run `./mvnw spring-boot:run -pl fhir4java-server`

4. **Test the New Resource**:
   - Create: `POST /fhir/r5/[ResourceType]`
   - Read: `GET /fhir/r5/[ResourceType]/[id]`
   - Search: `GET /fhir/r5/[ResourceType]?[params]`
