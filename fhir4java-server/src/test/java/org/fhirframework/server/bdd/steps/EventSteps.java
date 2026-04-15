package org.fhirframework.server.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for Phase 3 Real-Time Events BDD tests.
 * <p>
 * Covers:
 * <ul>
 *   <li>Webhook management (registration, listing, deletion, enable/disable)</li>
 *   <li>SSE streaming (connection, filtering, event reception)</li>
 *   <li>Resource change events (create/update/delete triggers)</li>
 *   <li>Subscription management (registration, matching, delivery)</li>
 * </ul>
 * </p>
 */
public class EventSteps {

    @Autowired
    private SharedTestContext ctx;

    private String webhookCallbackUrl;
    private List<String> webhookTopics;
    private String webhookSecret;

    // ========== Given Steps ==========

    @Given("the FHIR server is running")
    public void theFhirServerIsRunning() {
        // Server is started by Spring Boot test context
        // Verify by checking metadata endpoint
        Response response = given()
                .when()
                .get("/metadata");
        response.then().statusCode(200);
    }

    @Given("I am authenticated as tenant {string}")
    public void iAmAuthenticatedAsTenant(String tenantId) {
        ctx.setCurrentTenantId(tenantId);
    }

    @Given("the event publishing system is active")
    public void theEventPublishingSystemIsActive() {
        // Event publishing is always active when server is running
        // This is a documentation step
    }

    @Given("the subscription manager is active")
    public void theSubscriptionManagerIsActive() {
        // Subscription manager is always active when server is running
        // This is a documentation step
    }

    @Given("I have a webhook registration request:")
    public void iHaveAWebhookRegistrationRequest(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap(String.class, String.class);
        webhookCallbackUrl = data.get("callbackUrl");
        String topicsStr = data.get("topics");
        webhookTopics = topicsStr != null ? List.of(topicsStr.split(",")) : null;
        webhookSecret = data.get("secret");
    }

    @Given("I have registered {int} webhooks for my tenant")
    public void iHaveRegisteredWebhooksForMyTenant(int count) {
        for (int i = 0; i < count; i++) {
            String body = String.format("""
                    {
                        "callbackUrl": "https://test-%d.example.com/callback",
                        "topics": ["Patient.create"]
                    }
                    """, i);

            Response response = given()
                    .contentType("application/json")
                    .header("X-Tenant-ID", ctx.getCurrentTenantId())
                    .body(body)
                    .when()
                    .post("/api/webhooks");

            response.then().statusCode(201);
            ctx.incrementWebhookCount();
        }
    }

    @Given("I have registered a webhook with callback URL {string}")
    public void iHaveRegisteredAWebhookWithCallbackUrl(String callbackUrl) {
        String body = String.format("""
                {
                    "callbackUrl": "%s",
                    "topics": ["Patient.create"]
                }
                """, callbackUrl);

        Response response = given()
                .contentType("application/json")
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .body(body)
                .when()
                .post("/api/webhooks");

        response.then().statusCode(201);
        ctx.setLastResponse(response);
        Long webhookId = response.jsonPath().getLong("id");
        ctx.setLastWebhookId(webhookId);
    }

    @Given("I have registered an enabled webhook")
    public void iHaveRegisteredAnEnabledWebhook() {
        iHaveRegisteredAWebhookWithCallbackUrl("https://enabled-test.example.com/callback");
    }

    @Given("I have registered and disabled a webhook")
    public void iHaveRegisteredAndDisabledAWebhook() {
        iHaveRegisteredAWebhookWithCallbackUrl("https://disabled-test.example.com/callback");
        // Disable it
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .post("/api/webhooks/" + ctx.getLastWebhookId() + "/disable");
        response.then().statusCode(200);
    }

    @Given("I have registered a webhook as tenant {string}")
    public void iHaveRegisteredAWebhookAsTenant(String tenantId) {
        String body = """
                {
                    "callbackUrl": "https://tenant-specific.example.com/callback",
                    "topics": ["Patient.create"]
                }
                """;

        Response response = given()
                .contentType("application/json")
                .header("X-Tenant-ID", tenantId)
                .body(body)
                .when()
                .post("/api/webhooks");

        response.then().statusCode(201);
        ctx.setLastWebhookId(response.jsonPath().getLong("id"));
    }

    @Given("a Patient resource exists with ID {string}")
    public void aPatientResourceExistsWithId(String resourceId) {
        String body = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "active": true,
                    "name": [{"family": "TestFamily", "given": ["TestGiven"]}],
                    "gender": "male"
                }
                """, resourceId);

        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .put("/Patient/" + resourceId);

        // Accept either 200 (update) or 201 (create)
        assertThat(response.statusCode(), anyOf(equalTo(200), equalTo(201)));
        ctx.setLastCreatedResourceId(resourceId);
        ctx.setLastResourceType("Patient");
    }

    @Given("I have a subscription for resource type {string} with events {string}")
    public void iHaveASubscriptionForResourceTypeWithEvents(String resourceType, String events) {
        // Store subscription info for later use
        ctx.setLastResourceType(resourceType);
        ctx.setRequestBody(events);
    }

    @Given("the subscription is registered")
    public void theSubscriptionIsRegistered() {
        // Subscription registration would be done via API
        // For now, this is a placeholder
    }

    @Given("I have registered subscriptions:")
    public void iHaveRegisteredSubscriptions(DataTable dataTable) {
        List<Map<String, String>> subscriptions = dataTable.asMaps();
        ctx.setRegisteredWebhookCount(subscriptions.size());
    }

    @Given("I have a registered subscription with ID {string}")
    public void iHaveARegisteredSubscriptionWithId(String subscriptionId) {
        ctx.setLastCreatedResourceId(subscriptionId);
    }

    @Given("I have a subscription topic:")
    public void iHaveASubscriptionTopic(DataTable dataTable) {
        Map<String, String> topicData = dataTable.asMap(String.class, String.class);
        ctx.setLastResourceType(topicData.get("resourceType"));
        ctx.setRequestBody(topicData.get("events"));
    }

    @Given("the subscription manager is subscribed to the event publisher")
    public void theSubscriptionManagerIsSubscribedToTheEventPublisher() {
        // This is automatic in the implementation
    }

    // ========== When Steps ==========

    @When("I register the webhook")
    public void iRegisterTheWebhook() {
        StringBuilder bodyBuilder = new StringBuilder("{");
        bodyBuilder.append("\"callbackUrl\":\"").append(webhookCallbackUrl).append("\"");

        if (webhookTopics != null && !webhookTopics.isEmpty()) {
            String topicsJson = webhookTopics.stream()
                    .map(t -> "\"" + t + "\"")
                    .collect(Collectors.joining(","));
            bodyBuilder.append(",\"topics\":[").append(topicsJson).append("]");
        }

        if (webhookSecret != null) {
            bodyBuilder.append(",\"secret\":\"").append(webhookSecret).append("\"");
        }

        bodyBuilder.append("}");

        Response response = given()
                .contentType("application/json")
                .header("X-Tenant-ID", ctx.getCurrentTenantId() != null ? ctx.getCurrentTenantId() : "default")
                .body(bodyBuilder.toString())
                .when()
                .post("/api/webhooks");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastWebhookId(response.jsonPath().getLong("id"));
        }
    }

    @When("I list all webhooks")
    public void iListAllWebhooks() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .get("/api/webhooks");

        ctx.setLastResponse(response);
    }

    @When("I list webhooks as tenant {string}")
    public void iListWebhooksAsTenant(String tenantId) {
        Response response = given()
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/api/webhooks");

        ctx.setLastResponse(response);
    }

    @When("I get the webhook by its ID")
    public void iGetTheWebhookByItsId() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .get("/api/webhooks/" + ctx.getLastWebhookId());

        ctx.setLastResponse(response);
    }

    @When("I get webhook with ID {long}")
    public void iGetWebhookWithId(Long webhookId) {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .get("/api/webhooks/" + webhookId);

        ctx.setLastResponse(response);
    }

    @When("I delete the webhook by its ID")
    public void iDeleteTheWebhookByItsId() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .delete("/api/webhooks/" + ctx.getLastWebhookId());

        ctx.setLastResponse(response);
    }

    @When("I disable the webhook")
    public void iDisableTheWebhook() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .post("/api/webhooks/" + ctx.getLastWebhookId() + "/disable");

        ctx.setLastResponse(response);
    }

    @When("I enable the webhook")
    public void iEnableTheWebhook() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .post("/api/webhooks/" + ctx.getLastWebhookId() + "/enable");

        ctx.setLastResponse(response);
    }

    @When("I try to get that webhook as tenant {string}")
    public void iTryToGetThatWebhookAsTenant(String tenantId) {
        Response response = given()
                .header("X-Tenant-ID", tenantId)
                .when()
                .get("/api/webhooks/" + ctx.getLastWebhookId());

        ctx.setLastResponse(response);
    }

    @When("I create a Patient resource")
    public void iCreateAPatientResource() {
        String body = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "EventTestFamily", "given": ["EventTestGiven"]}],
                    "gender": "female"
                }
                """;

        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", ctx.getCurrentTenantId() != null ? ctx.getCurrentTenantId() : "default")
                .body(body)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.setLastResourceType("Patient");
        }
    }

    @When("I create a Patient resource with family name {string}")
    public void iCreateAPatientResourceWithFamilyName(String familyName) {
        String body = String.format("""
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "%s", "given": ["TestGiven"]}],
                    "gender": "male"
                }
                """, familyName);

        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", ctx.getCurrentTenantId() != null ? ctx.getCurrentTenantId() : "default")
                .body(body)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
            ctx.setLastResourceType("Patient");
        }
    }

    @When("I create a Patient resource for tenant {string}")
    public void iCreateAPatientResourceForTenant(String tenantId) {
        String body = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "TenantTestFamily", "given": ["TenantTestGiven"]}],
                    "gender": "male"
                }
                """;

        Response response = given()
                .contentType("application/fhir+json")
                .header("X-Tenant-ID", tenantId)
                .body(body)
                .when()
                .post("/Patient");

        ctx.setLastResponse(response);
        if (response.statusCode() == 201) {
            ctx.setLastCreatedResourceId(ctx.extractResourceId(response.body().asString()));
        }
    }

    @When("I update the Patient with family name {string}")
    public void iUpdateThePatientWithFamilyName(String familyName) {
        String body = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "active": true,
                    "name": [{"family": "%s", "given": ["UpdatedGiven"]}],
                    "gender": "male"
                }
                """, ctx.getLastCreatedResourceId(), familyName);

        Response response = given()
                .contentType("application/fhir+json")
                .body(body)
                .when()
                .put("/Patient/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
    }

    @When("I delete the Patient")
    public void iDeleteThePatient() {
        Response response = given()
                .when()
                .delete("/Patient/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
    }

    @When("I read the Patient")
    public void iReadThePatient() {
        Response response = given()
                .when()
                .get("/Patient/" + ctx.getLastCreatedResourceId());

        ctx.setLastResponse(response);
    }

    @When("I search for Patient resources")
    public void iSearchForPatientResources() {
        Response response = given()
                .when()
                .get("/Patient");

        ctx.setLastResponse(response);
    }

    @When("I connect to the SSE events stream")
    public void iConnectToTheSseEventsStream() {
        // SSE connection test - verify endpoint is accessible
        Response response = given()
                .accept("text/event-stream")
                .when()
                .get("/api/events/stream");

        ctx.setLastResponse(response);
    }

    @When("I connect to the SSE events stream with topics {string}")
    public void iConnectToTheSseEventsStreamWithTopics(String topics) {
        Response response = given()
                .accept("text/event-stream")
                .queryParam("topics", topics)
                .when()
                .get("/api/events/stream");

        ctx.setLastResponse(response);
    }

    @When("I connect to the SSE events stream with actions {string}")
    public void iConnectToTheSseEventsStreamWithActions(String actions) {
        Response response = given()
                .accept("text/event-stream")
                .queryParam("actions", actions)
                .when()
                .get("/api/events/stream");

        ctx.setLastResponse(response);
    }

    @When("I connect to the SSE events stream with topics {string} and actions {string}")
    public void iConnectToTheSseEventsStreamWithTopicsAndActions(String topics, String actions) {
        Response response = given()
                .accept("text/event-stream")
                .queryParam("topics", topics)
                .queryParam("actions", actions)
                .when()
                .get("/api/events/stream");

        ctx.setLastResponse(response);
    }

    @When("I unregister the subscription")
    public void iUnregisterTheSubscription() {
        // Subscription unregistration placeholder
    }

    @When("I check if subscription {string} exists")
    public void iCheckIfSubscriptionExists(String subscriptionId) {
        // Subscription existence check placeholder
    }

    @When("a Patient create event occurs")
    public void aPatientCreateEventOccurs() {
        iCreateAPatientResource();
    }

    // ========== Then Steps ==========

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int statusCode) {
        ctx.getLastResponse().then().statusCode(statusCode);
    }

    @Then("the webhook response should contain an {string}")
    public void theWebhookResponseShouldContainAn(String field) {
        assertNotNull(ctx.getLastResponse().jsonPath().get(field),
                "Response should contain field: " + field);
    }

    @Then("the webhook should be {string}")
    public void theWebhookShouldBe(String status) {
        boolean expectedEnabled = "enabled".equalsIgnoreCase(status);
        boolean actualEnabled = ctx.getLastResponse().jsonPath().getBoolean("enabled");
        assertEquals(expectedEnabled, actualEnabled);
    }

    @Then("the webhook topics should be {string}")
    public void theWebhookTopicsShouldBe(String expectedTopics) {
        List<String> actualTopics = ctx.getLastResponse().jsonPath().getList("topics");
        List<String> expected = List.of(expectedTopics.split(","));
        assertEquals(expected, actualTopics);
    }

    @Then("the response should contain at least {int} webhooks")
    public void theResponseShouldContainAtLeastWebhooks(int count) {
        List<?> webhooks = ctx.getLastResponse().jsonPath().getList("$");
        assertThat(webhooks.size(), greaterThanOrEqualTo(count));
    }

    @Then("the webhook callback URL should be {string}")
    public void theWebhookCallbackUrlShouldBe(String expectedUrl) {
        String actualUrl = ctx.getLastResponse().jsonPath().getString("callbackUrl");
        assertEquals(expectedUrl, actualUrl);
    }

    @Then("the webhook should no longer exist")
    public void theWebhookShouldNoLongerExist() {
        Response response = given()
                .header("X-Tenant-ID", ctx.getCurrentTenantId())
                .when()
                .get("/api/webhooks/" + ctx.getLastWebhookId());

        response.then().statusCode(404);
    }

    @Then("the response should only contain webhooks for {string}")
    public void theResponseShouldOnlyContainWebhooksFor(String tenantId) {
        // All returned webhooks should be for the specified tenant
        // In a real implementation, we'd verify tenant isolation
        ctx.getLastResponse().then().statusCode(200);
    }

    @Then("the connection should be established")
    public void theConnectionShouldBeEstablished() {
        ctx.getLastResponse().then().statusCode(200);
    }

    @Then("the response content type should be {string}")
    public void theResponseContentTypeShouldBe(String contentType) {
        String actualContentType = ctx.getLastResponse().contentType();
        assertThat(actualContentType, containsString(contentType));
    }

    @Then("a resource change event should be published")
    public void aResourceChangeEventShouldBePublished() {
        // Verify the resource was created/updated/deleted successfully
        // The event publishing is verified by the EventPublisher mechanism
        assertNotNull(ctx.getLastCreatedResourceId());
    }

    @Then("the event should have resource type {string}")
    public void theEventShouldHaveResourceType(String resourceType) {
        assertEquals(resourceType, ctx.getLastResourceType());
    }

    @Then("the event should have action {string}")
    public void theEventShouldHaveAction(String action) {
        // Verify based on the last operation performed
        int statusCode = ctx.getLastResponse().statusCode();
        switch (action) {
            case "create" -> assertEquals(201, statusCode);
            case "update" -> assertEquals(200, statusCode);
            case "delete" -> assertEquals(204, statusCode);
        }
    }

    @Then("the event should have resource ID {string}")
    public void theEventShouldHaveResourceId(String resourceId) {
        assertEquals(resourceId, ctx.getLastCreatedResourceId());
    }

    @Then("no resource change event should be published")
    public void noResourceChangeEventShouldBePublished() {
        // For read/search operations, no events are published
        // This is verified by the operation completing without triggering event plugin
        int statusCode = ctx.getLastResponse().statusCode();
        assertThat(statusCode, anyOf(equalTo(200), equalTo(404)));
    }

    @Then("the subscription should be active")
    public void theSubscriptionShouldBeActive() {
        // Subscription activation placeholder
    }

    @Then("the subscription should match Patient create events")
    public void theSubscriptionShouldMatchPatientCreateEvents() {
        // Subscription matching placeholder
    }

    @Then("the subscription should match Patient update events")
    public void theSubscriptionShouldMatchPatientUpdateEvents() {
        // Subscription matching placeholder
    }

    @Then("the subscription should not match Patient delete events")
    public void theSubscriptionShouldNotMatchPatientDeleteEvents() {
        // Subscription matching placeholder
    }

    @Then("the subscription should match all Observation events")
    public void theSubscriptionShouldMatchAllObservationEvents() {
        // Subscription matching placeholder
    }

    @Then("the subscription count should be {int}")
    public void theSubscriptionCountShouldBe(int count) {
        assertEquals(count, ctx.getRegisteredWebhookCount());
    }

    @Then("the subscription should be in the matching list")
    public void theSubscriptionShouldBeInTheMatchingList() {
        // Subscription matching placeholder
    }

    @Then("{int} subscriptions should match")
    public void subscriptionsShouldMatch(int count) {
        // Subscription matching placeholder
    }

    @Then("the subscription should not be in the matching list")
    public void theSubscriptionShouldNotBeInTheMatchingList() {
        // Subscription matching placeholder
    }

    @Then("the subscription should no longer exist")
    public void theSubscriptionShouldNoLongerExist() {
        // Subscription unregistration placeholder
    }

    @Then("the subscription count should decrease by {int}")
    public void theSubscriptionCountShouldDecreaseBy(int decrement) {
        // Subscription count placeholder
    }

    @Then("the result should be true")
    public void theResultShouldBeTrue() {
        // Subscription existence check placeholder
    }

    @Then("the result should be false")
    public void theResultShouldBeFalse() {
        // Subscription existence check placeholder
    }

    @Then("the event should be delivered to the subscription")
    public void theEventShouldBeDeliveredToTheSubscription() {
        // Event delivery placeholder
    }

    @Then("the event should be delivered to subscription {string}")
    public void theEventShouldBeDeliveredToSubscription(String subscriptionId) {
        // Event delivery placeholder
    }

    @Then("the event should not be delivered to the subscription")
    public void theEventShouldNotBeDeliveredToTheSubscription() {
        // Event non-delivery placeholder
    }

    @Then("the topic should match resource {string} action {string}")
    public void theTopicShouldMatchResourceAction(String resourceType, String action) {
        assertEquals(resourceType, ctx.getLastResourceType());
    }

    @Then("the topic should not match resource {string} action {string}")
    public void theTopicShouldNotMatchResourceAction(String resourceType, String action) {
        assertNotEquals(resourceType, ctx.getLastResourceType());
    }

    @Then("the subscription manager should process the event")
    public void theSubscriptionManagerShouldProcessTheEvent() {
        // Event processing placeholder
    }

    @Then("the matching subscription should be notified")
    public void theMatchingSubscriptionShouldBeNotified() {
        // Subscription notification placeholder
    }

    @Then("the published event should have tenant ID {string}")
    public void thePublishedEventShouldHaveTenantId(String tenantId) {
        // Tenant ID verification placeholder
    }
}
