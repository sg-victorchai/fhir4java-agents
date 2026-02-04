# Quick Testing Guide - ProfileValidator Fix

## ✅ Phase 2 Fixes Applied (February 4, 2026)

### Summary of Fixes
Three issues discovered during initial testing have been resolved:

1. **Health Endpoint 404** - Fixed by adding `show-components: always` to application.yml
2. **Metrics Endpoint 404** - Fixed by changing metric names to underscore notation
3. **Docker Configuration** - Clarified with comprehensive documentation

### What Changed
- **application.yml**: Added `show-components: always` to management configuration
- **ProfileValidator.java**: Changed metric names from `fhir.validation.*` to `fhir_validation_*`
- **application-docker.yml**: Added detailed comments explaining property precedence
- **PROFILE-VALIDATION.md**: Updated with corrected metric names and troubleshooting info

---

## Step-by-Step Verification

### 1. Build the Project
```bash
cd /Users/victorchai/app-dev/eclipse-workspace/fhir4java-agents
./mvnw clean install -DskipTests
```

Expected: Build should complete successfully without errors.

### 2. Start the Application (Local)
```bash
cd fhir4java-server
../mvnw spring-boot:run
```

**Check Startup Logs** - Look for:
```
INFO  ProfileValidator - Starting ProfileValidator initialization
INFO  ProfileValidator - Initializing validator for FHIR r5...
INFO  ProfileValidator - Initialized validator for FHIR r5 in XXXms
INFO  ProfileValidator - Initializing validator for FHIR r4b...
INFO  ProfileValidator - Initialized validator for FHIR r4b in XXXms
INFO  ProfileValidator - ProfileValidator initialization complete: 2/2 versions initialized in XXXXms
```

**Should NOT see:**
- `NoSuchMethodError` with TarArchiveInputStream
- "No Cache Service Providers found" error
- Any stack traces during ProfileValidator initialization

### 3. Test Health Endpoint
```bash
# Wait for application to fully start, then:
curl http://localhost:8080/actuator/health/profileValidator | jq
```

**Expected Response:**
```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "totalInitializationTime": "XXXXms",
    "successCount": 2,
    "totalCount": 2,
    "versions": {
      "r5": {
        "status": "initialized",
        "available": true,
        "initializationTime": "XXXms"
      },
      "r4b": {
        "status": "initialized",
        "available": true,
        "initializationTime": "XXXms"
      }
    }
  }
}
```

### 4. Test Metrics
```bash
# Check validation attempts metric (note: underscore notation)
curl http://localhost:8080/actuator/metrics/fhir_validation_attempts

# Check validation duration metric (note: underscore notation)
curl http://localhost:8080/actuator/metrics/fhir_validation_duration
```

**Expected**: Metrics should be available (may show 0 if no validations performed yet)

**Note**: Metrics use underscore notation (`fhir_validation_*`) for Prometheus compatibility.

### 5. Test with Validation Disabled
Stop the application and edit `fhir4java-server/src/main/resources/application.yml`:
```yaml
fhir4java:
  validation:
    profile-validator-enabled: false
```

Restart and check logs:
```
INFO  ProfileValidator - Starting ProfileValidator initialization
INFO  ProfileValidator - Profile validation is disabled by configuration - ProfileValidator will not initialize validators
```

Check health endpoint:
```bash
curl http://localhost:8080/actuator/health/profileValidator | jq
```

Expected:
```json
{
  "status": "UP",
  "details": {
    "disabled": true,
    "reason": "Profile validator disabled by configuration"
  }
}
```

### 6. Test Docker Build (Optional)
```bash
# Build the application
./mvnw clean package -DskipTests

# Start with docker-compose
docker-compose up -d fhir4java-server

# Check logs
docker-compose logs -f fhir4java-server
```

**Expected in Docker:**
- Fast startup (validation disabled by default)
- Log: "Profile validation is disabled by configuration"
- No validator initialization logs

**Enable validation in Docker:**
```bash
# Stop containers
docker-compose down

# Start with environment variable
PROFILE_VALIDATOR_ENABLED=true docker-compose up -d fhir4java-server

# Check logs - should see initialization
docker-compose logs -f fhir4java-server
```

### 7. Test Lazy Initialization (Optional)
Edit `application.yml`:
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    lazy-initialization: true
```

Restart and check logs:
- Should see fast startup
- Should NOT see "Initializing validator for FHIR..." messages at startup
- On first validation request per version, should see: "Lazy initializing validator for FHIR {version}..."

### 8. Test Validation Operation Logging
Edit `application.yml`:
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    log-validation-operations: true

logging:
  level:
    org.fhirframework.core.validation: DEBUG
```

Restart and perform a validation operation. Check logs for:
```
DEBUG ProfileValidator - Validating {ResourceType} against base StructureDefinition
DEBUG ProfileValidator - Validation completed for {ResourceType}: X issues found
```

## Common Issues & Solutions

### Issue: Build Fails with Dependency Errors
**Solution:** Ensure Maven can download dependencies:
```bash
./mvnw dependency:resolve
```

### Issue: Application Won't Start
**Solution:** Check logs for specific error. Try disabling validation:
```yaml
profile-validator-enabled: false
```

### Issue: Health Endpoint Returns 404
**Solution:** Ensure actuator is enabled:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Issue: Metrics Not Available
**Solution:** Check if Micrometer is properly configured. Ensure:
- `spring-boot-starter-actuator` is in dependencies
- `micrometer-core` is in dependencies
- Application started successfully

### Issue: Still Getting NoSuchMethodError
**Solution:** Check dependency versions:
```bash
./mvnw dependency:tree | grep commons-compress
```
Should show version 1.26.0 or later.

### Issue: Still Getting "No Cache Service Providers"
**Solution:** Check for hapi-fhir-caching-caffeine:
```bash
./mvnw dependency:tree | grep hapi-fhir-caching
```
Should show hapi-fhir-caching-caffeine.

## Success Criteria

✅ Application starts without ProfileValidator initialization errors
✅ No `NoSuchMethodError` during startup
✅ No "No Cache Service Providers found" error
✅ Health endpoint shows validator status
✅ Metrics endpoints are available
✅ Startup logs show initialization progress and timing
✅ Docker starts faster with validation disabled
✅ Enabling validation in Docker works correctly

## Performance Benchmarks

Record these for comparison:

| Configuration | Startup Time | Memory Usage |
|---------------|--------------|--------------|
| Validation Enabled (Eager) | _____ms | _____MB |
| Validation Enabled (Lazy) | _____ms | _____MB |
| Validation Disabled | _____ms | _____MB |

## Next Steps After Successful Verification

1. **Enable in Production**: Set `profile-validator-enabled: true` for production
2. **Configure Monitoring**: Import Grafana dashboard from `docs/grafana-validation-dashboard.json`
3. **Set Alerts**: Configure alerts based on health endpoint and metrics
4. **Optimize**: Use lazy initialization or disable validation in dev environments
5. **Document**: Update team documentation with new configuration options

## Getting Help

If you encounter issues:
1. Check `IMPLEMENTATION-SUMMARY.md` for detailed implementation info
2. Check `docs/PROFILE-VALIDATION.md` for comprehensive documentation
3. Review application logs for error messages
4. Check health endpoint for status details
5. Verify dependencies with `./mvnw dependency:tree`

## Report Back

Please report the following:
- ✅/❌ Build successful
- ✅/❌ Application starts without errors
- ✅/❌ Health endpoint accessible and shows correct status
- ✅/❌ Metrics available
- ✅/❌ Docker build works
- ✅/❌ Validation disabled mode works
- Any error messages encountered
- Startup time measurements