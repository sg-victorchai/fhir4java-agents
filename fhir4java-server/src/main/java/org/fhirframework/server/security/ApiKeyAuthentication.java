package org.fhirframework.server.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication token for API key-based authentication.
 * <p>
 * This token is used to represent a successfully authenticated AI agent
 * that used an API key for authentication. It holds the {@link AgentPrincipal}
 * and the granted authorities derived from the agent's scopes.
 * </p>
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AgentPrincipal principal;
    private final String credentials;

    /**
     * Creates an unauthenticated API key authentication token.
     * Used before authentication is complete.
     *
     * @param apiKey the API key (credentials)
     */
    public ApiKeyAuthentication(String apiKey) {
        super(null);
        this.principal = null;
        this.credentials = apiKey;
        setAuthenticated(false);
    }

    /**
     * Creates an authenticated API key authentication token.
     *
     * @param principal the authenticated agent principal
     * @param scopes    the scopes granted to the agent
     */
    public ApiKeyAuthentication(AgentPrincipal principal, Set<String> scopes) {
        super(scopesToAuthorities(scopes));
        this.principal = principal;
        this.credentials = null;
        setAuthenticated(true);
    }

    /**
     * Converts SMART on FHIR scopes to Spring Security authorities.
     *
     * @param scopes the scopes to convert
     * @return collection of granted authorities
     */
    private static Collection<GrantedAuthority> scopesToAuthorities(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Set.of();
        }
        return scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the credentials (API key) for unauthenticated tokens,
     * or null for authenticated tokens.
     *
     * @return the API key or null
     */
    @Override
    public Object getCredentials() {
        return credentials;
    }

    /**
     * Returns the authenticated agent principal.
     *
     * @return the agent principal, or null if not yet authenticated
     */
    @Override
    public AgentPrincipal getPrincipal() {
        return principal;
    }

    /**
     * Returns the agent name from the principal.
     *
     * @return the agent name, or null if not authenticated
     */
    @Override
    public String getName() {
        return principal != null ? principal.getName() : null;
    }
}
