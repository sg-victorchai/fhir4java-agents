package org.fhirframework.mcp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.persistence.entity.McpAuditLogEntity;
import org.fhirframework.persistence.repository.McpAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for MCP (Model Context Protocol) audit logging.
 * <p>
 * Records all MCP tool invocations for auditing, monitoring, and analytics purposes.
 * Each invocation is logged with details about the tool used, action performed,
 * target resource, request parameters, success/failure status, and timing.
 * </p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Log a successful tool invocation
 * auditService.logToolInvocation(
 *     "tenant-123",
 *     "agent-456",
 *     "fhir_query",
 *     "search",
 *     "Patient",
 *     null,
 *     Map.of("family", "Smith"),
 *     true,
 *     "Found 10 patients",
 *     null,
 *     150
 * );
 *
 * // Log a failed tool invocation
 * auditService.logToolInvocation(
 *     "tenant-123",
 *     "agent-456",
 *     "fhir_query",
 *     "read",
 *     "Patient",
 *     "999",
 *     Map.of("resourceType", "Patient", "resourceId", "999"),
 *     false,
 *     null,
 *     "Resource not found: Patient/999",
 *     25
 * );
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>This service is thread-safe and can be used concurrently by multiple threads.</p>
 */
@Service
public class McpAuditService {

    private static final Logger log = LoggerFactory.getLogger(McpAuditService.class);

    private static final String DEFAULT_TENANT_ID = "default";

    private final McpAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new McpAuditService.
     *
     * @param repository   the audit log repository
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     */
    public McpAuditService(McpAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Logs an MCP tool invocation.
     *
     * @param tenantId        the tenant ID (defaults to 'default' if null)
     * @param agentId         the identifier of the AI agent making the request
     * @param toolName        the name of the MCP tool (fhir_discover, fhir_query, fhir_mutate)
     * @param action          the action performed (read, search, create, update, delete)
     * @param resourceType    the FHIR resource type (may be null for some operations)
     * @param resourceId      the specific resource ID (null for search/create operations)
     * @param requestParams   the request parameters (will be serialized to JSON)
     * @param success         whether the invocation was successful
     * @param responseSummary a brief summary of the response (max 500 chars)
     * @param errorMessage    error message if the invocation failed (null for success)
     * @param latencyMs       time taken for the invocation in milliseconds
     */
    public void logToolInvocation(
            String tenantId,
            String agentId,
            String toolName,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> requestParams,
            boolean success,
            String responseSummary,
            String errorMessage,
            long latencyMs
    ) {
        try {
            McpAuditLogEntity entity = McpAuditLogEntity.builder()
                    .tenantId(tenantId != null ? tenantId : DEFAULT_TENANT_ID)
                    .agentId(agentId)
                    .toolName(toolName)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .requestParams(serializeParams(requestParams))
                    .success(success)
                    .responseSummary(truncateIfNeeded(responseSummary, 500))
                    .errorMessage(errorMessage)
                    .latencyMs((int) latencyMs)
                    .timestamp(Instant.now())
                    .build();

            repository.save(entity);

            if (log.isDebugEnabled()) {
                log.debug("Logged MCP audit: tool={}, action={}, success={}, latency={}ms",
                        toolName, action, success, latencyMs);
            }
        } catch (Exception e) {
            // Don't let audit logging failures affect the main request
            log.error("Failed to log MCP audit entry: tool={}, action={}, error={}",
                    toolName, action, e.getMessage());
        }
    }

    /**
     * Logs an MCP tool invocation with trace ID.
     *
     * @param tenantId        the tenant ID (defaults to 'default' if null)
     * @param agentId         the identifier of the AI agent making the request
     * @param toolName        the name of the MCP tool
     * @param action          the action performed
     * @param resourceType    the FHIR resource type
     * @param resourceId      the specific resource ID
     * @param requestParams   the request parameters
     * @param success         whether the invocation was successful
     * @param responseSummary a brief summary of the response
     * @param errorMessage    error message if failed
     * @param latencyMs       time taken in milliseconds
     * @param traceId         optional trace ID for distributed tracing
     */
    public void logToolInvocationWithTrace(
            String tenantId,
            String agentId,
            String toolName,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> requestParams,
            boolean success,
            String responseSummary,
            String errorMessage,
            long latencyMs,
            String traceId
    ) {
        try {
            McpAuditLogEntity entity = McpAuditLogEntity.builder()
                    .tenantId(tenantId != null ? tenantId : DEFAULT_TENANT_ID)
                    .agentId(agentId)
                    .toolName(toolName)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .requestParams(serializeParams(requestParams))
                    .success(success)
                    .responseSummary(truncateIfNeeded(responseSummary, 500))
                    .errorMessage(errorMessage)
                    .latencyMs((int) latencyMs)
                    .timestamp(Instant.now())
                    .traceId(traceId)
                    .build();

            repository.save(entity);

            if (log.isDebugEnabled()) {
                log.debug("Logged MCP audit: tool={}, action={}, success={}, latency={}ms, traceId={}",
                        toolName, action, success, latencyMs, traceId);
            }
        } catch (Exception e) {
            log.error("Failed to log MCP audit entry: tool={}, action={}, error={}",
                    toolName, action, e.getMessage());
        }
    }

    /**
     * Retrieves audit logs for a specific agent.
     *
     * @param agentId the agent identifier
     * @param limit   maximum number of logs to return
     * @return list of audit log entries, ordered by timestamp descending
     */
    public List<McpAuditLogEntity> getLogsForAgent(String agentId, int limit) {
        return repository.findByAgentIdOrderByTimestampDesc(agentId, PageRequest.of(0, limit));
    }

    /**
     * Retrieves audit logs for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @param limit    maximum number of logs to return
     * @return list of audit log entries, ordered by timestamp descending
     */
    public List<McpAuditLogEntity> getLogsForTenant(String tenantId, int limit) {
        return repository.findByTenantIdOrderByTimestampDesc(tenantId, PageRequest.of(0, limit));
    }

    /**
     * Retrieves audit logs for a specific tool.
     *
     * @param toolName the MCP tool name
     * @param limit    maximum number of logs to return
     * @return list of audit log entries, ordered by timestamp descending
     */
    public List<McpAuditLogEntity> getLogsForTool(String toolName, int limit) {
        return repository.findByToolNameOrderByTimestampDesc(toolName, PageRequest.of(0, limit));
    }

    /**
     * Serializes request parameters to JSON string.
     *
     * @param params the parameters to serialize
     * @return JSON string, or null if params is null
     */
    private String serializeParams(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize request params: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Truncates a string if it exceeds the maximum length.
     *
     * @param str       the string to truncate
     * @param maxLength the maximum allowed length
     * @return truncated string, or original if within limits
     */
    private String truncateIfNeeded(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
