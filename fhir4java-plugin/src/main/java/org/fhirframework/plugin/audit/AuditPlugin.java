package org.fhirframework.plugin.audit;

import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;

import java.time.Instant;
import java.util.Map;

/**
 * Plugin interface for audit logging.
 * <p>
 * Audit plugins record operations for compliance and security purposes.
 * They execute asynchronously in the AFTER phase to avoid impacting
 * request latency.
 * </p>
 */
public interface AuditPlugin extends FhirPlugin {

    @Override
    default ExecutionMode getExecutionMode() {
        // Audit logging is typically async to not impact latency
        return ExecutionMode.ASYNC;
    }

    @Override
    default int getPriority() {
        // Audit runs late in the pipeline
        return 200;
    }

    /**
     * Record an audit event for the operation.
     *
     * @param event The audit event to record
     */
    void recordAuditEvent(AuditEvent event);

    @Override
    default PluginResult executeAfter(PluginContext context) {
        AuditEvent event = AuditEvent.fromContext(context, AuditOutcome.SUCCESS, null);
        recordAuditEvent(event);
        return PluginResult.continueProcessing();
    }

    @Override
    default PluginResult executeOnError(PluginContext context, Exception exception) {
        AuditEvent event = AuditEvent.fromContext(context, AuditOutcome.FAILURE, exception.getMessage());
        recordAuditEvent(event);
        return PluginResult.continueProcessing();
    }

    /**
     * Audit event outcome.
     */
    enum AuditOutcome {
        SUCCESS,
        FAILURE,
        DENIED
    }

    /**
     * Represents an audit event.
     */
    record AuditEvent(
            String eventId,
            Instant timestamp,
            String operationType,
            String resourceType,
            String resourceId,
            String fhirVersion,
            String userId,
            String clientId,
            String tenantId,
            AuditOutcome outcome,
            String outcomeDescription,
            Map<String, String> additionalData
    ) {
        public static AuditEvent fromContext(PluginContext context, AuditOutcome outcome, String outcomeDescription) {
            return new AuditEvent(
                    context.getRequestId(),
                    context.getTimestamp(),
                    context.getOperationType().name(),
                    context.getResourceType(),
                    context.getResourceId().orElse(null),
                    context.getFhirVersion().getCode(),
                    context.getUserId().orElse(null),
                    context.getClientId().orElse(null),
                    context.getTenantId().orElse(null),
                    outcome,
                    outcomeDescription,
                    Map.of()
            );
        }

        public AuditEvent withAdditionalData(Map<String, String> data) {
            return new AuditEvent(
                    eventId, timestamp, operationType, resourceType, resourceId,
                    fhirVersion, userId, clientId, tenantId, outcome, outcomeDescription, data
            );
        }
    }
}
