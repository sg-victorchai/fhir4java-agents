@operations
Feature: $merge Operation
  As a FHIR client
  I want to merge two Patient resources
  So that I can consolidate duplicate patient records

  Background:
    Given the FHIR server is running

  Scenario: Merge two patients successfully
    Given two Patient resources exist for merge
    When I merge the source patient into the target patient
    Then the response status should be 200
    And the response should be an OperationOutcome
    And the OperationOutcome should indicate success

  Scenario: Merged source patient is deactivated
    Given two Patient resources exist for merge
    When I merge the source patient into the target patient
    Then the response status should be 200
    And the source patient should be inactive

  Scenario: Merged source patient has replaced-by link
    Given two Patient resources exist for merge
    When I merge the source patient into the target patient
    Then the response status should be 200
    And the source patient should have a replaced-by link

  Scenario: Merge without required parameters returns error
    When I call $merge without required parameters
    Then the response status should be 400
    And the response should be an OperationOutcome
    And the OperationOutcome should indicate an error
