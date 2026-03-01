@search
Feature: Search Parameter Modifiers
  As a FHIR API consumer
  I want to use search modifiers like :exact, :contains, :missing, :not
  So that I can refine my search results precisely

  Background:
    Given the FHIR server is running
    And a Patient resource exists with search data

  Scenario: Search Patient family name with :exact modifier
    When I search for Patient with parameter "family:exact" value "CommonPatient"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient family name with :contains modifier
    When I search for Patient with parameter "family:contains" value "Common"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient with :missing modifier
    When I search for Patient with parameter "birthdate:missing" value "false"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Patient gender with :not modifier
    When I search for Patient with parameter "gender:not" value "male"
    Then the response status should be 200
    And the response should be a search Bundle
