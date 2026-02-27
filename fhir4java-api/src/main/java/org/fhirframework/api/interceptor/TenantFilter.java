package org.fhirframework.api.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip for actuator, health check, and non-FHIR endpoints
        return path.startsWith("/actuator");
    }
}
