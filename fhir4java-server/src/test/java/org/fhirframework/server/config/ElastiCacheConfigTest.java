package org.fhirframework.server.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ElastiCacheConfigTest {

    @Test
    void shouldCreateClusterTopologyRefreshOptions() {
        // Given/When
        ClusterTopologyRefreshOptions options = ClusterTopologyRefreshOptions.builder()
                .enableAdaptiveRefreshTrigger(
                        ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                        ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS
                )
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .build();

        // Then
        assertThat(options).isNotNull();
        assertThat(options.useDynamicRefreshSources()).isTrue();
    }

    @Test
    void shouldCreateClusterClientOptions() {
        // Given
        ClusterTopologyRefreshOptions topologyOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .build();

        // When
        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyOptions)
                .build();

        // Then
        assertThat(clientOptions).isNotNull();
    }
}
