# ProfileValidator Troubleshooting Flowcharts

## Quick Navigation
1. [ProfileValidator Initialization Issues](#1-profilevalidator-initialization-issues)
2. [Health Endpoint 404 Error](#2-health-endpoint-404-error)
3. [Metrics Not Visible](#3-metrics-not-visible)
4. [HTTP 500 Instead of 422](#4-http-500-instead-of-422)
5. [Validation Not Working](#5-validation-not-working)
6. [Docker Configuration Issues](#6-docker-configuration-issues)
7. [Performance Issues](#7-performance-issues)
8. [Dependency Conflicts](#8-dependency-conflicts)

---

## 1. ProfileValidator Initialization Issues

```
START: Application fails to start or logs show ProfileValidator errors
    │
    ▼
Does the error mention "NoSuchMethodError"?
    │
    ├─YES─▶ Check commons-compress version
    │        │
    │        ├─▶ Version < 1.26.0?
    │        │    │
    │        │    ├─YES─▶ UPDATE to 1.26.0+
    │        │    │        │
    │        │    │        └─▶ Run: ./mvnw clean install
    │        │    │             │
    │        │    │             └─▶ RESOLVED ✓
    │        │    │
    │        │    └─NO──▶ Check dependency:tree for conflicts
    │        │             │
    │        │             └─▶ Run: ./mvnw dependency:tree | grep commons-compress
    │        │                  │
    │        │                  └─▶ Exclude conflicting versions in pom.xml
    │        │
    │        └─▶ Version missing?
    │             │
    │             └─YES─▶ Add to pom.xml:
    │                      <dependency>
    │                        <groupId>org.apache.commons</groupId>
    │                        <artifactId>commons-compress</artifactId>
    │                        <version>1.26.0</version>
    │                      </dependency>
    │                      │
    │                      └─▶ RESOLVED ✓
    │
    └─NO─▶ Does error mention "No Cache Service Providers"?
           │
           ├─YES─▶ Add hapi-fhir-caching-caffeine dependency
           │        │
           │        └─▶ Add to pom.xml:
           │             <dependency>
           │               <groupId>ca.uhn.hapi.fhir</groupId>
           │               <artifactId>hapi-fhir-caching-caffeine</artifactId>
           │             </dependency>
           │             │
           │             └─▶ Run: ./mvnw clean install
           │                  │
           │                  └─▶ RESOLVED ✓
           │
           └─NO─▶ Check logs for other errors
                  │
                  └─▶ Search for: "ProfileValidator initialization"
                       │
                       ├─▶ Found "initialization complete"?
                       │    │
                       │    └─YES─▶ Initialization successful ✓
                       │
                       └─▶ Found specific version error?
                            │
                            └─YES─▶ Check enabled FHIR versions in config
                                     │
                                     └─▶ Verify: fhir4java.versions.enabled
                                          │
                                          └─▶ RESOLVED ✓
```

---

## 2. Health Endpoint 404 Error

```
START: GET /actuator/health/profileValidator returns 404
    │
    ▼
Is /actuator/health accessible?
    │
    ├─NO──▶ Check actuator configuration
    │        │
    │        └─▶ Verify in application.yml:
    │             management:
    │               endpoints:
    │                 web:
    │                   exposure:
    │                     include: health
    │             │
    │             ├─▶ Missing?
    │             │    │
    │             │    └─▶ ADD configuration
    │             │         │
    │             │         └─▶ Restart application
    │             │              │
    │             │              └─▶ RESOLVED ✓
    │             │
    │             └─▶ Present?
    │                  │
    │                  └─▶ Check spring-boot-actuator dependency
    │                       │
    │                       └─▶ RESOLVED ✓
    │
    └─YES─▶ Check show-components setting
             │
             └─▶ In application.yml:
                  management:
                    endpoint:
                      health:
                        show-components: always
                  │
                  ├─▶ Missing or set to "never"?
                  │    │
                  │    └─▶ SET to "always"
                  │         │
                  │         └─▶ Restart application
                  │              │
                  │              └─▶ Test: curl /actuator/health/profileValidator
                  │                   │
                  │                   └─▶ Returns 200? ✓ RESOLVED
                  │
                  └─▶ Already set to "always"?
                       │
                       └─▶ Check if ProfileValidatorHealthIndicator exists
                            │
                            ├─▶ File missing?
                            │    │
                            │    └─▶ CREATE file or check compilation errors
                            │         │
                            │         └─▶ RESOLVED ✓
                            │
                            └─▶ File exists?
                                 │
                                 └─▶ Check @Component annotation
                                      │
                                      └─▶ RESOLVED ✓
```

---

## 3. Metrics Not Visible

```
START: /actuator/metrics doesn't show fhir.validation.* metrics
    │
    ▼
Is /actuator/metrics accessible?
    │
    ├─NO──▶ Check actuator metrics configuration
    │        │
    │        └─▶ Same as Health Endpoint troubleshooting
    │             │
    │             └─▶ RESOLVED ✓
    │
    └─YES─▶ Search for "fhir" in metrics list
             │
             └─▶ curl /actuator/metrics | jq '.names[] | select(contains("fhir"))'
                  │
                  ├─▶ No results?
                  │    │
                  │    ├─▶ Check if MeterRegistry is injected
                  │    │    │
                  │    │    └─▶ Look for log: "MeterRegistry is null"
                  │    │         │
                  │    │         ├─YES─▶ Add micrometer-core dependency
                  │    │         │       │
                  │    │         │       └─▶ RESOLVED ✓
                  │    │         │
                  │    │         └─NO──▶ Continue below
                  │    │
                  │    └─▶ Check for eager registration
                  │         │
                  │         └─▶ Look for log: "Eagerly registered validation metrics"
                  │              │
                  │              ├─YES─▶ Metrics should be visible
                  │              │       │
                  │              │       └─▶ Check metric name spelling
                  │              │            │
                  │              │            └─▶ Use: fhir.validation.attempts
                  │              │                 (dotted, not underscore)
                  │              │                 │
                  │              │                 └─▶ RESOLVED ✓
                  │              │
                  │              └─NO──▶ Check if validator is enabled
                  │                      │
                  │                      └─▶ profile-validator-enabled: true?
                  │                           │
                  │                           ├─NO─▶ SET to true
                  │                           │      │
                  │                           │      └─▶ RESOLVED ✓
                  │                           │
                  │                           └─YES─▶ Check registerMetricsEagerly() method
                  │                                    │
                  │                                    └─▶ Method exists?
                  │                                         │
                  │                                         └─▶ ADD if missing
                  │                                              │
                  │                                              └─▶ RESOLVED ✓
                  │
                  └─▶ Found fhir_validation_* (underscore)?
                       │
                       └─▶ WRONG! Should be fhir.validation.* (dotted)
                            │
                            └─▶ Fix metric names in ProfileValidator.java
                                 │
                                 └─▶ RESOLVED ✓
```

---

## 4. HTTP 500 Instead of 422

```
START: Invalid resource data returns HTTP 500
    │
    ▼
What type of validation error?
    │
    ├─▶ Invalid enum value (e.g., gender="99")
    │    │
    │    └─▶ Is DataFormatException handler present?
    │         │
    │         ├─NO─▶ ADD to FhirExceptionHandler.java:
    │         │      @ExceptionHandler(DataFormatException.class)
    │         │      public ResponseEntity<String> handleDataFormatException(...)
    │         │      │
    │         │      └─▶ Return HttpStatus.UNPROCESSABLE_ENTITY (422)
    │         │           │
    │         │           └─▶ RESOLVED ✓
    │         │
    │         └─YES─▶ Check HTTP status mapping
    │                  │
    │                  └─▶ Continue below
    │
    └─▶ Profile validation failure
         │
         └─▶ Check mapToHttpStatus() in FhirExceptionHandler
              │
              └─▶ Does "invalid" map to 422?
                   │
                   ├─NO─▶ UPDATE mapToHttpStatus():
                   │      case "invalid", "structure", "required" ->
                   │        HttpStatus.UNPROCESSABLE_ENTITY;
                   │      │
                   │      └─▶ RESOLVED ✓
                   │
                   └─YES─▶ Check if FhirException is thrown correctly
                            │
                            └─▶ Verify issueCode = "invalid"
                                 │
                                 └─▶ RESOLVED ✓

Alternative Path:
    │
    └─▶ Still returning 500?
         │
         └─▶ Check logs for actual exception type
              │
              ├─▶ Generic Exception caught?
              │    │
              │    └─▶ Add specific handler BEFORE generic handler
              │         │
              │         └─▶ RESOLVED ✓
              │
              └─▶ DataFormatException not caught?
                   │
                   └─▶ Verify handler is in @RestControllerAdvice class
                        │
                        └─▶ RESOLVED ✓
```

---

## 5. Validation Not Working

```
START: Resources not being validated (all resources accepted)
    │
    ▼
Is profile-validator-enabled: true?
    │
    ├─NO──▶ SET to true in application.yml
    │        │
    │        └─▶ Restart application
    │             │
    │             └─▶ RESOLVED ✓
    │
    └─YES─▶ Check logs for "Profile validation is disabled"
             │
             ├─YES─▶ Check active Spring profile
             │        │
             │        └─▶ Is "docker" profile active?
             │             │
             │             ├─YES─▶ Docker profile overrides config
             │             │       │
             │             │       └─▶ SET environment variable:
             │             │            PROFILE_VALIDATOR_ENABLED=true
             │             │            │
             │             │            └─▶ RESOLVED ✓
             │             │
             │             └─NO──▶ Check application.yml directly
             │                     │
             │                     └─▶ Verify property name spelling
             │                          │
             │                          └─▶ RESOLVED ✓
             │
             └─NO─▶ Check profile-validation mode
                    │
                    └─▶ Set to "off"?
                         │
                         ├─YES─▶ CHANGE to "strict" or "lenient"
                         │       │
                         │       └─▶ RESOLVED ✓
                         │
                         └─NO──▶ Check if validators initialized
                                  │
                                  └─▶ Look for: "ProfileValidator initialization complete"
                                       │
                                       ├─YES─▶ Check ValidationResult handling
                                       │       │
                                       │       └─▶ Verify validation errors are processed
                                       │            │
                                       │            └─▶ RESOLVED ✓
                                       │
                                       └─NO──▶ See "Initialization Issues" flowchart
                                               │
                                               └─▶ RESOLVED ✓
```

---

## 6. Docker Configuration Issues

```
START: Validation behavior different in Docker
    │
    ▼
Is validation enabled in Docker?
    │
    ├─NO──▶ EXPECTED: Default Docker behavior disables validation
    │        │
    │        └─▶ To enable:
    │             │
    │             ├─▶ Option 1: docker-compose.yml
    │             │    environment:
    │             │      - PROFILE_VALIDATOR_ENABLED=true
    │             │    │
    │             │    └─▶ docker-compose up --force-recreate
    │             │         │
    │             │         └─▶ RESOLVED ✓
    │             │
    │             ├─▶ Option 2: docker run
    │             │    docker run -e PROFILE_VALIDATOR_ENABLED=true ...
    │             │    │
    │             │    └─▶ RESOLVED ✓
    │             │
    │             └─▶ Option 3: Modify application-docker.yml
    │                  profile-validator-enabled: true
    │                  │
    │                  └─▶ Rebuild image
    │                       │
    │                       └─▶ RESOLVED ✓
    │
    └─YES─▶ Slower startup in Docker with validation enabled?
             │
             └─▶ EXPECTED: Validation adds ~2-3 seconds + ~300-400MB memory
                  │
                  ├─▶ Unacceptable?
                  │    │
                  │    └─▶ Consider:
                  │         ├─▶ Use lazy-initialization: true
                  │         │    │
                  │         │    └─▶ OPTIMIZED ✓
                  │         │
                  │         └─▶ Or disable for dev: PROFILE_VALIDATOR_ENABLED=false
                  │              │
                  │              └─▶ FASTER STARTUP ✓
                  │
                  └─▶ Acceptable?
                       │
                       └─▶ No action needed ✓
```

---

## 7. Performance Issues

```
START: Application slow or high memory usage
    │
    ▼
Is ProfileValidator enabled?
    │
    ├─NO──▶ Performance issue is elsewhere
    │        │
    │        └─▶ Check other components
    │
    └─YES─▶ What's the issue?
             │
             ├─▶ Slow startup (~30+ seconds)
             │    │
             │    ├─▶ Need validation?
             │    │    │
             │    │    ├─NO─▶ DISABLE: profile-validator-enabled: false
             │    │    │      │
             │    │    │      └─▶ Startup < 5 seconds ✓
             │    │    │
             │    │    └─YES─▶ Use lazy initialization
             │    │             lazy-initialization: true
             │    │             │
             │    │             └─▶ Validators load on first use ✓
             │    │
             │    └─▶ Check initialization logs
             │         │
             │         └─▶ One version taking too long?
             │              │
             │              └─▶ Disable that version in config
             │                   │
             │                   └─▶ FASTER ✓
             │
             ├─▶ High memory usage (~500MB+ increase)
             │    │
             │    └─▶ Each validator uses ~150-200MB
             │         │
             │         ├─▶ Can't reduce memory?
             │         │    │
             │         │    └─▶ Consider disabling unused FHIR versions
             │         │         │
             │         │         └─▶ REDUCED MEMORY ✓
             │         │
             │         └─▶ Must support multiple versions?
             │              │
             │              └─▶ Use lazy-initialization: true
             │                   (Memory allocated only when version used)
             │                   │
             │                   └─▶ OPTIMIZED ✓
             │
             └─▶ Slow validation response (>1 second)
                  │
                  └─▶ Check metrics:
                       curl /actuator/metrics/fhir.validation.duration
                       │
                       ├─▶ Avg duration > 1s?
                       │    │
                       │    ├─▶ Set profile-validation: lenient
                       │    │    (Skips some checks)
                       │    │    │
                       │    │    └─▶ FASTER ✓
                       │    │
                       │    └─▶ Or disable terminology checks
                       │         (Advanced config in HAPI FHIR)
                       │         │
                       │         └─▶ OPTIMIZED ✓
                       │
                       └─▶ Avg duration < 500ms?
                            │
                            └─▶ Performance acceptable ✓
```

---

## 8. Dependency Conflicts

```
START: Dependency resolution errors or version conflicts
    │
    ▼
Run: ./mvnw dependency:tree
    │
    ▼
Look for conflicts:
    │
    ├─▶ Multiple commons-compress versions?
    │    │
    │    └─▶ In pom.xml, exclude older versions:
    │         <dependency>
    │           <groupId>...</groupId>
    │           <artifactId>...</artifactId>
    │           <exclusions>
    │             <exclusion>
    │               <groupId>org.apache.commons</groupId>
    │               <artifactId>commons-compress</artifactId>
    │             </exclusion>
    │           </exclusions>
    │         </dependency>
    │         │
    │         └─▶ Add explicit version in dependencyManagement
    │              │
    │              └─▶ RESOLVED ✓
    │
    ├─▶ HAPI FHIR version mismatches?
    │    │
    │    └─▶ Ensure all HAPI dependencies use same version:
    │         - hapi-fhir-base
    │         - hapi-fhir-structures-r5
    │         - hapi-fhir-validation
    │         - hapi-fhir-caching-caffeine
    │         │
    │         └─▶ Use BOM or explicit version property
    │              │
    │              └─▶ RESOLVED ✓
    │
    └─▶ Spring Boot version conflicts?
         │
         └─▶ Check Spring Boot version compatibility:
              - Spring Boot 3.x requires Java 17+
              - HAPI FHIR 7.x compatible with Spring Boot 3.x
              │
              └─▶ Align versions in pom.xml
                   │
                   └─▶ RESOLVED ✓
```

---

## Quick Diagnostic Commands

```bash
# Check if server is running
curl -f http://localhost:8080/actuator/health

# Check ProfileValidator health
curl http://localhost:8080/actuator/health/profileValidator | jq

# List all metrics
curl http://localhost:8080/actuator/metrics | jq '.names[] | select(contains("fhir"))'

# Test validation with invalid data
curl -X POST http://localhost:8080/fhir/r5/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Patient","gender":"invalid"}' -v

# Check dependency tree
./mvnw dependency:tree | grep -E "commons-compress|hapi-fhir"

# Check Docker logs
docker-compose logs fhir4java-server | grep -i "profilevalidator\|error\|exception"

# Verify enabled FHIR versions
curl http://localhost:8080/actuator/configprops | jq '.fhir4java.validation'
```

---

## Common Error Messages & Solutions

| Error Message | Solution | Flowchart |
|---------------|----------|-----------|
| `NoSuchMethodError: TarArchiveInputStream.getNextEntry()` | Update commons-compress to 1.26.0+ | #1 |
| `No Cache Service Providers found` | Add hapi-fhir-caching-caffeine dependency | #1 |
| `404 on /actuator/health/profileValidator` | Add `show-components: always` | #2 |
| `Metrics not found: fhir.validation.attempts` | Check eager registration & metric names | #3 |
| `HTTP 500 for invalid gender code` | Add DataFormatException handler | #4 |
| `Profile validation is disabled` | Check config & Docker environment | #5, #6 |
| `Slow startup with validation` | Use lazy-initialization or disable | #7 |
| `Dependency convergence error` | Exclude conflicts & use dependencyManagement | #8 |

---

## Decision Tree: Which Flowchart to Use?

```
What's your issue?
    │
    ├─▶ Application won't start → Flowchart #1
    ├─▶ Health endpoint issues → Flowchart #2
    ├─▶ Metrics issues → Flowchart #3
    ├─▶ Wrong HTTP status codes → Flowchart #4
    ├─▶ Validation not working → Flowchart #5
    ├─▶ Docker-specific issues → Flowchart #6
    ├─▶ Performance/Memory → Flowchart #7
    └─▶ Dependency errors → Flowchart #8
```

---

**Document Version**: 1.0  
**Last Updated**: February 4, 2026  
**Coverage**: All 4 Phases + Common Issues
