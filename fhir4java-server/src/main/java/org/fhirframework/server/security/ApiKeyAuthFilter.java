package org.fhirframework.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fhirframework.persistence.entity.AgentApiKeyEntity;
import org.fhirframework.persistence.repository.AgentApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter for API key-based authentication.
 * <p>
 * This filter extracts the API key from the {@code X-API-Key} header,
 * validates it against the database, and sets up the security context
 * if authentication succeeds.
 * </p>
 * <p>
 * The filter runs before the OAuth2 JWT filter, allowing API key auth
 * to be used as an alternative to JWT-based authentication.
 * </p>
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final AgentApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(AgentApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        // If no API key header, skip this filter and let the chain continue
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // If already authenticated (e.g., by another filter), skip
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Hash the API key
            String keyHash = hashApiKey(apiKey);

            // Look up the key in the database
            Optional<AgentApiKeyEntity> apiKeyEntity = apiKeyRepository.findByKeyHash(keyHash);

            if (apiKeyEntity.isEmpty()) {
                logger.debug("API key not found in database");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }

            AgentApiKeyEntity entity = apiKeyEntity.get();

            // Validate the key is enabled and not expired
            if (!entity.isValid()) {
                logger.debug("API key is not valid (disabled or expired) for agent: {}", entity.getAgentName());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API key is disabled or expired");
                return;
            }

            // Parse scopes
            Set<String> scopes = parseScopes(entity.getScopes());

            // Create the principal and authentication token
            AgentPrincipal principal = new AgentPrincipal(
                    entity.getAgentName(),
                    entity.getTenantId(),
                    scopes
            );

            ApiKeyAuthentication authentication = new ApiKeyAuthentication(principal, scopes);

            // Set the security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Update last used timestamp using entity save
            entity.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(entity);

            logger.debug("API key authentication successful for agent: {}", entity.getAgentName());

            // Continue the filter chain
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error during API key authentication", e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication error");
        }
    }

    /**
     * Hash the API key using SHA-256.
     *
     * @param apiKey the raw API key
     * @return the hex-encoded SHA-256 hash
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Parse comma-separated scopes into a Set.
     *
     * @param scopes comma-separated scopes string
     * @return set of individual scopes
     */
    private Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
