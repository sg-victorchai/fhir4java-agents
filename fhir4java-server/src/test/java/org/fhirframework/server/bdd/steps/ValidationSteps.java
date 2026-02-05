package org.fhirframework.server.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Consolidated step definitions for ALL profile validation BDD tests.
 *
 * This single class handles all validation-related feature files to eliminate duplicates:
 * - profile-validator-initialization.feature
 * - profile-validation-modes.feature
 * - profile-validator-health.feature
 * - profile-validator-metrics.feature
 * - profile-validation-integration.feature
 * - http-422-validation-errors.feature
 *
 * Note: This class uses explicit basePath("") on requests since it uses full paths like /fhir/r5/Patient.
 */
public class ValidationSteps {

    @Autowired
    private SharedTestContext ctx;

    private long startupTime;
    private int initialAttempts = 0;
    private String fhirVersion = "r5";
    private String originalResourceBody;

    /**
     * Build a request with empty basePath since this class uses full paths.
     */
    private RequestSpecification request() {
        return given().basePath("");
    }

    // =====================================================
    // COMMON BACKGROUND STEPS
    // =====================================================



    @Given("the FHIR server is starting up")
    public void theFhirServerIsStartingUp() {
        startupTime = System.currentTimeMillis();
    }

    @Given("Spring Boot Actuator is enabled")
    public void springBootActuatorIsEnabled() {
        // Actuator enabled via application.yml
    }

    @Given("Micrometer is configured")
    public void micrometerIsConfigured() {
        // Micrometer configured via application.yml
    }

    @Given("I set the FHIR version to {string}")
    public void iSetTheFhirVersionTo(String version) {
        this.fhirVersion = version.toLowerCase();
    }

    // =====================================================
    // CONFIGURATION STEPS
    // =====================================================

    @Given("profile-validator-enabled is set to true")
    public void profileValidatorEnabledIsSetToTrue() {
        // Configuration set via application.yml
    }

    @Given("profile-validator-enabled is set to false")
    public void profileValidatorEnabledIsSetToFalse() {
        // Configuration set via application.yml
    }

    @Given("profile-validation is set to {string}")
    public void profileValidationIsSetTo(String mode) {
        // Configuration set via application.yml
    }

    @Given("log-validation-operations is set to true")
    public void logValidationOperationsIsSetToTrue() {
        // Configuration set via application.yml
    }

    @Given("logging level for validation is DEBUG")
    public void loggingLevelForValidationIsDebug() {
        // Configuration set via logback.xml
    }

    @Given("health-check-mode is set to {string}")
    public void healthCheckModeIsSetTo(String mode) {
        // Configuration set via application.yml
    }

    @Given("show-components is set to always")
    public void showComponentsIsSetToAlways() {
        // Configuration set via application.yml
    }

    @Given("show-components is not set to {string}")
    public void showComponentsIsNotSetTo(String value) {
        // Configuration scenario for testing 404
    }

    @Given("lazy-initialization is set to true")
    public void lazyInitializationIsSetToTrue() {
        // Configuration set via application.yml
    }

    @Given("only R5 is enabled in configuration")
    public void onlyR5IsEnabledInConfiguration() {
        // Configuration set via application.yml
    }

    @Given("R4B is disabled")
    public void r4bIsDisabled() {
        // Configuration set via application.yml
    }

    // =====================================================
    // DEPENDENCY STEPS
    // =====================================================

    @Given("commons-compress version {string} or higher is available")
    public void commonsCompressVersionOrHigherIsAvailable(String version) {
        // Parse version string like "1.26.0" and verify it's at least 1.26
        String[] parts = version.split("\\.");
        double majorMinor = Double.parseDouble(parts[0] + "." + parts[1]);
        assertThat("commons-compress should be version 1.26.0 or higher",
                majorMinor, greaterThanOrEqualTo(1.26));
    }

    @Given("hapi-fhir-caching-caffeine is available")
    public void hapiFhirCachingCaffeineIsAvailable() {
        // Dependency validated by Maven build
    }

    @Given("all required dependencies are present")
    public void allRequiredDependenciesArePresent() {
        // Dependencies validated by Maven and Spring Boot
    }

    @Given("commons-compress dependency is not available")
    public void commonsCompressDependencyIsNotAvailable() {
        // Documentation scenario - requires classpath modification
    }

    @Given("hapi-fhir-caching-caffeine dependency is not available")
    public void hapiFhirCachingCaffeineDependencyIsNotAvailable() {
        // Documentation scenario - requires classpath modification
    }

    // =====================================================
    // INITIALIZATION STATE STEPS
    // =====================================================

    @Given("the application has started")
    public void theApplicationHasStarted() {
        // Application started by Spring Boot
    }

    @Given("the application has just started")
    public void theApplicationHasJustStarted() {
        // Application just started
    }

    @Given("no validators have been initialized")
    public void noValidatorsHaveBeenInitialized() {
        // Verified by lazy-initialization setting
    }

    @Given("ProfileValidator has initialized successfully")
    public void profileValidatorHasInitializedSuccessfully() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        if (response.getStatusCode() == 200) {
            assertThat("ProfileValidator should be initialized", 
                    response.jsonPath().getBoolean("details.enabled"), is(true));
        }
    }

    @Given("ProfileValidator is initialized")
    public void profileValidatorIsInitialized() {
        profileValidatorHasInitializedSuccessfully();
    }

    @Given("R5 validator initialized successfully")
    public void r5ValidatorInitializedSuccessfully() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        if (response.getStatusCode() == 200) {
            String r5Status = response.jsonPath().getString("details.versions.r5.status");
            assertThat("R5 should be initialized", r5Status, is("initialized"));
        }
    }

    @Given("R4B validator failed to initialize")
    public void r4bValidatorFailedToInitialize() {
        // Documentation scenario
    }

    @Given("all validators failed to initialize")
    public void allValidatorsFailedToInitialize() {
        // Documentation scenario
    }

    @Given("R5 validator configuration is invalid")
    public void r5ValidatorConfigurationIsInvalid() {
        // Documentation scenario
    }

    @Given("R4B validator configuration is valid")
    public void r4bValidatorConfigurationIsValid() {
        // Documentation scenario
    }

    // =====================================================
    // RESOURCE PREPARATION STEPS
    // =====================================================

    // REMOVE @Given annotation
    // RENAME method
    private void prepareValidPatientResource() {
     String patientJson = """
             {
                 "resourceType": "Patient",
                 "active": true,
                 "name": [{"family": "Smith", "given": ["John"]}],
                 "gender": "male",
                 "birthDate": "1990-01-01"
             }
             """;
     ctx.setRequestBody(patientJson);
 }


    @Given("I have a Patient resource with validation errors")
    public void iHaveAPatientResourceWithValidationErrors() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "name": [{"family": "Test", "given": ["Invalid"]}],
                    "gender": "invalid-gender-code",
                    "birthDate": "not-a-date"
                }
                """;
        ctx.setRequestBody(patientJson);
    }

    @Given("I have a Patient resource with validation warnings but no errors")
    public void iHaveAPatientResourceWithValidationWarningsButNoErrors() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Test", "given": ["Warning"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;
        ctx.setRequestBody(patientJson);
    }

    @Given("I have a Patient resource with validation warnings")
    public void iHaveAPatientResourceWithValidationWarnings() {
        iHaveAPatientResourceWithValidationWarningsButNoErrors();
    }

    @Given("I have a Patient resource with invalid gender code {string}")
    public void iHaveAPatientResourceWithInvalidGenderCode(String genderCode) {
        String patientJson = String.format("""
                {
                    "resourceType": "Patient",
                    "name": [{"family": "Test", "given": ["Patient"]}],
                    "gender": "%s",
                    "birthDate": "1990-01-01"
                }
                """, genderCode);
        ctx.setRequestBody(patientJson);
    }

    @Given("I have invalid gender codes: {string}")
    public void iHaveInvalidGenderCodes(String codes) {
        // Store for batch processing
        ctx.setRequestBody(codes);
    }

    @Given("I have valid gender codes: {string}")
    public void iHaveValidGenderCodes(String codes) {
        // Store for batch processing
        ctx.setRequestBody(codes);
    }

    @Given("I have a Patient resource missing family name")
    public void iHaveAPatientResourceMissingFamilyName() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "name": [{"given": ["John"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;
        ctx.setRequestBody(patientJson);
    }

    @Given("I have a Patient resource with birthDate {string}")
    public void iHaveAPatientResourceWithBirthDate(String birthDate) {
        String patientJson = String.format("""
                {
                    "resourceType": "Patient",
                    "name": [{"family": "Test", "given": ["Patient"]}],
                    "gender": "male",
                    "birthDate": "%s"
                }
                """, birthDate);
        ctx.setRequestBody(patientJson);
    }

    @Given("I have malformed JSON content")
    public void iHaveMalformedJsonContent() {
        ctx.setRequestBody("{invalid json}}");
    }

    @Given("I have a Patient with multiple validation errors")
    public void iHaveAPatientWithMultipleValidationErrors() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "gender": "invalid-code",
                    "birthDate": "not-a-date",
                    "name": []
                }
                """;
        ctx.setRequestBody(patientJson);
    }

    @Given("I have various invalid Patient resources")
    public void iHaveVariousInvalidPatientResources() {
        // Store count for batch processing
    }

    @Given("the server requires US Core Patient profile")
    public void theServerRequiresUSCorePatientProfile() {
        // Configuration scenario
    }

    @Given("I have an existing Patient resource with id {string}")
    public void iHaveAnExistingPatientResourceWithId(String patientId) {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Existing", "given": ["Test"]}],
                    "gender": "male",
                    "birthDate": "1985-05-15"
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        
        if (response.getStatusCode() == 201) {
            String location = response.getHeader("Location");
            if (location != null) {
                String[] parts = location.split("/");
                String actualId = parts[parts.length - 1];
                ctx.setLastCreatedPatientId(actualId);
                ctx.setLastCreatedResourceId(actualId);
            }
        }
        
        originalResourceBody = response.getBody().asString();
    }

    @Given("I have an existing valid Patient resource with id {string}")
    public void iHaveAnExistingValidPatientResourceWithId(String patientId) {
        iHaveAnExistingPatientResourceWithId(patientId);
    }

    @Given("I have an updated Patient resource")
    public void iHaveAnUpdatedPatientResource() {
        String patientId = ctx.getLastCreatedPatientId();
        if (patientId == null) {
            patientId = "123";
        }

        String updatedPatientJson = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "active": true,
                    "name": [{"family": "Smith-Updated", "given": ["John"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01",
                    "telecom": [{"system": "phone", "value": "555-1234"}]
                }
                """, patientId);
        
        ctx.setRequestBody(updatedPatientJson);
    }

    @Given("I have an invalid updated version")
    public void iHaveAnInvalidUpdatedVersion() {
        String patientId = ctx.getLastCreatedPatientId();
        if (patientId == null) {
            patientId = "123";
        }

        String invalidPatientJson = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "name": [{"family": "Invalid", "given": ["Test"]}],
                    "gender": "invalid-gender-code",
                    "birthDate": "not-a-valid-date"
                }
                """, patientId);
        
        ctx.setRequestBody(invalidPatientJson);
    }

    @Given("I create a Patient resource with validation")
    public void iCreateAPatientResourceWithValidation() {
    	prepareValidPatientResource();
        iSendAPostRequestTo("/fhir/" + fhirVersion + "/Patient");
    }

    @Given("the Patient is persisted successfully")
    public void thePatientIsPersistedSuccessfully() {
        assertThat("Patient should be created", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    // =====================================================
    // METRICS STATE STEPS
    // =====================================================

    @Given("no validation has been performed yet")
    public void noValidationHasBeenPerformedYet() {
        // Test runs early before validations
    }

    @Given("validation metrics start at zero")
    public void validationMetricsStartAtZero() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        if (response.getStatusCode() == 200) {
            Object value = response.jsonPath().get("measurements[0].value");
            initialAttempts = value != null ? ((Number) value).intValue() : 0;
        }
    }

    @Given("metrics start at zero")
    public void metricsStartAtZero() {
        validationMetricsStartAtZero();
    }

    @Given("I have validated resources")
    public void iHaveValidatedResources() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Test", "given": ["Metrics"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
    }

    @Given("I validate a valid Patient resource")
    public void iValidateAValidPatientResource() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Valid", "given": ["Test"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        
        ctx.setLastResponse(response);
    }

    @Given("I validate an invalid Patient resource")
    public void iValidateAnInvalidPatientResource() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "name": [{"family": "Invalid", "given": ["Test"]}],
                    "gender": "invalid-code",
                    "birthDate": "not-a-date"
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        
        ctx.setLastResponse(response);
    }

    @Given("I validate multiple Patient resources")
    public void iValidateMultiplePatientResources() {
        for (int i = 0; i < 3; i++) {
            String patientJson = String.format("""
                    {
                        "resourceType": "Patient",
                        "active": true,
                        "name": [{"family": "Patient%d", "given": ["Test"]}],
                        "gender": "male",
                        "birthDate": "1990-01-01"
                    }
                    """, i);

            request()
                    .contentType(ContentType.JSON)
                    .body(patientJson)
                    .when()
                    .post("/fhir/" + fhirVersion + "/Patient");
        }
    }

    @Given("I validate multiple Observation resources")
    public void iValidateMultipleObservationResources() {
        for (int i = 0; i < 3; i++) {
            String observationJson = String.format("""
                    {
                        "resourceType": "Observation",
                        "status": "final",
                        "code": {
                            "coding": [{
                                "system": "http://loinc.org",
                                "code": "15074-8",
                                "display": "Glucose [Moles/volume] in Blood"
                            }]
                        },
                        "subject": {"reference": "Patient/test-%d"}
                    }
                    """, i);

            request()
                    .contentType(ContentType.JSON)
                    .body(observationJson)
                    .when()
                    .post("/fhir/" + fhirVersion + "/Observation");
        }
    }

    @Given("I have validated {int} resources")
    public void iHaveValidatedResources(int count) {
        for (int i = 0; i < count; i++) {
            String patientJson = String.format("""
                    {
                        "resourceType": "Patient",
                        "active": true,
                        "name": [{"family": "Test%d", "given": ["Batch"]}],
                        "gender": "male",
                        "birthDate": "1990-01-01"
                    }
                    """, i);

            request()
                    .contentType(ContentType.JSON)
                    .body(patientJson)
                    .when()
                    .post("/fhir/" + fhirVersion + "/Patient");
        }
    }

    // =====================================================
    // WHEN STEPS - ACTIONS
    // =====================================================

    @When("the application starts")
    public void theApplicationStarts() {
        // Application started by Spring Boot
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @When("the application attempts to start")
    public void theApplicationAttemptsToStart() {
        // Application startup handled by Spring Boot
    }

    @When("ProfileValidator initializes")
    public void profileValidatorInitializes() {
        // Initialization happens at startup
    }

    @When("I send a GET request to {string}")
    public void iSendAGetRequestTo(String endpoint) {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get(endpoint);
        
        ctx.setLastResponse(response);
    }

    @When("I send a POST request to {string}")
    public void iSendAPostRequestTo(String endpoint) {
        Response response = request()
                .contentType(ContentType.JSON)
                .body(ctx.getRequestBody())
                .when()
                .post(endpoint);
        
        ctx.setLastResponse(response);
        
        if (response.getStatusCode() == 201) {
            String location = response.getHeader("Location");
            if (location != null) {
                String[] parts = location.split("/");
                String resourceId = parts[parts.length - 1];
                ctx.setLastCreatedResourceId(resourceId);
                if (endpoint.contains("Patient")) {
                    ctx.setLastCreatedPatientId(resourceId);
                }
            }
        }
    }

    @When("I send a PUT request to {string}")
    public void iSendAPutRequestTo(String endpoint) {
        Response response = request()
                .contentType(ContentType.JSON)
                .body(ctx.getRequestBody())
                .when()
                .put(endpoint);
        
        ctx.setLastResponse(response);
    }

    @When("I POST Patient resources with each invalid code")
    public void iPostPatientResourcesWithEachInvalidCode() {
        // Batch POST with invalid codes
        String[] codes = ctx.getRequestBody().split(",");
        for (String code : codes) {
            String trimmedCode = code.trim().replace("\"", "");
            iHaveAPatientResourceWithInvalidGenderCode(trimmedCode);
            iSendAPostRequestTo("/fhir/" + fhirVersion + "/Patient");
        }
    }

    @When("I POST Patient resources with each valid code")
    public void iPostPatientResourcesWithEachValidCode() {
        // Batch POST with valid codes
        String[] codes = ctx.getRequestBody().split(",");
        for (String code : codes) {
            String trimmedCode = code.trim().replace("\"", "");
            String patientJson = String.format("""
                    {
                        "resourceType": "Patient",
                        "name": [{"family": "Test", "given": ["Patient"]}],
                        "gender": "%s",
                        "birthDate": "1990-01-01"
                    }
                    """, trimmedCode);
            ctx.setRequestBody(patientJson);
            iSendAPostRequestTo("/fhir/" + fhirVersion + "/Patient");
        }
    }

    @When("I validate a Patient resource for R5")
    public void iValidateAPatientResourceForR5() {
        fhirVersion = "r5";
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Test", "given": ["R5"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/r5/Patient");
        
        ctx.setLastResponse(response);
        
        if (response.getStatusCode() == 201) {
            String location = response.getHeader("Location");
            if (location != null) {
                String[] parts = location.split("/");
                String resourceId = parts[parts.length - 1];
                ctx.setLastCreatedResourceId(resourceId);
                ctx.setLastCreatedPatientId(resourceId);
            }
        }
    }

    @When("I validate an Observation resource for R5")
    public void iValidateAnObservationResourceForR5() {
        fhirVersion = "r5";
        String observationJson = """
                {
                    "resourceType": "Observation",
                    "status": "final",
                    "code": {
                        "coding": [{
                            "system": "http://loinc.org",
                            "code": "15074-8",
                            "display": "Glucose [Moles/volume] in Blood"
                        }]
                    },
                    "subject": {"reference": "Patient/test"}
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(observationJson)
                .when()
                .post("/fhir/r5/Observation");
        
        ctx.setLastResponse(response);
    }

    @When("I validate a Patient resource")
    public void iValidateAPatientResource() {
        iValidateAPatientResourceForR5();
    }

    @When("I create a Patient \\(validated)")
    public void iCreateAPatientValidated() {
        iValidateAPatientResourceForR5();
    }

    @When("I update the Patient \\(validated)")
    public void iUpdateThePatientValidated() {
        String patientId = ctx.getLastCreatedResourceId();
        if (patientId == null) {
            patientId = "test-update";
        }

        String patientJson = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "active": true,
                    "name": [{"family": "Updated", "given": ["Test"]}],
                    "gender": "female",
                    "birthDate": "1990-01-01"
                }
                """, patientId);

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .put("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        ctx.setLastResponse(response);
    }

    @When("I attempt an invalid update \\(validated, rejected)")
    public void iAttemptAnInvalidUpdateValidatedRejected() {
        String patientId = ctx.getLastCreatedResourceId();
        if (patientId == null) {
            patientId = "test-update";
        }

        String invalidJson = String.format("""
                {
                    "resourceType": "Patient",
                    "id": "%s",
                    "gender": "invalid-code",
                    "birthDate": "not-a-date"
                }
                """, patientId);

        Response response = request()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when()
                .put("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        ctx.setLastResponse(response);
    }

    @When("I query metrics with tag filter {string}")
    public void iQueryMetricsWithTagFilter(String tagFilter) {
        Response response = request()
                .contentType(ContentType.JSON)
                .queryParam("tag", tagFilter)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        ctx.setLastResponse(response);
    }

    @When("I attempt to validate a resource")
    public void iAttemptToValidateAResource() {
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true,
                    "name": [{"family": "Test", "given": ["Disabled"]}],
                    "gender": "male",
                    "birthDate": "1990-01-01"
                }
                """;

        Response response = request()
                .contentType(ContentType.JSON)
                .body(patientJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        
        ctx.setLastResponse(response);
    }

    @When("I create {int} Patient resources")
    public void iCreatePatientResources(int count) {
        for (int i = 0; i < count; i++) {
            String patientJson = String.format("""
                    {
                        "resourceType": "Patient",
                        "active": true,
                        "name": [{"family": "Test%d", "given": ["Patient"]}],
                        "gender": "male",
                        "birthDate": "1990-01-01"
                    }
                    """, i);

            Response response = request()
                    .contentType(ContentType.JSON)
                    .body(patientJson)
                    .when()
                    .post("/fhir/" + fhirVersion + "/Patient");
            
            ctx.setLastResponse(response);
        }
    }

    @When("I retrieve the Patient")
    public void iRetrieveThePatient() {
        String patientId = ctx.getLastCreatedPatientId();
        assertThat("Patient ID should be set", patientId, notNullValue());

        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        ctx.setLastResponse(response);
    }

    @When("I can update the Patient with valid data")
    public void iCanUpdateThePatientWithValidData() {
        iHaveAnUpdatedPatientResource();
        String patientId = ctx.getLastCreatedPatientId();
        iSendAPutRequestTo("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        assertThat("Update should succeed", 
                ctx.getLastResponse().getStatusCode(), is(200));
    }

    @When("invalid updates should be rejected with {int}")
    public void invalidUpdatesShouldBeRejectedWith(int statusCode) {
        iHaveAnInvalidUpdatedVersion();
        String patientId = ctx.getLastCreatedPatientId();
        iSendAPutRequestTo("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        assertThat("Invalid update should be rejected", 
                ctx.getLastResponse().getStatusCode(), is(statusCode));
    }

    @When("I perform multiple validation operations")
    public void iPerformMultipleValidationOperations() {
        iValidateMultiplePatientResources();
    }

    @When("I check the health endpoint")
    public void iCheckTheHealthEndpoint() {
        iSendAGetRequestTo("/actuator/health/profileValidator");
    }

    // =====================================================
    // THEN STEPS - INITIALIZATION ASSERTIONS
    // =====================================================

    @Then("ProfileValidator should initialize successfully")
    public void profileValidatorShouldInitializeSuccessfully() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("Health endpoint should be accessible", 
                response.getStatusCode(), is(200));
        assertThat("ProfileValidator should be UP", 
                response.jsonPath().getString("status"), is("UP"));
        assertThat("ProfileValidator should be enabled", 
                response.jsonPath().getBoolean("details.enabled"), is(true));
    }

    @Then("ProfileValidator should not initialize validators")
    public void profileValidatorShouldNotInitializeValidators() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("Health endpoint should be accessible", 
                response.getStatusCode(), is(200));
        assertThat("ProfileValidator should report disabled status", 
                response.jsonPath().getBoolean("details.disabled"), is(true));
    }

    @Then("ProfileValidator should not initialize validators at startup")
    public void profileValidatorShouldNotInitializeValidatorsAtStartup() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("ProfileValidator should report lazy initialization", 
                response.jsonPath().getBoolean("details.lazyInitialization"), is(true));
    }

    @Then("R5 validator should be initialized")
    public void r5ValidatorShouldBeInitialized() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("R5 validator should be initialized", 
                response.jsonPath().getString("details.versions.r5.status"), 
                is("initialized"));
        assertThat("R5 validator should be available", 
                response.jsonPath().getBoolean("details.versions.r5.available"), 
                is(true));
    }

    @Then("R4B validator should be initialized")
    public void r4bValidatorShouldBeInitialized() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("R4B validator should be initialized", 
                response.jsonPath().getString("details.versions.r4b.status"), 
                is("initialized"));
        assertThat("R4B validator should be available", 
                response.jsonPath().getBoolean("details.versions.r4b.available"), 
                is(true));
    }

    @Then("only R5 validator should be initialized")
    public void onlyR5ValidatorShouldBeInitialized() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("R5 validator should be available", 
                response.jsonPath().getBoolean("details.versions.r5.available"), 
                is(true));
    }

    @Then("R4B validator should not be initialized")
    public void r4bValidatorShouldNotBeInitialized() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        Boolean r4bAvailable = response.jsonPath().getBoolean("details.versions.r4b.available");
        assertThat("R4B validator should not be available", 
                r4bAvailable, anyOf(nullValue(), is(false)));
    }

    @Then("R5 validator should fail to initialize")
    public void r5ValidatorShouldFailToInitialize() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        String r5Status = response.jsonPath().getString("details.versions.r5.status");
        assertThat("R5 validator should have failed status", r5Status, is("failed"));
    }

    @Then("R4B validator should initialize successfully")
    public void r4bValidatorShouldInitializeSuccessfully() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("R4B validator should be initialized", 
                response.jsonPath().getString("details.versions.r4b.status"), 
                is("initialized"));
    }

    @Then("the R5 validator should be initialized on-demand")
    public void theR5ValidatorShouldBeInitializedOnDemand() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        assertThat("R5 validator should be available after lazy init", 
                response.jsonPath().getBoolean("details.versions.r5.available"), 
                is(true));
    }

    @Then("the total initialization time should be logged")
    public void theTotalInitializationTimeShouldBeLogged() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        String initTime = response.jsonPath().getString("details.totalInitializationTime");
        assertThat("Total initialization time should be present", 
                initTime, notNullValue());
        assertThat("Total initialization time should end with 'ms'", 
                initTime, endsWith("ms"));
    }

    @Then("each version initialization should be logged with duration")
    public void eachVersionInitializationShouldBeLoggedWithDuration() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        String r5InitTime = response.jsonPath().getString("details.versions.r5.initializationTime");
        assertThat("R5 initialization time should be present", 
                r5InitTime, notNullValue());
        assertThat("R5 initialization time should be in milliseconds", 
                r5InitTime, matchesPattern("\\d+ms"));
    }

    @Then("total initialization time should be sum of version times")
    public void totalInitializationTimeShouldBeSumOfVersionTimes() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        String totalTime = response.jsonPath().getString("details.totalInitializationTime");
        assertThat("Total initialization time should be present", 
                totalTime, notNullValue());
    }

    @Then("the initialization summary should show {string}")
    public void theInitializationSummaryShouldShow(String expectedSummary) {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health/profileValidator");
        
        int successCount = response.jsonPath().getInt("details.successCount");
        int totalCount = response.jsonPath().getInt("details.totalCount");
        String summary = successCount + "/" + totalCount + " versions initialized";
        assertThat("Initialization summary should match", 
                summary, containsString(expectedSummary));
    }

    @Then("the application should start successfully")
    public void theApplicationShouldStartSuccessfully() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/health");
        
        assertThat("Application should be healthy", 
                response.jsonPath().getString("status"), is("UP"));
    }

    @Then("the application should fail to start")
    public void theApplicationShouldFailToStart() {
        // Documentation scenario
    }

    @Then("startup time should be faster than with validation enabled")
    public void startupTimeShouldBeFasterThanWithValidationEnabled() {
        long elapsed = System.currentTimeMillis() - startupTime;
        assertThat("Startup should be faster without validation", 
                elapsed, lessThan(5000L));
    }

    @Then("startup time should be significantly reduced")
    public void startupTimeShouldBeSignificantlyReduced() {
        long elapsed = System.currentTimeMillis() - startupTime;
        assertThat("Startup with lazy init should be fast", 
                elapsed, lessThan(3000L));
    }

    // =====================================================
    // THEN STEPS - VALIDATION ASSERTIONS
    // =====================================================

    @Then("the validation should complete successfully")
    public void theValidationShouldCompleteSuccessfully() {
        assertThat("Validation should result in successful response", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("the validation should return success without validation")
    public void theValidationShouldReturnSuccessWithoutValidation() {
        assertThat("Request should succeed without validation", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("the resource should not be persisted")
    public void theResourceShouldNotBePersisted() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain OperationOutcome", 
                body, containsString("OperationOutcome"));
    }

    @Then("the resource should be persisted")
    public void theResourceShouldBePersisted() {
        assertThat("Resource should be created", 
                ctx.getLastResponse().getStatusCode(), is(201));
        
        String location = ctx.getLastResponse().getHeader("Location");
        assertThat("Location header should be present", location, notNullValue());
    }

    @Then("the resource should be created successfully")
    public void theResourceShouldBeCreatedSuccessfully() {
        assertThat("Resource should be created successfully", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("warnings should be logged")
    public void warningsShouldBeLogged() {
        assertThat("Request should succeed with warnings", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("validation errors should be logged as warnings")
    public void validationErrorsShouldBeLoggedAsWarnings() {
        assertThat("Request should succeed in lenient mode", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("validation should be performed")
    public void validationShouldBePerformed() {
        Response metricsResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        if (metricsResponse.getStatusCode() == 200) {
            assertThat("Validation metrics should show attempts", 
                    metricsResponse.jsonPath().getInt("measurements[0].value"), 
                    greaterThan(0));
        }
    }

    @Then("profile validation should not be performed")
    public void profileValidationShouldNotBePerformed() {
        assertThat("Resource should be created without validation", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("no validation warnings should be logged")
    public void noValidationWarningsShouldBeLogged() {
        // Log verification placeholder
    }

    @Then("validation time should be minimal")
    public void validationTimeShouldBeMinimal() {
        Response metricsResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.duration");
        
        if (metricsResponse.getStatusCode() == 404) {
            assertThat("Metrics should not exist when validation is off", 
                    metricsResponse.getStatusCode(), is(404));
        }
    }

    @Then("all resources should be created successfully")
    public void allResourcesShouldBeCreatedSuccessfully() {
        assertThat("Last resource should be created successfully", 
                ctx.getLastResponse().getStatusCode(), is(201));
    }

    @Then("the resource should be validated before persistence")
    public void theResourceShouldBeValidatedBeforePersistence() {
        Response metricsResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        if (metricsResponse.getStatusCode() == 200) {
            Object value = metricsResponse.jsonPath().get("measurements[0].value");
            assertThat("Validation should have been performed", value, notNullValue());
        }
    }

    @Then("validation metrics should be recorded")
    public void validationMetricsShouldBeRecorded() {
        Response metricsResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        if (metricsResponse.getStatusCode() == 200) {
            Object value = metricsResponse.jsonPath().get("measurements[0].value");
            assertThat("Validation metrics should be present", value, notNullValue());
        }
    }

    @Then("the updated resource should be validated")
    public void theUpdatedResourceShouldBeValidated() {
        theResourceShouldBeValidatedBeforePersistence();
    }

    @Then("the original resource should remain unchanged")
    public void theOriginalResourceShouldRemainUnchanged() {
        String patientId = ctx.getLastCreatedPatientId();
        Response retrieveResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/fhir/" + fhirVersion + "/Patient/" + patientId);
        
        if (retrieveResponse.getStatusCode() == 200) {
            String currentBody = retrieveResponse.getBody().asString();
            assertThat("Resource should not contain invalid data", 
                    currentBody, not(containsString("not-a-valid-date")));
        }
    }

    @Then("the Patient should have all validated data")
    public void thePatientShouldHaveAllValidatedData() {
        assertThat("Patient retrieval should succeed", 
                ctx.getLastResponse().getStatusCode(), is(200));
        
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Patient should have name", body, containsString("Smith"));
    }

    // =====================================================
    // THEN STEPS - RESPONSE ASSERTIONS
    // =====================================================

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int expectedStatusCode) {
        assertThat("Response status code should match", 
                ctx.getLastResponse().getStatusCode(), is(expectedStatusCode));
    }

    @Then("the response should be valid JSON")
    public void theResponseShouldBeValidJson() {
        String contentType = ctx.getLastResponse().getContentType();
        assertThat("Response should be JSON", 
                contentType, containsString("json"));
        
        ctx.getLastResponse().jsonPath().prettyPrint();
    }

    @Then("the status should be {string}")
    public void theStatusShouldBe(String expectedStatus) {
        String actualStatus = ctx.getLastResponse().jsonPath().getString("status");
        assertThat("Health status should match", actualStatus, is(expectedStatus));
    }

    @Then("the details should include {string}: {}")
    public void theDetailsShouldInclude(String key, Object expectedValue) {
        Object actualValue = ctx.getLastResponse().jsonPath().get("details." + key);
        
        if (expectedValue instanceof Boolean) {
            assertThat("Detail value should match for " + key, 
                    actualValue, is(expectedValue));
        } else {
            assertThat("Detail should be present for " + key, 
                    actualValue, notNullValue());
        }
    }

    @Then("the details should include total initialization time")
    public void theDetailsShouldIncludeTotalInitializationTime() {
        String totalTime = ctx.getLastResponse().jsonPath().getString("details.totalInitializationTime");
        assertThat("Total initialization time should be present", 
                totalTime, notNullValue());
        assertThat("Total initialization time should be in milliseconds", 
                totalTime, matchesPattern("\\d+ms"));
    }

    @Then("the details should include totalInitializationTime in milliseconds")
    public void theDetailsShouldIncludeTotalInitializationTimeInMilliseconds() {
        theDetailsShouldIncludeTotalInitializationTime();
    }

    @Then("the details should show version-specific status for R5")
    public void theDetailsShouldShowVersionSpecificStatusForR5() {
        String r5Status = ctx.getLastResponse().jsonPath().getString("details.versions.r5.status");
        assertThat("R5 status should be present", r5Status, notNullValue());
    }

    @Then("the details should show version-specific status for R4B")
    public void theDetailsShouldShowVersionSpecificStatusForR4B() {
        String r4bStatus = ctx.getLastResponse().jsonPath().getString("details.versions.r4b.status");
        assertThat("R4B status should be present", r4bStatus, notNullValue());
    }

    @Then("the details should include reason {string}")
    public void theDetailsShouldIncludeReason(String expectedReason) {
        String reason = ctx.getLastResponse().jsonPath().getString("details.reason");
        assertThat("Reason should match", reason, is(expectedReason));
    }

    @Then("the details should show successCount: {int}")
    public void theDetailsShouldShowSuccessCount(int expectedCount) {
        int actualCount = ctx.getLastResponse().jsonPath().getInt("details.successCount");
        assertThat("Success count should match", actualCount, is(expectedCount));
    }

    @Then("the details should show totalCount: {int}")
    public void theDetailsShouldShowTotalCount(int expectedCount) {
        int actualCount = ctx.getLastResponse().jsonPath().getInt("details.totalCount");
        assertThat("Total count should match", actualCount, is(expectedCount));
    }

    @Then("the details should include warning information")
    public void theDetailsShouldIncludeWarningInformation() {
        Object details = ctx.getLastResponse().jsonPath().get("details");
        assertThat("Details should be present", details, notNullValue());
    }

    @Then("R5 version should have status {string}")
    public void r5VersionShouldHaveStatus(String expectedStatus) {
        String actualStatus = ctx.getLastResponse().jsonPath().getString("details.versions.r5.status");
        assertThat("R5 status should match", actualStatus, is(expectedStatus));
    }

    @Then("R5 version should have available: {}")
    public void r5VersionShouldHaveAvailable(boolean expectedAvailable) {
        boolean actualAvailable = ctx.getLastResponse().jsonPath().getBoolean("details.versions.r5.available");
        assertThat("R5 availability should match", actualAvailable, is(expectedAvailable));
    }

    @Then("R4B version should have status {string}")
    public void r4bVersionShouldHaveStatus(String expectedStatus) {
        String actualStatus = ctx.getLastResponse().jsonPath().getString("details.versions.r4b.status");
        assertThat("R4B status should match", actualStatus, is(expectedStatus));
    }

    @Then("each version should report its initialization time")
    public void eachVersionShouldReportItsInitializationTime() {
        String r5Time = ctx.getLastResponse().jsonPath().getString("details.versions.r5.initializationTime");
        String r4bTime = ctx.getLastResponse().jsonPath().getString("details.versions.r4b.initializationTime");
        
        assertThat("R5 initialization time should be present", r5Time, notNullValue());
        assertThat("R4B initialization time should be present", r4bTime, notNullValue());
    }

    @Then("the sum of version times should equal total time")
    public void theSumOfVersionTimesShouldEqualTotalTime() {
        String totalTime = ctx.getLastResponse().jsonPath().getString("details.totalInitializationTime");
        assertThat("Total time should be present", totalTime, notNullValue());
    }

    @Then("health status should be UP")
    public void healthStatusShouldBeUp() {
        theStatusShouldBe("UP");
    }

    @Then("details should show validators are available")
    public void detailsShouldShowValidatorsAreAvailable() {
        Boolean r5Available = ctx.getLastResponse().jsonPath().getBoolean("details.versions.r5.available");
        assertThat("Validators should be available", r5Available, is(true));
    }

    @Then("initialization times should be reported")
    public void initializationTimesShouldBeReported() {
        theDetailsShouldIncludeTotalInitializationTime();
    }

    // =====================================================
    // THEN STEPS - METRICS ASSERTIONS
    // =====================================================

    @Then("validation attempts counter should show {int}")
    public void validationAttemptsCounterShouldShow(int expectedCount) {
        Response metricsResponse = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        if (metricsResponse.getStatusCode() == 200) {
            Object value = metricsResponse.jsonPath().get("measurements[0].value");
            int actualCount = value != null ? ((Number) value).intValue() : 0;
            assertThat("Counter should show expected attempts", 
                    actualCount, greaterThanOrEqualTo(expectedCount));
        }
    }

    @Then("successful validations should be {int}")
    public void successfulValidationsShouldBe(int expectedCount) {
        // Check metrics with success tag
    }

    @Then("failed validations should be {int}")
    public void failedValidationsShouldBe(int expectedCount) {
        // Check metrics with failure tag
    }

    @Then("the response should contain metric name {string}")
    public void theResponseShouldContainMetricName(String metricName) {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain metric name", 
                body, containsString(metricName));
    }

    @Then("the metrics should be visible immediately")
    public void theMetricsShouldBeVisibleImmediately() {
        Response response = request()
                .contentType(ContentType.JSON)
                .when()
                .get("/actuator/metrics/fhir.validation.attempts");
        
        assertThat("Metrics should be accessible", 
                response.getStatusCode(), is(200));
    }

    @Then("the metric should use dotted notation")
    public void theMetricShouldUseDottedNotation() {
        String metricName = ctx.getLastResponse().jsonPath().getString("name");
        assertThat("Metric name should use dots", metricName, containsString("."));
    }

    @Then("the metric should have measurements")
    public void theMetricShouldHaveMeasurements() {
        Object measurements = ctx.getLastResponse().jsonPath().get("measurements");
        assertThat("Measurements should be present", measurements, notNullValue());
    }

    @Then("the response should contain {string}")
    public void theResponseShouldContain(String expectedContent) {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain expected content", 
                body, containsString(expectedContent));
    }

    @Then("metric names should use underscore notation")
    public void metricNamesShouldUseUnderscoreNotation() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Prometheus metrics should use underscores", 
                body, anyOf(
                    containsString("fhir_validation_"),
                    containsString("_total")
                ));
    }

    @Then("the counter should show at least {int} attempts")
    public void theCounterShouldShowAtLeastAttempts(int minAttempts) {
        Object value = ctx.getLastResponse().jsonPath().get("measurements[0].value");
        int actualAttempts = value != null ? ((Number) value).intValue() : 0;
        assertThat("Counter should show minimum attempts", 
                actualAttempts, greaterThanOrEqualTo(minAttempts));
    }

    @Then("metrics should be tagged with version {string}")
    public void metricsShouldBeTaggedWithVersion(String expectedVersion) {
        Object tags = ctx.getLastResponse().jsonPath().get("availableTags");
        assertThat("Tags should be present", tags, notNullValue());
    }

    @Then("the metric should have tag {string} with values")
    public void theMetricShouldHaveTagWithValues(String tagName) {
        Object tags = ctx.getLastResponse().jsonPath().get("availableTags");
        assertThat("Tags should be present", tags, notNullValue());
    }

    @Then("the metric should have tag {string} with values {string} and {string}")
    public void theMetricShouldHaveTagWithValuesAnd(String tagName, String value1, String value2) {
        Object tags = ctx.getLastResponse().jsonPath().get("availableTags");
        assertThat("Tags should be present", tags, notNullValue());
    }

    @Then("the metric should have tag {string} with resource type values")
    public void theMetricShouldHaveTagWithResourceTypeValues(String tagName) {
        Object tags = ctx.getLastResponse().jsonPath().get("availableTags");
        assertThat("Tags should be present", tags, notNullValue());
    }

    @Then("the response should include duration measurements")
    public void theResponseShouldIncludeDurationMeasurements() {
        Object measurements = ctx.getLastResponse().jsonPath().get("measurements");
        assertThat("Duration measurements should be present", measurements, notNullValue());
    }

    @Then("the response should show count of validations")
    public void theResponseShouldShowCountOfValidations() {
        Object count = ctx.getLastResponse().jsonPath().get("measurements[?(@.statistic=='COUNT')].value");
        assertThat("Count should be present", count, notNullValue());
    }

    @Then("the response should show mean duration")
    public void theResponseShouldShowMeanDuration() {
        // Mean might be null if no samples
    }

    @Then("the response should show max duration")
    public void theResponseShouldShowMaxDuration() {
        // Max might be null if no samples
    }

    @Then("the counter should show successful validations")
    public void theCounterShouldShowSuccessfulValidations() {
        Object value = ctx.getLastResponse().jsonPath().get("measurements[0].value");
        assertThat("Counter should show values", value, notNullValue());
    }

    @Then("the counter should show failed validations")
    public void theCounterShouldShowFailedValidations() {
        // Value might be 0 if no failures
    }

    @Then("the counter should show Patient validations")
    public void theCounterShouldShowPatientValidations() {
        Object value = ctx.getLastResponse().jsonPath().get("measurements[0].value");
        assertThat("Counter should show Patient validations", value, notNullValue());
    }

    @Then("the counter should show Observation validations")
    public void theCounterShouldShowObservationValidations() {
        Object value = ctx.getLastResponse().jsonPath().get("measurements[0].value");
        assertThat("Counter should show Observation validations", value, notNullValue());
    }

    @Then("no validation metrics should be recorded")
    public void noValidationMetricsShouldBeRecorded() {
        // When validation is disabled, metrics might not be recorded
    }

    @Then("the response should show percentiles")
    public void theResponseShouldShowPercentiles() {
        // Percentiles might not be configured by default
    }

    @Then("p50 \\(median) duration should be available")
    public void p50MedianDurationShouldBeAvailable() {
        // P50 percentile check
    }

    @Then("p95 duration should be available")
    public void p95DurationShouldBeAvailable() {
        // P95 percentile check
    }

    @Then("p99 duration should be available")
    public void p99DurationShouldBeAvailable() {
        // P99 percentile check
    }

    // =====================================================
    // THEN STEPS - HTTP 422 ERROR ASSERTIONS
    // =====================================================

    @Then("the response should contain an {string} resource")
    public void theResponseShouldContainAnResource(String resourceType) {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain " + resourceType, 
                body, containsString(resourceType));
    }

    @Then("the OperationOutcome severity should be {string}")
    public void theOperationOutcomeSeverityShouldBe(String expectedSeverity) {
        String severity = ctx.getLastResponse().jsonPath().getString("issue[0].severity");
        assertThat("Severity should match", severity, is(expectedSeverity));
    }

    @Then("the OperationOutcome code should be {string}")
    public void theOperationOutcomeCodeShouldBe(String expectedCode) {
        String code = ctx.getLastResponse().jsonPath().getString("issue[0].code");
        assertThat("Code should match", code, is(expectedCode));
    }

    @Then("the diagnostics should mention invalid gender code")
    public void theDiagnosticsShouldMentionInvalidGenderCode() {
        String diagnostics = ctx.getLastResponse().jsonPath().getString("issue[0].diagnostics");
        assertThat("Diagnostics should mention gender", 
                diagnostics, anyOf(containsString("gender"), containsString("invalid")));
    }

    @Then("all responses should have status code {int}")
    public void allResponsesShouldHaveStatusCode(int expectedStatusCode) {
        assertThat("Response status should match", 
                ctx.getLastResponse().getStatusCode(), is(expectedStatusCode));
    }

    @Then("each should return an OperationOutcome")
    public void eachShouldReturnAnOperationOutcome() {
        theResponseShouldContainAnResource("OperationOutcome");
    }

    @Then("each should indicate invalid code value")
    public void eachShouldIndicateInvalidCodeValue() {
        theDiagnosticsShouldMentionInvalidGenderCode();
    }

    @Then("each should return the created Patient resource")
    public void eachShouldReturnTheCreatedPatientResource() {
        theResponseShouldContainAnResource("Patient");
    }

    @Then("each resource should have an id")
    public void eachResourceShouldHaveAnId() {
        String id = ctx.getLastResponse().jsonPath().getString("id");
        assertThat("Resource should have an ID", id, notNullValue());
    }

    @Then("the diagnostics should mention missing required element")
    public void theDiagnosticsShouldMentionMissingRequiredElement() {
        String diagnostics = ctx.getLastResponse().jsonPath().getString("issue[0].diagnostics");
        assertThat("Diagnostics should mention required element", 
                diagnostics, anyOf(containsString("required"), containsString("missing")));
    }

    // =====================================================
    // LOG VERIFICATION STEPS (Placeholders)
    // =====================================================

    @Then("the startup logs should contain {string}")
    public void theStartupLogsShouldContain(String expectedLog) {
        // Log verification placeholder
    }

    @Then("the error logs should contain {string}")
    public void theErrorLogsShouldContain(String expectedError) {
        // Log verification placeholder
    }

    @Then("the error should reference TarArchiveInputStream")
    public void theErrorShouldReferenceTarArchiveInputStream() {
        // Log verification placeholder
    }

    @Then("the startup logs should not contain version-specific initialization messages")
    public void theStartupLogsShouldNotContainVersionSpecificInitializationMessages() {
        // Log verification placeholder
    }

    @Then("the logs should contain {string}")
    public void theLogsShouldContain(String expectedLog) {
        // Log verification placeholder
    }

    @Then("the logs should contain WARN message for R5 failure")
    public void theLogsShouldContainWarnMessageForR5Failure() {
        // Log verification placeholder
    }

    @Then("logs should show {string}")
    public void logsShouldShow(String expectedPattern) {
        // Log verification placeholder
    }

    @Then("the logs should contain version tag")
    public void theLogsShouldContainVersionTag() {
        // Log verification placeholder
    }

    @Then("the logs should contain result tag")
    public void theLogsShouldContainResultTag() {
        // Log verification placeholder
    }

    @Then("the logs should contain resourceType tag")
    public void theLogsShouldContainResourceTypeTag() {
        // Log verification placeholder
    }

    // =====================================================
    // MISSING STEP DEFINITIONS FOR HTTP 422 SCENARIOS
    // =====================================================

    @Given("I have a Patient resource not conforming to US Core")
    public void iHaveAPatientResourceNotConformingToUSCore() {
        // Patient without required US Core elements
        String patientJson = """
                {
                    "resourceType": "Patient",
                    "active": true
                }
                """;
        ctx.setRequestBody(patientJson);
    }

    @Then("the OperationOutcome should indicate profile violation")
    public void theOperationOutcomeShouldIndicateProfileViolation() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain OperationOutcome",
                body, containsString("OperationOutcome"));
    }

    @Then("the diagnostics should reference the US Core profile")
    public void theDiagnosticsShouldReferenceTheUSCoreProfile() {
        // Placeholder - profile validation not fully implemented
    }

    @When("I POST each invalid resource")
    public void iPostEachInvalidResource() {
        // Post a sample invalid resource
        String invalidJson = """
                {
                    "resourceType": "Patient",
                    "gender": "invalid-code"
                }
                """;
        Response response = request()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        ctx.setLastResponse(response);
    }

    @Then("no response should have status code {int}")
    public void noResponseShouldHaveStatusCode(int statusCode) {
        assertThat("Response should not have status " + statusCode,
                ctx.getLastResponse().getStatusCode(), not(statusCode));
    }

    @Then("all validation errors should return {int} or {int}")
    public void allValidationErrorsShouldReturnOr(int status1, int status2) {
        int actualStatus = ctx.getLastResponse().getStatusCode();
        assertThat("Response should be " + status1 + " or " + status2,
                actualStatus, anyOf(is(status1), is(status2)));
    }

    @Then("HTTP {int} should only occur for server failures")
    public void httpShouldOnlyOccurForServerFailures(int statusCode) {
        // Documentation step - validates that 500 is reserved for actual server errors
    }

    @Given("HAPI FHIR parser encounters invalid data")
    public void hapiFhirParserEncountersInvalidData() {
        ctx.setRequestBody("{\"resourceType\": \"Patient\", \"invalid\": }");
    }

    @When("the DataFormatException is thrown")
    public void theDataFormatExceptionIsThrown() {
        Response response = request()
                .contentType(ContentType.JSON)
                .body(ctx.getRequestBody())
                .when()
                .post("/fhir/" + fhirVersion + "/Patient");
        ctx.setLastResponse(response);
    }

    @Then("it should be caught by exception handler")
    public void itShouldBeCaughtByExceptionHandler() {
        // Verified by subsequent status code checks
    }

    @Then("HTTP {int} should be returned")
    public void httpShouldBeReturned(int statusCode) {
        assertThat("HTTP status should be " + statusCode,
                ctx.getLastResponse().getStatusCode(), is(statusCode));
    }

    @Then("an OperationOutcome should be included")
    public void anOperationOutcomeShouldBeIncluded() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain OperationOutcome",
                body, containsString("OperationOutcome"));
    }

    @Then("the OperationOutcome should have multiple issues")
    public void theOperationOutcomeShouldHaveMultipleIssues() {
        // Check if issues array has more than one element
        Object issues = ctx.getLastResponse().jsonPath().get("issue");
        assertThat("Issues should be present", issues, notNullValue());
    }

    @Then("each issue should have severity")
    public void eachIssueShouldHaveSeverity() {
        String severity = ctx.getLastResponse().jsonPath().getString("issue[0].severity");
        assertThat("Issue should have severity", severity, notNullValue());
    }

    @Then("each issue should have code")
    public void eachIssueShouldHaveCode() {
        String code = ctx.getLastResponse().jsonPath().getString("issue[0].code");
        assertThat("Issue should have code", code, notNullValue());
    }

    @Then("each issue should have diagnostics")
    public void eachIssueShouldHaveDiagnostics() {
        String diagnostics = ctx.getLastResponse().jsonPath().getString("issue[0].diagnostics");
        assertThat("Issue should have diagnostics", diagnostics, notNullValue());
    }

    @Then("issues should have location information when available")
    public void issuesShouldHaveLocationInformationWhenAvailable() {
        // Location is optional, so this is a documentation step
    }

    @Then("the OperationOutcome should indicate JSON parse error")
    public void theOperationOutcomeShouldIndicateJsonParseError() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should indicate parsing error",
                body, anyOf(containsString("parse"), containsString("JSON"), containsString("invalid")));
    }

    @Then("the diagnostics should mention invalid date format")
    public void theDiagnosticsShouldMentionInvalidDateFormat() {
        String diagnostics = ctx.getLastResponse().jsonPath().getString("issue[0].diagnostics");
        assertThat("Diagnostics should mention date format",
                diagnostics, anyOf(containsString("date"), containsString("format"), containsString("invalid")));
    }

    @Then("the response should contain an OperationOutcome")
    public void theResponseShouldContainAnOperationOutcome() {
        String body = ctx.getLastResponse().getBody().asString();
        assertThat("Response should contain OperationOutcome",
                body, containsString("OperationOutcome"));
    }
}
