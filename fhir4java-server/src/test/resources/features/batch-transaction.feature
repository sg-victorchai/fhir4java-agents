Feature: Batch and Transaction Bundle Processing
  As a FHIR API consumer
  I want to submit batch and transaction bundles
  So that I can process multiple operations in a single request

  Background:
    Given the FHIR server is running

  Scenario: Process batch bundle with mixed operations
    Given I have a batch bundle with a Patient POST and a Patient GET
    When I submit the bundle
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type "batch-response"
    And the Bundle should have 2 entries

  Scenario: Process transaction bundle with 2 Patient creates
    Given I have a transaction bundle with 2 Patient creates
    When I submit the bundle
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type "transaction-response"
    And all entries should have status "201"

  Scenario: Batch continues on individual failure
    Given I have a batch bundle with a valid POST and an invalid GET
    When I submit the bundle
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should be of type "batch-response"
    And the Bundle should have entries with mixed statuses

  Scenario: Reject non-batch/transaction bundle
    Given I have a searchset bundle
    When I submit the bundle
    Then the response status should be 400
    And the response should be an OperationOutcome
