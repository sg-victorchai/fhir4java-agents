# ProfileValidator Implementation - Quick Reference Card

## 🚀 Four Phases at a Glance

---

### Phase 1: Core Implementation
**Problem**: ProfileValidator fails to initialize (NoSuchMethodError, No Cache Providers)  
**Solution**: Added dependencies + feature flags  
**Status**: ✅ COMPLETE

**Key Actions:**
- ✅ Added `commons-compress 1.26.0`
- ✅ Added `hapi-fhir-caching-caffeine`
- ✅ Created `ValidationConfig` with feature flags
- ✅ Enhanced `ProfileValidator` with logging & metrics
- ✅ Created `ProfileValidatorHealthIndicator`

**Quick Config:**
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    profile-validation: strict
```

---

### Phase 2: Health Endpoint Fix
**Problem**: `/actuator/health/profileValidator` returns 404  
**Solution**: Enable component exposure  
**Status**: ✅ COMPLETE

**Quick Fix:**
```yaml
management:
  endpoint:
    health:
      show-components: always
```

**Test:**
```bash
curl http://localhost:8080/actuator/health/profileValidator | jq
```

---

### Phase 3: Metrics Fix
**Problem**: Metrics not visible in `/actuator/metrics`  
**Solution**: Eager registration + dotted notation  
**Status**: ✅ COMPLETE

**Key Learnings:**
- ✅ Use dotted notation: `fhir.validation.attempts`
- ✅ Prometheus auto-converts: `fhir_validation_attempts`
- ✅ Register metrics eagerly at startup

**Test:**
```bash
curl http://localhost:8080/actuator/metrics/fhir.validation.attempts | jq
```

---

### Phase 4: HTTP 422 Validation
**Problem**: Invalid data returns HTTP 500 instead of 422  
**Solution**: Added DataFormatException handler + fixed status mapping  
**Status**: ✅ COMPLETE

**HTTP Status Codes:**
- **400**: Malformed JSON/XML
- **422**: Validation failure (FHIR spec) ← FIXED
- **500**: Server error

**Test:**
```bash
# Invalid gender (expect 422)
curl -X POST http://localhost:8080/fhir/r5/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{"resourceType":"Patient","gender":"99"}' -v
```

---

## ⚡ Quick Commands

### Check Health
```bash
curl http://localhost:8080/actuator/health/profileValidator | jq
```

### Check Metrics
```bash
curl http://localhost:8080/actuator/metrics | jq '.names[] | select(contains("fhir.validation"))'
```

### Test Validation
```bash
./scripts/test-validation-errors.sh
```

---

## 🎯 Configuration Presets

### Development (Fast Startup)
```yaml
fhir4java:
  validation:
    profile-validator-enabled: false
```

### Production (Full Validation)
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    profile-validation: strict
    health-check-mode: strict
    log-validation-operations: false
```

### Docker (Optimized)
```yaml
fhir4java:
  validation:
    profile-validator-enabled: ${PROFILE_VALIDATOR_ENABLED:false}
    health-check-mode: warn
```

### Debug Mode
```yaml
fhir4java:
  validation:
    profile-validator-enabled: true
    log-validation-operations: true

logging:
  level:
    org.fhirframework.core.validation: DEBUG
```

---

## 📊 Metrics Reference

| Metric Name | Type | Tags |
|-------------|------|------|
| `fhir.validation.attempts` | Counter | version, result, resourceType |
| `fhir.validation.duration` | Timer | version, resourceType |

---

## 🏥 Health Indicator Response

```json
{
  "status": "UP",
  "details": {
    "enabled": true,
    "totalInitializationTime": "2450ms",
    "successCount": 2,
    "totalCount": 2,
    "versions": {
      "r5": {"status": "initialized", "initializationTime": "1230ms"},
      "r4b": {"status": "initialized", "initializationTime": "1220ms"}
    }
  }
}
```

---

## 🔍 Troubleshooting Quick Fix

| Issue | Quick Fix |
|-------|-----------|
| 404 on health endpoint | Add `show-components: always` |
| Metrics not visible | Check eager registration in logs |
| Validation returns 500 | Apply Phase 4 fix (DataFormatException) |
| Docker validation disabled | Set `PROFILE_VALIDATOR_ENABLED=true` |
| Slow startup | Set `profile-validator-enabled: false` |

---

## 📁 Key Files Modified

1. `ProfileValidator.java` - Core validation logic
2. `FhirExceptionHandler.java` - HTTP status code handling
3. `application.yml` - Configuration
4. `application-docker.yml` - Docker overrides
5. `pom.xml` - Dependencies

---

## 🎓 Six Key Lessons

1. **Dependencies Matter**: Always use compatible versions
2. **Eager Registration**: Metrics need upfront registration
3. **Profile Precedence**: Docker configs override base configs
4. **Iterative Testing**: Test after each phase
5. **Documentation**: Explain "why" not just "what"
6. **HTTP Codes**: 422 for validation, not 500

---

## 📞 Support

**Documentation**: `docs/PROFILE-VALIDATION.md`  
**Full Summary**: `PROFILE-IMPLEMENTATION-SUMMARY.md`  
**Test Scripts**: `scripts/test-validation-errors.sh`  
**Grafana Dashboard**: `docs/grafana-validation-dashboard.json`

---

**Last Updated**: February 4, 2026  
**Status**: All 4 Phases Complete ✅
