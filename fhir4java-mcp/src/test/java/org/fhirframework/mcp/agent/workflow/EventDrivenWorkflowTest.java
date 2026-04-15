package org.fhirframework.mcp.agent.workflow;

import org.fhirframework.mcp.agent.AgentEventConsumer;
import org.fhirframework.mcp.tool.FhirMutateTool;
import org.fhirframework.mcp.tool.FhirQueryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventDrivenWorkflow.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventDrivenWorkflow Tests")
class EventDrivenWorkflowTest {

    @Mock
    private FhirQueryTool queryTool;

    @Mock
    private FhirMutateTool mutateTool;

    @Mock
    private AgentEventConsumer eventConsumer;

    private EventDrivenWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new EventDrivenWorkflow(queryTool, mutateTool, eventConsumer);
    }

    @Test
    @DisplayName("should create workflow with required dependencies")
    void createWorkflow() {
        assertNotNull(workflow);
        assertEquals(0, workflow.getActiveWorkflowCount());
    }

    @Nested
    @DisplayName("Critical Value Analysis Tests")
    class CriticalValueTests {

        @Test
        @DisplayName("critical value result should indicate critical status")
        void criticalValueResultIndicatesCritical() {
            EventDrivenWorkflow.CriticalValueResult result =
                    EventDrivenWorkflow.CriticalValueResult.critical(
                            "Glucose",
                            new BigDecimal("450"),
                            "mg/dL",
                            "Critically high glucose: 450 mg/dL"
                    );

            assertTrue(result.isCritical());
            assertEquals("Glucose", result.getTestName());
            assertEquals(new BigDecimal("450"), result.getValue());
            assertEquals("mg/dL", result.getUnit());
            assertNotNull(result.getMessage());
        }

        @Test
        @DisplayName("not critical result should indicate non-critical status")
        void notCriticalResultIndicatesNotCritical() {
            EventDrivenWorkflow.CriticalValueResult result =
                    EventDrivenWorkflow.CriticalValueResult.notCritical();

            assertFalse(result.isCritical());
            assertNull(result.getTestName());
            assertNull(result.getValue());
            assertNull(result.getMessage());
        }
    }

    @Nested
    @DisplayName("Patient Context Tests")
    class PatientContextTests {

        @Test
        @DisplayName("should create patient context with reference")
        void createPatientContext() {
            EventDrivenWorkflow.PatientContext context =
                    new EventDrivenWorkflow.PatientContext("Patient/123");

            assertEquals("Patient/123", context.getPatientRef());
            assertTrue(context.getLatestObservations().isEmpty());
        }

        @Test
        @DisplayName("should add and retrieve observations")
        void addAndRetrieveObservations() {
            EventDrivenWorkflow.PatientContext context =
                    new EventDrivenWorkflow.PatientContext("Patient/123");

            context.addObservation("2345-7", new BigDecimal("120"));
            context.addObservation("2823-3", new BigDecimal("4.5"));

            assertEquals(new BigDecimal("120"), context.getLatestValue("2345-7"));
            assertEquals(new BigDecimal("4.5"), context.getLatestValue("2823-3"));
        }

        @Test
        @DisplayName("should update observation value")
        void updateObservationValue() {
            EventDrivenWorkflow.PatientContext context =
                    new EventDrivenWorkflow.PatientContext("Patient/123");

            context.addObservation("2345-7", new BigDecimal("100"));
            context.addObservation("2345-7", new BigDecimal("150"));

            assertEquals(new BigDecimal("150"), context.getLatestValue("2345-7"));
        }

        @Test
        @DisplayName("should return null for unknown observation")
        void returnNullForUnknownObservation() {
            EventDrivenWorkflow.PatientContext context =
                    new EventDrivenWorkflow.PatientContext("Patient/123");

            assertNull(context.getLatestValue("unknown-code"));
        }
    }

    @Nested
    @DisplayName("Workflow Lifecycle Tests")
    class WorkflowLifecycleTests {

        @Test
        @DisplayName("should track active workflow count")
        void trackActiveWorkflowCount() {
            assertEquals(0, workflow.getActiveWorkflowCount());
        }

        @Test
        @DisplayName("should stop all workflows")
        void stopAllWorkflows() {
            workflow.stopAllWorkflows();
            assertEquals(0, workflow.getActiveWorkflowCount());
        }

        @Test
        @DisplayName("should stop specific workflow")
        void stopSpecificWorkflow() {
            workflow.stopWorkflow("non-existent");
            assertEquals(0, workflow.getActiveWorkflowCount());
        }

        @Test
        @DisplayName("should return null for unknown patient context")
        void returnNullForUnknownPatientContext() {
            assertNull(workflow.getPatientContext("Patient/unknown"));
        }
    }
}
