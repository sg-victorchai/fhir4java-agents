package org.fhirframework.server.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Principal representing an authenticated AI agent.
 * <p>
 * This class holds information about an agent that has been authenticated
 * via API key. It implements {@link Principal} for integration with
 * Spring Security.
 * </p>
 */
public class AgentPrincipal implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String agentName;
    private final String tenantId;
    private final Set<String> scopes;

    /**
     * Creates a new AgentPrincipal.
     *
     * @param agentName the name/identifier of the agent
     * @param tenantId  the tenant ID associated with the agent
     * @param scopes    the set of SMART on FHIR scopes granted to the agent
     */
    public AgentPrincipal(String agentName, String tenantId, Set<String> scopes) {
        this.agentName = agentName;
        this.tenantId = tenantId;
        this.scopes = scopes != null ? Set.copyOf(scopes) : Collections.emptySet();
    }

    /**
     * Returns the agent name as the principal name.
     *
     * @return the agent name
     */
    @Override
    public String getName() {
        return agentName;
    }

    /**
     * Returns the agent name.
     *
     * @return the agent name
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Returns the tenant ID associated with this agent.
     *
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns the scopes granted to this agent.
     *
     * @return an unmodifiable set of scopes
     */
    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * Checks if the agent has a specific scope.
     *
     * @param scope the scope to check
     * @return true if the agent has the scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    @Override
    public String toString() {
        return "AgentPrincipal{" +
                "agentName='" + agentName + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", scopes=" + scopes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentPrincipal that = (AgentPrincipal) o;
        return agentName.equals(that.agentName) &&
                tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        int result = agentName.hashCode();
        result = 31 * result + tenantId.hashCode();
        return result;
    }
}
