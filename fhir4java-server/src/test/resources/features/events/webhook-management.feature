@events @webhooks
Feature: Webhook Management
  As an AI agent developer
  I want to register and manage webhook callbacks
  So that my agent can receive real-time notifications of FHIR resource changes

  Background:
    Given the FHIR server is running
    And I am authenticated as tenant "test-tenant"

  # Registration Scenarios
  Scenario: Register a new webhook with specific topics
    Given I have a webhook registration request:
      | callbackUrl | https://agent.example.com/callback |
      | topics      | Patient.create,Patient.update      |
      | secret      | my-hmac-secret                     |
    When I register the webhook
    Then the response status should be 201
    And the webhook response should contain an "id"
    And the webhook should be "enabled"
    And the webhook topics should be "Patient.create,Patient.update"

  Scenario: Register a webhook with wildcard topic
    Given I have a webhook registration request:
      | callbackUrl | https://agent.example.com/all-patient |
      | topics      | Patient.*                             |
    When I register the webhook
    Then the response status should be 201
    And the webhook topics should be "Patient.*"

  Scenario: Register a webhook with global wildcard
    Given I have a webhook registration request:
      | callbackUrl | https://agent.example.com/all-events |
      | topics      | *.*                                  |
    When I register the webhook
    Then the response status should be 201

  Scenario: Register a webhook without topics subscribes to all events
    Given I have a webhook registration request:
      | callbackUrl | https://agent.example.com/all |
    When I register the webhook
    Then the response status should be 201

  # Validation Scenarios
  Scenario: Reject webhook with missing callback URL
    Given I have a webhook registration request:
      | topics | Patient.create |
    When I register the webhook
    Then the response status should be 400

  Scenario: Reject webhook with invalid callback URL
    Given I have a webhook registration request:
      | callbackUrl | not-a-valid-url |
      | topics      | Patient.create  |
    When I register the webhook
    Then the response status should be 400

  Scenario: Reject webhook with non-HTTP callback URL
    Given I have a webhook registration request:
      | callbackUrl | ftp://server.com/callback |
      | topics      | Patient.create            |
    When I register the webhook
    Then the response status should be 400

  # List and Get Scenarios
  Scenario: List all webhooks for tenant
    Given I have registered 3 webhooks for my tenant
    When I list all webhooks
    Then the response status should be 200
    And the response should contain at least 3 webhooks

  Scenario: Get a specific webhook by ID
    Given I have registered a webhook with callback URL "https://get-test.example.com/callback"
    When I get the webhook by its ID
    Then the response status should be 200
    And the webhook callback URL should be "https://get-test.example.com/callback"

  Scenario: Get non-existent webhook returns 404
    When I get webhook with ID 999999
    Then the response status should be 404

  # Delete Scenarios
  Scenario: Delete a webhook
    Given I have registered a webhook with callback URL "https://delete-test.example.com/callback"
    When I delete the webhook by its ID
    Then the response status should be 204
    And the webhook should no longer exist

  # Enable/Disable Scenarios
  Scenario: Disable an enabled webhook
    Given I have registered an enabled webhook
    When I disable the webhook
    Then the response status should be 200
    And the webhook should be "disabled"

  Scenario: Enable a disabled webhook
    Given I have registered and disabled a webhook
    When I enable the webhook
    Then the response status should be 200
    And the webhook should be "enabled"

  # Tenant Isolation Scenarios
  Scenario: Webhooks are isolated by tenant
    Given I have registered a webhook as tenant "tenant-a"
    And I have registered a webhook as tenant "tenant-b"
    When I list webhooks as tenant "tenant-a"
    Then the response should only contain webhooks for "tenant-a"

  Scenario: Cannot access another tenant's webhook
    Given I have registered a webhook as tenant "other-tenant"
    When I try to get that webhook as tenant "my-tenant"
    Then the response status should be 404
