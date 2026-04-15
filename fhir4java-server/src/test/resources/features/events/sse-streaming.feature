@events @sse
Feature: Server-Sent Events Streaming
  As an AI agent
  I want to subscribe to real-time event streams via SSE
  So that I can react immediately to FHIR resource changes

  Background:
    Given the FHIR server is running

  # Connection Scenarios
  Scenario: Connect to SSE endpoint
    When I connect to the SSE events stream
    Then the connection should be established
    And the response content type should be "text/event-stream"

  Scenario: Connect to SSE endpoint with topic filter
    When I connect to the SSE events stream with topics "Patient,Observation"
    Then the connection should be established
    And I should only receive events for "Patient" and "Observation" resources

  Scenario: Connect to SSE endpoint with action filter
    When I connect to the SSE events stream with actions "create,update"
    Then the connection should be established
    And I should only receive "create" and "update" events

  Scenario: Connect to SSE endpoint with combined filters
    When I connect to the SSE events stream with topics "Patient" and actions "create"
    Then the connection should be established
    And I should only receive "Patient" "create" events

  # Event Reception Scenarios
  Scenario: Receive event when Patient is created
    Given I am connected to the SSE events stream with topics "Patient"
    When a Patient resource is created with family name "SSETestPatient"
    Then I should receive an SSE event within 5 seconds
    And the event type should be "resource-change"
    And the event data should contain:
      | resourceType | Patient |
      | action       | create  |

  Scenario: Receive event when Patient is updated
    Given I am connected to the SSE events stream with topics "Patient"
    And a Patient resource exists with ID "patient-sse-update"
    When the Patient is updated with family name "UpdatedName"
    Then I should receive an SSE event within 5 seconds
    And the event data should contain:
      | resourceType | Patient        |
      | resourceId   | patient-sse-update |
      | action       | update         |

  Scenario: Receive event when Patient is deleted
    Given I am connected to the SSE events stream with topics "Patient"
    And a Patient resource exists with ID "patient-sse-delete"
    When the Patient is deleted
    Then I should receive an SSE event within 5 seconds
    And the event data should contain:
      | resourceType | Patient             |
      | resourceId   | patient-sse-delete  |
      | action       | delete              |

  # Event Format Scenarios
  Scenario: SSE event follows correct format
    Given I am connected to the SSE events stream
    When a Patient resource is created
    Then I should receive an SSE event with:
      | field     | value           |
      | event     | resource-change |
    And the event data should be valid JSON containing:
      | field        | type    |
      | resourceType | string  |
      | resourceId   | string  |
      | action       | string  |
      | tenantId     | string  |
      | timestamp    | string  |

  # Filtering Scenarios
  Scenario: Filter excludes non-matching events
    Given I am connected to the SSE events stream with topics "Observation"
    When a Patient resource is created
    Then I should not receive any events within 2 seconds

  Scenario: Multiple subscribers receive same event
    Given 3 clients are connected to the SSE events stream with topics "Patient"
    When a Patient resource is created
    Then all 3 clients should receive the event

  # Multi-Tenant Scenarios
  Scenario: Events include tenant ID
    Given I am authenticated as tenant "sse-tenant"
    And I am connected to the SSE events stream
    When a Patient resource is created for tenant "sse-tenant"
    Then the event data should contain:
      | tenantId | sse-tenant |
