# Generate BDD Tests

Guide the user through creating Cucumber BDD (Behavior-Driven Development) test features and step definitions for FHIR resources, operations, and plugins.

## Overview

BDD tests help you:
- **Verify** that FHIR resources work as expected
- **Document** behavior in plain English
- **Catch** regressions when code changes
- **Validate** business rules and workflows

## Test Structure

```
fhir4java-server/src/test/
├── java/org/fhirframework/server/bdd/
│   ├── CucumberIT.java           # Test runner
│   ├── CucumberSpringConfig.java # Spring configuration
│   └── steps/
│       ├── SharedTestContext.java # Shared state
│       ├── OperationSteps.java    # Operation step definitions
│       ├── PluginSteps.java       # Plugin step definitions
│       └── {Custom}Steps.java     # Your custom steps
└── resources/features/
    ├── operations/                # Operation tests
    ├── plugins/                   # Plugin tests
    ├── validation/                # Validation tests
    └── {custom}/                  # Your custom tests
```

## Instructions

When the user invokes this skill, walk them through the following steps interactively:

### Step 1: Determine Test Type

Ask what type of tests to generate:

| Type | Description | Example |
|------|-------------|---------|
| **Resource CRUD** | Test create, read, update, delete | Patient CRUD tests |
| **Search** | Test search parameters | Patient search by name |
| **Operation** | Test extended operations | $validate, $everything |
| **Plugin** | Test business logic plugins | Patient validation plugin |
| **Validation** | Test profile validation | US Core Patient validation |
| **Custom** | Custom test scenarios | Workflow tests |

### Step 2: Gather Test Information

Based on the test type, gather:

#### For Resource CRUD Tests:

| Field | Description | Example |
|-------|-------------|---------|
| Resource Type | Which FHIR resource | `Patient`, `Observation` |
| Required Fields | Fields that must be present | `name`, `birthDate` |
| Optional Fields | Fields to test optionally | `telecom`, `address` |
| Business Rules | Validation rules to test | "Must have family name" |

#### For Search Tests:

| Field | Description | Example |
|-------|-------------|---------|
| Resource Type | Which FHIR resource | `Patient` |
| Search Parameters | Parameters to test | `family`, `identifier`, `birthdate` |
| Modifiers | Modifiers to test | `:exact`, `:contains` |
| Combinations | Combined searches | `family=Smith&birthdate=1990` |

#### For Operation Tests:

| Field | Description | Example |
|-------|-------------|---------|
| Operation | Which operation | `$validate`, `$everything` |
| Level | System, type, or instance | `TYPE`, `INSTANCE` |
| Input Params | Parameters to provide | `resource`, `profile` |
| Expected Output | What to verify | OperationOutcome, Bundle |

#### For Plugin Tests:

| Field | Description | Example |
|-------|-------------|---------|
| Plugin Name | Which plugin | `patient-create-plugin` |
| Trigger | What triggers the plugin | Patient CREATE |
| Validations | Rules to test | "Require family name" |
| Enrichments | Auto-populated fields | MRN identifier |

### Step 3: Define Test Scenarios

For each test scenario, gather:

| Property | Description | Example |
|----------|-------------|---------|
| Name | Scenario name | "Create Patient with valid data" |
| Given | Preconditions | "I have a Patient resource with family name 'Smith'" |
| When | Action | "I create the Patient resource" |
| Then | Expected outcome | "the response status should be 201" |

**Scenario Templates:**

#### Success Scenario:
```gherkin
Scenario: {Action} with valid data
  Given I have a {ResourceType} resource with {valid-data}
  When I {action} the {ResourceType} resource
  Then the response status should be {success-status}
  And the response should be a {ResourceType}
```

#### Failure Scenario:
```gherkin
Scenario: {Action} fails with invalid data
  Given I have a {ResourceType} resource with {invalid-data}
  When I {action} the {ResourceType} resource
  Then the response status should be {error-status}
  And the response should be an OperationOutcome
  And the OperationOutcome should contain message "{error-message}"
```

### Step 4: Generate Files

After gathering all information, generate:

#### 1. Feature File
Location: `fhir4java-server/src/test/resources/features/{category}/{feature-name}.feature`

#### 2. Step Definitions (if new steps needed)
Location: `fhir4java-server/src/test/java/org/fhirframework/server/bdd/steps/{FeatureName}Steps.java`

---

## Templates

### Feature File Template

```gherkin
Feature: {Feature Title}
  As a {role}
  I want to {capability}
  So that {benefit}

  Background:
    Given the FHIR server is running

  Scenario: {Scenario 1 Name}
    Given {precondition}
    When {action}
    Then {expected result}
    And {additional verification}

  Scenario: {Scenario 2 Name}
    Given {precondition}
    When {action}
    Then {expected result}
```

### Resource CRUD Feature Template

```gherkin
Feature: {ResourceType} CRUD Operations
  As a FHIR API consumer
  I want to create, read, update, and delete {ResourceType} resources
  So that I can manage {resource-description}

  Background:
    Given the FHIR server is running

  # ========== CREATE ==========

  Scenario: Create {ResourceType} with minimum required fields
    Given I have a {ResourceType} resource with {minimum-required-fields}
    When I create the {ResourceType} resource
    Then the response status should be 201
    And the response should be a {ResourceType}
    And the {ResourceType} should have an id
    And the {ResourceType} should have a meta.versionId

  Scenario: Create {ResourceType} with all fields
    Given I have a {ResourceType} resource with {all-fields}
    When I create the {ResourceType} resource
    Then the response status should be 201
    And the {ResourceType} should have {field} equal to {value}

  Scenario: Create {ResourceType} fails with missing required field
    Given I have a {ResourceType} resource without {required-field}
    When I create the {ResourceType} resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "{error-message}"

  # ========== READ ==========

  Scenario: Read existing {ResourceType}
    Given a {ResourceType} resource exists
    When I read the {ResourceType} by id
    Then the response status should be 200
    And the response should be a {ResourceType}

  Scenario: Read non-existing {ResourceType} returns 404
    When I read {ResourceType} with id "non-existent-id"
    Then the response status should be 404
    And the response should be an OperationOutcome

  # ========== UPDATE ==========

  Scenario: Update existing {ResourceType}
    Given a {ResourceType} resource exists
    And I have an updated {ResourceType} resource
    When I update the {ResourceType} resource
    Then the response status should be 200
    And the {ResourceType} should have the updated values

  # ========== DELETE ==========

  Scenario: Delete existing {ResourceType}
    Given a {ResourceType} resource exists
    When I delete the {ResourceType} resource
    Then the response status should be 204
    And reading the {ResourceType} returns 410 Gone
```

### Search Feature Template

```gherkin
Feature: {ResourceType} Search
  As a FHIR API consumer
  I want to search for {ResourceType} resources
  So that I can find resources matching specific criteria

  Background:
    Given the FHIR server is running
    And multiple {ResourceType} resources exist with varying data

  # ========== String Search ==========

  Scenario: Search {ResourceType} by {string-param}
    When I search for {ResourceType} with {string-param}="{value}"
    Then the response status should be 200
    And the response should be a Bundle
    And all results should have {string-param} containing "{value}"

  Scenario: Search {ResourceType} by {string-param} with :exact modifier
    When I search for {ResourceType} with {string-param}:exact="{exact-value}"
    Then the response status should be 200
    And all results should have {string-param} exactly "{exact-value}"

  # ========== Token Search ==========

  Scenario: Search {ResourceType} by {token-param} with system and code
    When I search for {ResourceType} with {token-param}="{system}|{code}"
    Then the response status should be 200
    And all results should have {token-param} with system "{system}" and code "{code}"

  Scenario: Search {ResourceType} by {token-param} code only
    When I search for {ResourceType} with {token-param}="{code}"
    Then the response status should be 200
    And all results should have {token-param} with code "{code}"

  # ========== Date Search ==========

  Scenario: Search {ResourceType} by {date-param} equals
    When I search for {ResourceType} with {date-param}="{date}"
    Then the response status should be 200
    And all results should have {date-param} matching "{date}"

  Scenario: Search {ResourceType} by {date-param} range
    When I search for {ResourceType} with {date-param}=gt{start-date}&{date-param}=lt{end-date}
    Then the response status should be 200
    And all results should have {date-param} between "{start-date}" and "{end-date}"

  # ========== Reference Search ==========

  Scenario: Search {ResourceType} by {reference-param}
    Given a {ReferencedType} resource exists
    When I search for {ResourceType} with {reference-param}={ReferencedType}/{id}
    Then the response status should be 200
    And all results should reference the {ReferencedType}

  # ========== Combined Search ==========

  Scenario: Search {ResourceType} with multiple parameters
    When I search for {ResourceType} with {param1}="{value1}"&{param2}="{value2}"
    Then the response status should be 200
    And all results should match both criteria

  # ========== No Results ==========

  Scenario: Search {ResourceType} returns empty bundle when no matches
    When I search for {ResourceType} with {param}="{non-matching-value}"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should have 0 entries
```

### Operation Feature Template

```gherkin
Feature: {ResourceType} ${operation-code} Operation
  As a FHIR API consumer
  I want to invoke the ${operation-code} operation
  So that {benefit}

  Background:
    Given the FHIR server is running

  # ========== Type-Level Operation ==========

  Scenario: ${operation-code} at type level with valid parameters
    Given I have valid parameters for ${operation-code}
    When I invoke ${operation-code} on {ResourceType}
    Then the response status should be 200
    And the response should be a {ExpectedResponseType}

  Scenario: ${operation-code} at type level with missing required parameter
    Given I have parameters for ${operation-code} without {required-param}
    When I invoke ${operation-code} on {ResourceType}
    Then the response status should be 400
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "{error-message}"

  # ========== Instance-Level Operation ==========

  Scenario: ${operation-code} at instance level
    Given a {ResourceType} resource exists
    When I invoke ${operation-code} on {ResourceType}/{id}
    Then the response status should be 200
    And the response should contain expected data

  Scenario: ${operation-code} on non-existent resource
    When I invoke ${operation-code} on {ResourceType}/non-existent
    Then the response status should be 404
```

### Plugin Feature Template

```gherkin
Feature: {Plugin Name}
  As a FHIR server operator
  I want {plugin-description}
  So that {benefit}

  Background:
    Given the FHIR server is running

  # ========== Validation Rules ==========

  Scenario: {ResourceType} {Operation} with valid data passes validation
    Given I have a {ResourceType} resource with {valid-data}
    When I {operation} the {ResourceType} resource
    Then the response status should be {success-status}
    And the {ResourceType} should have {expected-enrichment}

  Scenario: {ResourceType} {Operation} fails validation for {rule-name}
    Given I have a {ResourceType} resource with {invalid-data}
    When I {operation} the {ResourceType} resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "{validation-error}"

  # ========== Enrichment ==========

  Scenario: {ResourceType} {Operation} auto-generates {field}
    Given I have a {ResourceType} resource without {auto-generated-field}
    When I {operation} the {ResourceType} resource
    Then the response status should be {success-status}
    And the {ResourceType} should have {auto-generated-field}
    And the {auto-generated-field} value should match pattern "{expected-pattern}"

  Scenario: {ResourceType} {Operation} does not override existing {field}
    Given I have a {ResourceType} resource with {existing-field-value}
    When I {operation} the {ResourceType} resource
    Then the response status should be {success-status}
    And the {ResourceType} should have exactly 1 {field}
    And the {field} value should be "{existing-value}"

  # ========== Transformation ==========

  Scenario: {ResourceType} {Operation} normalizes {field}
    Given I have a {ResourceType} resource with {unnormalized-field}
    When I {operation} the {ResourceType} resource
    Then the response status should be {success-status}
    And the {field} should be normalized to "{normalized-value}"
```

### Step Definitions Template

```java
package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Step definitions for {Feature} BDD tests.
 */
public class {FeatureName}Steps {

    @Autowired
    private SharedTestContext ctx;

    // ========== Given Steps ==========

    @Given("I have a {ResourceType} resource with {description}")
    public void iHaveResourceWith(String resourceType, String description) {
        String json = buildResourceJson(resourceType, description);
        ctx.setRequestBody(json);
    }

    @Given("a {ResourceType} resource exists")
    public void resourceExists(String resourceType) {
        String json = buildMinimalResourceJson(resourceType);
        Response response = given()
            .contentType("application/fhir+json")
            .body(json)
            .when()
            .post("/" + resourceType);
        response.then().statusCode(201);
        ctx.setLastResponse(response);
        ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
    }

    // ========== When Steps ==========

    @When("I create the {ResourceType} resource")
    public void iCreateResource(String resourceType) {
        ctx.setLastResponse(given()
            .contentType("application/fhir+json")
            .body(ctx.getRequestBody())
            .when()
            .post("/" + resourceType));
    }

    @When("I read the {ResourceType} by id")
    public void iReadResourceById(String resourceType) {
        ctx.setLastResponse(given()
            .accept("application/fhir+json")
            .when()
            .get("/" + resourceType + "/" + ctx.getLastCreatedResourceId()));
    }

    @When("I search for {ResourceType} with {string}")
    public void iSearchResourceWith(String resourceType, String queryParams) {
        ctx.setLastResponse(given()
            .accept("application/fhir+json")
            .when()
            .get("/" + resourceType + "?" + queryParams));
    }

    @When("I invoke ${string} on {ResourceType}")
    public void iInvokeOperationOnType(String operation, String resourceType) {
        ctx.setLastResponse(given()
            .contentType("application/fhir+json")
            .body(ctx.getRequestBody())
            .when()
            .post("/" + resourceType + "/$" + operation));
    }

    @When("I invoke ${string} on {ResourceType}/{string}")
    public void iInvokeOperationOnInstance(String operation, String resourceType, String id) {
        ctx.setLastResponse(given()
            .contentType("application/fhir+json")
            .body(ctx.getRequestBody())
            .when()
            .post("/" + resourceType + "/" + id + "/$" + operation));
    }

    // ========== Then Steps ==========

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        ctx.getLastResponse().then().statusCode(expectedStatus);
    }

    @Then("the response should be a {ResourceType}")
    public void theResponseShouldBeResourceType(String resourceType) {
        ctx.getLastResponse().then()
            .body("resourceType", equalTo(resourceType));
    }

    @Then("the response should be an OperationOutcome")
    public void theResponseShouldBeOperationOutcome() {
        ctx.getLastResponse().then()
            .body("resourceType", equalTo("OperationOutcome"));
    }

    @Then("the response should be a Bundle")
    public void theResponseShouldBeBundle() {
        ctx.getLastResponse().then()
            .body("resourceType", equalTo("Bundle"));
    }

    @Then("the Bundle should have {int} entries")
    public void theBundleShouldHaveEntries(int count) {
        if (count == 0) {
            ctx.getLastResponse().then()
                .body("entry", anyOf(nullValue(), hasSize(0)));
        } else {
            ctx.getLastResponse().then()
                .body("entry", hasSize(count));
        }
    }

    @Then("the OperationOutcome should contain message {string}")
    public void theOperationOutcomeShouldContainMessage(String message) {
        ctx.getLastResponse().then()
            .body("issue.diagnostics", hasItem(containsString(message)));
    }

    @Then("the {ResourceType} should have {string} equal to {string}")
    public void theResourceShouldHaveFieldEqualTo(String resourceType, String field, String value) {
        ctx.getLastResponse().then()
            .body(field, equalTo(value));
    }

    @Then("the {ResourceType} should have an id")
    public void theResourceShouldHaveAnId(String resourceType) {
        ctx.getLastResponse().then()
            .body("id", notNullValue());
    }

    // ========== Helper Methods ==========

    private String buildResourceJson(String resourceType, String description) {
        // Build JSON based on resource type and description
        // This would contain the actual JSON construction logic
        return "{}";
    }

    private String buildMinimalResourceJson(String resourceType) {
        // Build minimal valid JSON for the resource type
        return "{}";
    }
}
```

---

## Examples

### Example 1: Observation CRUD Tests

```gherkin
Feature: Observation CRUD Operations
  As a clinical data consumer
  I want to manage Observation resources
  So that I can record and retrieve clinical measurements

  Background:
    Given the FHIR server is running

  Scenario: Create Observation with required fields
    Given I have an Observation resource with status "final" and code "8867-4"
    And the Observation references an existing Patient
    When I create the Observation resource
    Then the response status should be 201
    And the response should be an Observation
    And the Observation should have status equal to "final"

  Scenario: Create Observation fails without status
    Given I have an Observation resource without status
    When I create the Observation resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "status"

  Scenario: Search Observations by patient
    Given a Patient resource exists
    And multiple Observations exist for the Patient
    When I search for Observation with patient=Patient/123
    Then the response status should be 200
    And the response should be a Bundle
    And all Bundle entries should reference the Patient

  Scenario: Search Observations by code
    Given Observations exist with code "8867-4"
    When I search for Observation with code=8867-4
    Then the response status should be 200
    And all results should have code "8867-4"

  Scenario: Search Observations by date range
    Given Observations exist from various dates
    When I search for Observation with date=gt2024-01-01&date=lt2024-12-31
    Then the response status should be 200
    And all results should have effectiveDateTime in 2024
```

### Example 2: MedicationRequest Plugin Tests

```gherkin
Feature: MedicationRequest Validation Plugin
  As a pharmacy system
  I want prescriptions to be validated automatically
  So that invalid prescriptions are rejected

  Background:
    Given the FHIR server is running

  Scenario: Create MedicationRequest with valid prescriber
    Given I have a MedicationRequest with status "active"
    And the MedicationRequest has a requester
    When I create the MedicationRequest resource
    Then the response status should be 201
    And the response should be a MedicationRequest

  Scenario: Create active MedicationRequest fails without prescriber
    Given I have a MedicationRequest with status "active"
    And the MedicationRequest has no requester
    When I create the MedicationRequest resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "prescriber"

  Scenario: Create MedicationRequest auto-sets authoredOn
    Given I have a MedicationRequest without authoredOn
    When I create the MedicationRequest resource
    Then the response status should be 201
    And the MedicationRequest should have authoredOn
    And the authoredOn should be today's date

  Scenario: Create MedicationRequest rejects negative quantity
    Given I have a MedicationRequest with quantity -5
    When I create the MedicationRequest resource
    Then the response status should be 422
    And the OperationOutcome should contain message "quantity must be positive"
```

---

## Common Step Definitions (Already Available)

These steps are already defined in the project:

### Given Steps:
- `Given the FHIR server is running`
- `Given a Patient resource exists`
- `Given I have a valid Patient resource`
- `Given I have an invalid Patient resource`

### When Steps:
- `When I create the {ResourceType} resource`
- `When I read the {ResourceType} by id`
- `When I validate the Patient resource at type level`
- `When I invoke $validate on Patient`

### Then Steps:
- `Then the response status should be {int}`
- `Then the response should be a {ResourceType}`
- `Then the response should be an OperationOutcome`
- `Then the OperationOutcome should contain message {string}`

---

## Running BDD Tests

After creating tests:

1. **Run All BDD Tests**:
   ```bash
   ./mvnw test -pl fhir4java-server -Dtest=CucumberIT
   ```

2. **Run Specific Feature**:
   ```bash
   ./mvnw test -pl fhir4java-server -Dtest=CucumberIT \
     -Dcucumber.filter.tags="@my-feature"
   ```

3. **Generate Report**:
   ```bash
   ./mvnw test -pl fhir4java-server -Dtest=CucumberIT \
     -Dcucumber.plugin="html:target/cucumber-report.html"
   ```

---

## Tips for Good BDD Tests

1. **Use descriptive scenario names** that explain the test case
2. **Keep scenarios independent** - each should set up its own data
3. **Test both success and failure cases**
4. **Verify specific values** when possible (not just "exists")
5. **Use tags** to organize and filter tests (`@smoke`, `@regression`, `@slow`)
6. **Keep Given/When/Then steps reusable** across scenarios
