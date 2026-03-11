package org.fhirframework.server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Set;

@Configuration
public class EndpointFilterConfiguration {

    @Value("${fhir4java.endpoints.enabled:all}")
    private String enabledEndpoints;

    @Bean
    public FilterRegistrationBean<EndpointFilter> endpointFilter() {
        FilterRegistrationBean<EndpointFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new EndpointFilter(enabledEndpoints));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;
    }

    public static class EndpointFilter implements Filter {
        private final String enabledEndpoints;
        private final Set<String> allowedPrefixes;

        public EndpointFilter(String enabledEndpoints) {
            this.enabledEndpoints = enabledEndpoints;
            this.allowedPrefixes = switch (enabledEndpoints) {
                case "fhir" -> Set.of("/fhir");
                case "metadata" -> Set.of("/fhir/r4b/metadata", "/fhir/r5/metadata");
                case "actuator" -> Set.of("/actuator");
                case "admin" -> Set.of("/api/admin");
                case "all" -> Set.of("/");
                default -> Set.of("/");
            };
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            String path = httpRequest.getRequestURI();

            // Always allow health checks for load balancer
            if (path.startsWith("/actuator/health")) {
                chain.doFilter(request, response);
                return;
            }

            // Check if path is allowed for this service
            if ("all".equals(enabledEndpoints) || isPathAllowed(path)) {
                chain.doFilter(request, response);
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Endpoint not available on this service\"}");
            }
        }

        private boolean isPathAllowed(String path) {
            return allowedPrefixes.stream().anyMatch(path::startsWith);
        }
    }
}
