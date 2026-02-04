@validation @integration
Feature: Profile Validation Integration
  As a FHIR API consumer
  I want validation integrated seamlessly with CRUD operations
  So that data quality is maintained throughout the resource lifecycle

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"
    And profile-validator-enabled is set to true
    And profile-validation is set to "strict"

  @integration @create
  Scenario: Validation on resource creation
    Given I have a valid Patient resource
    When I send a POST request to "/fhir/r5/Patient"
    Then the resource should be validated before persistence
    And the response status code should be 201
    And validation metrics should be recorded

  @integration @update
  Scenario: Validation on resource update
    Given I have an existing Patient resource with id "123"
    And I have an updated Patient resource
    When I send a PUT request to "/fhir/r5/Patient/123"
    Then the updated resource should be validated
    And the response status code should be 200
    And validation metrics should be recorded

  @integration @invalid-update
  Scenario: Reject invalid updates
    Given I have an existing valid Patient resource with id "123"
    And I have an invalid updated version
    When I send a PUT request to "/fhir/r5/Patient/123"
    Then the response status code should be 422
    And the original resource should remain unchanged

  @integration @end-to-end
  Scenario: End-to-end validation flow
    Given I create a Patient resource with validation
    And the Patient is persisted successfully
    When I retrieve the Patient
    Then the Patient should have all validated data
    And I can update the Patient with valid data
    And invalid updates should be rejected with 422

  @integration @metrics-flow
  Scenario: Metrics recorded throughout resource lifecycle
    Given metrics start at zero
    When I create a Patient (validated)
    And I update the Patient (validated)
    And I attempt an invalid update (validated, rejected)
    Then validation attempts counter should show 3
    And successful validations should be 2
    And failed validations should be 1

  @integration @health-monitoring
  Scenario: Health endpoint reflects validation activity
    Given ProfileValidator is initialized
    When I perform multiple validation operations
    And I check the health endpoint
    Then health status should be UP
    And details should show validators are available
    And initialization times should be reported
