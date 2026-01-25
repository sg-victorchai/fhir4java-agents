package org.fhirframework.core.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends FhirException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("Resource '%s/%s' not found", resourceType, resourceId),
                "not-found",
                String.format("No resource of type %s found with id %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String resourceType) {
        super(String.format("Resource type '%s' is not supported", resourceType),
                "not-supported",
                String.format("The resource type %s is not configured on this server", resourceType));
        this.resourceType = resourceType;
        this.resourceId = null;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
