package org.fhirframework.core.subscription;

import java.util.List;
import java.util.Map;

/**
 * Represents what events a FHIR subscription monitors.
 * <p>
 * A SubscriptionTopic defines the criteria for matching resource change events:
 * <ul>
 *   <li>resourceType - The FHIR resource type to monitor (e.g., "Patient")</li>
 *   <li>events - The actions to monitor (e.g., ["create", "update"])</li>
 *   <li>filters - Additional filter criteria (e.g., {"status": "active"})</li>
 * </ul>
 * </p>
 *
 * @param resourceType The FHIR resource type to monitor (e.g., "Patient", "Observation")
 * @param events       The actions to monitor: "create", "update", "delete". Empty list means all actions.
 * @param filters      Additional filter criteria for the subscription (e.g., {"status": "active"})
 */
public record SubscriptionTopic(
        String resourceType,
        List<String> events,
        Map<String, String> filters
) {

    /**
     * Creates a new SubscriptionTopic with validation.
     */
    public SubscriptionTopic {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be null or blank");
        }
        // Make defensive copies for immutability
        events = events != null ? List.copyOf(events) : List.of();
        filters = filters != null ? Map.copyOf(filters) : Map.of();
    }

    /**
     * Checks if this subscription topic matches the given resource type and action.
     * <p>
     * Matching rules:
     * <ul>
     *   <li>Resource type must match exactly</li>
     *   <li>Action must be in the events list, or events list must be empty (matches all)</li>
     *   <li>Action matching is case-insensitive</li>
     * </ul>
     * </p>
     *
     * @param resourceType The resource type of the event
     * @param action       The action that occurred (create, update, delete)
     * @return true if this topic matches the given criteria
     */
    public boolean matches(String resourceType, String action) {
        // Resource type must match exactly
        if (!this.resourceType.equals(resourceType)) {
            return false;
        }

        // Empty events list means match all actions
        if (events.isEmpty()) {
            return true;
        }

        // Check if the action is in the events list (case-insensitive)
        return events.stream()
                .anyMatch(event -> event.equalsIgnoreCase(action));
    }
}
