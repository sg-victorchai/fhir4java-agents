# FHIR4Java - Enterprise FHIR Server Implementation Plan

## Overview
Build a configuration-driven, plugin-based FHIR server supporting HL7 FHIR R4B and R5 with custom resource support, running on Java 25 LTS with Spring Boot, JPA, and PostgreSQL in Docker containers.

## Technology Stack
- **Java**: 25 LTS (September 2025 release)
- **Framework**: Spring Boot 3.4+
- **Persistence**: Spring Data JPA with Hibernate 6.x
- **Database**: PostgreSQL 16+
- **DB Migrations**: Flyway
- **FHIR Libraries**: HAPI FHIR 7.x (for serialization, validation, structure definitions)
- **Build**: Maven (multi-module)
- **Containerization**: Docker & Docker Compose
- **Plugin System**: Spring Bean-based with classpath scanning
- **FHIR Version Selection**: URL path-based (e.g., `/fhir/r5/Patient`)

---

## Project Structure

```
fhir4java/
├── db/                                # Database scripts (all SQL in one place)
│   ├── init/                          # Initial schema setup
│   │   ├── 00-init-schemas.sql        # Create schemas (fhir_resources, fhir_audit, etc.)
│   │   ├── 01-resource-tables.sql     # Main resource_data table with partitions
│   │   ├── 02-history-tables.sql      # History table with time-based partitions
│   │   ├── 03-search-index.sql        # Search index table with partitions
│   │   └── 04-audit-tables.sql        # Audit and performance tracking tables
│   ├── indexes/                       # Index definitions
│   │   ├── 01-covering-indexes.sql    # Covering indexes for common queries
│   │   ├── 02-search-indexes.sql      # Search parameter indexes
│   │   ├── 03-partial-indexes.sql     # Partial indexes for common patterns
│   │   └── 04-gin-brin-indexes.sql    # GIN and BRIN indexes
│   ├── partitions/                    # Partition management
│   │   ├── create-resource-partitions.sql
│   │   ├── create-history-partitions.sql
│   │   └── partition-maintenance.sql  # Scripts for adding/dropping partitions
│   ├── functions/                     # Stored procedures and functions
│   │   ├── history-functions.sql      # History management functions
│   │   └── maintenance-functions.sql  # Vacuum, analyze, etc.
│   ├── migrations/                    # Flyway migrations (versioned)
│   │   ├── V1__initial_schema.sql
│   │   ├── V2__add_search_index.sql
│   │   └── V3__add_sync_tables.sql
│   └── seeds/                         # Test/development data
│       ├── sample-patients.sql
│       └── sample-observations.sql
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── fhir4java-core/                    # Core FHIR processing engine
│   └── src/main/java/com/fhir4java/core/
│       ├── config/                    # Configuration classes
│       ├── fhir/                      # FHIR version support (R4B, R5)
│       ├── resource/                  # Resource registry & handling
│       ├── validation/                # Validation framework
│       └── exception/                 # Custom exceptions
├── fhir4java-persistence/             # JPA and database layer
│   └── src/main/java/com/fhir4java/persistence/
│       ├── entity/                    # JPA entities
│       ├── repository/                # Spring Data repositories
│       ├── schema/                    # Schema management
│       └── converter/                 # FHIR-to-Entity converters
├── fhir4java-api/                     # REST API layer
│   └── src/main/java/com/fhir4java/api/
│       ├── controller/                # REST controllers
│       ├── interceptor/               # Request/response interceptors
│       ├── filter/                    # Servlet filters
│       └── dto/                       # Data transfer objects
├── fhir4java-plugin/                  # Plugin framework
│   └── src/main/java/com/fhir4java/plugin/
│       ├── spi/                       # Plugin interfaces
│       ├── audit/                     # Audit plugin
│       ├── telemetry/                 # Telemetry plugin
│       └── performance/               # Performance tracking plugin
├── fhir4java-server/                  # Spring Boot application
│   └── src/main/
│       ├── java/
│       └── resources/
│           ├── application.yml
│           ├── fhir-config/           # FHIR configuration files
│           │   ├── resources/         # Resource configurations (YAML, version-agnostic)
│           │   │   ├── patient.yml    # Patient resource config (supports multiple versions)
│           │   │   ├── observation.yml
│           │   │   └── ...
│           │   ├── r5/                # FHIR R5 version-specific definitions
│           │   │   ├── capability.json    # R5 CapabilityStatement base config
│           │   │   ├── searchparameters/  # R5 SearchParameter JSON files
│           │   │   │   ├── SearchParameter-Resource-id.json
│           │   │   │   ├── SearchParameter-Resource-lastUpdated.json
│           │   │   │   ├── SearchParameter-DomainResource-text.json
│           │   │   │   ├── SearchParameter-Patient-*.json
│           │   │   │   └── ...
│           │   │   ├── operations/        # R5 OperationDefinition files
│           │   │   │   ├── OperationDefinition-Patient-merge.json
│           │   │   │   └── ...
│           │   │   └── profiles/          # R5 StructureDefinition files
│           │   │       ├── StructureDefinition-Patient.json
│           │   │       └── ...
│           │   └── r4b/               # FHIR R4B version-specific definitions
│           │       ├── capability.json    # R4B CapabilityStatement base config
│           │       ├── searchparameters/  # R4B SearchParameter JSON files
│           │       ├── operations/        # R4B OperationDefinition files
│           │       └── profiles/          # R4B StructureDefinition files
│           └── application-test.yml   # Test configuration
└── pom.xml                            # Parent POM
```

---

## Core Components Design

### 1. Resource Configuration System

Resource configurations are stored in `fhir-config/resources/` as YAML files. Each resource can support **multiple FHIR versions** with one version designated as the default.

**File: `fhir-config/resources/patient.yml`** (Example)
```yaml
resourceType: Patient
enabled: true

# Multiple FHIR versions support - one must be marked as default
fhirVersions:
  - version: R5
    default: true      # Default version when URL doesn't specify version
  - version: R4B
    default: false

schema:
  type: dedicated  # or 'shared'
  name: fhir_patient  # schema name if dedicated

interactions:
  read: true
  vread: true
  create: true
  update: true
  patch: true
  delete: false
  search: true
  history: true

# Search parameter restrictions (optional)
# If omitted, all search parameters defined in fhir-config/{version}/searchparameters/ are allowed
searchParameters:
  # Mode: 'allowlist' (only listed params allowed) or 'denylist' (all except listed)
  mode: allowlist
  # Common parameters (from SearchParameter-Resource-* and SearchParameter-DomainResource-*)
  common:
    - _id
    - _lastUpdated
    - _tag
    - _profile
    - _security
  # Resource-specific parameters (from SearchParameter-Patient-*)
  resourceSpecific:
    - identifier
    - family
    - given
    - name
    - birthdate
    - gender
    - address
    - telecom
    - email
    - phone
    - general-practitioner
    - organization
    - active
    - death-date
    - deceased
    - link

profiles:
  - url: http://hl7.org/fhir/StructureDefinition/Patient
    required: true
  - url: http://example.org/fhir/StructureDefinition/CustomPatient
    required: false

# NOTE: Search parameters are loaded from version-specific folders
# (fhir-config/r5/searchparameters/ or fhir-config/r4b/searchparameters/)
# but only those listed above will be allowed for search operations.
```

**File: `fhir-config/resources/observation.yml`** (Example with denylist mode)
```yaml
resourceType: Observation
enabled: true

fhirVersions:
  - version: R5
    default: true

schema:
  type: shared
  name: fhir_resources

interactions:
  read: true
  vread: true
  create: true
  update: true
  patch: false
  delete: false
  search: true
  history: true

# Search parameter restrictions using denylist mode
# All defined parameters allowed EXCEPT those listed
searchParameters:
  mode: denylist
  # Deny these common parameters
  common:
    - _text       # Full-text search not supported
    - _content    # Content search not supported
    - _filter     # Filter expressions not supported
  # Deny these resource-specific parameters
  resourceSpecific:
    - combo-code  # Complex combo searches not supported
```

**ResourceConfiguration Model:**
```java
@Data
public class ResourceConfiguration {
    private String resourceType;
    private boolean enabled;
    private List<FhirVersionConfig> fhirVersions;
    private SchemaConfig schema;
    private Map<InteractionType, Boolean> interactions;
    private SearchParameterConfig searchParameters;
    private List<ProfileConfig> profiles;

    /**
     * Get the default FHIR version for this resource.
     * If only one version configured, that is the default.
     * If multiple versions, returns the one marked as default.
     */
    public FhirVersion getDefaultVersion() {
        if (fhirVersions == null || fhirVersions.isEmpty()) {
            return FhirVersion.R5; // Global fallback
        }
        if (fhirVersions.size() == 1) {
            return fhirVersions.get(0).getVersion();
        }
        return fhirVersions.stream()
            .filter(FhirVersionConfig::isDefault)
            .map(FhirVersionConfig::getVersion)
            .findFirst()
            .orElse(fhirVersions.get(0).getVersion());
    }

    public boolean supportsVersion(FhirVersion version) {
        return fhirVersions.stream()
            .anyMatch(v -> v.getVersion() == version);
    }

    public Set<FhirVersion> getSupportedVersions() {
        return fhirVersions.stream()
            .map(FhirVersionConfig::getVersion)
            .collect(Collectors.toSet());
    }

    /**
     * Check if a search parameter is allowed for this resource.
     * @param paramName The search parameter name (e.g., "_id", "family", "birthdate")
     * @param isCommon True if this is a common parameter (Resource-* or DomainResource-*)
     * @return true if the parameter is allowed
     */
    public boolean isSearchParameterAllowed(String paramName, boolean isCommon) {
        if (searchParameters == null) {
            return true; // No restrictions = all allowed
        }
        return searchParameters.isAllowed(paramName, isCommon);
    }

    /**
     * Get list of enabled interactions.
     */
    public List<InteractionType> getEnabledInteractions() {
        return interactions.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
}

@Data
public class FhirVersionConfig {
    private FhirVersion version;
    @JsonProperty("default")
    private boolean defaultVersion;

    public boolean isDefault() {
        return defaultVersion;
    }
}

/**
 * Configuration for restricting allowed search parameters per resource.
 */
@Data
public class SearchParameterConfig {
    /**
     * Mode: 'allowlist' (only listed params allowed) or 'denylist' (all except listed)
     */
    private SearchParameterMode mode = SearchParameterMode.ALLOWLIST;

    /**
     * Common parameters (from SearchParameter-Resource-* and SearchParameter-DomainResource-*)
     */
    private List<String> common = new ArrayList<>();

    /**
     * Resource-specific parameters (from SearchParameter-<ResourceType>-*)
     */
    private List<String> resourceSpecific = new ArrayList<>();

    /**
     * Check if a search parameter is allowed based on mode and lists.
     */
    public boolean isAllowed(String paramName, boolean isCommon) {
        List<String> list = isCommon ? common : resourceSpecific;

        if (mode == SearchParameterMode.ALLOWLIST) {
            // Allowlist: parameter must be in the list
            return list.contains(paramName);
        } else {
            // Denylist: parameter must NOT be in the list
            return !list.contains(paramName);
        }
    }

    /**
     * Get all allowed parameter names (common + resourceSpecific).
     * Only applicable for allowlist mode.
     */
    public Set<String> getAllAllowedParameters() {
        Set<String> all = new HashSet<>();
        all.addAll(common);
        all.addAll(resourceSpecific);
        return all;
    }

    /**
     * Get all denied parameter names (common + resourceSpecific).
     * Only applicable for denylist mode.
     */
    public Set<String> getAllDeniedParameters() {
        Set<String> all = new HashSet<>();
        all.addAll(common);
        all.addAll(resourceSpecific);
        return all;
    }
}

public enum SearchParameterMode {
    ALLOWLIST,  // Only listed parameters are allowed
    DENYLIST    // All parameters allowed except those listed
}
```

### 1b. Search Parameter Configuration (Version-Specific JSON Files)

Search parameters are loaded from **version-specific folders** as individual FHIR SearchParameter JSON files (one search parameter per file), using the official HL7 FHIR definitions for each version.

**Version-Specific Paths:**
| FHIR Version | Path |
|--------------|------|
| R5 | `fhir-config/r5/searchparameters/` |
| R4B | `fhir-config/r4b/searchparameters/` |

**File Naming Convention:**
| Pattern | Description | Example |
|---------|-------------|---------|
| `SearchParameter-Resource-<param>.json` | Common params for ALL resources | `SearchParameter-Resource-id.json` |
| `SearchParameter-DomainResource-<param>.json` | Common params for DomainResource subtypes | `SearchParameter-DomainResource-text.json` |
| `SearchParameter-clinical-<param>.json` | Multi-resource clinical params | `SearchParameter-clinical-code.json` |
| `SearchParameter-<ResourceType>-<param>.json` | Resource-specific params | `SearchParameter-Patient-death-date.json` |

**Inheritance Rules:**
| Resource Type | Inherits Search Parameters From |
|--------------|--------------------------------|
| Bundle, Parameters, Binary | `SearchParameter-Resource-*` only |
| All other resources | `SearchParameter-Resource-*` AND `SearchParameter-DomainResource-*` AND applicable `SearchParameter-clinical-*` |

**Search Parameter Name:** The `name` element in the JSON file defines the search parameter name used in queries (e.g., `_id`, `_lastUpdated`, `death-date`).

**Note:** Each FHIR version may have different search parameters or different FHIRPath expressions for the same parameter. The registry loads parameters separately for each version.

---

**Example: `SearchParameter-Resource-id.json`** (Common parameter for ALL resources)
```json
{
  "resourceType": "SearchParameter",
  "id": "Resource-id",
  "url": "http://hl7.org/fhir/SearchParameter/Resource-id",
  "version": "5.0.0",
  "name": "_id",
  "status": "active",
  "description": "Logical id of this artifact",
  "code": "_id",
  "base": ["Resource"],
  "type": "token",
  "expression": "Resource.id",
  "processingMode": "normal"
}
```

**Example: `SearchParameter-Resource-lastUpdated.json`** (Common parameter for ALL resources)
```json
{
  "resourceType": "SearchParameter",
  "id": "Resource-lastUpdated",
  "url": "http://hl7.org/fhir/SearchParameter/Resource-lastUpdated",
  "version": "5.0.0",
  "name": "_lastUpdated",
  "status": "active",
  "description": "When the resource version last changed",
  "code": "_lastUpdated",
  "base": ["Resource"],
  "type": "date",
  "expression": "Resource.meta.lastUpdated",
  "processingMode": "normal",
  "comparator": ["eq", "ne", "gt", "ge", "lt", "le", "sa", "eb", "ap"]
}
```

**Example: `SearchParameter-DomainResource-text.json`** (Common parameter for DomainResource subtypes only)
```json
{
  "resourceType": "SearchParameter",
  "id": "DomainResource-text",
  "url": "http://hl7.org/fhir/SearchParameter/DomainResource-text",
  "version": "5.0.0",
  "name": "_text",
  "status": "draft",
  "description": "Search on the narrative of the resource",
  "code": "_text",
  "base": ["DomainResource"],
  "type": "special",
  "processingMode": "normal"
}
```

**Example: `SearchParameter-Patient-death-date.json`** (Resource-specific parameter)
```json
{
  "resourceType": "SearchParameter",
  "id": "Patient-death-date",
  "url": "http://hl7.org/fhir/SearchParameter/Patient-death-date",
  "version": "5.0.0",
  "name": "death-date",
  "status": "active",
  "description": "The date of death has been provided and satisfies this search value",
  "code": "death-date",
  "base": ["Patient"],
  "type": "date",
  "expression": "(Patient.deceased.ofType(dateTime))",
  "processingMode": "normal",
  "comparator": ["eq", "ne", "gt", "ge", "lt", "le", "sa", "eb", "ap"]
}
```

**Example: `SearchParameter-Patient-identifier.json`** (Resource-specific parameter with modifiers)
```json
{
  "resourceType": "SearchParameter",
  "id": "Patient-identifier",
  "url": "http://hl7.org/fhir/SearchParameter/Patient-identifier",
  "version": "5.0.0",
  "name": "identifier",
  "status": "active",
  "description": "A patient identifier",
  "code": "identifier",
  "base": ["Patient"],
  "type": "token",
  "expression": "Patient.identifier",
  "processingMode": "normal",
  "modifier": ["missing", "text", "not", "in", "not-in", "of-type"]
}
```

---

**Common Search Parameters (SearchParameter-Resource-*):**

| File | Parameter | Type | Description |
|------|-----------|------|-------------|
| `SearchParameter-Resource-id.json` | `_id` | token | Logical id of this artifact |
| `SearchParameter-Resource-lastUpdated.json` | `_lastUpdated` | date | When the resource version last changed |
| `SearchParameter-Resource-tag.json` | `_tag` | token | Tags applied to this resource |
| `SearchParameter-Resource-profile.json` | `_profile` | reference | Profiles this resource claims to conform to |
| `SearchParameter-Resource-security.json` | `_security` | token | Security Labels applied to this resource |
| `SearchParameter-Resource-source.json` | `_source` | uri | Identifies where the resource comes from |
| `SearchParameter-Resource-text.json` | `_text` | special | Text search against the narrative |
| `SearchParameter-Resource-content.json` | `_content` | special | Text search against the entire resource |
| `SearchParameter-Resource-list.json` | `_list` | special | Return resources on the list |
| `SearchParameter-Resource-has.json` | `_has` | special | Reverse chaining |
| `SearchParameter-Resource-type.json` | `_type` | special | Resource type filter (system-level search) |
| `SearchParameter-Resource-filter.json` | `_filter` | special | Filter expression |
| `SearchParameter-Resource-query.json` | `_query` | special | Named query |
| `SearchParameter-Resource-language.json` | `_language` | token | Language of the resource content |
| `SearchParameter-Resource-in.json` | `_in` | special | Inclusion in a compartment |

**DomainResource-specific Parameters (SearchParameter-DomainResource-*):**

| File | Parameter | Type | Description |
|------|-----------|------|-------------|
| `SearchParameter-DomainResource-text.json` | `_text` | special | Search on the narrative (DomainResource level) |

---

### 1c. Multi-Resource Clinical Search Parameters

FHIR defines common clinical search parameters that apply across multiple resource types. These are defined in `SearchParameter-clinical-*.json` files and contain **multiple FHIRPath expressions** (one per applicable resource type) in a single definition.

**Multi-Resource Clinical Parameters:**

| File | Parameter | Type | Applies To |
|------|-----------|------|------------|
| `SearchParameter-clinical-code.json` | `code` | token | AdverseEvent, AllergyIntolerance, Condition, Observation, Procedure, MedicationRequest, etc. (22+ resources) |
| `SearchParameter-clinical-date.json` | `date` | date | AdverseEvent, Appointment, CarePlan, Encounter, Observation, Procedure, etc. (27+ resources) |
| `SearchParameter-clinical-patient.json` | `patient` | reference | Account, Condition, Encounter, Observation, MedicationRequest, etc. (66+ resources) |
| `SearchParameter-clinical-encounter.json` | `encounter` | reference | CarePlan, Condition, Observation, Procedure, etc. |
| `SearchParameter-clinical-identifier.json` | `identifier` | token | Account, Condition, Patient, Observation, etc. |

**Example: `SearchParameter-clinical-date.json`**

This parameter defines the `date` search for multiple resources with resource-specific FHIRPath expressions:

```json
{
  "resourceType": "SearchParameter",
  "id": "clinical-date",
  "name": "date",
  "code": "date",
  "base": ["AdverseEvent", "AllergyIntolerance", "Appointment", "CarePlan",
           "Encounter", "Observation", "Procedure", ...],
  "type": "date",
  "expression": "AdverseEvent.occurrence.ofType(dateTime) | AdverseEvent.occurrence.ofType(Period) | AllergyIntolerance.recordedDate | CarePlan.period | Encounter.actualPeriod | Observation.effective.ofType(dateTime) | Observation.effective.ofType(Period) | Procedure.occurrence.ofType(dateTime) | ..."
}
```

**Expression Filtering:**

When a search is executed (e.g., `GET /Observation?date=2025-01-01`), the `SearchParameterRegistry` filters the multi-resource expression to extract only the paths applicable to the requested resource type:

- **Full expression:** `AdverseEvent.occurrence.ofType(dateTime) | ... | Observation.effective.ofType(dateTime) | Observation.effective.ofType(Period) | ...`
- **Filtered for Observation:** `Observation.effective.ofType(dateTime) | Observation.effective.ofType(Period)`

This filtering is performed by the `filterExpressionByResourceType()` method in `SearchParameterRegistry`:

```java
/**
 * Filters a FHIRPath expression to only include paths for the specified resource type.
 *
 * @param expression Full expression possibly containing multiple resource types
 * @param resourceType The resource type to filter for
 * @return Filtered expression containing only paths starting with the resource type
 */
private String filterExpressionByResourceType(String expression, String resourceType) {
    if (expression == null || expression.isEmpty()) {
        return expression;
    }

    // Split by pipe and filter for matching resource type
    String[] paths = expression.split("\\s*\\|\\s*");
    List<String> matchingPaths = new ArrayList<>();

    for (String path : paths) {
        // Check if path starts with the resource type
        if (path.trim().startsWith(resourceType + ".")) {
            matchingPaths.add(path.trim());
        }
    }

    // If we found matching paths, return them joined; otherwise return original
    if (!matchingPaths.isEmpty()) {
        return String.join(" | ", matchingPaths);
    }

    return expression;
}
```

**Commit:** `072f5d6` - Fix search parameter expression filtering for resource-specific queries

### 2. Resource Registry

The ResourceRegistry manages resource configurations and provides version-aware lookups.

```java
@Component
public class ResourceRegistry {
    private final Map<String, ResourceConfiguration> resources = new ConcurrentHashMap<>();
    private final FhirContextFactory fhirContextFactory;

    @Value("${fhir4java.server.default-version:R5}")
    private FhirVersion globalDefaultVersion;

    // Load from YAML configuration at startup
    public void registerResource(ResourceConfiguration config);

    public Optional<ResourceConfiguration> getResource(String resourceType);

    public List<ResourceConfiguration> getAllResources();

    /**
     * Get all resources that support a specific FHIR version.
     */
    public List<ResourceConfiguration> getResourcesForVersion(FhirVersion version) {
        return resources.values().stream()
            .filter(config -> config.supportsVersion(version))
            .collect(Collectors.toList());
    }

    /**
     * Check if interaction is enabled for a resource with specific FHIR version.
     */
    public boolean isInteractionEnabled(String resourceType, FhirVersion version, InteractionType type) {
        return getResource(resourceType)
            .filter(config -> config.supportsVersion(version))
            .map(config -> config.getInteractions().getOrDefault(type, false))
            .orElse(false);
    }

    /**
     * Get the default FHIR version for a resource type.
     * Falls back to global default if resource doesn't specify.
     */
    public FhirVersion getDefaultVersion(String resourceType) {
        return getResource(resourceType)
            .map(ResourceConfiguration::getDefaultVersion)
            .orElse(globalDefaultVersion);
    }

    /**
     * Check if a resource supports a specific FHIR version.
     */
    public boolean supportsVersion(String resourceType, FhirVersion version) {
        return getResource(resourceType)
            .map(config -> config.supportsVersion(version))
            .orElse(false);
    }

    /**
     * Get all FHIR versions supported by a resource.
     */
    public Set<FhirVersion> getSupportedVersions(String resourceType) {
        return getResource(resourceType)
            .map(ResourceConfiguration::getSupportedVersions)
            .orElse(Collections.emptySet());
    }

    /**
     * Check if a search parameter is allowed for a resource type.
     * @param resourceType The resource type (e.g., "Patient")
     * @param paramName The search parameter name (e.g., "_id", "family")
     * @param isCommon True if this is a common parameter (Resource-* or DomainResource-*)
     * @return true if the parameter is allowed (or no restrictions configured)
     */
    public boolean isSearchParameterAllowed(String resourceType, String paramName, boolean isCommon) {
        return getResource(resourceType)
            .map(config -> config.isSearchParameterAllowed(paramName, isCommon))
            .orElse(true); // No config = allow all
    }

    /**
     * Get the search parameter configuration for a resource type.
     * @return Optional SearchParameterConfig, empty if no restrictions configured
     */
    public Optional<SearchParameterConfig> getSearchParameterConfig(String resourceType) {
        return getResource(resourceType)
            .map(ResourceConfiguration::getSearchParameters);
    }

    /**
     * Check if a resource has search parameter restrictions configured.
     */
    public boolean hasSearchParameterRestrictions(String resourceType) {
        return getResource(resourceType)
            .map(config -> config.getSearchParameters() != null)
            .orElse(false);
    }
}
```

### 2b. Search Parameter Registry

Loads search parameters from **version-specific folders** as individual FHIR SearchParameter JSON files.

**Version-Specific Paths:**
| FHIR Version | Path |
|--------------|------|
| R5 | `fhir-config/r5/searchparameters/` |
| R4B | `fhir-config/r4b/searchparameters/` |

**File Naming Convention:**
- Resource-specific: `SearchParameter-<ResourceType>-<param-name>.json`
- Common parameters for all resources: `SearchParameter-Resource-<param-name>.json`
- Common parameters for DomainResource subtypes: `SearchParameter-DomainResource-<param-name>.json`
- Multi-resource clinical parameters: `SearchParameter-clinical-<param-name>.json`

**Inheritance Rules:**
| Resource Type | Inherits From |
|--------------|---------------|
| Bundle, Parameters, Binary | `SearchParameter-Resource-*` only |
| All other resources (DomainResource subtypes) | `SearchParameter-Resource-*` AND `SearchParameter-DomainResource-*` AND applicable `SearchParameter-clinical-*` |

**Note:** Multi-resource parameters (clinical-*) have a `base` array listing all applicable resource types. When retrieving expressions, the registry filters to return only paths matching the requested resource type (see `filterExpressionByResourceType()`).

```java
@Component
public class SearchParameterRegistry {
    private static final Logger log = LoggerFactory.getLogger(SearchParameterRegistry.class);
    private static final Set<String> NON_DOMAIN_RESOURCES = Set.of("Bundle", "Parameters", "Binary");

    private final FhirContextFactory fhirContextFactory;
    private final ResourceLoader resourceLoader;

    // Version-specific parameter storage
    private final Map<FhirVersion, List<SearchParameter>> resourceBaseParams = new ConcurrentHashMap<>();
    private final Map<FhirVersion, List<SearchParameter>> domainResourceParams = new ConcurrentHashMap<>();
    private final Map<FhirVersion, Map<String, List<SearchParameter>>> resourceSpecificParams = new ConcurrentHashMap<>();
    private final Map<FhirVersion, Map<String, SearchParameter>> parameterLookup = new ConcurrentHashMap<>();

    @Value("${fhir4java.config.base-path:classpath:fhir-config/}")
    private String configBasePath;

    @PostConstruct
    public void loadSearchParameters() throws IOException {
        // Load search parameters for each supported FHIR version
        for (FhirVersion version : FhirVersion.values()) {
            loadSearchParametersForVersion(version);
        }
    }

    private void loadSearchParametersForVersion(FhirVersion version) {
        String versionCode = version.getCode().toLowerCase(); // "r5" or "r4b"
        String versionPath = configBasePath + versionCode + "/searchparameters/";

        // Initialize maps for this version
        resourceBaseParams.put(version, new ArrayList<>());
        domainResourceParams.put(version, new ArrayList<>());
        resourceSpecificParams.put(version, new ConcurrentHashMap<>());
        parameterLookup.put(version, new ConcurrentHashMap<>());

        try {
            FhirContext ctx = fhirContextFactory.getContext(version);
            IParser parser = ctx.newJsonParser();

            Resource[] resources = resourceLoader.getResources(versionPath + "SearchParameter-*.json");

            for (Resource resource : resources) {
                loadSearchParameterFile(version, parser, resource);
            }

            log.info("Loaded search parameters for FHIR {}: {} Resource base, {} DomainResource, {} resource-specific",
                version.getCode(),
                resourceBaseParams.get(version).size(),
                domainResourceParams.get(version).size(),
                resourceSpecificParams.get(version).values().stream().mapToInt(List::size).sum());

        } catch (FileNotFoundException e) {
            log.warn("No search parameters found for FHIR {} at {}", version.getCode(), versionPath);
        } catch (IOException e) {
            log.error("Error loading search parameters for FHIR {}: {}", version.getCode(), e.getMessage());
        }
    }

    private void loadSearchParameterFile(FhirVersion version, IParser parser, Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            SearchParameter sp = parser.parseResource(SearchParameter.class, is);
            String filename = resource.getFilename();

            if (filename.startsWith("SearchParameter-Resource-")) {
                resourceBaseParams.get(version).add(sp);
            } else if (filename.startsWith("SearchParameter-DomainResource-")) {
                domainResourceParams.get(version).add(sp);
            } else {
                // Resource-specific parameter
                for (var base : sp.getBase()) {
                    String resourceType = base.getCode();
                    resourceSpecificParams.get(version)
                        .computeIfAbsent(resourceType, k -> new ArrayList<>())
                        .add(sp);
                    parameterLookup.get(version).put(resourceType + ":" + sp.getName(), sp);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load search parameter from {}: {}", resource.getFilename(), e.getMessage());
        }
    }

    /**
     * Get all search parameters applicable to a resource type for a specific FHIR version.
     */
    public List<SearchParameter> getSearchParameters(FhirVersion version, String resourceType) {
        List<SearchParameter> params = new ArrayList<>();

        // 1. Add Resource base parameters
        params.addAll(resourceBaseParams.getOrDefault(version, Collections.emptyList()));

        // 2. Add DomainResource parameters (only for DomainResource subtypes)
        if (!NON_DOMAIN_RESOURCES.contains(resourceType)) {
            params.addAll(domainResourceParams.getOrDefault(version, Collections.emptyList()));
        }

        // 3. Add resource-specific parameters
        Map<String, List<SearchParameter>> specificParams = resourceSpecificParams.get(version);
        if (specificParams != null) {
            params.addAll(specificParams.getOrDefault(resourceType, Collections.emptyList()));
        }

        return params;
    }

    /**
     * Get a specific search parameter by version, resource type, and parameter name.
     */
    public Optional<SearchParameter> getSearchParameter(FhirVersion version, String resourceType, String paramName) {
        // Check lookup cache first
        Map<String, SearchParameter> lookup = parameterLookup.get(version);
        if (lookup != null) {
            SearchParameter cached = lookup.get(resourceType + ":" + paramName);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        // Search in base parameters
        for (SearchParameter sp : resourceBaseParams.getOrDefault(version, Collections.emptyList())) {
            if (sp.getName().equals(paramName)) {
                return Optional.of(sp);
            }
        }

        // Search in DomainResource parameters (if applicable)
        if (!NON_DOMAIN_RESOURCES.contains(resourceType)) {
            for (SearchParameter sp : domainResourceParams.getOrDefault(version, Collections.emptyList())) {
                if (sp.getName().equals(paramName)) {
                    return Optional.of(sp);
                }
            }
        }

        // Search in resource-specific parameters
        Map<String, List<SearchParameter>> specificParams = resourceSpecificParams.get(version);
        if (specificParams != null) {
            return specificParams.getOrDefault(resourceType, Collections.emptyList())
                .stream()
                .filter(sp -> sp.getName().equals(paramName))
                .findFirst();
        }

        return Optional.empty();
    }

    /**
     * Check if a search parameter is defined for a resource type and version.
     */
    public boolean isSearchParameterDefined(FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName).isPresent();
    }

    /**
     * Get the search parameter type for a version.
     */
    public Optional<Enumerations.SearchParamType> getSearchParameterType(
            FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName)
            .map(SearchParameter::getType);
    }

    /**
     * Get the FHIRPath expression for a search parameter.
     * For multi-resource parameters (e.g., clinical-date, clinical-code),
     * filters the expression to only include paths for the requested resource type.
     */
    public Optional<String> getSearchParameterExpression(
            FhirVersion version, String resourceType, String paramName) {
        return getSearchParameter(version, resourceType, paramName)
            .map(sp -> filterExpressionByResourceType(sp.getExpression(), resourceType));
    }

    /**
     * Filters a FHIRPath expression to only include paths for the specified resource type.
     * This is essential for multi-resource search parameters like clinical-date and clinical-code.
     */
    private String filterExpressionByResourceType(String expression, String resourceType) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        String[] paths = expression.split("\\s*\\|\\s*");
        List<String> matchingPaths = new ArrayList<>();

        for (String path : paths) {
            if (path.trim().startsWith(resourceType + ".")) {
                matchingPaths.add(path.trim());
            }
        }

        return !matchingPaths.isEmpty() ? String.join(" | ", matchingPaths) : expression;
    }

    public boolean isDomainResource(String resourceType) {
        return !NON_DOMAIN_RESOURCES.contains(resourceType);
    }

    /**
     * Get allowed search parameters for a resource type, filtered by resource configuration.
     * This method respects the allowlist/denylist restrictions defined in the resource YAML.
     *
     * @param version The FHIR version
     * @param resourceType The resource type
     * @param resourceRegistry The ResourceRegistry to check configuration
     * @return List of allowed SearchParameters only
     */
    public List<SearchParameter> getAllowedSearchParameters(
            FhirVersion version, String resourceType, ResourceRegistry resourceRegistry) {

        List<SearchParameter> allParams = getSearchParameters(version, resourceType);

        // If no restrictions configured, return all parameters
        Optional<SearchParameterConfig> configOpt = resourceRegistry.getSearchParameterConfig(resourceType);
        if (configOpt.isEmpty()) {
            return allParams;
        }

        SearchParameterConfig config = configOpt.get();

        // Filter parameters based on configuration
        return allParams.stream()
            .filter(sp -> {
                String paramName = sp.getName();
                boolean isCommon = isCommonParameter(sp);
                return config.isAllowed(paramName, isCommon);
            })
            .collect(Collectors.toList());
    }

    /**
     * Check if a search parameter is a common parameter (Resource-* or DomainResource-*).
     */
    private boolean isCommonParameter(SearchParameter sp) {
        if (sp.getBase() == null || sp.getBase().isEmpty()) {
            return false;
        }
        // Check if base includes "Resource" or "DomainResource"
        return sp.getBase().stream()
            .anyMatch(base -> "Resource".equals(base.getCode()) ||
                             "DomainResource".equals(base.getCode()));
    }

    /**
     * Validate that a search parameter is allowed for a resource type.
     * Used during search request validation.
     *
     * @param version The FHIR version
     * @param resourceType The resource type
     * @param paramName The parameter name to validate
     * @param resourceRegistry The ResourceRegistry to check configuration
     * @return true if the parameter is allowed
     */
    public boolean isSearchParameterAllowed(
            FhirVersion version, String resourceType, String paramName,
            ResourceRegistry resourceRegistry) {

        // First check if the parameter is defined
        Optional<SearchParameter> spOpt = getSearchParameter(version, resourceType, paramName);
        if (spOpt.isEmpty()) {
            return false; // Parameter not defined at all
        }

        // Check resource configuration restrictions
        Optional<SearchParameterConfig> configOpt = resourceRegistry.getSearchParameterConfig(resourceType);
        if (configOpt.isEmpty()) {
            return true; // No restrictions = allowed
        }

        SearchParameterConfig config = configOpt.get();
        boolean isCommon = isCommonParameter(spOpt.get());
        return config.isAllowed(paramName, isCommon);
    }
}
```

### 2c. CapabilityStatement Generator

The server dynamically generates a **version-specific** CapabilityStatement based on:
- Registered resources that support the requested FHIR version
- **Allowed** search parameters from `fhir-config/{version}/searchparameters/` (filtered by resource config)
- Version-specific operations from `fhir-config/{version}/operations/`
- Base capability config from `fhir-config/{version}/capability.json`

```java
@Component
public class CapabilityStatementGenerator {
    private final ResourceRegistry resourceRegistry;
    private final SearchParameterRegistry searchParameterRegistry;
    private final ExtendedOperationRegistry operationRegistry;
    private final ServerProperties serverProperties;
    private final ResourceLoader resourceLoader;

    @Value("${fhir4java.config.base-path:classpath:fhir-config/}")
    private String configBasePath;

    /**
     * Generate a CapabilityStatement for a specific FHIR version.
     * Only includes resources that support the requested version.
     * Only includes search parameters that are allowed per resource configuration.
     */
    public CapabilityStatement generate(FhirVersion version) {
        CapabilityStatement cs = new CapabilityStatement();

        // Load base capability config from version-specific path
        loadBaseCapability(cs, version);

        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setDate(new Date());
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        cs.setFhirVersion(version.toEnumeration());
        cs.setFormat(Arrays.asList(new CodeType("json"), new CodeType("xml")));
        cs.setSoftware(buildSoftwareComponent());
        cs.setImplementation(buildImplementationComponent(version));

        // Build REST component
        CapabilityStatement.CapabilityStatementRestComponent rest = cs.addRest();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        // Add only resources that support this FHIR version
        for (ResourceConfiguration config : resourceRegistry.getResourcesForVersion(version)) {
            CapabilityStatement.CapabilityStatementRestResourceComponent resource = rest.addResource();
            resource.setType(config.getResourceType());

            // Add enabled interactions
            for (InteractionType interaction : config.getEnabledInteractions()) {
                resource.addInteraction().setCode(interaction.toRestfulInteraction());
            }

            // Add ALLOWED search parameters only (filtered by resource configuration)
            // Uses getAllowedSearchParameters() which respects allowlist/denylist settings
            for (SearchParameter param : searchParameterRegistry.getAllowedSearchParameters(
                    version, config.getResourceType(), resourceRegistry)) {
                CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent sp = resource.addSearchParam();
                sp.setName(param.getName());
                sp.setType(param.getType());
                sp.setDefinition(param.getUrl());
                sp.setDocumentation(param.getDescription());
            }

            // Add version-specific extended operations
            for (OperationDefinition op : operationRegistry.getOperationsForResource(version, config.getResourceType())) {
                resource.addOperation()
                    .setName(op.getCode())
                    .setDefinition(op.getUrl());
            }
        }

        return cs;
    }

    private void loadBaseCapability(CapabilityStatement cs, FhirVersion version) {
        String capabilityPath = configBasePath + version.getCode().toLowerCase() + "/capability.json";
        try {
            Resource resource = resourceLoader.getResource(capabilityPath);
            // Load and merge base capability settings (name, publisher, description, etc.)
        } catch (Exception e) {
            log.debug("No base capability.json found for {}, using defaults", version.getCode());
        }
    }

    private CapabilityStatement.CapabilityStatementImplementationComponent buildImplementationComponent(FhirVersion version) {
        CapabilityStatement.CapabilityStatementImplementationComponent impl =
            new CapabilityStatement.CapabilityStatementImplementationComponent();
        impl.setDescription("FHIR4Java Server - " + version.getCode());
        impl.setUrl(serverProperties.getBaseUrl() + "/" + version.getCode().toLowerCase());
        return impl;
    }
}
```

### 3. Validation Framework

```java
public interface FhirValidator {
    ValidationResult validateResource(IBaseResource resource, String profileUrl);
    ValidationResult validateSearchParameters(FhirVersion version, String resourceType, Map<String, String> params);
    ValidationResult validateOperation(String resourceType, String operationName, Parameters params);
}

@Component
public class ProfileValidator implements FhirValidator {
    private final FhirContext fhirContext;
    private final IValidationSupport validationSupport;

    // Uses HAPI FHIR's FhirValidator with StructureDefinition support
}

/**
 * Validates search parameters against:
 * 1. Parameter existence in SearchParameterRegistry (is it a valid FHIR search parameter?)
 * 2. Resource configuration restrictions (is it allowed for this resource?)
 */
@Component
public class SearchParameterValidator {
    private final SearchParameterRegistry searchParameterRegistry;
    private final ResourceRegistry resourceRegistry;

    /**
     * Validate all search parameters in a search request.
     * Returns ValidationResult with errors for any invalid or disallowed parameters.
     */
    public ValidationResult validateSearchParameters(
            FhirVersion version, String resourceType, Map<String, String> params) {

        List<ValidationIssue> issues = new ArrayList<>();

        for (String paramName : params.keySet()) {
            // Skip special parameters that don't need validation
            if (isSpecialParameter(paramName)) {
                continue;
            }

            // Extract base parameter name (remove modifiers like :exact, :contains)
            String baseParamName = extractBaseParamName(paramName);

            // Check 1: Is this parameter defined in the SearchParameterRegistry?
            if (!searchParameterRegistry.isSearchParameterDefined(version, resourceType, baseParamName)) {
                issues.add(ValidationIssue.error(
                    "Unknown search parameter '" + baseParamName + "' for resource " + resourceType));
                continue;
            }

            // Check 2: Is this parameter allowed per resource configuration?
            if (!searchParameterRegistry.isSearchParameterAllowed(
                    version, resourceType, baseParamName, resourceRegistry)) {
                issues.add(ValidationIssue.error(
                    "Search parameter '" + baseParamName + "' is not allowed for resource " + resourceType +
                    ". Check the resource configuration."));
            }
        }

        return new ValidationResult(issues.isEmpty(), issues);
    }

    /**
     * Extract base parameter name from parameter with modifiers.
     * e.g., "name:exact" -> "name", "birthdate:missing" -> "birthdate"
     */
    private String extractBaseParamName(String paramName) {
        int colonIndex = paramName.indexOf(':');
        return colonIndex > 0 ? paramName.substring(0, colonIndex) : paramName;
    }

    /**
     * Check if parameter is a special/control parameter that doesn't need validation.
     * e.g., _count, _sort, _include, _revinclude, _summary, _elements, _format
     */
    private boolean isSpecialParameter(String paramName) {
        return paramName.equals("_count") ||
               paramName.equals("_offset") ||
               paramName.equals("_sort") ||
               paramName.equals("_include") ||
               paramName.equals("_revinclude") ||
               paramName.equals("_summary") ||
               paramName.equals("_elements") ||
               paramName.equals("_format") ||
               paramName.equals("_pretty") ||
               paramName.equals("_total") ||
               paramName.equals("_contained") ||
               paramName.equals("_containedType");
    }
}

@Data
@AllArgsConstructor
public class ValidationResult {
    private final boolean valid;
    private final List<ValidationIssue> issues;

    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult failure(List<ValidationIssue> issues) {
        return new ValidationResult(false, issues);
    }
}

@Data
@AllArgsConstructor
public class ValidationIssue {
    private final IssueSeverity severity;
    private final String message;

    public static ValidationIssue error(String message) {
        return new ValidationIssue(IssueSeverity.ERROR, message);
    }

    public static ValidationIssue warning(String message) {
        return new ValidationIssue(IssueSeverity.WARNING, message);
    }
}

public enum IssueSeverity {
    ERROR, WARNING, INFORMATION
}
```

### 4. Interaction Guard

```java
@Component
public class InteractionGuard {
    private final ResourceRegistry registry;

    /**
     * Validate that an interaction is enabled for a resource type and FHIR version.
     */
    public void validateInteraction(String resourceType, FhirVersion version, InteractionType type)
        throws InteractionDisabledException, VersionNotSupportedException {

        // Check if resource supports this FHIR version
        if (!registry.supportsVersion(resourceType, version)) {
            throw new VersionNotSupportedException(resourceType, version);
        }

        // Check if interaction is enabled
        if (!registry.isInteractionEnabled(resourceType, version, type)) {
            throw new InteractionDisabledException(resourceType, type);
        }
    }
}
```

### 5. API Layer - FHIR Version Resolution

The API layer handles both **versioned** (`/fhir/r5/Patient`) and **unversioned** (`/fhir/Patient`) URL patterns. Unversioned requests are forwarded to the resource's default FHIR version.

**Supported URL Patterns:**
| URL Pattern | Behavior |
|-------------|----------|
| `/fhir/r5/Patient` | Explicit R5 version |
| `/fhir/r4b/Patient` | Explicit R4B version |
| `/fhir/Patient` | Forward to resource's default version |
| `/fhir/Patient/123` | Forward to resource's default version |
| `/fhir/r5/Patient/$merge` | R5 extended operation |
| `/fhir/Patient/$merge` | Extended operation on default version |

**FhirVersionResolver:**
```java
@Component
public class FhirVersionResolver {
    private final ResourceRegistry resourceRegistry;

    private static final Pattern VERSIONED_PATH =
        Pattern.compile("^/fhir/(r4b|r5)/(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNVERSIONED_PATH =
        Pattern.compile("^/fhir/([A-Z][a-zA-Z]+)(.*)$");

    /**
     * Resolve FHIR version from request URL.
     * Returns version and whether it was explicitly specified.
     */
    public ResolvedVersion resolve(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Check for explicit version in path (/fhir/r5/... or /fhir/r4b/...)
        Matcher versionedMatcher = VERSIONED_PATH.matcher(path);
        if (versionedMatcher.matches()) {
            String versionCode = versionedMatcher.group(1).toUpperCase();
            FhirVersion version = FhirVersion.fromCode(versionCode);
            String remainingPath = versionedMatcher.group(2);
            return new ResolvedVersion(version, true, remainingPath);
        }

        // No version in path - extract resource type and get default
        Matcher unversionedMatcher = UNVERSIONED_PATH.matcher(path);
        if (unversionedMatcher.matches()) {
            String resourceType = unversionedMatcher.group(1);
            String remainingPath = resourceType + unversionedMatcher.group(2);
            FhirVersion defaultVersion = resourceRegistry.getDefaultVersion(resourceType);
            return new ResolvedVersion(defaultVersion, false, remainingPath);
        }

        // Fallback for metadata and other endpoints
        return new ResolvedVersion(FhirVersion.R5, false, path);
    }
}

@Data
@AllArgsConstructor
public class ResolvedVersion {
    private FhirVersion version;
    private boolean explicit;       // True if version was specified in URL
    private String resourcePath;    // Path after version (e.g., "Patient/123")

    public String getVersionCode() {
        return version.getCode().toLowerCase();
    }
}
```

**FhirVersionFilter (Request Interceptor):**
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FhirVersionFilter extends OncePerRequestFilter {
    private final FhirVersionResolver versionResolver;
    private final ResourceRegistry resourceRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ResolvedVersion resolved = versionResolver.resolve(request);
        String resourceType = extractResourceType(resolved.getResourcePath());

        // Validate resource supports this FHIR version
        if (resourceType != null && !resourceRegistry.supportsVersion(resourceType, resolved.getVersion())) {
            writeOperationOutcome(response, HttpServletResponse.SC_BAD_REQUEST,
                "Resource " + resourceType + " does not support FHIR " + resolved.getVersion().getCode());
            return;
        }

        // Store resolved version in request attributes for downstream use
        request.setAttribute("fhirVersion", resolved.getVersion());
        request.setAttribute("fhirVersionExplicit", resolved.isExplicit());
        request.setAttribute("fhirResourcePath", resolved.getResourcePath());

        // Add response header indicating actual version used
        response.setHeader("X-FHIR-Version", resolved.getVersion().getCode());

        filterChain.doFilter(request, response);
    }

    private String extractResourceType(String path) {
        // Extract resource type from path like "Patient/123" or "Patient"
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("[/?]");
        return parts.length > 0 ? parts[0] : null;
    }

    private void writeOperationOutcome(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/fhir+json");
        // Write OperationOutcome JSON
    }
}
```

**FhirResourceController (Updated):**
```java
@RestController
@RequestMapping("/fhir")
public class FhirResourceController {
    private final ResourceService resourceService;

    /**
     * Read resource - handles both versioned and unversioned paths.
     * Version is resolved by FhirVersionFilter and stored in request attribute.
     */
    @GetMapping({"/{version}/{resourceType}/{id}", "/{resourceType}/{id}"})
    public ResponseEntity<IBaseResource> read(
            @PathVariable(required = false) String version,
            @PathVariable String resourceType,
            @PathVariable String id,
            HttpServletRequest request) {

        FhirVersion fhirVersion = (FhirVersion) request.getAttribute("fhirVersion");
        return resourceService.read(fhirVersion, resourceType, id);
    }

    /**
     * Search resources.
     */
    @GetMapping({"/{version}/{resourceType}", "/{resourceType}"})
    public ResponseEntity<Bundle> search(
            @PathVariable(required = false) String version,
            @PathVariable String resourceType,
            @RequestParam Map<String, String> searchParams,
            HttpServletRequest request) {

        FhirVersion fhirVersion = (FhirVersion) request.getAttribute("fhirVersion");
        return resourceService.search(fhirVersion, resourceType, searchParams);
    }

    /**
     * Create resource.
     */
    @PostMapping({"/{version}/{resourceType}", "/{resourceType}"})
    public ResponseEntity<IBaseResource> create(
            @PathVariable(required = false) String version,
            @PathVariable String resourceType,
            @RequestBody String resourceJson,
            HttpServletRequest request) {

        FhirVersion fhirVersion = (FhirVersion) request.getAttribute("fhirVersion");
        return resourceService.create(fhirVersion, resourceType, resourceJson);
    }

    /**
     * CapabilityStatement / Metadata endpoint.
     */
    @GetMapping({"/{version}/metadata", "/metadata"})
    public ResponseEntity<CapabilityStatement> metadata(
            @PathVariable(required = false) String version,
            HttpServletRequest request) {

        FhirVersion fhirVersion = (FhirVersion) request.getAttribute("fhirVersion");
        return ResponseEntity.ok(capabilityStatementGenerator.generate(fhirVersion));
    }
}
```

---

## Plugin Architecture (Detailed Design)

### Plugin Implementation Options

The FHIR server supports **two plugin implementation approaches**:

| Approach | Use Case | Language | Deployment | Hot-Reload |
|----------|----------|----------|------------|------------|
| **Spring Bean** | Embedded plugins, same JVM | Java only | Classpath | No (restart required) |
| **MCP Protocol** | External plugins, any language | Any (Java, Python, Node.js, etc.) | Separate process/container | Yes |

### Option 1: Spring Bean Plugins (Embedded)

Traditional Spring-based plugins running in the same JVM. Best for:
- Performance-critical plugins (no IPC overhead)
- Tight integration with Spring ecosystem
- Simple deployment (single artifact)

### Option 2: MCP-Based Plugins (External)

Using the [Model Context Protocol](https://modelcontextprotocol.io/) for external plugin communication. Best for:
- Polyglot plugins (Python ML models, Node.js integrations, etc.)
- Hot-pluggable without server restart
- Isolated plugin failures (won't crash main server)
- Third-party plugin ecosystem

**MCP Java SDK:** [Official SDK](https://github.com/modelcontextprotocol/java-sdk) maintained with Spring AI team.

```xml
<!-- Maven dependency for MCP -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.9.0</version>
</dependency>

<!-- Spring Boot starter for MCP -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

### Plugin Categories

The plugin system is organized into two main categories:

1. **Technical Plugins** (Infrastructure/Cross-cutting concerns)
   - Authentication Plugin
   - Authorization Plugin
   - Cache Plugin
   - Audit Plugin
   - Telemetry Plugin
   - Performance Tracking Plugin

2. **Business Logic Plugins** (Domain-specific logic)
   - Resource-specific business rules
   - Workflow orchestration
   - Custom validations

### Plugin Execution Modes

```java
public enum PluginExecutionMode {
    SYNCHRONOUS,   // Blocks until complete - required for auth, cache, validation
    ASYNCHRONOUS   // Fire-and-forget or async/await - suitable for audit, telemetry
}
```

### Plugin Execution Pipeline

The plugin execution pipeline supports both **CRUD operations** (read, create, update, delete, search) and **Extended Operations** (e.g., `$merge`, `$validate`, `$everything`). Business logic plugins can be configured to execute before and/or after any operation type.

#### Operation Types

```java
public enum OperationType {
    // Standard CRUD interactions
    READ,           // GET /Patient/123
    VREAD,          // GET /Patient/123/_history/1
    CREATE,         // POST /Patient
    UPDATE,         // PUT /Patient/123
    PATCH,          // PATCH /Patient/123
    DELETE,         // DELETE /Patient/123
    SEARCH,         // GET /Patient?name=smith
    HISTORY,        // GET /Patient/123/_history

    // Extended Operations (prefixed with $)
    OPERATION;      // POST /Patient/$merge, GET /Patient/123/$everything

    public boolean isExtendedOperation() {
        return this == OPERATION;
    }
}
```

#### CRUD Operations Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      CRUD OPERATIONS PROCESSING PIPELINE                         │
│              (read, vread, create, update, patch, delete, search)               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐      │
│  │ Performance  │──▶│Authentication│──▶│Authorization │──▶│    Cache     │      │
│  │   Start      │   │   Plugin     │   │   Plugin     │   │   Plugin     │      │
│  │   (SYNC)     │   │   (SYNC)     │   │   (SYNC)     │   │   (SYNC)     │      │
│  └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘      │
│         │                  │                  │                  │               │
│         │                  │                  │                  ▼               │
│         │                  │                  │          ┌──────────────┐        │
│         │                  │                  │          │ Cache Hit?   │        │
│         │                  │                  │          └──────┬───────┘        │
│         │                  │                  │           Yes   │   No           │
│         │                  │                  │            ▼    │    ▼           │
│         │                  │                  │     [Return]    │  [Continue]    │
│         │                  │                  │                  │               │
│         │                  │                  │                  ▼               │
│         │           ┌──────────────────────────────────────────────────┐        │
│         │           │         BUSINESS LOGIC PLUGINS (BEFORE)           │        │
│         │           │  ┌─────────┐  ┌─────────┐  ┌─────────┐           │        │
│         │           │  │Plugin 1 │─▶│Plugin 2 │─▶│Plugin N │ (ordered) │        │
│         │           │  │ (SYNC)  │  │ (SYNC)  │  │ (SYNC)  │           │        │
│         │           │  └─────────┘  └─────────┘  └─────────┘           │        │
│         │           └──────────────────────────────────────────────────┘        │
│         │                              │                                         │
│         │                              ▼                                         │
│         │                   ┌────────────────────┐                              │
│         │                   │   CORE OPERATION   │                              │
│         │                   │ (Read/Write/Search)│                              │
│         │                   └────────────────────┘                              │
│         │                              │                                         │
│         │           ┌──────────────────────────────────────────────────┐        │
│         │           │         BUSINESS LOGIC PLUGINS (AFTER)            │        │
│         │           │  ┌─────────┐  ┌─────────┐  ┌─────────┐           │        │
│         │           │  │Plugin 1 │─▶│Plugin 2 │─▶│Plugin N │ (ordered) │        │
│         │           │  │(SYNC/A) │  │(SYNC/A) │  │(SYNC/A) │           │        │
│         │           │  └─────────┘  └─────────┘  └─────────┘           │        │
│         │           └──────────────────────────────────────────────────┘        │
│         │                              │                                         │
│         ▼                              ▼                                         │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐      │
│  │ Performance  │◀──│    Cache     │◀──│   Audit      │◀──│  Telemetry   │      │
│  │    End       │   │   Update     │   │   Plugin     │   │   Plugin     │      │
│  │   (SYNC)     │   │   (SYNC)     │   │   (ASYNC)    │   │   (ASYNC)    │      │
│  └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘      │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Extended Operations Pipeline

Extended operations (e.g., `$merge`, `$validate`, `$everything`) follow a similar pipeline but with operation-specific validation and handling.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                      EXTENDED OPERATION PROCESSING PIPELINE                          │
│                    (e.g., POST /fhir/r5/Patient/$merge)                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                      │
│  1. REQUEST ENTRY & SECURITY                                                         │
│     ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                          │
│     │ Performance  │──▶│Authentication│──▶│Authorization │                          │
│     │   Start      │   │   Plugin     │   │   Plugin     │                          │
│     │   (SYNC)     │   │   (SYNC)     │   │   (SYNC)     │                          │
│     └──────────────┘   └──────────────┘   └──────────────┘                          │
│                                                  │                                   │
│  2. OPERATION RESOLUTION                         ▼                                   │
│     ┌──────────────────────────────────────────────────────────────┐                │
│     │  ExtendedOperationRegistry.getHandler(resourceType, opCode)  │                │
│     │  - Load OperationDefinition from fhir-config/operations/     │                │
│     │  - Validate operation is enabled for resource type           │                │
│     │  - Check instance-level vs type-level invocation             │                │
│     └──────────────────────────────────────────────────────────────┘                │
│                                                  │                                   │
│  3. INPUT VALIDATION                             ▼                                   │
│     ┌──────────────────────────────────────────────────────────────┐                │
│     │  Validate input Parameters against OperationDefinition       │                │
│     │  - Required parameters present (min cardinality)             │                │
│     │  - Parameter types match definition                          │                │
│     │  - Max cardinality constraints satisfied                     │                │
│     │  - Profile validation if specified                           │                │
│     └──────────────────────────────────────────────────────────────┘                │
│                                                  │                                   │
│  4. BUSINESS LOGIC PLUGINS (BEFORE)              ▼                                   │
│     ┌──────────────────────────────────────────────────────────────┐                │
│     │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │                │
│     │  │  Plugin 1   │─▶│  Plugin 2   │─▶│  Plugin N   │ (ordered)│                │
│     │  │ beforeOp()  │  │ beforeOp()  │  │ beforeOp()  │          │                │
│     │  └─────────────┘  └─────────────┘  └─────────────┘          │                │
│     │                                                              │                │
│     │  Can perform:                                                │                │
│     │  - Validate business rules (e.g., user can merge patients)  │                │
│     │  - Enrich/transform input parameters                        │                │
│     │  - Check preconditions (e.g., both patients exist)          │                │
│     │  - ABORT operation by returning PluginResult.failure()      │                │
│     └──────────────────────────────────────────────────────────────┘                │
│                                                  │                                   │
│  5. CORE OPERATION EXECUTION                     ▼                                   │
│     ┌──────────────────────────────────────────────────────────────┐                │
│     │           ExtendedOperationHandler.execute(context)          │                │
│     │                                                              │                │
│     │  - Execute the operation-specific logic                      │                │
│     │  - May use InternalOperationExecutor for:                    │                │
│     │    • Database reads/writes                                   │                │
│     │    • Internal searches                                       │                │
│     │    • Patch operations                                        │                │
│     │  - Returns output Parameters or Resource                     │                │
│     └──────────────────────────────────────────────────────────────┘                │
│                                                  │                                   │
│  6. BUSINESS LOGIC PLUGINS (AFTER)               ▼                                   │
│     ┌──────────────────────────────────────────────────────────────┐                │
│     │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │                │
│     │  │  Plugin 1   │─▶│  Plugin 2   │─▶│  Plugin N   │ (ordered)│                │
│     │  │ afterOp()   │  │ afterOp()   │  │ afterOp()   │          │                │
│     │  └─────────────┘  └─────────────┘  └─────────────┘          │                │
│     │                                                              │                │
│     │  Can perform:                                                │                │
│     │  - Transform/enrich output result                           │                │
│     │  - Trigger side effects (notifications, webhooks)           │                │
│     │  - Sync to external systems                                 │                │
│     │  - Post-operation validation/logging                        │                │
│     │  - CANNOT abort (operation already completed)               │                │
│     └──────────────────────────────────────────────────────────────┘                │
│                                                  │                                   │
│  7. RESPONSE & CLEANUP                           ▼                                   │
│     ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                          │
│     │  Telemetry   │──▶│    Audit     │──▶│ Performance  │                          │
│     │   (ASYNC)    │   │   (ASYNC)    │   │    End       │                          │
│     └──────────────┘   └──────────────┘   └──────────────┘                          │
│                                                                                      │
│  Audit captures: operation name, input params, output, duration, user               │
│                                                                                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Base Plugin Interfaces

```java
// Base plugin interface with execution mode support
public interface FhirPlugin {
    String getName();
    int getOrder();  // Lower number = higher priority
    PluginExecutionMode getExecutionMode();
    void initialize(PluginContext context);
    void shutdown();
}

// Plugin context for accessing shared resources
public interface PluginContext {
    FhirContextFactory getFhirContextFactory();
    ResourceRegistry getResourceRegistry();
    ApplicationContext getSpringContext();
    <T> T getConfiguration(String key, Class<T> type);
}

// Plugin execution result
public class PluginResult {
    private final boolean success;
    private final boolean continueChain;  // false to short-circuit
    private final String message;
    private final Map<String, Object> metadata;
}
```

### Technical Plugin Interfaces

#### Authentication Plugin

```java
public interface AuthenticationPlugin extends FhirPlugin {

    /**
     * Authenticate the incoming request.
     * @return AuthenticationResult with principal if successful
     * @throws AuthenticationException if authentication fails
     */
    AuthenticationResult authenticate(RequestContext request);

    /**
     * Check if this plugin can handle the authentication method
     */
    boolean supports(String authenticationScheme);
}

@Data
public class AuthenticationResult {
    private final boolean authenticated;
    private final Principal principal;
    private final Set<String> roles;
    private final Map<String, Object> claims;
}

// Example implementations
@Component
@Order(100)
public class JwtAuthenticationPlugin implements AuthenticationPlugin {
    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }
    // Validate JWT tokens
}

@Component
@Order(200)
public class BasicAuthenticationPlugin implements AuthenticationPlugin {
    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }
    // Validate Basic auth credentials
}
```

#### Authorization Plugin

```java
public interface AuthorizationPlugin extends FhirPlugin {

    /**
     * Authorize the operation for the authenticated principal.
     * @return AuthorizationResult
     */
    AuthorizationResult authorize(AuthorizationContext context);
}

@Data
public class AuthorizationContext {
    private final Principal principal;
    private final String resourceType;
    private final String resourceId;
    private final InteractionType interaction;
    private final Map<String, String> searchParameters;
}

@Data
public class AuthorizationResult {
    private final boolean authorized;
    private final String reason;
    private final Set<String> allowedFields;  // For field-level security
}

// Example: SMART on FHIR authorization
@Component
public class SmartAuthorizationPlugin implements AuthorizationPlugin {
    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }
    // Enforce SMART on FHIR scopes
}
```

#### Cache Plugin

```java
public interface CachePlugin extends FhirPlugin {

    /**
     * Get cached resource if available
     */
    Optional<CachedResource> get(CacheKey key);

    /**
     * Store resource in cache
     */
    void put(CacheKey key, IBaseResource resource, CacheOptions options);

    /**
     * Invalidate cache entry
     */
    void invalidate(CacheKey key);

    /**
     * Invalidate all entries for a resource type
     */
    void invalidateByResourceType(String resourceType);

    /**
     * Check if caching is enabled for this resource type
     */
    boolean isCacheable(String resourceType, InteractionType interaction);
}

@Data
public class CacheKey {
    private final String resourceType;
    private final String resourceId;
    private final String versionId;
    private final String fhirVersion;
}

@Data
public class CacheOptions {
    private final Duration ttl;
    private final boolean cacheOnRead;
    private final boolean cacheOnWrite;
}

// In-Memory Cache Implementation (for dev/testing)
@Component
@Profile("dev")
@ConditionalOnProperty(name = "fhir4java.cache.provider", havingValue = "memory")
public class InMemoryCachePlugin implements CachePlugin {
    private final Cache<CacheKey, CachedResource> cache;

    public InMemoryCachePlugin(@Value("${fhir4java.cache.max-size:1000}") int maxSize,
                               @Value("${fhir4java.cache.ttl-minutes:60}") int ttlMinutes) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
            .build();
    }

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public Optional<CachedResource> get(CacheKey key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void put(CacheKey key, IBaseResource resource, CacheOptions options) {
        cache.put(key, new CachedResource(resource, Instant.now()));
    }
}

// Redis Cache Implementation (for production)
@Component
@Profile("!dev")
@ConditionalOnProperty(name = "fhir4java.cache.provider", havingValue = "redis")
public class RedisCachePlugin implements CachePlugin {
    private final RedisTemplate<String, String> redisTemplate;
    private final FhirContextFactory fhirContextFactory;

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public Optional<CachedResource> get(CacheKey key) {
        String json = redisTemplate.opsForValue().get(toRedisKey(key));
        if (json == null) return Optional.empty();

        IParser parser = fhirContextFactory.getContext(FhirVersion.R5).newJsonParser();
        IBaseResource resource = parser.parseResource(json);
        return Optional.of(new CachedResource(resource, Instant.now()));
    }

    @Override
    public void put(CacheKey key, IBaseResource resource, CacheOptions options) {
        IParser parser = fhirContextFactory.getContext(FhirVersion.R5).newJsonParser();
        String json = parser.encodeResourceToString(resource);
        redisTemplate.opsForValue().set(toRedisKey(key), json, options.getTtl());
    }

    private String toRedisKey(CacheKey key) {
        return String.format("fhir:%s:%s:%s", key.getResourceType(), key.getResourceId(), key.getFhirVersion());
    }
}
```

#### Audit Plugin

The audit plugin captures comprehensive information for each interaction type:
- **CREATE**: Resource ID and version created
- **READ**: Resource ID and version read
- **UPDATE**: Before and after resource ID/version (captures both states)
- **DELETE**: Resource ID and version deleted
- **SEARCH**: Search parameters used
- **Extended Operation**: Operation name and input parameters

Payloads are stored in a separate table to keep the audit log table efficient.

```java
public interface AuditPlugin extends FhirPlugin {

    void onRequestStart(AuditContext context);
    void onRequestComplete(AuditContext context, OperationResult result);
    void onError(AuditContext context, Throwable error);
}

@Data
public class AuditContext {
    private final UUID requestId;
    private final Instant timestamp;
    private final Principal principal;
    private final String clientIp;
    private final String resourceType;
    private final InteractionType interaction;
    private final Map<String, String> requestHeaders;

    // For READ/CREATE/DELETE
    private String resourceId;
    private Integer resourceVersion;

    // For UPDATE - before and after states
    private String beforeResourceId;
    private Integer beforeResourceVersion;
    private String afterResourceId;
    private Integer afterResourceVersion;

    // For SEARCH
    private Map<String, String> searchParameters;

    // For Extended Operations
    private String operationName;
    private Map<String, Object> operationParameters;

    // Request/Response payloads (stored separately)
    private String requestPayload;
    private String responsePayload;
}

@Data
public class AuditRecord {
    // Core fields
    private UUID requestId;
    private Instant timestamp;
    private String userId;
    private String clientIp;
    private String resourceType;
    private InteractionType interaction;
    private String outcome;
    private Long durationMs;

    // Resource version tracking
    private String resourceId;
    private Integer resourceVersion;
    private String beforeResourceId;
    private Integer beforeResourceVersion;
    private String afterResourceId;
    private Integer afterResourceVersion;

    // Search parameters (JSON)
    private String searchParametersJson;

    // Extended operation details
    private String operationName;
    private String operationParametersJson;

    // Reference to payload table
    private UUID payloadId;
}

@Component
public class DatabaseAuditPlugin implements AuditPlugin {
    private final AuditLogRepository auditLogRepository;
    private final AuditPayloadRepository payloadRepository;
    private final ExecutorService asyncExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public PluginExecutionMode getExecutionMode() { return ASYNCHRONOUS; }

    @Override
    public void onRequestComplete(AuditContext context, OperationResult result) {
        asyncExecutor.submit(() -> {
            // Store payload in separate table
            UUID payloadId = null;
            if (context.getRequestPayload() != null || context.getResponsePayload() != null) {
                AuditPayloadEntity payload = AuditPayloadEntity.builder()
                    .requestId(context.getRequestId())
                    .requestPayload(context.getRequestPayload())
                    .responsePayload(context.getResponsePayload())
                    .createdAt(Instant.now())
                    .build();
                payloadId = payloadRepository.save(payload).getId();
            }

            // Build audit record based on interaction type
            AuditLogEntity.AuditLogEntityBuilder builder = AuditLogEntity.builder()
                .requestId(context.getRequestId())
                .timestamp(context.getTimestamp())
                .userId(context.getPrincipal().getName())
                .clientIp(context.getClientIp())
                .resourceType(context.getResourceType())
                .interaction(context.getInteraction().name())
                .outcome(result.isSuccess() ? "SUCCESS" : "FAILURE")
                .durationMs(result.getDurationMs())
                .payloadId(payloadId);

            switch (context.getInteraction()) {
                case READ, DELETE -> builder
                    .resourceId(context.getResourceId())
                    .resourceVersion(context.getResourceVersion());

                case CREATE -> builder
                    .resourceId(result.getResourceId())
                    .resourceVersion(result.getResourceVersion());

                case UPDATE -> builder
                    .beforeResourceId(context.getBeforeResourceId())
                    .beforeResourceVersion(context.getBeforeResourceVersion())
                    .afterResourceId(result.getResourceId())
                    .afterResourceVersion(result.getResourceVersion());

                case SEARCH -> builder
                    .searchParametersJson(toJson(context.getSearchParameters()));

                case OPERATION -> builder
                    .operationName(context.getOperationName())
                    .operationParametersJson(toJson(context.getOperationParameters()));
            }

            auditLogRepository.save(builder.build());
        });
    }
}
```

**Audit Database Schema:**

```sql
-- Main audit log table (efficient, no large payloads)
CREATE TABLE fhir_audit.audit_log (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id VARCHAR(255),
    client_ip VARCHAR(45),
    resource_type VARCHAR(100),
    interaction VARCHAR(50) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    duration_ms BIGINT,

    -- Resource version tracking
    resource_id VARCHAR(64),
    resource_version INTEGER,

    -- For UPDATE: before and after states
    before_resource_id VARCHAR(64),
    before_resource_version INTEGER,
    after_resource_id VARCHAR(64),
    after_resource_version INTEGER,

    -- For SEARCH: search parameters as JSON
    search_parameters_json JSONB,

    -- For OPERATION: operation details
    operation_name VARCHAR(100),
    operation_parameters_json JSONB,

    -- Reference to payload (stored separately)
    payload_id UUID,

    CONSTRAINT fk_audit_payload FOREIGN KEY (payload_id)
        REFERENCES fhir_audit.audit_payload(id)
);

-- Separate table for request/response payloads
CREATE TABLE fhir_audit.audit_payload (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for common queries
CREATE INDEX idx_audit_log_timestamp ON fhir_audit.audit_log(timestamp);
CREATE INDEX idx_audit_log_resource ON fhir_audit.audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_user ON fhir_audit.audit_log(user_id);
CREATE INDEX idx_audit_log_request ON fhir_audit.audit_log(request_id);
```
```

#### Telemetry Plugin (OTEL Support with Tracing Levels)

The telemetry plugin supports OpenTelemetry (OTEL) with configurable tracing levels:

**Tracing Levels:**
- **MINIMAL**: Just record request start/end and outcome
- **STANDARD**: Record different steps executed for the API services (auth, cache, validation, business logic, core operation)
- **DETAILED**: Include detailed SQL statements, FHIR parsing times, and internal method calls
- **FULL**: Everything including request/response payloads and all intermediate states

```java
public enum TracingLevel {
    MINIMAL,    // Request start/end only
    STANDARD,   // API steps (auth, cache, validation, business, core)
    DETAILED,   // + SQL statements, parsing times
    FULL        // + payloads, all intermediate states
}

public interface TelemetryPlugin extends FhirPlugin {

    // Metrics
    void recordMetric(String name, double value, Map<String, String> tags);
    void recordCounter(String name, long increment, Map<String, String> tags);
    void recordHistogram(String name, double value, Map<String, String> tags);

    // Tracing
    Span startSpan(String operationName, SpanContext parentContext);
    void endSpan(Span span, Map<String, Object> attributes);
    void addSpanEvent(Span span, String eventName, Map<String, Object> attributes);

    // SQL tracing (for DETAILED level)
    void recordSqlStatement(Span parentSpan, String sql, long durationMs, int rowCount);

    // Current tracing level
    TracingLevel getTracingLevel();
}

@Component
@ConditionalOnProperty(name = "fhir4java.telemetry.provider", havingValue = "otel")
public class OpenTelemetryPlugin implements TelemetryPlugin {
    private final Tracer tracer;
    private final Meter meter;
    private final TracingLevel tracingLevel;

    public OpenTelemetryPlugin(
            OpenTelemetry openTelemetry,
            @Value("${fhir4java.telemetry.tracing-level:STANDARD}") TracingLevel tracingLevel) {
        this.tracer = openTelemetry.getTracer("fhir4java");
        this.meter = openTelemetry.getMeter("fhir4java");
        this.tracingLevel = tracingLevel;
    }

    @Override
    public PluginExecutionMode getExecutionMode() { return ASYNCHRONOUS; }

    @Override
    public Span startSpan(String operationName, SpanContext parentContext) {
        SpanBuilder builder = tracer.spanBuilder(operationName);
        if (parentContext != null) {
            builder.setParent(Context.current().with(Span.wrap(parentContext)));
        }
        return builder.startSpan();
    }

    @Override
    public void endSpan(Span span, Map<String, Object> attributes) {
        if (tracingLevel.ordinal() >= TracingLevel.STANDARD.ordinal()) {
            attributes.forEach((k, v) -> span.setAttribute(k, String.valueOf(v)));
        }
        span.end();
    }

    @Override
    public void recordSqlStatement(Span parentSpan, String sql, long durationMs, int rowCount) {
        if (tracingLevel.ordinal() >= TracingLevel.DETAILED.ordinal()) {
            Span sqlSpan = tracer.spanBuilder("db.query")
                .setParent(Context.current().with(parentSpan))
                .setAttribute("db.statement", sql)
                .setAttribute("db.duration_ms", durationMs)
                .setAttribute("db.row_count", rowCount)
                .startSpan();
            sqlSpan.end();
        }
    }

    @Override
    public void recordCounter(String name, long increment, Map<String, String> tags) {
        LongCounter counter = meter.counterBuilder(name).build();
        Attributes attributes = buildAttributes(tags);
        counter.add(increment, attributes);
    }

    @Override
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        DoubleHistogram histogram = meter.histogramBuilder(name).build();
        Attributes attributes = buildAttributes(tags);
        histogram.record(value, attributes);
    }
}

// SQL Statement Interceptor for DETAILED tracing
@Component
@ConditionalOnProperty(name = "fhir4java.telemetry.tracing-level", havingValue = "DETAILED")
public class SqlTracingInterceptor implements StatementInspector {
    private final TelemetryPlugin telemetryPlugin;
    private final ThreadLocal<Span> currentSpan = new ThreadLocal<>();

    @Override
    public String inspect(String sql) {
        Span span = currentSpan.get();
        if (span != null && telemetryPlugin.getTracingLevel().ordinal() >= TracingLevel.DETAILED.ordinal()) {
            long startTime = System.currentTimeMillis();
            // SQL will be recorded after execution via afterQuery callback
        }
        return sql;
    }
}

// Telemetry context holder for propagating spans
@Component
public class TelemetryContextHolder {
    private static final ThreadLocal<TelemetryContext> context = new ThreadLocal<>();

    @Data
    public static class TelemetryContext {
        private Span rootSpan;
        private Span currentSpan;
        private TracingLevel level;
        private Map<String, Object> attributes = new HashMap<>();
    }

    public void setContext(TelemetryContext ctx) { context.set(ctx); }
    public TelemetryContext getContext() { return context.get(); }
    public void clear() { context.remove(); }
}
```

**Telemetry Configuration:**

```yaml
fhir4java:
  telemetry:
    enabled: true
    provider: otel  # or 'micrometer' for basic metrics only
    tracing-level: STANDARD  # MINIMAL, STANDARD, DETAILED, FULL

    # OTEL Configuration
    otel:
      service-name: fhir4java-server
      exporter:
        type: otlp  # or 'jaeger', 'zipkin'
        endpoint: http://localhost:4317
      sampling:
        rate: 1.0  # 100% for dev, lower for production

    # What to trace at each level
    trace-config:
      MINIMAL:
        - request-lifecycle
      STANDARD:
        - request-lifecycle
        - authentication
        - authorization
        - cache-lookup
        - validation
        - business-plugins
        - core-operation
      DETAILED:
        - all-from-standard
        - sql-statements
        - fhir-parsing
        - search-index-queries
      FULL:
        - all-from-detailed
        - request-payload
        - response-payload
        - intermediate-states
```
```

#### Performance Tracking Plugin

```java
public interface PerformancePlugin extends FhirPlugin {

    TraceContext startTrace(String operationName, RequestContext request);
    void recordStep(TraceContext context, String stepName, Runnable action);
    <T> T recordStep(TraceContext context, String stepName, Supplier<T> action);
    void endTrace(TraceContext context, OperationResult result);
}

@Data
public class TraceContext {
    private final UUID traceId;
    private final UUID requestId;
    private final String operationName;
    private final Instant startTime;
    private final List<TraceStep> steps = new ArrayList<>();
}

@Data
public class TraceStep {
    private final String name;
    private final int order;
    private final Instant startTime;
    private final Duration duration;
    private final Map<String, Object> metadata;
}

@Component
public class DatabasePerformancePlugin implements PerformancePlugin {
    private final TraceLogRepository traceLogRepository;

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public <T> T recordStep(TraceContext context, String stepName, Supplier<T> action) {
        Instant start = Instant.now();
        try {
            return action.get();
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            context.getSteps().add(new TraceStep(stepName, context.getSteps().size(), start, duration, Map.of()));
        }
    }
}
```

### Business Logic Plugin Interface

The BusinessLogicPlugin interface supports both CRUD operations and Extended Operations. Plugins can register to handle specific resource types, operation types, and/or specific extended operation codes.

```java
public interface BusinessLogicPlugin extends FhirPlugin {

    /**
     * Check if this plugin handles the given operation context.
     * For CRUD operations: check resourceType and operationType
     * For extended operations: also check operationCode (e.g., "merge", "validate")
     */
    boolean supports(OperationDescriptor descriptor);

    /**
     * Execute before the core operation.
     * Can modify the input, validate business rules, or abort the operation.
     *
     * @param context Contains request info, resource, parameters, and shared attributes
     * @return PluginResult - failure() to abort, success() to continue
     */
    PluginResult beforeOperation(BusinessContext context);

    /**
     * Execute after the core operation.
     * Can modify/enrich the result or trigger side effects.
     * Cannot abort the operation (already completed).
     *
     * @param context The operation context
     * @param result The result from core operation execution
     * @return PluginResult with optional modifications to the result
     */
    PluginResult afterOperation(BusinessContext context, OperationResult result);
}

/**
 * Describes the operation being performed - used for plugin routing.
 */
@Data
public class OperationDescriptor {
    private final String resourceType;           // e.g., "Patient"
    private final OperationType operationType;   // e.g., CREATE, UPDATE, OPERATION
    private final String operationCode;          // e.g., "merge", "validate" (null for CRUD)
    private final boolean instanceLevel;         // true for /Patient/123/$merge

    public boolean isExtendedOperation() {
        return operationType == OperationType.OPERATION;
    }

    // Factory methods for common cases
    public static OperationDescriptor crud(String resourceType, OperationType type) {
        return new OperationDescriptor(resourceType, type, null, type != OperationType.CREATE);
    }

    public static OperationDescriptor extendedOperation(String resourceType, String opCode, boolean instanceLevel) {
        return new OperationDescriptor(resourceType, OperationType.OPERATION, opCode, instanceLevel);
    }
}

/**
 * Context passed to business logic plugins for both CRUD and extended operations.
 */
@Data
public class BusinessContext {
    private final RequestContext request;        // HTTP request details, principal, headers
    private final String resourceType;           // e.g., "Patient"
    private final String resourceId;             // Resource ID (null for type-level operations)
    private final OperationType operationType;   // CRUD type or OPERATION for extended ops
    private final String operationCode;          // Extended operation code (e.g., "merge"), null for CRUD
    private final IBaseResource resource;        // Input resource (for create/update)
    private final Parameters inputParameters;    // Input parameters (for extended operations)
    private final Map<String, Object> attributes;  // Shared across plugins in the chain

    // Convenience methods
    public boolean isExtendedOperation() {
        return operationType == OperationType.OPERATION;
    }

    public OperationDescriptor getDescriptor() {
        return new OperationDescriptor(resourceType, operationType, operationCode, resourceId != null);
    }

    // Get typed parameter from input (for extended operations)
    @SuppressWarnings("unchecked")
    public <T extends IBase> Optional<T> getInputParameter(String name, Class<T> type) {
        if (inputParameters == null) return Optional.empty();
        return inputParameters.getParameter().stream()
            .filter(p -> name.equals(p.getName()))
            .map(p -> (T) p.getValue())
            .findFirst();
    }
}

/**
 * Result from core operation execution, passed to afterOperation plugins.
 */
@Data
public class OperationResult {
    private final boolean success;
    private final IBaseResource outputResource;      // Output resource (read, create result, etc.)
    private final Parameters outputParameters;       // Output parameters (for extended operations)
    private final Bundle searchResults;              // Search results (for search operations)
    private final OperationOutcome operationOutcome; // Errors/warnings
    private final Map<String, Object> metadata;      // Additional metadata

    // Convenience factory methods
    public static OperationResult success(IBaseResource resource) {
        return new OperationResult(true, resource, null, null, null, Map.of());
    }

    public static OperationResult successWithParams(Parameters params) {
        return new OperationResult(true, null, params, null, null, Map.of());
    }

    public static OperationResult failure(OperationOutcome outcome) {
        return new OperationResult(false, null, null, null, outcome, Map.of());
    }
}
```

#### Example: CRUD Business Logic Plugin (Patient Consent)

```java
@Component
@Order(100)  // Execute first among business plugins
public class PatientConsentPlugin implements BusinessLogicPlugin {

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public boolean supports(OperationDescriptor descriptor) {
        // Supports Patient CREATE and UPDATE (CRUD operations only)
        return "Patient".equals(descriptor.getResourceType()) &&
               !descriptor.isExtendedOperation() &&
               (descriptor.getOperationType() == OperationType.CREATE ||
                descriptor.getOperationType() == OperationType.UPDATE);
    }

    @Override
    public PluginResult beforeOperation(BusinessContext context) {
        Patient patient = (Patient) context.getResource();
        // Validate consent requirements
        if (!hasRequiredConsent(patient)) {
            return PluginResult.failure("Patient consent not provided");
        }
        return PluginResult.success();
    }

    @Override
    public PluginResult afterOperation(BusinessContext context, OperationResult result) {
        // No post-processing needed
        return PluginResult.success();
    }
}
```

#### Example: Extended Operation Business Logic Plugin (Patient $merge)

```java
@Component
@Order(100)
public class PatientMergeValidationPlugin implements BusinessLogicPlugin {

    private final PatientRepository patientRepository;

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public boolean supports(OperationDescriptor descriptor) {
        // Only supports Patient $merge extended operation
        return "Patient".equals(descriptor.getResourceType()) &&
               descriptor.isExtendedOperation() &&
               "merge".equals(descriptor.getOperationCode());
    }

    @Override
    public PluginResult beforeOperation(BusinessContext context) {
        // Extract source and target patient references from input parameters
        Optional<Reference> sourceRef = context.getInputParameter("source-patient", Reference.class);
        Optional<Reference> targetRef = context.getInputParameter("target-patient", Reference.class);

        if (sourceRef.isEmpty() || targetRef.isEmpty()) {
            return PluginResult.failure("Both source-patient and target-patient are required");
        }

        // Business rule: Cannot merge patient with themselves
        if (sourceRef.get().getReference().equals(targetRef.get().getReference())) {
            return PluginResult.failure("Cannot merge patient with themselves");
        }

        // Business rule: Both patients must exist and be active
        String sourceId = extractId(sourceRef.get());
        String targetId = extractId(targetRef.get());

        Optional<Patient> source = patientRepository.findById(sourceId);
        Optional<Patient> target = patientRepository.findById(targetId);

        if (source.isEmpty()) {
            return PluginResult.failure("Source patient not found: " + sourceId);
        }
        if (target.isEmpty()) {
            return PluginResult.failure("Target patient not found: " + targetId);
        }
        if (!Boolean.TRUE.equals(source.get().getActive())) {
            return PluginResult.failure("Source patient is not active");
        }

        // Store resolved patients in context for use by operation handler
        context.getAttributes().put("sourcePatient", source.get());
        context.getAttributes().put("targetPatient", target.get());

        return PluginResult.success();
    }

    @Override
    public PluginResult afterOperation(BusinessContext context, OperationResult result) {
        if (result.isSuccess()) {
            // Trigger notification after successful merge
            Patient sourcePatient = (Patient) context.getAttributes().get("sourcePatient");
            Patient targetPatient = (Patient) context.getAttributes().get("targetPatient");

            // Log the merge for compliance
            log.info("Patient merge completed: {} -> {}",
                sourcePatient.getIdElement().getIdPart(),
                targetPatient.getIdElement().getIdPart());

            // Could trigger webhook notifications here
            // notificationService.notifyPatientMerge(sourcePatient, targetPatient);
        }
        return PluginResult.success();
    }
}
```

#### Example: Extended Operation Business Logic Plugin ($validate)

```java
@Component
@Order(100)
public class CustomValidationPlugin implements BusinessLogicPlugin {

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public boolean supports(OperationDescriptor descriptor) {
        // Supports $validate on any resource type
        return descriptor.isExtendedOperation() &&
               "validate".equals(descriptor.getOperationCode());
    }

    @Override
    public PluginResult beforeOperation(BusinessContext context) {
        // Add custom validation rules before standard validation
        IBaseResource resource = context.getInputParameter("resource", IBaseResource.class)
            .orElse(null);

        if (resource == null) {
            return PluginResult.failure("Resource parameter is required for validation");
        }

        // Store for afterOperation enrichment
        context.getAttributes().put("validationStartTime", Instant.now());
        return PluginResult.success();
    }

    @Override
    public PluginResult afterOperation(BusinessContext context, OperationResult result) {
        // Enrich validation output with custom metadata
        Parameters output = result.getOutputParameters();
        if (output != null) {
            Instant startTime = (Instant) context.getAttributes().get("validationStartTime");
            Duration duration = Duration.between(startTime, Instant.now());

            // Add validation timing to output
            output.addParameter()
                .setName("validation-duration-ms")
                .setValue(new IntegerType((int) duration.toMillis()));
        }
        return PluginResult.success();
    }
}
```

#### Example: Observation Auto-Enrichment Plugin (CRUD)

```java
@Component
@Order(200)
public class ObservationEnrichmentPlugin implements BusinessLogicPlugin {

    @Override
    public PluginExecutionMode getExecutionMode() { return SYNCHRONOUS; }

    @Override
    public boolean supports(OperationDescriptor descriptor) {
        return "Observation".equals(descriptor.getResourceType()) &&
               !descriptor.isExtendedOperation() &&
               descriptor.getOperationType() == OperationType.CREATE;
    }

    @Override
    public PluginResult beforeOperation(BusinessContext context) {
        Observation obs = (Observation) context.getResource();
        // Auto-populate interpretation based on value
        enrichInterpretation(obs);
        return PluginResult.success();
    }
}
```

### Plugin Orchestrator

The Plugin Orchestrator manages the execution pipeline for both CRUD operations and Extended Operations.

```java
@Component
public class PluginOrchestrator {

    private final List<AuthenticationPlugin> authPlugins;
    private final List<AuthorizationPlugin> authzPlugins;
    private final List<CachePlugin> cachePlugins;
    private final List<AuditPlugin> auditPlugins;
    private final List<TelemetryPlugin> telemetryPlugins;
    private final List<PerformancePlugin> performancePlugins;
    private final List<BusinessLogicPlugin> businessPlugins;
    private final ExtendedOperationRegistry operationRegistry;
    private final ExecutorService asyncExecutor;

    @PostConstruct
    public void initialize() {
        // Sort all plugin lists by order
        sortPluginsByOrder(authPlugins);
        sortPluginsByOrder(authzPlugins);
        sortPluginsByOrder(cachePlugins);
        sortPluginsByOrder(businessPlugins);
        // ... etc
    }

    /**
     * Execute a CRUD operation (read, create, update, delete, search).
     */
    public OperationResult executeCrudRequest(RequestContext request, Supplier<OperationResult> coreOperation) {
        OperationDescriptor descriptor = OperationDescriptor.crud(
            request.getResourceType(),
            request.getOperationType()
        );
        return executeRequest(request, descriptor, null, coreOperation);
    }

    /**
     * Execute an Extended Operation (e.g., $merge, $validate, $everything).
     */
    public OperationResult executeExtendedOperation(
            RequestContext request,
            String operationCode,
            Parameters inputParameters,
            ExtendedOperationHandler handler) {

        OperationDescriptor descriptor = OperationDescriptor.extendedOperation(
            request.getResourceType(),
            operationCode,
            request.getResourceId() != null  // instance-level if resourceId present
        );

        return executeRequest(request, descriptor, inputParameters, () -> {
            OperationContext opContext = createOperationContext(request, inputParameters);
            IBaseResource result = handler.execute(opContext);

            if (result instanceof Parameters) {
                return OperationResult.successWithParams((Parameters) result);
            } else {
                return OperationResult.success(result);
            }
        });
    }

    /**
     * Core execution pipeline - handles both CRUD and Extended Operations.
     */
    private OperationResult executeRequest(
            RequestContext request,
            OperationDescriptor descriptor,
            Parameters inputParameters,
            Supplier<OperationResult> coreOperation) {

        TraceContext trace = null;
        AuditContext auditContext = createAuditContext(request, descriptor);

        try {
            // 1. Start performance tracking
            trace = startPerformanceTrace(request);

            // 2. Authentication (SYNC - must complete before proceeding)
            AuthenticationResult authResult = executeAuthentication(request, trace);
            request.setPrincipal(authResult.getPrincipal());

            // 3. Authorization (SYNC)
            executeAuthorization(request, descriptor, trace);

            // 4. For extended operations: validate input parameters
            if (descriptor.isExtendedOperation()) {
                validateOperationParameters(descriptor.getOperationCode(), inputParameters, trace);
            }

            // 5. Check cache (SYNC) - only for read operations, not extended operations
            if (!descriptor.isExtendedOperation() && isReadOperation(request)) {
                Optional<CachedResource> cached = checkCache(request, trace);
                if (cached.isPresent()) {
                    return OperationResult.success(cached.get().getResource());
                }
            }

            // 6. Business logic - BEFORE (SYNC, multiple plugins in order)
            BusinessContext bizContext = createBusinessContext(request, descriptor, inputParameters);
            executeBusinessPluginsBefore(bizContext, descriptor, trace);

            // 7. Core operation execution
            OperationResult result = recordStep(trace, "core-operation", coreOperation);

            // 8. Business logic - AFTER (SYNC/ASYNC based on plugin config)
            executeBusinessPluginsAfter(bizContext, descriptor, result, trace);

            // 9. Update cache (SYNC) - only for CRUD operations
            if (!descriptor.isExtendedOperation()) {
                updateCache(request, result, trace);
            }

            // 10. Audit (ASYNC) - captures operation details including extended operation info
            recordAudit(auditContext, result, descriptor);

            // 11. Telemetry (ASYNC)
            recordTelemetry(request, result, descriptor);

            return result;

        } catch (BusinessRuleException e) {
            // Business rule violation - return OperationOutcome
            recordAuditError(auditContext, e);
            return OperationResult.failure(createOperationOutcome(e));
        } catch (Exception e) {
            recordAuditError(auditContext, e);
            throw e;
        } finally {
            endPerformanceTrace(trace);
        }
    }

    /**
     * Execute business logic plugins BEFORE the core operation.
     * Any plugin can abort the operation by returning PluginResult.failure().
     */
    private void executeBusinessPluginsBefore(
            BusinessContext bizContext,
            OperationDescriptor descriptor,
            TraceContext trace) {

        for (BusinessLogicPlugin plugin : businessPlugins) {
            if (plugin.supports(descriptor)) {
                PluginResult result = recordStep(trace,
                    "business-before-" + plugin.getName(),
                    () -> plugin.beforeOperation(bizContext));

                if (!result.isSuccess()) {
                    throw new BusinessRuleException(result.getMessage());
                }
                if (!result.isContinueChain()) {
                    break;  // Plugin requested to stop chain (but continue operation)
                }
            }
        }
    }

    /**
     * Execute business logic plugins AFTER the core operation.
     * Plugins can modify the result or trigger side effects.
     * Cannot abort the operation (already completed).
     */
    private void executeBusinessPluginsAfter(
            BusinessContext bizContext,
            OperationDescriptor descriptor,
            OperationResult result,
            TraceContext trace) {

        for (BusinessLogicPlugin plugin : businessPlugins) {
            if (plugin.supports(descriptor)) {
                if (plugin.getExecutionMode() == ASYNCHRONOUS) {
                    // Run async plugins in background
                    asyncExecutor.submit(() -> {
                        try {
                            plugin.afterOperation(bizContext, result);
                        } catch (Exception e) {
                            log.warn("Async after-plugin {} failed: {}",
                                plugin.getName(), e.getMessage());
                        }
                    });
                } else {
                    // Run sync plugins inline
                    recordStep(trace,
                        "business-after-" + plugin.getName(),
                        () -> plugin.afterOperation(bizContext, result));
                }
            }
        }
    }

    /**
     * Create BusinessContext for plugin execution.
     */
    private BusinessContext createBusinessContext(
            RequestContext request,
            OperationDescriptor descriptor,
            Parameters inputParameters) {

        return new BusinessContext(
            request,
            descriptor.getResourceType(),
            request.getResourceId(),
            descriptor.getOperationType(),
            descriptor.getOperationCode(),
            request.getResource(),          // For CRUD create/update
            inputParameters,                // For extended operations
            new ConcurrentHashMap<>()       // Shared attributes across plugins
        );
    }

    /**
     * Validate extended operation input parameters against OperationDefinition.
     */
    private void validateOperationParameters(
            String operationCode,
            Parameters inputParameters,
            TraceContext trace) {

        recordStep(trace, "validate-operation-params", () -> {
            operationRegistry.validateOperationParameters(operationCode, inputParameters);
            return null;
        });
    }
}
```

### Plugin Configuration

```yaml
fhir4java:
  plugins:
    # Authentication
    authentication:
      enabled: true
      providers:
        - type: jwt
          order: 100
          config:
            issuer: https://auth.example.org
            audience: fhir-server
        - type: basic
          order: 200
          config:
            realm: FHIR Server

    # Authorization
    authorization:
      enabled: true
      provider: smart-on-fhir
      config:
        enforce-scopes: true

    # Cache
    cache:
      enabled: true
      provider: redis  # or 'memory' for dev
      config:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
        ttl-minutes: 60
        max-size: 10000  # for in-memory cache
      resources:
        Patient:
          cacheable: true
          ttl-minutes: 30
        Observation:
          cacheable: false  # Too volatile

    # Audit
    audit:
      enabled: true
      async: true
      log-request-body: true
      log-response-summary: true

    # Telemetry
    telemetry:
      enabled: true
      async: true
      export-interval-seconds: 60

    # Performance
    performance:
      enabled: true
      trace-all-requests: true
      sample-rate: 1.0  # 100% of requests
```

---

## MCP-Based Plugin Architecture (External Plugins)

For plugins that need to be written in other languages, hot-pluggable, or run in isolation, we support the **Model Context Protocol (MCP)**.

### MCP Plugin Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FHIR4Java Server                                 │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      MCP Plugin Manager                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │   │
│  │  │ MCP Client  │  │ MCP Client  │  │ MCP Client  │  ...         │   │
│  │  │  (stdio)    │  │  (HTTP)     │  │  (SSE)      │              │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │   │
│  └─────────┼────────────────┼────────────────┼─────────────────────┘   │
└────────────┼────────────────┼────────────────┼─────────────────────────┘
             │                │                │
             ▼                ▼                ▼
    ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
    │  MCP Server    │ │  MCP Server    │ │  MCP Server    │
    │  (Python)      │ │  (Node.js)     │ │  (Java)        │
    │                │ │                │ │                │
    │ Business Logic │ │ ML Validation  │ │ Auth Plugin    │
    │    Plugin      │ │    Plugin      │ │                │
    └────────────────┘ └────────────────┘ └────────────────┘
```

### MCP Plugin Manager

```java
@Component
public class McpPluginManager {
    private final Map<String, McpSyncClient> mcpClients = new ConcurrentHashMap<>();
    private final McpPluginConfig config;

    @PostConstruct
    public void initializeMcpPlugins() {
        for (McpPluginDefinition plugin : config.getPlugins()) {
            McpSyncClient client = createClient(plugin);
            client.initialize();

            // Discover available tools from the MCP server
            ListToolsResult tools = client.listTools();
            log.info("MCP Plugin '{}' registered with {} tools", plugin.getName(), tools.tools().size());

            mcpClients.put(plugin.getName(), client);
        }
    }

    private McpSyncClient createClient(McpPluginDefinition plugin) {
        McpTransport transport = switch (plugin.getTransport()) {
            case STDIO -> new StdioClientTransport(plugin.getCommand(), plugin.getArgs());
            case HTTP -> new HttpClientTransport(plugin.getUrl());
            case SSE -> new SseClientTransport(plugin.getUrl());
        };

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(plugin.getTimeoutSeconds()))
            .build();
    }

    /**
     * Execute a tool on an MCP plugin server
     */
    public ToolResult executeTool(String pluginName, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = mcpClients.get(pluginName);
        if (client == null) {
            throw new PluginNotFoundException("MCP plugin not found: " + pluginName);
        }

        CallToolRequest request = new CallToolRequest(toolName, arguments);
        return client.callTool(request);
    }

    /**
     * Get resources from an MCP plugin server
     */
    public List<Resource> getResources(String pluginName) {
        McpSyncClient client = mcpClients.get(pluginName);
        return client.listResources().resources();
    }
}
```

### MCP Business Logic Plugin (Example in Python)

```python
# business_logic_plugin.py - MCP Server for Patient validation
from mcp import Server, Tool, Resource
from mcp.server.stdio import stdio_server

server = Server("patient-validation-plugin")

@server.tool()
async def validate_patient_consent(patient_id: str, patient_data: dict) -> dict:
    """Validate that patient has required consent forms"""
    # Custom business logic
    has_consent = check_consent_database(patient_id)

    return {
        "valid": has_consent,
        "message": "Consent validated" if has_consent else "Missing consent form",
        "required_forms": ["HIPAA", "Treatment Consent"] if not has_consent else []
    }

@server.tool()
async def enrich_patient_data(patient_data: dict) -> dict:
    """Enrich patient data with external information"""
    # Call external APIs, ML models, etc.
    enriched = patient_data.copy()
    enriched["risk_score"] = calculate_risk_score(patient_data)
    enriched["care_gaps"] = identify_care_gaps(patient_data)
    return enriched

@server.resource("consent-requirements")
async def get_consent_requirements() -> str:
    """Return current consent requirements as JSON"""
    return json.dumps(CONSENT_REQUIREMENTS)

if __name__ == "__main__":
    stdio_server(server)
```

### MCP Plugin Configuration

```yaml
fhir4java:
  plugins:
    # MCP-based plugins (external)
    mcp:
      enabled: true
      plugins:
        # Local plugin via stdio
        - name: patient-validation
          transport: stdio
          command: python
          args: ["/plugins/patient_validation_plugin.py"]
          timeout-seconds: 30
          tools:
            - name: validate_patient_consent
              maps-to: beforeCreate  # Hook into create operation
              resource-types: [Patient]
            - name: enrich_patient_data
              maps-to: beforeCreate
              resource-types: [Patient]

        # Remote plugin via HTTP
        - name: ml-observation-validator
          transport: http
          url: http://ml-service:8080/mcp
          timeout-seconds: 60
          tools:
            - name: validate_observation_value
              maps-to: beforeCreate
              resource-types: [Observation]
            - name: predict_abnormal
              maps-to: afterCreate
              resource-types: [Observation]

        # Containerized plugin
        - name: auth-plugin
          transport: http
          url: http://auth-plugin:3000/mcp
          timeout-seconds: 10
          tools:
            - name: authenticate
              maps-to: authentication
            - name: authorize
              maps-to: authorization
```

### Hybrid Plugin Orchestrator

The orchestrator supports both Spring Bean and MCP plugins:

```java
@Component
public class HybridPluginOrchestrator {
    private final List<BusinessLogicPlugin> springPlugins;  // Embedded
    private final McpPluginManager mcpPluginManager;        // External

    public void executeBeforeOperation(BusinessContext context) {
        // Execute Spring Bean plugins first (faster, same JVM)
        for (BusinessLogicPlugin plugin : springPlugins) {
            if (plugin.supports(context.getResourceType(), context.getInteraction())) {
                plugin.beforeOperation(context);
            }
        }

        // Then execute MCP plugins (external, any language)
        List<McpToolMapping> mcpTools = getMcpToolsForHook("beforeCreate", context.getResourceType());
        for (McpToolMapping mapping : mcpTools) {
            ToolResult result = mcpPluginManager.executeTool(
                mapping.getPluginName(),
                mapping.getToolName(),
                Map.of(
                    "resourceType", context.getResourceType(),
                    "resourceData", serializeResource(context.getResource())
                )
            );

            if (!result.isSuccess()) {
                throw new BusinessRuleException(result.getErrorMessage());
            }

            // Apply any modifications from the MCP plugin
            if (result.hasModifiedResource()) {
                context.setResource(deserializeResource(result.getModifiedResource()));
            }
        }
    }
}
```

### MCP Plugin Docker Compose Example

```yaml
version: '3.8'
services:
  fhir4java:
    build: .
    ports:
      - "8080:8080"
    environment:
      - MCP_PLUGINS_ENABLED=true
    depends_on:
      - ml-validator
      - auth-plugin

  # Python ML validation plugin
  ml-validator:
    build: ./plugins/ml-validator
    expose:
      - "8080"
    environment:
      - MODEL_PATH=/models/observation_validator.pkl

  # Node.js auth plugin
  auth-plugin:
    build: ./plugins/auth
    expose:
      - "3000"
    environment:
      - OAUTH_ISSUER=https://auth.example.org
```

### Benefits of MCP for FHIR Plugins

| Feature | Benefit |
|---------|---------|
| **Polyglot** | Write plugins in Python (ML), Node.js (integrations), Go (performance) |
| **Isolation** | Plugin crashes don't affect main server |
| **Hot-reload** | Update plugins without server restart |
| **Scaling** | Scale plugins independently |
| **Security** | Sandboxed execution, explicit tool permissions |
| **AI-Ready** | MCP is designed for AI/LLM integration |
| **Ecosystem** | Growing library of MCP servers/tools |

---

## Database Schema Design

### Schema Strategy

```sql
-- Audit and Performance Tracking Schema (always separate)
CREATE SCHEMA IF NOT EXISTS fhir_audit;
CREATE SCHEMA IF NOT EXISTS fhir_performance;

-- Shared resource schema (default)
CREATE SCHEMA IF NOT EXISTS fhir_resources;

-- Dedicated schemas created dynamically based on configuration
-- e.g., fhir_patient, fhir_observation, etc.
```

---

## History Management (Versioning)

### How History Records Are Created

Every mutating operation (CREATE, UPDATE, DELETE) automatically creates a history record in the same transaction:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    HISTORY RECORD CREATION FLOW                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  CREATE Operation:                                                       │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐               │
│  │  New Data   │────▶│ resource_data│────▶│  history    │               │
│  │  (v1)       │     │   (v1)      │     │  (v1, CREATE)│              │
│  └─────────────┘     └─────────────┘     └─────────────┘               │
│                                                                          │
│  UPDATE Operation:                                                       │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐               │
│  │ Existing v1 │────▶│ Copy to     │────▶│  history    │               │
│  │             │     │ history     │     │  (v1, stored)│              │
│  └─────────────┘     └─────────────┘     └─────────────┘               │
│         │                                                                │
│         ▼                                                                │
│  ┌─────────────┐     ┌─────────────┐                                    │
│  │  New Data   │────▶│ resource_data│                                    │
│  │  (v2)       │     │   (v2)      │                                    │
│  └─────────────┘     └─────────────┘                                    │
│                                                                          │
│  DELETE Operation:                                                       │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐               │
│  │ Existing vN │────▶│ Copy to     │────▶│  history    │               │
│  │             │     │ history     │     │  (vN, DELETE)│              │
│  └─────────────┘     └─────────────┘     └─────────────┘               │
│         │                                                                │
│         ▼                                                                │
│  ┌─────────────┐                                                        │
│  │ Soft delete │  (is_deleted = true, or physical delete)               │
│  │ resource_data│                                                        │
│  └─────────────┘                                                        │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Resource Service Implementation

The `ResourceService` is the main service for all CRUD operations. History management is handled transparently as an internal implementation detail.

```java
@Service
@Transactional
public class ResourceService {
    private final ResourceRepository resourceRepository;
    private final ResourceHistoryRepository historyRepository;
    private final FhirContextFactory fhirContextFactory;

    /**
     * CREATE: Insert new resource and create history entry
     */
    public ResourceEntity create(String resourceType, IBaseResource resource) {
        // Generate new ID and set version 1
        String resourceId = UUID.randomUUID().toString();
        resource.setId(resourceId);
        resource.getMeta().setVersionId("1");
        resource.getMeta().setLastUpdated(new Date());

        // Save to main table
        ResourceEntity entity = ResourceEntity.builder()
            .id(UUID.fromString(resourceId))
            .resourceType(resourceType)
            .versionId(1)
            .content(serialize(resource))
            .lastUpdated(Instant.now())
            .build();
        resourceRepository.save(entity);

        // Create history record (same transaction)
        createHistoryRecord(entity, HistoryOperation.CREATE);

        return entity;
    }

    /**
     * UPDATE: Copy current version to history, then update main table
     */
    public ResourceEntity update(String resourceType, String id, IBaseResource resource) {
        ResourceEntity existing = resourceRepository.findById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException(resourceType, id));

        // Step 1: Copy CURRENT version to history BEFORE updating
        createHistoryRecord(existing, HistoryOperation.UPDATE);

        // Step 2: Update main table with new version
        int newVersion = existing.getVersionId() + 1;
        resource.getMeta().setVersionId(String.valueOf(newVersion));
        resource.getMeta().setLastUpdated(new Date());

        existing.setVersionId(newVersion);
        existing.setContent(serialize(resource));
        existing.setLastUpdated(Instant.now());
        resourceRepository.save(existing);

        return existing;
    }

    /**
     * DELETE: Copy current version to history, then soft/hard delete
     */
    public void delete(String resourceType, String id, boolean hardDelete) {
        ResourceEntity existing = resourceRepository.findById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException(resourceType, id));

        // Copy to history with DELETE operation
        createHistoryRecord(existing, HistoryOperation.DELETE);

        if (hardDelete) {
            resourceRepository.delete(existing);
        } else {
            // Soft delete - mark as deleted but keep in main table
            existing.setDeleted(true);
            existing.setLastUpdated(Instant.now());
            resourceRepository.save(existing);
        }
    }

    /**
     * Create history record - called within same transaction
     */
    private void createHistoryRecord(ResourceEntity entity, HistoryOperation operation) {
        ResourceHistoryEntity history = ResourceHistoryEntity.builder()
            .resourceId(entity.getId())
            .resourceType(entity.getResourceType())
            .versionId(entity.getVersionId())
            .fhirVersion(entity.getFhirVersion())
            .content(entity.getContent())  // Copy the JSONB content
            .operation(operation)
            .changedAt(Instant.now())
            .changedBy(SecurityContextHolder.getContext().getAuthentication().getName())
            .build();
        historyRepository.save(history);
    }

    /**
     * VREAD: Read specific version from history
     */
    public Optional<IBaseResource> vread(String resourceType, String id, String versionId) {
        int version = Integer.parseInt(versionId);

        // Check if it's the current version
        Optional<ResourceEntity> current = resourceRepository.findById(UUID.fromString(id));
        if (current.isPresent() && current.get().getVersionId() == version) {
            return Optional.of(deserialize(current.get()));
        }

        // Otherwise, look in history
        return historyRepository.findByResourceIdAndVersionId(UUID.fromString(id), version)
            .map(this::deserialize);
    }

    /**
     * HISTORY: Get all versions of a resource
     */
    public Bundle history(String resourceType, String id, HistoryParameters params) {
        List<ResourceHistoryEntity> versions = historyRepository
            .findByResourceIdOrderByVersionIdDesc(
                UUID.fromString(id),
                PageRequest.of(0, params.getCount())
            );

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.HISTORY);

        for (ResourceHistoryEntity history : versions) {
            Bundle.BundleEntryComponent entry = bundle.addEntry();
            entry.setResource(deserialize(history));
            entry.getRequest()
                .setMethod(toHttpMethod(history.getOperation()))
                .setUrl(resourceType + "/" + id);
            entry.getResponse()
                .setLastModified(Date.from(history.getChangedAt()));
        }

        return bundle;
    }
}
```

### History Table Schema

```sql
-- ============================================
-- HISTORY TABLE
-- ============================================
-- Stores all previous versions of FHIR resources
-- Partitioned by changed_at (time) for efficient archival

CREATE TABLE fhir_resources.resource_history (
    -- History record identifier
    history_id BIGSERIAL,

    -- Reference to the resource
    resource_id UUID NOT NULL,               -- References resource_data.id
    resource_type VARCHAR(100) NOT NULL,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL,       -- R4B, R5

    -- External system reference (copied from main table)
    external_id VARCHAR(255),
    external_system VARCHAR(255),

    -- FHIR resource content (snapshot at this version)
    content JSONB NOT NULL,

    -- Operation that created this history record
    operation VARCHAR(20) NOT NULL,          -- CREATE, UPDATE, DELETE

    -- Timestamp when this version was superseded
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Who made this change
    changed_by VARCHAR(255),

    -- Composite primary key for partitioning
    PRIMARY KEY (history_id, changed_at)
) PARTITION BY RANGE (changed_at);

-- ============================================
-- HISTORY PARTITIONS (Monthly)
-- ============================================
-- Auto-managed by pg_partman or scheduled job

CREATE TABLE fhir_resources.resource_history_2025_01
    PARTITION OF fhir_resources.resource_history
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE fhir_resources.resource_history_2025_02
    PARTITION OF fhir_resources.resource_history
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Default partition for future data
CREATE TABLE fhir_resources.resource_history_default
    PARTITION OF fhir_resources.resource_history
    DEFAULT;

-- ============================================
-- HISTORY INDEXES
-- ============================================

-- Index for vread: GET /Patient/123/_history/2
CREATE INDEX idx_history_resource_version
    ON fhir_resources.resource_history(resource_id, version_id);

-- Index for history: GET /Patient/123/_history
CREATE INDEX idx_history_resource_time
    ON fhir_resources.resource_history(resource_id, changed_at DESC);

-- Index for type-level history: GET /Patient/_history
CREATE INDEX idx_history_type_time
    ON fhir_resources.resource_history(resource_type, changed_at DESC);

-- Index for system-level history: GET /_history
CREATE INDEX idx_history_system_time
    ON fhir_resources.resource_history(changed_at DESC);
```

---

## Search Parameter Re-indexing Strategy

### Does Adding New Search Parameters Require Re-indexing?

**Yes**, adding new search parameters requires re-indexing existing data. However, we implement a **non-blocking, incremental re-indexing** strategy:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SEARCH PARAMETER RE-INDEXING                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. NEW SEARCH PARAM ADDED                                               │
│     ┌─────────────────┐                                                  │
│     │ birthdate added │                                                  │
│     │ to Patient      │                                                  │
│     └────────┬────────┘                                                  │
│              │                                                           │
│              ▼                                                           │
│  2. MARK PARAM AS "INDEXING"                                            │
│     ┌─────────────────┐                                                  │
│     │ search_param    │                                                  │
│     │ status=INDEXING │                                                  │
│     └────────┬────────┘                                                  │
│              │                                                           │
│              ▼                                                           │
│  3. BACKGROUND RE-INDEX JOB (Non-blocking)                              │
│     ┌─────────────────────────────────────────┐                         │
│     │  Process in batches (e.g., 1000 records)│                         │
│     │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐       │                         │
│     │  │Batch│→│Batch│→│Batch│→│Batch│→ ...  │                         │
│     │  │  1  │ │  2  │ │  3  │ │  N  │       │                         │
│     │  └─────┘ └─────┘ └─────┘ └─────┘       │                         │
│     │  Track progress in reindex_job table    │                         │
│     └────────┬────────────────────────────────┘                         │
│              │                                                           │
│              ▼                                                           │
│  4. MARK PARAM AS "ACTIVE"                                              │
│     ┌─────────────────┐                                                  │
│     │ search_param    │                                                  │
│     │ status=ACTIVE   │                                                  │
│     └─────────────────┘                                                  │
│                                                                          │
│  DURING INDEXING:                                                        │
│  - New writes automatically index the new param                          │
│  - Searches on new param return partial results (with warning)          │
│  - Or searches wait until indexing complete (configurable)               │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Re-indexing Implementation

```java
@Service
public class SearchIndexRebuilder {
    private final ResourceRepository resourceRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final ReindexJobRepository reindexJobRepository;
    private final SearchParameterRegistry searchParamRegistry;

    private static final int BATCH_SIZE = 1000;

    /**
     * Trigger re-index when new search parameter is added
     */
    @Async
    public void reindexForNewSearchParameter(String resourceType, SearchParameter newParam) {
        // Create reindex job record
        ReindexJob job = ReindexJob.builder()
            .resourceType(resourceType)
            .searchParameterCode(newParam.getCode())
            .status(ReindexStatus.RUNNING)
            .totalRecords(resourceRepository.countByResourceType(resourceType))
            .processedRecords(0)
            .startedAt(Instant.now())
            .build();
        reindexJobRepository.save(job);

        try {
            long lastProcessedId = 0;
            int processed = 0;

            while (true) {
                // Fetch batch of resources
                List<ResourceEntity> batch = resourceRepository
                    .findByResourceTypeAndIdGreaterThan(
                        resourceType, lastProcessedId,
                        PageRequest.of(0, BATCH_SIZE)
                    );

                if (batch.isEmpty()) break;

                // Process batch
                List<SearchIndexEntity> indexEntries = new ArrayList<>();
                for (ResourceEntity resource : batch) {
                    // Extract search parameter value from resource
                    List<SearchIndexEntity> entries = extractSearchIndexEntries(
                        resource, newParam);
                    indexEntries.addAll(entries);
                    lastProcessedId = resource.getId();
                }

                // Bulk insert index entries
                searchIndexRepository.saveAll(indexEntries);

                // Update progress
                processed += batch.size();
                job.setProcessedRecords(processed);
                job.setLastProcessedId(lastProcessedId);
                reindexJobRepository.save(job);

                // Yield to prevent resource starvation
                Thread.sleep(10);
            }

            // Mark complete
            job.setStatus(ReindexStatus.COMPLETED);
            job.setCompletedAt(Instant.now());

            // Activate the search parameter
            searchParamRegistry.activateParameter(resourceType, newParam.getCode());

        } catch (Exception e) {
            job.setStatus(ReindexStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            throw new ReindexException("Re-indexing failed", e);
        } finally {
            reindexJobRepository.save(job);
        }
    }

    /**
     * Resume interrupted reindex job
     */
    public void resumeReindexJob(Long jobId) {
        ReindexJob job = reindexJobRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != ReindexStatus.FAILED) {
            throw new IllegalStateException("Can only resume failed jobs");
        }

        // Resume from last processed ID
        job.setStatus(ReindexStatus.RUNNING);
        reindexJobRepository.save(job);

        // Continue processing...
    }
}

// Reindex job tracking table
@Entity
@Table(name = "reindex_job", schema = "fhir_resources")
public class ReindexJob {
    @Id @GeneratedValue
    private Long id;
    private String resourceType;
    private String searchParameterCode;

    @Enumerated(EnumType.STRING)
    private ReindexStatus status;

    private long totalRecords;
    private long processedRecords;
    private Long lastProcessedId;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
}
```

### Search Parameter Status Management

```yaml
# Search parameter states
search-parameter-states:
  PENDING:    # Defined but not yet indexed
  INDEXING:   # Background indexing in progress
  ACTIVE:     # Fully indexed and searchable
  DEPRECATED: # No longer indexed for new data
```

---

## High-Performance Query Strategy (100ms @ 100M Records)

### Performance Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                 HIGH-PERFORMANCE QUERY ARCHITECTURE                      │
│                     (100ms @ 100 Million Records)                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                        CACHING LAYER                             │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │   │
│  │  │ L1: Local   │  │ L2: Redis   │  │ Query Cache │              │   │
│  │  │ (Caffeine)  │  │ (Cluster)   │  │ (Results)   │              │   │
│  │  │ ~1ms        │  │ ~5ms        │  │ ~10ms       │              │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │ Cache Miss                               │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     CONNECTION POOLING                           │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ HikariCP (min=10, max=50, prepared statements cached)   │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    DATABASE TOPOLOGY                             │   │
│  │                                                                  │   │
│  │  ┌───────────────┐      ┌───────────────┐                       │   │
│  │  │   PRIMARY     │─────▶│  READ REPLICA │ (for queries)         │   │
│  │  │   (writes)    │      │     1         │                       │   │
│  │  └───────────────┘      └───────────────┘                       │   │
│  │         │                      │                                 │   │
│  │         │               ┌───────────────┐                       │   │
│  │         └──────────────▶│  READ REPLICA │ (for queries)         │   │
│  │                         │     2         │                       │   │
│  │                         └───────────────┘                       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                          │
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                 OPTIMIZED TABLE STRUCTURE                        │   │
│  │                                                                  │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ PARTITIONED TABLES (by resource_type + time range)      │    │   │
│  │  │ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │    │   │
│  │  │ │Patient  │ │Observ.  │ │Encounter│ │  ...    │        │    │   │
│  │  │ │2024-Q1  │ │2024-Q1  │ │2024-Q1  │ │         │        │    │   │
│  │  │ └─────────┘ └─────────┘ └─────────┘ └─────────┘        │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  │                                                                  │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │ OPTIMIZED INDEXES                                        │    │   │
│  │  │ • B-tree: Primary keys, foreign keys                     │    │   │
│  │  │ • GIN: JSONB content (for flexible queries)              │    │   │
│  │  │ • BRIN: Time-based columns (very compact)                │    │   │
│  │  │ • Partial: Conditional indexes for common queries        │    │   │
│  │  │ • Covering: Include columns to avoid table lookups       │    │   │
│  │  └─────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. Table Partitioning Strategy

```sql
-- ============================================
-- MAIN RESOURCE TABLE
-- ============================================
-- Stores current version of all FHIR resources
-- Partitioned by resource_type for query performance

CREATE TABLE fhir_resources.resource_data (
    -- Primary identifier
    id UUID PRIMARY KEY,

    -- Resource metadata
    resource_type VARCHAR(100) NOT NULL,
    version_id INTEGER NOT NULL DEFAULT 1,
    fhir_version VARCHAR(10) NOT NULL,        -- R4B, R5

    -- External system reference (for integrations)
    external_id VARCHAR(255),                  -- e.g., MRN, legacy system ID
    external_system VARCHAR(255),              -- e.g., "HIS", "EMR", "LAB"

    -- FHIR resource content
    content JSONB NOT NULL,

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Soft delete flag
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,

    -- Audit columns
    created_by VARCHAR(255),                   -- User/system who created
    updated_by VARCHAR(255)                    -- User/system who last updated
) PARTITION BY LIST (resource_type);

-- Unique constraint on external_id per system (if provided)
CREATE UNIQUE INDEX idx_resource_external_id
    ON fhir_resources.resource_data (external_system, external_id)
    WHERE external_id IS NOT NULL;

-- ============================================
-- RESOURCE TYPE PARTITIONS
-- ============================================

-- Patient partition
CREATE TABLE fhir_resources.resource_data_patient
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('Patient');

-- Observation partition (high volume - consider sub-partitioning)
CREATE TABLE fhir_resources.resource_data_observation
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('Observation');

-- Encounter partition
CREATE TABLE fhir_resources.resource_data_encounter
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('Encounter');

-- Condition partition
CREATE TABLE fhir_resources.resource_data_condition
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('Condition');

-- MedicationRequest partition
CREATE TABLE fhir_resources.resource_data_medicationrequest
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('MedicationRequest');

-- DiagnosticReport partition
CREATE TABLE fhir_resources.resource_data_diagnosticreport
    PARTITION OF fhir_resources.resource_data
    FOR VALUES IN ('DiagnosticReport');

-- Default partition for other resource types
CREATE TABLE fhir_resources.resource_data_default
    PARTITION OF fhir_resources.resource_data
    DEFAULT;

-- Search index table - also partitioned
CREATE TABLE fhir_resources.search_index (
    id BIGSERIAL,
    resource_id UUID NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    -- Different value columns for different types
    string_value VARCHAR(2000),
    string_value_normalized VARCHAR(2000), -- lowercase, accent-folded
    date_value_start TIMESTAMP WITH TIME ZONE,
    date_value_end TIMESTAMP WITH TIME ZONE,
    number_value NUMERIC,
    quantity_value NUMERIC,
    quantity_unit VARCHAR(100),
    reference_id UUID,
    reference_type VARCHAR(100),
    token_system VARCHAR(500),
    token_code VARCHAR(500),
    token_text VARCHAR(500),
    PRIMARY KEY (id, resource_type)
) PARTITION BY LIST (resource_type);

-- Partition search index per resource type
CREATE TABLE fhir_resources.search_index_patient
    PARTITION OF fhir_resources.search_index
    FOR VALUES IN ('Patient');
```

### 2. Optimized Indexing Strategy

```sql
-- ============================================
-- COVERING INDEXES (avoid table lookups)
-- ============================================

-- Read by ID - most common operation (~1ms)
CREATE UNIQUE INDEX idx_resource_pk_covering
    ON fhir_resources.resource_data (id, resource_type)
    INCLUDE (version_id, content, last_updated)
    WHERE is_deleted = FALSE;

-- ============================================
-- SEARCH INDEX OPTIMIZATION
-- ============================================

-- String search (name, address, etc.) - case-insensitive
CREATE INDEX idx_search_string_normalized
    ON fhir_resources.search_index (resource_type, param_name, string_value_normalized)
    INCLUDE (resource_id)
    WHERE string_value_normalized IS NOT NULL;

-- Token search (identifier, code, etc.) - exact match
CREATE INDEX idx_search_token
    ON fhir_resources.search_index (resource_type, param_name, token_system, token_code)
    INCLUDE (resource_id)
    WHERE token_code IS NOT NULL;

-- Date range search (birthdate, effective date, etc.)
CREATE INDEX idx_search_date_range
    ON fhir_resources.search_index (resource_type, param_name, date_value_start, date_value_end)
    INCLUDE (resource_id)
    WHERE date_value_start IS NOT NULL;

-- Reference search (patient, subject, etc.)
CREATE INDEX idx_search_reference
    ON fhir_resources.search_index (resource_type, param_name, reference_type, reference_id)
    INCLUDE (resource_id)
    WHERE reference_id IS NOT NULL;

-- ============================================
-- PARTIAL INDEXES (for common queries)
-- ============================================

-- Active patients only (very common query)
CREATE INDEX idx_patient_active
    ON fhir_resources.resource_data_patient (id)
    INCLUDE (content)
    WHERE is_deleted = FALSE
    AND content->>'active' = 'true';

-- Recent observations (last 30 days)
CREATE INDEX idx_observation_recent
    ON fhir_resources.resource_data_observation (last_updated DESC)
    INCLUDE (id, content)
    WHERE last_updated > CURRENT_TIMESTAMP - INTERVAL '30 days';

-- ============================================
-- GIN INDEX for flexible JSONB queries
-- ============================================

-- For ad-hoc queries on JSONB content
CREATE INDEX idx_resource_content_gin
    ON fhir_resources.resource_data
    USING GIN (content jsonb_path_ops);

-- ============================================
-- BRIN INDEX for time-based columns (very compact)
-- ============================================

CREATE INDEX idx_resource_created_brin
    ON fhir_resources.resource_data
    USING BRIN (created_at)
    WITH (pages_per_range = 128);
```

### 3. Multi-Level Caching

```java
@Configuration
public class CacheConfiguration {

    // L1: Local in-memory cache (Caffeine) - ~1ms
    @Bean
    public Cache<String, IBaseResource> localResourceCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }

    // L2: Distributed cache (Redis) - ~5ms
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}

@Service
public class CachedResourceService {
    private final Cache<String, IBaseResource> localCache;
    private final RedisTemplate<String, String> redisTemplate;
    private final ResourceRepository repository;
    private final FhirContextFactory fhirContextFactory;

    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    public Optional<IBaseResource> read(String resourceType, String id) {
        String cacheKey = resourceType + "/" + id;

        // L1: Check local cache first (~1ms)
        IBaseResource cached = localCache.getIfPresent(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        // L2: Check Redis (~5ms)
        String redisValue = redisTemplate.opsForValue().get(cacheKey);
        if (redisValue != null) {
            IBaseResource resource = deserialize(redisValue);
            localCache.put(cacheKey, resource);  // Populate L1
            return Optional.of(resource);
        }

        // L3: Database query (~20-50ms with good indexes)
        Optional<ResourceEntity> entity = repository.findByIdAndResourceType(
            UUID.fromString(id), resourceType);

        if (entity.isPresent()) {
            IBaseResource resource = deserialize(entity.get());

            // Populate both caches
            localCache.put(cacheKey, resource);
            redisTemplate.opsForValue().set(cacheKey, serialize(resource), REDIS_TTL);

            return Optional.of(resource);
        }

        return Optional.empty();
    }

    // Cache invalidation on write
    @CacheEvict(cacheNames = {"resourceCache"}, key = "#resourceType + '/' + #id")
    public void invalidateCache(String resourceType, String id) {
        String cacheKey = resourceType + "/" + id;
        localCache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
    }
}
```

### 4. Query Result Caching

```java
@Service
public class SearchResultCache {
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Cache search results for common queries
     */
    public Optional<Bundle> getCachedSearchResult(String resourceType,
                                                   Map<String, String> params) {
        String cacheKey = buildSearchCacheKey(resourceType, params);
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return Optional.of(deserializeBundle(cached));
        }
        return Optional.empty();
    }

    public void cacheSearchResult(String resourceType,
                                   Map<String, String> params,
                                   Bundle result) {
        // Only cache if result set is reasonable size
        if (result.getEntry().size() <= 100) {
            String cacheKey = buildSearchCacheKey(resourceType, params);
            redisTemplate.opsForValue().set(cacheKey, serializeBundle(result), SEARCH_CACHE_TTL);
        }
    }

    private String buildSearchCacheKey(String resourceType, Map<String, String> params) {
        // Deterministic key from sorted parameters
        String paramString = params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
        return "search:" + resourceType + "?" + paramString;
    }
}
```

### 5. Read Replica Configuration

```yaml
# application.yml
spring:
  datasource:
    # Primary for writes
    primary:
      url: jdbc:postgresql://primary-db:5432/fhir4java
      username: ${DB_USER}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5

    # Read replicas for queries
    replica:
      url: jdbc:postgresql://replica-db:5432/fhir4java
      username: ${DB_USER}
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 50
        minimum-idle: 10

fhir4java:
  database:
    read-write-splitting:
      enabled: true
      # Route reads to replica, writes to primary
      read-operations: [read, vread, search, history]
      write-operations: [create, update, delete, patch]
```

```java
@Configuration
public class ReadWriteRoutingConfiguration {

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primary);
        targetDataSources.put(DataSourceType.REPLICA, replica);

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primary);
        return routingDataSource;
    }
}

public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? DataSourceType.REPLICA
            : DataSourceType.PRIMARY;
    }
}

// Usage in service
@Service
public class ResourceService {

    @Transactional(readOnly = true)  // Routes to replica
    public Optional<IBaseResource> read(String resourceType, String id) {
        // ...
    }

    @Transactional  // Routes to primary
    public IBaseResource create(String resourceType, IBaseResource resource) {
        // ...
    }
}
```

### 6. Async Search Index Updates

```java
@Service
public class AsyncSearchIndexService {
    private final SearchIndexRepository searchIndexRepository;
    private final BlockingQueue<IndexTask> indexQueue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService indexExecutor = Executors.newFixedThreadPool(4);

    @PostConstruct
    public void startIndexWorkers() {
        for (int i = 0; i < 4; i++) {
            indexExecutor.submit(this::processIndexQueue);
        }
    }

    /**
     * Queue index update for async processing (non-blocking write path)
     */
    public void queueIndexUpdate(ResourceEntity resource) {
        IndexTask task = new IndexTask(resource.getId(), resource.getResourceType(),
                                        resource.getContent());
        if (!indexQueue.offer(task)) {
            // Queue full - process synchronously as fallback
            processIndexTask(task);
        }
    }

    private void processIndexQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                IndexTask task = indexQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processIndexTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Transactional
    private void processIndexTask(IndexTask task) {
        // Delete existing index entries
        searchIndexRepository.deleteByResourceId(task.resourceId());

        // Extract and save new index entries
        List<SearchIndexEntity> entries = extractSearchIndexEntries(task);
        searchIndexRepository.saveAll(entries);
    }
}
```

### 7. Performance Monitoring

```java
@Aspect
@Component
public class QueryPerformanceMonitor {
    private final MeterRegistry meterRegistry;
    private static final long SLOW_QUERY_THRESHOLD_MS = 100;

    @Around("execution(* org.fhirframework.persistence.repository.*.*(..))")
    public Object monitorQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        String operation = joinPoint.getSignature().getName();

        try {
            Object result = joinPoint.proceed();

            long durationMs = sample.stop(Timer.builder("fhir.db.query")
                .tag("operation", operation)
                .tag("status", "success")
                .register(meterRegistry));

            if (durationMs > SLOW_QUERY_THRESHOLD_MS) {
                log.warn("Slow query detected: {} took {}ms", operation, durationMs);
            }

            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder("fhir.db.query")
                .tag("operation", operation)
                .tag("status", "error")
                .register(meterRegistry));
            throw e;
        }
    }
}
```

### Performance Summary

| Operation | Target | Technique |
|-----------|--------|-----------|
| **Read by ID** | <10ms | L1 cache (1ms) → L2 Redis (5ms) → Covering index |
| **Search (common)** | <50ms | Query result cache + optimized indexes |
| **Search (complex)** | <100ms | Partitioned tables + parallel query |
| **Write** | <50ms | Async index updates + connection pooling |
| **History** | <100ms | Partitioned history table + time-based indexes |

### Core Tables

```sql
-- fhir_resources schema (or dedicated schema per resource)
CREATE TABLE {schema}.resource_data (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(100) NOT NULL,
    version_id INTEGER NOT NULL DEFAULT 1,
    fhir_version VARCHAR(10) NOT NULL, -- R4B, R5
    content JSONB NOT NULL,             -- Full FHIR resource JSON
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_resource_version UNIQUE (id, version_id)
);

-- History table for vread/history operations
CREATE TABLE {schema}.resource_history (
    history_id BIGSERIAL PRIMARY KEY,
    resource_id UUID NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    version_id INTEGER NOT NULL,
    fhir_version VARCHAR(10) NOT NULL,
    content JSONB NOT NULL,
    operation VARCHAR(20) NOT NULL, -- CREATE, UPDATE, DELETE
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changed_by VARCHAR(255)
);

-- Search index table (for efficient querying)
CREATE TABLE {schema}.search_index (
    id BIGSERIAL PRIMARY KEY,
    resource_id UUID NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL,
    string_value VARCHAR(2000),
    date_value TIMESTAMP WITH TIME ZONE,
    number_value NUMERIC,
    reference_value UUID,
    token_system VARCHAR(500),
    token_code VARCHAR(500)
);

-- Audit log table
CREATE TABLE fhir_audit.audit_log (
    id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id VARCHAR(255),
    client_ip VARCHAR(45),
    resource_type VARCHAR(100),
    resource_id UUID,
    operation VARCHAR(50) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    request_payload JSONB,
    response_summary JSONB,
    duration_ms INTEGER
);

-- Performance tracking table
CREATE TABLE fhir_performance.trace_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id UUID NOT NULL,
    request_id UUID NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    total_duration_ms INTEGER,
    status VARCHAR(20)
);

CREATE TABLE fhir_performance.trace_steps (
    id BIGSERIAL PRIMARY KEY,
    trace_id UUID NOT NULL,
    step_name VARCHAR(100) NOT NULL,
    step_order INTEGER NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_ms INTEGER NOT NULL,
    metadata JSONB
);
```

---

## Persistence Providers (Internal vs External Storage)

The system supports configurable persistence per resource type. For external persistence, the system implements a **write-through pattern with rollback capability**:
1. Data is **always stored in internal database first**
2. Then synced to external service
3. If external service fails, the internal changes can be **rolled back**

### Persistence Mode

```java
public enum PersistenceMode {
    INTERNAL_ONLY,      // Store only in internal PostgreSQL
    EXTERNAL_SYNC,      // Store internally first, then sync to external (with rollback on failure)
    EXTERNAL_ASYNC      // Store internally first, then async sync to external (no rollback)
}

public enum RollbackPolicy {
    ROLLBACK_ON_FAILURE,  // Rollback internal changes if external sync fails
    KEEP_INTERNAL,        // Keep internal changes even if external sync fails (mark for retry)
    FAIL_SILENT           // Log error but don't rollback, don't retry
}
```

### Persistence Provider Interface

```java
public interface PersistenceProvider {
    String getName();
    boolean isExternal();

    // CRUD operations
    IBaseResource create(String resourceType, IBaseResource resource);
    Optional<IBaseResource> read(String resourceType, String id);
    Optional<IBaseResource> vread(String resourceType, String id, String versionId);
    IBaseResource update(String resourceType, String id, IBaseResource resource);
    void delete(String resourceType, String id);

    // Search
    Bundle search(String resourceType, SearchCriteria criteria);

    // History
    Bundle history(String resourceType, String id, HistoryParameters params);
}

// External service interface for sync operations
public interface ExternalSyncService {
    String getName();
    SyncResult syncCreate(String resourceType, IBaseResource resource);
    SyncResult syncUpdate(String resourceType, String id, IBaseResource resource);
    SyncResult syncDelete(String resourceType, String id);
}

@Data
public class SyncResult {
    private final boolean success;
    private final String externalId;
    private final String errorMessage;
    private final boolean retryable;
}
```

### Write-Through Persistence Orchestrator

The orchestrator handles the internal-first, then external pattern with rollback support:

```java
@Component
public class PersistenceOrchestrator {
    private final JpaPersistenceProvider internalProvider;
    private final Map<String, ExternalSyncService> externalServices;
    private final SyncFailureRepository syncFailureRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Create resource with write-through to external service
     */
    @Transactional
    public IBaseResource create(String resourceType, IBaseResource resource, PersistenceConfig config) {
        // Step 1: Always store internally first
        IBaseResource savedResource = internalProvider.create(resourceType, resource);
        String resourceId = savedResource.getIdElement().getIdPart();

        // Step 2: If external sync is configured, sync to external service
        if (config.getMode() == PersistenceMode.EXTERNAL_SYNC) {
            ExternalSyncService externalService = externalServices.get(config.getExternalServiceName());

            try {
                SyncResult syncResult = externalService.syncCreate(resourceType, savedResource);

                if (!syncResult.isSuccess()) {
                    handleSyncFailure(resourceType, resourceId, "CREATE", config, syncResult);
                } else {
                    // Update internal record with external reference
                    updateExternalReference(resourceType, resourceId, syncResult.getExternalId());
                }
            } catch (Exception e) {
                handleSyncException(resourceType, resourceId, "CREATE", config, e);
            }
        } else if (config.getMode() == PersistenceMode.EXTERNAL_ASYNC) {
            // Queue for async processing
            queueAsyncSync(resourceType, resourceId, "CREATE", config);
        }

        return savedResource;
    }

    /**
     * Update resource with write-through and rollback support
     */
    @Transactional
    public IBaseResource update(String resourceType, String id, IBaseResource resource, PersistenceConfig config) {
        // Step 1: Get current version for potential rollback
        Optional<IBaseResource> previousVersion = internalProvider.read(resourceType, id);
        if (previousVersion.isEmpty()) {
            throw new ResourceNotFoundException("Resource not found: " + resourceType + "/" + id);
        }

        // Step 2: Store internally first
        IBaseResource updatedResource = internalProvider.update(resourceType, id, resource);

        // Step 3: Sync to external service
        if (config.getMode() == PersistenceMode.EXTERNAL_SYNC) {
            ExternalSyncService externalService = externalServices.get(config.getExternalServiceName());

            try {
                SyncResult syncResult = externalService.syncUpdate(resourceType, id, updatedResource);

                if (!syncResult.isSuccess()) {
                    handleSyncFailureWithRollback(resourceType, id, previousVersion.get(), config, syncResult);
                }
            } catch (Exception e) {
                handleSyncExceptionWithRollback(resourceType, id, previousVersion.get(), config, e);
            }
        } else if (config.getMode() == PersistenceMode.EXTERNAL_ASYNC) {
            queueAsyncSync(resourceType, id, "UPDATE", config);
        }

        return updatedResource;
    }

    /**
     * Handle sync failure based on rollback policy
     */
    private void handleSyncFailure(String resourceType, String resourceId, String operation,
                                    PersistenceConfig config, SyncResult syncResult) {
        switch (config.getRollbackPolicy()) {
            case ROLLBACK_ON_FAILURE:
                // Rollback the internal transaction
                throw new ExternalSyncException(
                    "External sync failed, rolling back: " + syncResult.getErrorMessage());

            case KEEP_INTERNAL:
                // Keep internal changes, record for retry
                SyncFailureEntity failure = SyncFailureEntity.builder()
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .operation(operation)
                    .externalService(config.getExternalServiceName())
                    .errorMessage(syncResult.getErrorMessage())
                    .retryable(syncResult.isRetryable())
                    .retryCount(0)
                    .status(SyncStatus.PENDING_RETRY)
                    .createdAt(Instant.now())
                    .build();
                syncFailureRepository.save(failure);
                log.warn("External sync failed, marked for retry: {}/{}", resourceType, resourceId);
                break;

            case FAIL_SILENT:
                log.error("External sync failed silently: {}/{} - {}",
                    resourceType, resourceId, syncResult.getErrorMessage());
                break;
        }
    }

    /**
     * Rollback internal changes on sync failure
     */
    private void handleSyncFailureWithRollback(String resourceType, String resourceId,
                                                IBaseResource previousVersion,
                                                PersistenceConfig config, SyncResult syncResult) {
        if (config.getRollbackPolicy() == RollbackPolicy.ROLLBACK_ON_FAILURE) {
            // Restore previous version
            internalProvider.update(resourceType, resourceId, previousVersion);
            throw new ExternalSyncException(
                "External sync failed, rolled back to previous version: " + syncResult.getErrorMessage());
        } else {
            handleSyncFailure(resourceType, resourceId, "UPDATE", config, syncResult);
        }
    }
}
```

### Sync Failure Tracking and Retry

```java
@Entity
@Table(name = "sync_failure", schema = "fhir_sync")
public class SyncFailureEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String resourceType;
    private String resourceId;
    private String operation;  // CREATE, UPDATE, DELETE
    private String externalService;
    private String errorMessage;
    private boolean retryable;
    private int retryCount;

    @Enumerated(EnumType.STRING)
    private SyncStatus status;  // PENDING_RETRY, RETRYING, FAILED, SUCCEEDED

    private Instant createdAt;
    private Instant lastRetryAt;
    private Instant nextRetryAt;
}

@Component
public class SyncRetryScheduler {
    private final SyncFailureRepository syncFailureRepository;
    private final PersistenceOrchestrator orchestrator;

    @Scheduled(fixedDelay = 60000)  // Every minute
    public void retryFailedSyncs() {
        List<SyncFailureEntity> pendingRetries = syncFailureRepository
            .findByStatusAndNextRetryAtBefore(SyncStatus.PENDING_RETRY, Instant.now());

        for (SyncFailureEntity failure : pendingRetries) {
            try {
                failure.setStatus(SyncStatus.RETRYING);
                failure.setLastRetryAt(Instant.now());
                syncFailureRepository.save(failure);

                // Retry the sync
                Optional<IBaseResource> resource = internalProvider.read(
                    failure.getResourceType(), failure.getResourceId());

                if (resource.isPresent()) {
                    ExternalSyncService service = externalServices.get(failure.getExternalService());
                    SyncResult result = switch (failure.getOperation()) {
                        case "CREATE" -> service.syncCreate(failure.getResourceType(), resource.get());
                        case "UPDATE" -> service.syncUpdate(failure.getResourceType(),
                                            failure.getResourceId(), resource.get());
                        case "DELETE" -> service.syncDelete(failure.getResourceType(),
                                            failure.getResourceId());
                        default -> throw new IllegalStateException("Unknown operation: " + failure.getOperation());
                    };

                    if (result.isSuccess()) {
                        failure.setStatus(SyncStatus.SUCCEEDED);
                    } else {
                        handleRetryFailure(failure, result);
                    }
                }
            } catch (Exception e) {
                handleRetryException(failure, e);
            }
            syncFailureRepository.save(failure);
        }
    }

    private void handleRetryFailure(SyncFailureEntity failure, SyncResult result) {
        failure.setRetryCount(failure.getRetryCount() + 1);

        if (failure.getRetryCount() >= maxRetries || !result.isRetryable()) {
            failure.setStatus(SyncStatus.FAILED);
        } else {
            failure.setStatus(SyncStatus.PENDING_RETRY);
            // Exponential backoff
            long delayMinutes = (long) Math.pow(2, failure.getRetryCount());
            failure.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(delayMinutes)));
        }
    }
}
```

### External Service Implementations

#### 1. External FHIR Server Sync Service

```java
@Component
public class ExternalFhirSyncService implements ExternalSyncService {
    private final IGenericClient fhirClient;

    @Override
    public String getName() { return "external-fhir"; }

    @Override
    public SyncResult syncCreate(String resourceType, IBaseResource resource) {
        try {
            MethodOutcome outcome = fhirClient.create()
                .resource(resource)
                .execute();
            return SyncResult.success(outcome.getId().getIdPart());
        } catch (Exception e) {
            return SyncResult.failure(e.getMessage(), isRetryable(e));
        }
    }

    @Override
    public SyncResult syncUpdate(String resourceType, String id, IBaseResource resource) {
        try {
            MethodOutcome outcome = fhirClient.update()
                .resource(resource)
                .execute();
            return SyncResult.success(outcome.getId().getIdPart());
        } catch (Exception e) {
            return SyncResult.failure(e.getMessage(), isRetryable(e));
        }
    }
}
```

#### 2. Cloud Storage Sync Service

```java
@Component
public class CloudStorageSyncService implements ExternalSyncService {
    private final CloudStorageClient storageClient;
    private final FhirContextFactory fhirContextFactory;

    @Override
    public String getName() { return "cloud-storage"; }

    @Override
    public SyncResult syncCreate(String resourceType, IBaseResource resource) {
        try {
            String id = resource.getIdElement().getIdPart();
            String version = resource.getMeta().getVersionId();
            String path = buildPath(resourceType, id, version);
            String json = serializeToJson(resource);

            storageClient.putObject(bucketName, path, json);
            return SyncResult.success(path);
        } catch (Exception e) {
            return SyncResult.failure(e.getMessage(), isRetryable(e));
        }
    }
}
```

### Per-Resource Persistence Configuration

```yaml
fhir4java:
  persistence:
    # Default configuration for all resources
    default:
      mode: INTERNAL_ONLY

    # Sync failure tracking schema
    sync-schema: fhir_sync

    # External services
    external-services:
      central-fhir:
        type: external-fhir
        base-url: https://fhir.example.org/r5
        auth:
          type: bearer
          token: ${EXTERNAL_FHIR_TOKEN}

      azure-blob:
        type: cloud-storage
        cloud: azure
        container: fhir-resources
        connection-string: ${AZURE_STORAGE_CONNECTION_STRING}

      s3-archive:
        type: cloud-storage
        cloud: aws
        bucket: fhir-archive
        region: us-east-1

    # Per-resource persistence configuration
    resources:
      Patient:
        mode: INTERNAL_ONLY  # Only store in internal database

      Observation:
        mode: EXTERNAL_SYNC  # Sync to external service with rollback
        external-service: s3-archive
        rollback-policy: ROLLBACK_ON_FAILURE

      DocumentReference:
        mode: EXTERNAL_SYNC
        external-service: azure-blob
        rollback-policy: KEEP_INTERNAL  # Keep internal, retry later

      AuditEvent:
        mode: EXTERNAL_ASYNC  # Async sync, no blocking
        external-service: central-fhir
        rollback-policy: FAIL_SILENT

    # Retry configuration
    retry:
      max-retries: 5
      initial-delay-minutes: 1
      max-delay-minutes: 60
      backoff-multiplier: 2
```

### Sync Status Dashboard

```sql
-- Track sync status
CREATE SCHEMA IF NOT EXISTS fhir_sync;

CREATE TABLE fhir_sync.sync_failure (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    external_service VARCHAR(100) NOT NULL,
    error_message TEXT,
    retryable BOOLEAN DEFAULT TRUE,
    retry_count INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_retry_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE
);

-- External references table (stores external IDs for synced resources)
CREATE TABLE fhir_sync.external_reference (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    external_service VARCHAR(100) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_external_ref UNIQUE (resource_type, resource_id, external_service)
);

CREATE INDEX idx_sync_failure_status ON fhir_sync.sync_failure(status, next_retry_at);
```

---

## Profile (StructureDefinition) Configuration

Profiles are stored as standard FHIR StructureDefinition resources in JSON format.

**File: `fhir-config/profiles/custom-patient-profile.json`** (StructureDefinition)
```json
{
  "resourceType": "StructureDefinition",
  "id": "custom-patient-profile",
  "url": "http://example.org/fhir/StructureDefinition/CustomPatient",
  "version": "1.0.0",
  "name": "CustomPatient",
  "title": "Custom Patient Profile",
  "status": "active",
  "fhirVersion": "5.0.0",
  "kind": "resource",
  "abstract": false,
  "type": "Patient",
  "baseDefinition": "http://hl7.org/fhir/StructureDefinition/Patient",
  "derivation": "constraint",
  "differential": {
    "element": [
      {
        "id": "Patient.identifier",
        "path": "Patient.identifier",
        "min": 1,
        "mustSupport": true
      },
      {
        "id": "Patient.identifier.system",
        "path": "Patient.identifier.system",
        "min": 1,
        "fixedUri": "http://example.org/fhir/identifier/mrn"
      },
      {
        "id": "Patient.name",
        "path": "Patient.name",
        "min": 1,
        "mustSupport": true
      },
      {
        "id": "Patient.birthDate",
        "path": "Patient.birthDate",
        "min": 1,
        "mustSupport": true
      },
      {
        "id": "Patient.gender",
        "path": "Patient.gender",
        "min": 1,
        "mustSupport": true
      }
    ]
  }
}
```

---

## Extended Operations Support

### Operation Definition Configuration

**File: `fhir-config/operations/patient-merge.json`** (OperationDefinition - FHIR JSON)
```json
{
  "resourceType": "OperationDefinition",
  "id": "patient-merge",
  "url": "http://example.org/fhir/OperationDefinition/patient-merge",
  "name": "PatientMerge",
  "status": "active",
  "kind": "operation",
  "code": "merge",
  "resource": ["Patient"],
  "system": false,
  "type": true,
  "instance": true,
  "parameter": [
    {
      "name": "source-patient",
      "use": "in",
      "min": 1,
      "max": "1",
      "type": "Reference"
    },
    {
      "name": "target-patient",
      "use": "in",
      "min": 1,
      "max": "1",
      "type": "Reference"
    }
  ]
}
```

### Extended Operation Handler

```java
public interface ExtendedOperationHandler {
    String getOperationCode();
    Set<String> getSupportedResourceTypes();
    boolean isInstanceLevel();
    boolean isTypeLevel();

    IBaseResource execute(OperationContext context);
}

@Component
public class ExtendedOperationRegistry {
    private final Map<String, ExtendedOperationHandler> handlers;
    private final Map<String, OperationDefinition> definitions;

    public void registerOperation(OperationDefinition definition, ExtendedOperationHandler handler);
    public Optional<ExtendedOperationHandler> getHandler(String resourceType, String operationCode);
    public void validateOperationParameters(String operationCode, Parameters params);
}
```

### Internal Patch/Search Operations

```java
@Component
public class InternalOperationExecutor {
    private final ResourceRepository repository;
    private final FhirPatchService patchService;
    private final SearchService searchService;

    // For extended operations that modify data
    public IBaseResource executePatch(String resourceType, String id, List<PatchOperation> patches);

    // For extended operations that query data
    public Bundle executeSearch(String resourceType, SearchCriteria criteria);
}
```

---

## API Layer Design

### Metadata Endpoint (CapabilityStatement)

The server exposes its CapabilityStatement at the `/metadata` endpoint, following FHIR specification.

```java
@RestController
@RequestMapping("/fhir/{fhirVersion}")
public class MetadataController {
    private final CapabilityStatementGenerator capabilityStatementGenerator;
    private final FhirContextFactory fhirContextFactory;

    /**
     * GET /fhir/r5/metadata - Returns the server's CapabilityStatement
     * Reference: https://hapi.fhir.org/baseR5/metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<String> getMetadata(
            @PathVariable String fhirVersion,
            @RequestHeader(value = "Accept", defaultValue = "application/fhir+json") String accept) {

        FhirVersion version = FhirVersion.fromPath(fhirVersion);
        CapabilityStatement capabilityStatement = capabilityStatementGenerator.generate(version);

        FhirContext ctx = fhirContextFactory.getContext(version);
        String content = ctx.newJsonParser().encodeResourceToString(capabilityStatement);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(content);
    }

    /**
     * OPTIONS /fhir/r5 - Also returns CapabilityStatement per FHIR spec
     */
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<String> options(@PathVariable String fhirVersion) {
        return getMetadata(fhirVersion, "application/fhir+json");
    }
}
```

### REST Controller Structure

```java
@RestController
@RequestMapping("/fhir/{fhirVersion}")
public class FhirResourceController {

    @GetMapping("/{resourceType}/{id}")
    public ResponseEntity<String> read(
        @PathVariable String fhirVersion,
        @PathVariable String resourceType,
        @PathVariable String id);

    @GetMapping("/{resourceType}/{id}/_history/{versionId}")
    public ResponseEntity<String> vread(...);

    @PostMapping("/{resourceType}")
    public ResponseEntity<String> create(
        @PathVariable String resourceType,
        @RequestBody String resource);

    @PutMapping("/{resourceType}/{id}")
    public ResponseEntity<String> update(...);

    @PatchMapping("/{resourceType}/{id}")
    public ResponseEntity<String> patch(...);

    @DeleteMapping("/{resourceType}/{id}")
    public ResponseEntity<Void> delete(...);

    @GetMapping("/{resourceType}")
    public ResponseEntity<String> search(
        @PathVariable String resourceType,
        @RequestParam Map<String, String> params);

    @PostMapping("/{resourceType}/${operationName}")
    public ResponseEntity<String> typeOperation(...);

    @PostMapping("/{resourceType}/{id}/${operationName}")
    public ResponseEntity<String> instanceOperation(...);
}
```

### Request Processing Pipeline

```
Request → Filter → Interceptor → Controller
                      ↓
              InteractionGuard (check enabled)
                      ↓
              Validation (profile/search params/operation)
                      ↓
              Business Logic Plugins (before)
                      ↓
              Core Operation Execution
                      ↓
              Business Logic Plugins (after)
                      ↓
              Response + Audit/Telemetry/Performance logging
```

---

## Configuration Files

### application.yml

```yaml
fhir4java:
  server:
    # Global default FHIR version (used when resource doesn't specify)
    default-version: R5
    # Base URL for the FHIR server
    base-url: http://localhost:8080/fhir

  # Supported FHIR versions
  versions:
    enabled:
      - R5
      - R4B

  # Configuration paths
  config:
    # Base path for all FHIR configuration files
    base-path: classpath:fhir-config/
    # Resource YAML configurations (version-agnostic)
    resources-path: ${fhir4java.config.base-path}resources/
    # Version-specific paths are derived as:
    #   ${fhir4java.config.base-path}r5/searchparameters/
    #   ${fhir4java.config.base-path}r5/operations/
    #   ${fhir4java.config.base-path}r5/profiles/
    #   ${fhir4java.config.base-path}r4b/searchparameters/
    #   etc.

  validation:
    enabled: true
    profile-validation: strict  # strict, lenient, off

  database:
    default-schema: fhir_resources
    audit-schema: fhir_audit
    performance-schema: fhir_performance

  plugins:
    audit:
      enabled: true
      log-request-body: true
      log-response-summary: true
    telemetry:
      enabled: true
      export-interval-seconds: 60
    performance:
      enabled: true
      trace-all-requests: true

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:fhir4java}
    username: ${DB_USER:fhir4java}
    password: ${DB_PASSWORD:fhir4java}

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: fhir_resources
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    enabled: true
    locations: filesystem:./db/migrations    # Top-level db/migrations folder
    baseline-on-migrate: true
    schemas:
      - fhir_resources
      - fhir_audit
      - fhir_performance
      - fhir_sync
```

---

## Docker Setup

### Dockerfile

```dockerfile
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/fhir4java-server/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml

```yaml
version: '3.8'
services:
  fhir4java:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=fhir4java
      - DB_USER=fhir4java
      - DB_PASSWORD=fhir4java
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=fhir4java
      - POSTGRES_USER=fhir4java
      - POSTGRES_PASSWORD=fhir4java
    volumes:
      - ./db/init:/docker-entrypoint-initdb.d    # DB init scripts from top-level db folder
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U fhir4java"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

---

## Implementation Phases

### Phase 1: Project Foundation
1. Create Maven multi-module project structure
2. Configure parent POM with Java 21, Spring Boot 3.4, HAPI FHIR dependencies
3. Set up Docker and docker-compose files
4. Create database initialization scripts with schema setup
5. Configure Spring Boot application with JPA

### Phase 2: Core Framework
1. Implement ResourceConfiguration model with **multi-version support**:
   - `FhirVersionConfig` for version list with default flag
   - `getDefaultVersion()`, `supportsVersion()`, `getSupportedVersions()` methods
   - **`SearchParameterConfig`** for allowlist/denylist search parameter restrictions
   - `isSearchParameterAllowed(paramName, isCommon)` method
2. Build ResourceRegistry with **version-aware** configuration loading:
   - `getResourcesForVersion(FhirVersion)` method
   - `getDefaultVersion(resourceType)` method
   - `supportsVersion(resourceType, version)` method
   - **`isSearchParameterAllowed(resourceType, paramName, isCommon)`** method
   - **`getSearchParameterConfig(resourceType)`** method
3. Implement InteractionGuard with version checking:
   - Validate both version support and interaction enablement
4. Create FhirContext factory for R4B and R5 support
5. Build basic JPA entities and repositories
6. Implement **version-aware** SearchParameterRegistry:
   - Load from `fhir-config/r5/searchparameters/` for R5
   - Load from `fhir-config/r4b/searchparameters/` for R4B
   - Version-keyed parameter storage
   - `getSearchParameters(FhirVersion, resourceType)` method
   - **`getAllowedSearchParameters(version, resourceType, resourceRegistry)`** method
   - **`isSearchParameterAllowed(version, resourceType, paramName, resourceRegistry)`** method

### Phase 3: API Layer
1. Implement **FhirVersionResolver** for URL version extraction:
   - Parse versioned paths (`/fhir/r5/Patient`)
   - Parse unversioned paths (`/fhir/Patient`) → forward to default version
   - Return `ResolvedVersion` with version, explicit flag, and resource path
2. Implement **FhirVersionFilter** request interceptor:
   - Resolve FHIR version from request
   - Validate resource supports requested version
   - Store version in request attributes
   - Add `X-FHIR-Version` response header
3. Implement FhirResourceController with **dual path support**:
   - Handle both `/{version}/{resourceType}` and `/{resourceType}` patterns
   - Extract version from request attributes
4. Create request/response interceptors
5. Build content negotiation (JSON/XML)
6. Implement error handling with OperationOutcome responses
7. Implement extended operation endpoints (`/$operation`, `/{id}/$operation`)

### Phase 4: Validation Framework
1. Integrate HAPI FHIR validation with **version-specific** StructureDefinition support:
   - Load profiles from `fhir-config/{version}/profiles/`
2. Implement **SearchParameterValidator** with:
   - Validation against SearchParameterRegistry (is parameter defined?)
   - **Validation against resource configuration restrictions** (is parameter allowed?)
   - Support for parameter modifiers (:exact, :contains, etc.)
   - Return OperationOutcome for disallowed parameters
3. Build OperationDefinition validation for extended operations
4. Create validation result to OperationOutcome converter

### Phase 5: Plugin System
1. Define plugin SPI interfaces:
   - FhirPlugin (base interface)
   - AuthenticationPlugin, AuthorizationPlugin, CachePlugin
   - AuditPlugin, TelemetryPlugin, PerformancePlugin
   - **BusinessLogicPlugin** with OperationDescriptor-based routing
2. Implement OperationType enum (READ, CREATE, UPDATE, DELETE, SEARCH, OPERATION)
3. Implement OperationDescriptor for flexible plugin matching:
   - Support CRUD operations by resourceType + operationType + **fhirVersion**
   - Support extended operations by resourceType + operationCode + **fhirVersion**
4. Implement PluginOrchestrator with:
   - `executeCrudRequest()` for standard CRUD operations
   - `executeExtendedOperation()` for $merge, $validate, etc.
   - Unified pipeline with descriptor-based plugin routing
5. Build default plugins: AuditPlugin, TelemetryPlugin, PerformancePlugin
6. Create BusinessLogicPlugin with before/after hooks for both CRUD and extended operations

### Phase 6: Extended Operations
1. Implement **version-aware** ExtendedOperationRegistry:
   - Load from `fhir-config/{version}/operations/`
   - `getOperationsForResource(FhirVersion, resourceType)` method
2. Build OperationDefinition loader and validator
3. Create InternalOperationExecutor for patch/search
4. Implement Extended Operations Pipeline:
   - Operation resolution and input validation
   - **BusinessLogicPlugin BEFORE hooks** (validation, transformation, abort)
   - Core operation execution via ExtendedOperationHandler
   - **BusinessLogicPlugin AFTER hooks** (enrichment, notifications, sync)
5. Implement example extended operations ($merge, $validate, $everything)
6. Add BDD tests for extended operations (features/operations/*.feature)

### Phase 7: Advanced Features
1. Implement search functionality with index tables:
   - Load search parameters from **version-specific** folders
   - Support all common parameters (_id, _lastUpdated, _tag, _profile, etc.)
   - Support search modifiers (:exact, :contains, :missing, :not, etc.)
   - Support search prefixes (eq, ne, gt, lt, ge, le, sa, eb, ap)
2. Add history/vread support
3. Build **version-aware** CapabilityStatement generator:
   - Generate version-specific CapabilityStatement
   - Only include resources that support requested version
   - Load base config from `fhir-config/{version}/capability.json`
   - Version-specific search parameters and operations
4. Add batch/transaction support (optional)
5. Add BDD tests for plugins (features/plugins/*.feature)
6. Add BDD tests for version resolution and forwarding

---

## Implementation Status

> **Last Updated:** January 2026

### ✅ Phase 1: Project Foundation - COMPLETED
- Maven multi-module project structure created
- Parent POM configured with Java 25 LTS, Spring Boot 3.4, HAPI FHIR 7.x
- Docker and docker-compose files set up
- Database initialization scripts created
- Spring Boot application configured with JPA

**Commit:** `5d27f0c` - Restructure fhir-config for multi-version FHIR support

### ✅ Phase 2: Core Framework - COMPLETED
- ResourceConfiguration model with multi-version support
- ResourceRegistry with version-aware configuration loading
- InteractionGuard with version checking
- FhirContextFactory for R4B and R5 support
- JPA entities and repositories (FhirResourceEntity)
- Version-aware SearchParameterRegistry loading from JSON definitions
- SearchParameterConfig with allowlist/denylist support

**Commit:** `f9b3b1f` - Implement Phase 2: Core Framework for multi-version FHIR support

### ✅ Phase 3: API Layer - COMPLETED
- FhirVersionResolver for URL version extraction
- FhirVersionFilter request interceptor
- FhirResourceController with dual path support (`/{version}/{resourceType}` and `/{resourceType}`)
- FhirResourceService with full CRUD operations (create, read, vread, update, delete, search, history)
- Content negotiation (JSON/XML via FhirMediaType, FhirWebConfig)
- Error handling with OperationOutcome responses (FhirExceptionHandler, OperationOutcomeBuilder)
- Extended operation endpoints via OperationController

**Commit:** `d77abe1` - Implement Phase 3: FHIR resource services and advanced search

### ✅ Phase 6: Extended Operations - PARTIALLY COMPLETED
- Operation framework implemented (OperationHandler, OperationRegistry, OperationContext, OperationScope)
- OperationService for invoking operations
- Sample $validate operation handler implemented

**Included in Commit:** `d77abe1`

### ✅ Phase 7: Advanced Features - PARTIALLY COMPLETED
Advanced search functionality implemented with full FHIR search parameter type support:

| Parameter Type | Supported Formats | Modifiers |
|---------------|-------------------|-----------|
| **Token** | `system\|code`, `\|code`, `system\|`, `code` | `:exact`, `:text`, `:not`, `:missing` |
| **Quantity** | `[prefix]value\|system\|code` | Prefixes: eq, ne, lt, gt, le, ge, ap |
| **Reference** | `[type]/[id]`, absolute URL, `[id]` | `:identifier`, `:missing`, type modifier |
| **Composite** | `value1$value2` | Uses SearchParameter components |
| **Date** | ISO formats (instant, date, year-month, year) | Prefixes: eq, ne, lt, gt, le, ge, sa, eb, ap |
| **Number** | Numeric values | Prefixes: eq, ne, lt, gt, le, ge |
| **String** | Text values | `:exact`, `:contains`, `:missing` |
| **URI** | URI values | `:above`, `:below`, `:missing` |

**Included in Commit:** `d77abe1`

### 🔧 Bug Fixes and Enhancements

**Commit `ead62f1`** - fix: Improve quantity search and error handling
- Fixed quantity/number search by using PostgreSQL `to_number()` function instead of `CAST` (which doesn't work with JPA Criteria API)
- Fixed polymorphic element path resolution (`value[x]` → `valueQuantity`) by properly handling FHIRPath `ofType()` and `as` operators
- Added `NoResourceFoundException` handler for static resources like favicon.ico

**Commit `072f5d6`** - Fix search parameter expression filtering for resource-specific queries
- Added `filterExpressionByResourceType()` method in `SearchParameterRegistry` to filter multi-resource FHIRPath expressions
- Updated `FhirResourceRepositoryImpl` to pass expression parameter to date, number, and string predicate builders
- Ensures correct JSON path extraction for multi-resource parameters like `clinical-date` and `clinical-code`

### ✅ Phase 4: Validation Framework - COMPLETED

**Commit `3a145c0`** - Implement Phase 4: Validation Framework

#### Validation Core Classes (fhir4java-core)
- **IssueSeverity.java** - Enum for validation issue severity levels (ERROR, WARNING, INFORMATION) with FHIR OperationOutcome severity mapping
- **ValidationIssue.java** - Record for individual validation issues with factory methods (error, warning, notFound, notSupported, information)
- **ValidationResult.java** - Container for validation issues with helper methods (isValid, hasErrors, merge) and static factory methods

#### Validators
- **SearchParameterValidator.java** - Validates search parameters against:
  - SearchParameterRegistry (is parameter defined for resource type?)
  - Resource configuration restrictions (allowlist/denylist via SearchParameterConfig)
  - Common FHIR parameters (_id, _lastUpdated, _count, _offset, _format, _pretty, _summary, _elements)
  - Modifier validation (contains, exact, missing, etc.)

- **ProfileValidator.java** - HAPI FHIR integration for profile validation:
  - Version-specific validators with CachingValidationSupport
  - ValidationSupportChain with DefaultProfileValidationSupport, InMemoryTerminologyServerValidationSupport, CommonCodeSystemsTerminologyService
  - FhirInstanceValidator configured with no terminology checks disabled
  - Validates against base StructureDefinition or specific profiles
  - `validateAgainstRequiredProfiles()` for resource-type configured profiles

#### Result Conversion
- **ValidationResultConverter.java** - Converts ValidationResult to FHIR OperationOutcome:
  - `toOperationOutcome()` with optional success message
  - Factory methods: `createErrorOutcome()`, `createNotFoundOutcome()`, `createInvalidParameterOutcome()`, `createSuccessOutcome()`
  - Utility methods: `isSuccessful()`, `countErrors()`

#### Configuration
- **ValidationConfig.java** - Spring @Value-based configuration:
  - `fhir4java.validation.enabled` (default: true)
  - `fhir4java.validation.profile-validation` (strict/lenient/off, default: lenient)
  - `fhir4java.validation.validate-search-parameters` (default: true)
  - `fhir4java.validation.fail-on-unknown-search-parameters` (default: false)
  - ProfileValidationMode enum (STRICT, LENIENT, OFF)

#### Integration
- **ResourceRegistry.java** - Added `isResourceConfigured()` method for validation checks
- **FhirResourceService.java** - Integrated validation into CRUD operations:
  - Profile validation on create/update (when enabled and profile validation not OFF)
  - Search parameter validation on search (when enabled)
  - `validateResourceOrThrow()` - throws FhirException on strict mode errors, logs warnings in lenient mode
  - `validateSearchParametersOrThrow()` - throws FhirException when failOnUnknownSearchParameters is true

### ⏳ Phase 5: Plugin System - NOT STARTED
- Plugin SPI interfaces
- PluginOrchestrator
- Default plugins (Audit, Telemetry, Performance)
- BusinessLogicPlugin with CRUD and operation hooks

### Remaining Items
- [ ] History/vread endpoint implementation (basic structure exists)
- [ ] Version-aware CapabilityStatement generator
- [ ] Batch/transaction support
- [ ] BDD tests for all features
- [ ] Plugin system implementation

---

## Key Files to Create

| Module | File | Purpose |
|--------|------|---------|
| parent | `pom.xml` | Maven parent POM |
| core | `ResourceRegistry.java` | Resource configuration registry |
| core | `SearchParameterRegistry.java` | Search parameter configuration registry |
| core | `CapabilityStatementGenerator.java` | Dynamic CapabilityStatement generation |
| core | `InteractionGuard.java` | Interaction enable/disable guard |
| core | `FhirContextFactory.java` | FHIR context for R4B/R5 |
| core | `FhirVersion.java` | Enum for supported FHIR versions |
| core | `FhirVersionConfig.java` | Version config with default flag |
| core | `SearchParameterConfig.java` | Search parameter allowlist/denylist configuration |
| core | `SearchParameterMode.java` | Enum for ALLOWLIST/DENYLIST modes |
| core | `ProfileValidator.java` | StructureDefinition validation (version-aware) ✅ |
| core | `SearchParameterValidator.java` | Search parameter validation (with restriction checking) ✅ |
| core | `IssueSeverity.java` | Enum for validation issue severity levels ✅ |
| core | `ValidationIssue.java` | Record for individual validation issues ✅ |
| core | `ValidationResult.java` | Container for validation issues ✅ |
| core | `ValidationResultConverter.java` | Converts ValidationResult to OperationOutcome ✅ |
| core | `ValidationConfig.java` | Validation configuration with @Value annotations ✅ |
| persistence | `ResourceEntity.java` | Main JPA entity |
| persistence | `ResourceRepository.java` | Spring Data repository |
| persistence | `SchemaManager.java` | Dynamic schema management |
| persistence | `PersistenceProvider.java` | Provider interface for storage abstraction |
| persistence | `PersistenceOrchestrator.java` | Write-through orchestrator with rollback |
| persistence | `JpaPersistenceProvider.java` | Internal PostgreSQL provider |
| persistence | `ExternalSyncService.java` | External sync service interface |
| persistence | `ExternalFhirSyncService.java` | External FHIR server sync |
| persistence | `CloudStorageSyncService.java` | S3/Azure/GCS sync service |
| persistence | `SyncFailureEntity.java` | Sync failure tracking entity |
| persistence | `SyncFailureRepository.java` | Sync failure repository |
| persistence | `SyncRetryScheduler.java` | Scheduled retry for failed syncs |
| persistence | `ExternalReferenceEntity.java` | External ID reference tracking |
| api | `MetadataController.java` | /metadata endpoint for CapabilityStatement |
| api | `FhirResourceController.java` | Main REST controller (dual path support) |
| api | `FhirExceptionHandler.java` | Global error handling |
| api | `FhirVersionResolver.java` | URL version extraction and resolution |
| api | `FhirVersionFilter.java` | Request filter for version handling |
| api | `ResolvedVersion.java` | DTO for resolved version info |
| plugin | `FhirPlugin.java` | Base plugin interface with execution modes |
| plugin | `PluginOrchestrator.java` | Plugin execution pipeline orchestrator |
| plugin | `AuthenticationPlugin.java` | Authentication plugin interface |
| plugin | `JwtAuthenticationPlugin.java` | JWT authentication implementation |
| plugin | `AuthorizationPlugin.java` | Authorization plugin interface |
| plugin | `SmartAuthorizationPlugin.java` | SMART on FHIR authorization |
| plugin | `CachePlugin.java` | Cache plugin interface |
| plugin | `InMemoryCachePlugin.java` | In-memory cache (dev/testing) |
| plugin | `RedisCachePlugin.java` | Redis cache (production) |
| plugin | `AuditPlugin.java` | Audit plugin interface (async) |
| plugin | `DatabaseAuditPlugin.java` | Database audit implementation |
| plugin | `AuditLogEntity.java` | Audit log JPA entity |
| plugin | `AuditPayloadEntity.java` | Separate payload storage entity |
| plugin | `AuditLogRepository.java` | Audit log repository |
| plugin | `AuditPayloadRepository.java` | Audit payload repository |
| plugin | `TelemetryPlugin.java` | Telemetry plugin interface with OTEL support |
| plugin | `OpenTelemetryPlugin.java` | OTEL implementation with tracing levels |
| plugin | `SqlTracingInterceptor.java` | SQL statement tracing for DETAILED level |
| plugin | `TelemetryContextHolder.java` | Context holder for span propagation |
| plugin | `PerformancePlugin.java` | Performance tracking interface |
| plugin | `DatabasePerformancePlugin.java` | Database performance tracking |
| plugin | `BusinessLogicPlugin.java` | Business logic plugin interface |
| plugin | `OperationType.java` | Enum for CRUD + OPERATION types |
| plugin | `OperationDescriptor.java` | Descriptor for plugin matching (resourceType, opType, opCode) |
| plugin | `BusinessContext.java` | Context for business logic plugins (request, resource, params) |
| plugin | `OperationResult.java` | Result from core operation (resource, params, outcome) |
| plugin | `McpPluginManager.java` | MCP plugin manager for external plugins |
| plugin | `HybridPluginOrchestrator.java` | Orchestrator for Spring + MCP plugins |
| plugin | `McpPluginConfig.java` | MCP plugin configuration |
| server | `application.yml` | Main configuration |
| server | `fhir-config/resources/*.yml` | Resource configurations (multi-version support) |
| server | `fhir-config/r5/capability.json` | R5 CapabilityStatement base config |
| server | `fhir-config/r5/searchparameters/SearchParameter-*.json` | R5 search parameters |
| server | `fhir-config/r5/operations/OperationDefinition-*.json` | R5 operation definitions |
| server | `fhir-config/r5/profiles/StructureDefinition-*.json` | R5 structure definitions |
| server | `fhir-config/r4b/capability.json` | R4B CapabilityStatement base config |
| server | `fhir-config/r4b/searchparameters/SearchParameter-*.json` | R4B search parameters |
| server | `fhir-config/r4b/operations/OperationDefinition-*.json` | R4B operation definitions |
| server | `fhir-config/r4b/profiles/StructureDefinition-*.json` | R4B structure definitions |
| docker | `docker-compose.yml` | Container orchestration |
| db | `init/00-init-schemas.sql` | Create database schemas |
| db | `init/01-resource-tables.sql` | Main resource_data table with partitions |
| db | `init/02-history-tables.sql` | History table with time-based partitions |
| db | `init/03-search-index.sql` | Search index table with partitions |
| db | `init/04-audit-tables.sql` | Audit and performance tracking tables |
| db | `indexes/01-covering-indexes.sql` | Covering indexes for common queries |
| db | `indexes/02-search-indexes.sql` | Search parameter indexes |
| db | `migrations/V1__initial_schema.sql` | Flyway initial migration |
| db | `partitions/partition-maintenance.sql` | Partition management scripts |
| test | `features/operations/patient-merge.feature` | BDD tests for $merge operation |
| test | `features/operations/resource-validate.feature` | BDD tests for $validate operation |
| test | `features/operations/patient-everything.feature` | BDD tests for $everything operation |
| test | `features/operations/operation-errors.feature` | BDD tests for operation error handling |
| test | `features/plugins/before-operation-plugins.feature` | BDD tests for before-operation hooks |
| test | `features/plugins/after-operation-plugins.feature` | BDD tests for after-operation hooks |
| test | `features/plugins/plugin-chain-execution.feature` | BDD tests for plugin chain ordering |
| test | `features/plugins/plugin-crud-operations.feature` | BDD tests for plugins with CRUD |
| test | `steps/ExtendedOperationSteps.java` | Step definitions for extended operations |
| test | `steps/BusinessLogicPluginSteps.java` | Step definitions for business logic plugins |

---

## Verification Plan

1. **Build Verification**
   - Run `mvn clean install` - all modules should compile
   - Run `docker-compose up --build` - containers start successfully

2. **CapabilityStatement/Metadata Endpoint Testing**
   - GET `/fhir/r5/metadata` - returns valid CapabilityStatement
   - Verify CapabilityStatement contains all registered resources
   - Verify each resource lists enabled interactions (read, create, update, etc.)
   - Verify search parameters include both base params (_id, _lastUpdated) and resource-specific params
   - Verify extended operations are listed for each resource
   - OPTIONS `/fhir/r5` - returns CapabilityStatement

3. **API Testing**
   - POST `/fhir/r5/Patient` with valid Patient resource - returns 201
   - GET `/fhir/r5/Patient/{id}` - returns the created patient
   - POST with invalid resource - returns 400 with OperationOutcome
   - Access disabled interaction - returns 405

4. **FHIR Version Resolution Testing**
   - **Versioned URL Paths:**
     - GET `/fhir/r5/Patient` - uses R5, response has `X-FHIR-Version: R5`
     - GET `/fhir/r4b/Patient` - uses R4B, response has `X-FHIR-Version: R4B`
     - GET `/fhir/r5/metadata` - returns R5 CapabilityStatement
     - GET `/fhir/r4b/metadata` - returns R4B CapabilityStatement
   - **Unversioned URL Paths (forward to default):**
     - GET `/fhir/Patient` - forwards to resource's default version
     - GET `/fhir/Patient/123` - forwards to resource's default version
     - Response includes `X-FHIR-Version` header with actual version used
   - **Version Support Validation:**
     - Request R4B for resource only configured for R5 - returns 400
     - Request R5 for resource configured for both - succeeds
   - **Extended Operations:**
     - POST `/fhir/r5/Patient/$merge` - uses R5 operation definitions
     - POST `/fhir/Patient/$merge` - uses default version operation definitions

5. **Search Parameter Validation Testing**
   - GET `/fhir/r5/Patient?family=Smith` - valid search parameter works
   - GET `/fhir/r5/Patient?invalid_param=value` - returns 400 with OperationOutcome
   - GET `/fhir/r5/Patient?_id=123` - base search parameter works across all resources
   - Verify search parameters in CapabilityStatement match those defined in configuration
   - **Search Parameter Restriction Testing (Allowlist/Denylist):**
     - **Allowlist mode:**
       - GET `/fhir/r5/Patient?family=Smith` - allowed parameter succeeds
       - GET `/fhir/r5/Patient?address-city=Boston` - disallowed parameter returns 400
       - Verify CapabilityStatement only lists allowed parameters
     - **Denylist mode:**
       - GET `/fhir/r5/Observation?code=1234-5` - allowed (not in denylist) succeeds
       - GET `/fhir/r5/Observation?_text=blood` - denied parameter returns 400
       - Verify CapabilityStatement excludes denied parameters
     - **No restrictions (default):**
       - All defined search parameters are allowed
       - All parameters appear in CapabilityStatement
     - **Modifiers with restricted parameters:**
       - GET `/fhir/r5/Patient?family:exact=Smith` - allowed base parameter with modifier works
       - GET `/fhir/r5/Patient?address-city:contains=Bos` - disallowed base parameter with modifier returns 400

6. **Profile Validation Testing**
   - Submit resource not matching required profile - validation error
   - Submit resource matching required profile - succeeds

6. **Extended Operation Testing** (BDD: `features/operations/*.feature`)
   - **$merge Operation** (`@operations @merge`):
     - Type-level and instance-level invocation
     - Validation: source/target exist, not same patient, source is active
     - Error handling: missing params, non-existent patients
     - BusinessLogicPlugin BEFORE hooks execute (validation, transformation)
     - BusinessLogicPlugin AFTER hooks execute (notifications, sync)
   - **$validate Operation** (`@operations @validate`):
     - Validate resource against base spec
     - Validate against specific profile
     - Validate with mode (create, update)
   - **$everything Operation** (`@operations @everything`):
     - Return all related resources
     - Support date range and _type filters
   - **Error Handling** (`@operations @errors`):
     - Call undefined operation - returns 404
     - Call operation on wrong resource type - returns 404
     - Invalid parameter types - returns 400

7. **Plugin Testing** (BDD: `features/plugins/*.feature`)
   - **Cache Plugin**:
     - First GET returns from database, second GET returns from cache (verify with trace logs)
     - Cache invalidation on PUT/DELETE
     - In-memory cache works in dev profile
     - Redis cache works in production profile
   - **Authentication Plugin**:
     - Request without token returns 401
     - Request with invalid JWT returns 401
     - Request with valid JWT proceeds
   - **Authorization Plugin**:
     - Request without proper scopes returns 403
     - Request with proper scopes proceeds
   - **Audit Plugin** (enhanced):
     - Verify CREATE captures new resource ID and version
     - Verify READ captures resource ID and version read
     - Verify UPDATE captures before/after resource ID and version
     - Verify DELETE captures deleted resource ID and version
     - Verify SEARCH captures search parameters as JSON
     - Verify Extended Operation captures operation name and parameters
     - Verify payloads stored in separate audit_payload table
     - Verify audit_log references payload via payload_id
   - **Performance Plugin**:
     - Verify performance traces are created in fhir_performance schema
     - Verify each step (auth, cache, business, core) is traced
   - **Business Logic Plugin** (BDD: `features/plugins/*.feature`):
     - **Before Operation Plugins** (`@plugins @before-operation`):
       - Plugins execute in order (by @Order annotation)
       - Plugin can abort operation (PluginResult.failure())
       - Plugin can modify resource/parameters before operation
       - Plugin can validate business rules and return errors
       - Short-circuit: continueChain=false stops chain but continues operation
     - **After Operation Plugins** (`@plugins @after-operation`):
       - Plugins execute after core operation completes
       - Plugin can transform/enrich output
       - Plugin can trigger notifications, webhooks, sync
       - Async plugins don't block response
       - Plugin errors logged but don't fail operation
     - **Plugin Chain Execution** (`@plugins @chain`):
       - Plugins execute in order priority
       - Context attributes shared across plugins
       - Selective execution based on OperationDescriptor matching
     - **Extended Operation Support**:
       - BusinessLogicPlugin.supports(OperationDescriptor) matches by operationCode
       - Before hooks can validate/transform input Parameters
       - After hooks can enrich output, trigger side effects
       - Example: PatientMergeValidationPlugin for $merge
   - **Telemetry/OTEL Plugin**:
     - Verify MINIMAL level only traces request lifecycle
     - Verify STANDARD level traces auth, cache, validation, business, core steps
     - Verify DETAILED level includes SQL statements and parsing times
     - Verify OTEL spans are exported to configured exporter (OTLP/Jaeger/Zipkin)
     - Verify metrics are recorded (counters, histograms)

8. **Persistence Provider Testing**
   - **Internal Only Mode**:
     - Verify CRUD operations work with PostgreSQL only
     - Verify no external sync triggered
   - **Write-Through Pattern (EXTERNAL_SYNC)**:
     - Verify data stored in internal database FIRST
     - Verify external service called AFTER internal save
     - Verify external reference stored on success
   - **Rollback on Failure (ROLLBACK_ON_FAILURE)**:
     - Simulate external service failure on CREATE
     - Verify internal database changes are rolled back
     - Simulate external service failure on UPDATE
     - Verify resource reverted to previous version
   - **Keep Internal on Failure (KEEP_INTERNAL)**:
     - Simulate external service failure
     - Verify internal changes are kept
     - Verify failure recorded in sync_failure table
     - Verify retry scheduler picks up pending retries
     - Verify exponential backoff applied
   - **Async Sync (EXTERNAL_ASYNC)**:
     - Verify internal save completes immediately
     - Verify external sync queued for async processing
     - Verify API response not blocked by external sync
   - **Per-Resource Configuration**:
     - Verify Patient uses INTERNAL_ONLY
     - Verify Observation uses EXTERNAL_SYNC with rollback
     - Verify different resources use different external services
   - **Sync Retry**:
     - Verify failed syncs are retried after delay
     - Verify max retries respected
     - Verify exponential backoff working
     - Verify status transitions (PENDING_RETRY → RETRYING → SUCCEEDED/FAILED)

9. **Configuration Testing**
   - Add new resource YAML file - resource becomes available
   - Add new search parameter YAML file - parameter becomes available
   - Verify new resource/parameters appear in CapabilityStatement
   - Disable interaction in config - interaction returns 405

---

## BDD API Testing with Cucumber

### Overview

The project uses Cucumber BDD framework for API testing with Gherkin syntax. This provides readable, business-focused test scenarios that serve as living documentation.

### Test Module Structure

```
fhir4java-server/
└── src/
    └── test/
        ├── java/
        │   └── com/fhir4java/
        │       └── bdd/
        │           ├── CucumberTestRunner.java
        │           ├── SpringIntegrationTest.java
        │           ├── steps/
        │           │   ├── CommonSteps.java
        │           │   ├── PatientSteps.java
        │           │   ├── ObservationSteps.java
        │           │   ├── SearchSteps.java
        │           │   ├── HistorySteps.java
        │           │   ├── ValidationSteps.java
        │           │   ├── CacheSteps.java
        │           │   ├── SecuritySteps.java
        │           │   ├── ExtendedOperationSteps.java      # Extended operation steps
        │           │   └── BusinessLogicPluginSteps.java    # Business logic plugin steps
        │           ├── plugins/
        │           │   ├── TestPatientConsentPlugin.java    # Test plugin for consent validation
        │           │   ├── TestPatientMergePlugin.java      # Test plugin for $merge
        │           │   └── TestAuditCapturePlugin.java      # Test plugin to capture audit events
        │           └── context/
        │               └── TestContext.java
        └── resources/
            ├── features/
            │   ├── patient/
            │   │   ├── patient-crud.feature
            │   │   ├── patient-search.feature
            │   │   └── patient-history.feature
            │   ├── observation/
            │   │   ├── observation-crud.feature
            │   │   └── observation-search.feature
            │   ├── search/
            │   │   ├── common-search-parameters.feature
            │   │   ├── search-modifiers.feature
            │   │   └── search-pagination.feature
            │   ├── cache/
            │   │   ├── resource-cache.feature
            │   │   ├── search-cache.feature
            │   │   └── cache-invalidation.feature
            │   ├── validation/
            │   │   ├── profile-validation.feature
            │   │   └── resource-validation.feature
            │   ├── security/
            │   │   ├── authentication.feature
            │   │   └── authorization.feature
            │   ├── operations/                              # Extended operations tests
            │   │   ├── patient-merge.feature                # Patient $merge operation
            │   │   ├── patient-everything.feature           # Patient $everything operation
            │   │   ├── resource-validate.feature            # $validate operation
            │   │   └── operation-errors.feature             # Operation error handling
            │   ├── plugins/                                 # Business logic plugin tests
            │   │   ├── before-operation-plugins.feature     # Before operation plugin tests
            │   │   ├── after-operation-plugins.feature      # After operation plugin tests
            │   │   ├── plugin-chain-execution.feature       # Plugin chain ordering
            │   │   └── plugin-crud-operations.feature       # Plugins with CRUD operations
            │   └── metadata/
            │       └── capability-statement.feature
            ├── test-data/
            │   ├── patients/
            │   │   ├── valid-patient.json
            │   │   ├── invalid-patient.json
            │   │   ├── patient-us-core.json
            │   │   ├── patient-for-merge-source.json        # Source patient for merge tests
            │   │   └── patient-for-merge-target.json        # Target patient for merge tests
            │   ├── observations/
            │   │   ├── valid-observation.json
            │   │   └── vital-signs-observation.json
            │   └── operations/
            │       ├── merge-parameters.json                # $merge input parameters
            │       └── validate-parameters.json             # $validate input parameters
            └── application-test.yml
```

### Maven Dependencies

Add to `fhir4java-server/pom.xml`:

```xml
<!-- BDD Testing Dependencies -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-spring</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <version>7.18.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite</artifactId>
    <version>1.10.2</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.4.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <version>2.0.1</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.1</version>
    <scope>test</scope>
</dependency>
```

### Test Runner Configuration

**File: `CucumberTestRunner.java`**
```java
package org.fhirframework.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/cucumber.html, json:target/cucumber-reports/cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "org.fhirframework.bdd.steps")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @ignore")
public class CucumberTestRunner {
}
```

**File: `SpringIntegrationTest.java`**
```java
package org.fhirframework.bdd;

import com.redis.testcontainers.RedisContainer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public class SpringIntegrationTest {

    @LocalServerPort
    protected int port;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fhir4java_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
}
```

**File: `TestContext.java`**
```java
package org.fhirframework.bdd.context;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TestContext {
    private Response response;
    private RequestSpecification request;
    private String authToken;
    private Map<String, String> createdResources = new HashMap<>();
    private String currentResourceType;
    private String currentResourceId;
    private Map<String, Object> scenarioData = new HashMap<>();

    // Getters and setters
    public Response getResponse() { return response; }
    public void setResponse(Response response) { this.response = response; }

    public RequestSpecification getRequest() { return request; }
    public void setRequest(RequestSpecification request) { this.request = request; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public void addCreatedResource(String resourceType, String id) {
        createdResources.put(resourceType + "/" + id, id);
        this.currentResourceType = resourceType;
        this.currentResourceId = id;
    }

    public String getCurrentResourceId() { return currentResourceId; }
    public String getCurrentResourceType() { return currentResourceType; }
    public Map<String, String> getCreatedResources() { return createdResources; }

    public void setScenarioData(String key, Object value) { scenarioData.put(key, value); }
    public Object getScenarioData(String key) { return scenarioData.get(key); }
    public <T> T getScenarioData(String key, Class<T> type) { return type.cast(scenarioData.get(key)); }

    public void reset() {
        response = null;
        request = null;
        currentResourceId = null;
        currentResourceType = null;
        scenarioData.clear();
    }
}
```

---

### Feature Files

#### 1. Patient CRUD Operations

**File: `features/patient/patient-crud.feature`**
```gherkin
@patient @crud
Feature: Patient CRUD Operations
  As a healthcare application
  I want to perform CRUD operations on Patient resources
  So that I can manage patient demographic information

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @create @smoke
  Scenario: Create a new Patient resource
    Given I have a valid Patient resource
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201
    And the response should contain a "Patient" resource
    And the response should have a "Location" header
    And the resource should have an "id" element
    And the resource should have a "meta.versionId" element
    And the resource should have a "meta.lastUpdated" element

  @create
  Scenario: Create Patient with client-assigned ID using PUT
    Given I have a valid Patient resource with id "patient-12345"
    When I send a PUT request to "/fhir/r5/Patient/patient-12345"
    Then the response status code should be 201
    And the resource id should be "patient-12345"

  @read @smoke
  Scenario: Read an existing Patient resource
    Given a Patient resource exists in the system
    When I send a GET request to "/fhir/r5/Patient/{id}"
    Then the response status code should be 200
    And the response should contain a "Patient" resource
    And the response should have "ETag" header with version

  @read
  Scenario: Read a non-existent Patient resource
    When I send a GET request to "/fhir/r5/Patient/non-existent-id"
    Then the response status code should be 404
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have severity "error"
    And the OperationOutcome should have code "not-found"

  @vread
  Scenario: Read a specific version of a Patient resource
    Given a Patient resource exists with multiple versions
    When I send a GET request to "/fhir/r5/Patient/{id}/_history/1"
    Then the response status code should be 200
    And the resource version should be "1"

  @update @smoke
  Scenario: Update an existing Patient resource
    Given a Patient resource exists in the system
    And I modify the Patient family name to "UpdatedName"
    When I send a PUT request to "/fhir/r5/Patient/{id}"
    Then the response status code should be 200
    And the resource family name should be "UpdatedName"
    And the resource version should be incremented

  @update
  Scenario: Update with version conflict (optimistic locking)
    Given a Patient resource exists in the system
    And another client has updated the resource
    When I send a PUT request with outdated If-Match header
    Then the response status code should be 409
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have code "conflict"

  @update
  Scenario: Conditional update - resource exists
    Given a Patient resource exists with identifier "MRN|12345"
    And I have an updated Patient resource
    When I send a PUT request to "/fhir/r5/Patient?identifier=MRN|12345"
    Then the response status code should be 200
    And the existing resource should be updated

  @update
  Scenario: Conditional update - resource does not exist
    Given no Patient exists with identifier "MRN|99999"
    And I have a valid Patient resource with identifier "MRN|99999"
    When I send a PUT request to "/fhir/r5/Patient?identifier=MRN|99999"
    Then the response status code should be 201
    And a new resource should be created

  @patch
  Scenario: Patch a Patient resource using JSON Patch
    Given a Patient resource exists in the system
    When I send a PATCH request with JSON Patch operations:
      | op      | path          | value       |
      | replace | /name/0/given | ["Modified"] |
    Then the response status code should be 200
    And the Patient given name should be "Modified"

  @patch
  Scenario: Patch a Patient resource using FHIR Patch
    Given a Patient resource exists in the system
    When I send a PATCH request with FHIR Patch Parameters:
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "operation",
            "part": [
              { "name": "type", "valueCode": "replace" },
              { "name": "path", "valueString": "Patient.active" },
              { "name": "value", "valueBoolean": false }
            ]
          }
        ]
      }
      """
    Then the response status code should be 200
    And the Patient active status should be false

  @delete @smoke
  Scenario: Delete a Patient resource
    Given a Patient resource exists in the system
    When I send a DELETE request to "/fhir/r5/Patient/{id}"
    Then the response status code should be 204
    And the resource should not be retrievable
    And the resource should be available in history

  @delete
  Scenario: Delete a non-existent Patient resource
    When I send a DELETE request to "/fhir/r5/Patient/non-existent-id"
    Then the response status code should be 204

  @delete
  Scenario: Conditional delete
    Given a Patient resource exists with identifier "MRN|to-delete"
    When I send a DELETE request to "/fhir/r5/Patient?identifier=MRN|to-delete"
    Then the response status code should be 204
    And no Patient should exist with identifier "MRN|to-delete"
```

#### 2. Patient Search Operations

**File: `features/patient/patient-search.feature`**
```gherkin
@patient @search
Feature: Patient Search Operations
  As a healthcare application
  I want to search for Patient resources using various parameters
  So that I can find patients matching specific criteria

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the following Patients exist:
      | id         | family    | given   | birthDate  | gender | identifier     | active |
      | patient-1  | Smith     | John    | 1980-01-15 | male   | MRN\|12345     | true   |
      | patient-2  | Smith     | Jane    | 1985-06-20 | female | MRN\|12346     | true   |
      | patient-3  | Johnson   | Robert  | 1990-03-10 | male   | MRN\|12347     | true   |
      | patient-4  | Williams  | Sarah   | 1975-11-30 | female | MRN\|12348     | false  |
      | patient-5  | Brown     | Michael | 2000-07-04 | male   | MRN\|12349     | true   |

  @smoke
  Scenario: Search patients by family name
    When I search for Patients with parameter "family" = "Smith"
    Then the response status code should be 200
    And the response should be a Bundle of type "searchset"
    And the Bundle should contain 2 entries
    And all entries should have family name "Smith"

  Scenario: Search patients by family name with exact modifier
    When I search for Patients with parameter "family:exact" = "Smith"
    Then the Bundle should contain 2 entries

  Scenario: Search patients by family name with contains modifier
    When I search for Patients with parameter "family:contains" = "mit"
    Then the Bundle should contain 2 entries

  Scenario: Search patients by given name
    When I search for Patients with parameter "given" = "John"
    Then the Bundle should contain 1 entry
    And the entry should have given name "John"

  Scenario: Search patients by name (any part)
    When I search for Patients with parameter "name" = "Smith"
    Then the Bundle should contain 2 entries

  @smoke
  Scenario: Search patients by birthdate
    When I search for Patients with parameter "birthdate" = "1980-01-15"
    Then the Bundle should contain 1 entry
    And the entry should have birthDate "1980-01-15"

  Scenario: Search patients by birthdate range - greater than
    When I search for Patients with parameter "birthdate" = "gt1990-01-01"
    Then the Bundle should contain 2 entries
    And all entries should have birthDate after "1990-01-01"

  Scenario: Search patients by birthdate range - less than or equal
    When I search for Patients with parameter "birthdate" = "le1985-06-20"
    Then the Bundle should contain 3 entries

  Scenario: Search patients by birthdate range - between dates
    When I search for Patients with parameters:
      | parameter | value            |
      | birthdate | ge1980-01-01     |
      | birthdate | le1990-12-31     |
    Then the Bundle should contain 3 entries

  @smoke
  Scenario: Search patients by gender
    When I search for Patients with parameter "gender" = "male"
    Then the Bundle should contain 3 entries
    And all entries should have gender "male"

  @smoke
  Scenario: Search patients by identifier
    When I search for Patients with parameter "identifier" = "MRN|12345"
    Then the Bundle should contain 1 entry
    And the entry should have identifier system "MRN" and value "12345"

  Scenario: Search patients by identifier system only
    When I search for Patients with parameter "identifier" = "MRN|"
    Then the Bundle should contain 5 entries

  Scenario: Search patients by active status
    When I search for Patients with parameter "active" = "true"
    Then the Bundle should contain 4 entries
    And all entries should have active status true

  Scenario: Search with multiple parameters (AND logic)
    When I search for Patients with parameters:
      | parameter | value  |
      | family    | Smith  |
      | gender    | female |
    Then the Bundle should contain 1 entry
    And the entry should have family name "Smith"
    And the entry should have gender "female"

  Scenario: Search with missing modifier
    When I search for Patients with parameter "organization:missing" = "true"
    Then all entries should not have managingOrganization

  Scenario: Search with not modifier
    When I search for Patients with parameter "gender:not" = "male"
    Then all entries should have gender "female"

  Scenario: Search returns empty bundle when no matches
    When I search for Patients with parameter "family" = "NonExistentName"
    Then the response status code should be 200
    And the Bundle should contain 0 entries

  Scenario: Search with invalid parameter returns error
    When I search for Patients with parameter "invalid_param" = "value"
    Then the response status code should be 400
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should indicate invalid search parameter
```

#### 3. Common Search Parameters

**File: `features/search/common-search-parameters.feature`**
```gherkin
@search @common-params
Feature: Common FHIR Search Parameters
  As a healthcare application
  I want to use common search parameters across all resource types
  So that I can query resources using standard FHIR mechanisms

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @_id @smoke
  Scenario: Search by _id parameter
    Given a Patient resource exists with id "test-patient-001"
    When I search for Patients with parameter "_id" = "test-patient-001"
    Then the Bundle should contain 1 entry
    And the entry id should be "test-patient-001"

  Scenario: Search by multiple _id values
    Given Patients exist with ids "patient-a", "patient-b", "patient-c"
    When I search for Patients with parameter "_id" = "patient-a,patient-b"
    Then the Bundle should contain 2 entries

  @_lastUpdated
  Scenario: Search by _lastUpdated - exact match
    Given a Patient was last updated at "2024-01-15T10:30:00Z"
    When I search for Patients with parameter "_lastUpdated" = "2024-01-15T10:30:00Z"
    Then the Bundle should contain the matching Patient

  Scenario: Search by _lastUpdated - greater than
    Given resources were updated at various times
    When I search for Patients with parameter "_lastUpdated" = "gt2024-01-01"
    Then the Bundle should contain resources updated after "2024-01-01"

  @_tag
  Scenario: Search by _tag parameter
    Given a Patient exists with tag "http://example.org/tags|important"
    When I search for Patients with parameter "_tag" = "http://example.org/tags|important"
    Then the Bundle should contain the tagged Patient

  Scenario: Search by _tag code only
    Given a Patient exists with tag "http://example.org/tags|important"
    When I search for Patients with parameter "_tag" = "important"
    Then the Bundle should contain the tagged Patient

  @_profile
  Scenario: Search by _profile parameter
    Given a Patient exists claiming profile "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
    When I search for Patients with parameter "_profile" = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
    Then the Bundle should contain the profiled Patient

  @_security
  Scenario: Search by _security label
    Given a Patient exists with security label "http://terminology.hl7.org/CodeSystem/v3-Confidentiality|R"
    When I search for Patients with parameter "_security" = "http://terminology.hl7.org/CodeSystem/v3-Confidentiality|R"
    Then the Bundle should contain the labeled Patient

  @_source
  Scenario: Search by _source parameter
    Given a Patient exists with source "http://hospital.example.org/ehr"
    When I search for Patients with parameter "_source" = "http://hospital.example.org/ehr"
    Then the Bundle should contain the sourced Patient

  @_text
  Scenario: Search by _text parameter (full-text on narrative)
    Given a Patient exists with narrative containing "diabetes management"
    When I search for Patients with parameter "_text" = "diabetes"
    Then the Bundle should contain the Patient with matching narrative

  @_content
  Scenario: Search by _content parameter (full-text on entire resource)
    Given a Patient exists with extension containing "special-program"
    When I search for Patients with parameter "_content" = "special-program"
    Then the Bundle should contain the Patient with matching content

  @_list
  Scenario: Search by _list parameter
    Given a List "high-risk-patients" contains Patients "patient-1", "patient-2"
    When I search for Patients with parameter "_list" = "high-risk-patients"
    Then the Bundle should contain exactly 2 entries
    And the Bundle should contain Patient "patient-1"
    And the Bundle should contain Patient "patient-2"

  @_has
  Scenario: Search using _has for reverse chaining
    Given Patient "patient-has-001" has Observations with code "1234-5"
    And Patient "patient-has-002" has no Observations
    When I search for Patients with parameter "_has:Observation:patient:code" = "1234-5"
    Then the Bundle should contain 1 entry
    And the entry should be Patient "patient-has-001"

  @_has
  Scenario: Search using _has with multiple levels
    Given Patient "patient-has-003" has Encounter with Condition code "diabetes"
    When I search for Patients with parameter "_has:Encounter:patient:_has:Condition:encounter:code" = "diabetes"
    Then the Bundle should contain Patient "patient-has-003"

  @_type
  Scenario: Search across multiple resource types with _type
    Given Patient "multi-001" and Practitioner "multi-002" exist
    When I search across all resources with parameters:
      | parameter | value              |
      | _type     | Patient,Practitioner |
      | name      | Smith              |
    Then the Bundle should contain resources of type Patient and Practitioner

  @_summary
  Scenario: Search with _summary=true returns summary elements only
    Given a Patient resource exists with full data
    When I search for Patients with parameter "_summary" = "true"
    Then the response entries should contain only summary elements
    And the response should not contain non-summary elements like "contact"

  @_summary
  Scenario: Search with _summary=text returns text and mandatory elements
    Given a Patient resource exists with narrative
    When I search for Patients with parameter "_summary" = "text"
    Then the response entries should contain "text", "id", and "meta"

  @_summary
  Scenario: Search with _summary=count returns only count
    Given 10 Patient resources exist
    When I search for Patients with parameter "_summary" = "count"
    Then the Bundle total should be 10
    And the Bundle should contain 0 entries

  @_summary
  Scenario: Search with _summary=data excludes text element
    Given a Patient resource exists with narrative
    When I search for Patients with parameter "_summary" = "data"
    Then the response entries should not contain "text" element

  @_elements
  Scenario: Search with _elements returns only specified elements
    Given a Patient resource exists with name, birthDate, and address
    When I search for Patients with parameter "_elements" = "name,birthDate"
    Then the response entries should contain "name" and "birthDate"
    And the response entries should not contain "address"

  @_elements
  Scenario: Search with _elements always includes mandatory elements
    Given a Patient resource exists
    When I search for Patients with parameter "_elements" = "name"
    Then the response entries should contain "id" and "meta"

  @_contained
  Scenario: Search with _contained=true searches contained resources
    Given a Patient resource with contained Organization
    When I search for Patients with parameter "_contained" = "true"
    Then the search should include contained resources

  @_contained
  Scenario: Search with _contained=both searches all
    Given a Patient resource with and without contained resources
    When I search for Patients with parameter "_contained" = "both"
    Then the Bundle should contain both types of Patients
```

#### 3b. Search Modifiers

**File: `features/search/search-modifiers.feature`**
```gherkin
@search @modifiers
Feature: FHIR Search Modifiers
  As a healthcare application
  I want to use search modifiers to refine my searches
  So that I can precisely control how search parameters are matched

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the following Patients exist:
      | id          | family     | given    | birthDate  | gender | identifier        | active |
      | mod-test-1  | Smith      | John     | 1980-01-15 | male   | MRN\|12345        | true   |
      | mod-test-2  | SMITH      | Jane     | 1985-06-20 | female | MRN\|12346        | true   |
      | mod-test-3  | Smithson   | Robert   | 1990-03-10 | male   | SSN\|123-45-6789  | true   |
      | mod-test-4  | VanSmith   | Sarah    | 1975-11-30 | female | MRN\|12348        | false  |
      | mod-test-5  | Jones      | Michael  | 2000-07-04 | male   |                   | true   |

  # :missing modifier
  @missing @smoke
  Scenario: Search with :missing=true finds resources without the element
    When I search for Patients with parameter "identifier:missing" = "true"
    Then the Bundle should contain 1 entry
    And the entry should be Patient "mod-test-5"

  Scenario: Search with :missing=false finds resources with the element
    When I search for Patients with parameter "identifier:missing" = "false"
    Then the Bundle should contain 4 entries
    And all entries should have an identifier element

  Scenario: Search with :missing on optional element
    Given Patient "mod-test-6" has no managingOrganization
    When I search for Patients with parameter "organization:missing" = "true"
    Then the Bundle should contain Patient "mod-test-6"

  # :exact modifier
  @exact @smoke
  Scenario: Search with :exact matches case-sensitively
    When I search for Patients with parameter "family:exact" = "Smith"
    Then the Bundle should contain 1 entry
    And the entry should have family name "Smith"

  Scenario: Search with :exact does not match different case
    When I search for Patients with parameter "family:exact" = "smith"
    Then the Bundle should contain 0 entries

  Scenario: Search with :exact does not match partial strings
    When I search for Patients with parameter "family:exact" = "Smit"
    Then the Bundle should contain 0 entries

  # :contains modifier
  @contains @smoke
  Scenario: Search with :contains matches substring anywhere
    When I search for Patients with parameter "family:contains" = "Smith"
    Then the Bundle should contain 3 entries
    And all entries should have family name containing "Smith"

  Scenario: Search with :contains is case-insensitive
    When I search for Patients with parameter "family:contains" = "smith"
    Then the Bundle should contain 3 entries

  Scenario: Search with :contains matches at start
    When I search for Patients with parameter "family:contains" = "Van"
    Then the Bundle should contain 1 entry
    And the entry should be Patient "mod-test-4"

  # :text modifier
  @text
  Scenario: Search token with :text matches on display text
    Given an Observation exists with code display "Blood Pressure"
    When I search for Observations with parameter "code:text" = "Blood"
    Then the Bundle should contain the Observation with Blood Pressure code

  Scenario: Search reference with :text matches on display
    Given a Patient exists with generalPractitioner display "Dr. Smith"
    When I search for Patients with parameter "general-practitioner:text" = "Smith"
    Then the Bundle should contain the Patient

  # :not modifier
  @not @smoke
  Scenario: Search with :not excludes matching values
    When I search for Patients with parameter "gender:not" = "male"
    Then the Bundle should contain 2 entries
    And all entries should have gender "female"

  Scenario: Search with :not on token with system
    When I search for Patients with parameter "identifier:not" = "MRN|12345"
    Then the Bundle should not contain Patient "mod-test-1"
    And the Bundle should contain Patients with other identifiers

  # :above modifier (hierarchical)
  @above
  Scenario: Search with :above finds ancestors
    Given Locations with hierarchy "Building A > Floor 1 > Room 101"
    When I search for Locations with parameter "partof:above" = "Room-101"
    Then the Bundle should contain "Floor-1" and "Building-A"

  Scenario: Search token with :above for code system hierarchy
    Given Conditions with SNOMED codes in hierarchy
    When I search for Conditions with parameter "code:above" = "http://snomed.info/sct|73211009"
    Then the Bundle should contain Conditions with ancestor codes

  # :below modifier (hierarchical)
  @below
  Scenario: Search with :below finds descendants
    Given Locations with hierarchy "Building A > Floor 1 > Room 101"
    When I search for Locations with parameter "partof:below" = "Building-A"
    Then the Bundle should contain "Floor-1" and "Room-101"

  Scenario: Search token with :below for code system hierarchy
    Given Conditions with SNOMED codes in hierarchy
    When I search for Conditions with parameter "code:below" = "http://snomed.info/sct|404684003"
    Then the Bundle should contain Conditions with descendant codes

  # :in modifier (ValueSet membership)
  @in
  Scenario: Search with :in matches codes in ValueSet
    Given a ValueSet "vital-signs-codes" contains codes "85354-9", "8480-6", "8462-4"
    And Observations exist with those codes
    When I search for Observations with parameter "code:in" = "vital-signs-codes"
    Then the Bundle should contain Observations with those codes only

  Scenario: Search with :in using ValueSet URL
    When I search for Observations with parameter "code:in" = "http://hl7.org/fhir/ValueSet/observation-vitalsignresult"
    Then the Bundle should contain Observations with vital sign codes

  # :not-in modifier (ValueSet non-membership)
  @not-in
  Scenario: Search with :not-in excludes codes in ValueSet
    Given a ValueSet "excluded-codes" contains codes "X", "Y", "Z"
    And Observations exist with codes "A", "X", "B"
    When I search for Observations with parameter "code:not-in" = "excluded-codes"
    Then the Bundle should contain Observations with codes "A" and "B"
    And the Bundle should not contain Observations with code "X"

  # :of-type modifier (Identifier type)
  @of-type
  Scenario: Search identifier with :of-type matches by type
    Given Patient "ot-001" has identifier type "MR" with value "12345"
    And Patient "ot-002" has identifier type "SS" with value "12345"
    When I search for Patients with parameter "identifier:of-type" = "http://terminology.hl7.org/CodeSystem/v2-0203|MR|12345"
    Then the Bundle should contain 1 entry
    And the entry should be Patient "ot-001"

  # :identifier modifier (Reference by identifier)
  @identifier
  Scenario: Search reference with :identifier matches Reference.identifier
    Given Patient "ref-id-001" has generalPractitioner with identifier "NPI|1234567890"
    When I search for Patients with parameter "general-practitioner:identifier" = "NPI|1234567890"
    Then the Bundle should contain Patient "ref-id-001"

  Scenario: Search reference with :identifier without system
    Given Patient "ref-id-002" has organization with identifier value "ORG-001"
    When I search for Patients with parameter "organization:identifier" = "ORG-001"
    Then the Bundle should contain Patient "ref-id-002"

  # :[type] modifier (Reference type restriction)
  @type-modifier
  Scenario: Search reference with type modifier restricts target type
    Given Patient "type-001" has generalPractitioner referencing Practitioner "prac-001"
    And Patient "type-002" has generalPractitioner referencing Organization "org-001"
    When I search for Patients with parameter "general-practitioner:Practitioner" = "prac-001"
    Then the Bundle should contain 1 entry
    And the entry should be Patient "type-001"

  Scenario: Search reference with type modifier using ID only
    Given Observation "obs-001" has subject referencing Patient "pat-001"
    When I search for Observations with parameter "subject:Patient" = "pat-001"
    Then the Bundle should contain Observation "obs-001"

  # :code-text modifier
  @code-text
  Scenario: Search with :code-text matches code display starting with value
    Given Observations with code displays "Systolic blood pressure", "Diastolic blood pressure"
    When I search for Observations with parameter "code:code-text" = "Systolic"
    Then the Bundle should contain 1 entry
    And the entry should have code display starting with "Systolic"

  # Modifier validation
  @validation
  Scenario: Invalid modifier returns error
    When I search for Patients with parameter "family:invalid" = "Smith"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid modifier

  Scenario: Modifier not applicable to parameter type returns error
    When I search for Patients with parameter "birthdate:exact" = "1980-01-15"
    Then the response status code should be 400
    And the OperationOutcome should indicate modifier not applicable

  # SQL Injection prevention
  @security @sql-injection
  Scenario: Modifier value with SQL injection attempt is safely handled
    When I search for Patients with parameter "family" = "Smith'; DROP TABLE patients;--"
    Then the response status code should be 200
    And the Bundle should contain 0 entries
    And no database error should occur

  Scenario: Parameter name with SQL injection attempt is rejected
    When I search for Patients with parameter "family; DROP TABLE" = "Smith"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid parameter name
```

#### 3c. Search Prefixes (Comparators)

**File: `features/search/search-prefixes.feature`**
```gherkin
@search @prefixes
Feature: FHIR Search Prefixes (Comparators)
  As a healthcare application
  I want to use search prefixes for comparison operations
  So that I can search for resources with values in specific ranges

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  # Date prefixes
  @date-prefix
  Scenario Outline: Search dates with prefix <prefix>
    Given Patients with birthDates "1980-01-15", "1990-06-20", "2000-12-25"
    When I search for Patients with parameter "birthdate" = "<prefix>1990-06-20"
    Then the Bundle should contain <count> entries
    And <assertion>

    Examples:
      | prefix | count | assertion                                          |
      | eq     | 1     | the entry should have birthDate "1990-06-20"       |
      | ne     | 2     | no entry should have birthDate "1990-06-20"        |
      | gt     | 1     | all entries should have birthDate after 1990-06-20 |
      | lt     | 1     | all entries should have birthDate before 1990-06-20|
      | ge     | 2     | all entries should have birthDate on or after 1990-06-20 |
      | le     | 2     | all entries should have birthDate on or before 1990-06-20 |

  @date-prefix @smoke
  Scenario: Search with eq prefix (default) matches date range
    Given Patient with birthDate "1990-06-20"
    When I search for Patients with parameter "birthdate" = "1990-06-20"
    Then the Bundle should contain 1 entry
    And the entry birthDate should be within 1990-06-20

  Scenario: Search with eq prefix on partial date matches entire period
    Given Patients with birthDates "1990-01-15", "1990-06-20", "1990-12-25"
    When I search for Patients with parameter "birthdate" = "eq1990"
    Then the Bundle should contain 3 entries
    And all entries should have birthDate in year 1990

  Scenario: Search with sa (starts after) prefix
    Given Observations with effectiveDateTime "2024-01-01", "2024-06-15", "2024-12-31"
    When I search for Observations with parameter "date" = "sa2024-06-15"
    Then the Bundle should contain 1 entry
    And the entry should have effectiveDateTime after "2024-06-15"

  Scenario: Search with eb (ends before) prefix
    Given Observations with effectiveDateTime "2024-01-01", "2024-06-15", "2024-12-31"
    When I search for Observations with parameter "date" = "eb2024-06-15"
    Then the Bundle should contain 1 entry
    And the entry should have effectiveDateTime before "2024-06-15"

  Scenario: Search with ap (approximately) prefix on date
    Given Observations with effectiveDateTime "2024-06-01", "2024-06-15", "2024-07-01"
    When I search for Observations with parameter "date" = "ap2024-06-15"
    Then the Bundle should contain Observations with dates approximately "2024-06-15"

  # Number prefixes
  @number-prefix
  Scenario Outline: Search numbers with prefix <prefix>
    Given Observations with valueQuantity values 50, 100, 150
    When I search for Observations with parameter "value-quantity" = "<prefix>100"
    Then the Bundle should contain <count> entries

    Examples:
      | prefix | count |
      | eq     | 1     |
      | ne     | 2     |
      | gt     | 1     |
      | lt     | 1     |
      | ge     | 2     |
      | le     | 2     |

  @number-prefix @smoke
  Scenario: Search with eq prefix considers precision
    Given Observations with valueQuantity values 99.5, 100.0, 100.4
    When I search for Observations with parameter "value-quantity" = "eq100"
    Then the Bundle should contain 3 entries
    And all values should be within implicit range of 100 (99.5-100.5)

  Scenario: Search with ap (approximately) prefix on number
    Given Observations with valueQuantity values 90, 100, 110, 150
    When I search for Observations with parameter "value-quantity" = "ap100"
    Then the Bundle should contain Observations with values within 10% of 100
    And the Bundle should contain values 90, 100, 110
    And the Bundle should not contain value 150

  Scenario: Search number with high precision
    Given Observations with valueQuantity values 99.95, 100.00, 100.05
    When I search for Observations with parameter "value-quantity" = "eq100.00"
    Then the Bundle should contain Observations within range 99.995-100.005

  # Quantity prefixes
  @quantity-prefix
  Scenario: Search quantity with unit consideration
    Given Observations with values "100 mg", "0.1 g", "100000 mcg"
    When I search for Observations with parameter "value-quantity" = "eq100|http://unitsofmeasure.org|mg"
    Then the Bundle should contain 1 entry with "100 mg"

  Scenario: Search quantity without unit matches any unit
    Given Observations with values "100 mg", "100 kg", "100 mL"
    When I search for Observations with parameter "value-quantity" = "eq100"
    Then the Bundle should contain 3 entries

  Scenario: Search quantity with gt prefix
    Given Observations with values "50 mg", "100 mg", "150 mg"
    When I search for Observations with parameter "value-quantity" = "gt100|http://unitsofmeasure.org|mg"
    Then the Bundle should contain 1 entry with "150 mg"

  # Combined prefix usage
  @combined-prefixes
  Scenario: Search with multiple prefixed values (OR logic)
    Given Patients with birthDates "1970-01-01", "1985-06-15", "2000-12-31"
    When I search for Patients with parameter "birthdate" = "lt1980,gt1990"
    Then the Bundle should contain 2 entries
    And one entry should have birthDate before 1980
    And one entry should have birthDate after 1990

  Scenario: Search with multiple parameters using prefixes (AND logic)
    Given Observations with dates and values
    When I search for Observations with parameters:
      | parameter      | value              |
      | date           | ge2024-01-01       |
      | date           | le2024-12-31       |
      | value-quantity | gt50               |
    Then the Bundle should contain Observations matching all criteria

  # Date range searches
  @date-range
  Scenario: Search for date range using ge and le
    Given Patients with birthDates spanning 1970 to 2020
    When I search for Patients with parameters:
      | parameter | value           |
      | birthdate | ge1980-01-01    |
      | birthdate | le1989-12-31    |
    Then the Bundle should contain Patients born in the 1980s

  Scenario: Search date with timezone handling
    Given Observation with effectiveDateTime "2024-06-15T10:30:00+05:00"
    When I search for Observations with parameter "date" = "eq2024-06-15T05:30:00Z"
    Then the Bundle should contain the Observation (same instant)

  # Edge cases
  @edge-cases
  Scenario: Search with no prefix defaults to eq
    Given Patients with birthDates "1990-06-20"
    When I search for Patients with parameter "birthdate" = "1990-06-20"
    Then the search should behave as "birthdate=eq1990-06-20"

  Scenario: Prefix only applies to immediately following value
    Given Observations with values 50, 100, 150
    When I search for Observations with parameter "value-quantity" = "lt100,150"
    Then the Bundle should contain values less than 100 OR equal to 150

  # Validation
  @validation
  Scenario: Invalid prefix on non-ordered type returns error
    When I search for Patients with parameter "family" = "gt1990"
    Then the response status code should be 400
    And the OperationOutcome should indicate prefix not applicable to string type

  Scenario: Invalid date format with prefix returns error
    When I search for Patients with parameter "birthdate" = "gtinvalid-date"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid date format

  # SQL Injection prevention with prefixes
  @security @sql-injection
  Scenario: Prefix value with SQL injection attempt is safely handled
    When I search for Patients with parameter "birthdate" = "gt1990'; DROP TABLE patients;--"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid date format
    And no database error should occur

  Scenario: Numeric value with SQL injection attempt is safely handled
    When I search for Observations with parameter "value-quantity" = "gt100; DELETE FROM observations"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid number format
```

#### 3d. Advanced Search Features

**File: `features/search/search-advanced.feature`**
```gherkin
@search @advanced
Feature: Advanced FHIR Search Features
  As a healthcare application
  I want to use advanced search capabilities
  So that I can perform complex queries

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  # _filter parameter
  @_filter
  Scenario: Search using _filter with simple expression
    Given Patients exist with various attributes
    When I search for Patients with parameter "_filter" = "family co \"smith\""
    Then the Bundle should contain Patients with family name containing "smith"

  Scenario: Search using _filter with AND logic
    When I search for Patients with parameter "_filter" = "family co \"smith\" and gender eq \"male\""
    Then all entries should have family containing "smith" AND gender "male"

  Scenario: Search using _filter with OR logic
    When I search for Patients with parameter "_filter" = "gender eq \"male\" or gender eq \"female\""
    Then the Bundle should contain Patients with either gender

  Scenario: Search using _filter with NOT logic
    When I search for Patients with parameter "_filter" = "not (gender eq \"male\")"
    Then no entries should have gender "male"

  Scenario: Search using _filter with nested expressions
    When I search for Patients with parameter "_filter" = "(family co \"smith\" or family co \"jones\") and active eq true"
    Then all entries should match the nested filter conditions

  Scenario: Search using _filter with date comparisons
    When I search for Patients with parameter "_filter" = "birthdate ge 1990-01-01 and birthdate lt 2000-01-01"
    Then all entries should have birthDate in the 1990s

  # Chained searches
  @chaining
  Scenario: Search with chained parameter
    Given Patient "chain-001" has organization "General Hospital"
    When I search for Patients with parameter "organization.name" = "General Hospital"
    Then the Bundle should contain Patient "chain-001"

  Scenario: Search with multiple level chaining
    Given Observation referencing Patient with organization "Mayo Clinic"
    When I search for Observations with parameter "patient.organization.name" = "Mayo Clinic"
    Then the Bundle should contain the matching Observation

  # Composite search parameters
  @composite
  Scenario: Search with composite parameter
    Given Observations with code-value pairs
    When I search for Observations with parameter "code-value-quantity" = "http://loinc.org|8480-6$gt100"
    Then the Bundle should contain Observations matching both code AND value

  # Security - SQL Injection Prevention
  @security @sql-injection
  Scenario: _filter with SQL injection attempt is safely handled
    When I search for Patients with parameter "_filter" = "family eq \"Smith'; DROP TABLE patients;--\""
    Then the response status code should be 200
    And the Bundle should contain 0 entries
    And no database error should occur

  Scenario: Chained parameter with SQL injection attempt is safely handled
    When I search for Patients with parameter "organization.name" = "Hospital'; DELETE FROM organizations;--"
    Then the response status code should be 200
    And the Bundle should contain 0 entries
    And no database error should occur

  Scenario: Resource ID with SQL injection attempt is rejected
    When I search for Patients with parameter "_id" = "123; DROP TABLE patients"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid ID format

  Scenario: Sort parameter with SQL injection attempt is rejected
    When I search for Patients with parameter "_sort" = "family; DROP TABLE patients"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid sort parameter

  Scenario: _count with SQL injection attempt is safely handled
    When I search for Patients with parameter "_count" = "10; DROP TABLE patients"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid count value

  Scenario: _elements with SQL injection attempt is safely handled
    When I search for Patients with parameter "_elements" = "name; DROP TABLE patients"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid elements parameter

  # Input validation
  @validation
  Scenario: Parameter name validation rejects special characters
    When I search for Patients with parameter "family<script>" = "Smith"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid parameter name

  Scenario: Resource type validation prevents injection
    When I search for resource type "Patient; DROP TABLE users" with parameter "name" = "Smith"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid resource type

  Scenario: Maximum query length is enforced
    When I search with a query string longer than 10000 characters
    Then the response status code should be 400
    And the OperationOutcome should indicate query too long

  Scenario: Maximum parameter count is enforced
    When I search with more than 100 parameters
    Then the response status code should be 400
    And the OperationOutcome should indicate too many parameters
```

#### 4. Search Pagination and Sorting

**File: `features/search/search-pagination.feature`**
```gherkin
@search @pagination
Feature: Search Pagination and Sorting
  As a healthcare application
  I want to paginate and sort search results
  So that I can efficiently navigate large result sets

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And 50 Patient resources exist in the system

  @_count @smoke
  Scenario: Limit search results with _count
    When I search for Patients with parameter "_count" = "10"
    Then the Bundle should contain exactly 10 entries
    And the Bundle should have a "next" link

  Scenario: Default page size is applied
    When I search for all Patients
    Then the Bundle should contain at most the default page size entries

  @_offset
  Scenario: Paginate with _offset
    When I search for Patients with parameters:
      | parameter | value |
      | _count    | 10    |
      | _offset   | 20    |
    Then the Bundle should contain results starting from position 21

  Scenario: Navigate using Bundle links
    Given I search for Patients with parameter "_count" = "10"
    When I follow the "next" link in the Bundle
    Then I should receive the next page of results
    And the Bundle should have a "previous" link

  @_sort @smoke
  Scenario: Sort results by family name ascending
    When I search for Patients with parameter "_sort" = "family"
    Then the results should be sorted by family name in ascending order

  Scenario: Sort results by family name descending
    When I search for Patients with parameter "_sort" = "-family"
    Then the results should be sorted by family name in descending order

  Scenario: Sort by multiple fields
    When I search for Patients with parameter "_sort" = "family,-birthdate"
    Then the results should be sorted by family ascending, then birthdate descending

  Scenario: Sort by _lastUpdated
    When I search for Patients with parameter "_sort" = "-_lastUpdated"
    Then the results should be sorted by most recently updated first

  @_total
  Scenario: Request total count with _total=accurate
    When I search for Patients with parameter "_total" = "accurate"
    Then the Bundle should have a "total" element with accurate count

  Scenario: Request estimated count with _total=estimate
    When I search for Patients with parameter "_total" = "estimate"
    Then the Bundle should have a "total" element

  Scenario: Disable total count with _total=none
    When I search for Patients with parameter "_total" = "none"
    Then the Bundle should not have a "total" element
```

#### 5. Search Includes

**File: `features/search/search-includes.feature`**
```gherkin
@search @include
Feature: Search Include and RevInclude
  As a healthcare application
  I want to include related resources in search results
  So that I can reduce the number of API calls

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @_include @smoke
  Scenario: Include referenced Organization in Patient search
    Given a Patient exists with a managingOrganization reference
    When I search for Patients with parameter "_include" = "Patient:organization"
    Then the Bundle should contain the Patient
    And the Bundle should contain the referenced Organization
    And the Organization entry should have search mode "include"

  Scenario: Include multiple reference types
    Given a Patient exists with organization and general-practitioner references
    When I search for Patients with parameters:
      | parameter | value                          |
      | _include  | Patient:organization           |
      | _include  | Patient:general-practitioner   |
    Then the Bundle should contain all referenced resources

  Scenario: Recursive include with _include:iterate
    Given an Organization with a partOf reference to another Organization
    When I search for Organizations with parameter "_include:iterate" = "Organization:partof"
    Then the Bundle should contain the entire Organization hierarchy

  @_revinclude
  Scenario: Reverse include Observations for Patient
    Given a Patient exists with related Observations
    When I search for Patients with parameter "_revinclude" = "Observation:patient"
    Then the Bundle should contain the Patient
    And the Bundle should contain the related Observations
    And the Observation entries should have search mode "include"

  Scenario: Include with specific target type
    Given a Patient with generalPractitioner referencing both Practitioner and Organization
    When I search for Patients with parameter "_include" = "Patient:general-practitioner:Practitioner"
    Then the Bundle should only include Practitioner resources, not Organizations
```

#### 6. Cache Operations

**File: `features/cache/resource-cache.feature`**
```gherkin
@cache @resource-cache
Feature: Resource Cache Operations
  As a FHIR server
  I want to cache frequently accessed resources
  So that I can improve read performance and reduce database load

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the cache is cleared

  @l1-cache @smoke
  Scenario: First read populates L1 local cache
    Given a Patient resource exists with id "cache-test-001"
    When I read the Patient "cache-test-001"
    Then the response status code should be 200
    And the resource should be stored in L1 local cache
    And the cache key should be "Patient/cache-test-001"

  Scenario: Second read serves from L1 local cache
    Given a Patient resource exists with id "cache-test-002"
    And I have read the Patient "cache-test-002" once
    When I read the Patient "cache-test-002" again
    Then the response should be served from L1 cache
    And the response time should be less than 5 milliseconds
    And no database query should be executed

  @l2-cache @smoke
  Scenario: L1 cache miss falls back to L2 Redis cache
    Given a Patient resource exists with id "cache-test-003"
    And the resource is in L2 Redis cache but not L1
    When I read the Patient "cache-test-003"
    Then the response should be served from L2 Redis cache
    And the resource should be populated in L1 cache
    And the response time should be less than 10 milliseconds

  Scenario: Cache miss queries database and populates both caches
    Given a Patient resource exists with id "cache-test-004"
    And the resource is not in any cache
    When I read the Patient "cache-test-004"
    Then a database query should be executed
    And the resource should be stored in L1 local cache
    And the resource should be stored in L2 Redis cache

  @cache-headers
  Scenario: Response includes cache-related headers
    Given a Patient resource exists with id "cache-test-005"
    When I read the Patient "cache-test-005"
    Then the response should have "ETag" header
    And the response should have "Last-Modified" header
    And the response should have "Cache-Control" header

  @conditional-read
  Scenario: Conditional read with If-None-Match returns 304
    Given a Patient resource exists with id "cache-test-006"
    And I have the ETag from a previous read
    When I read the Patient "cache-test-006" with If-None-Match header
    Then the response status code should be 304
    And the response body should be empty

  Scenario: Conditional read with changed resource returns 200
    Given a Patient resource exists with id "cache-test-007"
    And I have an outdated ETag
    When I read the Patient "cache-test-007" with If-None-Match header
    Then the response status code should be 200
    And the response should contain the updated resource

  @cache-ttl
  Scenario: L1 cache expires after configured TTL
    Given a Patient resource exists with id "cache-test-008"
    And L1 cache TTL is configured to 60 seconds
    And the resource has been cached for 61 seconds
    When I read the Patient "cache-test-008"
    Then the L1 cache should have expired
    And the resource should be fetched from L2 or database

  Scenario: L2 Redis cache expires after configured TTL
    Given a Patient resource exists with id "cache-test-009"
    And L2 Redis cache TTL is configured to 300 seconds
    And the resource has been in Redis cache for 301 seconds
    When I read the Patient "cache-test-009"
    Then the L2 cache should have expired
    And a database query should be executed

  @vread-cache
  Scenario: Version read (vread) is cached separately
    Given a Patient resource exists with id "cache-test-010" and version "2"
    When I read version 1 of the Patient "cache-test-010"
    And I read version 2 of the Patient "cache-test-010"
    Then both versions should be cached separately
    And cache key for version 1 should be "Patient/cache-test-010/_history/1"
    And cache key for version 2 should be "Patient/cache-test-010/_history/2"
```

**File: `features/cache/search-cache.feature`**
```gherkin
@cache @search-cache
Feature: Search Result Cache Operations
  As a FHIR server
  I want to cache search results for common queries
  So that I can improve search performance for repeated queries

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the cache is cleared
    And the following Patients exist:
      | id         | family    | given   | gender |
      | search-p1  | Smith     | John    | male   |
      | search-p2  | Smith     | Jane    | female |
      | search-p3  | Johnson   | Robert  | male   |

  @smoke
  Scenario: Search results are cached
    When I search for Patients with parameter "family" = "Smith"
    Then the response status code should be 200
    And the search result should be cached
    And the cache key should include "search:Patient?family=Smith"

  Scenario: Repeated search serves from cache
    Given I have searched for Patients with parameter "family" = "Smith"
    When I search for Patients with parameter "family" = "Smith" again
    Then the response should be served from search cache
    And no database query should be executed

  Scenario: Search cache key is deterministic regardless of parameter order
    When I search for Patients with parameters:
      | parameter | value  |
      | family    | Smith  |
      | gender    | male   |
    And I search for Patients with parameters:
      | parameter | value  |
      | gender    | male   |
      | family    | Smith  |
    Then both searches should use the same cache key
    And the second search should be served from cache

  @cache-size-limit
  Scenario: Large search results are not cached
    Given 200 Patient resources exist in the system
    When I search for all Patients without pagination
    Then the search result should NOT be cached
    And a warning should be logged about result size

  Scenario: Search results within size limit are cached
    When I search for Patients with parameter "_count" = "50"
    Then the search result should be cached

  @search-cache-ttl
  Scenario: Search cache expires after configured TTL
    Given search cache TTL is configured to 300 seconds
    And I have cached a search for "family=Smith" 301 seconds ago
    When I search for Patients with parameter "family" = "Smith"
    Then the search cache should have expired
    And a fresh database query should be executed
    And the new result should be cached

  @cache-miss
  Scenario: Different search parameters result in cache miss
    Given I have searched for Patients with parameter "family" = "Smith"
    When I search for Patients with parameter "family" = "Johnson"
    Then the search should NOT be served from cache
    And a database query should be executed

  @pagination-cache
  Scenario: Each page of search results is cached separately
    Given 50 Patient resources exist
    When I search for Patients with "_count=10&_offset=0"
    And I search for Patients with "_count=10&_offset=10"
    Then each page should be cached with its own key
    And page 1 cache key should include "_offset=0"
    And page 2 cache key should include "_offset=10"
```

**File: `features/cache/cache-invalidation.feature`**
```gherkin
@cache @cache-invalidation
Feature: Cache Invalidation
  As a FHIR server
  I want to invalidate cached resources when they are modified
  So that clients always receive current data

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the cache is cleared

  @update-invalidation @smoke
  Scenario: Update invalidates resource cache
    Given a Patient resource exists with id "inv-test-001"
    And the Patient "inv-test-001" is cached in L1 and L2
    When I update the Patient "inv-test-001"
    Then the response status code should be 200
    And the L1 cache for "Patient/inv-test-001" should be invalidated
    And the L2 Redis cache for "Patient/inv-test-001" should be invalidated

  Scenario: Update populates cache with new version
    Given a Patient resource exists with id "inv-test-002"
    And the Patient "inv-test-002" is cached
    When I update the Patient "inv-test-002" with new family name "NewName"
    Then the cache should contain the updated resource
    And subsequent reads should return the updated resource

  @delete-invalidation @smoke
  Scenario: Delete invalidates resource cache
    Given a Patient resource exists with id "inv-test-003"
    And the Patient "inv-test-003" is cached in L1 and L2
    When I delete the Patient "inv-test-003"
    Then the response status code should be 204
    And the L1 cache for "Patient/inv-test-003" should be invalidated
    And the L2 Redis cache for "Patient/inv-test-003" should be invalidated

  @patch-invalidation
  Scenario: Patch invalidates resource cache
    Given a Patient resource exists with id "inv-test-004"
    And the Patient "inv-test-004" is cached
    When I patch the Patient "inv-test-004" to change active status
    Then the response status code should be 200
    And the cache should be invalidated
    And the cache should be repopulated with patched resource

  @search-cache-invalidation @smoke
  Scenario: Create invalidates related search caches
    Given the following Patients exist:
      | id        | family | given |
      | inv-p1    | Smith  | John  |
      | inv-p2    | Smith  | Jane  |
    And I have cached a search for "family=Smith" returning 2 results
    When I create a new Patient with family name "Smith"
    Then the search cache for "family=Smith" should be invalidated
    And subsequent search should return 3 results

  Scenario: Update invalidates related search caches
    Given a Patient "inv-test-005" exists with family "Jones"
    And I have cached a search for "family=Jones"
    And I have cached a search for "family=Smith"
    When I update Patient "inv-test-005" to have family "Smith"
    Then the search cache for "family=Jones" should be invalidated
    And the search cache for "family=Smith" should be invalidated

  Scenario: Delete invalidates related search caches
    Given a Patient "inv-test-006" exists with family "Williams"
    And I have cached a search for "family=Williams"
    When I delete Patient "inv-test-006"
    Then the search cache for "family=Williams" should be invalidated

  @cascade-invalidation
  Scenario: Invalidation cascades to related resource caches
    Given a Patient "inv-test-007" exists
    And Observations exist referencing Patient "inv-test-007"
    And I have cached searches including Patient "inv-test-007"
    When I update Patient "inv-test-007"
    Then all related search caches should be invalidated

  @bulk-invalidation
  Scenario: Bulk update invalidates multiple cache entries
    Given Patients "bulk-1", "bulk-2", "bulk-3" exist and are cached
    When I perform a bulk update on all three Patients
    Then all three Patient caches should be invalidated
    And all three should be repopulated with updated data

  @concurrent-invalidation
  Scenario: Concurrent updates properly invalidate cache
    Given a Patient "inv-test-008" exists and is cached
    When two concurrent updates are made to Patient "inv-test-008"
    Then the cache should reflect the final state
    And no stale data should be served

  @cache-stampede-prevention
  Scenario: Cache stampede prevention on invalidation
    Given a frequently accessed Patient "inv-test-009" is cached
    And 100 concurrent read requests are in flight
    When the cache is invalidated
    Then only one database query should be executed
    And all requests should receive the refreshed data

  @cross-node-invalidation
  Scenario: Cache invalidation propagates across cluster nodes
    Given the FHIR server runs in a 3-node cluster
    And a Patient "inv-test-010" is cached on all nodes
    When node 1 updates Patient "inv-test-010"
    Then the cache on node 2 should be invalidated
    And the cache on node 3 should be invalidated
    And all nodes should serve the updated resource
```

#### 7. History Operations

**File: `features/patient/patient-history.feature`**
```gherkin
@patient @history
Feature: Patient History Operations
  As a healthcare application
  I want to retrieve the history of Patient resources
  So that I can track changes over time

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @instance-history @smoke
  Scenario: Get history of a specific Patient
    Given a Patient resource with id "patient-hist-001" has been updated 3 times
    When I send a GET request to "/fhir/r5/Patient/patient-hist-001/_history"
    Then the response status code should be 200
    And the response should be a Bundle of type "history"
    And the Bundle should contain 4 entries
    And entries should be ordered by version descending

  Scenario: Get specific version from history
    Given a Patient with id "patient-hist-002" has version 2
    When I send a GET request to "/fhir/r5/Patient/patient-hist-002/_history/2"
    Then the response status code should be 200
    And the resource version should be "2"

  Scenario: History includes deleted resources
    Given a Patient "patient-hist-003" was created then deleted
    When I send a GET request to "/fhir/r5/Patient/patient-hist-003/_history"
    Then the Bundle should contain a deleted entry
    And the deleted entry should have request method "DELETE"

  @type-history
  Scenario: Get history of all Patients
    When I send a GET request to "/fhir/r5/Patient/_history"
    Then the response status code should be 200
    And the response should be a Bundle of type "history"
    And the Bundle should contain Patient history entries

  Scenario: Filter history by _since parameter
    Given Patients were modified at various times
    When I send a GET request to "/fhir/r5/Patient/_history?_since=2024-01-01T00:00:00Z"
    Then all entries should have lastUpdated after "2024-01-01T00:00:00Z"

  Scenario: Filter history by _at parameter
    When I send a GET request to "/fhir/r5/Patient/_history?_at=2024-01-15T12:00:00Z"
    Then the Bundle should contain resource states at that point in time

  Scenario: Paginate history results
    Given a Patient has more than 10 versions
    When I send a GET request to "/fhir/r5/Patient/{id}/_history?_count=5"
    Then the Bundle should contain 5 entries
    And the Bundle should have pagination links

  @system-history
  Scenario: Get system-level history
    When I send a GET request to "/fhir/r5/_history"
    Then the response status code should be 200
    And the Bundle should contain history entries for all resource types
```

#### 8. Validation Feature

**File: `features/validation/resource-validation.feature`**
```gherkin
@validation
Feature: Resource Validation
  As a healthcare application
  I want resources to be validated before persistence
  So that data integrity is maintained

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @structure @smoke
  Scenario: Reject resource with invalid structure
    Given I have a Patient resource missing required elements
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 400
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have severity "error"

  Scenario: Reject resource with invalid data type
    Given I have a Patient resource with birthDate "not-a-date"
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid date format

  Scenario: Reject resource with invalid code value
    Given I have a Patient resource with gender "invalid-gender"
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 400
    And the OperationOutcome should indicate invalid code

  @profile
  Scenario: Validate against required profile
    Given the server requires US Core Patient profile for Patient resources
    And I have a Patient resource not conforming to US Core
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 400
    And the OperationOutcome should indicate profile violation

  Scenario: Accept resource conforming to required profile
    Given the server requires US Core Patient profile for Patient resources
    And I have a Patient resource conforming to US Core
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201

  @validate-operation
  Scenario: Validate resource without persisting using $validate
    Given I have a Patient resource to validate
    When I send a POST request to "/fhir/r5/Patient/$validate" with the resource
    Then the response status code should be 200
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should indicate validation result

  Scenario: Validate resource against specific profile
    Given I have a Patient resource
    When I send a POST request to "/fhir/r5/Patient/$validate?profile=http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
    Then the OperationOutcome should indicate conformance to US Core profile
```

#### 9. Authentication and Authorization

**File: `features/security/authentication.feature`**
```gherkin
@security @authentication
Feature: Authentication
  As a healthcare application
  I want the FHIR server to enforce authentication
  So that only authenticated clients can access resources

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"

  @smoke
  Scenario: Reject request without authentication
    Given I am not authenticated
    When I send a GET request to "/fhir/r5/Patient"
    Then the response status code should be 401
    And the response should contain WWW-Authenticate header

  Scenario: Reject request with invalid token
    Given I have an invalid JWT token
    When I send a GET request to "/fhir/r5/Patient" with the token
    Then the response status code should be 401
    And the OperationOutcome should indicate authentication failure

  Scenario: Reject request with expired token
    Given I have an expired JWT token
    When I send a GET request to "/fhir/r5/Patient" with the token
    Then the response status code should be 401
    And the OperationOutcome should indicate token expired

  @smoke
  Scenario: Accept request with valid token
    Given I have a valid JWT token
    When I send a GET request to "/fhir/r5/Patient" with the token
    Then the response status code should not be 401

  Scenario: Metadata endpoint is accessible without authentication
    Given I am not authenticated
    When I send a GET request to "/fhir/r5/metadata"
    Then the response status code should be 200
```

**File: `features/security/authorization.feature`**
```gherkin
@security @authorization
Feature: Authorization
  As a healthcare application
  I want the FHIR server to enforce authorization
  So that clients can only access resources they are permitted to

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"

  @scope @smoke
  Scenario: Reject read without patient read scope
    Given I am authenticated without "patient/*.read" scope
    When I send a GET request to "/fhir/r5/Patient/123"
    Then the response status code should be 403
    And the OperationOutcome should indicate insufficient scope

  Scenario: Allow read with patient read scope
    Given I am authenticated with "patient/Patient.read" scope
    And a Patient resource exists
    When I send a GET request to "/fhir/r5/Patient/{id}"
    Then the response status code should be 200

  Scenario: Reject write without write scope
    Given I am authenticated with only "patient/Patient.read" scope
    And I have a valid Patient resource
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 403

  Scenario: Allow write with write scope
    Given I am authenticated with "patient/Patient.write" scope
    And I have a valid Patient resource
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201

  @compartment
  Scenario: Enforce patient compartment access
    Given I am authenticated with access to patient "patient-123" only
    When I send a GET request to "/fhir/r5/Patient/patient-456"
    Then the response status code should be 403

  Scenario: Allow access within authorized compartment
    Given I am authenticated with access to patient "patient-123" only
    When I send a GET request to "/fhir/r5/Patient/patient-123"
    Then the response status code should be 200

  @search
  Scenario: Search filters results to authorized resources
    Given I am authenticated with access to patient "patient-123" only
    And multiple Patients exist in the system
    When I search for all Patients
    Then the Bundle should only contain resources I am authorized to access
```

#### 10. Capability Statement

**File: `features/metadata/capability-statement.feature`**
```gherkin
@metadata @capability
Feature: Capability Statement
  As a healthcare application
  I want to retrieve the server's CapabilityStatement
  So that I can discover supported features and resources

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"

  @smoke
  Scenario: Retrieve CapabilityStatement via GET metadata
    When I send a GET request to "/fhir/r5/metadata"
    Then the response status code should be 200
    And the response should contain a "CapabilityStatement" resource
    And the CapabilityStatement fhirVersion should be "5.0.0"
    And the CapabilityStatement status should be "active"
    And the CapabilityStatement kind should be "instance"

  Scenario: Retrieve CapabilityStatement via OPTIONS
    When I send an OPTIONS request to "/fhir/r5"
    Then the response status code should be 200
    And the response should contain a "CapabilityStatement" resource

  @resources
  Scenario: CapabilityStatement lists all supported resources
    When I retrieve the CapabilityStatement
    Then the CapabilityStatement should list resource "Patient"
    And the CapabilityStatement should list resource "Observation"
    And each resource should list supported interactions

  @interactions
  Scenario: CapabilityStatement shows enabled interactions
    Given Patient resource has read, create, update, delete enabled
    When I retrieve the CapabilityStatement
    Then the Patient resource should list interaction "read"
    And the Patient resource should list interaction "create"
    And the Patient resource should list interaction "update"
    And the Patient resource should list interaction "delete"
    And the Patient resource should list interaction "search-type"

  @search-params
  Scenario: CapabilityStatement lists search parameters
    When I retrieve the CapabilityStatement
    Then the Patient resource should list search parameter "family"
    And the Patient resource should list search parameter "given"
    And the Patient resource should list search parameter "birthdate"
    And the Patient resource should list search parameter "_id"
    And the Patient resource should list search parameter "_lastUpdated"

  @operations
  Scenario: CapabilityStatement lists extended operations
    Given $validate operation is enabled for Patient
    When I retrieve the CapabilityStatement
    Then the Patient resource should list operation "$validate"

  @formats
  Scenario: CapabilityStatement indicates supported formats
    When I retrieve the CapabilityStatement
    Then the CapabilityStatement should list format "application/fhir+json"
    And the CapabilityStatement should list format "application/fhir+xml"
```

---

### Extended Operations Feature Files

#### 1. Patient $merge Operation

**File: `features/operations/patient-merge.feature`**
```gherkin
@operations @merge @patient
Feature: Patient $merge Extended Operation
  As a healthcare administrator
  I want to merge duplicate Patient records
  So that I can maintain clean patient data

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the $merge operation is enabled for Patient

  @smoke
  Scenario: Successfully merge two Patient records (type-level)
    Given a source Patient exists with id "patient-source-1"
    And a target Patient exists with id "patient-target-1"
    When I invoke the $merge operation on Patient type with parameters:
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "source-patient",
            "valueReference": { "reference": "Patient/patient-source-1" }
          },
          {
            "name": "target-patient",
            "valueReference": { "reference": "Patient/patient-target-1" }
          }
        ]
      }
      """
    Then the response status code should be 200
    And the response should contain a "Parameters" resource
    And the output should contain parameter "return" with a Patient reference
    And the source Patient should be marked as inactive
    And the source Patient should have a link to the target Patient

  Scenario: Successfully merge two Patient records (instance-level)
    Given a source Patient exists with id "patient-source-2"
    And a target Patient exists with id "patient-target-2"
    When I invoke the $merge operation on Patient instance "patient-target-2" with parameters:
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "source-patient",
            "valueReference": { "reference": "Patient/patient-source-2" }
          }
        ]
      }
      """
    Then the response status code should be 200
    And the source Patient should be merged into target

  @error-handling
  Scenario: Merge fails when source patient does not exist
    Given a target Patient exists with id "patient-target-3"
    When I invoke the $merge operation with non-existent source patient "non-existent-source"
    Then the response status code should be 404
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have severity "error"
    And the OperationOutcome should have code "not-found"
    And the OperationOutcome message should contain "Source patient not found"

  @error-handling
  Scenario: Merge fails when target patient does not exist
    Given a source Patient exists with id "patient-source-4"
    When I invoke the $merge operation with non-existent target patient "non-existent-target"
    Then the response status code should be 404
    And the OperationOutcome message should contain "Target patient not found"

  @validation
  Scenario: Merge fails when merging patient with themselves
    Given a Patient exists with id "patient-self"
    When I invoke the $merge operation with source and target both "patient-self"
    Then the response status code should be 400
    And the OperationOutcome should have code "invalid"
    And the OperationOutcome message should contain "Cannot merge patient with themselves"

  @validation
  Scenario: Merge fails when source patient is inactive
    Given an inactive source Patient exists with id "inactive-source"
    And a target Patient exists with id "patient-target-5"
    When I invoke the $merge operation on Patient type with parameters:
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          { "name": "source-patient", "valueReference": { "reference": "Patient/inactive-source" } },
          { "name": "target-patient", "valueReference": { "reference": "Patient/patient-target-5" } }
        ]
      }
      """
    Then the response status code should be 422
    And the OperationOutcome message should contain "Source patient is not active"

  @validation
  Scenario: Merge fails with missing required parameters
    When I invoke the $merge operation on Patient type with empty parameters
    Then the response status code should be 400
    And the OperationOutcome should have code "required"
    And the OperationOutcome message should contain "source-patient"

  @authorization
  Scenario: Merge requires appropriate permissions
    Given I am authenticated as a user without merge permissions
    And a source Patient exists with id "patient-source-6"
    And a target Patient exists with id "patient-target-6"
    When I invoke the $merge operation on Patient type
    Then the response status code should be 403
    And the OperationOutcome should have code "forbidden"
```

#### 2. Resource $validate Operation

**File: `features/operations/resource-validate.feature`**
```gherkin
@operations @validate
Feature: Resource $validate Extended Operation
  As a healthcare developer
  I want to validate FHIR resources against profiles
  So that I can ensure data quality before persisting

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the $validate operation is enabled

  @smoke @patient
  Scenario: Validate a valid Patient resource
    Given I have a valid Patient resource
    When I invoke the $validate operation on Patient type with the resource
    Then the response status code should be 200
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have no errors
    And the OperationOutcome should have severity "information" or "warning" only

  @patient
  Scenario: Validate an invalid Patient resource
    Given I have a Patient resource missing required elements
    When I invoke the $validate operation on Patient type with the resource
    Then the response status code should be 200
    And the OperationOutcome should have severity "error"
    And the OperationOutcome should describe the validation errors

  @profile
  Scenario: Validate Patient against US Core profile
    Given I have a Patient resource
    When I invoke the $validate operation with profile "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
    Then the response status code should be 200
    And the OperationOutcome should indicate profile validation results

  @mode
  Scenario: Validate in create mode
    Given I have a valid Patient resource without an id
    When I invoke the $validate operation with mode "create"
    Then the response status code should be 200
    And the validation should apply create-mode rules

  @mode
  Scenario: Validate in update mode
    Given a Patient resource exists with id "existing-patient"
    And I have an updated Patient resource for "existing-patient"
    When I invoke the $validate operation with mode "update"
    Then the response status code should be 200
    And the validation should apply update-mode rules

  @observation
  Scenario: Validate Observation with reference to existing Patient
    Given a Patient resource exists with id "referenced-patient"
    And I have an Observation referencing "Patient/referenced-patient"
    When I invoke the $validate operation on Observation type with the resource
    Then the response status code should be 200
    And the reference validation should pass

  @instance-level
  Scenario: Validate existing resource instance
    Given a Patient resource exists with id "validate-instance"
    When I send a GET request to "/fhir/r5/Patient/validate-instance/$validate"
    Then the response status code should be 200
    And the response should contain an "OperationOutcome" resource
```

#### 3. Patient $everything Operation

**File: `features/operations/patient-everything.feature`**
```gherkin
@operations @everything @patient
Feature: Patient $everything Extended Operation
  As a healthcare application
  I want to retrieve all data for a patient
  So that I can provide a complete patient summary

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And the $everything operation is enabled for Patient

  @smoke
  Scenario: Retrieve all resources for a patient
    Given a Patient exists with id "everything-patient"
    And the Patient has 3 related Observations
    And the Patient has 2 related Conditions
    And the Patient has 1 related Encounter
    When I invoke the $everything operation on Patient instance "everything-patient"
    Then the response status code should be 200
    And the response should be a Bundle of type "searchset"
    And the Bundle should contain the Patient resource
    And the Bundle should contain 3 Observation resources
    And the Bundle should contain 2 Condition resources
    And the Bundle should contain 1 Encounter resource

  Scenario: $everything with date range filter
    Given a Patient exists with id "dated-patient"
    And the Patient has Observations from various dates
    When I invoke the $everything operation with parameters:
      | parameter | value      |
      | start     | 2024-01-01 |
      | end       | 2024-06-30 |
    Then the Bundle should only contain resources within the date range

  Scenario: $everything with _type filter
    Given a Patient exists with id "typed-patient"
    And the Patient has various related resources
    When I invoke the $everything operation with parameter "_type" = "Observation,Condition"
    Then the Bundle should only contain Patient, Observation, and Condition resources

  Scenario: $everything with pagination
    Given a Patient exists with many related resources
    When I invoke the $everything operation with parameter "_count" = "10"
    Then the Bundle should contain at most 10 entries
    And the Bundle should have pagination links if more resources exist

  @authorization
  Scenario: $everything respects access controls
    Given a Patient exists with id "restricted-patient"
    And the Patient has some resources I am not authorized to see
    When I invoke the $everything operation on Patient instance "restricted-patient"
    Then the Bundle should only contain resources I am authorized to access
```

#### 4. Operation Error Handling

**File: `features/operations/operation-errors.feature`**
```gherkin
@operations @errors
Feature: Extended Operation Error Handling
  As a FHIR client
  I want clear error responses from operations
  So that I can handle failures appropriately

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  Scenario: Invoke undefined operation
    When I invoke an undefined operation "$undefined-op" on Patient type
    Then the response status code should be 404
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome should have code "not-supported"
    And the OperationOutcome message should contain "Operation $undefined-op is not supported"

  Scenario: Invoke operation on wrong resource type
    Given $merge operation is only enabled for Patient
    When I invoke the $merge operation on Observation type
    Then the response status code should be 404
    And the OperationOutcome message should contain "not supported for Observation"

  Scenario: Invoke instance-level operation on type level
    Given $everything is only available at instance level
    When I invoke the $everything operation on Patient type (not instance)
    Then the response status code should be 405
    And the OperationOutcome should have code "not-supported"

  Scenario: Invoke type-level operation on instance level
    Given $bulk-export is only available at type level
    When I invoke the $bulk-export operation on Patient instance "some-patient"
    Then the response status code should be 405

  Scenario: Operation with invalid parameter type
    When I invoke the $merge operation with invalid parameter type:
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "source-patient",
            "valueString": "not-a-reference"
          }
        ]
      }
      """
    Then the response status code should be 400
    And the OperationOutcome should have code "invalid"
    And the OperationOutcome message should contain "Expected Reference"

  Scenario: Operation timeout handling
    Given an operation that takes longer than timeout
    When I invoke the slow operation with a 5 second timeout
    Then the response status code should be 408 or 504
    And the OperationOutcome should indicate timeout
```

---

### Business Logic Plugin Feature Files

#### 1. Before Operation Plugins

**File: `features/plugins/before-operation-plugins.feature`**
```gherkin
@plugins @before-operation
Feature: Before Operation Business Logic Plugins
  As a FHIR server administrator
  I want to execute business logic before operations
  So that I can enforce rules, validate data, and transform inputs

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  # ===== CRUD Operation Plugins =====

  @crud @create
  Scenario: Before-plugin validates Patient consent on create
    Given the PatientConsentPlugin is enabled
    And I have a Patient resource without consent flag
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the OperationOutcome should have severity "error"
    And the OperationOutcome message should contain "Patient consent not provided"

  @crud @create
  Scenario: Before-plugin allows Patient create with valid consent
    Given the PatientConsentPlugin is enabled
    And I have a Patient resource with consent flag set to true
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201
    And the Patient resource should be created successfully

  @crud @update
  Scenario: Before-plugin validates on update
    Given the PatientConsentPlugin is enabled
    And a Patient resource exists with id "consent-update-test"
    And I modify the Patient to remove consent flag
    When I send a PUT request to "/fhir/r5/Patient/consent-update-test"
    Then the response status code should be 422
    And the OperationOutcome message should contain "consent"

  @crud @create
  Scenario: Before-plugin enriches Observation on create
    Given the ObservationEnrichmentPlugin is enabled
    And I have an Observation with a numeric value but no interpretation
    When I send a POST request to "/fhir/r5/Observation"
    Then the response status code should be 201
    And the Observation should have an auto-populated interpretation

  # ===== Extended Operation Plugins =====

  @operations @merge
  Scenario: Before-plugin validates $merge business rules
    Given the PatientMergeValidationPlugin is enabled
    And a source Patient exists with id "merge-source-plugin"
    And a target Patient exists with id "merge-target-plugin"
    And the source patient has an open encounter
    When I invoke the $merge operation
    Then the response status code should be 422
    And the OperationOutcome message should contain "Cannot merge patient with open encounters"

  @operations @merge
  Scenario: Before-plugin transforms $merge input parameters
    Given the PatientMergeTransformPlugin is enabled
    And source and target Patients exist
    When I invoke the $merge operation
    Then the plugin should resolve patient references before execution
    And the resolved patients should be available to the operation handler

  @operations @validate
  Scenario: Before-plugin adds custom validation rules
    Given the CustomValidationPlugin is enabled
    And I have a Patient resource
    When I invoke the $validate operation
    Then the plugin should apply custom business validation rules
    And the validation output should include custom check results

  @abort
  Scenario: Before-plugin can abort operation with failure
    Given a before-plugin that always fails is enabled
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the response should contain an "OperationOutcome" resource
    And the core operation should NOT have been executed
    And after-plugins should NOT have been executed
```

#### 2. After Operation Plugins

**File: `features/plugins/after-operation-plugins.feature`**
```gherkin
@plugins @after-operation
Feature: After Operation Business Logic Plugins
  As a FHIR server administrator
  I want to execute business logic after operations
  So that I can transform outputs, trigger notifications, and log events

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  # ===== CRUD Operation Plugins =====

  @crud @create
  Scenario: After-plugin triggers notification on Patient create
    Given the PatientNotificationPlugin is enabled
    And the notification service is mocked
    When I create a new Patient resource
    Then the response status code should be 201
    And a notification should have been sent with event "patient.created"
    And the notification should contain the Patient resource id

  @crud @update
  Scenario: After-plugin logs audit event on update
    Given the AuditLoggingPlugin is enabled
    And a Patient exists with id "audit-update-test"
    When I update the Patient resource
    Then the response status code should be 200
    And an audit event should be logged with action "update"
    And the audit event should contain before and after resource versions

  @crud @delete
  Scenario: After-plugin triggers cleanup on delete
    Given the ResourceCleanupPlugin is enabled
    And a Patient exists with related Observations
    When I delete the Patient resource
    Then the response status code should be 204
    And the cleanup plugin should have been notified
    And related resources cleanup should be scheduled

  # ===== Extended Operation Plugins =====

  @operations @merge
  Scenario: After-plugin triggers notification on successful merge
    Given the PatientMergeNotificationPlugin is enabled
    And source and target Patients exist
    When I invoke the $merge operation successfully
    Then a notification should be sent with event "patient.merged"
    And the notification should contain source and target patient ids

  @operations @merge
  Scenario: After-plugin syncs merge to external system
    Given the ExternalSystemSyncPlugin is enabled
    And source and target Patients exist
    When I invoke the $merge operation successfully
    Then the plugin should sync the merge to the external system
    And the sync request should contain merge details

  @operations @validate
  Scenario: After-plugin enriches validation output
    Given the ValidationEnrichmentPlugin is enabled
    And I have a Patient resource
    When I invoke the $validate operation
    Then the response should contain additional validation metadata
    And the output should include "validation-duration-ms" parameter

  @async
  Scenario: After-plugin runs asynchronously without blocking response
    Given an async NotificationPlugin is enabled
    When I create a new Patient resource
    Then the response should be returned immediately
    And the notification should be sent asynchronously
    And response time should not include notification processing time

  @transform
  Scenario: After-plugin can transform operation result
    Given the ResultTransformPlugin is enabled
    When I read a Patient resource
    Then the response should include additional computed fields
    And the "fullName" field should be populated from name components
```

#### 3. Plugin Chain Execution

**File: `features/plugins/plugin-chain-execution.feature`**
```gherkin
@plugins @chain
Feature: Plugin Chain Execution Order
  As a FHIR server administrator
  I want plugins to execute in a defined order
  So that I can control the processing pipeline

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"

  @order
  Scenario: Before-plugins execute in order priority
    Given the following before-plugins are enabled with orders:
      | plugin                     | order |
      | ValidationPlugin           | 100   |
      | EnrichmentPlugin           | 200   |
      | AuditPrepPlugin            | 300   |
    When I create a new Patient resource
    Then plugins should execute in order: ValidationPlugin, EnrichmentPlugin, AuditPrepPlugin

  @order
  Scenario: After-plugins execute in order priority
    Given the following after-plugins are enabled with orders:
      | plugin                     | order |
      | TransformPlugin            | 100   |
      | NotificationPlugin         | 200   |
      | AuditLogPlugin             | 300   |
    When I create a new Patient resource
    Then after-plugins should execute in order: TransformPlugin, NotificationPlugin, AuditLogPlugin

  @short-circuit
  Scenario: Before-plugin can stop chain but continue operation
    Given plugins A (order 100), B (order 200), C (order 300) are enabled
    And plugin B returns continueChain=false but success=true
    When I create a new Patient resource
    Then plugin A should execute
    And plugin B should execute
    And plugin C should NOT execute
    And the core operation should execute
    And the Patient should be created

  @abort
  Scenario: Before-plugin failure stops entire pipeline
    Given plugins A (order 100), B (order 200), C (order 300) are enabled
    And plugin A returns failure
    When I create a new Patient resource
    Then plugin A should execute
    And plugin B should NOT execute
    And the core operation should NOT execute
    And after-plugins should NOT execute

  @selective
  Scenario: Plugins only execute for supported operations
    Given PatientPlugin supports only Patient resources
    And ObservationPlugin supports only Observation resources
    When I create a new Patient resource
    Then PatientPlugin should execute
    And ObservationPlugin should NOT execute

  @extended-operation
  Scenario: Plugin chain works for extended operations
    Given the following plugins support $merge:
      | plugin                     | order | phase  |
      | MergeValidationPlugin      | 100   | before |
      | MergeTransformPlugin       | 200   | before |
      | MergeNotificationPlugin    | 100   | after  |
      | MergeAuditPlugin           | 200   | after  |
    When I invoke the $merge operation
    Then before-plugins execute: MergeValidationPlugin, MergeTransformPlugin
    And after-plugins execute: MergeNotificationPlugin, MergeAuditPlugin

  @context-sharing
  Scenario: Plugins share context attributes
    Given PluginA stores attribute "validatedAt" in context
    And PluginB reads attribute "validatedAt" from context
    When I create a new Patient resource
    Then PluginB should have access to the "validatedAt" attribute

  @error-handling
  Scenario: After-plugin errors are logged but don't fail operation
    Given an after-plugin that throws an exception
    When I create a new Patient resource
    Then the response status code should be 201
    And the Patient should be created
    And the plugin error should be logged
```

#### 4. Plugins with CRUD Operations

**File: `features/plugins/plugin-crud-operations.feature`**
```gherkin
@plugins @crud
Feature: Business Logic Plugins with CRUD Operations
  As a FHIR server administrator
  I want plugins to integrate with all CRUD operations
  So that I can apply consistent business logic

  Background:
    Given the FHIR server is running
    And I am authenticated with valid credentials
    And I set the FHIR version to "R5"
    And business logic plugins are enabled

  # ===== CREATE =====
  @create
  Scenario: Plugin executes before and after CREATE
    Given the audit capture plugin is enabled
    When I create a new Patient resource
    Then the before-create hook should have been called
    And the after-create hook should have been called
    And audit should capture operationType "CREATE"

  @create
  Scenario: Plugin can modify resource before CREATE
    Given the AutoTimestampPlugin is enabled
    And I have a Patient resource without meta.tag
    When I send a POST request to "/fhir/r5/Patient"
    Then the created Patient should have meta.tag "created-by-system"

  # ===== READ =====
  @read
  Scenario: Plugin executes after READ
    Given a Patient exists with id "read-plugin-test"
    And the read logging plugin is enabled
    When I read the Patient resource
    Then the after-read hook should have been called
    And the access should be logged

  @read
  Scenario: Plugin can filter fields on READ
    Given a Patient exists with sensitive data
    And the SensitiveDataFilterPlugin is enabled
    When I read the Patient resource
    Then sensitive fields should be redacted from response

  # ===== UPDATE =====
  @update
  Scenario: Plugin executes before and after UPDATE
    Given a Patient exists with id "update-plugin-test"
    And the audit capture plugin is enabled
    When I update the Patient resource
    Then the before-update hook should have been called with old and new resource
    And the after-update hook should have been called
    And audit should capture operationType "UPDATE"

  @update
  Scenario: Plugin can prevent update based on business rules
    Given a Patient exists with status "deceased"
    And the DeceasedPatientProtectionPlugin is enabled
    When I attempt to update the Patient
    Then the response status code should be 422
    And the OperationOutcome message should contain "Cannot modify deceased patient"

  # ===== DELETE =====
  @delete
  Scenario: Plugin executes before and after DELETE
    Given a Patient exists with id "delete-plugin-test"
    And the audit capture plugin is enabled
    When I delete the Patient resource
    Then the before-delete hook should have been called
    And the after-delete hook should have been called
    And audit should capture operationType "DELETE"

  @delete
  Scenario: Plugin can prevent delete based on business rules
    Given a Patient exists with active encounters
    And the ActiveEncounterProtectionPlugin is enabled
    When I attempt to delete the Patient
    Then the response status code should be 422
    And the OperationOutcome message should contain "Cannot delete patient with active encounters"

  # ===== SEARCH =====
  @search
  Scenario: Plugin executes after SEARCH
    Given multiple Patients exist
    And the search logging plugin is enabled
    When I search for Patients
    Then the after-search hook should have been called
    And the search parameters should be logged

  @search
  Scenario: Plugin can filter search results
    Given the PatientAccessControlPlugin is enabled
    And Patients exist from different departments
    When I search for all Patients
    Then only Patients from my authorized departments should be returned

  # ===== PATCH =====
  @patch
  Scenario: Plugin executes before and after PATCH
    Given a Patient exists with id "patch-plugin-test"
    And the audit capture plugin is enabled
    When I patch the Patient resource
    Then the before-patch hook should have been called
    And the after-patch hook should have been called
    And audit should capture operationType "PATCH"
```

---

### Step Definitions

**File: `CommonSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import io.cucumber.java.Before;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class CommonSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private TestContext testContext;

    private String baseUrl;

    @Before
    public void setUp() {
        baseUrl = "http://localhost:" + port;
        RestAssured.baseURI = baseUrl;
        testContext.reset();
    }

    @After
    public void tearDown() {
        // Clean up created resources if needed
    }

    @Given("the FHIR server is running")
    public void theFhirServerIsRunning() {
        given()
            .when()
            .get("/actuator/health")
            .then()
            .statusCode(200);
    }

    @Given("I am authenticated with valid credentials")
    public void iAmAuthenticatedWithValidCredentials() {
        String token = generateTestToken("patient/*.read", "patient/*.write");
        testContext.setAuthToken(token);
    }

    @Given("I am not authenticated")
    public void iAmNotAuthenticated() {
        testContext.setAuthToken(null);
    }

    @Given("I set the FHIR version to {string}")
    public void iSetTheFhirVersionTo(String version) {
        // Store version in context for URL construction
    }

    @When("I send a GET request to {string}")
    public void iSendAGetRequestTo(String path) {
        String resolvedPath = resolvePath(path);
        RequestSpecification request = buildRequest();

        Response response = request
            .when()
            .get(resolvedPath);

        testContext.setResponse(response);
    }

    @When("I send a POST request to {string}")
    public void iSendAPostRequestTo(String path) {
        String resolvedPath = resolvePath(path);
        RequestSpecification request = buildRequest();

        Response response = request
            .contentType("application/fhir+json")
            .when()
            .post(resolvedPath);

        testContext.setResponse(response);
    }

    @When("I send a PUT request to {string}")
    public void iSendAPutRequestTo(String path) {
        String resolvedPath = resolvePath(path);
        RequestSpecification request = buildRequest();

        Response response = request
            .contentType("application/fhir+json")
            .when()
            .put(resolvedPath);

        testContext.setResponse(response);
    }

    @When("I send a DELETE request to {string}")
    public void iSendADeleteRequestTo(String path) {
        String resolvedPath = resolvePath(path);
        RequestSpecification request = buildRequest();

        Response response = request
            .when()
            .delete(resolvedPath);

        testContext.setResponse(response);
    }

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int statusCode) {
        assertEquals(statusCode, testContext.getResponse().getStatusCode());
    }

    @Then("the response should contain a {string} resource")
    public void theResponseShouldContainAResource(String resourceType) {
        testContext.getResponse()
            .then()
            .body("resourceType", equalTo(resourceType));
    }

    @Then("the response should contain an {string} resource")
    public void theResponseShouldContainAnResource(String resourceType) {
        theResponseShouldContainAResource(resourceType);
    }

    @Then("the response should have a {string} header")
    public void theResponseShouldHaveAHeader(String headerName) {
        assertNotNull(testContext.getResponse().getHeader(headerName));
    }

    @Then("the resource should have an {string} element")
    public void theResourceShouldHaveAnElement(String elementPath) {
        testContext.getResponse()
            .then()
            .body(elementPath, notNullValue());
    }

    @Then("the resource should have a {string} element")
    public void theResourceShouldHaveAElement(String elementPath) {
        theResourceShouldHaveAnElement(elementPath);
    }

    @Then("the response should be a Bundle of type {string}")
    public void theResponseShouldBeABundleOfType(String bundleType) {
        testContext.getResponse()
            .then()
            .body("resourceType", equalTo("Bundle"))
            .body("type", equalTo(bundleType));
    }

    @Then("the Bundle should contain {int} entries")
    public void theBundleShouldContainEntries(int count) {
        testContext.getResponse()
            .then()
            .body("entry.size()", equalTo(count));
    }

    @Then("the Bundle should contain {int} entry")
    public void theBundleShouldContainEntry(int count) {
        theBundleShouldContainEntries(count);
    }

    @Then("the Bundle should contain exactly {int} entries")
    public void theBundleShouldContainExactlyEntries(int count) {
        theBundleShouldContainEntries(count);
    }

    @Then("the OperationOutcome should have severity {string}")
    public void theOperationOutcomeShouldHaveSeverity(String severity) {
        testContext.getResponse()
            .then()
            .body("issue[0].severity", equalTo(severity));
    }

    @Then("the OperationOutcome should have code {string}")
    public void theOperationOutcomeShouldHaveCode(String code) {
        testContext.getResponse()
            .then()
            .body("issue[0].code", equalTo(code));
    }

    private RequestSpecification buildRequest() {
        RequestSpecification request = given()
            .accept("application/fhir+json");

        if (testContext.getAuthToken() != null) {
            request = request.header("Authorization", "Bearer " + testContext.getAuthToken());
        }

        return request;
    }

    private String resolvePath(String path) {
        if (path.contains("{id}")) {
            path = path.replace("{id}", testContext.getCurrentResourceId());
        }
        return path;
    }

    private String generateTestToken(String... scopes) {
        // Generate JWT token for testing
        return "test-token";
    }
}
```

**File: `CacheSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import com.github.benmanes.caffeine.cache.Cache;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

public class CacheSteps {

    @Autowired
    private TestContext testContext;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private Cache<String, Object> localCache;

    @Given("the cache is cleared")
    public void theCacheIsCleared() {
        localCache.invalidateAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Given("a Patient resource exists with id {string}")
    public void aPatientResourceExistsWithId(String id) {
        String patientJson = String.format("""
            {
                "resourceType": "Patient",
                "id": "%s",
                "name": [{"family": "CacheTest", "given": ["Test"]}],
                "gender": "male"
            }
            """, id);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(patientJson)
            .when()
            .put("/fhir/r5/Patient/" + id);

        testContext.addCreatedResource("Patient", id);
    }

    @When("I read the Patient {string}")
    public void iReadThePatient(String id) {
        long startTime = System.currentTimeMillis();

        var response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .when()
            .get("/fhir/r5/Patient/" + id);

        long endTime = System.currentTimeMillis();
        testContext.setResponse(response);
        testContext.setScenarioData("responseTime", endTime - startTime);
    }

    @Given("I have read the Patient {string} once")
    public void iHaveReadThePatientOnce(String id) {
        iReadThePatient(id);
    }

    @When("I read the Patient {string} again")
    public void iReadThePatientAgain(String id) {
        iReadThePatient(id);
    }

    @Then("the resource should be stored in L1 local cache")
    public void theResourceShouldBeStoredInL1LocalCache() {
        String cacheKey = "Patient/" + testContext.getCurrentResourceId();
        assertNotNull(localCache.getIfPresent(cacheKey),
            "Resource should be in L1 cache");
    }

    @Then("the cache key should be {string}")
    public void theCacheKeyShouldBe(String expectedKey) {
        String resolvedKey = expectedKey.replace("{id}", testContext.getCurrentResourceId());
        assertNotNull(localCache.getIfPresent(resolvedKey),
            "Cache key " + resolvedKey + " should exist");
    }

    @Then("the response should be served from L1 cache")
    public void theResponseShouldBeServedFromL1Cache() {
        // Verify via response time or cache hit metrics
        Long responseTime = testContext.getScenarioData("responseTime", Long.class);
        assertTrue(responseTime < 5, "Response should be fast when served from L1 cache");
    }

    @Then("the response time should be less than {int} milliseconds")
    public void theResponseTimeShouldBeLessThanMilliseconds(int maxMs) {
        Long responseTime = testContext.getScenarioData("responseTime", Long.class);
        assertTrue(responseTime < maxMs,
            String.format("Response time %dms should be less than %dms", responseTime, maxMs));
    }

    @Then("no database query should be executed")
    public void noDatabaseQueryShouldBeExecuted() {
        // Verify via metrics or trace logs
        // This would typically check a query counter metric
    }

    @Given("the resource is in L2 Redis cache but not L1")
    public void theResourceIsInL2RedisCacheButNotL1() {
        String id = testContext.getCurrentResourceId();
        String cacheKey = "Patient/" + id;

        // Clear L1 but keep L2
        localCache.invalidate(cacheKey);

        // Verify L2 still has it
        assertNotNull(redisTemplate.opsForValue().get(cacheKey),
            "Resource should be in Redis cache");
    }

    @Then("the response should be served from L2 Redis cache")
    public void theResponseShouldBeServedFromL2RedisCache() {
        Long responseTime = testContext.getScenarioData("responseTime", Long.class);
        assertTrue(responseTime < 10, "Response should be fast when served from L2 cache");
    }

    @Then("the resource should be populated in L1 cache")
    public void theResourceShouldBePopulatedInL1Cache() {
        theResourceShouldBeStoredInL1LocalCache();
    }

    @Given("the resource is not in any cache")
    public void theResourceIsNotInAnyCache() {
        String cacheKey = "Patient/" + testContext.getCurrentResourceId();
        localCache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
    }

    @Then("a database query should be executed")
    public void aDatabaseQueryShouldBeExecuted() {
        // Verify via metrics - query count should increase
    }

    @Then("the resource should be stored in L2 Redis cache")
    public void theResourceShouldBeStoredInL2RedisCache() {
        String cacheKey = "Patient/" + testContext.getCurrentResourceId();
        assertNotNull(redisTemplate.opsForValue().get(cacheKey),
            "Resource should be in Redis cache");
    }

    @Given("the Patient {string} is cached in L1 and L2")
    public void thePatientIsCachedInL1AndL2(String id) {
        // Read the resource to populate caches
        iReadThePatient(id);

        // Verify both caches have it
        String cacheKey = "Patient/" + id;
        assertNotNull(localCache.getIfPresent(cacheKey));
        assertNotNull(redisTemplate.opsForValue().get(cacheKey));
    }

    @When("I update the Patient {string}")
    public void iUpdateThePatient(String id) {
        String updatedJson = String.format("""
            {
                "resourceType": "Patient",
                "id": "%s",
                "name": [{"family": "Updated", "given": ["Test"]}],
                "gender": "male"
            }
            """, id);

        var response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(updatedJson)
            .when()
            .put("/fhir/r5/Patient/" + id);

        testContext.setResponse(response);
    }

    @Then("the L1 cache for {string} should be invalidated")
    public void theL1CacheForShouldBeInvalidated(String cacheKey) {
        assertNull(localCache.getIfPresent(cacheKey),
            "L1 cache should be invalidated for " + cacheKey);
    }

    @Then("the L2 Redis cache for {string} should be invalidated")
    public void theL2RedisCacheForShouldBeInvalidated(String cacheKey) {
        assertNull(redisTemplate.opsForValue().get(cacheKey),
            "L2 Redis cache should be invalidated for " + cacheKey);
    }

    @Given("I have cached a search for {string} returning {int} results")
    public void iHaveCachedASearchForReturningResults(String searchParams, int resultCount) {
        // Execute search to populate cache
        String[] parts = searchParams.split("=");
        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .queryParam(parts[0], parts[1])
            .when()
            .get("/fhir/r5/Patient");

        testContext.setScenarioData("cachedSearchParams", searchParams);
    }

    @Then("the search cache for {string} should be invalidated")
    public void theSearchCacheForShouldBeInvalidated(String searchParams) {
        String cacheKey = "search:Patient?" + searchParams;
        assertNull(redisTemplate.opsForValue().get(cacheKey),
            "Search cache should be invalidated for " + searchParams);
    }

    @Then("the search result should be cached")
    public void theSearchResultShouldBeCached() {
        // Verify search result is in Redis cache
        // The exact key format depends on implementation
    }

    @Then("the search result should NOT be cached")
    public void theSearchResultShouldNotBeCached() {
        // Verify search result is not in cache due to size
    }

    @Then("the cache should contain the updated resource")
    public void theCacheShouldContainTheUpdatedResource() {
        String cacheKey = "Patient/" + testContext.getCurrentResourceId();
        Object cached = localCache.getIfPresent(cacheKey);
        assertNotNull(cached, "Cache should contain updated resource");
        // Could also verify the content matches the update
    }

    @Then("subsequent reads should return the updated resource")
    public void subsequentReadsShouldReturnTheUpdatedResource() {
        iReadThePatient(testContext.getCurrentResourceId());
        testContext.getResponse()
            .then()
            .body("name[0].family", org.hamcrest.Matchers.equalTo("Updated"));
    }
}
```

**File: `PatientSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class PatientSteps {

    @Autowired
    private TestContext testContext;

    private String patientJson;

    @Given("I have a valid Patient resource")
    public void iHaveAValidPatientResource() {
        patientJson = """
            {
                "resourceType": "Patient",
                "name": [{
                    "family": "TestFamily",
                    "given": ["TestGiven"]
                }],
                "gender": "male",
                "birthDate": "1990-01-15"
            }
            """;
    }

    @Given("I have a valid Patient resource with id {string}")
    public void iHaveAValidPatientResourceWithId(String id) {
        patientJson = String.format("""
            {
                "resourceType": "Patient",
                "id": "%s",
                "name": [{
                    "family": "TestFamily",
                    "given": ["TestGiven"]
                }],
                "gender": "male",
                "birthDate": "1990-01-15"
            }
            """, id);
    }

    @Given("a Patient resource exists in the system")
    public void aPatientResourceExistsInTheSystem() {
        iHaveAValidPatientResource();

        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(patientJson)
            .when()
            .post("/fhir/r5/Patient");

        String id = response.jsonPath().getString("id");
        testContext.addCreatedResource("Patient", id);
    }

    @Given("the following Patients exist:")
    public void theFollowingPatientsExist(DataTable dataTable) {
        List<Map<String, String>> patients = dataTable.asMaps();

        for (Map<String, String> patient : patients) {
            String json = buildPatientJson(patient);

            given()
                .header("Authorization", "Bearer " + testContext.getAuthToken())
                .contentType("application/fhir+json")
                .body(json)
                .when()
                .put("/fhir/r5/Patient/" + patient.get("id"));

            testContext.addCreatedResource("Patient", patient.get("id"));
        }
    }

    @Given("I modify the Patient family name to {string}")
    public void iModifyThePatientFamilyNameTo(String newName) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .when()
            .get("/fhir/r5/Patient/" + testContext.getCurrentResourceId());

        String currentJson = response.asString();
        patientJson = currentJson.replaceFirst("\"family\":\"[^\"]+\"",
            "\"family\":\"" + newName + "\"");
    }

    @When("I search for Patients with parameter {string} = {string}")
    public void iSearchForPatientsWithParameter(String param, String value) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .queryParam(param, value)
            .when()
            .get("/fhir/r5/Patient");

        testContext.setResponse(response);
    }

    @When("I search for Patients with parameters:")
    public void iSearchForPatientsWithParameters(DataTable dataTable) {
        List<Map<String, String>> params = dataTable.asMaps();

        var request = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken());

        for (Map<String, String> param : params) {
            request = request.queryParam(param.get("parameter"), param.get("value"));
        }

        Response response = request
            .when()
            .get("/fhir/r5/Patient");

        testContext.setResponse(response);
    }

    @Then("the resource family name should be {string}")
    public void theResourceFamilyNameShouldBe(String expectedName) {
        testContext.getResponse()
            .then()
            .body("name[0].family", equalTo(expectedName));
    }

    @Then("all entries should have family name {string}")
    public void allEntriesShouldHaveFamilyName(String expectedName) {
        testContext.getResponse()
            .then()
            .body("entry.resource.name[0].family", everyItem(equalTo(expectedName)));
    }

    @Then("all entries should have gender {string}")
    public void allEntriesShouldHaveGender(String expectedGender) {
        testContext.getResponse()
            .then()
            .body("entry.resource.gender", everyItem(equalTo(expectedGender)));
    }

    private String buildPatientJson(Map<String, String> data) {
        String identifier = data.get("identifier");
        String[] idParts = identifier != null ? identifier.split("\\|") : new String[]{"", ""};

        return String.format("""
            {
                "resourceType": "Patient",
                "id": "%s",
                "identifier": [{
                    "system": "%s",
                    "value": "%s"
                }],
                "name": [{
                    "family": "%s",
                    "given": ["%s"]
                }],
                "gender": "%s",
                "birthDate": "%s",
                "active": %s
            }
            """,
            data.get("id"),
            idParts[0].replace("\\", ""),
            idParts.length > 1 ? idParts[1] : "",
            data.get("family"),
            data.get("given"),
            data.get("gender"),
            data.get("birthDate"),
            data.get("active")
        );
    }
}
```

**File: `SearchSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class SearchSteps {

    @Autowired
    private TestContext testContext;

    @Then("the Bundle should have a {string} link")
    public void theBundleShouldHaveALink(String linkRelation) {
        testContext.getResponse()
            .then()
            .body("link.findAll { it.relation == '" + linkRelation + "' }.size()",
                greaterThan(0));
    }

    @Then("the Bundle should not have a {string} element")
    public void theBundleShouldNotHaveAElement(String element) {
        Object value = testContext.getResponse().jsonPath().get(element);
        assertNull(value);
    }

    @Then("the results should be sorted by family name in ascending order")
    public void theResultsShouldBeSortedByFamilyNameInAscendingOrder() {
        List<String> familyNames = testContext.getResponse()
            .jsonPath()
            .getList("entry.resource.name[0].family", String.class);

        for (int i = 1; i < familyNames.size(); i++) {
            assertTrue(familyNames.get(i-1).compareToIgnoreCase(familyNames.get(i)) <= 0,
                "Results not sorted in ascending order");
        }
    }

    @Then("the results should be sorted by family name in descending order")
    public void theResultsShouldBeSortedByFamilyNameInDescendingOrder() {
        List<String> familyNames = testContext.getResponse()
            .jsonPath()
            .getList("entry.resource.name[0].family", String.class);

        for (int i = 1; i < familyNames.size(); i++) {
            assertTrue(familyNames.get(i-1).compareToIgnoreCase(familyNames.get(i)) >= 0,
                "Results not sorted in descending order");
        }
    }

    @Then("all entries should have birthDate after {string}")
    public void allEntriesShouldHaveBirthDateAfter(String dateStr) {
        LocalDate threshold = LocalDate.parse(dateStr);
        List<String> birthDates = testContext.getResponse()
            .jsonPath()
            .getList("entry.resource.birthDate", String.class);

        for (String birthDate : birthDates) {
            LocalDate bd = LocalDate.parse(birthDate);
            assertTrue(bd.isAfter(threshold),
                "BirthDate " + birthDate + " is not after " + dateStr);
        }
    }

    @When("I follow the {string} link in the Bundle")
    public void iFollowTheLinkInTheBundle(String linkRelation) {
        String nextUrl = testContext.getResponse()
            .jsonPath()
            .getString("link.find { it.relation == '" + linkRelation + "' }.url");

        assertNotNull(nextUrl, "No " + linkRelation + " link found in Bundle");

        testContext.setResponse(
            given()
                .header("Authorization", "Bearer " + testContext.getAuthToken())
                .when()
                .get(nextUrl)
        );
    }

    @Then("the Bundle should contain the referenced Organization")
    public void theBundleShouldContainTheReferencedOrganization() {
        testContext.getResponse()
            .then()
            .body("entry.resource.resourceType", hasItem("Organization"));
    }

    @Then("the Organization entry should have search mode {string}")
    public void theOrganizationEntryShouldHaveSearchMode(String mode) {
        testContext.getResponse()
            .then()
            .body("entry.find { it.resource.resourceType == 'Organization' }.search.mode",
                equalTo(mode));
    }
}
```

**File: `ExtendedOperationSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class ExtendedOperationSteps {

    @Autowired
    private TestContext testContext;

    // ===== Setup Steps =====

    @Given("the ${operationName} operation is enabled for {resourceType}")
    public void theOperationIsEnabledForResource(String operationName, String resourceType) {
        // Verify operation is enabled via CapabilityStatement
        testContext.setScenarioData("operationName", operationName);
        testContext.setScenarioData("operationResourceType", resourceType);
    }

    @Given("the ${operationName} operation is enabled")
    public void theOperationIsEnabled(String operationName) {
        testContext.setScenarioData("operationName", operationName);
    }

    @Given("a source Patient exists with id {string}")
    public void aSourcePatientExistsWithId(String patientId) {
        // Create or ensure source patient exists
        String patientJson = """
            {
                "resourceType": "Patient",
                "id": "%s",
                "active": true,
                "name": [{"family": "SourceFamily", "given": ["SourceGiven"]}]
            }
            """.formatted(patientId);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType(ContentType.JSON)
            .body(patientJson)
            .when()
            .put("/fhir/r5/Patient/" + patientId)
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)));

        testContext.setScenarioData("sourcePatientId", patientId);
    }

    @Given("a target Patient exists with id {string}")
    public void aTargetPatientExistsWithId(String patientId) {
        String patientJson = """
            {
                "resourceType": "Patient",
                "id": "%s",
                "active": true,
                "name": [{"family": "TargetFamily", "given": ["TargetGiven"]}]
            }
            """.formatted(patientId);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType(ContentType.JSON)
            .body(patientJson)
            .when()
            .put("/fhir/r5/Patient/" + patientId)
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)));

        testContext.setScenarioData("targetPatientId", patientId);
    }

    @Given("an inactive source Patient exists with id {string}")
    public void anInactiveSourcePatientExistsWithId(String patientId) {
        String patientJson = """
            {
                "resourceType": "Patient",
                "id": "%s",
                "active": false,
                "name": [{"family": "InactiveFamily", "given": ["InactiveGiven"]}]
            }
            """.formatted(patientId);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType(ContentType.JSON)
            .body(patientJson)
            .when()
            .put("/fhir/r5/Patient/" + patientId);

        testContext.setScenarioData("sourcePatientId", patientId);
    }

    // ===== Invocation Steps =====

    @When("I invoke the ${operationName} operation on {resourceType} type with parameters:")
    public void iInvokeOperationOnTypeWithParameters(String operationName, String resourceType, String parametersJson) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(parametersJson)
            .when()
            .post("/fhir/r5/" + resourceType + "/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke the ${operationName} operation on {resourceType} instance {string} with parameters:")
    public void iInvokeOperationOnInstanceWithParameters(String operationName, String resourceType,
            String resourceId, String parametersJson) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(parametersJson)
            .when()
            .post("/fhir/r5/" + resourceType + "/" + resourceId + "/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke the ${operationName} operation on {resourceType} type with the resource")
    public void iInvokeOperationOnTypeWithResource(String operationName, String resourceType) {
        String resource = testContext.getScenarioData("currentResource", String.class);

        String parametersJson = """
            {
                "resourceType": "Parameters",
                "parameter": [
                    {
                        "name": "resource",
                        "resource": %s
                    }
                ]
            }
            """.formatted(resource);

        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .body(parametersJson)
            .when()
            .post("/fhir/r5/" + resourceType + "/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke the ${operationName} operation with profile {string}")
    public void iInvokeOperationWithProfile(String operationName, String profileUrl) {
        String resource = testContext.getScenarioData("currentResource", String.class);

        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .queryParam("profile", profileUrl)
            .body(resource)
            .when()
            .post("/fhir/r5/Patient/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke the ${operationName} operation on {resourceType} instance {string}")
    public void iInvokeOperationOnInstance(String operationName, String resourceType, String resourceId) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .when()
            .get("/fhir/r5/" + resourceType + "/" + resourceId + "/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke an undefined operation {string} on {resourceType} type")
    public void iInvokeUndefinedOperation(String operationName, String resourceType) {
        Response response = given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType("application/fhir+json")
            .when()
            .post("/fhir/r5/" + resourceType + "/$" + operationName.replace("$", ""));

        testContext.setResponse(response);
    }

    @When("I invoke the ${operationName} operation with non-existent source patient {string}")
    public void iInvokeOperationWithNonExistentSource(String operationName, String patientId) {
        String targetId = testContext.getScenarioData("targetPatientId", String.class);

        String parametersJson = """
            {
                "resourceType": "Parameters",
                "parameter": [
                    {"name": "source-patient", "valueReference": {"reference": "Patient/%s"}},
                    {"name": "target-patient", "valueReference": {"reference": "Patient/%s"}}
                ]
            }
            """.formatted(patientId, targetId);

        iInvokeOperationOnTypeWithParameters(operationName, "Patient", parametersJson);
    }

    @When("I invoke the ${operationName} operation with source and target both {string}")
    public void iInvokeOperationWithSameSourceAndTarget(String operationName, String patientId) {
        String parametersJson = """
            {
                "resourceType": "Parameters",
                "parameter": [
                    {"name": "source-patient", "valueReference": {"reference": "Patient/%s"}},
                    {"name": "target-patient", "valueReference": {"reference": "Patient/%s"}}
                ]
            }
            """.formatted(patientId, patientId);

        iInvokeOperationOnTypeWithParameters(operationName, "Patient", parametersJson);
    }

    @When("I invoke the ${operationName} operation on {resourceType} type with empty parameters")
    public void iInvokeOperationWithEmptyParameters(String operationName, String resourceType) {
        String parametersJson = """
            {
                "resourceType": "Parameters",
                "parameter": []
            }
            """;

        iInvokeOperationOnTypeWithParameters(operationName, resourceType, parametersJson);
    }

    // ===== Assertion Steps =====

    @Then("the output should contain parameter {string} with a {resourceType} reference")
    public void theOutputShouldContainParameterWithReference(String paramName, String resourceType) {
        testContext.getResponse()
            .then()
            .body("parameter.find { it.name == '" + paramName + "' }.valueReference.reference",
                startsWith(resourceType + "/"));
    }

    @Then("the source Patient should be marked as inactive")
    public void theSourcePatientShouldBeMarkedAsInactive() {
        String sourceId = testContext.getScenarioData("sourcePatientId", String.class);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .when()
            .get("/fhir/r5/Patient/" + sourceId)
            .then()
            .body("active", equalTo(false));
    }

    @Then("the source Patient should have a link to the target Patient")
    public void theSourcePatientShouldHaveLinkToTarget() {
        String sourceId = testContext.getScenarioData("sourcePatientId", String.class);
        String targetId = testContext.getScenarioData("targetPatientId", String.class);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .when()
            .get("/fhir/r5/Patient/" + sourceId)
            .then()
            .body("link.other.reference", hasItem("Patient/" + targetId))
            .body("link.type", hasItem("replaced-by"));
    }

    @Then("the source Patient should be merged into target")
    public void theSourcePatientShouldBeMergedIntoTarget() {
        theSourcePatientShouldBeMarkedAsInactive();
        theSourcePatientShouldHaveLinkToTarget();
    }

    @Then("the OperationOutcome should have no errors")
    public void theOperationOutcomeShouldHaveNoErrors() {
        testContext.getResponse()
            .then()
            .body("issue.findAll { it.severity == 'error' }.size()", equalTo(0));
    }

    @Then("the OperationOutcome message should contain {string}")
    public void theOperationOutcomeMessageShouldContain(String expectedText) {
        testContext.getResponse()
            .then()
            .body("issue.diagnostics", hasItem(containsString(expectedText)));
    }

    @Then("the OperationOutcome should describe the validation errors")
    public void theOperationOutcomeShouldDescribeValidationErrors() {
        testContext.getResponse()
            .then()
            .body("issue.find { it.severity == 'error' }", notNullValue())
            .body("issue.find { it.severity == 'error' }.diagnostics", notNullValue());
    }
}
```

**File: `BusinessLogicPluginSteps.java`**
```java
package org.fhirframework.bdd.steps;

import org.fhirframework.bdd.context.TestContext;
import org.fhirframework.bdd.plugins.TestAuditCapturePlugin;
import org.fhirframework.bdd.plugins.TestPatientConsentPlugin;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.restassured.http.ContentType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class BusinessLogicPluginSteps {

    @Autowired
    private TestContext testContext;

    @Autowired
    private TestPatientConsentPlugin consentPlugin;

    @Autowired
    private TestAuditCapturePlugin auditCapturePlugin;

    // ===== Plugin Configuration Steps =====

    @Given("the {pluginName}Plugin is enabled")
    public void thePluginIsEnabled(String pluginName) {
        testContext.setScenarioData("enabledPlugin", pluginName);
        // In test environment, plugins are configured via Spring profiles
    }

    @Given("the following before-plugins are enabled with orders:")
    public void theFollowingBeforePluginsAreEnabledWithOrders(DataTable dataTable) {
        List<Map<String, String>> plugins = dataTable.asMaps();
        testContext.setScenarioData("configuredPlugins", plugins);
        // Configure plugin order for test
    }

    @Given("the following after-plugins are enabled with orders:")
    public void theFollowingAfterPluginsAreEnabledWithOrders(DataTable dataTable) {
        List<Map<String, String>> plugins = dataTable.asMaps();
        testContext.setScenarioData("configuredAfterPlugins", plugins);
    }

    @Given("plugins A \\(order {int}), B \\(order {int}), C \\(order {int}) are enabled")
    public void pluginsABCAreEnabled(int orderA, int orderB, int orderC) {
        testContext.setScenarioData("pluginOrderA", orderA);
        testContext.setScenarioData("pluginOrderB", orderB);
        testContext.setScenarioData("pluginOrderC", orderC);
    }

    @Given("plugin {word} returns continueChain=false but success=true")
    public void pluginReturnsContinueChainFalse(String pluginName) {
        testContext.setScenarioData("shortCircuitPlugin", pluginName);
    }

    @Given("plugin {word} returns failure")
    public void pluginReturnsFailure(String pluginName) {
        testContext.setScenarioData("failingPlugin", pluginName);
    }

    @Given("a before-plugin that always fails is enabled")
    public void aBeforePluginThatAlwaysFailsIsEnabled() {
        testContext.setScenarioData("alwaysFailPlugin", true);
    }

    @Given("an async NotificationPlugin is enabled")
    public void anAsyncNotificationPluginIsEnabled() {
        testContext.setScenarioData("asyncNotificationEnabled", true);
    }

    // ===== Plugin Input Setup Steps =====

    @Given("I have a Patient resource without consent flag")
    public void iHaveAPatientResourceWithoutConsentFlag() {
        String patientJson = """
            {
                "resourceType": "Patient",
                "name": [{"family": "NoConsent", "given": ["Test"]}],
                "active": true
            }
            """;
        testContext.setScenarioData("currentResource", patientJson);
    }

    @Given("I have a Patient resource with consent flag set to true")
    public void iHaveAPatientResourceWithConsentFlag() {
        String patientJson = """
            {
                "resourceType": "Patient",
                "name": [{"family": "WithConsent", "given": ["Test"]}],
                "active": true,
                "extension": [{
                    "url": "http://example.org/fhir/consent-given",
                    "valueBoolean": true
                }]
            }
            """;
        testContext.setScenarioData("currentResource", patientJson);
    }

    @Given("I have an Observation with a numeric value but no interpretation")
    public void iHaveAnObservationWithNumericValueNoInterpretation() {
        String observationJson = """
            {
                "resourceType": "Observation",
                "status": "final",
                "code": {"coding": [{"system": "http://loinc.org", "code": "2339-0"}]},
                "subject": {"reference": "Patient/test-patient"},
                "valueQuantity": {"value": 150, "unit": "mg/dL"}
            }
            """;
        testContext.setScenarioData("currentResource", observationJson);
    }

    @Given("the source patient has an open encounter")
    public void theSourcePatientHasAnOpenEncounter() {
        String sourceId = testContext.getScenarioData("sourcePatientId", String.class);

        // Create an open encounter for the source patient
        String encounterJson = """
            {
                "resourceType": "Encounter",
                "status": "in-progress",
                "class": {"system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB"},
                "subject": {"reference": "Patient/%s"}
            }
            """.formatted(sourceId);

        given()
            .header("Authorization", "Bearer " + testContext.getAuthToken())
            .contentType(ContentType.JSON)
            .body(encounterJson)
            .when()
            .post("/fhir/r5/Encounter");
    }

    // ===== Plugin Execution Verification Steps =====

    @Then("the before-create hook should have been called")
    public void theBeforeCreateHookShouldHaveBeenCalled() {
        assertTrue(auditCapturePlugin.wasBeforeOperationCalled("CREATE"),
            "Before-create hook should have been called");
    }

    @Then("the after-create hook should have been called")
    public void theAfterCreateHookShouldHaveBeenCalled() {
        assertTrue(auditCapturePlugin.wasAfterOperationCalled("CREATE"),
            "After-create hook should have been called");
    }

    @Then("the before-update hook should have been called with old and new resource")
    public void theBeforeUpdateHookShouldHaveBeenCalledWithOldAndNew() {
        assertTrue(auditCapturePlugin.wasBeforeOperationCalled("UPDATE"),
            "Before-update hook should have been called");
        assertNotNull(auditCapturePlugin.getCapturedOldResource(),
            "Old resource should have been captured");
        assertNotNull(auditCapturePlugin.getCapturedNewResource(),
            "New resource should have been captured");
    }

    @Then("the after-update hook should have been called")
    public void theAfterUpdateHookShouldHaveBeenCalled() {
        assertTrue(auditCapturePlugin.wasAfterOperationCalled("UPDATE"),
            "After-update hook should have been called");
    }

    @Then("the before-delete hook should have been called")
    public void theBeforeDeleteHookShouldHaveBeenCalled() {
        assertTrue(auditCapturePlugin.wasBeforeOperationCalled("DELETE"),
            "Before-delete hook should have been called");
    }

    @Then("the after-delete hook should have been called")
    public void theAfterDeleteHookShouldHaveBeenCalled() {
        assertTrue(auditCapturePlugin.wasAfterOperationCalled("DELETE"),
            "After-delete hook should have been called");
    }

    @Then("audit should capture operationType {string}")
    public void auditShouldCaptureOperationType(String operationType) {
        assertEquals(operationType, auditCapturePlugin.getCapturedOperationType(),
            "Audit should capture correct operation type");
    }

    @Then("the core operation should NOT have been executed")
    public void theCoreOperationShouldNotHaveBeenExecuted() {
        assertFalse(auditCapturePlugin.wasCoreOperationExecuted(),
            "Core operation should not have been executed");
    }

    @Then("after-plugins should NOT have been executed")
    public void afterPluginsShouldNotHaveBeenExecuted() {
        assertFalse(auditCapturePlugin.wasAnyAfterPluginCalled(),
            "After plugins should not have been executed");
    }

    @Then("plugins should execute in order: {}, {}, {}")
    public void pluginsShouldExecuteInOrder(String first, String second, String third) {
        List<String> executionOrder = auditCapturePlugin.getPluginExecutionOrder();
        assertEquals(first, executionOrder.get(0));
        assertEquals(second, executionOrder.get(1));
        assertEquals(third, executionOrder.get(2));
    }

    @Then("plugin {word} should execute")
    public void pluginShouldExecute(String pluginName) {
        assertTrue(auditCapturePlugin.getPluginExecutionOrder().contains(pluginName),
            "Plugin " + pluginName + " should have executed");
    }

    @Then("plugin {word} should NOT execute")
    public void pluginShouldNotExecute(String pluginName) {
        assertFalse(auditCapturePlugin.getPluginExecutionOrder().contains(pluginName),
            "Plugin " + pluginName + " should not have executed");
    }

    // ===== Plugin Result Verification Steps =====

    @Then("the Observation should have an auto-populated interpretation")
    public void theObservationShouldHaveAutoPopulatedInterpretation() {
        testContext.getResponse()
            .then()
            .body("interpretation", notNullValue())
            .body("interpretation[0].coding[0].code", notNullValue());
    }

    @Then("the created Patient should have meta.tag {string}")
    public void theCreatedPatientShouldHaveMetaTag(String tagValue) {
        testContext.getResponse()
            .then()
            .body("meta.tag.code", hasItem(tagValue));
    }

    @Then("a notification should have been sent with event {string}")
    public void aNotificationShouldHaveBeenSentWithEvent(String eventType) {
        // Check mock notification service for sent notifications
        assertTrue(testContext.getScenarioData("lastNotificationEvent", String.class)
            .equals(eventType), "Notification should have been sent with event " + eventType);
    }

    @Then("the notification should contain the Patient resource id")
    public void theNotificationShouldContainThePatientResourceId() {
        String notificationBody = testContext.getScenarioData("lastNotificationBody", String.class);
        String patientId = testContext.getCurrentResourceId();
        assertTrue(notificationBody.contains(patientId),
            "Notification should contain patient ID");
    }

    @Then("the plugin should resolve patient references before execution")
    public void thePluginShouldResolvePatientReferences() {
        // Verify resolved patients are in context attributes
        assertNotNull(auditCapturePlugin.getContextAttribute("sourcePatient"),
            "Source patient should be resolved");
        assertNotNull(auditCapturePlugin.getContextAttribute("targetPatient"),
            "Target patient should be resolved");
    }

    @Then("the resolved patients should be available to the operation handler")
    public void theResolvedPatientsShouldBeAvailableToHandler() {
        assertTrue(auditCapturePlugin.wasContextAttributeUsedByHandler("sourcePatient"),
            "Handler should have access to resolved source patient");
    }

    @Then("the response should contain additional validation metadata")
    public void theResponseShouldContainAdditionalValidationMetadata() {
        testContext.getResponse()
            .then()
            .body("parameter.name", hasItem("validation-duration-ms"));
    }

    @Then("the output should include {string} parameter")
    public void theOutputShouldIncludeParameter(String paramName) {
        testContext.getResponse()
            .then()
            .body("parameter.find { it.name == '" + paramName + "' }", notNullValue());
    }

    @Then("the plugin error should be logged")
    public void thePluginErrorShouldBeLogged() {
        // Check logs for plugin error - typically via log appender in tests
        assertTrue(testContext.getScenarioData("pluginErrorLogged", Boolean.class),
            "Plugin error should have been logged");
    }
}
```

---

### Running BDD Tests

```bash
# Run all BDD tests
./mvnw test -Dtest=CucumberTestRunner

# Run specific feature
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@patient and @crud"

# Run smoke tests only
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@smoke"

# Run cache tests only
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@cache"

# Run extended operations tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@operations"

# Run $merge operation tests only
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@operations and @merge"

# Run $validate operation tests only
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@operations and @validate"

# Run $everything operation tests only
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@operations and @everything"

# Run business logic plugin tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@plugins"

# Run before-operation plugin tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@plugins and @before-operation"

# Run after-operation plugin tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@plugins and @after-operation"

# Run plugin chain execution tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@plugins and @chain"

# Run all operations and plugin tests
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="@operations or @plugins"

# Run tests excluding ignored
./mvnw test -Dtest=CucumberTestRunner -Dcucumber.filter.tags="not @ignore"

# Generate HTML report
./mvnw test -Dtest=CucumberTestRunner
# Report available at: target/cucumber-reports/cucumber.html
```

### Test Data Files

**File: `test-data/patients/valid-patient.json`**
```json
{
  "resourceType": "Patient",
  "identifier": [
    {
      "system": "http://hospital.example.org/mrn",
      "value": "12345"
    }
  ],
  "name": [
    {
      "use": "official",
      "family": "Smith",
      "given": ["John", "Jacob"]
    }
  ],
  "gender": "male",
  "birthDate": "1980-01-15",
  "address": [
    {
      "use": "home",
      "line": ["123 Main St"],
      "city": "Anytown",
      "state": "CA",
      "postalCode": "12345"
    }
  ],
  "telecom": [
    {
      "system": "phone",
      "value": "555-555-5555",
      "use": "home"
    },
    {
      "system": "email",
      "value": "john.smith@example.com"
    }
  ],
  "active": true
}
```

**File: `test-data/patients/patient-us-core.json`**
```json
{
  "resourceType": "Patient",
  "meta": {
    "profile": [
      "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
    ]
  },
  "identifier": [
    {
      "system": "http://hospital.example.org/mrn",
      "value": "US-12345"
    }
  ],
  "name": [
    {
      "use": "official",
      "family": "USCorePatient",
      "given": ["Test"]
    }
  ],
  "gender": "female",
  "birthDate": "1985-06-20",
  "extension": [
    {
      "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race",
      "extension": [
        {
          "url": "ombCategory",
          "valueCoding": {
            "system": "urn:oid:2.16.840.1.113883.6.238",
            "code": "2106-3",
            "display": "White"
          }
        },
        {
          "url": "text",
          "valueString": "White"
        }
      ]
    },
    {
      "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity",
      "extension": [
        {
          "url": "ombCategory",
          "valueCoding": {
            "system": "urn:oid:2.16.840.1.113883.6.238",
            "code": "2186-5",
            "display": "Not Hispanic or Latino"
          }
        },
        {
          "url": "text",
          "valueString": "Not Hispanic or Latino"
        }
      ]
    }
  ]
}
```
