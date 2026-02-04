package org.fhirframework.server.test.validation;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared context for Profile Validation BDD tests.
 * Maintains state across step definition classes within a scenario.
 */
@Component
@ScenarioScope
public class ProfileValidationTestContext {

    private Response lastResponse;
    private String lastResourceId;
    private Map<String, Object> testData = new HashMap<>();
    private Map<String, Integer> statusCodes = new HashMap<>();
    private String currentFhirVersion = "r5";

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getLastResourceId() {
        return lastResourceId;
    }

    public void setLastResourceId(String lastResourceId) {
        this.lastResourceId = lastResourceId;
    }

    public Map<String, Object> getTestData() {
        return testData;
    }

    public void putTestData(String key, Object value) {
        this.testData.put(key, value);
    }

    public Object getTestData(String key) {
        return testData.get(key);
    }

    public void clearTestData() {
        this.testData.clear();
    }

    public Map<String, Integer> getStatusCodes() {
        return statusCodes;
    }

    public void recordStatusCode(String scenario, int statusCode) {
        this.statusCodes.put(scenario, statusCode);
    }

    public String getCurrentFhirVersion() {
        return currentFhirVersion;
    }

    public void setCurrentFhirVersion(String currentFhirVersion) {
        this.currentFhirVersion = currentFhirVersion;
    }

    public void reset() {
        this.lastResponse = null;
        this.lastResourceId = null;
        this.testData.clear();
        this.statusCodes.clear();
        this.currentFhirVersion = "r5";
    }
}
