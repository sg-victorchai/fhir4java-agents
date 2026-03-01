@tenancy
Feature: Multi-Tenant Resource Isolation

  Background:
    Given the FHIR server is running
    And multi-tenancy is enabled

  # Scenario 1: Create + Read with same tenant → Success
  Scenario: Resources created under a tenant are readable by that tenant
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    When I create a Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 201
    When I read the created Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 200
    And the response should contain a Patient resource

  # Scenario 2: Create with tenant A + Read with tenant B → Not Found
  Scenario: Resources are isolated between tenants
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    And tenant "HOSP-B" exists with external ID "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    When I create a Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 201
    When I read the created Patient with tenant header "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    Then the response status should be 404

  # Scenario 3: Same resource ID in different tenants → Both succeed
  Scenario: Same resource type can exist independently in different tenants
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    And tenant "HOSP-B" exists with external ID "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    When I create a Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 201
    When I create a Patient with tenant header "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    Then the response status should be 201

  # Scenario 4: Search isolation
  Scenario: Search returns only resources from the requesting tenant
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    And tenant "HOSP-B" exists with external ID "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    When I create a Patient with family name "SearchIsolationBDD" and tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 201
    When I search for Patients with family "SearchIsolationBDD" and tenant header "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    Then the response status should be 200
    And the search bundle should have 0 entries

  # Scenario 5: Update isolation (Patient delete is disabled in config)
  Scenario: Update in one tenant does not affect other tenants
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    And tenant "HOSP-B" exists with external ID "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    When I create a Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    And I store the created Patient ID as "patient-a-id"
    And I create a Patient with tenant header "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    And I store the created Patient ID as "patient-b-id"
    When I update Patient "patient-a-id" with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 200
    When I read Patient "patient-b-id" with tenant header "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    Then the response status should be 200

  # Scenario 6: Missing header when tenancy enabled → 400
  Scenario: Request without tenant header is rejected when tenancy is enabled
    When I send a FHIR request without X-Tenant-ID header
    Then the response status should be 400

  # Scenario 7: Unknown tenant GUID → 400
  Scenario: Unknown tenant GUID is rejected
    When I create a Patient with tenant header "cccccccc-cccc-cccc-cccc-cccccccccccc"
    Then the response status should be 400

  # Scenario 8: Disabled tenant GUID → 403
  Scenario: Disabled tenant is rejected
    Given tenant "HOSP-C" exists but is disabled with external ID "dddddddd-dddd-dddd-dddd-dddddddddddd"
    When I create a Patient with tenant header "dddddddd-dddd-dddd-dddd-dddddddddddd"
    Then the response status should be 403

  # Scenario 9: No header when tenancy disabled
  @tenancy-disabled
  Scenario: No header required when tenancy is disabled
    Given multi-tenancy is disabled
    When I create a Patient without tenant header
    Then the response status should be 201

  # Scenario 10: History isolation
  Scenario: History only shows versions from the requesting tenant
    Given tenant "HOSP-A" exists with external ID "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    When I create a Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 201
    When I update the created Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 200
    When I get history of the created Patient with tenant header "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    Then the response status should be 200
    And the history bundle should have 2 entries
