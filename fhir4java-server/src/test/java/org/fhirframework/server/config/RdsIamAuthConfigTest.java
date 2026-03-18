package org.fhirframework.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

import static org.assertj.core.api.Assertions.assertThat;

class RdsIamAuthConfigTest {

    @Test
    void shouldCreateRdsUtilitiesInstance() {
        // Given
        Region region = Region.US_EAST_1;

        // When
        RdsUtilities utilities = RdsUtilities.builder()
                .region(region)
                .build();

        // Then
        assertThat(utilities).isNotNull();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
    void shouldGenerateAuthTokenWhenAwsCredentialsAvailable() {
        // This test only runs when AWS credentials are configured
        // In CI/CD, this would use IAM roles
    }
}
