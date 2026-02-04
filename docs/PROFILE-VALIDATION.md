# FHIR Profile Validation

## Overview

The FHIR Profile Validation feature provides comprehensive validation of FHIR resources against StructureDefinitions and profiles using HAPI FHIR's validation engine. It supports multi-version validation (R5 and R4B) with configurable strictness levels, detailed logging, health monitoring, and performance metrics.

## Features

- **Multi-version Support**: Validates resources against FHIR R5 and R4B specifications
- **Configurable Validation Modes**: Strict, lenient, or disabled validation
- **Feature Flag Control**: Completely disable validator initialization for faster startup
- **Eager and Lazy Loading**: Choose between eager initialization at startup or lazy per-version initialization
- **Comprehensive Logging**: Detailed startup logging and optional validation operation logging
- **Health Monitoring**: Spring Boot Actuator health endpoint with configurable sensitivity
- **Performance Metrics**: Micrometer metrics for validation operations (always enabled)
- **Docker Optimization**: Validation disabled by default in Docker for faster startup

## Configuration

### Basic Configuration

Add to your `application.yml`:

```yaml
fhir4java:
  validation:
    enabled: true                           # Enable/disable validation entirely
    profile-validator-enabled: true         # Enable/disable ProfileValidator initialization
    profile-validation: strict              # Validation strictness: strict, lenient, off
    log-validation-operations: false        # Enable detailed validation logging
    lazy-initialization: false              # Enable lazy per-version initialization
    health-check-mode: strict               # Health check sensitivity: strict, warn, disabled
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `profile-validator-enabled` | `true` | Controls whether ProfileValidator initializes validators at startup. Set to `false` for faster startup when validation is not needed. |
| `profile-validation` | `lenient` | Validation strictness mode. `strict` rejects resources with errors, `lenient` allows warnings, `off` disables validation. |
| `log-validation-operations` | `false` | Enable detailed DEBUG logging of each validation operation (useful for debugging). |
| `lazy-initialization` | `false` | **Experimental**: Initialize validators on first use per FHIR version instead of at startup. |
| `health-check-mode` | `strict` | Controls health endpoint behavior when validators fail to initialize. See Health Check Modes below. |

### Validation Modes

#### Strict Mode
```yaml
profile-validation: strict
```
- Full validation performed
- Errors cause resource rejection
- Warnings logged but don't block operations
- Recommended for production environments with strict compliance requirements

#### Lenient Mode
```yaml
profile-validation: lenient
```
- Validation performed but results are advisory
- Errors and warnings logged but don't cause rejection
- Recommended for development and testing
- Default mode

#### Off Mode
```yaml
profile-validation: off
```
- No profile validation performed
- Fastest performance
- Not recommended for production

### Health Check Modes

#### Strict Mode (Default)
```yaml
health-check-mode: strict
```
- Reports `DOWN` if no validators initialized successfully
- Ensures operators know when validation is broken
- Recommended for production with monitoring

#### Warn Mode
```yaml
health-check-mode: warn
```
- Reports `UP` with warning details if no validators initialized
- Allows graceful degradation in containerized environments
- Recommended for Docker deployments

#### Disabled Mode
```yaml
health-check-mode: disabled
```
- Always reports `UP` regardless of validator status
- Use when validation is optional and shouldn't affect health checks

## Docker Configuration

In Docker environments, validation is disabled by default for faster container startup and reduced memory footprint.

`application-docker.yml`:
```yaml
fhir4java:
  validation:
    profile-validator-enabled: ${PROFILE_VALIDATOR_ENABLED:false}
    health-check-mode: ${HEALTH_CHECK_MODE:warn}
```

To enable validation in Docker:
```bash
docker run -e PROFILE_VALIDATOR_ENABLED=true your-image
```

Or in `docker-compose.yml`:
```yaml
environment:
  PROFILE_VALIDATOR_ENABLED: "true"
  HEALTH_CHECK_MODE: "strict"
```

## Lazy Initialization (Experimental)

Lazy initialization reduces startup time by initializing validators only when first used:

```yaml
fhir4java:
  validation:
    lazy-initialization: true
```

**Benefits:**
- Near-zero startup time for validator
- Validators created only for FHIR versions actually used
- Useful for services that only use specific FHIR versions

**Trade-offs:**
- First validation request per version will be slower
- Synchronization overhead on first use
- Still experimental - use with caution in production

## Health Endpoint

The ProfileValidator exposes health information via Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health/profileValidator
```

Response when enabled and healthy:
```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "totalInitializationTime": "2450ms",
    "successCount": 2,
    "totalCount": 2,
    "versions": {
      "r5": {
        "status": "initialized",
        "available": true,
        "initializationTime": "1230ms"
      },
      "r4b": {
        "status": "initialized",
        "available": true,
        "initializationTime": "1220ms"
      }
    }
  }
}
```

Response when disabled:
```json
{
  "status": "UP",
  "details": {
    "disabled": true,
    "reason": "Profile validator disabled by configuration"
  }
}
```

## Metrics

ProfileValidator automatically records Micrometer metrics (always enabled):

### Available Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `fhir.validation.attempts` | Counter | `version`, `result`, `resourceType` | Count of validation attempts |
| `fhir.validation.duration` | Timer | `version`, `resourceType` | Validation duration in milliseconds |

**Important**: Metrics are registered **eagerly** during ProfileValidator initialization, so they appear in `/actuator/metrics` immediately, even before any validation is performed.

### Viewing Metrics

Via Actuator metrics endpoint (uses dotted notation):
```bash
curl http://localhost:8080/actuator/metrics/fhir.validation.attempts
curl http://localhost:8080/actuator/metrics/fhir.validation.duration
```

List all available metrics:
```bash
curl http://localhost:8080/actuator/metrics | jq '.names | sort'
```

Via Prometheus (automatically converts to underscore notation):
```bash
curl http://localhost:8080/actuator/prometheus | grep fhir_validation
```

**Note**: 
- Actuator endpoints use **dotted notation** (`fhir.validation.*`)
- Prometheus export uses **underscore notation** (`fhir_validation_*`)
- Both formats are supported automatically by Spring Boot

### Grafana Dashboard

A pre-built Grafana dashboard template is available at `docs/grafana-validation-dashboard.json`.

**Dashboard Features:**
- Validation request rate by FHIR version
- Success/failure ratio visualization
- Duration percentiles (p50, p95, p99)
- Top 10 slowest resource types to validate
- Health status monitoring
- Failure rate tracking

**Import Instructions:**
1. Open Grafana
2. Go to Dashboards → Import
3. Upload `docs/grafana-validation-dashboard.json`
4. Configure your Prometheus data source
5. Save the dashboard

## Logging

### Startup Logging

ProfileValidator logs detailed information during initialization:

```
INFO  ProfileValidator - Starting ProfileValidator initialization
INFO  ProfileValidator - Initializing validator for FHIR r5...
INFO  ProfileValidator - Initialized validator for FHIR r5 in 1230ms
INFO  ProfileValidator - Initializing validator for FHIR r4b...
INFO  ProfileValidator - Initialized validator for FHIR r4b in 1220ms
INFO  ProfileValidator - ProfileValidator initialization complete: 2/2 versions initialized in 2450ms
```

When disabled:
```
INFO  ProfileValidator - Starting ProfileValidator initialization
INFO  ProfileValidator - Profile validation is disabled by configuration - ProfileValidator will not initialize validators
```

### Validation Operation Logging

Enable detailed validation operation logging:

```yaml
fhir4java:
  validation:
    log-validation-operations: true
```

Produces DEBUG logs:
```
DEBUG ProfileValidator - Validating Patient against base StructureDefinition
DEBUG ProfileValidator - Validation completed for Patient: 0 issues found
```

### Recommended Logging Levels

Development:
```yaml
logging:
  level:
    org.fhirframework.core.validation: DEBUG
```

Production:
```yaml
logging:
  level:
    org.fhirframework.core.validation: INFO
```

## Troubleshooting

### Error: NoSuchMethodError with TarArchiveInputStream

**Symptom:**
```
java.lang.NoSuchMethodError: 'org.apache.commons.compress.archivers.tar.TarArchiveEntry 
org.apache.commons.compress.archivers.tar.TarArchiveInputStream.getNextEntry()'
```

**Cause:** Missing or incompatible `commons-compress` dependency.

**Solution:** Ensure `commons-compress` version 1.26.0 or later is in your classpath. This is automatically included in `fhir4java-core` module.

### Error: No Cache Service Providers found

**Symptom:**
```
HAPI-2200: No Cache Service Providers found. Choose between hapi-fhir-caching-caffeine 
(Default) and hapi-fhir-caching-guava (Android)
```

**Cause:** Missing HAPI FHIR caching library.

**Solution:** Ensure `hapi-fhir-caching-caffeine` is in your classpath. This is automatically included in `fhir4java-core` module.

### Slow Startup Times

**Symptom:** Application takes 30+ seconds to start with validation enabled.

**Solutions:**
1. **Disable in Development:**
   ```yaml
   profile-validator-enabled: false
   ```

2. **Use Lazy Initialization:**
   ```yaml
   lazy-initialization: true
   ```

3. **Disable in Docker:**
   ```yaml
   PROFILE_VALIDATOR_ENABLED: false
   ```

### Validation Always Returns Success

**Symptom:** All resources pass validation even with obvious errors.

**Check:**
1. Is `profile-validator-enabled: true`?
2. Is `profile-validation` set to `off`?
3. Check logs for "Profile validation is disabled" messages
4. Check health endpoint for validator status

### Health Check Reports DOWN

**Symptom:** `/actuator/health` shows ProfileValidator as DOWN.

**Solutions:**
1. Check logs for initialization errors
2. Verify dependencies are correct
3. Change health-check-mode to `warn` for graceful degradation:
   ```yaml
   health-check-mode: warn
   ```

## Performance Tuning

### Startup Time Optimization

| Configuration | Startup Time | Memory Usage | Validation Available |
|---------------|--------------|--------------|---------------------|
| Eager loading (default) | ~2-3 seconds | High | Immediately |
| Lazy initialization | ~50ms | Low initially | On first use |
| Disabled | ~10ms | Minimal | Never |

### Memory Usage

Typical memory consumption per initialized validator:
- R5 validator: ~150-200 MB
- R4B validator: ~150-200 MB

**Recommendations:**
- Docker environments: Disable by default, enable selectively
- Development: Use lazy initialization or disable
- Production: Enable with eager loading for predictable performance

### Validation Performance

Average validation times:
- Simple resources (Patient, Observation): 50-100ms
- Complex resources (Bundle, large compositions): 200-500ms

**Optimization tips:**
1. Use lenient mode if strict validation isn't required
2. Cache validation results for frequently validated resources
3. Monitor metrics to identify slow resource types
4. Consider disabling terminology checks for better performance

## Migration Guide

### From No Validation

Add to `application.yml`:
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    profile-validation: lenient  # Start with lenient mode
    log-validation-operations: true  # Enable for initial debugging
```

### From Basic Validation

Existing applications using basic validation can enable new features incrementally:

1. **Add Health Monitoring:**
   ```yaml
   health-check-mode: strict
   ```

2. **Enable Metrics:**
   Metrics are automatically enabled when `spring-boot-starter-actuator` is present.

3. **Add Grafana Dashboard:**
   Import `docs/grafana-validation-dashboard.json`

4. **Optimize for Docker:**
   Add Docker-specific configuration to disable by default.

## Best Practices

1. **Production:** Enable with strict mode and eager loading
2. **Development:** Disable or use lazy initialization for faster feedback loops
3. **Docker:** Disable by default, enable via environment variables when needed
4. **Monitoring:** Always enable metrics, use Grafana dashboard for visibility
5. **Health Checks:** Use strict mode with monitoring, warn mode without
6. **Logging:** Keep operation logging disabled in production unless debugging
7. **Testing:** Use lenient mode to identify issues without blocking tests

## API Usage

### Programmatic Validation

```java
@Autowired
private ProfileValidator profileValidator;

public void validateResource(IBaseResource resource, FhirVersion version) {
    ValidationResult result = profileValidator.validateResource(resource, version);
    
    if (!result.isSuccessful()) {
        for (ValidationIssue issue : result.getIssues()) {
            log.warn("Validation issue: {}", issue.getDiagnostics());
        }
    }
}
```

### Check Validator Availability

```java
if (!profileValidator.isValidatorEnabled()) {
    log.warn("Profile validation is disabled");
    return;
}

if (profileValidator.isValidationAvailable(FhirVersion.R5)) {
    // R5 validator is available
}
```

### Get Initialization Status

```java
Map<FhirVersion, Boolean> status = profileValidator.getInitializationStatus();
Map<FhirVersion, Long> durations = profileValidator.getInitializationDurations();
long totalTime = profileValidator.getTotalInitializationTime();
```

## Support

For issues or questions:
1. Check logs for detailed error messages
2. Review this documentation
3. Check health endpoint status
4. Review metrics for validation patterns
5. Enable debug logging for detailed troubleshooting

## Future Enhancements

Planned features for future releases:
- Custom profile loading from file system or URLs
- Validation result caching
- Async validation for large batches
- Per-resource-type validation configuration
- Integration with terminology servers for code validation
