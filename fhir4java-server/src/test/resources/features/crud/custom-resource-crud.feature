@crud @custom
Feature: Custom Resource CRUD Lifecycle
  As a FHIR API consumer
  I want to perform CRUD operations on custom FHIR resources
  So that I can manage domain-specific data through the FHIR API

  Background:
    Given the FHIR server is running

  # ========== CREATE ==========

  Scenario Outline: Create a custom resource
    When I create a <resourceType> resource
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"
    And the response should have a Location header
    And the response should have an ETag header

    Examples:
      | resourceType        |
      | MedicationInventory |
      | Course              |

  Scenario Outline: Create a custom resource with data profile variants
    When I create a "<profile>" <resourceType> resource
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType        | profile      |
      | MedicationInventory | minimum-set  |
      | MedicationInventory | common-set   |
      | MedicationInventory | maximum-set  |
      | Course              | minimum-set  |
      | Course              | common-set   |
      | Course              | maximum-set  |

  # ========== READ ==========

  Scenario Outline: Read a custom resource by ID
    Given a <resourceType> resource exists
    When I read the <resourceType> resource by its ID
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType        |
      | MedicationInventory |
      | Course              |

  # ========== VREAD ==========

  Scenario Outline: Version-specific read of a custom resource
    Given a <resourceType> resource exists
    When I vread the <resourceType> resource at version 1
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType        |
      | MedicationInventory |
      | Course              |

  # ========== UPDATE ==========

  Scenario Outline: Update a custom resource
    Given a <resourceType> resource exists
    When I update the <resourceType> resource
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType        |
      | MedicationInventory |
      | Course              |

  # ========== PATCH (MedicationInventory only) ==========

  Scenario: Patch MedicationInventory resource
    Given a MedicationInventory resource exists
    When I patch the MedicationInventory resource
    Then the response status should be 200
    And the response should contain resourceType "MedicationInventory"

  Scenario: Patch a non-PATCH custom resource returns 405
    Given a Course resource exists
    When I patch the Course resource
    Then the response status should be 405

  # ========== DELETE (MedicationInventory only) ==========

  Scenario: Delete MedicationInventory resource
    Given a MedicationInventory resource exists
    When I delete the MedicationInventory resource
    Then the response status should be 204

  Scenario: Read deleted MedicationInventory returns 410
    Given a MedicationInventory resource exists
    And the MedicationInventory resource is deleted
    When I read the MedicationInventory resource by its ID
    Then the response status should be 410

  Scenario: Delete a non-DELETE custom resource returns 405
    Given a Course resource exists
    When I delete the Course resource
    Then the response status should be 405
