package org.fhirframework.core.interaction;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of FHIR RESTful interactions.
 * <p>
 * Based on HL7 FHIR RESTful API specification for resource operations.
 * </p>
 */
public enum InteractionType {

    READ("read", "GET", false),
    VREAD("vread", "GET", false),
    CREATE("create", "POST", true),
    UPDATE("update", "PUT", true),
    PATCH("patch", "PATCH", true),
    DELETE("delete", "DELETE", true),
    SEARCH("search", "GET", false),
    HISTORY("history", "GET", false);

    private final String code;
    private final String httpMethod;
    private final boolean mutating;

    private static final Map<String, InteractionType> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(i -> i.code.toLowerCase(), Function.identity()));

    InteractionType(String code, String httpMethod, boolean mutating) {
        this.code = code;
        this.httpMethod = httpMethod;
        this.mutating = mutating;
    }

    /**
     * Returns the FHIR interaction code (lowercase).
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the HTTP method associated with this interaction.
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * Returns true if this interaction modifies resource state.
     */
    public boolean isMutating() {
        return mutating;
    }

    /**
     * Returns true if this is a read-only interaction.
     */
    public boolean isReadOnly() {
        return !mutating;
    }

    /**
     * Parses an InteractionType from its code (case-insensitive).
     *
     * @param code the interaction code
     * @return the corresponding InteractionType
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static InteractionType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Interaction code cannot be null or blank");
        }

        InteractionType type = CODE_MAP.get(code.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Unknown interaction type: " + code +
                    ". Supported types: " + Arrays.toString(values()));
        }
        return type;
    }

    /**
     * Checks if the given code represents a valid interaction type.
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return CODE_MAP.containsKey(code.toLowerCase());
    }
}
