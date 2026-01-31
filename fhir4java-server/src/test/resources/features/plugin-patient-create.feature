Feature: Patient CREATE Plugin
  As a FHIR server operator
  I want business logic plugins to execute during Patient creation
  So that patients are validated and enriched automatically

  Background:
    Given the FHIR server is running

  Scenario: Patient CREATE with MRN auto-generation
    Given I have a Patient resource with family name "TestFamily" and given name "TestGiven"
    When I create the Patient resource
    Then the response status should be 201
    And the Patient should have an identifier with system "urn:fhir4java:patient:mrn"
    And the MRN identifier value should match pattern "MRN-\d{8}-[A-Z0-9]{5}"

  Scenario: Patient CREATE adds creation timestamp extension
    Given I have a Patient resource with family name "TimestampTest" and given name "Extension"
    When I create the Patient resource
    Then the response status should be 201
    And the Patient should have an extension with URL "http://fhir4java.org/StructureDefinition/creation-timestamp"

  Scenario: Patient CREATE rejects missing family name
    Given I have a Patient resource without a family name
    When I create the Patient resource
    Then the response status should be 422
    And the response should be an OperationOutcome
    And the OperationOutcome should contain message "family name"

  Scenario: Patient CREATE with existing MRN does not add duplicate
    Given I have a Patient resource with family name "MrnTest" and given name "NoDup"
    And the Patient has an identifier with system "urn:fhir4java:patient:mrn" and value "MRN-EXISTING-001"
    When I create the Patient resource
    Then the response status should be 201
    And the Patient should have exactly 1 identifier with system "urn:fhir4java:patient:mrn"

  Scenario: Patient CREATE normalizes phone number
    Given I have a Patient resource with family name "PhoneTest" and given name "Normalize"
    And the Patient has a phone number "(555) 123-4567"
    When I create the Patient resource
    Then the response status should be 201
    And the Patient phone number should be "5551234567"
