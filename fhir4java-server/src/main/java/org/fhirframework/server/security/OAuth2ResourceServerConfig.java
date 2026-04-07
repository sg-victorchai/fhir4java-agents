package org.fhirframework.server.security;

import org.fhirframework.persistence.repository.AgentApiKeyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server configuration for FHIR4Java.
 *
 * <p>This configuration secures the FHIR and MCP API endpoints using either JWT-based OAuth2
 * authentication or API key authentication. Both methods work alongside each other - clients
 * can use either an OAuth2 JWT token or an API key (via X-API-Key header) to authenticate.</p>
 *
 * <h3>Authentication Methods:</h3>
 * <ul>
 *   <li>OAuth2 JWT - Standard JWT bearer token authentication</li>
 *   <li>API Key - Alternative authentication using X-API-Key header (for AI agents)</li>
 * </ul>
 *
 * <h3>Security Rules:</h3>
 * <ul>
 *   <li>Metadata endpoints (/fhir/&#42;&#42;/metadata) - publicly accessible</li>
 *   <li>Actuator health endpoints - publicly accessible</li>
 *   <li>MCP API endpoints (/api/mcp/&#42;&#42;) - require authentication</li>
 *   <li>FHIR endpoints (/fhir/&#42;&#42;) - require authentication</li>
 *   <li>All other endpoints - require authentication</li>
 * </ul>
 *
 * <h3>Configuration:</h3>
 * <p>This configuration can be disabled by setting:</p>
 * <pre>
 * fhir4java:
 *   security:
 *     oauth2:
 *       enabled: false
 * </pre>
 *
 * <p>JWT issuer URI must be configured in application.yml:</p>
 * <pre>
 * spring:
 *   security:
 *     oauth2:
 *       resourceserver:
 *         jwt:
 *           issuer-uri: http://your-auth-server/realms/your-realm
 * </pre>
 *
 * @see org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
 * @see ApiKeyAuthFilter
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
        name = "fhir4java.security.oauth2.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OAuth2ResourceServerConfig {

    @Autowired
    private AgentApiKeyRepository apiKeyRepository;

    /**
     * Configures the security filter chain for OAuth2 resource server with API key support.
     *
     * <p>Key configurations:</p>
     * <ul>
     *   <li>CSRF disabled (stateless REST API)</li>
     *   <li>Session management set to STATELESS (no server-side sessions)</li>
     *   <li>API key authentication filter (runs before JWT filter)</li>
     *   <li>JWT-based authentication for protected endpoints</li>
     * </ul>
     *
     * <p>The API key filter is added before the JWT bearer token filter, so requests
     * with an X-API-Key header will be authenticated via API key first. If no API key
     * is present, authentication falls through to the JWT filter.</p>
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API (stateless, token-based auth)
            .csrf(csrf -> csrf.disable())

            // Stateless session management (no server-side sessions)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - FHIR metadata (CapabilityStatement)
                .requestMatchers("/fhir/metadata").permitAll()
                .requestMatchers("/fhir/r5/metadata").permitAll()
                .requestMatchers("/fhir/r4b/metadata").permitAll()
                .requestMatchers("/fhir/*/metadata").permitAll()

                // Public endpoints - Actuator health checks
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()

                // Protected endpoints - MCP API
                .requestMatchers("/api/mcp/**").authenticated()

                // Protected endpoints - FHIR API
                .requestMatchers("/fhir/**").authenticated()

                // Default - require authentication
                .anyRequest().authenticated()
            )

            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            )

            // Add API key authentication filter before JWT filter
            .addFilterBefore(
                new ApiKeyAuthFilter(apiKeyRepository),
                BearerTokenAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * Security filter chain that permits all requests when OAuth2 is disabled.
     * This configuration is activated when fhir4java.security.oauth2.enabled=false.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain that permits all requests
     * @throws Exception if configuration fails
     */
    @Configuration
    @ConditionalOnProperty(
            name = "fhir4java.security.oauth2.enabled",
            havingValue = "false"
    )
    public static class NoSecurityConfig {

        @Bean
        @Order(1)
        public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
