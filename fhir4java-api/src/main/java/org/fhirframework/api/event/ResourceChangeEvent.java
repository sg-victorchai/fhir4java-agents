package org.fhirframework.api.event;

import java.time.Instant;

/**
 * Event object representing a change to a FHIR resource.
 * <p>
 * This is a type alias for {@link org.fhirframework.core.event.ResourceChangeEvent}
 * for use in the API layer. Use either type interchangeably.
 * </p>
 * <p>
 * Published after resource create, update, or delete operations complete.
 * Can be consumed by subscribers for notifications, indexing, or audit purposes.
 * </p>
 *
 * @param resourceType The FHIR resource type (e.g., "Patient", "Observation")
 * @param resourceId   The logical ID of the resource
 * @param action       The action performed: "create", "update", or "delete"
 * @param tenantId     The tenant context in which the change occurred
 * @param timestamp    When the event occurred
 *
 * @see org.fhirframework.core.event.ResourceChangeEvent
 */
public record ResourceChangeEvent(
        String resourceType,
        String resourceId,
        String action,
        String tenantId,
        Instant timestamp
) {
    /**
     * Action constant for resource creation.
     */
    public static final String ACTION_CREATE = "create";

    /**
     * Action constant for resource update.
     */
    public static final String ACTION_UPDATE = "update";

    /**
     * Action constant for resource deletion.
     */
    public static final String ACTION_DELETE = "delete";

    /**
     * Creates a new ResourceChangeEvent with validation.
     */
    public ResourceChangeEvent {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be null or blank");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be null or blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }

    /**
     * Factory method to create a create event.
     */
    public static ResourceChangeEvent create(String resourceType, String resourceId, String tenantId) {
        return new ResourceChangeEvent(resourceType, resourceId, ACTION_CREATE, tenantId, Instant.now());
    }

    /**
     * Factory method to create an update event.
     */
    public static ResourceChangeEvent update(String resourceType, String resourceId, String tenantId) {
        return new ResourceChangeEvent(resourceType, resourceId, ACTION_UPDATE, tenantId, Instant.now());
    }

    /**
     * Factory method to create a delete event.
     */
    public static ResourceChangeEvent delete(String resourceType, String resourceId, String tenantId) {
        return new ResourceChangeEvent(resourceType, resourceId, ACTION_DELETE, tenantId, Instant.now());
    }

    /**
     * Convert from core event type.
     */
    public static ResourceChangeEvent from(org.fhirframework.core.event.ResourceChangeEvent coreEvent) {
        return new ResourceChangeEvent(
                coreEvent.resourceType(),
                coreEvent.resourceId(),
                coreEvent.action(),
                coreEvent.tenantId(),
                coreEvent.timestamp()
        );
    }

    /**
     * Convert to core event type.
     */
    public org.fhirframework.core.event.ResourceChangeEvent toCore() {
        return new org.fhirframework.core.event.ResourceChangeEvent(
                resourceType, resourceId, action, tenantId, timestamp
        );
    }
}
