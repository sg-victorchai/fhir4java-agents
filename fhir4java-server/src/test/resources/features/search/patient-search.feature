@search
Feature: Patient Search with Allowlist Enforcement
  As a FHIR API consumer
  I want to search for Patient resources using allowlisted parameters
  So that I can find patients by common clinical identifiers

  Background:
    Given the FHIR server is running
    And a Patient resource exists with search data

  # ========== Allowlisted parameter search ==========

  Scenario: Search Patient by family name (string type)
    When I search for Patient with parameter "family" value "Smith"
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be greater than 0

  Scenario: Search Patient by identifier (token type)
    When I search for Patient with parameter "identifier" value "http://example.org/mrn|MRN-COMMON-001"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient by birthdate (date type)
    When I search for Patient with parameter "birthdate" value "1985-06-15"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient by gender (token type)
    When I search for Patient with parameter "gender" value "female"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient by active status (token type)
    When I search for Patient with parameter "active" value "true"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient by _id (common parameter)
    When I search for Patient by _id
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be 1

  # ========== Denied parameter ==========

  Scenario: Search Patient with non-allowlisted parameter returns 400
    When I search for Patient with parameter "_content" value "test"
    Then the response status should be 400
