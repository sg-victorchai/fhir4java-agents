@http
Feature: FHIR Response Headers
  As a FHIR API consumer
  I want proper HTTP headers on FHIR responses
  So that I can implement caching and version-aware workflows

  Background:
    Given the FHIR server is running

  Scenario: CREATE response includes required headers
    When I create a Patient resource
    Then the response status should be 201
    And the response should have a Location header
    And the response should have an ETag header
    And the response should have a Last-Modified header
    And the response Content-Type should be "application/fhir+json"

  Scenario: READ response includes ETag and Last-Modified
    Given a Patient resource exists
    When I read the Patient resource by its ID
    Then the response status should be 200
    And the response should have an ETag header
    And the response should have a Last-Modified header
    And the response Content-Type should be "application/fhir+json"

  Scenario: UPDATE response includes ETag and Content-Location
    Given a Patient resource exists
    When I update the Patient resource
    Then the response status should be 200
    And the response should have an ETag header
    And the response should have a Content-Location header

  Scenario: DELETE response includes ETag
    Given a MedicationInventory resource exists
    When I delete the MedicationInventory resource
    Then the response status should be 204
    And the response should have an ETag header
