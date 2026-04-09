package org.fhirframework.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA Entity for MCP (Model Context Protocol) audit logging.
 * <p>
 * Records all MCP tool invocations for auditing and monitoring purposes.
 * Each entry captures the tool used, the action performed, the target
 * resource (if applicable), request parameters, response summary,
 * success/failure status, and timing information.
 * </p>
 *
 * <h3>Tool Names:</h3>
 * <ul>
 *   <li>{@code fhir_discover} - Discovery and metadata operations</li>
 *   <li>{@code fhir_query} - Read and search operations</li>
 *   <li>{@code fhir_mutate} - Create, update, and delete operations</li>
 * </ul>
 *
 * <h3>Actions:</h3>
 * <ul>
 *   <li>{@code read} - Read a single resource</li>
 *   <li>{@code search} - Search for resources</li>
 *   <li>{@code create} - Create a new resource</li>
 *   <li>{@code update} - Update an existing resource</li>
 *   <li>{@code delete} - Delete a resource</li>
 * </ul>
 */
@Entity
@Table(name = "mcp_audit_log", schema = "fhir")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant ID for multi-tenancy support.
     * Defaults to 'default' if not specified.
     */
    @Column(name = "tenant_id", length = 64)
    @Builder.Default
    private String tenantId = "default";

    /**
     * Identifier of the AI agent making the request.
     */
    @Column(name = "agent_id", nullable = false, length = 100)
    private String agentId;

    /**
     * Name of the MCP tool invoked.
     * One of: fhir_discover, fhir_query, fhir_mutate
     */
    @Column(name = "tool_name", nullable = false, length = 50)
    private String toolName;

    /**
     * The action performed within the tool.
     * Examples: read, search, create, update, delete
     */
    @Column(name = "action", length = 50)
    private String action;

    /**
     * The FHIR resource type being operated on.
     * May be null for discovery operations that don't target a specific type.
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * The specific resource ID being operated on.
     * Null for search operations or operations that create new resources.
     */
    @Column(name = "resource_id", length = 64)
    private String resourceId;

    /**
     * The request parameters as a JSON string.
     * Stored in JSONB format in PostgreSQL for efficient querying.
     */
    @Column(name = "request_params", columnDefinition = "jsonb")
    private String requestParams;

    /**
     * A summary of the response, truncated to 500 characters.
     * For successful searches, this might include the count of results.
     * For errors, this might be a brief description.
     */
    @Column(name = "response_summary", length = 500)
    private String responseSummary;

    /**
     * Whether the tool invocation was successful.
     */
    @Column(name = "success", nullable = false)
    private boolean success;

    /**
     * Error message if the invocation failed.
     * Contains the full error text for debugging.
     */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /**
     * Latency of the tool invocation in milliseconds.
     */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * Timestamp when the tool invocation occurred.
     */
    @Column(name = "timestamp")
    private Instant timestamp;

    /**
     * Optional trace ID for correlating logs across services.
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (tenantId == null) {
            tenantId = "default";
        }
    }
}
