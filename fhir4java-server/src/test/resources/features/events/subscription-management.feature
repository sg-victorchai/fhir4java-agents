@events @subscriptions
Feature: FHIR Subscription Management
  As an AI agent
  I want to create and manage FHIR Subscriptions
  So that I can receive targeted notifications based on subscription criteria

  Background:
    Given the FHIR server is running
    And the subscription manager is active

  # Subscription Registration Scenarios
  Scenario: Register a subscription for Patient resources
    Given I have a subscription for resource type "Patient" with events "create,update"
    When I register the subscription
    Then the subscription should be active
    And the subscription should match Patient create events
    And the subscription should match Patient update events
    And the subscription should not match Patient delete events

  Scenario: Register a subscription for all events of a resource type
    Given I have a subscription for resource type "Observation" with events ""
    When I register the subscription
    Then the subscription should match all Observation events

  Scenario: Register multiple subscriptions
    Given I have registered subscriptions:
      | resourceType | events        |
      | Patient      | create        |
      | Observation  | create,update |
      | Condition    | delete        |
    Then the subscription count should be 3

  # Subscription Matching Scenarios
  Scenario: Event matches single subscription
    Given I have a subscription for resource type "Patient" with events "create"
    And the subscription is registered
    When a Patient create event occurs
    Then the subscription should be in the matching list

  Scenario: Event matches multiple subscriptions
    Given I have registered subscriptions:
      | resourceType | events        |
      | Patient      | create,update |
      | Patient      | create        |
    When a Patient create event occurs
    Then 2 subscriptions should match

  Scenario: Event does not match non-matching subscription
    Given I have a subscription for resource type "Observation" with events "create"
    And the subscription is registered
    When a Patient create event occurs
    Then the subscription should not be in the matching list

  # Subscription Lifecycle Scenarios
  Scenario: Unregister a subscription
    Given I have a registered subscription with ID "sub-to-remove"
    When I unregister the subscription
    Then the subscription should no longer exist
    And the subscription count should decrease by 1

  Scenario: Check subscription existence
    Given I have a registered subscription with ID "sub-exists"
    When I check if subscription "sub-exists" exists
    Then the result should be true
    When I check if subscription "sub-not-exists" exists
    Then the result should be false

  # Event Delivery Scenarios
  Scenario: Event is delivered to matching subscription
    Given I have a subscription for resource type "Patient" with events "create"
    And the subscription is registered
    When a Patient resource is created
    Then the event should be delivered to the subscription

  Scenario: Event is delivered to all matching subscriptions
    Given I have registered subscriptions:
      | id      | resourceType | events |
      | sub-1   | Patient      | create |
      | sub-2   | Patient      | create |
    When a Patient resource is created
    Then the event should be delivered to subscription "sub-1"
    And the event should be delivered to subscription "sub-2"

  Scenario: Event is not delivered to non-matching subscription
    Given I have a subscription for resource type "Observation" with events "create"
    And the subscription is registered
    When a Patient resource is created
    Then the event should not be delivered to the subscription

  # Topic Matching Scenarios
  Scenario: Subscription topic with exact resource type match
    Given I have a subscription topic:
      | resourceType | Patient       |
      | events       | create,update |
    Then the topic should match resource "Patient" action "create"
    And the topic should match resource "Patient" action "update"
    And the topic should not match resource "Patient" action "delete"
    And the topic should not match resource "Observation" action "create"

  Scenario: Subscription topic with case-insensitive action matching
    Given I have a subscription topic:
      | resourceType | Patient |
      | events       | CREATE  |
    Then the topic should match resource "Patient" action "create"
    And the topic should match resource "Patient" action "CREATE"

  # Integration with Event Publisher
  Scenario: Subscription manager receives events from publisher
    Given the subscription manager is subscribed to the event publisher
    And I have a subscription for resource type "Patient" with events "create"
    When a Patient resource is created
    Then the subscription manager should process the event
    And the matching subscription should be notified
