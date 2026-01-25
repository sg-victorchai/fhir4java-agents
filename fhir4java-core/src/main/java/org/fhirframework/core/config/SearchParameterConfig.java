package org.fhirframework.core.config;

import org.fhirframework.core.searchparam.SearchParameterMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for search parameter restrictions on a resource.
 * <p>
 * Supports allowlist (only listed parameters allowed) or denylist
 * (all parameters allowed except those listed) modes.
 * </p>
 */
public class SearchParameterConfig {

    private SearchParameterMode mode = SearchParameterMode.ALLOWLIST;
    private List<String> common = new ArrayList<>();
    private List<String> resourceSpecific = new ArrayList<>();

    public SearchParameterConfig() {
    }

    public SearchParameterConfig(SearchParameterMode mode, List<String> common, List<String> resourceSpecific) {
        this.mode = mode;
        this.common = common != null ? common : new ArrayList<>();
        this.resourceSpecific = resourceSpecific != null ? resourceSpecific : new ArrayList<>();
    }

    public SearchParameterMode getMode() {
        return mode;
    }

    public void setMode(SearchParameterMode mode) {
        this.mode = mode;
    }

    public List<String> getCommon() {
        return common;
    }

    public void setCommon(List<String> common) {
        this.common = common != null ? common : new ArrayList<>();
    }

    public List<String> getResourceSpecific() {
        return resourceSpecific;
    }

    public void setResourceSpecific(List<String> resourceSpecific) {
        this.resourceSpecific = resourceSpecific != null ? resourceSpecific : new ArrayList<>();
    }

    /**
     * Checks if a search parameter is allowed based on this configuration.
     *
     * @param paramName the parameter name (e.g., "_id", "name", "birthdate")
     * @param isCommon  true if this is a common parameter (applies to all resources)
     * @return true if the parameter is allowed
     */
    public boolean isAllowed(String paramName, boolean isCommon) {
        boolean isInList = isCommon
                ? common.contains(paramName)
                : resourceSpecific.contains(paramName);

        return switch (mode) {
            case ALLOWLIST -> isInList;
            case DENYLIST -> !isInList;
        };
    }

    /**
     * Returns all allowed parameters based on this configuration.
     * <p>
     * For ALLOWLIST mode, returns the union of common and resourceSpecific lists.
     * For DENYLIST mode, returns null (meaning all except denied).
     * </p>
     */
    public Set<String> getAllAllowedParameters() {
        if (mode == SearchParameterMode.DENYLIST) {
            return null; // Indicates all parameters allowed except denied ones
        }
        Set<String> allowed = new HashSet<>();
        allowed.addAll(common);
        allowed.addAll(resourceSpecific);
        return allowed;
    }

    /**
     * Returns all denied parameters.
     * <p>
     * For DENYLIST mode, returns the union of common and resourceSpecific lists.
     * For ALLOWLIST mode, returns null (meaning all except allowed).
     * </p>
     */
    public Set<String> getAllDeniedParameters() {
        if (mode == SearchParameterMode.ALLOWLIST) {
            return null; // Indicates all parameters denied except allowed ones
        }
        Set<String> denied = new HashSet<>();
        denied.addAll(common);
        denied.addAll(resourceSpecific);
        return denied;
    }

    /**
     * Returns true if there are any restrictions configured.
     */
    public boolean hasRestrictions() {
        return !common.isEmpty() || !resourceSpecific.isEmpty();
    }

    @Override
    public String toString() {
        return "SearchParameterConfig{mode=" + mode +
                ", common=" + common +
                ", resourceSpecific=" + resourceSpecific + "}";
    }
}
