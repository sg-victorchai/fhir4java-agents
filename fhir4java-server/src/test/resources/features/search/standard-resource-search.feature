@search
Feature: Standard Resource Search Smoke Tests
  As a FHIR API consumer
  I want to search for resources with unrestricted search parameters
  So that I can verify search works across all resource types

  Background:
    Given the FHIR server is running

  Scenario Outline: Search returns a Bundle for all-allowed resources
    Given a <resourceType> resource exists
    When I search for <resourceType> resources
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be greater than 0

    Examples:
      | resourceType      |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |
      | CarePlan          |
      | Procedure         |
