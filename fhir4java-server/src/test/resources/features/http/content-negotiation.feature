@http
Feature: Content Negotiation
  As a FHIR API consumer
  I want the server to respect Accept headers
  So that I can request resources in preferred formats

  Background:
    Given the FHIR server is running
    And a Patient resource exists

  Scenario: Request with application/fhir+json Accept returns JSON
    When I read the Patient with Accept "application/fhir+json"
    Then the response status should be 200
    And the response Content-Type should be "application/fhir+json"

  Scenario: Request with no Accept header returns JSON by default
    When I read the Patient without Accept header
    Then the response status should be 200

  Scenario: Create with application/json Content-Type is accepted
    When I create a Patient with Content-Type "application/json"
    Then the response status should be 201
