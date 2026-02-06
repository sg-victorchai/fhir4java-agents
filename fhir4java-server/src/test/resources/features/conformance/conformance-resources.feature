Feature: FHIR Conformance Resources
  As a FHIR API consumer
  I want to query conformance resources (StructureDefinition, SearchParameter, OperationDefinition)
  So that I can understand the server's FHIR capabilities

  Background:
    Given the FHIR server is running

  # StructureDefinition Endpoints

  Scenario: Read StructureDefinition by ID
    When I read StructureDefinition "Patient" via "/fhir/r5/StructureDefinition/Patient"
    Then the response status should be 200
    And the response should have resourceType "StructureDefinition"
    And the response should have id "Patient"

  Scenario: Read StructureDefinition via unversioned path
    When I read StructureDefinition "Patient" via "/fhir/StructureDefinition/Patient"
    Then the response status should be 200
    And the response should have resourceType "StructureDefinition"

  Scenario: Search StructureDefinitions without filter
    When I search StructureDefinitions via "/fhir/r5/StructureDefinition"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should have total greater than 0

  Scenario: Search StructureDefinitions by name
    When I search StructureDefinitions via "/fhir/r5/StructureDefinition?name=Patient"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should contain entry with resource id "Patient"

  Scenario: StructureDefinition not found returns 404
    When I read StructureDefinition "NonExistentResource" via "/fhir/r5/StructureDefinition/NonExistentResource"
    Then the response status should be 404
    And the response should have resourceType "OperationOutcome"

  # SearchParameter Endpoints

  Scenario: Read SearchParameter by ID
    When I read SearchParameter "Patient-identifier" via "/fhir/r5/SearchParameter/Patient-identifier"
    Then the response status should be 200
    And the response should have resourceType "SearchParameter"
    And the response should have id "Patient-identifier"

  Scenario: Search SearchParameters by base resource type
    When I search SearchParameters via "/fhir/r5/SearchParameter?base=Patient"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should have total greater than 0

  Scenario: Search SearchParameters via unversioned path
    When I search SearchParameters via "/fhir/SearchParameter?base=Observation"
    Then the response status should be 200
    And the response should be a Bundle

  # OperationDefinition Endpoints

  Scenario: Read OperationDefinition by ID
    When I read OperationDefinition "Resource-validate" via "/fhir/r5/OperationDefinition/Resource-validate"
    Then the response status should be 200
    And the response should have resourceType "OperationDefinition"
    And the response should have id "Resource-validate"

  Scenario: Search OperationDefinitions without filter
    When I search OperationDefinitions via "/fhir/r5/OperationDefinition"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should have total greater than 0

  # Pagination

  Scenario: Search with pagination
    When I search StructureDefinitions via "/fhir/r5/StructureDefinition?_count=5"
    Then the response status should be 200
    And the response should be a Bundle
    And the Bundle should have at most 5 entries
    And the Bundle should have pagination links

  # CapabilityStatement includes conformance resources

  Scenario: CapabilityStatement includes conformance resource types
    When I request metadata via "/fhir/r5/metadata"
    Then the response status should be 200
    And the CapabilityStatement should include resource type "StructureDefinition"
    And the CapabilityStatement should include resource type "SearchParameter"
    And the CapabilityStatement should include resource type "OperationDefinition"
