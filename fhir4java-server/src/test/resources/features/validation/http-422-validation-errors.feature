@validation @http-422 @phase4
Feature: HTTP 422 Validation Error Handling
  As a FHIR API client
  I want validation errors to return HTTP 422 Unprocessable Entity
  So that I can distinguish between client errors and server failures

  Background:
    Given the FHIR server is running
    And I set the FHIR version to "R5"
    And profile-validator-enabled is set to true

  @smoke @422 @invalid-enum
  Scenario: Invalid enum value returns HTTP 422
    Given I have a Patient resource with invalid gender code "02"
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the response should contain an "OperationOutcome" resource
    And the OperationOutcome severity should be "error"
    And the OperationOutcome code should be "structure"
    And the diagnostics should mention invalid gender code

  @422 @invalid-enum
  Scenario: Various invalid gender codes return HTTP 422
    Given I have invalid gender codes: "99,invalid,M,F"
    When I POST Patient resources with each invalid code
    Then all responses should have status code 422
    And each should return an OperationOutcome
    And each should indicate invalid code value

  @422 @valid-enum
  Scenario: Valid enum values return HTTP 201 Created
    Given I have valid gender codes: "male,female,other,unknown"
    When I POST Patient resources with each valid code
    Then all responses should have status code 201
    And each should return the created Patient resource
    And each resource should have an id

  @422 @missing-required
  Scenario: Missing required field returns HTTP 422
    Given I have a Patient resource missing family name
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the OperationOutcome code should be "required"
    And the diagnostics should mention missing required element

  @422 @invalid-format
  Scenario: Invalid date format returns HTTP 422
    Given I have a Patient resource with birthDate "not-a-date"
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the OperationOutcome code should be "structure"
    And the diagnostics should mention invalid date format

  @422 @profile-violation
  Scenario: Profile validation failure returns HTTP 422
    Given the server requires US Core Patient profile
    And I have a Patient resource not conforming to US Core
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the OperationOutcome should indicate profile violation
    And the diagnostics should reference the US Core profile

  @400 @malformed-json
  Scenario: Malformed JSON returns HTTP 400 Bad Request
    Given I have malformed JSON content
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 400
    And the response should contain an OperationOutcome
    And the OperationOutcome should indicate JSON parse error

  @422 @operation-outcome-details
  Scenario: OperationOutcome provides detailed validation information
    Given I have a Patient with multiple validation errors
    When I send a POST request to "/fhir/r5/Patient"
    Then the response status code should be 422
    And the OperationOutcome should have multiple issues
    And each issue should have severity
    And each issue should have code
    And each issue should have diagnostics
    And issues should have location information when available

  @422 @not-500
  Scenario: Validation errors never return HTTP 500
    Given I have various invalid Patient resources
    When I POST each invalid resource
    Then no response should have status code 500
    And all validation errors should return 422 or 400
    And HTTP 500 should only occur for server failures

  @422 @dataformat-exception
  Scenario: DataFormatException is caught and returns HTTP 422
    Given HAPI FHIR parser encounters invalid data
    When the DataFormatException is thrown
    Then it should be caught by exception handler
    And HTTP 422 should be returned
    And an OperationOutcome should be included
