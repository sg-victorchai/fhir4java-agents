package org.fhirframework.api.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fhirframework.core.exception.TenantDisabledException;
import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that resolves the tenant from the incoming request header
 * and sets it on {@link TenantContext} for downstream use.
 * <p>
 * Runs before {@link FhirVersionFilter} in the filter chain.
 * When multi-tenancy is disabled, sets the default tenant ID.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // Before FhirVersionFilter (HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final TenantService tenantService;
    private final TenantProperties tenantProperties;

    public TenantFilter(TenantService tenantService, TenantProperties tenantProperties) {
        this.tenantService = tenantService;
        this.tenantProperties = tenantProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String headerValue = request.getHeader(tenantProperties.getHeaderName());
            String tenantId = tenantService.resolveEffectiveTenantId(headerValue);
            TenantContext.setCurrentTenantId(tenantId);

            if (log.isDebugEnabled()) {
                log.debug("Tenant resolved: header={}, tenantId={}, path={}",
                        headerValue, tenantId, request.getRequestURI());
            }

            filterChain.doFilter(request, response);
        } catch (TenantNotFoundException ex) {
            log.warn("Tenant not found: {}", ex.getMessage());
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (TenantDisabledException ex) {
            log.warn("Tenant disabled: {}", ex.getMessage());
            sendErrorResponse(response, HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid tenant header: {}", ex.getMessage());
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = """
                {
                  "resourceType": "OperationOutcome",
                  "issue": [{
                    "severity": "error",
                    "code": "%s",
                    "diagnostics": "%s"
                  }]
                }
                """.formatted(
                status == HttpStatus.FORBIDDEN ? "forbidden" : "invalid",
                message.replace("\"", "\\\"")
        );
        response.getWriter().write(body);
        response.getWriter().flush();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip for actuator, health check, admin API, and non-FHIR endpoints
        return path.startsWith("/actuator") || path.startsWith("/api/admin");
    }
}
