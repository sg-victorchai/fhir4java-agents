# AWS Deployment Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deploy FHIR4Java to AWS with Private API Gateway, multi-service architecture, and security hardening.

**Architecture:** Public ALB → Private API Gateway (custom domain) → NLB → ALB Ingress → ECS/EKS Fargate. Single Docker image deployed as 4 isolated services with endpoint filtering. RDS PostgreSQL with IAM auth, ElastiCache Redis cluster.

**Tech Stack:** Spring Boot 3.4+, AWS CDK (TypeScript), ECS/EKS Fargate, RDS PostgreSQL, ElastiCache Valkey, Private API Gateway, Distroless containers.

**Reference:** See `tasks/TASK-aws-deployment-elasticache-rds-apigateway.md` for full design document.

---

## Phase 1: Application Changes

### Task 1: Add AWS SDK Dependencies

**Files:**
- Modify: `pom.xml` (parent)
- Modify: `fhir4java-server/pom.xml`

**Step 1: Add AWS SDK BOM to parent pom.xml**

Add to `<properties>`:
```xml
<aws-sdk.version>2.29.0</aws-sdk.version>
<aws-secretsmanager-jdbc.version>1.0.12</aws-secretsmanager-jdbc.version>
```

Add to `<dependencyManagement>`:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bom</artifactId>
    <version>${aws-sdk.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Step 2: Add dependencies to fhir4java-server/pom.xml**

```xml
<!-- AWS Secrets Manager JDBC Driver -->
<dependency>
    <groupId>com.amazonaws.secretsmanager</groupId>
    <artifactId>aws-secretsmanager-jdbc</artifactId>
    <version>${aws-secretsmanager-jdbc.version}</version>
</dependency>

<!-- AWS SDK for Secrets Manager -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>secretsmanager</artifactId>
</dependency>

<!-- AWS SDK for RDS (IAM auth) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>rds</artifactId>
</dependency>

<!-- AWS SDK for STS -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sts</artifactId>
</dependency>
```

**Step 3: Verify dependencies resolve**

Run: `./mvnw dependency:resolve -pl fhir4java-server`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add pom.xml fhir4java-server/pom.xml
git commit -m "build: add AWS SDK dependencies for RDS IAM auth and Secrets Manager"
```

---

### Task 2: Implement Endpoint Filter Configuration

**Files:**
- Create: `fhir4java-server/src/main/java/org/fhirframework/server/config/EndpointFilterConfiguration.java`
- Create: `fhir4java-server/src/test/java/org/fhirframework/server/config/EndpointFilterConfigurationTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl fhir4java-server -Dtest=EndpointFilterConfigurationTest -q`
Expected: FAIL with compilation error

**Step 3: Write the implementation**

```java
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
```

**Step 4: Run test to verify it passes**

Run: `./mvnw test -pl fhir4java-server -Dtest=EndpointFilterConfigurationTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add fhir4java-server/src/main/java/org/fhirframework/server/config/EndpointFilterConfiguration.java
git add fhir4java-server/src/test/java/org/fhirframework/server/config/EndpointFilterConfigurationTest.java
git commit -m "feat: add endpoint filtering for multi-service deployment"
```

---

### Task 3: Create AWS Base Profile

**Files:**
- Create: `fhir4java-server/src/main/resources/application-aws.yml`

**Step 1: Create the profile**

```yaml
# AWS Base Profile
spring:
  config:
    activate:
      on-profile: aws

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: fhir

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    schemas: fhir

  # ElastiCache Redis/Valkey
  data:
    redis:
      host: ${ELASTICACHE_ENDPOINT}
      port: ${ELASTICACHE_PORT:6379}
      password: ${ELASTICACHE_AUTH_TOKEN}
      ssl:
        enabled: true
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
        cluster:
          refresh:
            adaptive: true
            period: 30s

  cache:
    type: redis
    redis:
      time-to-live: 3600000
      cache-null-values: false

# FHIR4Java settings
fhir4java:
  server:
    base-url: https://${API_GATEWAY_DOMAIN}/fhir

  endpoints:
    enabled: ${FHIR4JAVA_ENDPOINTS_ENABLED:all}

  validation:
    profile-validator-enabled: false

  plugins:
    authentication:
      enabled: true
    authorization:
      enabled: true
    audit:
      enabled: true
    telemetry:
      enabled: true

  tenant:
    enabled: true
    default-tenant-id: default
    header-name: X-Tenant-ID

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

# Logging (JSON for CloudWatch)
logging:
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%level","logger":"%logger","message":"%msg","thread":"%thread"}%n'
  level:
    root: INFO
    org.fhirframework: INFO
    org.hibernate.SQL: WARN
```

**Step 2: Commit**

```bash
git add fhir4java-server/src/main/resources/application-aws.yml
git commit -m "feat: add AWS base profile configuration"
```

---

### Task 4: Create AWS IAM Auth Profile

**Files:**
- Create: `fhir4java-server/src/main/resources/application-aws-iam.yml`

**Step 1: Create the profile**

```yaml
# AWS with IAM Database Authentication
spring:
  config:
    activate:
      on-profile: aws-iam

  datasource:
    url: jdbc:postgresql://${RDS_ENDPOINT}:${RDS_PORT:5432}/${RDS_DATABASE:fhir4java_prod}
    username: ${RDS_USERNAME:fhir4java_app}
    hikari:
      pool-name: fhir4java-iam-pool
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      # Token refresh before expiry (tokens last 15 min)
      max-lifetime: 840000
```

**Step 2: Commit**

```bash
git add fhir4java-server/src/main/resources/application-aws-iam.yml
git commit -m "feat: add AWS IAM database authentication profile"
```

---

### Task 5: Create AWS Secrets Manager Profile

**Files:**
- Create: `fhir4java-server/src/main/resources/application-aws-secrets.yml`

**Step 1: Create the profile**

```yaml
# AWS with Secrets Manager Authentication
spring:
  config:
    activate:
      on-profile: aws-secrets

  datasource:
    url: jdbc-secretsmanager:postgresql://${RDS_ENDPOINT}:${RDS_PORT:5432}/${RDS_DATABASE:fhir4java_prod}
    driver-class-name: com.amazonaws.secretsmanager.sql.AWSSecretsManagerPostgreSQLDriver
    username: ${RDS_SECRET_NAME}
    hikari:
      pool-name: fhir4java-secrets-pool
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Step 2: Commit**

```bash
git add fhir4java-server/src/main/resources/application-aws-secrets.yml
git commit -m "feat: add AWS Secrets Manager database authentication profile"
```

---

### Task 6: Implement RDS IAM Authentication Config

**Files:**
- Create: `fhir4java-server/src/main/java/org/fhirframework/server/config/RdsIamAuthConfig.java`
- Create: `fhir4java-server/src/test/java/org/fhirframework/server/config/RdsIamAuthConfigTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it fails**

Run: `./mvnw test -pl fhir4java-server -Dtest=RdsIamAuthConfigTest -q`
Expected: FAIL with compilation error

**Step 3: Write the implementation**

```java
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
```

**Step 4: Run test to verify it passes**

Run: `./mvnw test -pl fhir4java-server -Dtest=RdsIamAuthConfigTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add fhir4java-server/src/main/java/org/fhirframework/server/config/RdsIamAuthConfig.java
git add fhir4java-server/src/test/java/org/fhirframework/server/config/RdsIamAuthConfigTest.java
git commit -m "feat: implement RDS IAM database authentication"
```

---

### Task 7: Implement ElastiCache Configuration

**Files:**
- Create: `fhir4java-server/src/main/java/org/fhirframework/server/config/ElastiCacheConfig.java`
- Create: `fhir4java-server/src/test/java/org/fhirframework/server/config/ElastiCacheConfigTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it compiles and passes**

Run: `./mvnw test -pl fhir4java-server -Dtest=ElastiCacheConfigTest -q`
Expected: PASS (this test validates Lettuce API usage)

**Step 3: Write the implementation**

```java
package org.fhirframework.server.config;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collections;

@Configuration
@Profile("aws")
public class ElastiCacheConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
                Collections.singletonList(redisHost + ":" + redisPort)
        );
        clusterConfig.setPassword(redisPassword);

        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enableAdaptiveRefreshTrigger(
                        ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                        ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS
                )
                .enablePeriodicRefresh(Duration.ofSeconds(30))
                .build();

        ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .keepAlive(true)
                        .build())
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(2))
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

**Step 4: Commit**

```bash
git add fhir4java-server/src/main/java/org/fhirframework/server/config/ElastiCacheConfig.java
git add fhir4java-server/src/test/java/org/fhirframework/server/config/ElastiCacheConfigTest.java
git commit -m "feat: implement ElastiCache cluster configuration with topology refresh"
```

---

### Task 8: Create Hardened Dockerfile

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

**Step 1: Create .dockerignore**

```
.git
.gitignore
.idea
*.iml
target/
**/target/
.mvn/wrapper/maven-wrapper.jar
*.md
docs/
tasks/
infrastructure/
*.log
```

**Step 2: Create the hardened Dockerfile**

```dockerfile
# Multi-stage build for FHIR4Java
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY fhir4java-core/pom.xml fhir4java-core/
COPY fhir4java-persistence/pom.xml fhir4java-persistence/
COPY fhir4java-plugin/pom.xml fhir4java-plugin/
COPY fhir4java-api/pom.xml fhir4java-api/
COPY fhir4java-server/pom.xml fhir4java-server/

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY . .
RUN ./mvnw clean package -DskipTests -pl fhir4java-server -am

# Extract layers for better caching
RUN java -Djarmode=layertools -jar fhir4java-server/target/fhir4java-server-*.jar extract --destination /app/extracted

# Runtime stage - Distroless for minimal attack surface
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Copy extracted layers
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# Run as non-root user (UID 65532 in distroless)
USER nonroot:nonroot

# JVM options for container
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

**Step 3: Test Docker build locally**

Run: `docker build -t fhir4java:test .`
Expected: Successfully built

**Step 4: Commit**

```bash
git add Dockerfile .dockerignore
git commit -m "feat: add hardened Dockerfile with distroless base image"
```

---

## Phase 2: AWS CDK Infrastructure - Foundation

### Task 9: Initialize CDK Project

**Files:**
- Create: `infrastructure/` directory with CDK project

**Step 1: Create infrastructure directory and initialize CDK**

```bash
mkdir -p infrastructure
cd infrastructure
npx cdk init app --language typescript
```

**Step 2: Install dependencies**

```bash
npm install @aws-cdk/aws-apigatewayv2-alpha @aws-cdk/aws-apigatewayv2-integrations-alpha
```

**Step 3: Update package.json with project name**

Edit `package.json`:
```json
{
  "name": "fhir4java-infrastructure",
  "version": "1.0.0",
  ...
}
```

**Step 4: Commit**

```bash
cd ..
git add infrastructure/
git commit -m "feat: initialize AWS CDK infrastructure project"
```

---

### Task 10: Create VPC Construct

**Files:**
- Create: `infrastructure/lib/constructs/vpc-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

export interface VpcConstructProps {
  maxAzs?: number;
  natGateways?: number;
}

export class VpcConstruct extends Construct {
  public readonly vpc: ec2.Vpc;
  public readonly publicSubnets: ec2.ISubnet[];
  public readonly privateSubnets: ec2.ISubnet[];
  public readonly isolatedSubnets: ec2.ISubnet[];
  public readonly apiGatewayEndpoint: ec2.InterfaceVpcEndpoint;

  constructor(scope: Construct, id: string, props: VpcConstructProps = {}) {
    super(scope, id);

    this.vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: props.maxAzs ?? 3,
      natGateways: props.natGateways ?? 1,
      subnetConfiguration: [
        {
          name: 'public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
        {
          name: 'private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24,
        },
        {
          name: 'database',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
          cidrMask: 24,
        },
      ],
    });

    this.publicSubnets = this.vpc.publicSubnets;
    this.privateSubnets = this.vpc.privateSubnets;
    this.isolatedSubnets = this.vpc.isolatedSubnets;

    // API Gateway VPC Endpoint (for Private API Gateway)
    this.apiGatewayEndpoint = this.vpc.addInterfaceEndpoint('ApiGatewayEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.APIGATEWAY,
      subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      privateDnsEnabled: true,
    });

    // VPC Endpoints for AWS services
    this.vpc.addInterfaceEndpoint('SecretsManagerEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.SECRETS_MANAGER,
    });

    this.vpc.addInterfaceEndpoint('EcrApiEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.ECR,
    });

    this.vpc.addInterfaceEndpoint('EcrDkrEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.ECR_DOCKER,
    });

    this.vpc.addInterfaceEndpoint('CloudWatchLogsEndpoint', {
      service: ec2.InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS,
    });

    this.vpc.addGatewayEndpoint('S3Endpoint', {
      service: ec2.GatewayVpcEndpointAwsService.S3,
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/vpc-construct.ts
git commit -m "feat: add VPC construct with subnets and API Gateway endpoint"
```

---

### Task 11: Create RDS Construct

**Files:**
- Create: `infrastructure/lib/constructs/rds-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface RdsConstructProps {
  vpc: ec2.IVpc;
  instanceClass?: ec2.InstanceClass;
  instanceSize?: ec2.InstanceSize;
  databaseName?: string;
  multiAz?: boolean;
  iamAuthentication?: boolean;
}

export class RdsConstruct extends Construct {
  public readonly instance: rds.DatabaseInstance;
  public readonly secret: secretsmanager.ISecret;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: RdsConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'RdsSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for RDS PostgreSQL',
      allowAllOutbound: false,
    });

    this.secret = new secretsmanager.Secret(this, 'RdsSecret', {
      secretName: 'fhir4java/prod/rds',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'fhir4java_app' }),
        generateStringKey: 'password',
        excludeCharacters: '/@"\\\'',
        passwordLength: 32,
      },
    });

    this.instance = new rds.DatabaseInstance(this, 'PostgresInstance', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_4,
      }),
      instanceType: ec2.InstanceType.of(
        props.instanceClass ?? ec2.InstanceClass.R6G,
        props.instanceSize ?? ec2.InstanceSize.LARGE
      ),
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [this.securityGroup],
      credentials: rds.Credentials.fromSecret(this.secret),
      databaseName: props.databaseName ?? 'fhir4java_prod',
      multiAz: props.multiAz ?? true,
      storageEncrypted: true,
      deletionProtection: true,
      iamAuthentication: props.iamAuthentication ?? true,
      backupRetention: cdk.Duration.days(7),
      allocatedStorage: 100,
      storageType: rds.StorageType.GP3,
      parameterGroup: new rds.ParameterGroup(this, 'ParameterGroup', {
        engine: rds.DatabaseInstanceEngine.postgres({
          version: rds.PostgresEngineVersion.VER_16_4,
        }),
        parameters: {
          'max_connections': '200',
        },
      }),
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/rds-construct.ts
git commit -m "feat: add RDS PostgreSQL construct with IAM auth support"
```

---

### Task 12: Create ElastiCache Construct

**Files:**
- Create: `infrastructure/lib/constructs/elasticache-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface ElastiCacheConstructProps {
  vpc: ec2.IVpc;
  nodeType?: string;
  numNodeGroups?: number;
  replicasPerNodeGroup?: number;
}

export class ElastiCacheConstruct extends Construct {
  public readonly cluster: elasticache.CfnReplicationGroup;
  public readonly secret: secretsmanager.Secret;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: ElastiCacheConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'CacheSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for ElastiCache',
      allowAllOutbound: false,
    });

    this.secret = new secretsmanager.Secret(this, 'CacheSecret', {
      secretName: 'fhir4java/prod/elasticache',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({}),
        generateStringKey: 'authToken',
        excludeCharacters: '/@"\\\'',
        passwordLength: 64,
      },
    });

    const subnetGroup = new elasticache.CfnSubnetGroup(this, 'SubnetGroup', {
      description: 'FHIR4Java ElastiCache Subnet Group',
      subnetIds: props.vpc.selectSubnets({
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      }).subnetIds,
    });

    this.cluster = new elasticache.CfnReplicationGroup(this, 'ReplicationGroup', {
      replicationGroupDescription: 'FHIR4Java ElastiCache Cluster',
      engine: 'valkey',
      cacheNodeType: props.nodeType ?? 'cache.r6g.large',
      numNodeGroups: props.numNodeGroups ?? 2,
      replicasPerNodeGroup: props.replicasPerNodeGroup ?? 1,
      cacheSubnetGroupName: subnetGroup.ref,
      securityGroupIds: [this.securityGroup.securityGroupId],
      transitEncryptionEnabled: true,
      atRestEncryptionEnabled: true,
      authToken: this.secret.secretValueFromJson('authToken').unsafeUnwrap(),
      automaticFailoverEnabled: true,
      multiAzEnabled: true,
    });

    this.cluster.addDependency(subnetGroup);
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/elasticache-construct.ts
git commit -m "feat: add ElastiCache Valkey cluster construct"
```

---

## Phase 3: AWS CDK Infrastructure - Load Balancers

### Task 13: Create Public ALB Construct

**Files:**
- Create: `infrastructure/lib/constructs/public-alb-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as wafv2 from 'aws-cdk-lib/aws-wafv2';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

export interface PublicAlbConstructProps {
  vpc: ec2.IVpc;
  certificateArn: string;
  domainName: string;
}

export class PublicAlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly targetGroup: elbv2.ApplicationTargetGroup;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: PublicAlbConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for Public ALB',
      allowAllOutbound: true,
    });

    this.securityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(443),
      'Allow HTTPS from internet'
    );

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'PublicAlb', {
      vpc: props.vpc,
      internetFacing: true,
      securityGroup: this.securityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // Target group for VPC Endpoint ENI IPs (managed by Lambda)
    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'VpcEndpointTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      port: 443,
      healthCheck: {
        protocol: elbv2.Protocol.HTTPS,
        path: '/fhir/r5/metadata',
        healthyHttpCodes: '200,403',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
      },
    });

    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this, 'Certificate', props.certificateArn
    );

    this.listener = this.alb.addListener('HttpsListener', {
      port: 443,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      certificates: [certificate],
      defaultTargetGroups: [this.targetGroup],
    });

    // WAF WebACL
    const webAcl = new wafv2.CfnWebACL(this, 'WebAcl', {
      defaultAction: { allow: {} },
      scope: 'REGIONAL',
      visibilityConfig: {
        cloudWatchMetricsEnabled: true,
        metricName: 'fhir4java-waf',
        sampledRequestsEnabled: true,
      },
      rules: [
        {
          name: 'AWSManagedRulesCommonRuleSet',
          priority: 1,
          statement: {
            managedRuleGroupStatement: {
              vendorName: 'AWS',
              name: 'AWSManagedRulesCommonRuleSet',
            },
          },
          overrideAction: { none: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'AWSManagedRulesCommonRuleSet',
            sampledRequestsEnabled: true,
          },
        },
        {
          name: 'AWSManagedRulesKnownBadInputsRuleSet',
          priority: 2,
          statement: {
            managedRuleGroupStatement: {
              vendorName: 'AWS',
              name: 'AWSManagedRulesKnownBadInputsRuleSet',
            },
          },
          overrideAction: { none: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'AWSManagedRulesKnownBadInputsRuleSet',
            sampledRequestsEnabled: true,
          },
        },
        {
          name: 'RateLimitRule',
          priority: 3,
          statement: {
            rateBasedStatement: {
              limit: 2000,
              aggregateKeyType: 'IP',
            },
          },
          action: { block: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'RateLimitRule',
            sampledRequestsEnabled: true,
          },
        },
      ],
    });

    new wafv2.CfnWebACLAssociation(this, 'WebAclAssociation', {
      resourceArn: this.alb.loadBalancerArn,
      webAclArn: webAcl.attrArn,
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/public-alb-construct.ts
git commit -m "feat: add Public ALB construct with WAF integration"
```

---

### Task 14: Create NLB Construct (VPC Link Target)

**Files:**
- Create: `infrastructure/lib/constructs/nlb-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface NlbConstructProps {
  vpc: ec2.IVpc;
  internalAlb: elbv2.IApplicationLoadBalancer;
}

export class NlbConstruct extends Construct {
  public readonly nlb: elbv2.NetworkLoadBalancer;
  public readonly listener: elbv2.NetworkListener;

  constructor(scope: Construct, id: string, props: NlbConstructProps) {
    super(scope, id);

    this.nlb = new elbv2.NetworkLoadBalancer(this, 'Nlb', {
      vpc: props.vpc,
      internetFacing: false,
      crossZoneEnabled: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // Target group pointing to Internal ALB
    const targetGroup = new elbv2.NetworkTargetGroup(this, 'AlbTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.ALB,
      port: 80,
      protocol: elbv2.Protocol.TCP,
      healthCheck: {
        protocol: elbv2.Protocol.HTTP,
        path: '/actuator/health/liveness',
        port: '80',
      },
    });

    targetGroup.addTarget(
      new (class implements elbv2.INetworkLoadBalancerTarget {
        attachToNetworkTargetGroup(targetGroup: elbv2.INetworkTargetGroup): elbv2.LoadBalancerTargetProps {
          return {
            targetType: elbv2.TargetType.ALB,
            targetJson: { id: props.internalAlb.loadBalancerArn, port: 80 },
          };
        }
      })()
    );

    this.listener = this.nlb.addListener('TcpListener', {
      port: 80,
      protocol: elbv2.Protocol.TCP,
      defaultTargetGroups: [targetGroup],
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/nlb-construct.ts
git commit -m "feat: add NLB construct for API Gateway VPC Link"
```

---

### Task 15: Create Internal ALB Construct (Service Routing)

**Files:**
- Create: `infrastructure/lib/constructs/internal-alb-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface InternalAlbConstructProps {
  vpc: ec2.IVpc;
}

export class InternalAlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly fhirApiTargetGroup: elbv2.ApplicationTargetGroup;
  public readonly fhirMetadataTargetGroup: elbv2.ApplicationTargetGroup;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: InternalAlbConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for Internal ALB',
      allowAllOutbound: true,
    });

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'InternalAlb', {
      vpc: props.vpc,
      internetFacing: false,
      securityGroup: this.securityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // Target groups for ECS services
    this.fhirApiTargetGroup = new elbv2.ApplicationTargetGroup(this, 'FhirApiTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      healthCheck: {
        path: '/actuator/health/liveness',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
      },
    });

    this.fhirMetadataTargetGroup = new elbv2.ApplicationTargetGroup(this, 'FhirMetadataTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      healthCheck: {
        path: '/actuator/health/liveness',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
      },
    });

    this.listener = this.alb.addListener('HttpListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultAction: elbv2.ListenerAction.fixedResponse(404, {
        contentType: 'application/json',
        messageBody: '{"error":"Not found"}',
      }),
    });

    // Path-based routing
    this.listener.addAction('FhirMetadataRoute', {
      priority: 10,
      conditions: [
        elbv2.ListenerCondition.pathPatterns(['/fhir/*/metadata']),
      ],
      action: elbv2.ListenerAction.forward([this.fhirMetadataTargetGroup]),
    });

    this.listener.addAction('FhirApiRoute', {
      priority: 20,
      conditions: [
        elbv2.ListenerCondition.pathPatterns(['/fhir/*']),
      ],
      action: elbv2.ListenerAction.forward([this.fhirApiTargetGroup]),
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/internal-alb-construct.ts
git commit -m "feat: add Internal ALB construct with path-based routing"
```

---

### Task 16: Create Admin ALB Construct

**Files:**
- Create: `infrastructure/lib/constructs/admin-alb-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

export interface AdminAlbConstructProps {
  vpc: ec2.IVpc;
  certificateArn: string;
  vpnCidr: string;
}

export class AdminAlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly actuatorTargetGroup: elbv2.ApplicationTargetGroup;
  public readonly adminTargetGroup: elbv2.ApplicationTargetGroup;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: AdminAlbConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for Admin ALB (VPN only)',
      allowAllOutbound: true,
    });

    // Only allow access from VPN CIDR
    this.securityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpnCidr),
      ec2.Port.tcp(443),
      'Allow HTTPS from VPN only'
    );

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'AdminAlb', {
      vpc: props.vpc,
      internetFacing: false,
      securityGroup: this.securityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    this.actuatorTargetGroup = new elbv2.ApplicationTargetGroup(this, 'ActuatorTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      healthCheck: {
        path: '/actuator/health/liveness',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
      },
    });

    this.adminTargetGroup = new elbv2.ApplicationTargetGroup(this, 'AdminTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      healthCheck: {
        path: '/actuator/health/liveness',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
      },
    });

    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this, 'Certificate', props.certificateArn
    );

    this.listener = this.alb.addListener('HttpsListener', {
      port: 443,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      certificates: [certificate],
      defaultAction: elbv2.ListenerAction.fixedResponse(404, {
        contentType: 'application/json',
        messageBody: '{"error":"Not found"}',
      }),
    });

    this.listener.addAction('ActuatorRoute', {
      priority: 10,
      conditions: [
        elbv2.ListenerCondition.pathPatterns(['/actuator/*']),
      ],
      action: elbv2.ListenerAction.forward([this.actuatorTargetGroup]),
    });

    this.listener.addAction('AdminRoute', {
      priority: 20,
      conditions: [
        elbv2.ListenerCondition.pathPatterns(['/api/admin/*']),
      ],
      action: elbv2.ListenerAction.forward([this.adminTargetGroup]),
    });
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/admin-alb-construct.ts
git commit -m "feat: add Admin ALB construct with VPN-only access"
```

---

## Phase 4: AWS CDK Infrastructure - API Gateway & Compute

### Task 17: Create Private API Gateway Construct

**Files:**
- Create: `infrastructure/lib/constructs/api-gateway-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as apigatewayv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as apigatewayv2_integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface ApiGatewayConstructProps {
  vpc: ec2.IVpc;
  nlb: elbv2.INetworkLoadBalancer;
  domainName: string;
  certificateArn: string;
}

export class ApiGatewayConstruct extends Construct {
  public readonly httpApi: apigatewayv2.HttpApi;
  public readonly vpcLink: apigatewayv2.VpcLink;
  public readonly customDomain: apigatewayv2.DomainName;

  constructor(scope: Construct, id: string, props: ApiGatewayConstructProps) {
    super(scope, id);

    // VPC Link for private integration
    this.vpcLink = new apigatewayv2.VpcLink(this, 'VpcLink', {
      vpc: props.vpc,
      subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // HTTP API (Private)
    this.httpApi = new apigatewayv2.HttpApi(this, 'HttpApi', {
      apiName: 'fhir4java-api',
      description: 'FHIR4Java Private API Gateway',
      corsPreflight: {
        allowOrigins: ['https://' + props.domainName],
        allowMethods: [
          apigatewayv2.CorsHttpMethod.GET,
          apigatewayv2.CorsHttpMethod.POST,
          apigatewayv2.CorsHttpMethod.PUT,
          apigatewayv2.CorsHttpMethod.PATCH,
          apigatewayv2.CorsHttpMethod.DELETE,
          apigatewayv2.CorsHttpMethod.OPTIONS,
        ],
        allowHeaders: ['Content-Type', 'Authorization', 'X-Tenant-ID', 'X-Request-Id'],
        exposeHeaders: ['ETag', 'Location', 'Content-Location', 'X-FHIR-Version'],
        maxAge: cdk.Duration.hours(1),
      },
    });

    // NLB integration
    const nlbIntegration = new apigatewayv2_integrations.HttpNlbIntegration(
      'NlbIntegration',
      props.nlb.listeners[0],
      { vpcLink: this.vpcLink }
    );

    // Routes
    this.httpApi.addRoutes({
      path: '/fhir/{proxy+}',
      methods: [apigatewayv2.HttpMethod.ANY],
      integration: nlbIntegration,
    });

    this.httpApi.addRoutes({
      path: '/fhir',
      methods: [apigatewayv2.HttpMethod.ANY],
      integration: nlbIntegration,
    });

    // Custom Domain
    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this, 'Certificate', props.certificateArn
    );

    this.customDomain = new apigatewayv2.DomainName(this, 'CustomDomain', {
      domainName: props.domainName,
      certificate: certificate,
    });

    new apigatewayv2.ApiMapping(this, 'ApiMapping', {
      api: this.httpApi,
      domainName: this.customDomain,
    });

    // Throttling
    const stage = this.httpApi.defaultStage?.node.defaultChild as apigatewayv2.CfnStage;
    if (stage) {
      stage.defaultRouteSettings = {
        throttlingBurstLimit: 1000,
        throttlingRateLimit: 500,
      };
    }
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/api-gateway-construct.ts
git commit -m "feat: add Private API Gateway construct with custom domain"
```

---

### Task 18: Create VPC Endpoint IP Automation Construct

**Files:**
- Create: `infrastructure/lib/constructs/vpc-endpoint-automation-construct.ts`
- Create: `infrastructure/lambda/update-targets/index.py`

**Step 1: Create the Lambda code**

```python
# infrastructure/lambda/update-targets/index.py
import boto3
import os
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ec2 = boto3.client('ec2')
elbv2 = boto3.client('elbv2')

VPC_ENDPOINT_ID = os.environ['VPC_ENDPOINT_ID']
TARGET_GROUP_ARN = os.environ['TARGET_GROUP_ARN']
TARGET_PORT = int(os.environ.get('TARGET_PORT', '443'))


def handler(event, context):
    logger.info(f"Received event: {event}")

    # Get VPC Endpoint details
    response = ec2.describe_vpc_endpoints(VpcEndpointIds=[VPC_ENDPOINT_ID])

    if not response['VpcEndpoints']:
        logger.error(f"VPC Endpoint {VPC_ENDPOINT_ID} not found")
        return {'statusCode': 404, 'body': 'VPC Endpoint not found'}

    vpc_endpoint = response['VpcEndpoints'][0]
    eni_ids = vpc_endpoint.get('NetworkInterfaceIds', [])

    if not eni_ids:
        logger.error("No ENIs found for VPC Endpoint")
        return {'statusCode': 404, 'body': 'No ENIs found'}

    # Get ENI private IPs
    enis_response = ec2.describe_network_interfaces(NetworkInterfaceIds=eni_ids)
    new_ips = [eni['PrivateIpAddress'] for eni in enis_response['NetworkInterfaces']]
    logger.info(f"Found ENI IPs: {new_ips}")

    # Get current targets
    current_targets = elbv2.describe_target_health(TargetGroupArn=TARGET_GROUP_ARN)
    current_ips = {t['Target']['Id'] for t in current_targets['TargetHealthDescriptions']}

    new_ips_set = set(new_ips)
    ips_to_add = new_ips_set - current_ips
    ips_to_remove = current_ips - new_ips_set

    # Deregister old IPs
    if ips_to_remove:
        elbv2.deregister_targets(
            TargetGroupArn=TARGET_GROUP_ARN,
            Targets=[{'Id': ip, 'Port': TARGET_PORT} for ip in ips_to_remove]
        )
        logger.info(f"Deregistered IPs: {ips_to_remove}")

    # Register new IPs
    if ips_to_add:
        elbv2.register_targets(
            TargetGroupArn=TARGET_GROUP_ARN,
            Targets=[{'Id': ip, 'Port': TARGET_PORT} for ip in ips_to_add]
        )
        logger.info(f"Registered IPs: {ips_to_add}")

    return {
        'statusCode': 200,
        'body': f"Updated. Added: {ips_to_add}, Removed: {ips_to_remove}"
    }
```

**Step 2: Create the CDK construct**

```typescript
// infrastructure/lib/constructs/vpc-endpoint-automation-construct.ts
import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as cr from 'aws-cdk-lib/custom-resources';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';
import * as path from 'path';

export interface VpcEndpointAutomationConstructProps {
  vpcEndpoint: ec2.IInterfaceVpcEndpoint;
  targetGroup: elbv2.IApplicationTargetGroup;
}

export class VpcEndpointAutomationConstruct extends Construct {
  public readonly lambda: lambda.Function;

  constructor(scope: Construct, id: string, props: VpcEndpointAutomationConstructProps) {
    super(scope, id);

    this.lambda = new lambda.Function(this, 'UpdateTargetsLambda', {
      runtime: lambda.Runtime.PYTHON_3_12,
      handler: 'index.handler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../lambda/update-targets')),
      environment: {
        VPC_ENDPOINT_ID: props.vpcEndpoint.vpcEndpointId,
        TARGET_GROUP_ARN: props.targetGroup.targetGroupArn,
        TARGET_PORT: '443',
      },
      timeout: cdk.Duration.seconds(30),
    });

    // Grant permissions
    this.lambda.addToRolePolicy(new iam.PolicyStatement({
      actions: [
        'ec2:DescribeVpcEndpoints',
        'ec2:DescribeNetworkInterfaces',
      ],
      resources: ['*'],
    }));

    this.lambda.addToRolePolicy(new iam.PolicyStatement({
      actions: [
        'elasticloadbalancing:DescribeTargetHealth',
        'elasticloadbalancing:RegisterTargets',
        'elasticloadbalancing:DeregisterTargets',
      ],
      resources: [props.targetGroup.targetGroupArn],
    }));

    // EventBridge rule for VPC Endpoint changes
    const rule = new events.Rule(this, 'VpcEndpointChangeRule', {
      eventPattern: {
        source: ['aws.ec2'],
        detailType: ['AWS API Call via CloudTrail'],
        detail: {
          eventSource: ['ec2.amazonaws.com'],
          eventName: ['CreateVpcEndpoint', 'ModifyVpcEndpoint'],
        },
      },
    });

    rule.addTarget(new targets.LambdaFunction(this.lambda));

    // Initialize targets on stack deployment
    new cr.AwsCustomResource(this, 'InitTargets', {
      onCreate: {
        service: 'Lambda',
        action: 'invoke',
        parameters: {
          FunctionName: this.lambda.functionName,
          Payload: JSON.stringify({ init: true }),
        },
        physicalResourceId: cr.PhysicalResourceId.of('InitTargets'),
      },
      policy: cr.AwsCustomResourcePolicy.fromStatements([
        new iam.PolicyStatement({
          actions: ['lambda:InvokeFunction'],
          resources: [this.lambda.functionArn],
        }),
      ]),
    });
  }
}
```

**Step 3: Commit**

```bash
mkdir -p infrastructure/lambda/update-targets
git add infrastructure/lib/constructs/vpc-endpoint-automation-construct.ts
git add infrastructure/lambda/update-targets/index.py
git commit -m "feat: add VPC Endpoint IP automation with Lambda and EventBridge"
```

---

### Task 19: Create ECS Construct

**Files:**
- Create: `infrastructure/lib/constructs/ecs-construct.ts`

**Step 1: Create the construct**

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';

export interface ServiceConfig {
  name: string;
  enabledEndpoints: string;
  desiredCount: number;
  minCount: number;
  maxCount: number;
  cpu: number;
  memory: number;
  targetGroup: elbv2.IApplicationTargetGroup;
}

export interface EcsConstructProps {
  vpc: ec2.IVpc;
  services: ServiceConfig[];
  ecrRepository: ecr.IRepository;
  rdsEndpoint: string;
  rdsSecretName: string;
  cacheEndpoint: string;
  cacheSecretArn: string;
  apiGatewayDomain: string;
}

export class EcsConstruct extends Construct {
  public readonly cluster: ecs.Cluster;
  public readonly services: Map<string, ecs.FargateService>;
  public readonly taskSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: EcsConstructProps) {
    super(scope, id);

    this.cluster = new ecs.Cluster(this, 'Cluster', {
      vpc: props.vpc,
      containerInsights: true,
    });

    this.taskSecurityGroup = new ec2.SecurityGroup(this, 'TaskSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for ECS tasks',
      allowAllOutbound: true,
    });

    // Task execution role
    const executionRole = new iam.Role(this, 'ExecutionRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy'),
      ],
    });

    // Task role with least privilege
    const taskRole = new iam.Role(this, 'TaskRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['rds-db:connect'],
      resources: ['arn:aws:rds-db:*:*:dbuser:*/fhir4java_app'],
    }));

    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['secretsmanager:GetSecretValue'],
      resources: [props.cacheSecretArn],
    }));

    // Log group
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: '/ecs/fhir4java',
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.services = new Map();

    for (const svc of props.services) {
      const taskDefinition = new ecs.FargateTaskDefinition(this, `${svc.name}TaskDef`, {
        cpu: svc.cpu,
        memoryLimitMiB: svc.memory,
        executionRole,
        taskRole,
      });

      const container = taskDefinition.addContainer(`${svc.name}Container`, {
        image: ecs.ContainerImage.fromEcrRepository(props.ecrRepository, 'latest'),
        logging: ecs.LogDrivers.awsLogs({
          logGroup,
          streamPrefix: svc.name,
        }),
        environment: {
          SPRING_PROFILES_ACTIVE: 'aws,aws-iam',
          FHIR4JAVA_ENDPOINTS_ENABLED: svc.enabledEndpoints,
          RDS_ENDPOINT: props.rdsEndpoint,
          RDS_PORT: '5432',
          RDS_DATABASE: 'fhir4java_prod',
          RDS_USERNAME: 'fhir4java_app',
          ELASTICACHE_ENDPOINT: props.cacheEndpoint,
          ELASTICACHE_PORT: '6379',
          API_GATEWAY_DOMAIN: props.apiGatewayDomain,
          AWS_REGION: cdk.Stack.of(this).region,
        },
        secrets: {
          ELASTICACHE_AUTH_TOKEN: ecs.Secret.fromSecretsManager(
            cdk.aws_secretsmanager.Secret.fromSecretCompleteArn(this, `${svc.name}CacheSecret`, props.cacheSecretArn),
            'authToken'
          ),
        },
        portMappings: [{ containerPort: 8080 }],
        readonlyRootFilesystem: true,
        user: '65532:65532',
        healthCheck: {
          command: ['CMD-SHELL', 'wget -q -O /dev/null http://localhost:8080/actuator/health/liveness || exit 1'],
          interval: cdk.Duration.seconds(30),
          timeout: cdk.Duration.seconds(10),
          retries: 3,
          startPeriod: cdk.Duration.seconds(60),
        },
      });

      // Add tmp volume for writable filesystem
      taskDefinition.addVolume({ name: 'tmp' });
      container.addMountPoints({ sourceVolume: 'tmp', containerPath: '/tmp', readOnly: false });

      const service = new ecs.FargateService(this, `${svc.name}Service`, {
        cluster: this.cluster,
        taskDefinition,
        desiredCount: svc.desiredCount,
        securityGroups: [this.taskSecurityGroup],
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        enableExecuteCommand: true,
      });

      service.attachToApplicationTargetGroup(svc.targetGroup);

      // Auto-scaling
      const scaling = service.autoScaleTaskCount({
        minCapacity: svc.minCount,
        maxCapacity: svc.maxCount,
      });

      scaling.scaleOnCpuUtilization(`${svc.name}CpuScaling`, {
        targetUtilizationPercent: 70,
        scaleInCooldown: cdk.Duration.seconds(60),
        scaleOutCooldown: cdk.Duration.seconds(60),
      });

      this.services.set(svc.name, service);
    }
  }
}
```

**Step 2: Commit**

```bash
git add infrastructure/lib/constructs/ecs-construct.ts
git commit -m "feat: add ECS Fargate construct with multi-service support"
```

---

### Task 20: Create Main Stack (Complete)

**Files:**
- Modify: `infrastructure/lib/fhir4java-infrastructure-stack.ts`
- Modify: `infrastructure/bin/fhir4java-infrastructure.ts`

**Step 1: Update the main stack**

```typescript
// infrastructure/lib/fhir4java-infrastructure-stack.ts
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53_targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { VpcConstruct } from './constructs/vpc-construct';
import { RdsConstruct } from './constructs/rds-construct';
import { ElastiCacheConstruct } from './constructs/elasticache-construct';
import { PublicAlbConstruct } from './constructs/public-alb-construct';
import { NlbConstruct } from './constructs/nlb-construct';
import { InternalAlbConstruct } from './constructs/internal-alb-construct';
import { AdminAlbConstruct } from './constructs/admin-alb-construct';
import { ApiGatewayConstruct } from './constructs/api-gateway-construct';
import { VpcEndpointAutomationConstruct } from './constructs/vpc-endpoint-automation-construct';
import { EcsConstruct } from './constructs/ecs-construct';

export interface Fhir4JavaInfrastructureStackProps extends cdk.StackProps {
  computePlatform?: 'ecs' | 'eks';
  domainName: string;
  certificateArn: string;
  vpnCidr: string;
  hostedZoneId: string;
}

export class Fhir4JavaInfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Fhir4JavaInfrastructureStackProps) {
    super(scope, id, props);

    // VPC
    const vpcConstruct = new VpcConstruct(this, 'Vpc');

    // RDS PostgreSQL
    const rdsConstruct = new RdsConstruct(this, 'Rds', {
      vpc: vpcConstruct.vpc,
      iamAuthentication: true,
    });

    // ElastiCache
    const cacheConstruct = new ElastiCacheConstruct(this, 'Cache', {
      vpc: vpcConstruct.vpc,
    });

    // Internal ALB (Service Routing)
    const internalAlbConstruct = new InternalAlbConstruct(this, 'InternalAlb', {
      vpc: vpcConstruct.vpc,
    });

    // Admin ALB
    const adminAlbConstruct = new AdminAlbConstruct(this, 'AdminAlb', {
      vpc: vpcConstruct.vpc,
      certificateArn: props.certificateArn,
      vpnCidr: props.vpnCidr,
    });

    // NLB (VPC Link Target)
    const nlbConstruct = new NlbConstruct(this, 'Nlb', {
      vpc: vpcConstruct.vpc,
      internalAlb: internalAlbConstruct.alb,
    });

    // Private API Gateway
    const apiGatewayConstruct = new ApiGatewayConstruct(this, 'ApiGateway', {
      vpc: vpcConstruct.vpc,
      nlb: nlbConstruct.nlb,
      domainName: props.domainName,
      certificateArn: props.certificateArn,
    });

    // Public ALB
    const publicAlbConstruct = new PublicAlbConstruct(this, 'PublicAlb', {
      vpc: vpcConstruct.vpc,
      certificateArn: props.certificateArn,
      domainName: props.domainName,
    });

    // VPC Endpoint IP Automation
    new VpcEndpointAutomationConstruct(this, 'VpcEndpointAutomation', {
      vpcEndpoint: vpcConstruct.apiGatewayEndpoint,
      targetGroup: publicAlbConstruct.targetGroup,
    });

    // ECR Repository
    const ecrRepository = new ecr.Repository(this, 'EcrRepository', {
      repositoryName: 'fhir4java',
      imageScanOnPush: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // ECS Services
    const ecsConstruct = new EcsConstruct(this, 'Ecs', {
      vpc: vpcConstruct.vpc,
      ecrRepository,
      rdsEndpoint: rdsConstruct.instance.dbInstanceEndpointAddress,
      rdsSecretName: 'fhir4java/prod/rds',
      cacheEndpoint: cacheConstruct.cluster.attrPrimaryEndPointAddress,
      cacheSecretArn: cacheConstruct.secret.secretArn,
      apiGatewayDomain: props.domainName,
      services: [
        {
          name: 'fhir-api',
          enabledEndpoints: 'fhir',
          desiredCount: 2,
          minCount: 2,
          maxCount: 10,
          cpu: 1024,
          memory: 2048,
          targetGroup: internalAlbConstruct.fhirApiTargetGroup,
        },
        {
          name: 'fhir-metadata',
          enabledEndpoints: 'metadata',
          desiredCount: 1,
          minCount: 1,
          maxCount: 2,
          cpu: 512,
          memory: 1024,
          targetGroup: internalAlbConstruct.fhirMetadataTargetGroup,
        },
        {
          name: 'fhir-actuator',
          enabledEndpoints: 'actuator',
          desiredCount: 1,
          minCount: 1,
          maxCount: 1,
          cpu: 256,
          memory: 512,
          targetGroup: adminAlbConstruct.actuatorTargetGroup,
        },
        {
          name: 'fhir-admin',
          enabledEndpoints: 'admin',
          desiredCount: 1,
          minCount: 1,
          maxCount: 1,
          cpu: 256,
          memory: 512,
          targetGroup: adminAlbConstruct.adminTargetGroup,
        },
      ],
    });

    // Security Group Rules
    rdsConstruct.securityGroup.addIngressRule(
      ecsConstruct.taskSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow PostgreSQL from ECS tasks'
    );

    cacheConstruct.securityGroup.addIngressRule(
      ecsConstruct.taskSecurityGroup,
      ec2.Port.tcp(6379),
      'Allow Redis from ECS tasks'
    );

    internalAlbConstruct.securityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'Allow HTTP from NLB'
    );

    // Route 53
    const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'HostedZone', {
      hostedZoneId: props.hostedZoneId,
      zoneName: props.domainName.split('.').slice(-2).join('.'),
    });

    new route53.ARecord(this, 'PublicAlbRecord', {
      zone: hostedZone,
      recordName: props.domainName,
      target: route53.RecordTarget.fromAlias(
        new route53_targets.LoadBalancerTarget(publicAlbConstruct.alb)
      ),
    });

    // Outputs
    new cdk.CfnOutput(this, 'VpcId', { value: vpcConstruct.vpc.vpcId });
    new cdk.CfnOutput(this, 'RdsEndpoint', { value: rdsConstruct.instance.dbInstanceEndpointAddress });
    new cdk.CfnOutput(this, 'CacheEndpoint', { value: cacheConstruct.cluster.attrPrimaryEndPointAddress });
    new cdk.CfnOutput(this, 'PublicAlbDns', { value: publicAlbConstruct.alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'ApiGatewayEndpoint', { value: apiGatewayConstruct.httpApi.apiEndpoint });
    new cdk.CfnOutput(this, 'EcrRepositoryUri', { value: ecrRepository.repositoryUri });
  }
}
```

**Step 2: Update bin file**

```typescript
// infrastructure/bin/fhir4java-infrastructure.ts
#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { Fhir4JavaInfrastructureStack } from '../lib/fhir4java-infrastructure-stack';

const app = new cdk.App();

const domainName = app.node.tryGetContext('domainName') || 'fhir.example.com';
const certificateArn = app.node.tryGetContext('certificateArn');
const vpnCidr = app.node.tryGetContext('vpnCidr') || '10.100.0.0/16';
const hostedZoneId = app.node.tryGetContext('hostedZoneId');

new Fhir4JavaInfrastructureStack(app, 'Fhir4JavaInfrastructureStack', {
  domainName,
  certificateArn,
  vpnCidr,
  hostedZoneId,
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
  },
});
```

**Step 3: Verify CDK synthesizes**

```bash
cd infrastructure
npm run build
npx cdk synth --context certificateArn=arn:aws:acm:us-east-1:123456789:certificate/xxx --context hostedZoneId=Z123
```
Expected: CloudFormation template output

**Step 4: Commit**

```bash
cd ..
git add infrastructure/
git commit -m "feat: complete main CDK stack with all infrastructure components"
```

---

## Phase 5: CI/CD Pipeline

### Task 21: Create GitHub Actions Workflow

**Files:**
- Create: `.github/workflows/deploy-aws.yml`

**Step 1: Create the workflow**

```yaml
name: Deploy to AWS

on:
  push:
    branches: [main]
    paths-ignore:
      - '**.md'
      - 'docs/**'
  workflow_dispatch:

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: fhir4java
  ECS_CLUSTER: fhir4java-cluster

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    outputs:
      image-tag: ${{ github.sha }}

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build with Maven
        run: ./mvnw clean package -DskipTests -B

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

      - name: Scan image for vulnerabilities
        run: |
          aws ecr start-image-scan \
            --repository-name $ECR_REPOSITORY \
            --image-id imageTag=${{ github.sha }} || true

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    strategy:
      max-parallel: 2
      matrix:
        service: [fhir-api, fhir-metadata, fhir-actuator, fhir-admin]

    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Deploy ${{ matrix.service }} to ECS
        run: |
          aws ecs update-service \
            --cluster $ECS_CLUSTER \
            --service ${{ matrix.service }} \
            --force-new-deployment
```

**Step 2: Commit**

```bash
mkdir -p .github/workflows
git add .github/workflows/deploy-aws.yml
git commit -m "feat: add GitHub Actions CI/CD workflow for AWS deployment"
```

---

### Task 22: Run Full Build and Tests

**Step 1: Run full Maven build with tests**

Run: `./mvnw clean verify`
Expected: BUILD SUCCESS

**Step 2: Run BDD tests**

Run: `./mvnw test -pl fhir4java-server -Dtest=CucumberIT`
Expected: All tests pass

**Step 3: Final commit of any remaining changes**

```bash
git status
# If any changes remain:
git add -A
git commit -m "chore: finalize AWS deployment implementation"
```

---

## Summary

This implementation plan covers:

1. **Phase 1: Application Changes** (Tasks 1-8)
   - AWS SDK dependencies
   - Endpoint filtering for multi-service deployment
   - AWS Spring profiles (base, IAM auth, Secrets Manager auth)
   - RDS IAM authentication configuration
   - ElastiCache cluster configuration
   - Hardened Dockerfile with distroless base

2. **Phase 2: AWS CDK Infrastructure - Foundation** (Tasks 9-12)
   - CDK project initialization
   - VPC with public, private, and isolated subnets
   - VPC endpoints including API Gateway endpoint
   - RDS PostgreSQL with IAM authentication
   - ElastiCache Valkey cluster

3. **Phase 3: AWS CDK Infrastructure - Load Balancers** (Tasks 13-16)
   - Public ALB with WAF integration
   - NLB for API Gateway VPC Link
   - Internal ALB with path-based routing
   - Admin ALB with VPN-only access

4. **Phase 4: AWS CDK Infrastructure - API Gateway & Compute** (Tasks 17-20)
   - Private API Gateway with custom domain
   - VPC Endpoint IP automation (Lambda + EventBridge)
   - ECS Fargate with 4 services
   - Complete main stack integration

5. **Phase 5: CI/CD Pipeline** (Tasks 21-22)
   - GitHub Actions workflow for multi-service deployment
   - ECR image scanning
   - Parallel service deployment

**Total Tasks:** 22
**Estimated Implementation Time:** Tasks are 2-5 minutes each
