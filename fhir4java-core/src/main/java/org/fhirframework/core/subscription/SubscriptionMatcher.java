package org.fhirframework.core.subscription;

import org.fhirframework.core.event.ResourceChangeEvent;
import org.springframework.stereotype.Component;

/**
 * Matches resource change events against subscription topics.
 * <p>
 * This component is responsible for determining if a given event
 * matches a subscription's criteria. It delegates to the SubscriptionTopic's
 * matching logic and can be extended to support more complex matching
 * scenarios (e.g., filter-based matching on resource content).
 * </p>
 */
@Component
public class SubscriptionMatcher {

    /**
     * Check if a resource change event matches a subscription topic.
     * <p>
     * The event matches if:
     * <ul>
     *   <li>The event's resource type matches the topic's resource type</li>
     *   <li>The event's action is in the topic's events list (or events list is empty)</li>
     * </ul>
     * </p>
     *
     * @param event The resource change event to check
     * @param topic The subscription topic to match against
     * @return true if the event matches the topic's criteria
     */
    public boolean matches(ResourceChangeEvent event, SubscriptionTopic topic) {
        if (event == null || topic == null) {
            return false;
        }

        return topic.matches(event.resourceType(), event.action());
    }
}
