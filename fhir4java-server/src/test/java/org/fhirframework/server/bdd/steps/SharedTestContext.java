package org.fhirframework.server.bdd.steps;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

/**
 * Shared test context for Cucumber BDD scenarios.
 * <p>
 * Scoped per scenario to share state across step definition classes.
 * </p>
 */
@Component
@ScenarioScope
public class SharedTestContext {

    private Response lastResponse;
    private String lastCreatedPatientId;
    private String lastCreatedResourceId;
    private String requestBody;
    private String lastVersionId;
    private String lastResourceType;
    private String lastEtag;

    public Response getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(Response lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getLastCreatedPatientId() {
        return lastCreatedPatientId;
    }

    public void setLastCreatedPatientId(String lastCreatedPatientId) {
        this.lastCreatedPatientId = lastCreatedPatientId;
    }

    public String getLastCreatedResourceId() {
        return lastCreatedResourceId;
    }

    public void setLastCreatedResourceId(String lastCreatedResourceId) {
        this.lastCreatedResourceId = lastCreatedResourceId;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getLastVersionId() {
        return lastVersionId;
    }

    public void setLastVersionId(String lastVersionId) {
        this.lastVersionId = lastVersionId;
    }

    public String getLastResourceType() {
        return lastResourceType;
    }

    public void setLastResourceType(String lastResourceType) {
        this.lastResourceType = lastResourceType;
    }

    public String getLastEtag() {
        return lastEtag;
    }

    public void setLastEtag(String lastEtag) {
        this.lastEtag = lastEtag;
    }

    /**
     * Extract the version ID from an ETag header value like {@code W/"1"}.
     */
    public String extractVersionId(String etag) {
        if (etag == null) return null;
        return etag.replaceAll("W/\"", "").replaceAll("\"", "");
    }

    /**
     * Extract and store the ETag from the last response.
     */
    public void captureEtag() {
        if (lastResponse != null) {
            lastEtag = lastResponse.header("ETag");
            lastVersionId = extractVersionId(lastEtag);
        }
    }

    /**
     * Extract the resource ID from a JSON response body.
     */
    public String extractResourceId(String responseBody) {
        int idStart = responseBody.indexOf("\"id\"");
        if (idStart == -1) return null;

        int valueStart = responseBody.indexOf("\"", idStart + 4) + 1;
        int valueEnd = responseBody.indexOf("\"", valueStart);
        String fullId = responseBody.substring(valueStart, valueEnd);

        if (fullId.contains("/")) {
            return fullId.substring(fullId.lastIndexOf("/") + 1);
        }
        return fullId;
    }
}
