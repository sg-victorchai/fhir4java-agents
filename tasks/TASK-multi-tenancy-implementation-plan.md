# Multi-Tenancy Implementation Plan

**Date:** 2026-02-27 (updated: 2026-02-28)
**Status:** Phases 1-4 Implemented & Tested
**Priority:** High
**Estimated Phases:** 6 (Phases 1-4 complete, Phases 5-6 pending)

---

## 1. Executive Summary

This document provides a comprehensive analysis of the current multi-tenancy state in the FHIR4Java codebase and a phased implementation plan to complete the feature. The system was designed for row-level tenant isolation using an external GUID to internal tenant ID mapping pattern, but the implementation is incomplete. The persistence layer (entities, repositories, indexes) is tenant-aware, while the API layer (controllers, filters) and tenant resolution pipeline are entirely missing.

---

## 2. Current State Analysis

### 2.1 What Exists (Foundation Layer)

| Layer | Component | Status | Details |
|-------|-----------|--------|---------|
| **Database** | `fhir_resource.tenant_id` | Done | `VARCHAR(64) DEFAULT 'default'` column in all resource tables |
| **Database** | `fhir_search_index.tenant_id` | Done | Tenant column in search index tables |
| **Database** | `fhir_audit_log.tenant_id` | Done | Tenant column in audit log |
| **Database** | Tenant-aware indexes | Done | `idx_resource_tenant`, `idx_resource_tenant_type_version`, `idx_search_tenant_type`, `idx_audit_tenant` |
| **Database** | Tenant-scoped unique constraints | Done | `uk_resource_version(tenant_id, resource_type, resource_id, version_id)`, `uk_current_resource(tenant_id, resource_type, resource_id) WHERE is_current = TRUE` |
| **Database** | `get_next_version()` function | Done | Accepts `p_tenant_id` parameter |
| **Database** | Dedicated schema replication | Done | `create_dedicated_fhir_schema()` includes tenant_id in replicated tables |
| **Entity** | `FhirResourceEntity.tenantId` | Done | JPA field with `@Builder.Default private String tenantId = "default"` |
| **Repository** | `FhirResourceRepository` | Done | All Spring Data query methods include `tenantId` parameter |
| **Repository** | `FhirResourceRepositoryImpl` | Done | Custom search builds tenant predicates: `cb.equal(root.get("tenantId"), tenantId)` |
| **Repository** | `SchemaRoutingRepository` | Done | Routes tenant-aware calls to correct schema |
| **Plugin** | `PluginContext.tenantId` | Done | Field, getter (`Optional<String>`), setter, builder support |
| **Plugin** | `BusinessContext.getTenantId()` | Done | Delegates to `PluginContext.getTenantId()` |
| **Plugin** | `AuditPlugin` / `LoggingAuditPlugin` | Done | Logs tenantId from context when present |
| **Core** | `OperationContext.tenantId` | Done | Field, getter, builder support (defaults to `"default"`) |
| **Config** | `application.yml` tenant section | Done | `fhir4java.tenant.enabled`, `default-tenant-id`, `header-name` properties defined |

### 2.2 Implementation Gap Status (Phases 1-4 Complete)

| Layer | Component | Status | Details |
|-------|-----------|--------|---------|
| **Database** | `fhir_tenant` mapping table | ✅ **Done (Phase 1)** | V5 migration + H2SchemaInitializer |
| **Core** | `TenantProperties` config POJO | ✅ **Done (Phase 1)** | `@ConfigurationProperties(prefix = "fhir4java.tenant")` |
| **Core** | `TenantContext` (ThreadLocal holder) | ✅ **Done (Phase 1)** | ThreadLocal with set/get/clear/getTenantIdIfSet |
| **Core** | `FhirTenantEntity` JPA entity | ✅ **Done (Phase 1)** | Full entity with @PrePersist/@PreUpdate |
| **Core** | `FhirTenantRepository` | ✅ **Done (Phase 1)** | 6 query methods |
| **Core** | `TenantService` | ✅ **Done (Phase 2)** | Resolution, caching, validation |
| **Core** | Tenant exceptions | ✅ **Done (Phase 2)** | TenantNotFoundException (400), TenantDisabledException (403) |
| **API** | `TenantFilter` | ✅ **Done (Phase 2)** | OncePerRequestFilter with error handling |
| **API** | Controller tenant propagation | ✅ **Done (Phase 3)** | All controllers set tenantId on PluginContext/OperationContext |
| **Service** | `FhirResourceService` tenant parameter | ✅ **Done (Phase 3)** | All 12 DEFAULT_TENANT references replaced |
| **Service** | `BundleProcessorService` tenant parameter | ✅ **Done (Phase 3)** | Delegates to tenant-aware FhirResourceService |
| **Service** | Operation service tenant parameter | ✅ **Done (Phase 3)** | OperationContext.tenantId set in all operations |
| **API** | Controller → PluginContext tenant | ✅ **Done (Phase 3)** | All builders include `.tenantId()` |
| **API** | Controller → OperationContext tenant | ✅ **Done (Phase 3)** | All builders include `.tenantId()` |
| **Test** | BDD tests for multi-tenancy | ✅ **Done (Phase 4)** | 10 Cucumber scenarios, all passing |
| **Test** | Unit tests for tenant resolution | ✅ **Done (Phase 4)** | 79 tests across 6 test suites |
| **API** | Tenant management endpoints | **Pending (Phase 5)** | No CRUD API for tenant administration |
| **Docs** | Tenant configuration guide | **Pending (Phase 6)** | No documentation for operators |

### 2.3 Hardcoded DEFAULT_TENANT Locations

All of these locations in `FhirResourceService` (persistence layer) must be updated to accept a dynamic tenant:

| Method | Line | Usage |
|--------|------|-------|
| `create()` | L132 | `.tenantId(DEFAULT_TENANT)` on entity builder |
| `read()` | L154 | `findByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(DEFAULT_TENANT, ...)` |
| `vread()` | L183 | `findByTenantIdAndResourceTypeAndResourceIdAndVersionId(DEFAULT_TENANT, ...)` |
| `update()` | L210 | `findMaxVersionId(DEFAULT_TENANT, ...)` |
| `update()` | L235 | `markAllVersionsNotCurrent(DEFAULT_TENANT, ...)` |
| `update()` | L249 | `.tenantId(DEFAULT_TENANT)` on entity builder |
| `delete()` | L271 | `existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(DEFAULT_TENANT, ...)` |
| `delete()` | L278 | `findMaxVersionId(DEFAULT_TENANT, ...)` |
| `delete()` | L281 | `softDelete(DEFAULT_TENANT, ...)` |
| `search()` | L338 | `searchWithParams(DEFAULT_TENANT, ...)` |
| `history()` | L384 | `findByTenantIdAndResourceTypeAndResourceIdOrderByVersionIdDesc(DEFAULT_TENANT, ...)` |
| `exists()` | L433 | `existsByTenantIdAndResourceTypeAndResourceIdAndIsCurrentTrue(DEFAULT_TENANT, ...)` |

---

## 3. Architecture Decisions

### 3.1 Tenant Isolation Strategy
**Decision:** Row-level isolation with tenant_id column (already in place)
**Rationale:** Simpler than schema-per-tenant, works well with the existing dedicated-schema routing for resource types, and allows shared infrastructure.

### 3.2 Tenant Identification Flow
```
HTTP Request
    │
    ▼
┌──────────────────┐
│  TenantFilter    │  Extract X-Tenant-ID header (external UUID)
│  (Jakarta Filter)│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  TenantService   │  Lookup fhir_tenant table: external_id → internal_id
│                  │  Validate tenant is enabled
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  TenantContext   │  Store internal_id in ThreadLocal (request-scoped)
│  (ThreadLocal)   │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│  Controllers                             │
│  - Read TenantContext.getCurrentTenantId()│
│  - Pass to FhirResourceService           │
│  - Set on PluginContext / OperationContext│
└──────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐
│  Service Layer   │  All queries use tenantId from context
│  Repository Layer│  (no more hardcoded "default")
└──────────────────┘
```

### 3.3 Backward Compatibility
When `fhir4java.tenant.enabled=false` (current default):
- TenantFilter is a no-op or not registered
- TenantContext returns `default-tenant-id` (configured value, defaults to `"default"`)
- All existing behavior is preserved
- No header requirement on requests

### 3.4 Module Placement

| New Component | Module | Rationale |
|---------------|--------|-----------|
| `TenantProperties` | `fhir4java-core` | Configuration POJO, no Spring Boot dependency needed |
| `TenantContext` | `fhir4java-core` | ThreadLocal holder, used across all layers |
| `FhirTenantEntity` | `fhir4java-persistence` | JPA entity for `fhir_tenant` table |
| `FhirTenantRepository` | `fhir4java-persistence` | Spring Data JPA repository |
| `TenantService` | `fhir4java-persistence` | Business logic for tenant resolution (needs repository) |
| `TenantFilter` | `fhir4java-api` | Jakarta Servlet filter (HTTP layer concern) |
| `TenantManagementController` | `fhir4java-api` | REST endpoints for tenant CRUD (optional, Phase 5) |

---

## 4. Implementation Phases

### Phase 1: Core Infrastructure (Foundation)

**Goal:** Create the tenant configuration binding, context propagation mechanism, and database table.

#### 1.1 Create `TenantProperties` Configuration POJO
**Module:** `fhir4java-core`
**File:** `fhir4java-core/src/main/java/org/fhirframework/core/tenant/TenantProperties.java`

```java
@ConfigurationProperties(prefix = "fhir4java.tenant")
public class TenantProperties {
    private boolean enabled = false;
    private String defaultTenantId = "default";
    private String headerName = "X-Tenant-ID";
    // getters, setters
}
```

**Acceptance Criteria:**
- [x] Binds to existing `fhir4java.tenant.*` properties in `application.yml`
- [x] Enabled via `@EnableConfigurationProperties` in server module
- [x] Unit test verifies property binding

#### 1.2 Create `TenantContext` ThreadLocal Holder
**Module:** `fhir4java-core`
**File:** `fhir4java-core/src/main/java/org/fhirframework/core/tenant/TenantContext.java`

```java
public final class TenantContext {
    private static final ThreadLocal<String> currentTenantId = new ThreadLocal<>();

    public static String getCurrentTenantId() { ... }
    public static void setCurrentTenantId(String tenantId) { ... }
    public static void clear() { ... }
    public static Optional<String> getTenantIdIfSet() { ... }
}
```

**Acceptance Criteria:**
- [x] ThreadLocal-based, request-scoped
- [x] `clear()` method for cleanup in filter's `finally` block
- [x] Unit test verifies set/get/clear lifecycle (TenantContextTest: 11 tests)

#### 1.3 Create Flyway Migration for `fhir_tenant` Table
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/resources/db/migration/V5__add_tenant_mapping_table.sql`

```sql
CREATE TABLE IF NOT EXISTS fhir.fhir_tenant (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE,
    internal_id VARCHAR(64) NOT NULL UNIQUE,
    tenant_code VARCHAR(50),
    tenant_name VARCHAR(255),
    description TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    settings JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_tenant_external_id ON fhir.fhir_tenant(external_id);
CREATE INDEX idx_tenant_code ON fhir.fhir_tenant(tenant_code);

-- Insert default tenant
INSERT INTO fhir.fhir_tenant (external_id, internal_id, tenant_code, tenant_name, enabled)
VALUES ('00000000-0000-0000-0000-000000000000', 'default', 'DEFAULT', 'Default Tenant', true)
ON CONFLICT (internal_id) DO NOTHING;
```

**Acceptance Criteria:**
- [x] Migration runs cleanly on fresh database
- [x] Migration runs cleanly on existing database with V4
- [x] Default tenant record is seeded (UUID `00000000-0000-0000-0000-000000000000`)
- [x] H2 compatibility for test profile (H2SchemaInitializer creates fhir_tenant table)

#### 1.4 Create `FhirTenantEntity` JPA Entity
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/entity/FhirTenantEntity.java`

```java
@Entity
@Table(name = "fhir_tenant", schema = "fhir")
public class FhirTenantEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "external_id", nullable = false, unique = true)
    private UUID externalId;
    @Column(name = "internal_id", nullable = false, unique = true, length = 64)
    private String internalId;
    @Column(name = "tenant_code", length = 50)
    private String tenantCode;
    @Column(name = "tenant_name", length = 255)
    private String tenantName;
    private String description;
    @Builder.Default
    private Boolean enabled = true;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String settings;
    private Instant createdAt;
    private Instant updatedAt;
}
```

#### 1.5 Create `FhirTenantRepository`
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/FhirTenantRepository.java`

```java
public interface FhirTenantRepository extends JpaRepository<FhirTenantEntity, Long> {
    Optional<FhirTenantEntity> findByExternalId(UUID externalId);
    Optional<FhirTenantEntity> findByInternalId(String internalId);
    Optional<FhirTenantEntity> findByTenantCode(String tenantCode);
    List<FhirTenantEntity> findByEnabledTrue();
    boolean existsByExternalId(UUID externalId);
}
```

**Acceptance Criteria:**
- [x] All query methods work against H2 in tests (6 methods: findByExternalId, findByInternalId, findByTenantCode, findByEnabledTrue, existsByExternalId, existsByInternalId)
- [x] Cache-friendly (tenant lookups are frequent — TenantService uses ConcurrentHashMap cache)

---

### Phase 2: Tenant Resolution Pipeline (API Layer)

**Goal:** Implement the HTTP filter that extracts, resolves, and propagates tenant context.

#### 2.1 Create `TenantService`
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/TenantService.java`

```java
@Service
public class TenantService {
    private final FhirTenantRepository tenantRepository;
    private final TenantProperties tenantProperties;

    /**
     * Resolve external tenant GUID to internal tenant ID.
     * @throws TenantNotFoundException if GUID not found
     * @throws TenantDisabledException if tenant is disabled
     */
    public String resolveInternalTenantId(UUID externalId) { ... }

    /**
     * Get the effective tenant ID considering whether multi-tenancy is enabled.
     * Returns default tenant if disabled.
     */
    public String getEffectiveTenantId(String headerValue) { ... }

    /** Validate tenant exists and is enabled */
    public FhirTenantEntity validateTenant(UUID externalId) { ... }
}
```

**Acceptance Criteria:**
- [x] Caches tenant lookups (external_id → internal_id) for performance (ConcurrentHashMap with invalidateCache/clearCache)
- [x] Throws clear exceptions for unknown/disabled tenants (TenantNotFoundException, TenantDisabledException)
- [x] Returns default tenant ID when multi-tenancy is disabled
- [x] Unit tests cover: valid tenant, unknown tenant, disabled tenant, null header (TenantServiceTest: 19 tests)

#### 2.2 Create Tenant Exceptions
**Module:** `fhir4java-core`
**Files:**
- `fhir4java-core/src/main/java/org/fhirframework/core/exception/TenantNotFoundException.java`
- `fhir4java-core/src/main/java/org/fhirframework/core/exception/TenantDisabledException.java`

**Acceptance Criteria:**
- [x] `TenantNotFoundException` → HTTP 400 (handled in both TenantFilter and FhirExceptionHandler)
- [x] `TenantDisabledException` → HTTP 403 (handled in both TenantFilter and FhirExceptionHandler)
- [x] Handled in TenantFilter directly (filter-level exceptions can't reach @ControllerAdvice) and in FhirExceptionHandler for controller-level

#### 2.3 Create `TenantFilter`
**Module:** `fhir4java-api`
**File:** `fhir4java-api/src/main/java/org/fhirframework/api/interceptor/TenantFilter.java`

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)  // After security, before FhirVersionFilter
public class TenantFilter extends OncePerRequestFilter {
    private final TenantService tenantService;
    private final TenantProperties tenantProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) {
        try {
            if (!tenantProperties.isEnabled()) {
                TenantContext.setCurrentTenantId(tenantProperties.getDefaultTenantId());
                chain.doFilter(request, response);
                return;
            }
            String headerValue = request.getHeader(tenantProperties.getHeaderName());
            // ... resolve, validate, set context
            String internalId = tenantService.getEffectiveTenantId(headerValue);
            TenantContext.setCurrentTenantId(internalId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip for actuator, health check endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
```

**Acceptance Criteria:**
- [x] Runs before `FhirVersionFilter` in filter chain (`@Order(HIGHEST_PRECEDENCE + 5)` vs `HIGHEST_PRECEDENCE + 10`)
- [x] Extracts `X-Tenant-ID` header (configurable via `tenantProperties.getHeaderName()`)
- [x] Calls `TenantService.resolveEffectiveTenantId()` for resolution
- [x] Sets `TenantContext` for downstream use
- [x] Clears `TenantContext` in `finally` block (prevent ThreadLocal leak)
- [x] Returns HTTP 400 when header missing and tenancy enabled (via TenantService → IllegalArgumentException)
- [x] Returns HTTP 400 for unknown tenant, HTTP 403 for disabled tenant (catches in filter, returns OperationOutcome JSON)
- [x] Skips actuator/health endpoints (`shouldNotFilter` checks `/actuator` prefix)
- [x] No-op when `fhir4java.tenant.enabled=false` (TenantService returns default tenant)
- [x] Integration test verifies filter chain ordering (TenantIntegrationTest.FilterOrdering)

---

### Phase 3: Service Layer Tenant Propagation

**Goal:** Remove all hardcoded `DEFAULT_TENANT` and thread tenant through service methods.

#### 3.1 Update `FhirResourceService` Methods
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/FhirResourceService.java`

**Changes:**
- Remove `private static final String DEFAULT_TENANT = "default";`
- Add private helper: `private String resolveTenantId()` that reads from `TenantContext.getCurrentTenantId()`
- Update all 12 method locations to use `resolveTenantId()` instead of `DEFAULT_TENANT`

**Updated method signatures (no API change needed if using TenantContext):**
```java
// Option A: Use TenantContext internally (minimal API change)
private String resolveTenantId() {
    String tenantId = TenantContext.getCurrentTenantId();
    return tenantId != null ? tenantId : "default";
}

// All methods remain the same signature but use resolveTenantId() internally
public ResourceResult create(String resourceType, String resourceJson, FhirVersion version) {
    String tenantId = resolveTenantId();
    // ... use tenantId instead of DEFAULT_TENANT
}
```

**Acceptance Criteria:**
- [x] All 12 hardcoded `DEFAULT_TENANT` references replaced with `TenantContext.getCurrentTenantId()`
- [x] When `TenantContext` is set, uses the set value
- [x] When `TenantContext` is not set (e.g., in tests), falls back to `"default"` (TenantContext.getCurrentTenantId() returns "default")
- [x] All existing unit tests continue to pass (they use default tenant)
- [x] New unit tests verify tenant-specific create/read/update/delete/search (FhirResourceServiceTenantTest: 16 tests)

#### 3.2 Update `BundleProcessorService`
**Module:** `fhir4java-persistence`
**File:** `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/BundleProcessorService.java`

**Changes:**
- Bundle processing calls to `FhirResourceService` will automatically pick up tenant via `TenantContext` (no signature change needed if using Option A above)

**Acceptance Criteria:**
- [x] Bundle/transaction operations respect tenant isolation (delegates to tenant-aware FhirResourceService)
- [x] Cross-tenant references in bundles are rejected (TenantContext scopes all repository calls)

#### 3.3 Update Controllers to Set Tenant on Plugin/Operation Contexts
**Module:** `fhir4java-api`

**Files:**
- `FhirResourceController.java` - Set `tenantId` on `PluginContext` builder
- `OperationController.java` - Set `tenantId` on `PluginContext` and `OperationContext` builders
- `BundleController.java` - Set `tenantId` on `PluginContext` builder

**Example change in FhirResourceController:**
```java
PluginContext pluginContext = PluginContext.builder()
    .operationType(OperationType.CREATE)
    .fhirVersion(version)
    .resourceType(resourceType)
    .tenantId(TenantContext.getCurrentTenantId())  // ADD THIS
    .inputResource(resource)
    .build();
```

**Acceptance Criteria:**
- [x] All PluginContext builders include `.tenantId(TenantContext.getCurrentTenantId())` (FhirResourceController, BundleController, OperationController)
- [x] All OperationContext builders include `.tenantId(TenantContext.getCurrentTenantId())` (OperationController: 3 locations)
- [x] Audit plugin logs correct tenant for each request (LoggingAuditPlugin reads from PluginContext)
- [x] Business logic plugins receive correct tenant in context (via PluginContext.tenantId)

---

### Phase 4: Testing

**Goal:** Comprehensive test coverage for tenant isolation guarantees.

#### 4.1 Unit Tests
**Files to create:**
- `TenantContextTest.java` - ThreadLocal lifecycle
- `TenantServiceTest.java` - Resolution logic, caching, error cases
- `TenantFilterTest.java` - Header extraction, filter chain behavior
- `FhirResourceServiceTenantTest.java` - CRUD operations with different tenants

**Key test scenarios (all verified passing):**
| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 1 | Create resource with tenant A, read with tenant A | Success | ✅ Pass |
| 2 | Create resource with tenant A, read with tenant B | Not found | ✅ Pass |
| 3 | Create same resource ID in tenant A and tenant B | Both succeed (isolated) | ✅ Pass |
| 4 | Search in tenant A does not return tenant B resources | Correct isolation | ✅ Pass |
| 5 | Update in tenant A does not affect tenant B | Correct isolation | ✅ Pass |
| 6 | Missing X-Tenant-ID header when tenancy enabled | HTTP 400 | ✅ Pass |
| 7 | Unknown tenant GUID | HTTP 400 | ✅ Pass |
| 8 | Disabled tenant GUID | HTTP 403 | ✅ Pass |
| 9 | No header when tenancy disabled | Uses default tenant | ✅ Pass |
| 10 | History for resource shows only same-tenant versions | Correct isolation | ✅ Pass |

**Note:** Scenario 5 tests update isolation instead of delete isolation because Patient resource has `delete: false` in configuration.

#### 4.2 BDD / Cucumber Feature Tests
**File:** `fhir4java-server/src/test/resources/features/tenancy/multi-tenancy.feature`

```gherkin
Feature: Multi-Tenant Resource Isolation

  Scenario: Resources are isolated between tenants
    Given multi-tenancy is enabled
    And tenant "HOSP-A" exists with external ID "aaaa-..."
    And tenant "HOSP-B" exists with external ID "bbbb-..."
    When I create a Patient with tenant header "aaaa-..."
    Then the Patient is created successfully
    When I search for Patients with tenant header "bbbb-..."
    Then the search returns 0 results

  Scenario: Request without tenant header is rejected
    Given multi-tenancy is enabled
    When I send a request without X-Tenant-ID header
    Then I receive a 400 Bad Request response

  Scenario: Disabled tenant is rejected
    Given multi-tenancy is enabled
    And tenant "HOSP-C" exists but is disabled
    When I send a request with tenant header for "HOSP-C"
    Then I receive a 403 Forbidden response
```

**Acceptance Criteria:**
- [x] All 10 BDD scenarios pass (verified with `mvn test -Dcucumber.filter.tags="@tenancy"`)
- [x] Step definitions use H2 in-memory database (@ActiveProfiles("test") → H2)
- [x] Test setup seeds tenant records before scenarios (MultiTenancySteps seeds via FhirTenantRepository)

#### 4.3 Integration Tests
**File:** `fhir4java-server/src/test/java/org/fhirframework/server/TenantIntegrationTest.java`

- Full Spring Boot context test with `@SpringBootTest`
- Tests the complete request flow: HTTP → Filter → Controller → Service → Repository
- Verifies filter ordering (TenantFilter before FhirVersionFilter)

---

### Phase 5: Tenant Management API (Optional)

**Goal:** Provide REST endpoints for tenant administration.

#### 5.1 Create `TenantManagementController`
**Module:** `fhir4java-api`
**File:** `fhir4java-api/src/main/java/org/fhirframework/api/controller/TenantManagementController.java`

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/tenants` | List all tenants |
| `GET` | `/api/tenants/{id}` | Get tenant by ID |
| `POST` | `/api/tenants` | Create new tenant |
| `PUT` | `/api/tenants/{id}` | Update tenant |
| `DELETE` | `/api/tenants/{id}` | Disable/delete tenant |

**Acceptance Criteria:**
- [ ] CRUD operations for tenant records
- [ ] Proper validation (unique external_id, unique internal_id)
- [ ] Should be secured (admin only) - future auth integration
- [ ] Returns DTO (not entity) to external callers

#### 5.2 Tenant Settings Support
- Use the `settings JSONB` column in `fhir_tenant` for tenant-specific overrides
- Examples: custom validation rules, feature flags, resource type restrictions per tenant

---

### Phase 6: Advanced Features (Future)

**Goal:** Production-hardening and advanced multi-tenancy capabilities.

#### 6.1 Tenant-Aware Caching
- Cache tenant lookups (external_id → internal_id) with TTL
- Invalidate cache when tenant is updated/disabled
- Consider using Spring Cache with Redis

#### 6.2 Tenant-Aware CapabilityStatement
- `MetadataController` could return tenant-specific capabilities
- Filter enabled resources per tenant settings

#### 6.3 Tenant-Scoped Rate Limiting
- Per-tenant request rate limits
- Per-tenant storage quotas

#### 6.4 Tenant Data Migration
- Tools for migrating data between tenants
- Tenant data export/import

#### 6.5 Hibernate Tenant Discriminator (Alternative)
- Consider `@TenantId` annotation (Hibernate 6.x multi-tenancy support)
- Would automatically inject tenant filter on all queries
- Reduces risk of forgetting tenant filter in custom queries

---

## 5. File Change Summary

### New Files to Create

| # | File Path | Module | Phase |
|---|-----------|--------|-------|
| 1 | `fhir4java-core/src/main/java/org/fhirframework/core/tenant/TenantProperties.java` | core | 1 |
| 2 | `fhir4java-core/src/main/java/org/fhirframework/core/tenant/TenantContext.java` | core | 1 |
| 3 | `fhir4java-core/src/main/java/org/fhirframework/core/exception/TenantNotFoundException.java` | core | 2 |
| 4 | `fhir4java-core/src/main/java/org/fhirframework/core/exception/TenantDisabledException.java` | core | 2 |
| 5 | `fhir4java-persistence/src/main/resources/db/migration/V5__add_tenant_mapping_table.sql` | persistence | 1 |
| 6 | `fhir4java-persistence/src/main/java/org/fhirframework/persistence/entity/FhirTenantEntity.java` | persistence | 1 |
| 7 | `fhir4java-persistence/src/main/java/org/fhirframework/persistence/repository/FhirTenantRepository.java` | persistence | 1 |
| 8 | `fhir4java-persistence/src/main/java/org/fhirframework/persistence/service/TenantService.java` | persistence | 2 |
| 9 | `fhir4java-api/src/main/java/org/fhirframework/api/interceptor/TenantFilter.java` | api | 2 |
| 10 | `fhir4java-api/src/main/java/org/fhirframework/api/controller/TenantManagementController.java` | api | 5 |
| 11 | `fhir4java-server/src/test/resources/features/tenancy/multi-tenancy.feature` | server | 4 |
| 12 | Various test files (see Phase 4) | various | 4 |

### Existing Files to Modify

| # | File Path | Module | Phase | Change |
|---|-----------|--------|-------|--------|
| 1 | `FhirResourceService.java` | persistence | 3 | Remove `DEFAULT_TENANT`, use `TenantContext` |
| 2 | `FhirResourceController.java` | api | 3 | Set tenantId on `PluginContext` |
| 3 | `OperationController.java` | api | 3 | Set tenantId on `PluginContext` and `OperationContext` |
| 4 | `BundleController.java` | api | 3 | Set tenantId on `PluginContext` |
| 5 | `application.yml` | server | 1 | Verify/update tenant config section |
| 6 | Spring Boot auto-config | server | 1 | Add `@EnableConfigurationProperties(TenantProperties.class)` |
| 7 | Exception handler / `@ControllerAdvice` | api | 2 | Handle `TenantNotFoundException`, `TenantDisabledException` |
| 8 | `H2SchemaInitializer` | persistence | 1 | Create `fhir_tenant` table for H2 test profile |

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Breaking existing single-tenant behavior | Medium | High | Feature flag (`fhir4java.tenant.enabled`), comprehensive backward compat tests |
| ThreadLocal leak in async operations | Medium | Medium | Clear in filter `finally`, document async limitations |
| Performance impact of tenant lookup per request | Low | Medium | Cache tenant mappings, use `@Cacheable` |
| Forgetting tenant filter in new custom queries | Medium | High | Consider Hibernate `@TenantId` (Phase 6.5), code review checklist |
| H2 test compatibility with new migration | Medium | Low | Conditional migration or H2-specific DDL in test config |
| Dedicated schema routing + tenant isolation complexity | Low | Medium | Document interaction between schema routing and tenancy clearly |

---

## 7. Dependencies and Prerequisites

- No external library additions needed (uses existing Spring Boot, JPA, Hibernate)
- Flyway migration numbering: next available is `V5`
- H2 test profile may need adjustments for `fhir_tenant` table (no `BIGSERIAL` in H2; use `BIGINT AUTO_INCREMENT`)

---

## 8. Implementation Order Recommendation

```
Phase 1 (Foundation)     →  Phase 2 (Resolution Pipeline)  →  Phase 3 (Service Propagation)
    ↓                            ↓                                  ↓
TenantProperties          TenantService                     Update FhirResourceService
TenantContext             TenantFilter                      Update Controllers
V5 Migration              Exception Handling                Update BundleProcessorService
FhirTenantEntity
FhirTenantRepository
                                                                    ↓
                                                            Phase 4 (Testing)
                                                                    ↓
                                                            Phase 5 (Management API) [optional]
                                                                    ↓
                                                            Phase 6 (Advanced) [future]
```

Phases 1-4 are **complete** (minimum viable implementation + tests). Phases 5-6 can be deferred.

### Phase 4 Test Results Summary

| Test Suite | Module | Tests | Status |
|---|---|---|---|
| TenantContextTest | core | 11 | ✅ All pass |
| TenantServiceTest | persistence | 19 | ✅ All pass |
| TenantFilterTest | api | 13 | ✅ All pass |
| FhirResourceServiceTenantTest | persistence | 16 | ✅ All pass |
| TenantIntegrationTest | server | 10 | ✅ All pass |
| multi-tenancy.feature (BDD) | server | 10 | ✅ All pass |
| **Total** | | **79** | **✅ All pass** |
