@validation @validation-modes
Feature: Profile Validation Modes
  As a FHIR server administrator
  I want to configure validation strictness
  So that I can balance data quality with system flexibility

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"

  @strict-mode
  Scenario: Strict mode rejects resources with validation errors
    Given profile-validation is set to "strict"
    And I have a Patient resource with validation errors
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the resource should not be persisted

  @strict-mode
  Scenario: Strict mode accepts resources with only warnings
    Given profile-validation is set to "strict"
    And I have a Patient resource with validation warnings but no errors
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201
    And the resource should be persisted
    And warnings should be logged

  @lenient-mode
  Scenario: Lenient mode accepts resources with validation errors
    Given profile-validation is set to "lenient"
    And I have a Patient resource with validation errors
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 201
    And the resource should be persisted
    And validation errors should be logged as warnings

  @lenient-mode
  Scenario: Lenient mode logs validation issues but continues
    Given profile-validation is set to "lenient"
    And log-validation-operations is set to true
    And I have a Patient resource with validation warnings
    When I send a POST request to "/fhir/r5/Patient"
    Then validation should be performed
    And warnings should be logged
    And the resource should be created successfully

  @off-mode
  Scenario: Off mode skips profile validation entirely
    Given profile-validation is set to "off"
    And I have a Patient resource with validation errors
    When I send a POST request to "/fhir/r5/Patient"
    Then profile validation should not be performed
    And the response status code should be 201
    And no validation warnings should be logged

  @off-mode @performance
  Scenario: Off mode provides fastest validation performance
    Given profile-validation is set to "off"
    When I create 100 Patient resources
    Then validation time should be minimal
    And all resources should be created successfully
