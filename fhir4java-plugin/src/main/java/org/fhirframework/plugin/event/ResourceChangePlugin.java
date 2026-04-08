package org.fhirframework.plugin.event;

import org.fhirframework.core.event.EventPublisher;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.fhirframework.plugin.ExecutionMode;
import org.fhirframework.plugin.FhirPlugin;
import org.fhirframework.plugin.OperationDescriptor;
import org.fhirframework.plugin.OperationType;
import org.fhirframework.plugin.PluginContext;
import org.fhirframework.plugin.PluginResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Plugin that publishes resource change events after write operations.
 * <p>
 * This plugin runs in the AFTER phase and publishes events for:
 * <ul>
 *   <li>CREATE - when a new resource is created</li>
 *   <li>UPDATE - when an existing resource is updated</li>
 *   <li>DELETE - when a resource is deleted</li>
 * </ul>
 * </p>
 * <p>
 * Events are published asynchronously to avoid impacting request latency.
 * Subscribers can use these events for notifications, indexing, audit trails,
 * or integration with external systems.
 * </p>
 */
@Component
public class ResourceChangePlugin implements FhirPlugin {

    private static final Logger log = LoggerFactory.getLogger(ResourceChangePlugin.class);

    private final EventPublisher eventPublisher;
    private boolean enabled = true;

    /**
     * Creates a new ResourceChangePlugin.
     *
     * @param eventPublisher The event publisher to use for publishing events
     */
    @Autowired
    public ResourceChangePlugin(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getName() {
        return "resource-change-plugin";
    }

    @Override
    public ExecutionMode getExecutionMode() {
        // Async to avoid impacting request latency
        return ExecutionMode.ASYNC;
    }

    @Override
    public int getPriority() {
        // Run late in the pipeline, after other AFTER plugins
        return 150;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable this plugin.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public List<OperationDescriptor> getSupportedOperations() {
        // Only handle write operations (all resource types)
        return List.of(
                OperationDescriptor.forAllResources(OperationType.CREATE),
                OperationDescriptor.forAllResources(OperationType.UPDATE),
                OperationDescriptor.forAllResources(OperationType.DELETE)
        );
    }

    @Override
    public PluginResult executeAfter(PluginContext context) {
        // Only publish events for write operations
        if (!context.getOperationType().isWriteOperation()) {
            log.trace("Skipping non-write operation: {}", context.getOperationType());
            return PluginResult.continueProcessing();
        }

        // Need resource ID to publish a meaningful event
        if (context.getResourceId().isEmpty()) {
            log.debug("Skipping event publishing - no resource ID available for {} on {}",
                    context.getOperationType(), context.getResourceType());
            return PluginResult.continueProcessing();
        }

        String resourceType = context.getResourceType();
        String resourceId = context.getResourceId().get();
        String tenantId = context.getTenantId().orElse(null);
        String action = mapOperationToAction(context.getOperationType());

        ResourceChangeEvent event = new ResourceChangeEvent(
                resourceType,
                resourceId,
                action,
                tenantId,
                Instant.now()
        );

        try {
            eventPublisher.publish(event);
            log.debug("Published {} event for {}/{} (tenant: {})",
                    action, resourceType, resourceId, tenantId);
        } catch (Exception e) {
            // Log error but don't fail the request - event publishing is best-effort
            log.error("Failed to publish {} event for {}/{}: {}",
                    action, resourceType, resourceId, e.getMessage(), e);
        }

        return PluginResult.continueProcessing();
    }

    /**
     * Maps an operation type to the corresponding action string.
     */
    private String mapOperationToAction(OperationType operationType) {
        return switch (operationType) {
            case CREATE -> ResourceChangeEvent.ACTION_CREATE;
            case UPDATE -> ResourceChangeEvent.ACTION_UPDATE;
            case DELETE -> ResourceChangeEvent.ACTION_DELETE;
            default -> operationType.name().toLowerCase();
        };
    }
}
