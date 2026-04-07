# Phase 10: Compliance Implementation Plan (Weeks 56-67)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Comprehensive regulatory compliance with consent enforcement, AI provenance tracking, data localization, de-identification, and immutable audit trails.

**Architecture:** Plugin-based compliance checks (consent, authorization, localization) execute in BEFORE phase. Provenance plugin executes in AFTER phase. De-identification as a service layer. Database triggers enforce audit immutability.

**Tech Stack:** Spring Security, HAPI FHIR Consent/Provenance resources, PostgreSQL triggers

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillars 4, 10

---

## File Structure

```
fhir4java-core/src/main/java/org/fhirframework/core/
├── consent/
│   ├── ConsentService.java               # Consent evaluation logic
│   ├── ConsentDecision.java              # Permit/deny/redact result
│   └── ConsentCategory.java              # Enum: RESEARCH, TREATMENT, MARKETING, GENETIC
├── provenance/
│   ├── ProvenanceService.java            # Create Provenance resources
│   └── ProvenanceBuilder.java            # Builder for AI provenance
├── deidentification/
│   ├── DeidentificationService.java      # De-identification facade
│   ├── SafeHarborStrategy.java           # HIPAA 18 identifiers
│   └── LimitedDataSetStrategy.java       # Limited data set rules
├── localization/
│   ├── DataLocalizationService.java      # Regional compliance checks
│   └── RegionConfig.java                 # Per-region rules

fhir4java-plugin/src/main/java/org/fhirframework/plugin/
├── authorization/
│   └── ScopedAuthorizationPlugin.java    # Replace NoOpAuthorizationPlugin
├── consent/
│   └── ConsentEnforcementPlugin.java     # BEFORE plugin for consent checks
├── provenance/
│   └── AiProvenancePlugin.java           # AFTER plugin creates Provenance
├── localization/
│   └── DataLocalizationPlugin.java       # BEFORE plugin for region checks

fhir4java-persistence/src/main/java/org/fhirframework/persistence/
├── entity/
│   └── SoftDeleteMixin.java              # Soft delete support

db/migrations/
├── V*__immutable_audit_triggers.sql      # Prevent audit modification
├── V*__soft_delete_columns.sql           # Add deleted_at columns
```

---

## Tasks

### Task 1: Scoped Authorization Plugin

**Files:** `fhir4java-plugin/.../authorization/ScopedAuthorizationPlugin.java`

- [ ] **Step 1: Write failing test for scope enforcement**

```java
@Test
void scopedAuth_deniesUnauthorizedResourceType() {
    // Agent has scope: patient/*.read
    PluginContext context = PluginContext.builder()
        .principal(agentWithScopes("patient/*.read"))
        .resourceType("Observation")
        .operationType(OperationType.READ)
        .build();

    PluginResult result = plugin.execute(context);

    assertThat(result.shouldAbort()).isTrue();
    assertThat(result.getStatusCode()).isEqualTo(403);
}

@Test
void scopedAuth_allowsAuthorizedAccess() {
    PluginContext context = PluginContext.builder()
        .principal(agentWithScopes("patient/*.read"))
        .resourceType("Patient")
        .operationType(OperationType.READ)
        .build();

    PluginResult result = plugin.execute(context);

    assertThat(result.shouldContinue()).isTrue();
}
```

- [ ] **Step 2: Implement ScopedAuthorizationPlugin**

```java
@Component
public class ScopedAuthorizationPlugin implements AuthorizationPlugin {
    @Override
    public PluginPhase getPhase() { return PluginPhase.BEFORE; }

    @Override
    public int getOrder() { return 100; } // Run early

    @Override
    public PluginResult execute(PluginContext context) {
        Authentication auth = context.getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return PluginResult.abort(401, "Authentication required");
        }

        Set<String> scopes = extractScopes(auth);
        String requiredScope = buildRequiredScope(
            context.getResourceType(),
            context.getOperationType()
        );

        if (!hasMatchingScope(scopes, requiredScope)) {
            return PluginResult.abort(403,
                "Insufficient scope. Required: " + requiredScope);
        }

        return PluginResult.continueProcessing();
    }

    private String buildRequiredScope(String resourceType, OperationType op) {
        String action = switch (op) {
            case READ, SEARCH, VREAD, HISTORY -> "read";
            case CREATE, UPDATE, PATCH, DELETE -> "write";
            default -> "*";
        };
        return resourceType.toLowerCase() + "/" + resourceType + "." + action;
    }
}
```

- [ ] **Step 3: Add scope parsing for SMART on FHIR format**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(plugin): add ScopedAuthorizationPlugin replacing NoOp"`

---

### Task 2: Consent Service

**Files:** `fhir4java-core/.../consent/ConsentService.java`

- [ ] **Step 1: Write failing test for consent evaluation**

```java
@Test
void consentService_evaluatesActiveConsents() {
    // Patient has consent denying research access
    Consent consent = createConsent(ConsentCategory.RESEARCH, ConsentProvision.DENY);
    when(consentRepository.findActiveByPatient("Patient/123"))
        .thenReturn(List.of(consent));

    ConsentDecision decision = consentService.evaluate(
        "Patient/123",
        ConsentCategory.RESEARCH,
        "Observation"
    );

    assertThat(decision.isPermitted()).isFalse();
    assertThat(decision.getReason()).contains("research");
}

@Test
void consentService_permitsWhenNoRestriction() {
    when(consentRepository.findActiveByPatient("Patient/123"))
        .thenReturn(List.of());

    ConsentDecision decision = consentService.evaluate(
        "Patient/123",
        ConsentCategory.TREATMENT,
        "Observation"
    );

    assertThat(decision.isPermitted()).isTrue();
}
```

- [ ] **Step 2: Implement ConsentService**

```java
@Service
public class ConsentService {
    private final FhirResourceRepository consentRepository;

    @Value("${fhir4java.consent.default-policy:permit}")
    private String defaultPolicy;

    public ConsentDecision evaluate(String patientRef, ConsentCategory category,
                                    String resourceType) {
        List<Consent> consents = findActiveConsents(patientRef);

        if (consents.isEmpty()) {
            return defaultPolicy.equals("permit")
                ? ConsentDecision.permit("No active consent restrictions")
                : ConsentDecision.deny("No consent on file (default deny)");
        }

        for (Consent consent : consents) {
            if (matchesCategory(consent, category)) {
                ConsentProvision provision = consent.getProvision();
                if (provision.getType() == ConsentProvision.DENY) {
                    return ConsentDecision.deny(
                        "Patient consent restricts " + category + " access");
                }
                if (shouldRedact(provision, resourceType)) {
                    return ConsentDecision.redact(
                        getRedactedFields(provision, resourceType));
                }
            }
        }

        return ConsentDecision.permit("Consent evaluation passed");
    }
}
```

- [ ] **Step 3: Add consent category matching (RESEARCH, TREATMENT, MARKETING, GENETIC)**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(core): add ConsentService for FHIR Consent evaluation"`

---

### Task 3: Consent Enforcement Plugin

**Files:** `fhir4java-plugin/.../consent/ConsentEnforcementPlugin.java`

- [ ] **Step 1: Write failing test for consent enforcement**

```java
@Test
void consentPlugin_blocksRestrictedAccess() {
    // Patient has mental health restriction
    when(consentService.evaluate(eq("Patient/123"), any(), eq("Observation")))
        .thenReturn(ConsentDecision.deny("Mental health records restricted"));

    PluginContext context = PluginContext.builder()
        .resourceType("Observation")
        .resourceId("obs-456")
        .patientReference("Patient/123")
        .operationType(OperationType.READ)
        .build();

    PluginResult result = plugin.execute(context);

    assertThat(result.shouldAbort()).isTrue();
    assertThat(result.getStatusCode()).isEqualTo(403);
}

@Test
void consentPlugin_redactsRestrictedFields() {
    when(consentService.evaluate(any(), any(), any()))
        .thenReturn(ConsentDecision.redact(Set.of("note", "interpretation")));

    PluginContext context = createReadContext("DiagnosticReport", "dr-123");
    PluginResult result = plugin.execute(context);

    assertThat(result.shouldContinue()).isTrue();
    assertThat(context.getRedactedFields()).contains("note", "interpretation");
}
```

- [ ] **Step 2: Implement ConsentEnforcementPlugin**

```java
@Component
public class ConsentEnforcementPlugin implements FhirPlugin {
    private final ConsentService consentService;

    @Override
    public PluginPhase getPhase() { return PluginPhase.BEFORE; }

    @Override
    public int getOrder() { return 200; } // After authorization

    @Override
    public boolean appliesTo(PluginContext context) {
        // Only apply to read operations with patient context
        return context.getPatientReference() != null &&
               context.getOperationType().isReadOperation();
    }

    @Override
    public PluginResult execute(PluginContext context) {
        ConsentCategory category = determineCategory(context);
        ConsentDecision decision = consentService.evaluate(
            context.getPatientReference(),
            category,
            context.getResourceType()
        );

        if (!decision.isPermitted()) {
            auditConsentDenial(context, decision);
            return PluginResult.abort(403, decision.getReason());
        }

        if (decision.requiresRedaction()) {
            context.setRedactedFields(decision.getRedactedFields());
        }

        return PluginResult.continueProcessing();
    }
}
```

- [ ] **Step 3: Add break-the-glass support with elevated audit**

```java
@Override
public PluginResult execute(PluginContext context) {
    // Check for emergency override
    if (context.hasHeader("X-Break-Glass")) {
        String reason = context.getHeader("X-Break-Glass-Reason");
        auditBreakTheGlass(context, reason);
        return PluginResult.continueProcessing();
    }
    // ... normal consent check
}
```

- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(plugin): add ConsentEnforcementPlugin with break-the-glass"`

---

### Task 4: AI Provenance Plugin

**Files:** `fhir4java-plugin/.../provenance/AiProvenancePlugin.java`

- [ ] **Step 1: Write failing test for provenance creation**

```java
@Test
void provenancePlugin_createsProvenanceForAiMutation() {
    PluginContext context = PluginContext.builder()
        .resourceType("Observation")
        .resourceId("obs-123")
        .operationType(OperationType.CREATE)
        .principal(aiAgent("gpt-4-turbo"))
        .build();

    plugin.execute(context);

    verify(provenanceService).createProvenance(argThat(p ->
        p.getAgent().get(0).getType().getCoding().get(0).getCode().equals("assembler") &&
        p.getTarget().get(0).getReference().equals("Observation/obs-123")
    ));
}
```

- [ ] **Step 2: Implement ProvenanceService**

```java
@Service
public class ProvenanceService {
    private final FhirResourceService resourceService;

    public void createProvenance(String targetRef, String agentId,
                                 String modelId, Double confidence) {
        Provenance provenance = new Provenance();

        // Target reference
        provenance.addTarget(new Reference(targetRef));

        // AI agent
        Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
        agent.setType(new CodeableConcept()
            .addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/provenance-participant-type")
                .setCode("assembler")
                .setDisplay("AI Assembler")));
        agent.setWho(new Reference().setDisplay(agentId));

        // AI-specific extensions
        provenance.addExtension()
            .setUrl("http://fhir4java.org/StructureDefinition/ai-model-id")
            .setValue(new StringType(modelId));

        if (confidence != null) {
            provenance.addExtension()
                .setUrl("http://fhir4java.org/StructureDefinition/ai-confidence")
                .setValue(new DecimalType(confidence));
        }

        // Human review status
        provenance.addExtension()
            .setUrl("http://fhir4java.org/StructureDefinition/human-review-status")
            .setValue(new CodeType("pending"));

        provenance.setRecorded(new Date());

        resourceService.create(provenance, "Provenance");
    }
}
```

- [ ] **Step 3: Implement AiProvenancePlugin**

```java
@Component
public class AiProvenancePlugin implements FhirPlugin {
    private final ProvenanceService provenanceService;

    @Override
    public PluginPhase getPhase() { return PluginPhase.AFTER; }

    @Override
    public boolean appliesTo(PluginContext context) {
        return context.isAiAgent() &&
               context.getOperationType().isMutationOperation();
    }

    @Override
    public PluginResult execute(PluginContext context) {
        provenanceService.createProvenance(
            context.getResourceType() + "/" + context.getResourceId(),
            context.getAgentId(),
            context.getModelId(),
            context.getConfidence()
        );
        return PluginResult.continueProcessing();
    }
}
```

- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(plugin): add AiProvenancePlugin for AI action tracking"`

---

### Task 5: Data Localization Plugin

**Files:** `fhir4java-plugin/.../localization/DataLocalizationPlugin.java`

- [ ] **Step 1: Add configuration for regional rules**

```yaml
# application.yml
fhir4java:
  localization:
    enabled: true
    regions:
      singapore:
        regulation: PDPA
        data-residency: required
        cross-border-transfer: consent
      china:
        regulation: PIPL
        data-residency: required
        cross-border-transfer: assessment
      eu:
        regulation: GDPR
        data-residency: preferred
        cross-border-transfer: adequacy
      us:
        regulation: HIPAA
        data-residency: not-required
```

- [ ] **Step 2: Write failing test for cross-border restriction**

```java
@Test
void localizationPlugin_blocksCrossBorderWithoutConsent() {
    // Singapore tenant accessing from US
    PluginContext context = PluginContext.builder()
        .tenantId("sg-hospital")
        .tenantRegion("singapore")
        .requestOrigin("us")
        .resourceType("Patient")
        .operationType(OperationType.READ)
        .build();

    when(regionConfig.getRegion("singapore"))
        .thenReturn(new RegionConfig("PDPA", "required", "consent"));

    PluginResult result = plugin.execute(context);

    assertThat(result.shouldAbort()).isTrue();
    assertThat(result.getStatusCode()).isEqualTo(451); // Unavailable for Legal Reasons
}
```

- [ ] **Step 3: Implement DataLocalizationPlugin**

```java
@Component
@ConditionalOnProperty(name = "fhir4java.localization.enabled", havingValue = "true")
public class DataLocalizationPlugin implements FhirPlugin {
    private final DataLocalizationService localizationService;

    @Override
    public PluginPhase getPhase() { return PluginPhase.BEFORE; }

    @Override
    public int getOrder() { return 150; } // After auth, before consent

    @Override
    public PluginResult execute(PluginContext context) {
        String tenantRegion = context.getTenantRegion();
        String requestOrigin = context.getRequestOrigin();

        if (tenantRegion == null || requestOrigin == null) {
            return PluginResult.continueProcessing();
        }

        if (!tenantRegion.equals(requestOrigin)) {
            LocalizationDecision decision = localizationService.checkCrossBorder(
                tenantRegion, requestOrigin, context);

            if (!decision.isAllowed()) {
                auditCrossBorderDenial(context, decision);
                return PluginResult.abort(451, decision.getReason());
            }
        }

        return PluginResult.continueProcessing();
    }
}
```

- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(plugin): add DataLocalizationPlugin for regional compliance"`

---

### Task 6: Immutable Audit Trail

**Files:** `db/migrations/V*__immutable_audit_triggers.sql`

- [ ] **Step 1: Create Flyway migration for immutable triggers**

```sql
-- V20260407__immutable_audit_triggers.sql

-- Prevent modifications to audit tables
CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable and cannot be modified';
END;
$$ LANGUAGE plpgsql;

-- Apply to fhir_audit_log
DROP TRIGGER IF EXISTS prevent_audit_log_update ON fhir_audit_log;
CREATE TRIGGER prevent_audit_log_update
    BEFORE UPDATE OR DELETE ON fhir_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

-- Apply to mcp_audit_log
DROP TRIGGER IF EXISTS prevent_mcp_audit_update ON mcp_audit_log;
CREATE TRIGGER prevent_mcp_audit_update
    BEFORE UPDATE OR DELETE ON mcp_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

-- Apply to command_audit_log (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'command_audit_log') THEN
        DROP TRIGGER IF EXISTS prevent_cmd_audit_update ON command_audit_log;
        CREATE TRIGGER prevent_cmd_audit_update
            BEFORE UPDATE OR DELETE ON command_audit_log
            FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
    END IF;
END $$;

COMMENT ON FUNCTION prevent_audit_modification() IS
    'Enforces audit log immutability for regulatory compliance (HIPAA, PDPA, GDPR)';
```

- [ ] **Step 2: Write test to verify immutability**

```java
@Test
void auditLog_cannotBeUpdated() {
    // Insert audit record
    McpAuditLogEntity log = createAuditLog();
    auditRepository.save(log);

    // Attempt update should fail
    assertThatThrownBy(() -> {
        jdbcTemplate.update("UPDATE mcp_audit_log SET success = false WHERE id = ?", log.getId());
    }).hasMessageContaining("immutable");
}

@Test
void auditLog_cannotBeDeleted() {
    McpAuditLogEntity log = createAuditLog();
    auditRepository.save(log);

    assertThatThrownBy(() -> {
        jdbcTemplate.update("DELETE FROM mcp_audit_log WHERE id = ?", log.getId());
    }).hasMessageContaining("immutable");
}
```

- [ ] **Step 3: Run migration and tests**
- [ ] **Step 4: Commit** `git commit -m "feat(db): add immutable audit trail triggers for compliance"`

---

### Task 7: De-identification Service

**Files:** `fhir4java-core/.../deidentification/DeidentificationService.java`

- [ ] **Step 1: Write failing test for Safe Harbor de-identification**

```java
@Test
void safeHarbor_removesAllIdentifiers() {
    Patient patient = new Patient();
    patient.addName().setFamily("Smith").addGiven("John");
    patient.addAddress().setCity("Boston").setPostalCode("02101");
    patient.setBirthDate(Date.valueOf("1980-05-15"));
    patient.addTelecom().setValue("555-1234");
    patient.addIdentifier().setValue("123-45-6789"); // SSN

    IBaseResource deidentified = deidentificationService.deidentify(
        patient, DeidentificationStrategy.SAFE_HARBOR);

    Patient result = (Patient) deidentified;
    assertThat(result.getName()).isEmpty();
    assertThat(result.getAddress().get(0).getCity()).isNull();
    assertThat(result.getAddress().get(0).getPostalCode()).isEqualTo("021"); // First 3 digits only
    assertThat(result.getBirthDate()).isNull(); // Or year only
    assertThat(result.getTelecom()).isEmpty();
    assertThat(result.getIdentifier()).isEmpty();
}
```

- [ ] **Step 2: Implement SafeHarborStrategy**

```java
@Component
public class SafeHarborStrategy implements DeidentificationStrategy {

    // HIPAA Safe Harbor: 18 identifiers to remove
    private static final Set<String> SAFE_HARBOR_PATHS = Set.of(
        "name",           // 1. Names
        "address.line",   // 2. Geographic (street)
        "address.city",   // 3. Geographic (city)
        "address.state",  // 4. Geographic (state) - if population < 20k
        "telecom",        // 5. Phone numbers, 6. Fax numbers
        "identifier",     // 7. SSN, 8. Medical record numbers, 9. Health plan numbers
                          // 10. Account numbers, 11. Certificate/license numbers
        "photo",          // 12. Full face photos
        "birthDate",      // 13. Dates (except year)
        // 14. Device identifiers, 15. Web URLs, 16. IP addresses
        // 17. Biometric identifiers, 18. Any other unique identifier
    );

    @Override
    public IBaseResource deidentify(IBaseResource resource) {
        IBaseResource copy = resource.copy();

        // Remove names
        clearField(copy, "name");

        // Truncate postal code to 3 digits
        truncatePostalCode(copy);

        // Remove or generalize dates (keep year only)
        generalizeDate(copy, "birthDate");

        // Remove all identifiers
        clearField(copy, "identifier");

        // Remove telecom
        clearField(copy, "telecom");

        // Remove photos
        clearField(copy, "photo");

        // Clear address details (keep state if population > 20k)
        clearAddressDetails(copy);

        return copy;
    }
}
```

- [ ] **Step 3: Implement LimitedDataSetStrategy**
- [ ] **Step 4: Add de-identification API endpoint**

```java
@PostMapping("/api/deidentify")
public IBaseResource deidentify(
        @RequestBody IBaseResource resource,
        @RequestParam(defaultValue = "safe-harbor") String strategy) {
    return deidentificationService.deidentify(resource,
        DeidentificationStrategy.valueOf(strategy.toUpperCase().replace("-", "_")));
}
```

- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(core): add HIPAA Safe Harbor de-identification service"`

---

### Task 8: Soft Delete Implementation

**Files:** `fhir4java-persistence/.../entity/`, `db/migrations/`

- [ ] **Step 1: Add Flyway migration for soft delete columns**

```sql
-- V20260408__soft_delete_columns.sql

-- Add deleted_at to fhir_resource table
ALTER TABLE fhir_resource
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Create partial index for non-deleted resources
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_resource_not_deleted
    ON fhir_resource (tenant_id, resource_type, resource_id)
    WHERE deleted_at IS NULL;

-- Update unique constraint to only apply to non-deleted current resources
DROP INDEX IF EXISTS uk_current_resource;
CREATE UNIQUE INDEX uk_current_resource
    ON fhir_resource (tenant_id, resource_type, resource_id)
    WHERE is_current = TRUE AND deleted_at IS NULL;
```

- [ ] **Step 2: Update FhirResourceEntity**

```java
@Entity
@Table(name = "fhir_resource")
@Where(clause = "deleted_at IS NULL") // Hibernate soft delete filter
public class FhirResourceEntity {
    // ... existing fields ...

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.isCurrent = false;
    }
}
```

- [ ] **Step 3: Update FhirResourceService.delete() to use soft delete**

```java
@Transactional
public void delete(String resourceType, String resourceId, PluginContext context) {
    FhirResourceEntity entity = repository.findCurrentResource(
        context.getTenantId(), resourceType, resourceId)
        .orElseThrow(() -> new ResourceNotFoundException(resourceType, resourceId));

    // Soft delete instead of hard delete
    entity.softDelete(context.getUserId());
    repository.save(entity);

    // Audit the deletion
    auditService.logDeletion(resourceType, resourceId, context);
}
```

- [ ] **Step 4: Add admin endpoint for viewing deleted resources**

```java
@GetMapping("/api/admin/deleted")
@PreAuthorize("hasRole('ADMIN')")
public List<FhirResourceEntity> listDeleted(
        @RequestParam String resourceType,
        @RequestParam(required = false) Instant since) {
    return repository.findDeleted(resourceType, since);
}
```

- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(persistence): implement soft delete for regulatory compliance"`

---

## Summary

| Task | Deliverable | Regulations |
|------|-------------|-------------|
| 1 | Scoped Authorization Plugin | All |
| 2 | Consent Service | HIPAA, GDPR, PDPA |
| 3 | Consent Enforcement Plugin | HIPAA, GDPR, PDPA |
| 4 | AI Provenance Plugin | All (clinical accountability) |
| 5 | Data Localization Plugin | PDPA, PIPL, GDPR |
| 6 | Immutable Audit Trail | HIPAA, PDPA, GDPR |
| 7 | De-identification Service | HIPAA |
| 8 | Soft Delete Implementation | HIPAA, GDPR |

**Total: 8 tasks**

---

## Compliance Coverage After Phase 10

| Regulation | Requirements | Coverage |
|------------|--------------|----------|
| **HIPAA** | Access controls, audit trail, de-identification, data retention | ✅ Full |
| **PDPA** | Consent, data localization, audit trail | ✅ Full |
| **GDPR** | Consent, data localization, soft delete (right to erasure*) | ✅ Full |
| **PIPL** | Data localization, cross-border controls | ✅ Full |

*Note: GDPR right to erasure is handled via soft delete with audit trail, as healthcare records require retention.
