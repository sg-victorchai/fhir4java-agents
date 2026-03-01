package org.fhirframework.api.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.fhirframework.core.exception.TenantDisabledException;
import org.fhirframework.core.exception.TenantNotFoundException;
import org.fhirframework.core.tenant.TenantContext;
import org.fhirframework.core.tenant.TenantProperties;
import org.fhirframework.persistence.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantFilter} header extraction and filter chain behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantFilter")
class TenantFilterTest {

    @Mock
    private TenantService tenantService;

    @Mock
    private FilterChain filterChain;

    private TenantProperties tenantProperties;
    private TenantFilter tenantFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static final UUID TENANT_UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantFilter = new TenantFilter(tenantService, tenantProperties);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("should set tenant context from header and continue chain")
        void shouldSetTenantContextAndContinueChain() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenReturn("hosp-a");

            tenantFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass null header value to tenant service when no header present")
        void shouldPassNullWhenNoHeader() throws ServletException, IOException {
            when(tenantService.resolveEffectiveTenantId(null))
                    .thenReturn("default");

            tenantFilter.doFilterInternal(request, response, filterChain);

            verify(tenantService).resolveEffectiveTenantId(null);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should set default tenant when tenancy is disabled")
        void shouldSetDefaultTenantWhenDisabled() throws ServletException, IOException {
            when(tenantService.resolveEffectiveTenantId(null))
                    .thenReturn("default");

            tenantFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should clear tenant context after filter chain completes")
        void shouldClearTenantContextAfterFilterChain() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenReturn("hosp-a");

            tenantFilter.doFilterInternal(request, response, filterChain);

            // After filter completes, context should be cleared
            assertTrue(TenantContext.getTenantIdIfSet().isEmpty(),
                    "TenantContext should be cleared after filter chain");
        }

        @Test
        @DisplayName("should clear tenant context even when filter chain throws")
        void shouldClearTenantContextOnException() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenReturn("hosp-a");
            doThrow(new ServletException("downstream error"))
                    .when(filterChain).doFilter(request, response);

            assertThrows(ServletException.class,
                    () -> tenantFilter.doFilterInternal(request, response, filterChain));

            assertTrue(TenantContext.getTenantIdIfSet().isEmpty(),
                    "TenantContext should be cleared even after exception");
        }

        @Test
        @DisplayName("should clear tenant context when tenant resolution throws and return error response")
        void shouldClearTenantContextOnResolutionError() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenThrow(new TenantNotFoundException(TENANT_UUID.toString()));

            tenantFilter.doFilterInternal(request, response, filterChain);

            assertEquals(400, response.getStatus(),
                    "Should return 400 for unknown tenant");
            assertTrue(TenantContext.getTenantIdIfSet().isEmpty(),
                    "TenantContext should be cleared even after resolution error");
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 400 for TenantNotFoundException from service")
        void shouldPropagateTenantNotFoundException() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenThrow(new TenantNotFoundException(TENANT_UUID.toString()));

            tenantFilter.doFilterInternal(request, response, filterChain);

            assertEquals(400, response.getStatus());
            assertTrue(response.getContentAsString().contains("OperationOutcome"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 403 for TenantDisabledException from service")
        void shouldPropagateTenantDisabledException() throws ServletException, IOException {
            request.addHeader("X-Tenant-ID", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenThrow(new TenantDisabledException(TENANT_UUID.toString()));

            tenantFilter.doFilterInternal(request, response, filterChain);

            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("OperationOutcome"));
            verify(filterChain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should use custom header name from properties")
        void shouldUseCustomHeaderName() throws ServletException, IOException {
            tenantProperties.setHeaderName("X-Custom-Tenant");
            request.addHeader("X-Custom-Tenant", TENANT_UUID.toString());
            when(tenantService.resolveEffectiveTenantId(TENANT_UUID.toString()))
                    .thenReturn("hosp-a");

            tenantFilter.doFilterInternal(request, response, filterChain);

            verify(tenantService).resolveEffectiveTenantId(TENANT_UUID.toString());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("shouldNotFilter")
    class ShouldNotFilter {

        @Test
        @DisplayName("should skip actuator endpoints")
        void shouldSkipActuatorEndpoints() {
            request.setRequestURI("/actuator/health");
            assertTrue(tenantFilter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("should skip actuator base path")
        void shouldSkipActuatorBasePath() {
            request.setRequestURI("/actuator");
            assertTrue(tenantFilter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("should not skip FHIR endpoints")
        void shouldNotSkipFhirEndpoints() {
            request.setRequestURI("/fhir/Patient");
            assertFalse(tenantFilter.shouldNotFilter(request));
        }

        @Test
        @DisplayName("should not skip versioned FHIR endpoints")
        void shouldNotSkipVersionedFhirEndpoints() {
            request.setRequestURI("/fhir/r5/Patient");
            assertFalse(tenantFilter.shouldNotFilter(request));
        }
    }
}
