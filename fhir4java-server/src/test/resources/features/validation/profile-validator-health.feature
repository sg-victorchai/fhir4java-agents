@validation @health @phase2
Feature: Profile Validator Health Indicator
  As a FHIR server operator
  I want to monitor ProfileValidator health via Spring Boot Actuator
  So that I can ensure validation is working and troubleshoot issues

  Background:
    Given the FHIR server is running
    And Spring Boot Actuator is enabled
    And show-components is set to always

  @smoke @health
  Scenario: Health endpoint exposes ProfileValidator status when enabled
    Given profile-validator-enabled is set to true
    And ProfileValidator has initialized successfully
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 200
    And the response should be valid JSON
    And the status should be "UP"
    And the details should include "enabled": true
    And the details should include total initialization time
    And the details should show version-specific status for R5
    And the details should show version-specific status for R4B

  @health @disabled
  Scenario: Health endpoint shows disabled status when validation is off
    Given profile-validator-enabled is set to false
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 200
    And the status should be "UP"
    And the details should include "disabled": true
    And the details should include reason "Profile validator disabled by configuration"

  @health @strict-mode
  Scenario: Health check reports DOWN in strict mode when no validators initialized
    Given health-check-mode is set to "strict"
    And profile-validator-enabled is set to true
    And all validators failed to initialize
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 503
    And the status should be "DOWN"
    And the details should show successCount: 0
    And the details should show totalCount: 2

  @health @warn-mode
  Scenario: Health check reports UP with warnings in warn mode when no validators initialized
    Given health-check-mode is set to "warn"
    And profile-validator-enabled is set to true
    And all validators failed to initialize
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 200
    And the status should be "UP"
    And the details should include warning information
    And the details should show successCount: 0

  @health @disabled-mode
  Scenario: Health check always reports UP in disabled mode
    Given health-check-mode is set to "disabled"
    And profile-validator-enabled is set to true
    And all validators failed to initialize
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 200
    And the status should be "UP"

  @health @partial-success
  Scenario: Health check shows partial success when some validators initialize
    Given R5 validator initialized successfully
    And R4B validator failed to initialize
    When I send a GET request to "/actuator/health/profileValidator"
    Then the status should be "UP"
    And the details should show successCount: 1
    And the details should show totalCount: 2
    And R5 version should have status "initialized"
    And R5 version should have available: true
    And R4B version should have status "failed"

  @health @initialization-times
  Scenario: Health endpoint reports initialization timing details
    Given ProfileValidator has initialized successfully
    When I send a GET request to "/actuator/health/profileValidator"
    Then the details should include totalInitializationTime in milliseconds
    And each version should report its initialization time
    And the sum of version times should equal total time

  @health @404-error
  Scenario: Health endpoint returns 404 when show-components is not configured
    Given show-components is not set to "always"
    When I send a GET request to "/actuator/health/profileValidator"
    Then the response status code should be 404
