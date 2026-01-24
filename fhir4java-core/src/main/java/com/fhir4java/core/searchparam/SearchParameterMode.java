package com.fhir4java.core.searchparam;

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
    ALLOWLIST,

    /**
     * All search parameters are allowed except those listed.
     * Listed parameters will be rejected.
     */
    DENYLIST
}
