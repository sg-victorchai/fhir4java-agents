@events @resource-change
Feature: Resource Change Events
  As a FHIR server operator
  I want resource changes to trigger events
  So that AI agents and other systems can be notified in real-time

  Background:
    Given the FHIR server is running
    And the event publishing system is active

  # Event Publishing Scenarios
  Scenario: Patient create triggers event
    When I create a Patient resource with family name "EventTestPatient"
    Then a resource change event should be published
    And the event should have resource type "Patient"
    And the event should have action "create"

  Scenario: Patient update triggers event
    Given a Patient resource exists with ID "patient-event-update"
    When I update the Patient with family name "UpdatedEventPatient"
    Then a resource change event should be published
    And the event should have resource type "Patient"
    And the event should have action "update"
    And the event should have resource ID "patient-event-update"

  Scenario: Patient delete triggers event
    Given a Patient resource exists with ID "patient-event-delete"
    When I delete the Patient
    Then a resource change event should be published
    And the event should have resource type "Patient"
    And the event should have action "delete"
    And the event should have resource ID "patient-event-delete"

  Scenario: Observation create triggers event
    Given a Patient resource exists with ID "patient-for-obs"
    When I create an Observation resource for patient "patient-for-obs"
    Then a resource change event should be published
    And the event should have resource type "Observation"
    And the event should have action "create"

  # Event Payload Scenarios
  Scenario: Event payload contains required fields
    When I create a Patient resource
    Then the published event should contain:
      | field        | type             |
      | resourceType | non-empty string |
      | resourceId   | non-empty string |
      | action       | non-empty string |
      | timestamp    | ISO 8601 instant |

  Scenario: Event payload includes tenant ID
    Given I am authenticated as tenant "event-tenant"
    When I create a Patient resource
    Then the published event should have tenant ID "event-tenant"

  Scenario: Event timestamp is approximately current time
    When I create a Patient resource
    Then the published event timestamp should be within 5 seconds of now

  # Non-Event Scenarios
  Scenario: Read operation does not trigger event
    Given a Patient resource exists with ID "patient-no-event"
    When I read the Patient
    Then no resource change event should be published

  Scenario: Search operation does not trigger event
    When I search for Patient resources
    Then no resource change event should be published

  # Batch Transaction Scenarios
  Scenario: Batch create triggers multiple events
    When I submit a batch bundle with:
      | method | resourceType | familyName      |
      | POST   | Patient      | BatchPatient1   |
      | POST   | Patient      | BatchPatient2   |
      | POST   | Patient      | BatchPatient3   |
    Then 3 resource change events should be published
    And all events should have action "create"

  # Error Scenarios
  Scenario: Failed operation does not trigger event
    Given I have an invalid Patient resource (missing required fields)
    When I try to create the Patient resource
    Then the response status should be 422
    And no resource change event should be published

  # FHIR Version Scenarios
  Scenario: R5 resource change triggers event with correct version context
    When I create a Patient resource using FHIR R5
    Then a resource change event should be published
    And the FHIR context should be R5

  Scenario: R4B resource change triggers event with correct version context
    When I create a Patient resource using FHIR R4B
    Then a resource change event should be published
    And the FHIR context should be R4B
