package org.fhirframework.core.searchparam;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mode for search parameter restriction configuration.
 * <p>
 * Determines how the search parameter list is interpreted for a resource.
 * </p>
 */
public enum SearchParameterMode {

    /**
     * Only the listed search parameters are allowed.
     * Any parameters not in the list will be rejected.
     */
    ALLOWLIST("allowlist"),

    /**
     * All search parameters are allowed except those listed.
     * Listed parameters will be rejected.
     */
    DENYLIST("denylist");

    private final String value;

    SearchParameterMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SearchParameterMode fromValue(String value) {
        if (value == null) {
            return ALLOWLIST;
        }
        for (SearchParameterMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown SearchParameterMode: " + value);
    }
}
