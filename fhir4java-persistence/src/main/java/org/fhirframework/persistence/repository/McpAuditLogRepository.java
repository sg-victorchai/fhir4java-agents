package org.fhirframework.persistence.repository;

import org.fhirframework.persistence.entity.McpAuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for MCP audit log operations.
 * <p>
 * Provides methods for retrieving audit logs by various criteria
 * such as agent ID, tenant ID, tool name, and time range.
 * </p>
 *
 * <h3>Usage Notes:</h3>
 * <ul>
 *   <li>All queries return results ordered by timestamp descending (most recent first)</li>
 *   <li>Use Pageable parameter to limit results and implement pagination</li>
 *   <li>Indexes on (tenant_id, timestamp), (agent_id, timestamp), and (tool_name, timestamp)
 *       ensure efficient querying</li>
 * </ul>
 */
@Repository
public interface McpAuditLogRepository extends JpaRepository<McpAuditLogEntity, Long> {

    /**
     * Find audit logs for a specific agent, ordered by timestamp descending.
     *
     * @param agentId  the agent identifier
     * @param pageable pagination parameters
     * @return list of audit log entries
     */
    List<McpAuditLogEntity> findByAgentIdOrderByTimestampDesc(String agentId, Pageable pageable);

    /**
     * Find audit logs for a specific tenant, ordered by timestamp descending.
     *
     * @param tenantId the tenant identifier
     * @param pageable pagination parameters
     * @return list of audit log entries
     */
    List<McpAuditLogEntity> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    /**
     * Find audit logs for a specific tool, ordered by timestamp descending.
     *
     * @param toolName the MCP tool name (fhir_discover, fhir_query, fhir_mutate)
     * @param pageable pagination parameters
     * @return list of audit log entries
     */
    List<McpAuditLogEntity> findByToolNameOrderByTimestampDesc(String toolName, Pageable pageable);

    /**
     * Find audit logs for a specific agent within a time range.
     *
     * @param agentId   the agent identifier
     * @param startTime the start of the time range (inclusive)
     * @param endTime   the end of the time range (inclusive)
     * @param pageable  pagination parameters
     * @return list of audit log entries
     */
    List<McpAuditLogEntity> findByAgentIdAndTimestampBetweenOrderByTimestampDesc(
            String agentId, Instant startTime, Instant endTime, Pageable pageable);

    /**
     * Find audit logs for a specific tenant and tool.
     *
     * @param tenantId the tenant identifier
     * @param toolName the MCP tool name
     * @param pageable pagination parameters
     * @return list of audit log entries
     */
    List<McpAuditLogEntity> findByTenantIdAndToolNameOrderByTimestampDesc(
            String tenantId, String toolName, Pageable pageable);

    /**
     * Find failed audit logs for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @param success  false to find failures
     * @param pageable pagination parameters
     * @return list of failed audit log entries
     */
    List<McpAuditLogEntity> findByTenantIdAndSuccessOrderByTimestampDesc(
            String tenantId, boolean success, Pageable pageable);

    /**
     * Count audit logs for a specific agent.
     *
     * @param agentId the agent identifier
     * @return count of audit log entries
     */
    long countByAgentId(String agentId);

    /**
     * Count audit logs for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return count of audit log entries
     */
    long countByTenantId(String tenantId);

    /**
     * Count failed invocations for a specific agent.
     *
     * @param agentId the agent identifier
     * @param success false to count failures
     * @return count of failed audit log entries
     */
    long countByAgentIdAndSuccess(String agentId, boolean success);
}
