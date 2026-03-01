@tenant-management
Feature: Tenant Management API

  Background:
    Given the FHIR server is running

  Scenario: Create a new tenant via API
    When I create a tenant with external ID and internal ID "mgmt-bdd-create"
    Then the response status should be 201
    And the response should contain tenant with internal ID "mgmt-bdd-create"

  Scenario: List all tenants
    When I list all tenants via admin API
    Then the response status should be 200
    And the response should contain at least 1 tenant

  Scenario: Get tenant by ID after creation
    When I create a tenant with external ID and internal ID "mgmt-bdd-get"
    Then the response status should be 201
    When I get the created tenant by its database ID
    Then the response status should be 200
    And the response should contain tenant with internal ID "mgmt-bdd-get"

  Scenario: Update a tenant
    When I create a tenant with external ID and internal ID "mgmt-bdd-update"
    Then the response status should be 201
    When I update the created tenant with name "Updated Tenant Name"
    Then the response status should be 200
    And the response should contain tenant name "Updated Tenant Name"

  Scenario: Disable and re-enable a tenant
    When I create a tenant with external ID and internal ID "mgmt-bdd-toggle"
    Then the response status should be 201
    When I disable the created tenant
    Then the response status should be 200
    And the tenant should be disabled
    When I enable the created tenant
    Then the response status should be 200
    And the tenant should be enabled

  Scenario: Delete a tenant
    When I create a tenant with external ID and internal ID "mgmt-bdd-delete"
    Then the response status should be 201
    When I delete the created tenant
    Then the response status should be 204
    When I get the created tenant by its database ID
    Then the response status should be 400

  Scenario: Reject duplicate external ID on create
    When I create a tenant with specific external ID "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee" and internal ID "mgmt-bdd-dup1"
    Then the response status should be 201
    When I create a tenant with specific external ID "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee" and internal ID "mgmt-bdd-dup2"
    Then the response status should be 400

  Scenario: Admin API does not require tenant header
    Given multi-tenancy is enabled
    When I list all tenants via admin API
    Then the response status should be 200
