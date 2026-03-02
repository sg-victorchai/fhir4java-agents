@search
Feature: Search Pagination
  As a FHIR API consumer
  I want to control search result pagination with _count
  So that I can page through large result sets

  Background:
    Given the FHIR server is running

  Scenario: Search with _count limits results
    Given 3 Patient resources exist
    When I search for Patient with parameter "_count" value "2"
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle should have at most 2 entries

  Scenario: Search with _count returns next link when more results exist
    Given 3 Patient resources exist
    When I search for Patient with parameter "_count" value "1"
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle should have a next link

  Scenario: Default search without _count returns results
    Given a Patient resource exists
    When I search for Patient resources
    Then the response status should be 200
    And the response should be a search Bundle
    And the Bundle total should be greater than 0
