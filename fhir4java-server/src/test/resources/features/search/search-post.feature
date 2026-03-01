@search
Feature: POST-based Search
  As a FHIR API consumer
  I want to search via POST to /{resourceType}/_search
  So that I can submit search parameters in the request body

  Background:
    Given the FHIR server is running
    And a Patient resource exists with search data

  Scenario: POST search for Patient
    When I POST search for Patient with parameter "family" value "CommonPatient"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: POST search for Patient with multiple parameters
    When I POST search for Patient with parameters "family=CommonPatient&gender=female"
    Then the response status should be 200
    And the response should be a search Bundle
