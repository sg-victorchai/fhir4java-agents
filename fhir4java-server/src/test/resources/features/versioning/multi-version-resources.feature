@versioning @multi-version
Feature: Multi-Version Resource Access
  As a FHIR API consumer
  I want to access CarePlan and Procedure resources via both R5 and R4B paths
  So that I can work with resources across FHIR versions

  Background:
    Given the FHIR server is running

  # ========== CarePlan version paths ==========

  Scenario: Create CarePlan via R5 path and read via R5
    When I create a CarePlan resource via version "r5"
    Then the response status should be 201
    When I read the created resource via version "r5" for "CarePlan"
    Then the response status should be 200
    And the response should contain resourceType "CarePlan"

  Scenario: Create CarePlan via R4B path and read via R4B
    When I create a CarePlan resource via version "r4b"
    Then the response status should be 201
    When I read the created resource via version "r4b" for "CarePlan"
    Then the response status should be 200
    And the response should contain resourceType "CarePlan"

  Scenario: Create CarePlan via R5 path and read via unversioned path
    When I create a CarePlan resource via version "r5"
    Then the response status should be 201
    When I read the created resource via unversioned path for "CarePlan"
    Then the response status should be 200
    And the response should contain resourceType "CarePlan"

  # ========== Procedure version paths ==========

  Scenario: Create Procedure via R5 path and read via R5
    When I create a Procedure resource via version "r5"
    Then the response status should be 201
    When I read the created resource via version "r5" for "Procedure"
    Then the response status should be 200
    And the response should contain resourceType "Procedure"

  Scenario: Create Procedure via R4B path and read via R4B
    When I create a Procedure resource via version "r4b"
    Then the response status should be 201
    When I read the created resource via version "r4b" for "Procedure"
    Then the response status should be 200
    And the response should contain resourceType "Procedure"

  Scenario: Create Procedure via R4B path and read via unversioned path
    When I create a Procedure resource via version "r4b"
    Then the response status should be 201
    When I read the created resource via unversioned path for "Procedure"
    Then the response status should be 200
    And the response should contain resourceType "Procedure"

  # ========== Search across versions ==========

  Scenario: Search CarePlan via R5 path returns results
    Given a CarePlan resource exists
    When I search via version "r5" for "CarePlan"
    Then the response status should be 200
    And the response should be a search Bundle

  Scenario: Search Procedure via R4B path returns results
    Given a Procedure resource exists
    When I search via version "r4b" for "Procedure"
    Then the response status should be 200
    And the response should be a search Bundle

  # ========== Version header validation ==========

  Scenario: R5 path returns R5 version header for CarePlan
    Given a CarePlan resource exists
    When I read the created resource via version "r5" for "CarePlan"
    Then the response status should be 200
    And the response should have header "X-FHIR-Version" with value "5.0.0"

  Scenario: R4B path returns R4B version header for Procedure
    Given a Procedure resource exists
    When I read the created resource via version "r4b" for "Procedure"
    Then the response status should be 200
    And the response should have header "X-FHIR-Version" with value "4.3.0"
