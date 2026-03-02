@search @custom
Feature: Custom Resource Search
  As a FHIR API consumer
  I want to search for custom resources using allowlisted parameters
  So that I can find MedicationInventory and Course resources

  Background:
    Given the FHIR server is running

  # ========== MedicationInventory (allowlist: 5 common + 7 resource-specific) ==========

  Scenario: Search MedicationInventory by status
    Given a MedicationInventory resource exists
    When I search for MedicationInventory with parameter "status" value "active"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search MedicationInventory by _id
    Given a MedicationInventory resource exists
    When I search for MedicationInventory by _id
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be 1

  # ========== Course (allowlist: 5 common + 7 resource-specific) ==========

  Scenario: Search Course by status
    Given a Course resource exists
    When I search for Course with parameter "status" value "active"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Course by title
    Given a Course resource exists
    When I search for Course with parameter "title" value "Basic Life Support"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Course by _id
    Given a Course resource exists
    When I search for Course by _id
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be 1
