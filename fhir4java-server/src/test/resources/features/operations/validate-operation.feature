Feature: $validate Operation
  As a FHIR client
  I want to validate FHIR resources
  So that I can ensure they conform to the specification

  Background:
    Given the FHIR server is running

  Scenario: Validate a valid Patient resource at type level
    Given I have a valid Patient resource
    When I validate the Patient resource at type level
    Then the response status should be 200
    And the response should be an OperationOutcome
    And the OperationOutcome should indicate success

  Scenario: Validate an invalid Patient resource at type level
    Given I have an invalid Patient resource
    When I validate the Patient resource at type level
    Then the response status should be 200
    And the response should be an OperationOutcome

  Scenario: Validate a valid Patient resource at instance level
    Given a Patient resource exists
    And I have a valid Patient resource
    When I validate the Patient resource at instance level
    Then the response status should be 200
    And the response should be an OperationOutcome

  # ========== Extended scenarios (Phase 3) ==========

  Scenario: Validate an Observation resource at type level
    Given I have a valid Observation resource
    When I validate the Observation resource at type level
    Then the response status should be 200
    And the response should be an OperationOutcome

  Scenario: Validate a Condition resource at type level
    Given I have a valid Condition resource
    When I validate the Condition resource at type level
    Then the response status should be 200
    And the response should be an OperationOutcome
