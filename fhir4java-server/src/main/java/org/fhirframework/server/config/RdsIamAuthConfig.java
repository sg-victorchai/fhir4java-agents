package org.fhirframework.server.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("aws-iam")
public class RdsIamAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(RdsIamAuthConfig.class);
    private static final long TOKEN_REFRESH_INTERVAL_MINUTES = 10;

    @Value("${RDS_ENDPOINT}")
    private String rdsEndpoint;

    @Value("${RDS_PORT:5432}")
    private int rdsPort;

    @Value("${RDS_USERNAME:fhir4java_app}")
    private String rdsUsername;

    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Generate initial IAM auth token
        refreshToken(dataSource);

        // Schedule token refresh every 10 minutes (tokens expire after 15 minutes)
        scheduler.scheduleAtFixedRate(
                () -> refreshToken(dataSource),
                TOKEN_REFRESH_INTERVAL_MINUTES,
                TOKEN_REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        return dataSource;
    }

    private void refreshToken(HikariDataSource dataSource) {
        try {
            String authToken = generateAuthToken();
            dataSource.setPassword(authToken);
            log.info("Successfully refreshed RDS IAM authentication token");
        } catch (Exception e) {
            log.error("Failed to refresh RDS IAM authentication token", e);
        }
    }

    String generateAuthToken() {
        RdsUtilities rdsUtilities = RdsUtilities.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        GenerateAuthenticationTokenRequest request = GenerateAuthenticationTokenRequest.builder()
                .hostname(rdsEndpoint)
                .port(rdsPort)
                .username(rdsUsername)
                .build();

        return rdsUtilities.generateAuthenticationToken(request);
    }
}
