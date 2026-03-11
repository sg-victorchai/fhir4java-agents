package org.fhirframework.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointFilterConfigurationTest {

    @ParameterizedTest
    @CsvSource({
        "fhir, /fhir/r5/Patient/123, 200",
        "fhir, /fhir/r5/metadata, 200",
        "fhir, /actuator/health, 200",
        "fhir, /actuator/metrics, 404",
        "fhir, /api/admin/tenants, 404",
        "metadata, /fhir/r5/metadata, 200",
        "metadata, /fhir/r4b/metadata, 200",
        "metadata, /fhir/r5/Patient/123, 404",
        "metadata, /actuator/health, 200",
        "actuator, /actuator/health, 200",
        "actuator, /actuator/metrics, 200",
        "actuator, /fhir/r5/Patient/123, 404",
        "admin, /api/admin/tenants, 200",
        "admin, /fhir/r5/Patient/123, 404",
        "admin, /actuator/health, 200",
        "all, /fhir/r5/Patient/123, 200",
        "all, /actuator/metrics, 200",
        "all, /api/admin/tenants, 200"
    })
    void shouldFilterEndpointsBasedOnConfiguration(String enabledEndpoints, String path, int expectedStatus)
            throws Exception {
        // Given
        var filter = new EndpointFilterConfiguration.EndpointFilter(enabledEndpoints);
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        if (expectedStatus == 200) {
            assertThat(chain.getRequest()).isNotNull();
        } else {
            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Test
    void shouldAlwaysAllowHealthEndpoint() throws Exception {
        // Given - even with restrictive config, health should pass
        var filter = new EndpointFilterConfiguration.EndpointFilter("admin");
        var request = new MockHttpServletRequest("GET", "/actuator/health/liveness");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(chain.getRequest()).isNotNull();
    }
}
