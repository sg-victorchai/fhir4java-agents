@crud
Feature: Standard Resource CRUD Lifecycle
  As a FHIR API consumer
  I want to perform CRUD operations on standard FHIR resources
  So that I can manage clinical data through the FHIR API

  Background:
    Given the FHIR server is running

  # ========== CREATE ==========

  Scenario Outline: Create a standard resource
    When I create a <resourceType> resource
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"
    And the response should have a Location header
    And the response should have an ETag header

    Examples:
      | resourceType      |
      | Patient           |
      | Observation       |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |

  Scenario Outline: Create a resource with data profile variants
    When I create a "<profile>" <resourceType> resource
    Then the response status should be 201
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType      | profile      |
      | Patient           | minimum-set  |
      | Patient           | common-set   |
      | Patient           | maximum-set  |
      | Observation       | minimum-set  |
      | Observation       | common-set   |
      | Observation       | maximum-set  |
      | Condition         | minimum-set  |
      | Condition         | common-set   |
      | Condition         | maximum-set  |
      | Encounter         | minimum-set  |
      | Encounter         | common-set   |
      | Encounter         | maximum-set  |
      | Organization      | minimum-set  |
      | Organization      | common-set   |
      | Organization      | maximum-set  |
      | Practitioner      | minimum-set  |
      | Practitioner      | common-set   |
      | Practitioner      | maximum-set  |
      | MedicationRequest | minimum-set  |
      | MedicationRequest | common-set   |
      | MedicationRequest | maximum-set  |

  # ========== READ ==========

  Scenario Outline: Read a standard resource by ID
    Given a <resourceType> resource exists
    When I read the <resourceType> resource by its ID
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType      |
      | Patient           |
      | Observation       |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |

  Scenario: Read a non-existent resource returns 404
    When I read a Patient resource with ID "non-existent-id-12345"
    Then the response status should be 404

  # ========== VREAD ==========

  Scenario Outline: Version-specific read of a resource
    Given a <resourceType> resource exists
    When I vread the <resourceType> resource at version 1
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType      |
      | Patient           |
      | Observation       |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |

  # ========== UPDATE ==========

  Scenario Outline: Update a standard resource
    Given a <resourceType> resource exists
    When I update the <resourceType> resource
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"
    And the response should have an ETag header

    Examples:
      | resourceType      |
      | Patient           |
      | Observation       |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |

  # ========== PATCH ==========

  Scenario Outline: Patch a PATCH-enabled resource
    Given a <resourceType> resource exists
    When I patch the <resourceType> resource
    Then the response status should be 200
    And the response should contain resourceType "<resourceType>"

    Examples:
      | resourceType      |
      | Condition         |
      | Encounter         |
      | Organization      |
      | Practitioner      |
      | MedicationRequest |

  Scenario: Patch a non-PATCH resource returns 405
    Given a Observation resource exists
    When I patch the Observation resource
    Then the response status should be 405

  # ========== DELETE ==========

  Scenario: Delete a non-DELETE resource returns 405
    Given a Patient resource exists
    When I delete the Patient resource
    Then the response status should be 405
