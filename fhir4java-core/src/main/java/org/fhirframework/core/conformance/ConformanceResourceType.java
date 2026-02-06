package org.fhirframework.core.conformance;

/**
 * Enumeration of supported FHIR conformance resource types.
 * <p>
 * These are resources that describe the FHIR specification itself
 * (StructureDefinition, SearchParameter, OperationDefinition) and are
 * served from static JSON files rather than persisted in the database.
 * </p>
 */
public enum ConformanceResourceType {

    STRUCTURE_DEFINITION("StructureDefinition", "profiles", "StructureDefinition-"),
    SEARCH_PARAMETER("SearchParameter", "searchparameters", "SearchParameter-"),
    OPERATION_DEFINITION("OperationDefinition", "operations", "OperationDefinition-");

    private final String resourceTypeName;
    private final String directoryName;
    private final String filePrefix;

    ConformanceResourceType(String resourceTypeName, String directoryName, String filePrefix) {
        this.resourceTypeName = resourceTypeName;
        this.directoryName = directoryName;
        this.filePrefix = filePrefix;
    }

    /**
     * Returns the FHIR resource type name (e.g., "StructureDefinition").
     */
    public String getResourceTypeName() {
        return resourceTypeName;
    }

    /**
     * Returns the directory name where these resources are stored (e.g., "profiles").
     */
    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * Returns the file name prefix for this resource type (e.g., "StructureDefinition-").
     */
    public String getFilePrefix() {
        return filePrefix;
    }

    /**
     * Returns the glob pattern to match all files for this resource type.
     */
    public String getFilePattern() {
        return filePrefix + "*.json";
    }

    /**
     * Extracts the resource ID from a filename.
     *
     * @param filename the filename (e.g., "StructureDefinition-Patient.json")
     * @return the resource ID (e.g., "Patient")
     */
    public String extractId(String filename) {
        if (filename == null || !filename.startsWith(filePrefix) || !filename.endsWith(".json")) {
            return null;
        }
        return filename.substring(filePrefix.length(), filename.length() - 5);
    }

    /**
     * Returns the ConformanceResourceType for a given FHIR resource type name.
     *
     * @param resourceTypeName the FHIR resource type name (e.g., "StructureDefinition")
     * @return the matching ConformanceResourceType, or null if not found
     */
    public static ConformanceResourceType fromResourceTypeName(String resourceTypeName) {
        for (ConformanceResourceType type : values()) {
            if (type.resourceTypeName.equals(resourceTypeName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Checks if the given FHIR resource type name is a conformance resource.
     */
    public static boolean isConformanceResource(String resourceTypeName) {
        return fromResourceTypeName(resourceTypeName) != null;
    }
}
