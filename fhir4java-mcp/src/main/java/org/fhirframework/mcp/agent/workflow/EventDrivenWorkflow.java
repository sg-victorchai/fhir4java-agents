package org.fhirframework.mcp.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.mcp.agent.AgentEventConsumer;
import org.fhirframework.mcp.dto.ToolCallRequest;
import org.fhirframework.mcp.dto.ToolCallResponse;
import org.fhirframework.mcp.tool.FhirMutateTool;
import org.fhirframework.mcp.tool.FhirQueryTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Event-Driven Workflow for AI Agents.
 * <p>
 * Demonstrates a complete event-driven AI agent workflow that combines
 * MCP tools (fhir_query, fhir_mutate) with real-time SSE events.
 * </p>
 *
 * <h3>Clinical Decision Support Use Case:</h3>
 * <ol>
 *   <li>Agent subscribes to lab result events (Observations)</li>
 *   <li>When a result arrives, agent queries the full Observation</li>
 *   <li>Agent analyzes the result for critical values</li>
 *   <li>If critical, agent creates a Flag alert using fhir_mutate</li>
 *   <li>Agent updates its internal patient context</li>
 * </ol>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * EventDrivenWorkflow workflow = new EventDrivenWorkflow(
 *     queryTool, mutateTool, eventConsumer);
 *
 * // Start monitoring for a specific patient
 * workflow.startClinicalDecisionSupport("Patient/123");
 *
 * // Or monitor all patients
 * workflow.startClinicalDecisionSupport(null);
 *
 * // Later, stop monitoring
 * workflow.stopAllWorkflows();
 * }</pre>
 */
public class EventDrivenWorkflow {

    private static final Logger log = LoggerFactory.getLogger(EventDrivenWorkflow.class);

    private final FhirQueryTool queryTool;
    private final FhirMutateTool mutateTool;
    private final AgentEventConsumer eventConsumer;
    private final ObjectMapper objectMapper;

    private final Map<String, Disposable> activeWorkflows = new ConcurrentHashMap<>();
    private final Map<String, PatientContext> patientContexts = new ConcurrentHashMap<>();

    // Critical value thresholds (example values)
    private static final BigDecimal GLUCOSE_HIGH_CRITICAL = new BigDecimal("400");
    private static final BigDecimal GLUCOSE_LOW_CRITICAL = new BigDecimal("50");
    private static final BigDecimal POTASSIUM_HIGH_CRITICAL = new BigDecimal("6.5");
    private static final BigDecimal POTASSIUM_LOW_CRITICAL = new BigDecimal("2.5");

    /**
     * Creates a new EventDrivenWorkflow.
     *
     * @param queryTool     the fhir_query tool for reading resources
     * @param mutateTool    the fhir_mutate tool for creating/updating resources
     * @param eventConsumer the event consumer for SSE subscriptions
     */
    public EventDrivenWorkflow(FhirQueryTool queryTool, FhirMutateTool mutateTool,
                               AgentEventConsumer eventConsumer) {
        this.queryTool = queryTool;
        this.mutateTool = mutateTool;
        this.eventConsumer = eventConsumer;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Starts clinical decision support monitoring.
     * <p>
     * Monitors Observation events and generates alerts for critical values.
     * </p>
     *
     * @param patientId optional patient ID to filter events (null for all patients)
     */
    public void startClinicalDecisionSupport(String patientId) {
        String workflowId = "cds-" + (patientId != null ? patientId : "all");

        log.info("Starting clinical decision support workflow: {}", workflowId);

        // Create a filter for events
        Predicate<ResourceChangeEvent> eventFilter = event -> {
            // Only process Observation create/update events
            if (!"Observation".equals(event.resourceType())) {
                return false;
            }
            if (!"create".equals(event.action()) && !"update".equals(event.action())) {
                return false;
            }
            // If patientId specified, we'll filter after fetching the Observation
            return true;
        };

        // Subscribe to Observation events
        Disposable subscription = eventConsumer.subscribeToEvents(
                        List.of("Observation"),
                        List.of("create", "update")
                )
                .filter(eventFilter)
                .subscribe(
                        event -> processLabResult(event, patientId),
                        error -> log.error("CDS workflow error", error),
                        () -> log.info("CDS workflow completed: {}", workflowId)
                );

        activeWorkflows.put(workflowId, subscription);
    }

    /**
     * Processes a lab result event.
     * <p>
     * This is the main workflow logic:
     * 1. Query the Observation
     * 2. Check if it's for the monitored patient
     * 3. Analyze for critical values
     * 4. Create alerts if needed
     * 5. Update patient context
     * </p>
     */
    private void processLabResult(ResourceChangeEvent event, String targetPatientId) {
        log.debug("Processing lab result event: {} {}", event.resourceType(), event.resourceId());

        try {
            // Step 1: Query the Observation
            ToolCallResponse obsResponse = queryTool.execute(
                    new ToolCallRequest("fhir_query", Map.of(
                            "action", "read",
                            "resourceType", "Observation",
                            "id", event.resourceId()
                    ))
            );

            if (obsResponse.isError()) {
                log.warn("Failed to read Observation: {}", obsResponse.getFirstTextContent());
                return;
            }

            // Step 2: Parse the Observation
            String observationJson = obsResponse.getFirstTextContent();
            JsonNode observation = objectMapper.readTree(observationJson);

            // Step 3: Check if it's for the target patient (if specified)
            String patientRef = extractPatientReference(observation);
            if (targetPatientId != null && !targetPatientId.equals(patientRef)) {
                log.debug("Observation not for target patient, skipping");
                return;
            }

            // Step 4: Check for critical values
            CriticalValueResult criticalResult = checkForCriticalValue(observation);

            if (criticalResult.isCritical()) {
                log.warn("CRITICAL VALUE DETECTED: {} - {}", criticalResult.getTestName(),
                        criticalResult.getMessage());

                // Step 5: Create a Flag alert
                createCriticalAlert(patientRef, observation, criticalResult);
            }

            // Step 6: Update patient context
            updatePatientContext(patientRef, observation);

        } catch (Exception e) {
            log.error("Failed to process lab result", e);
        }
    }

    /**
     * Extracts the patient reference from an Observation.
     */
    private String extractPatientReference(JsonNode observation) {
        JsonNode subject = observation.path("subject");
        if (subject.has("reference")) {
            return subject.get("reference").asText();
        }
        return null;
    }

    /**
     * Checks if an Observation contains a critical value.
     */
    private CriticalValueResult checkForCriticalValue(JsonNode observation) {
        // Extract the code and value
        String testCode = extractTestCode(observation);
        BigDecimal value = extractNumericValue(observation);
        String unit = extractUnit(observation);

        if (value == null) {
            return CriticalValueResult.notCritical();
        }

        // Check against critical thresholds
        // This is a simplified example - real implementations would use proper
        // reference ranges from the lab or LOINC codes

        if ("2345-7".equals(testCode) || containsText(observation, "Glucose")) {
            if (value.compareTo(GLUCOSE_HIGH_CRITICAL) > 0) {
                return CriticalValueResult.critical("Glucose", value, unit,
                        "Critically high glucose: " + value + " " + unit);
            }
            if (value.compareTo(GLUCOSE_LOW_CRITICAL) < 0) {
                return CriticalValueResult.critical("Glucose", value, unit,
                        "Critically low glucose: " + value + " " + unit);
            }
        }

        if ("2823-3".equals(testCode) || containsText(observation, "Potassium")) {
            if (value.compareTo(POTASSIUM_HIGH_CRITICAL) > 0) {
                return CriticalValueResult.critical("Potassium", value, unit,
                        "Critically high potassium: " + value + " " + unit);
            }
            if (value.compareTo(POTASSIUM_LOW_CRITICAL) < 0) {
                return CriticalValueResult.critical("Potassium", value, unit,
                        "Critically low potassium: " + value + " " + unit);
            }
        }

        return CriticalValueResult.notCritical();
    }

    /**
     * Extracts the test code (LOINC) from an Observation.
     */
    private String extractTestCode(JsonNode observation) {
        JsonNode code = observation.path("code");
        if (code.has("coding")) {
            for (JsonNode coding : code.get("coding")) {
                if (coding.has("code")) {
                    return coding.get("code").asText();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the numeric value from an Observation.
     */
    private BigDecimal extractNumericValue(JsonNode observation) {
        JsonNode valueQuantity = observation.path("valueQuantity");
        if (valueQuantity.has("value")) {
            return new BigDecimal(valueQuantity.get("value").asText());
        }
        return null;
    }

    /**
     * Extracts the unit from an Observation.
     */
    private String extractUnit(JsonNode observation) {
        JsonNode valueQuantity = observation.path("valueQuantity");
        if (valueQuantity.has("unit")) {
            return valueQuantity.get("unit").asText();
        }
        return "";
    }

    /**
     * Checks if the Observation contains specific text in its display or code.
     */
    private boolean containsText(JsonNode observation, String text) {
        String json = observation.toString().toLowerCase();
        return json.contains(text.toLowerCase());
    }

    /**
     * Creates a Flag resource for a critical value alert.
     */
    private void createCriticalAlert(String patientRef, JsonNode observation,
                                     CriticalValueResult criticalResult) {
        log.info("Creating critical value alert for patient: {}", patientRef);

        // Build the Flag resource
        Map<String, Object> flag = new LinkedHashMap<>();
        flag.put("resourceType", "Flag");
        flag.put("status", "active");
        flag.put("category", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://terminology.hl7.org/CodeSystem/flag-category",
                        "code", "clinical",
                        "display", "Clinical"
                ))
        )));
        flag.put("code", Map.of(
                "coding", List.of(Map.of(
                        "system", "http://fhir4java.org/CodeSystem/alert-type",
                        "code", "critical-value",
                        "display", "Critical Value Alert"
                )),
                "text", criticalResult.getMessage()
        ));
        flag.put("subject", Map.of("reference", patientRef));
        flag.put("period", Map.of("start", java.time.Instant.now().toString()));

        // First, validate with dry-run
        ToolCallResponse dryRunResponse = mutateTool.execute(
                new ToolCallRequest("fhir_mutate", Map.of(
                        "action", "create",
                        "resourceType", "Flag",
                        "dryRun", true,
                        "body", flag
                ))
        );

        if (dryRunResponse.isError()) {
            log.error("Dry-run validation failed for Flag: {}", dryRunResponse.getFirstTextContent());
            return;
        }

        // Create the actual Flag
        ToolCallResponse createResponse = mutateTool.execute(
                new ToolCallRequest("fhir_mutate", Map.of(
                        "action", "create",
                        "resourceType", "Flag",
                        "body", flag
                ))
        );

        if (createResponse.isError()) {
            log.error("Failed to create Flag: {}", createResponse.getFirstTextContent());
        } else {
            log.info("Critical value alert created successfully");
        }
    }

    /**
     * Updates the patient context with new observation data.
     */
    private void updatePatientContext(String patientRef, JsonNode observation) {
        PatientContext context = patientContexts.computeIfAbsent(
                patientRef, k -> new PatientContext(patientRef));

        String testCode = extractTestCode(observation);
        BigDecimal value = extractNumericValue(observation);

        if (testCode != null && value != null) {
            context.addObservation(testCode, value);
            log.debug("Updated patient context: {} - {} = {}",
                    patientRef, testCode, value);
        }
    }

    /**
     * Stops a specific workflow by ID.
     *
     * @param workflowId the workflow ID to stop
     */
    public void stopWorkflow(String workflowId) {
        Disposable subscription = activeWorkflows.remove(workflowId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Stopped workflow: {}", workflowId);
        }
    }

    /**
     * Stops all active workflows.
     */
    public void stopAllWorkflows() {
        activeWorkflows.forEach((id, subscription) -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        activeWorkflows.clear();
        log.info("Stopped all workflows");
    }

    /**
     * Gets the patient context for a specific patient.
     *
     * @param patientRef the patient reference
     * @return the patient context, or null if not found
     */
    public PatientContext getPatientContext(String patientRef) {
        return patientContexts.get(patientRef);
    }

    /**
     * Gets the number of active workflows.
     *
     * @return count of active workflows
     */
    public int getActiveWorkflowCount() {
        return (int) activeWorkflows.values().stream()
                .filter(d -> !d.isDisposed())
                .count();
    }

    /**
     * Result of critical value analysis.
     */
    public static class CriticalValueResult {
        private final boolean critical;
        private final String testName;
        private final BigDecimal value;
        private final String unit;
        private final String message;

        private CriticalValueResult(boolean critical, String testName, BigDecimal value,
                                    String unit, String message) {
            this.critical = critical;
            this.testName = testName;
            this.value = value;
            this.unit = unit;
            this.message = message;
        }

        public static CriticalValueResult critical(String testName, BigDecimal value,
                                                   String unit, String message) {
            return new CriticalValueResult(true, testName, value, unit, message);
        }

        public static CriticalValueResult notCritical() {
            return new CriticalValueResult(false, null, null, null, null);
        }

        public boolean isCritical() { return critical; }
        public String getTestName() { return testName; }
        public BigDecimal getValue() { return value; }
        public String getUnit() { return unit; }
        public String getMessage() { return message; }
    }

    /**
     * Patient context maintained by the agent.
     */
    public static class PatientContext {
        private final String patientRef;
        private final Map<String, BigDecimal> latestObservations = new ConcurrentHashMap<>();

        public PatientContext(String patientRef) {
            this.patientRef = patientRef;
        }

        public void addObservation(String code, BigDecimal value) {
            latestObservations.put(code, value);
        }

        public BigDecimal getLatestValue(String code) {
            return latestObservations.get(code);
        }

        public String getPatientRef() { return patientRef; }
        public Map<String, BigDecimal> getLatestObservations() { return latestObservations; }
    }
}
