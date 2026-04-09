package org.fhirframework.mcp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.persistence.entity.McpAuditLogEntity;
import org.fhirframework.persistence.repository.McpAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link McpAuditService}.
 * <p>
 * Tests the MCP audit logging functionality including:
 * <ul>
 *   <li>Logging successful tool invocations</li>
 *   <li>Logging failed tool invocations with error messages</li>
 *   <li>Recording latency</li>
 *   <li>Serializing request params as JSON</li>
 *   <li>Retrieving audit logs for agents</li>
 * </ul>
 * </p>
 */
class McpAuditServiceTest {

    @Mock
    private McpAuditLogRepository repository;

    private McpAuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        auditService = new McpAuditService(repository, objectMapper);
    }

    @Nested
    @DisplayName("logToolInvocation")
    class LogToolInvocationTests {

        @Test
        @DisplayName("should save audit entry for successful invocation")
        void shouldSaveAuditEntryForSuccessfulInvocation() {
            // Given
            String tenantId = "tenant-123";
            String agentId = "agent-456";
            String toolName = "fhir_query";
            String action = "search";
            String resourceType = "Patient";
            String resourceId = null;
            Map<String, Object> requestParams = Map.of("resourceType", "Patient", "searchParams", "_count=10");
            String responseSummary = "Found 5 patients";
            long latencyMs = 150;

            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> {
                McpAuditLogEntity entity = invocation.getArgument(0);
                entity.setId(1L);
                return entity;
            });

            // When
            auditService.logToolInvocation(
                    tenantId,
                    agentId,
                    toolName,
                    action,
                    resourceType,
                    resourceId,
                    requestParams,
                    true,
                    responseSummary,
                    null,
                    latencyMs
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertEquals(tenantId, saved.getTenantId());
            assertEquals(agentId, saved.getAgentId());
            assertEquals(toolName, saved.getToolName());
            assertEquals(action, saved.getAction());
            assertEquals(resourceType, saved.getResourceType());
            assertNull(saved.getResourceId());
            assertTrue(saved.isSuccess());
            assertEquals(responseSummary, saved.getResponseSummary());
            assertNull(saved.getErrorMessage());
            assertEquals(150, saved.getLatencyMs());
            assertNotNull(saved.getTimestamp());
        }

        @Test
        @DisplayName("should save audit entry with success=true for successful invocation")
        void shouldSetSuccessTrueForSuccessfulInvocation() {
            // Given
            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_discover",
                    "read",
                    "Patient",
                    "123",
                    Map.of(),
                    true,  // success = true
                    "Success",
                    null,
                    50
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertTrue(saved.isSuccess());
        }

        @Test
        @DisplayName("should save audit entry with success=false and error message for failed invocation")
        void shouldSetSuccessFalseWithErrorMessageForFailedInvocation() {
            // Given
            String errorMessage = "Resource not found: Patient/999";

            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_query",
                    "read",
                    "Patient",
                    "999",
                    Map.of("resourceType", "Patient", "resourceId", "999"),
                    false,  // success = false
                    null,
                    errorMessage,
                    25
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertFalse(saved.isSuccess());
            assertEquals(errorMessage, saved.getErrorMessage());
        }

        @Test
        @DisplayName("should record latency correctly")
        void shouldRecordLatencyCorrectly() {
            // Given
            long expectedLatency = 1234L;

            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "tenant-1",
                    "agent-1",
                    "fhir_mutate",
                    "create",
                    "Observation",
                    null,
                    Map.of(),
                    true,
                    "Created",
                    null,
                    expectedLatency
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertEquals(expectedLatency, saved.getLatencyMs().longValue());
        }

        @Test
        @DisplayName("should save request params as JSON")
        void shouldSaveRequestParamsAsJson() throws JsonProcessingException {
            // Given
            Map<String, Object> requestParams = Map.of(
                    "resourceType", "Patient",
                    "searchParams", "_id=123&family=Smith",
                    "maxResults", 100
            );

            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_query",
                    "search",
                    "Patient",
                    null,
                    requestParams,
                    true,
                    "Found results",
                    null,
                    100
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertNotNull(saved.getRequestParams());

            // Verify it's valid JSON by parsing it
            Map<String, Object> parsedParams = objectMapper.readValue(saved.getRequestParams(), Map.class);
            assertEquals("Patient", parsedParams.get("resourceType"));
            assertEquals("_id=123&family=Smith", parsedParams.get("searchParams"));
            assertEquals(100, parsedParams.get("maxResults"));
        }

        @Test
        @DisplayName("should handle null request params gracefully")
        void shouldHandleNullRequestParams() {
            // Given
            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_discover",
                    "list",
                    null,
                    null,
                    null,  // null request params
                    true,
                    "Listed resources",
                    null,
                    50
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertNull(saved.getRequestParams());
        }

        @Test
        @DisplayName("should handle empty request params")
        void shouldHandleEmptyRequestParams() {
            // Given
            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_discover",
                    "list",
                    null,
                    null,
                    Map.of(),  // empty map
                    true,
                    "Listed resources",
                    null,
                    50
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertEquals("{}", saved.getRequestParams());
        }

        @Test
        @DisplayName("should set timestamp to current time")
        void shouldSetTimestampToCurrentTime() {
            // Given
            Instant before = Instant.now();

            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    "default",
                    "agent-1",
                    "fhir_query",
                    "search",
                    "Patient",
                    null,
                    Map.of(),
                    true,
                    "Success",
                    null,
                    100
            );

            Instant after = Instant.now();

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertNotNull(saved.getTimestamp());
            assertTrue(!saved.getTimestamp().isBefore(before));
            assertTrue(!saved.getTimestamp().isAfter(after));
        }

        @Test
        @DisplayName("should use default tenant ID when tenantId is null")
        void shouldUseDefaultTenantIdWhenNull() {
            // Given
            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            auditService.logToolInvocation(
                    null,  // null tenant ID
                    "agent-1",
                    "fhir_query",
                    "search",
                    "Patient",
                    null,
                    Map.of(),
                    true,
                    "Success",
                    null,
                    100
            );

            // Then
            ArgumentCaptor<McpAuditLogEntity> captor = ArgumentCaptor.forClass(McpAuditLogEntity.class);
            verify(repository).save(captor.capture());

            McpAuditLogEntity saved = captor.getValue();
            assertEquals("default", saved.getTenantId());
        }

        @Test
        @DisplayName("should handle all MCP tool names")
        void shouldHandleAllMcpToolNames() {
            // Given
            when(repository.save(any(McpAuditLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String[] toolNames = {"fhir_discover", "fhir_query", "fhir_mutate"};

            for (String toolName : toolNames) {
                // When
                auditService.logToolInvocation(
                        "default",
                        "agent-1",
                        toolName,
                        "action",
                        "Patient",
                        null,
                        Map.of(),
                        true,
                        "Success",
                        null,
                        100
                );
            }

            // Then
            verify(repository, times(3)).save(any(McpAuditLogEntity.class));
        }
    }

    @Nested
    @DisplayName("getLogsForAgent")
    class GetLogsForAgentTests {

        @Test
        @DisplayName("should retrieve logs for a specific agent")
        void shouldRetrieveLogsForAgent() {
            // Given
            String agentId = "agent-123";
            int limit = 10;

            McpAuditLogEntity log1 = new McpAuditLogEntity();
            log1.setId(1L);
            log1.setAgentId(agentId);
            log1.setToolName("fhir_query");
            log1.setTimestamp(Instant.now());

            McpAuditLogEntity log2 = new McpAuditLogEntity();
            log2.setId(2L);
            log2.setAgentId(agentId);
            log2.setToolName("fhir_discover");
            log2.setTimestamp(Instant.now().minusSeconds(60));

            when(repository.findByAgentIdOrderByTimestampDesc(eq(agentId), any(PageRequest.class)))
                    .thenReturn(List.of(log1, log2));

            // When
            List<McpAuditLogEntity> logs = auditService.getLogsForAgent(agentId, limit);

            // Then
            assertEquals(2, logs.size());
            assertEquals(log1, logs.get(0));
            assertEquals(log2, logs.get(1));

            ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
            verify(repository).findByAgentIdOrderByTimestampDesc(eq(agentId), pageRequestCaptor.capture());

            PageRequest captured = pageRequestCaptor.getValue();
            assertEquals(0, captured.getPageNumber());
            assertEquals(limit, captured.getPageSize());
        }

        @Test
        @DisplayName("should return empty list when no logs exist")
        void shouldReturnEmptyListWhenNoLogs() {
            // Given
            String agentId = "agent-no-logs";

            when(repository.findByAgentIdOrderByTimestampDesc(eq(agentId), any(PageRequest.class)))
                    .thenReturn(List.of());

            // When
            List<McpAuditLogEntity> logs = auditService.getLogsForAgent(agentId, 10);

            // Then
            assertTrue(logs.isEmpty());
        }
    }

    @Nested
    @DisplayName("getLogsForTenant")
    class GetLogsForTenantTests {

        @Test
        @DisplayName("should retrieve logs for a specific tenant")
        void shouldRetrieveLogsForTenant() {
            // Given
            String tenantId = "tenant-456";
            int limit = 20;

            McpAuditLogEntity log1 = new McpAuditLogEntity();
            log1.setId(1L);
            log1.setTenantId(tenantId);
            log1.setToolName("fhir_mutate");
            log1.setTimestamp(Instant.now());

            when(repository.findByTenantIdOrderByTimestampDesc(eq(tenantId), any(PageRequest.class)))
                    .thenReturn(List.of(log1));

            // When
            List<McpAuditLogEntity> logs = auditService.getLogsForTenant(tenantId, limit);

            // Then
            assertEquals(1, logs.size());
            assertEquals(log1, logs.get(0));
        }
    }
}
