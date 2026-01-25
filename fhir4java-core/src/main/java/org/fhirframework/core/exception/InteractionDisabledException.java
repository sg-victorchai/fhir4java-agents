package org.fhirframework.core.exception;

import org.fhirframework.core.interaction.InteractionType;

/**
 * Exception thrown when an interaction is disabled for a resource type.
 */
public class InteractionDisabledException extends FhirException {

    private final String resourceType;
    private final InteractionType interaction;

    public InteractionDisabledException(String resourceType, InteractionType interaction) {
        super(String.format("Interaction '%s' is not supported for resource type '%s'",
                interaction.getCode(), resourceType),
                "not-supported",
                String.format("The %s interaction is disabled for %s resources in this server's configuration",
                        interaction.getCode(), resourceType));
        this.resourceType = resourceType;
        this.interaction = interaction;
    }

    public String getResourceType() {
        return resourceType;
    }

    public InteractionType getInteraction() {
        return interaction;
    }
}
