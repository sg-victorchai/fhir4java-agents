# ProfileValidator Architecture Diagrams

## Overview
This document provides visual representations of the ProfileValidator architecture and component interactions across all 4 implementation phases.

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    FHIR4Java Server                              │
│                                                                   │
│  ┌───────────────────────────────────────────────────────┐      │
│  │           FhirResourceController                       │      │
│  │  (HTTP POST /fhir/{version}/{resourceType})           │      │
│  └───────────────┬───────────────────────────────────────┘      │
│                  │                                               │
│                  ▼                                               │
│  ┌───────────────────────────────────────────────────────┐      │
│  │         FhirExceptionHandler                          │      │
│  │  • DataFormatException → HTTP 422                     │      │
│  │  • FhirException → HTTP 422/404/500                  │      │
│  │  • Generic Exception → HTTP 500                       │      │
│  └───────────────────────────────────────────────────────┘      │
│                                                                   │
│  ┌───────────────────────────────────────────────────────┐      │
│  │         PluginOrchestrator                            │      │
│  │  executeBefore() → Validation Plugin                  │      │
│  └───────────────┬───────────────────────────────────────┘      │
│                  │                                               │
│                  ▼                                               │
│  ┌───────────────────────────────────────────────────────┐      │
│  │         ProfileValidator                              │      │
│  │  • validateResource()                                 │      │
│  │  • validateAgainstRequiredProfiles()                  │      │
│  │  • registerMetricsEagerly()                           │      │
│  └─────┬──────────────────────────┬──────────────────────┘      │
│        │                          │                             │
│        ▼                          ▼                             │
│  ┌──────────────┐       ┌──────────────────────┐              │
│  │ HAPI FHIR    │       │ Micrometer           │              │
│  │ Validators   │       │ Metrics              │              │
│  │ • R5         │       │ • Counters           │              │
│  │ • R4B        │       │ • Timers             │              │
│  └──────────────┘       └──────────────────────┘              │
│                                                                   │
│  ┌───────────────────────────────────────────────────────┐      │
│  │         Spring Boot Actuator                          │      │
│  │  • /actuator/health/profileValidator                  │      │
│  │  • /actuator/metrics/fhir.validation.*               │      │
│  │  • /actuator/prometheus                               │      │
│  └───────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1: Core Implementation - Component Flow

```
┌──────────────────────────────────────────────────────────────┐
│  Application Startup                                          │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
        ┌──────────────────────┐
        │  ValidationConfig    │
        │  @Configuration      │
        │                      │
        │  Properties:         │
        │  • enabled           │
        │  • lazy-init         │
        │  • health-check-mode │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────────────┐
        │  ProfileValidator            │
        │  @PostConstruct initialize() │
        └──────────┬───────────────────┘
                   │
                   ├─────────────────────────────┐
                   │                             │
                   ▼                             ▼
    ┌──────────────────────┐      ┌──────────────────────┐
    │ Dependency Check     │      │  Per-Version Init    │
    │ ✓ commons-compress   │      │  • R5 Validator      │
    │ ✓ hapi-fhir-caching  │      │  • R4B Validator     │
    └──────────────────────┘      └──────────┬───────────┘
                                              │
                                              ▼
                                  ┌────────────────────────┐
                                  │ HAPI FHIR Components   │
                                  │ • ValidationSupport    │
                                  │ • CachingSupport       │
                                  │ • InstanceValidator    │
                                  └────────────────────────┘
```

---

## 3. Phase 2: Health Indicator Integration

```
┌─────────────────────────────────────────────────────────┐
│  Spring Boot Actuator Request                           │
│  GET /actuator/health/profileValidator                  │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
     ┌──────────────────────────────┐
     │ ProfileValidatorHealthIndicator │
     │ implements HealthIndicator      │
     └──────────────┬──────────────────┘
                    │
                    ├─── Check: validatorEnabled
                    │
                    ├─── Check: initializationStatus
                    │
                    └─── Check: healthCheckMode
                             │
                             ├─── STRICT: DOWN if no validators
                             ├─── WARN: UP with warning details
                             └─── DISABLED: Always UP
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │  Health Response            │
                    │  {                          │
                    │    "status": "UP",          │
                    │    "details": {             │
                    │      "enabled": true,       │
                    │      "versions": {...}      │
                    │    }                        │
                    │  }                          │
                    └─────────────────────────────┘
```

---

## 4. Phase 3: Metrics Collection Flow

```
┌──────────────────────────────────────────────────────────┐
│  Startup: Eager Metrics Registration                     │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
     ┌───────────────────────────────┐
     │  registerMetricsEagerly()     │
     │  • Register counters          │
     │  • Register timers            │
     │  • Use dummy tags             │
     └────────────┬──────────────────┘
                  │
                  ▼
     ┌────────────────────────────────────┐
     │  MeterRegistry                     │
     │  • fhir.validation.attempts        │
     │  • fhir.validation.duration        │
     └──────────┬─────────────────────────┘
                │
                ├────────────────────────────────┐
                │                                │
                ▼                                ▼
    ┌─────────────────────┐      ┌──────────────────────────┐
    │ Actuator Endpoint   │      │ Prometheus Export        │
    │ /actuator/metrics/  │      │ /actuator/prometheus     │
    │ fhir.validation.*   │      │ fhir_validation_*        │
    │ (dotted notation)   │      │ (underscore notation)    │
    └─────────────────────┘      └──────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│  Runtime: Validation Metrics Recording                   │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
     ┌───────────────────────────────┐
     │  validateResource()           │
     │  1. Validate resource         │
     │  2. Calculate duration        │
     │  3. Record metrics            │
     └────────────┬──────────────────┘
                  │
                  ▼
     ┌────────────────────────────────────┐
     │  recordValidationMetrics()         │
     │  • Counter.increment()             │
     │    - tags: version, result, type   │
     │  • Timer.record(duration)          │
     │    - tags: version, type           │
     └────────────────────────────────────┘
```

---

## 5. Phase 4: HTTP 422 Error Handling Flow

```
┌─────────────────────────────────────────────────────────┐
│  Client Request with Invalid Data                       │
│  POST /fhir/r5/Patient                                  │
│  {"resourceType":"Patient","gender":"02"}               │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
     ┌───────────────────────────────┐
     │  FhirResourceController       │
     │  createVersioned()            │
     └────────────┬──────────────────┘
                  │
                  ├─── Try: Parse JSON
                  │         │
                  │         ▼
                  │    ┌──────────────────────┐
                  │    │ HAPI FHIR Parser     │
                  │    │ ctx.newJsonParser()  │
                  │    └──────┬───────────────┘
                  │           │
                  │           ├─── Invalid enum value
                  │           │    (e.g., gender="02")
                  │           │
                  │           ▼
                  │    ┌──────────────────────┐
                  │    │ DataFormatException  │
                  │    │ thrown               │
                  │    └──────┬───────────────┘
                  │           │
                  ▼           ▼
     ┌─────────────────────────────────────────┐
     │  FhirExceptionHandler                   │
     │  @RestControllerAdvice                  │
     └────────────┬────────────────────────────┘
                  │
                  ├─── @ExceptionHandler(DataFormatException)
                  │         │
                  │         ▼
                  │    ┌──────────────────────────────┐
                  │    │ handleDataFormatException()  │
                  │    │ • Build OperationOutcome     │
                  │    │ • Return HTTP 422            │
                  │    └──────────────────────────────┘
                  │
                  ├─── @ExceptionHandler(FhirException)
                  │         │
                  │         ▼
                  │    ┌──────────────────────────────┐
                  │    │ handleFhirException()        │
                  │    │ • mapToHttpStatus()          │
                  │    │   - invalid → 422            │
                  │    │   - structure → 422          │
                  │    │   - required → 422           │
                  │    │ • Build OperationOutcome     │
                  │    └──────────────────────────────┘
                  │
                  └─── @ExceptionHandler(Exception)
                            │
                            ▼
                       ┌──────────────────────────────┐
                       │ handleGenericException()     │
                       │ • Return HTTP 500            │
                       │ • Last resort handler        │
                       └──────────────────────────────┘
                            │
                            ▼
     ┌─────────────────────────────────────────────┐
     │  HTTP Response                              │
     │  HTTP/1.1 422 Unprocessable Entity          │
     │  {                                          │
     │    "resourceType": "OperationOutcome",      │
     │    "issue": [{                              │
     │      "severity": "error",                   │
     │      "code": "structure",                   │
     │      "diagnostics": "Unknown code '02'"     │
     │    }]                                       │
     │  }                                          │
     └─────────────────────────────────────────────┘
```

---

## 6. Complete Validation Request Flow (All Phases)

```
┌──────────────────────────────────────────────────────────────┐
│  Client                                                       │
└────────────┬─────────────────────────────────────────────────┘
             │
             │ POST /fhir/r5/Patient
             │ {"resourceType":"Patient",...}
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│  FhirResourceController                                      │
│  ┌────────────────────────────────────────────────────┐     │
│  │ 1. Parse JSON (may throw DataFormatException)      │     │
│  │ 2. Build PluginContext                             │     │
│  │ 3. Execute BEFORE plugins                          │     │
│  └────────────┬───────────────────────────────────────┘     │
└───────────────┼─────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│  PluginOrchestrator                                          │
│  ┌────────────────────────────────────────────────────┐     │
│  │ executeBefore(context)                             │     │
│  │  → ValidationPlugin (if enabled)                   │     │
│  └────────────┬───────────────────────────────────────┘     │
└───────────────┼─────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────┐
│  ProfileValidator                                            │
│  ┌────────────────────────────────────────────────────┐     │
│  │ validateResource(resource, version)                │     │
│  │                                                     │     │
│  │ IF validator disabled:                             │     │
│  │   → Return success (no validation)                 │     │
│  │                                                     │     │
│  │ IF validator enabled:                              │     │
│  │   1. Get/Initialize validator for version         │     │
│  │   2. Validate against HAPI FHIR                   │     │
│  │   3. Convert results to ValidationResult          │     │
│  │   4. Record metrics (Counter + Timer)             │     │
│  │   5. Return validation result                     │     │
│  └────────────┬───────────────────────────────────────┘     │
└───────────────┼─────────────────────────────────────────────┘
                │
                ├─── Metrics Update
                │         │
                │         ▼
                │    ┌──────────────────────────┐
                │    │ MeterRegistry            │
                │    │ • fhir.validation.       │
                │    │   attempts++             │
                │    │ • fhir.validation.       │
                │    │   duration.record()      │
                │    └──────────────────────────┘
                │
                ├─── Validation Success
                │         │
                │         ▼
                │    ┌──────────────────────────┐
                │    │ Continue Processing      │
                │    │ • Save to database       │
                │    │ • Execute AFTER plugins  │
                │    │ • Return HTTP 201        │
                │    └──────────────────────────┘
                │
                └─── Validation Failure
                          │
                          ▼
                     ┌──────────────────────────┐
                     │ Throw FhirException      │
                     │ issueCode: "invalid"     │
                     └──────┬───────────────────┘
                            │
                            ▼
                     ┌──────────────────────────┐
                     │ FhirExceptionHandler     │
                     │ • mapToHttpStatus()      │
                     │   invalid → 422          │
                     │ • Build OperationOutcome │
                     │ • Return HTTP 422        │
                     └──────────────────────────┘
```

---

## 7. Docker Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Docker Compose Environment                                  │
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │  fhir4java-server Container                      │       │
│  │                                                   │       │
│  │  Environment Variables:                          │       │
│  │  • PROFILE_VALIDATOR_ENABLED=false (default)    │       │
│  │  • HEALTH_CHECK_MODE=warn                       │       │
│  │                                                   │       │
│  │  ┌────────────────────────────────────────┐     │       │
│  │  │ Spring Profile: docker                 │     │       │
│  │  │ Config: application-docker.yml         │     │       │
│  │  │                                         │     │       │
│  │  │ Overrides:                             │     │       │
│  │  │ • profile-validator-enabled: false     │     │       │
│  │  │   (Faster startup ~2-3s)              │     │       │
│  │  │ • health-check-mode: warn              │     │       │
│  │  │   (Graceful degradation)              │     │       │
│  │  └────────────────────────────────────────┘     │       │
│  │                                                   │       │
│  │  Result:                                         │       │
│  │  ✓ Fast container startup                       │       │
│  │  ✓ Reduced memory footprint (~300-400MB saved)  │       │
│  │  ✓ Health checks still pass                     │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │  postgres Container                              │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │  redis Container                                 │       │
│  └──────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

---

## 8. Configuration Cascade

```
┌─────────────────────────────────────────────────────┐
│  Configuration Loading Priority                      │
│  (Higher number = Higher priority)                  │
└─────────────────────────────────────────────────────┘

Priority 1 (Lowest):
┌────────────────────────────────┐
│  application.yml               │
│  • Base configuration          │
│  • profile-validator-enabled:  │
│    true (default)              │
└────────────────────────────────┘
            ↓ Merged with
Priority 2:
┌────────────────────────────────┐
│  application-docker.yml        │
│  • Active when docker profile  │
│  • profile-validator-enabled:  │
│    ${ENV:false}                │
│  • OVERRIDES base config       │
└────────────────────────────────┘
            ↓ Merged with
Priority 3 (Highest):
┌────────────────────────────────┐
│  Environment Variables         │
│  • PROFILE_VALIDATOR_ENABLED   │
│  • HEALTH_CHECK_MODE           │
│  • OVERRIDES all configs       │
└────────────────────────────────┘
            ↓
      Final Configuration
```

---

## 9. Metrics Architecture

```
┌──────────────────────────────────────────────────────┐
│  Metrics Collection & Export                         │
└──────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│  ProfileValidator                                   │
│  • registerMetricsEagerly() at startup             │
│  • recordValidationMetrics() at runtime            │
└────────────┬───────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────┐
│  Micrometer MeterRegistry                          │
│  • In-memory metric storage                        │
│  • Tags: version, result, resourceType             │
└────────────┬───────────────────────────────────────┘
             │
             ├──────────────────┬──────────────────┐
             │                  │                  │
             ▼                  ▼                  ▼
┌──────────────────┐  ┌─────────────────┐  ┌────────────┐
│ Actuator         │  │ Prometheus      │  │ Grafana    │
│ /metrics         │  │ /prometheus     │  │ Dashboard  │
│ (JSON)           │  │ (Text format)   │  │ (Visual)   │
│                  │  │                  │  │            │
│ Dotted notation: │  │ Underscore:     │  │ Query:     │
│ fhir.validation  │  │ fhir_validation │  │ rate()     │
│ .attempts        │  │ _attempts_total │  │ histogram()│
└──────────────────┘  └─────────────────┘  └────────────┘
```

---

## 10. Component Dependencies

```
┌─────────────────────────────────────────────────────┐
│  Dependency Graph                                    │
└─────────────────────────────────────────────────────┘

ProfileValidator
    ├── FhirContextFactory (required)
    ├── ResourceRegistry (required)
    ├── ValidationConfig (required)
    └── MeterRegistry (optional)
           ↓
    FhirContext (per version)
           ├── DefaultProfileValidationSupport
           ├── InMemoryTerminologyServerValidationSupport
           ├── CommonCodeSystemsTerminologyService
           └── CachingValidationSupport
                  └── commons-compress 1.26.0+ (required)
                  └── hapi-fhir-caching-caffeine (required)

ProfileValidatorHealthIndicator
    ├── ProfileValidator (required)
    └── ValidationConfig (required)

FhirExceptionHandler
    ├── FhirContextFactory (required)
    └── Spring @RestControllerAdvice

PluginOrchestrator
    └── ProfileValidator (via ValidationPlugin)
```

---

## Legend

```
┌─────────┐
│ Component│  = System Component
└─────────┘

    ↓        = Data/Control Flow
    
    →        = Transformation/Mapping

┌───────────┐
│ Decision  │  = Decision Point
└───────────┘

✓            = Success/Validation Pass
✗            = Failure/Validation Fail
```

---

**Document Version**: 1.0  
**Last Updated**: February 4, 2026  
**Status**: Complete - All 4 Phases Documented
