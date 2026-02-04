@validation @metrics @phase3
Feature: Profile Validator Metrics
  As a FHIR server operator
  I want validation metrics collected and exposed via Spring Boot Actuator
  So that I can monitor validation performance and troubleshoot issues

  Background:
    Given the FHIR server is running
    And Spring Boot Actuator is enabled
    And Micrometer is configured
    And profile-validator-enabled is set to true

  @smoke @metrics @eager-registration
  Scenario: Validation metrics are registered eagerly at startup
    Given the application has just started
    And no validation has been performed yet
    When I send a GET request to "/actuator/metrics"
    Then the response should contain metric name "fhir.validation.attempts"
    And the response should contain metric name "fhir.validation.duration"
    And the metrics should be visible immediately

  @metrics @naming
  Scenario: Metrics use correct dotted notation for Actuator
    When I send a GET request to "/actuator/metrics/fhir.validation.attempts"
    Then the response status code should be 200
    And the metric should use dotted notation
    And the metric should have measurements

  @metrics @prometheus
  Scenario: Metrics are exported with underscore notation for Prometheus
    When I send a GET request to "/actuator/prometheus"
    Then the response should contain "fhir_validation_attempts_total"
    And the response should contain "fhir_validation_duration_seconds"
    And metric names should use underscore notation

  @metrics @validation-attempts
  Scenario: Counter increments on validation attempts
    Given validation metrics start at zero
    When I validate a Patient resource for R5
    And I validate an Observation resource for R5
    When I send a GET request to "/actuator/metrics/fhir.validation.attempts"
    Then the counter should show at least 2 attempts
    And metrics should be tagged with version "r5"

  @metrics @tags
  Scenario: Validation attempts are tagged correctly
    Given I have validated resources
    When I send a GET request to "/actuator/metrics/fhir.validation.attempts"
    Then the metric should have tag "version" with values
    And the metric should have tag "result" with values "success" and "failure"
    And the metric should have tag "resourceType" with resource type values

  @metrics @duration
  Scenario: Timer records validation duration
    When I validate a Patient resource
    And I send a GET request to "/actuator/metrics/fhir.validation.duration"
    Then the response should include duration measurements
    And the response should show count of validations
    And the response should show mean duration
    And the response should show max duration

  @metrics @success-failure
  Scenario: Metrics distinguish between successful and failed validations
    Given I validate a valid Patient resource
    And I validate an invalid Patient resource
    When I query metrics with tag filter "result=success"
    Then the counter should show successful validations
    When I query metrics with tag filter "result=failure"
    Then the counter should show failed validations

  @metrics @resource-type-tracking
  Scenario: Metrics track validation by resource type
    Given I validate multiple Patient resources
    And I validate multiple Observation resources
    When I query metrics with tag filter "resourceType=Patient"
    Then the counter should show Patient validations
    When I query metrics with tag filter "resourceType=Observation"
    Then the counter should show Observation validations

  @metrics @disabled
  Scenario: No metrics recorded when validation is disabled
    Given profile-validator-enabled is set to false
    When I attempt to validate a resource
    Then no validation metrics should be recorded
    And the validation should return success without validation

  @metrics @debug-logging
  Scenario: Debug logs show metric recording
    Given log-validation-operations is set to true
    And logging level for validation is DEBUG
    When I validate a Patient resource
    Then the logs should contain "Recorded metric: fhir.validation.attempts"
    And the logs should contain version tag
    And the logs should contain result tag
    And the logs should contain resourceType tag

  @metrics @performance
  Scenario: Metrics show validation performance characteristics
    Given I have validated 100 resources
    When I send a GET request to "/actuator/metrics/fhir.validation.duration"
    Then the response should show percentiles
    And p50 (median) duration should be available
    And p95 duration should be available
    And p99 duration should be available
