package org.fhirframework.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Bootstrap configuration for AWS RDS deployments.
 *
 * This configuration runs before the main DataSource is created and handles:
 * 1. Creating the IAM authentication user if it doesn't exist
 * 2. Granting the rds_iam role to enable IAM authentication
 *
 * This solves the chicken-and-egg problem where:
 * - The app needs to connect as fhir4java_app with IAM auth
 * - But that user doesn't exist on a fresh RDS instance
 * - We need to connect as the master user first to create it
 */
@Configuration
@Profile("aws")
@ConditionalOnProperty(name = "DB_AUTO_INIT", havingValue = "true")
public class DatabaseBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrapConfig.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${RDS_ENDPOINT}")
    private String rdsEndpoint;

    @Value("${RDS_PORT:5432}")
    private int rdsPort;

    @Value("${RDS_DATABASE}")
    private String rdsDatabase;

    @Value("${RDS_USERNAME}")
    private String iamUsername;

    @Value("${FHIR4JAVA_DB_RDS_SECRET_NAME:#{null}}")
    private String rdsSecretName;

    @PostConstruct
    public void bootstrapDatabase() {
        if (rdsSecretName == null || rdsSecretName.isEmpty()) {
            log.info("RDS secret name not configured, skipping IAM user bootstrap");
            return;
        }

        log.info("Bootstrap config - RDS endpoint: {}, port: {}, database: {}, IAM user: {}, secret: {}",
                rdsEndpoint, rdsPort, rdsDatabase, iamUsername, rdsSecretName);

        try {
            log.info("Starting database bootstrap for IAM user: {}", iamUsername);

            // Get master credentials from Secrets Manager
            log.info("Retrieving master credentials from Secrets Manager...");
            MasterCredentials creds = getMasterCredentials();
            log.info("Retrieved master credentials for user: {}", creds.username());

            // Connect as master user and create IAM user
            createIamUserIfNotExists(creds);

            log.info("Database bootstrap completed successfully");
        } catch (Exception e) {
            log.error("Database bootstrap failed - Error: {} - Cause: {}",
                    e.getMessage(),
                    e.getCause() != null ? e.getCause().getMessage() : "none");
            log.error("Full stack trace:", e);
            // Don't throw - let the app try to start anyway
            // If the user already exists, it will work
        }
    }

    private MasterCredentials getMasterCredentials() {
        log.info("Creating Secrets Manager client...");
        try (SecretsManagerClient client = SecretsManagerClient.create()) {
            log.info("Fetching secret: {}", rdsSecretName);
            GetSecretValueResponse response = client.getSecretValue(
                GetSecretValueRequest.builder()
                    .secretId(rdsSecretName)
                    .build()
            );

            String secretString = response.secretString();
            log.info("Secret retrieved, parsing JSON...");
            JsonNode json = objectMapper.readTree(secretString);

            String username = json.get("username").asText();
            String password = json.get("password").asText();
            log.info("Parsed credentials for user: {}, password length: {}", username, password.length());

            return new MasterCredentials(username, password);
        } catch (Exception e) {
            log.error("Failed to retrieve RDS master credentials: {}", e.getMessage());
            throw new RuntimeException("Failed to retrieve RDS master credentials from Secrets Manager", e);
        }
    }

    private void createIamUserIfNotExists(MasterCredentials creds) {
        // RDS requires SSL connections - use sslmode=require
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=require",
                rdsEndpoint, rdsPort, rdsDatabase);
        log.info("Connecting to database with URL: {}", jdbcUrl);
        log.info("Connecting as master user: {}", creds.username());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, creds.username(), creds.password());
             Statement stmt = conn.createStatement()) {

            log.info("Successfully connected to database");

            // Check if IAM user is the same as master user
            boolean isSameAsMaster = creds.username().equals(iamUsername);
            log.info("IAM user same as master: {}", isSameAsMaster);

            // Check if user exists
            boolean userExists = false;
            try (ResultSet rs = stmt.executeQuery(
                    String.format("SELECT 1 FROM pg_roles WHERE rolname = '%s'", iamUsername))) {
                userExists = rs.next();
            }
            log.info("User '{}' exists: {}", iamUsername, userExists);

            if (!userExists) {
                log.info("Creating IAM user: {}", iamUsername);
                stmt.execute(String.format("CREATE USER %s", iamUsername));
                log.info("Created user {}", iamUsername);
                stmt.execute(String.format("GRANT rds_iam TO %s", iamUsername));
                log.info("Granted rds_iam role to {}", iamUsername);
            } else {
                log.info("IAM user already exists: {}", iamUsername);
                // Ensure rds_iam role is granted
                stmt.execute(String.format("GRANT rds_iam TO %s", iamUsername));
                log.info("Ensured rds_iam role is granted to {}", iamUsername);
            }

            // Only grant additional permissions if IAM user is different from master
            // Master user already has all privileges
            if (!isSameAsMaster) {
                log.info("Granting additional permissions to IAM user...");
                stmt.execute(String.format("ALTER USER %s CREATEDB", iamUsername));
                stmt.execute(String.format("GRANT ALL PRIVILEGES ON DATABASE %s TO %s", rdsDatabase, iamUsername));
                log.info("Granted CREATEDB and database privileges to {}", iamUsername);
            } else {
                log.info("IAM user is master user, skipping additional grants (already has privileges)");
            }

            log.info("IAM user bootstrap complete: {}", iamUsername);

        } catch (Exception e) {
            log.error("Failed to create/configure IAM user: {}", e.getMessage());
            throw new RuntimeException("Failed to create IAM user", e);
        }
    }

    private record MasterCredentials(String username, String password) {}
}
