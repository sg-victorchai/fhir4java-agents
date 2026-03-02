@search
Feature: Observation Search with Denylist Enforcement
  As a FHIR API consumer
  I want to search for Observation resources respecting the denylist
  So that I can find observations while unsupported parameters are rejected

  Background:
    Given the FHIR server is running
    And an Observation resource exists with search data

  # ========== Allowed parameter search (not on denylist) ==========

  Scenario: Search Observation by status (token type)
    When I search for Observation with parameter "status" value "final"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Observation by code (token type)
    When I search for Observation with parameter "code" value "http://loinc.org|29463-7"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Observation by _id
    When I search for Observation by _id
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be 1

  # ========== Denied parameters ==========

  Scenario: Search Observation with denylisted _text returns 400
    When I search for Observation with parameter "_text" value "weight"
    Then the response status should be 400

  Scenario: Search Observation with denylisted _content returns 400
    When I search for Observation with parameter "_content" value "weight"
    Then the response status should be 400

  Scenario: Search Observation with denylisted _filter returns 400
    When I search for Observation with parameter "_filter" value "status eq final"
    Then the response status should be 400

  Scenario: Search Observation with denylisted combo param returns 400
    When I search for Observation with parameter "combo-code-value-concept" value "test"
    Then the response status should be 400
