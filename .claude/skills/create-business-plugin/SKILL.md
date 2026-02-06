# Create Business Logic Plugin

Guide the user through creating a business logic plugin that intercepts FHIR operations to implement custom validation, transformation, or integration logic.

## Overview

Business logic plugins allow you to:
- **Validate** resources beyond standard FHIR profile validation
- **Transform** or enrich resources before they are saved
- **Integrate** with external systems (notifications, sync)
- **Enforce** business rules specific to your organization
- **Trigger** workflows based on FHIR operations

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Plugin Identity

Gather basic information about the plugin:

| Field | Description | Example |
|-------|-------------|---------|
| Plugin Name | Unique identifier (kebab-case) | `medication-request-validator` |
| Description | What the plugin does | "Validates MedicationRequest prescriptions" |
| Target Resources | Which resource types to intercept | `MedicationRequest`, `Patient` |
| Operations | Which operations to intercept | `CREATE`, `UPDATE` |

**Available Operations:**
- `CREATE` - New resource creation
- `UPDATE` - Resource updates
- `DELETE` - Resource deletion
- `READ` - Resource reads
- `SEARCH` - Search operations
- `VREAD` - Version reads
- `HISTORY` - History operations
- `OPERATION` - Extended operations ($validate, etc.)

### Step 2: Plugin Behavior - Before Operation

Ask what the plugin should do **BEFORE** the operation executes:

#### 2a. Validation Rules

For each validation rule, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Rule Name | Short identifier | `require-prescriber` |
| Description | What it validates | "MedicationRequest must have a prescriber" |
| Condition | When to apply (FHIRPath or plain English) | "When status is 'active'" |
| Check | What to check (FHIRPath or plain English) | "requester must exist" |
| Error Message | Message if validation fails | "Active prescriptions require a prescriber" |
| Severity | `error` (abort) or `warning` (log only) | `error` |

**Example validations:**
```
1. Rule: require-family-name
   Description: Patient must have a family name
   Check: name.where(family.exists()).exists()
   Error: "Patient must have at least one name with a family name"
   Severity: error

2. Rule: ssn-format
   Description: SSN must be in XXX-XX-XXXX format
   Condition: identifier.where(system='http://hl7.org/fhir/sid/us-ssn').exists()
   Check: identifier.where(system='http://hl7.org/fhir/sid/us-ssn').value.matches('\\d{3}-\\d{2}-\\d{4}')
   Error: "Invalid SSN format. Expected XXX-XX-XXXX"
   Severity: error
```

#### 2b. Transformations/Enrichments

For each transformation, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Name | Short identifier | `auto-generate-mrn` |
| Description | What it does | "Generate MRN if not present" |
| Condition | When to apply | "When no MRN identifier exists" |
| Action | What to do | "Add identifier with system urn:example:mrn" |
| Value Source | Where value comes from | "Generated: MRN-{date}-{random}" |

**Example transformations:**
```
1. Name: auto-generate-mrn
   Description: Auto-generate Medical Record Number
   Condition: No identifier with system 'urn:fhir4java:patient:mrn' exists
   Action: Add identifier
   Value: MRN-{YYYYMMDD}-{5-char-random}

2. Name: normalize-phone
   Description: Remove non-digits from phone numbers
   Condition: Always for phone ContactPoints
   Action: Replace value with digits only

3. Name: add-creation-timestamp
   Description: Add creation timestamp extension
   Condition: Extension not already present
   Action: Add extension with current timestamp
```

#### 2c. Duplicate Checks

Ask if the plugin should check for duplicates:

| Property | Description | Example |
|----------|-------------|---------|
| Enabled | Check for duplicates? | `true` |
| Identifier Systems | Which identifier systems to check | `http://hl7.org/fhir/sid/us-ssn` |
| Error Message | Message if duplicate found | "Patient with this SSN already exists" |

### Step 3: Plugin Behavior - After Operation

Ask what the plugin should do **AFTER** the operation completes successfully:

#### 3a. Logging

| Property | Description | Example |
|----------|-------------|---------|
| Log Success | Log successful operations? | `true` |
| Log Level | INFO, DEBUG, WARN | `INFO` |
| Log Fields | What to include in log | Resource ID, resource type, user |

#### 3b. Notifications (Future)

| Property | Description | Example |
|----------|-------------|---------|
| Webhook URL | URL to call after operation | `https://api.example.com/fhir-events` |
| Events | Which outcomes trigger notification | `success`, `created` |
| Payload | What to send | Full resource or summary |

### Step 4: Configuration Options

Ask about configurable options (exposed via application.yml):

| Property | Description | Default | Example |
|----------|-------------|---------|---------|
| `enabled` | Enable/disable plugin | `true` | `true` |
| Custom options | Plugin-specific settings | varies | `require-family-name: true` |

**Configuration will be added to:**
```yaml
fhir4java:
  plugins:
    {plugin-name}:
      enabled: true
      # Custom options here
```

### Step 5: Priority

Ask about execution priority:

| Priority | Range | Description |
|----------|-------|-------------|
| High | 10-30 | Run early (e.g., critical validation) |
| Normal | 40-60 | Standard business logic (default: 50) |
| Low | 70-90 | Run late (e.g., enrichment after other plugins) |

### Step 6: Generate Plugin Files

After gathering all information, generate:

#### 1. Java Plugin Class
Location: `fhir4java-plugin/src/main/java/org/fhirframework/plugin/business/{PluginClassName}.java`

#### 2. Configuration Properties
Add to: `fhir4java-server/src/main/resources/application.yml`

#### 3. BDD Test Feature (optional)
Location: `fhir4java-server/src/test/resources/features/plugins/{plugin-name}.feature`

---

## Templates

### Plugin Class Template

```java
package org.fhirframework.plugin.business;

import org.fhirframework.plugin.OperationDescriptor;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.business.BusinessContext;
import org.fhirframework.plugin.business.BusinessLogicPlugin;
import org.fhirframework.plugin.business.OperationResult;
import org.hl7.fhir.r5.model.{ResourceType};
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {Description}
 * <p>
 * Validates and enriches {ResourceType} resources during {Operations}.
 * </p>
 */
@Component
public class {PluginClassName} implements BusinessLogicPlugin {

    private static final Logger log = LoggerFactory.getLogger({PluginClassName}.class);

    @Value("${fhir4java.plugins.{plugin-name}.enabled:true}")
    private boolean enabled;

    // Add custom configuration properties here
    // @Value("${fhir4java.plugins.{plugin-name}.{property-name}:{default}}")
    // private {Type} {propertyName};

    @Override
    public String getName() {
        return "{plugin-name}";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return {priority}; // 10-30: high, 40-60: normal, 70-90: low
    }

    @Override
    public List<OperationDescriptor> getSupportedOperations() {
        return List.of(
            OperationDescriptor.forCrud("{ResourceType}", OperationType.{OPERATION}, null)
            // Add more operations as needed
        );
    }

    @Override
    public BusinessResult beforeOperation(BusinessContext context) {
        log.debug("{PluginClassName}: beforeOperation for request {}", context.getRequestId());

        // Get the input resource
        {ResourceType} resource = context.getCurrentResource()
                .filter(r -> r instanceof {ResourceType})
                .map(r -> ({ResourceType}) r)
                .orElse(null);

        if (resource == null) {
            return BusinessResult.abort("Expected {ResourceType} resource");
        }

        // === VALIDATION RULES ===
        // {validation-rule-1}
        // if (!{condition}) {
        //     return BusinessResult.unprocessable("{error-message}");
        // }

        // === TRANSFORMATIONS ===
        // {transformation-1}
        // if ({condition}) {
        //     // Apply transformation
        // }

        // === DUPLICATE CHECK ===
        // if ({duplicateCheckEnabled}) {
        //     // Check for duplicates
        // }

        log.info("{PluginClassName}: Validated and enriched {ResourceType} for request {}",
                context.getRequestId());

        // Return modified resource
        return BusinessResult.proceedWithResource(resource);
    }

    @Override
    public BusinessResult afterOperation(BusinessContext context, OperationResult result) {
        if (result.isSuccess()) {
            result.getResource().ifPresent(resource -> {
                if (resource instanceof {ResourceType} r) {
                    String id = r.getIdElement().getIdPart();
                    log.info("{PluginClassName}: Successfully processed {ResourceType}/{}", id);
                }
            });
        }
        return BusinessResult.proceed();
    }

    // === PRIVATE HELPER METHODS ===

    // private BusinessResult validate{RuleName}({ResourceType} resource) {
    //     // Validation logic here
    //     return BusinessResult.proceed();
    // }

    // private void enrich{TransformationName}({ResourceType} resource) {
    //     // Enrichment logic here
    // }
}
```

### Configuration Template

```yaml
# Add to fhir4java-server/src/main/resources/application.yml

fhir4java:
  plugins:
    {plugin-name}:
      enabled: true
      # Validation options
      # {validation-option}: true
      # Transformation options
      # {transformation-option}: true
      # Duplicate check options
      # duplicate-check-enabled: true
      # duplicate-check-systems:
      #   - "http://example.org/identifier-system"
```

### BDD Test Feature Template

```gherkin
Feature: {Plugin Name}
  As a FHIR server operator
  I want {description}
  So that {benefit}

  Background:
    Given the FHIR server is running

  Scenario: {ResourceType} {Operation} with valid data
    Given I have a {ResourceType} resource with {valid-data}
    When I {operation} the {ResourceType} resource
    Then the response status should be {expected-status}
    And the {ResourceType} should have {expected-result}

  Scenario: {ResourceType} {Operation} fails validation
    Given I have a {ResourceType} resource with {invalid-data}
    When I {operation} the {ResourceType} resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "{error-message}"

  Scenario: {ResourceType} {Operation} with enrichment
    Given I have a {ResourceType} resource with {data-needing-enrichment}
    When I {operation} the {ResourceType} resource
    Then the response status should be {expected-status}
    And the {ResourceType} should have {enriched-data}
```

---

## Examples

### Example 1: MedicationRequest Validator

**User Input:**
- Plugin Name: `medication-request-validator`
- Target Resource: `MedicationRequest`
- Operations: `CREATE`, `UPDATE`
- Validations:
  1. Active prescriptions must have a prescriber
  2. Quantity must be positive
- Transformations:
  1. Set authoredOn to current timestamp if not provided

**Generated Plugin:**
```java
@Component
public class MedicationRequestValidatorPlugin implements BusinessLogicPlugin {

    @Value("${fhir4java.plugins.medication-request-validator.enabled:true}")
    private boolean enabled;

    @Value("${fhir4java.plugins.medication-request-validator.require-prescriber:true}")
    private boolean requirePrescriber;

    @Override
    public String getName() {
        return "medication-request-validator";
    }

    @Override
    public List<OperationDescriptor> getSupportedOperations() {
        return List.of(
            OperationDescriptor.forCrud("MedicationRequest", OperationType.CREATE, null),
            OperationDescriptor.forCrud("MedicationRequest", OperationType.UPDATE, null)
        );
    }

    @Override
    public BusinessResult beforeOperation(BusinessContext context) {
        MedicationRequest rx = context.getCurrentResource()
                .filter(r -> r instanceof MedicationRequest)
                .map(r -> (MedicationRequest) r)
                .orElse(null);

        if (rx == null) {
            return BusinessResult.abort("Expected MedicationRequest resource");
        }

        // Validation 1: Active prescriptions must have prescriber
        if (requirePrescriber && rx.getStatus() == MedicationRequest.MedicationrequestStatus.ACTIVE) {
            if (!rx.hasRequester()) {
                return BusinessResult.unprocessable(
                    "Active prescriptions must have a prescriber (requester)"
                );
            }
        }

        // Validation 2: Quantity must be positive
        if (rx.hasDispenseRequest() && rx.getDispenseRequest().hasQuantity()) {
            var qty = rx.getDispenseRequest().getQuantity();
            if (qty.hasValue() && qty.getValue().doubleValue() <= 0) {
                return BusinessResult.unprocessable("Quantity must be positive");
            }
        }

        // Transformation: Set authoredOn if not provided
        if (!rx.hasAuthoredOn()) {
            rx.setAuthoredOn(new Date());
        }

        return BusinessResult.proceedWithResource(rx);
    }

    @Override
    public BusinessResult afterOperation(BusinessContext context, OperationResult result) {
        return BusinessResult.proceed();
    }
}
```

### Example 2: Observation Auto-Interpreter

**User Input:**
- Plugin Name: `observation-interpreter`
- Target Resource: `Observation`
- Operations: `CREATE`
- Transformations:
  1. Add interpretation code based on reference range

**Generated enrichment logic:**
```java
private void addInterpretation(Observation obs) {
    if (!obs.hasReferenceRange() || !obs.hasValueQuantity()) {
        return;
    }

    var value = obs.getValueQuantity().getValue().doubleValue();
    var range = obs.getReferenceRangeFirstRep();

    String interpretation = null;
    if (range.hasLow() && value < range.getLow().getValue().doubleValue()) {
        interpretation = "L"; // Low
    } else if (range.hasHigh() && value > range.getHigh().getValue().doubleValue()) {
        interpretation = "H"; // High
    } else {
        interpretation = "N"; // Normal
    }

    if (interpretation != null && !obs.hasInterpretation()) {
        obs.addInterpretation()
            .addCoding()
            .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
            .setCode(interpretation);
    }
}
```

---

## Business Result Options

When returning from `beforeOperation` or `afterOperation`, use these result types:

| Method | HTTP Status | Use Case |
|--------|-------------|----------|
| `BusinessResult.proceed()` | - | Continue normally |
| `BusinessResult.proceedWithResource(resource)` | - | Continue with modified resource |
| `BusinessResult.abort(message)` | 400 | General validation error |
| `BusinessResult.unprocessable(message)` | 422 | Semantic validation error |
| `BusinessResult.forbidden(message)` | 403 | Authorization/permission error |
| `BusinessResult.conflict(message)` | 409 | Duplicate/conflict error |
| `BusinessResult.abort(message, httpStatus)` | custom | Custom error code |

---

## After Creating the Plugin

Remind the user to:

1. **Build the Project**:
   ```bash
   ./mvnw clean compile -pl fhir4java-plugin
   ```

2. **Run Tests**:
   ```bash
   ./mvnw test -pl fhir4java-server -Dtest=CucumberIT
   ```

3. **Start the Server**:
   ```bash
   ./mvnw spring-boot:run -pl fhir4java-server
   ```

4. **Test the Plugin**:
   - Create a resource that should pass validation
   - Create a resource that should fail validation
   - Verify enrichments are applied

5. **Monitor Logs**:
   - Check for plugin execution logs
   - Verify validation messages appear correctly

---

## Context Available in Plugins

The `BusinessContext` provides access to:

| Method | Returns | Description |
|--------|---------|-------------|
| `getRequestId()` | `String` | Unique request ID |
| `getOperationType()` | `OperationType` | CREATE, UPDATE, etc. |
| `getFhirVersion()` | `FhirVersion` | R4B or R5 |
| `getResourceType()` | `String` | "Patient", "Observation", etc. |
| `getResourceId()` | `Optional<String>` | Resource ID (for updates) |
| `getTenantId()` | `Optional<String>` | Current tenant |
| `getUserId()` | `Optional<String>` | Current user |
| `getCurrentResource()` | `Optional<IBaseResource>` | The resource being processed |
| `getOriginalResource()` | `Optional<IBaseResource>` | Original (before modifications) |
| `getSearchParameters()` | `Map<String, String[]>` | Search params (for searches) |
