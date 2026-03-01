@crud @multi-version
Feature: Multi-Version Resource CRUD Lifecycle
  As a FHIR API consumer
  I want to perform CRUD operations on resources that support both R5 and R4B
  So that I can manage clinical data across FHIR versions

  Background:
    Given the FHIR server is running

  # ========== CREATE ==========

  Scenario Outline: Create a multi-version resource via R5 path
    When I create a <resourceType> resource via version "r5"
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  Scenario Outline: Create a multi-version resource via R4B path
    When I create a <resourceType> resource via version "r4b"
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  Scenario Outline: Create multi-version resource with data profile variants
    When I create a "<profile>" <resourceType> resource
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType | profile      |
      | CarePlan     | minimum-set  |
      | CarePlan     | common-set   |
      | CarePlan     | maximum-set  |
      | Procedure    | minimum-set  |
      | Procedure    | common-set   |
      | Procedure    | maximum-set  |

  # ========== READ ==========

  Scenario Outline: Read a multi-version resource by ID
    Given a <resourceType> resource exists
    When I read the <resourceType> resource by its ID
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  # ========== VREAD ==========

  Scenario Outline: Version-specific read of a multi-version resource
    Given a <resourceType> resource exists
    When I vread the <resourceType> resource at version 1
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  # ========== UPDATE ==========

  Scenario Outline: Update a multi-version resource
    Given a <resourceType> resource exists
    When I update the <resourceType> resource
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  # ========== PATCH not supported for CarePlan or Procedure ==========

  Scenario Outline: Patch a non-PATCH multi-version resource returns 405
    Given a <resourceType> resource exists
    When I patch the <resourceType> resource
    Then the response status should be 405

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |

  # ========== DELETE not supported for CarePlan or Procedure ==========

  Scenario Outline: Delete a non-DELETE multi-version resource returns 405
    Given a <resourceType> resource exists
    When I delete the <resourceType> resource
    Then the response status should be 405

    Examples:
      | resourceType |
      | CarePlan     |
      | Procedure    |
