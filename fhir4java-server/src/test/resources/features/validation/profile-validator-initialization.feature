@validation @profile-validator @phase1
Feature: Profile Validator Initialization
  As a FHIR server operator
  I want the ProfileValidator to initialize correctly with all dependencies
  So that FHIR resource validation can be performed reliably

  Background:
    Given the FHIR server is starting up
    And commons-compress version 1.26.0 or higher is available
    And hapi-fhir-caching-caffeine is available

  @smoke @initialization
  Scenario: ProfileValidator initializes successfully with all dependencies
    Given all required dependencies are present
    And profile-validator-enabled is set to true
    When the application starts
    Then ProfileValidator should initialize successfully
    And the startup logs should contain "ProfileValidator initialization complete"
    And R5 validator should be initialized
    And R4B validator should be initialized
    And the total initialization time should be logged

  @initialization @dependency-error
  Scenario: ProfileValidator fails gracefully when commons-compress is missing
    Given commons-compress dependency is not available
    When the application attempts to start
    Then the application should fail to start
    And the error logs should contain "NoSuchMethodError"
    And the error should reference TarArchiveInputStream

  @initialization @dependency-error
  Scenario: ProfileValidator fails gracefully when hapi-fhir-caching-caffeine is missing
    Given hapi-fhir-caching-caffeine dependency is not available
    When the application attempts to start
    Then the application should fail to start
    And the error logs should contain "No Cache Service Providers found"

  @initialization @feature-flag
  Scenario: ProfileValidator skips initialization when disabled
    Given profile-validator-enabled is set to false
    When the application starts
    Then ProfileValidator should not initialize validators
    And the startup logs should contain "Profile validation is disabled by configuration"
    And the application should start successfully
    And startup time should be faster than with validation enabled

  @initialization @lazy-loading
  Scenario: ProfileValidator supports lazy initialization for faster startup
    Given lazy-initialization is set to true
    And profile-validator-enabled is set to true
    When the application starts
    Then ProfileValidator should not initialize validators at startup
    And startup time should be significantly reduced
    And the startup logs should not contain version-specific initialization messages

  @initialization @lazy-loading
  Scenario: Lazy-initialized validators load on first use
    Given lazy-initialization is set to true
    And the application has started
    And no validators have been initialized
    When I validate a Patient resource for R5
    Then the R5 validator should be initialized on-demand
    And the logs should contain "Lazy initializing validator for FHIR r5"
    And the validation should complete successfully

  @initialization @version-specific
  Scenario: ProfileValidator initializes only enabled FHIR versions
    Given only R5 is enabled in configuration
    And R4B is disabled
    When the application starts
    Then only R5 validator should be initialized
    And R4B validator should not be initialized
    And the initialization summary should show "1/1 versions initialized"

  @initialization @timing
  Scenario: ProfileValidator logs per-version initialization times
    Given profile-validator-enabled is set to true
    When the application starts
    And ProfileValidator initializes
    Then each version initialization should be logged with duration
    And logs should show "Initialized validator for FHIR r5 in XXXms"
    And logs should show "Initialized validator for FHIR r4b in XXXms"
    And total initialization time should be sum of version times

  @initialization @error-recovery
  Scenario: ProfileValidator continues initialization when one version fails
    Given R5 validator configuration is invalid
    And R4B validator configuration is valid
    When the application starts
    Then R5 validator should fail to initialize
    And the logs should contain WARN message for R5 failure
    And R4B validator should initialize successfully
    And the initialization summary should show "1/2 versions initialized"
