Feature: JSON Patch (PATCH) Operation
  As a FHIR client
  I want to partially update resources using JSON Patch
  So that I can make targeted changes without replacing the entire resource

  Background:
    Given the FHIR server is running

  Scenario: Successfully patch a Patient resource
    Given a Patient resource exists
    And I have a JSON Patch document to update the patient name
    When I apply the JSON Patch to the Patient
    Then the response status should be 200
    And the response should be a Patient resource
    And the Patient family name should be "Johnson"
    And the response should have an ETag header
    And the response should have a Last-Modified header

  Scenario: Patch with invalid JSON Patch document
    Given a Patient resource exists
    And I have an invalid JSON Patch document
    When I apply the JSON Patch to the Patient
    Then the response status should be 400
