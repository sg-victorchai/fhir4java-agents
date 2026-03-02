@operations
Feature: Operation Routing
  As a FHIR client
  I want to receive appropriate errors for unregistered operations
  So that I can distinguish supported from unsupported operations

  Background:
    Given the FHIR server is running

  Scenario: Unregistered type-level operation returns 404
    When I invoke unregistered operation "$nonexistent" on "Patient"
    Then the response status should be 404

  Scenario: Unregistered instance-level operation returns 404
    Given a Patient resource exists
    When I invoke unregistered instance operation "$nonexistent" on the Patient
    Then the response status should be 404
