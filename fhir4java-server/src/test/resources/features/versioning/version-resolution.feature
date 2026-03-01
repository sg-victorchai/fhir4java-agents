Feature: FHIR Version Resolution
  As a FHIR API consumer
  I want the server to resolve FHIR versions from request paths
  So that I can access resources via both versioned and unversioned URLs

  Background:
    Given the FHIR server is running
    And a Patient resource exists

  Scenario: Access Patient via versioned R5 path
    When I read the Patient via versioned path "/fhir/r5/Patient/{id}"
    Then the response status should be 200
    And the response should have header "X-FHIR-Version" with value "5.0.0"

  Scenario: Access Patient via unversioned path
    When I read the Patient via unversioned path "/fhir/Patient/{id}"
    Then the response status should be 200
    And the response should have header "X-FHIR-Version" with value "5.0.0"

  Scenario: Access metadata via versioned path
    When I request metadata via "/fhir/r5/metadata"
    Then the response status should be 200
    And the response should contain "fhirVersion" with value "5.0.0"

  Scenario: Access metadata via unversioned path
    When I request metadata via "/fhir/metadata"
    Then the response status should be 200
    And the response body should contain "fhirVersion"

  Scenario: Search via versioned path
    When I search for Patients via "/fhir/r5/Patient"
    Then the response status should be 200
    And the response should be a Bundle
    And the response should have header "X-FHIR-Version" with value "5.0.0"

  Scenario: Search via unversioned path
    When I search for Patients via "/fhir/Patient"
    Then the response status should be 200
    And the response should be a Bundle
    And the response should have header "X-FHIR-Version" with value "5.0.0"

  Scenario: Case-insensitive version code
    When I read the Patient via versioned path "/fhir/R5/Patient/{id}"
    Then the response status should be 200
    And the response should have header "X-FHIR-Version" with value "5.0.0"
