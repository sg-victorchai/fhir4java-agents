@http
Feature: Error Responses
  As a FHIR API consumer
  I want proper OperationOutcome error responses
  So that I can handle errors programmatically

  Background:
    Given the FHIR server is running

  Scenario: Read non-existent resource returns 404 with OperationOutcome
    When I read a Patient resource with ID "does-not-exist-12345"
    Then the response status should be 404
    And the response should be an OperationOutcome
    And the OperationOutcome should indicate an error

  Scenario: Create with invalid JSON returns 400
    When I create a Patient with invalid body "not-json"
    Then the response status should be 400

  Scenario: Unsupported interaction returns 405
    Given a Patient resource exists
    When I delete the Patient resource
    Then the response status should be 405

  Scenario: Read deleted resource returns 410
    Given a MedicationInventory resource exists
    And the MedicationInventory resource is deleted
    When I read the MedicationInventory resource by its ID
    Then the response status should be 410
