@http
Feature: Conditional Operations
  As a FHIR API consumer
  I want to use If-Match and If-None-Match headers
  So that I can perform optimistic concurrency control

  Background:
    Given the FHIR server is running

  Scenario: Read with If-None-Match matching ETag returns 304
    Given a Patient resource exists
    When I read the Patient with If-None-Match matching the current ETag
    Then the response status should be 304

  Scenario: Read with If-None-Match not matching returns 200
    Given a Patient resource exists
    When I read the Patient with If-None-Match "W/\"999\""
    Then the response status should be 200
    And the response should contain resourceType "Patient"
