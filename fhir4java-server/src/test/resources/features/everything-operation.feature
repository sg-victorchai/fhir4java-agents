Feature: $everything Operation
  As a FHIR client
  I want to retrieve all resources related to a Patient
  So that I can get a complete view of a patient's record

  Background:
    Given the FHIR server is running

  Scenario: Get everything for an existing Patient
    Given a Patient resource exists
    When I request $everything for the Patient
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type searchset
    And the Bundle should contain the Patient resource

  Scenario: Get everything with count parameter
    Given a Patient resource exists
    When I request $everything for the Patient with _count 5
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type searchset

  Scenario: Type-level everything returns patients
    Given a Patient resource exists
    When I request type-level $everything for Patient
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type searchset
