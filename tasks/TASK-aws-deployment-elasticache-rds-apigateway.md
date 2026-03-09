# Implementation Plan: AWS Deployment with ElastiCache, RDS, and API Gateway

## Executive Summary

This plan outlines the implementation strategy for deploying FHIR4Java to AWS production environment using:
- **Amazon ElastiCache (Redis/Valkey)** as cache provider
- **Amazon RDS PostgreSQL** as database provider
- **Amazon API Gateway** for API exposure

The implementation maintains backward compatibility with local development using embedded Redis and PostgreSQL containers.

---

## Table of Contents

1. [Current State Assessment](#1-current-state-assessment)
2. [Target Architecture](#2-target-architecture)
3. [AWS Infrastructure Setup](#3-aws-infrastructure-setup)
4. [Application Changes](#4-application-changes)
5. [Security Configuration](#5-security-configuration)
6. [Deployment Strategy](#6-deployment-strategy)
7. [Testing Strategy](#7-testing-strategy)
8. [Monitoring and Observability](#8-monitoring-and-observability)
9. [Cost Estimation](#9-cost-estimation)
10. [Implementation Phases](#10-implementation-phases)
11. [Risks and Mitigations](#11-risks-and-mitigations)
12. [References](#12-references)

---

## 1. Current State Assessment

### 1.1 Caching Implementation

| Aspect | Current State | Notes |
|--------|---------------|-------|
| Dependencies | `spring-boot-starter-data-redis`, `spring-boot-starter-cache` | Ready for Redis |
| Configuration | Redis on `localhost:6379` with Lettuce client | Connection pooling configured |
| Spring Cache | `@EnableCaching` enabled | No `@Cacheable` annotations in services |
| Plugin System | `CachePlugin` interface with tenant-scoping | Not actively used |
| Test Profile | Uses `spring.cache.type: simple` | In-memory cache |

**Gap Analysis:**
- Redis infrastructure configured but not utilized in service layer
- No cache annotations on CRUD operations
- Lettuce client configured but needs cluster mode support for ElastiCache

### 1.2 Database Implementation

| Aspect | Current State | Notes |
|--------|---------------|-------|
| Database | PostgreSQL 16+ | JSONB storage for FHIR resources |
| Connection Pool | HikariCP (5-20 connections) | Well-configured |
| Migrations | Flyway with 5 migration versions | Baseline-on-migrate enabled |
| Multi-tenancy | Row-level isolation with tenant mapping | Fully implemented |
| Schemas | `fhir`, `careplan` (dedicated) | Schema routing implemented |

**Gap Analysis:**
- Credentials hardcoded in `application.yml`
- No AWS Secrets Manager integration
- No IAM database authentication

### 1.3 API Layer

| Aspect | Current State | Notes |
|--------|---------------|-------|
| Framework | Spring Boot 3.5.7 with Spring MVC | Standard REST controllers |
| URL Patterns | `/fhir/{version}/{resourceType}/{id}` | Version-aware routing |
| Authentication | Plugin-based (disabled by default) | No built-in auth |
| CORS | Configured for `*` origins | Needs restriction for production |
| Health Checks | Actuator endpoints exposed | Ready for ALB health checks |

**Gap Analysis:**
- No AWS-specific authentication (Cognito, IAM)
- CORS needs production hardening
- No request throttling/rate limiting

---

## 2. Target Architecture

### 2.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AWS Cloud                                       │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                           VPC (10.0.0.0/16)                            │ │
│  │                                                                         │ │
│  │  ┌─────────────┐     ┌─────────────────────────────────────────────┐   │ │
│  │  │   Public    │     │              Private Subnets                │   │ │
│  │  │   Subnets   │     │                                             │   │ │
│  │  │             │     │  ┌─────────────┐    ┌─────────────────────┐ │   │ │
│  │  │ ┌─────────┐ │     │  │   ECS/EKS   │    │   Amazon RDS        │ │   │ │
│  │  │ │   NAT   │ │     │  │   Fargate   │    │   PostgreSQL        │ │   │ │
│  │  │ │ Gateway │ │     │  │             │    │   (Multi-AZ)        │ │   │ │
│  │  │ └─────────┘ │     │  │ ┌─────────┐ │    │                     │ │   │ │
│  │  │             │     │  │ │FHIR4Java│ │───▶│ ┌─────────────────┐ │ │   │ │
│  │  └─────────────┘     │  │ │  App    │ │    │ │ fhir4java_prod  │ │ │   │ │
│  │                      │  │ └─────────┘ │    │ └─────────────────┘ │ │   │ │
│  │                      │  └──────┬──────┘    └─────────────────────┘ │   │ │
│  │                      │         │                                   │   │ │
│  │                      │         ▼                                   │   │ │
│  │                      │  ┌─────────────────────────────────────┐    │   │ │
│  │                      │  │      Amazon ElastiCache             │    │   │ │
│  │                      │  │      (Redis/Valkey Cluster)         │    │   │ │
│  │                      │  │                                     │    │   │ │
│  │                      │  │  Primary ◄──► Replica (Multi-AZ)    │    │   │ │
│  │                      │  └─────────────────────────────────────┘    │   │ │
│  │                      └─────────────────────────────────────────────┘   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                      ▲                                       │
│                                      │                                       │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │                     Amazon API Gateway                                 │  │
│  │                     (REST API / HTTP API)                              │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │  │
│  │  │   /fhir/*   │  │  /metadata  │  │  /actuator  │  │ /api/admin  │   │  │
│  │  │   (proxy)   │  │   (proxy)   │  │  (internal) │  │  (private)  │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      ▲                                       │
└──────────────────────────────────────┼───────────────────────────────────────┘
                                       │
                              ┌────────┴────────┐
                              │   Clients       │
                              │   (HTTPS)       │
                              └─────────────────┘
```

### 2.2 Component Overview

| Component | AWS Service | Configuration |
|-----------|-------------|---------------|
| API Gateway | Amazon API Gateway (HTTP API) | Regional, with VPC Link |
| Compute | ECS Fargate or EKS | Auto-scaling, Multi-AZ |
| Database | Amazon RDS PostgreSQL | Multi-AZ, db.r6g.large |
| Cache | Amazon ElastiCache (Valkey/Redis) | Cluster Mode, r6g.large |
| Secrets | AWS Secrets Manager | Auto-rotation enabled |
| DNS | Amazon Route 53 | Custom domain |
| CDN | Amazon CloudFront (optional) | Edge caching for static |
| Monitoring | CloudWatch, X-Ray | Full observability |

---

## 3. AWS Infrastructure Setup

### 3.1 VPC and Networking

#### 3.1.1 VPC Configuration

```yaml
# CDK/Terraform Pseudo-configuration
VPC:
  cidr: 10.0.0.0/16
  azs: [us-east-1a, us-east-1b, us-east-1c]

  publicSubnets:
    - 10.0.1.0/24  # NAT Gateway, ALB
    - 10.0.2.0/24
    - 10.0.3.0/24

  privateSubnets:
    - 10.0.11.0/24  # ECS/EKS
    - 10.0.12.0/24
    - 10.0.13.0/24

  databaseSubnets:
    - 10.0.21.0/24  # RDS, ElastiCache
    - 10.0.22.0/24
    - 10.0.23.0/24
```

#### 3.1.2 Security Groups

| Security Group | Inbound Rules | Outbound Rules |
|----------------|---------------|----------------|
| `sg-api-gateway` | HTTPS (443) from Internet | All to VPC |
| `sg-ecs-tasks` | HTTP (8080) from `sg-api-gateway` | All to VPC |
| `sg-rds` | PostgreSQL (5432) from `sg-ecs-tasks` | None |
| `sg-elasticache` | Redis (6379) from `sg-ecs-tasks` | None |

### 3.2 Amazon RDS PostgreSQL Setup

#### 3.2.1 RDS Instance Configuration

```yaml
RDS:
  engine: postgres
  engineVersion: "16.4"
  instanceClass: db.r6g.large  # Production
  # instanceClass: db.t3.micro  # Development

  storage:
    type: gp3
    size: 100  # GB
    iops: 3000
    throughput: 125  # MB/s

  multiAz: true
  deletionProtection: true
  storageEncrypted: true

  database:
    name: fhir4java_prod
    port: 5432

  backup:
    retentionPeriod: 7  # days
    preferredWindow: "03:00-04:00"

  maintenance:
    preferredWindow: "Sun:04:00-Sun:05:00"

  parameters:
    # PostgreSQL parameters
    shared_buffers: "{DBInstanceClassMemory/4}"
    max_connections: "200"
    work_mem: "64MB"
    maintenance_work_mem: "512MB"
    effective_cache_size: "{DBInstanceClassMemory*3/4}"
    random_page_cost: "1.1"
```

#### 3.2.2 Database Initialization Script

```sql
-- Execute after RDS creation via Lambda or bastion host

-- Create required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS fhir;
CREATE SCHEMA IF NOT EXISTS careplan;

-- Create application user (credentials in Secrets Manager)
CREATE USER fhir4java_app WITH PASSWORD '<from-secrets-manager>';
GRANT USAGE ON SCHEMA fhir, careplan TO fhir4java_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA fhir, careplan TO fhir4java_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA fhir, careplan TO fhir4java_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir, careplan
    GRANT ALL PRIVILEGES ON TABLES TO fhir4java_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir, careplan
    GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java_app;
```

#### 3.2.3 Secrets Manager Configuration

```json
{
  "SecretName": "fhir4java/prod/rds",
  "SecretString": {
    "username": "fhir4java_app",
    "password": "<auto-generated>",
    "host": "fhir4java-prod.xxxx.us-east-1.rds.amazonaws.com",
    "port": 5432,
    "dbname": "fhir4java_prod",
    "engine": "postgres"
  },
  "RotationConfiguration": {
    "AutomaticallyAfterDays": 30,
    "RotateImmediatelyOnUpdate": true
  }
}
```

### 3.3 Amazon ElastiCache Setup

#### 3.3.1 ElastiCache Cluster Configuration

```yaml
ElastiCache:
  engine: valkey  # or redis
  engineVersion: "7.2"

  # Option 1: Serverless (recommended for variable workloads)
  serverless:
    cacheUsageLimits:
      dataStorage:
        maximum: 10  # GB
        unit: GB
      ecpuPerSecond:
        maximum: 15000

  # Option 2: Cluster Mode (recommended for predictable workloads)
  clusterMode:
    enabled: true
    nodeType: cache.r6g.large
    numNodeGroups: 2  # Shards
    replicasPerNodeGroup: 1  # 1 replica per shard

  # Security
  transitEncryptionEnabled: true
  atRestEncryptionEnabled: true
  authToken: "<from-secrets-manager>"

  # Subnet Group
  subnetGroupName: fhir4java-cache-subnets
  securityGroupIds:
    - sg-elasticache

  # Maintenance
  preferredMaintenanceWindow: "sun:05:00-sun:06:00"
  snapshotRetentionLimit: 7
  snapshotWindow: "04:00-05:00"
```

#### 3.3.2 ElastiCache Secrets Manager

```json
{
  "SecretName": "fhir4java/prod/elasticache",
  "SecretString": {
    "authToken": "<auto-generated>",
    "primaryEndpoint": "fhir4java-cache.xxxx.clustercfg.use1.cache.amazonaws.com",
    "readerEndpoint": "fhir4java-cache-ro.xxxx.clustercfg.use1.cache.amazonaws.com",
    "port": 6379
  }
}
```

### 3.4 Amazon API Gateway Setup

#### 3.4.1 API Gateway Configuration (HTTP API - Recommended)

```yaml
APIGateway:
  type: HTTP_API  # Preferred over REST API for lower latency/cost

  # VPC Link for private integration
  vpcLink:
    name: fhir4java-vpc-link
    targetArns:
      - arn:aws:elasticloadbalancing:...:targetgroup/fhir4java-tg/xxx

  # Routes
  routes:
    - path: "/fhir/{proxy+}"
      method: ANY
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${ALB_DNS}/fhir/{proxy}"

    - path: "/fhir"
      method: ANY
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${ALB_DNS}/fhir"

    - path: "/{proxy+}"
      method: ANY
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${ALB_DNS}/{proxy}"

  # CORS
  corsConfiguration:
    allowOrigins:
      - "https://your-frontend-domain.com"
    allowMethods:
      - GET
      - POST
      - PUT
      - PATCH
      - DELETE
      - OPTIONS
    allowHeaders:
      - Content-Type
      - Authorization
      - X-Tenant-ID
      - X-Request-Id
    exposeHeaders:
      - ETag
      - Location
      - Content-Location
      - X-FHIR-Version
    maxAge: 3600

  # Throttling
  throttlingBurstLimit: 1000
  throttlingRateLimit: 500  # requests per second

  # Access Logging
  accessLogSettings:
    destinationArn: arn:aws:logs:...:log-group:api-gateway-logs
    format: '{"requestId":"$context.requestId","ip":"$context.identity.sourceIp","requestTime":"$context.requestTime","httpMethod":"$context.httpMethod","path":"$context.path","status":"$context.status","responseLength":"$context.responseLength"}'

  # Custom Domain
  domainName: api.fhir4java.example.com
  certificateArn: arn:aws:acm:...:certificate/xxx
```

#### 3.4.2 API Gateway Authorizer (Optional - Cognito)

```yaml
Authorizer:
  type: JWT
  identitySource: "$request.header.Authorization"
  jwtConfiguration:
    audience:
      - "fhir4java-api"
    issuer: "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_xxxxx"
```

### 3.5 Infrastructure as Code (AWS CDK)

#### 3.5.1 CDK Project Structure

```
infrastructure/
├── cdk.json
├── package.json
├── tsconfig.json
├── bin/
│   └── fhir4java-infra.ts
├── lib/
│   ├── fhir4java-stack.ts           # Main stack
│   ├── constructs/
│   │   ├── vpc-construct.ts         # VPC, subnets, NAT
│   │   ├── rds-construct.ts         # RDS PostgreSQL
│   │   ├── elasticache-construct.ts # ElastiCache cluster
│   │   ├── api-gateway-construct.ts # API Gateway
│   │   └── ecs-construct.ts         # ECS Fargate service
│   └── config/
│       ├── dev.ts
│       ├── staging.ts
│       └── prod.ts
└── test/
    └── fhir4java-stack.test.ts
```

#### 3.5.2 CDK Stack Example

```typescript
// lib/fhir4java-stack.ts
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as apigatewayv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';

export class Fhir4JavaStack extends cdk.Stack {
  constructor(scope: cdk.App, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // VPC
    const vpc = new ec2.Vpc(this, 'Fhir4JavaVpc', {
      maxAzs: 3,
      natGateways: 1,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC },
        { name: 'private', subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        { name: 'database', subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      ],
    });

    // RDS PostgreSQL
    const dbSecret = new secretsmanager.Secret(this, 'DbSecret', {
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'fhir4java_app' }),
        generateStringKey: 'password',
        excludeCharacters: '/@"\\',
      },
    });

    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_4,
      }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.R6G, ec2.InstanceSize.LARGE),
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      credentials: rds.Credentials.fromSecret(dbSecret),
      databaseName: 'fhir4java_prod',
      multiAz: true,
      storageEncrypted: true,
      deletionProtection: true,
    });

    // ElastiCache (Valkey/Redis)
    const cacheSubnetGroup = new elasticache.CfnSubnetGroup(this, 'CacheSubnetGroup', {
      description: 'FHIR4Java Cache Subnet Group',
      subnetIds: vpc.selectSubnets({ subnetType: ec2.SubnetType.PRIVATE_ISOLATED }).subnetIds,
    });

    const cacheCluster = new elasticache.CfnReplicationGroup(this, 'CacheCluster', {
      replicationGroupDescription: 'FHIR4Java ElastiCache Cluster',
      engine: 'valkey',
      cacheNodeType: 'cache.r6g.large',
      numNodeGroups: 2,
      replicasPerNodeGroup: 1,
      cacheSubnetGroupName: cacheSubnetGroup.ref,
      transitEncryptionEnabled: true,
      atRestEncryptionEnabled: true,
    });

    // Outputs
    new cdk.CfnOutput(this, 'DatabaseEndpoint', {
      value: database.dbInstanceEndpointAddress,
    });
    new cdk.CfnOutput(this, 'CacheEndpoint', {
      value: cacheCluster.attrPrimaryEndPointAddress,
    });
  }
}
```

---

## 4. Application Changes

### 4.1 New Dependencies

#### 4.1.1 Parent pom.xml

```xml
<properties>
    <!-- AWS SDK -->
    <aws-sdk.version>2.29.0</aws-sdk.version>
    <aws-secretsmanager-jdbc.version>1.0.12</aws-secretsmanager-jdbc.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- AWS SDK BOM -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 4.1.2 fhir4java-server/pom.xml

```xml
<dependencies>
    <!-- AWS Secrets Manager JDBC Driver -->
    <dependency>
        <groupId>com.amazonaws.secretsmanager</groupId>
        <artifactId>aws-secretsmanager-jdbc</artifactId>
        <version>${aws-secretsmanager-jdbc.version}</version>
    </dependency>

    <!-- AWS SDK for Secrets Manager (ElastiCache auth token) -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>secretsmanager</artifactId>
    </dependency>

    <!-- AWS SDK for STS (assume role) -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sts</artifactId>
    </dependency>
</dependencies>
```

### 4.2 Application Profiles

#### 4.2.1 application-aws.yml (New File)

```yaml
# AWS Production Profile
spring:
  config:
    activate:
      on-profile: aws

  # RDS PostgreSQL with Secrets Manager
  datasource:
    url: jdbc-secretsmanager:postgresql://${RDS_ENDPOINT}:${RDS_PORT:5432}/${RDS_DATABASE:fhir4java_prod}
    driver-class-name: com.amazonaws.secretsmanager.sql.AWSSecretsManagerPostgreSQLDriver
    username: ${RDS_SECRET_NAME}  # Secret name, not actual username
    hikari:
      pool-name: fhir4java-aws-pool
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

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

# FHIR4Java AWS-specific settings
fhir4java:
  server:
    base-url: https://${API_GATEWAY_DOMAIN}/fhir

  validation:
    profile-validator-enabled: false  # Enable after initial deployment

  plugins:
    authentication:
      enabled: true  # Enable for production
    authorization:
      enabled: true  # Enable for production
    audit:
      enabled: true
    telemetry:
      enabled: true

  tenant:
    enabled: true
    default-tenant-id: default
    header-name: X-Tenant-ID

# Actuator for health checks
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

# Logging for AWS CloudWatch
logging:
  pattern:
    console: '{"timestamp":"%d{ISO8601}","level":"%level","logger":"%logger","message":"%msg","thread":"%thread"}%n'
  level:
    root: INFO
    org.fhirframework: INFO
    org.hibernate.SQL: WARN
```

#### 4.2.2 application-aws-dev.yml (AWS Development)

```yaml
# AWS Development Profile (smaller instances, relaxed security)
spring:
  config:
    activate:
      on-profile: aws-dev

  datasource:
    hikari:
      minimum-idle: 2
      maximum-pool-size: 5

fhir4java:
  validation:
    profile-validator-enabled: false
  plugins:
    authentication:
      enabled: false
    authorization:
      enabled: false
```

### 4.3 ElastiCache Configuration Class

#### 4.3.1 ElastiCacheConfig.java (New File)

```java
package org.fhirframework.server.config;

import io.lettuce.core.ClientOptions;
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
        // Cluster configuration for ElastiCache
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
            Collections.singletonList(redisHost + ":" + redisPort)
        );
        clusterConfig.setPassword(redisPassword);

        // Topology refresh for cluster mode
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

### 4.4 Secrets Manager Integration

#### 4.4.1 SecretsManagerConfig.java (New File)

```java
package org.fhirframework.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
@Profile("aws")
public class SecretsManagerConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
            .region(Region.of(System.getenv("AWS_REGION")))
            .build();
    }
}
```

### 4.5 Health Check Enhancements

#### 4.5.1 ElastiCacheHealthIndicator.java (New File)

```java
package org.fhirframework.server.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("aws")
public class ElastiCacheHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public ElastiCacheHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try {
            connectionFactory.getConnection().ping();
            return Health.up()
                .withDetail("type", "ElastiCache")
                .withDetail("cluster", "available")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("type", "ElastiCache")
                .withException(e)
                .build();
        }
    }
}
```

---

## 5. Security Configuration

### 5.1 IAM Roles and Policies

#### 5.1.1 ECS Task Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:fhir4java/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": [
        "arn:aws:kms:*:*:key/*"
      ],
      "Condition": {
        "StringEquals": {
          "kms:ViaService": "secretsmanager.*.amazonaws.com"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "xray:PutTraceSegments",
        "xray:PutTelemetryRecords"
      ],
      "Resource": "*"
    }
  ]
}
```

#### 5.1.2 API Gateway Resource Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "execute-api:Invoke",
      "Resource": "arn:aws:execute-api:*:*:*/*/*/*"
    },
    {
      "Effect": "Deny",
      "Principal": "*",
      "Action": "execute-api:Invoke",
      "Resource": "arn:aws:execute-api:*:*:*/*/GET/actuator/*",
      "Condition": {
        "NotIpAddress": {
          "aws:SourceIp": ["10.0.0.0/16"]
        }
      }
    }
  ]
}
```

### 5.2 Network Security

#### 5.2.1 VPC Endpoints (Recommended)

```yaml
VPCEndpoints:
  - service: secretsmanager
    type: Interface
    privateDnsEnabled: true

  - service: rds
    type: Interface
    privateDnsEnabled: true

  - service: logs
    type: Interface
    privateDnsEnabled: true

  - service: ecr.api
    type: Interface
    privateDnsEnabled: true

  - service: ecr.dkr
    type: Interface
    privateDnsEnabled: true

  - service: s3
    type: Gateway
```

### 5.3 Encryption

| Resource | Encryption Type | Key Management |
|----------|-----------------|----------------|
| RDS | At-rest (AES-256) | AWS KMS CMK |
| ElastiCache | At-rest + In-transit | AWS KMS CMK |
| Secrets Manager | At-rest (AES-256) | AWS KMS CMK |
| API Gateway | TLS 1.2+ | ACM Certificate |
| ECS Task | In-transit (TLS) | N/A |

---

## 6. Deployment Strategy

### 6.1 Container Image

#### 6.1.1 Dockerfile

```dockerfile
# Multi-stage build for FHIR4Java
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY fhir4java-core/pom.xml fhir4java-core/
COPY fhir4java-persistence/pom.xml fhir4java-persistence/
COPY fhir4java-plugin/pom.xml fhir4java-plugin/
COPY fhir4java-api/pom.xml fhir4java-api/
COPY fhir4java-server/pom.xml fhir4java-server/

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY . .
RUN ./mvnw clean package -DskipTests -pl fhir4java-server -am

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy JAR
COPY --from=builder /app/fhir4java-server/target/fhir4java-server-*.jar app.jar

# Non-root user
RUN addgroup -g 1001 fhir4java && \
    adduser -u 1001 -G fhir4java -D fhir4java
USER fhir4java

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 6.2 ECS Task Definition

```json
{
  "family": "fhir4java",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::xxx:role/fhir4java-execution-role",
  "taskRoleArn": "arn:aws:iam::xxx:role/fhir4java-task-role",
  "containerDefinitions": [
    {
      "name": "fhir4java",
      "image": "xxx.dkr.ecr.us-east-1.amazonaws.com/fhir4java:latest",
      "essential": true,
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "aws"},
        {"name": "RDS_ENDPOINT", "value": "fhir4java-prod.xxx.rds.amazonaws.com"},
        {"name": "RDS_PORT", "value": "5432"},
        {"name": "RDS_DATABASE", "value": "fhir4java_prod"},
        {"name": "RDS_SECRET_NAME", "value": "fhir4java/prod/rds"},
        {"name": "ELASTICACHE_ENDPOINT", "value": "fhir4java-cache.xxx.cache.amazonaws.com"},
        {"name": "ELASTICACHE_PORT", "value": "6379"},
        {"name": "API_GATEWAY_DOMAIN", "value": "api.fhir4java.example.com"},
        {"name": "AWS_REGION", "value": "us-east-1"}
      ],
      "secrets": [
        {
          "name": "ELASTICACHE_AUTH_TOKEN",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxx:secret:fhir4java/prod/elasticache:authToken::"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/fhir4java",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/liveness || exit 1"],
        "interval": 30,
        "timeout": 10,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

### 6.3 CI/CD Pipeline

```yaml
# .github/workflows/deploy-aws.yml
name: Deploy to AWS

on:
  push:
    branches: [main]
    paths-ignore:
      - '**.md'
      - 'docs/**'

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: fhir4java
  ECS_CLUSTER: fhir4java-cluster
  ECS_SERVICE: fhir4java-service

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Build with Maven
        run: ./mvnw clean package -DskipTests

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

      - name: Deploy to ECS
        run: |
          aws ecs update-service \
            --cluster $ECS_CLUSTER \
            --service $ECS_SERVICE \
            --force-new-deployment
```

---

## 7. Testing Strategy

### 7.1 Local AWS Testing

#### 7.1.1 LocalStack Configuration

```yaml
# docker-compose-localstack.yml
version: '3.8'
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=secretsmanager,rds,elasticache
      - DEFAULT_REGION=us-east-1
    volumes:
      - "./localstack-init:/docker-entrypoint-initaws.d"
```

#### 7.1.2 application-localstack.yml

```yaml
spring:
  config:
    activate:
      on-profile: localstack

  datasource:
    url: jdbc:postgresql://localhost:5432/fhir4java_test
    # Standard driver for LocalStack (no Secrets Manager)
    driver-class-name: org.postgresql.Driver
    username: fhir4java
    password: fhir4java

  data:
    redis:
      host: localhost
      port: 6379
      ssl:
        enabled: false

cloud:
  aws:
    endpoint: http://localhost:4566
    region:
      static: us-east-1
```

### 7.2 Integration Test Profile

```yaml
# application-integration-test.yml
spring:
  config:
    activate:
      on-profile: integration-test

  # Use TestContainers for RDS-like PostgreSQL
  # Use TestContainers for Redis

fhir4java:
  validation:
    profile-validator-enabled: false
```

---

## 8. Monitoring and Observability

### 8.1 CloudWatch Metrics

| Metric | Source | Alarm Threshold |
|--------|--------|-----------------|
| CPU Utilization | ECS | > 80% for 5 min |
| Memory Utilization | ECS | > 80% for 5 min |
| DB Connections | RDS | > 150 |
| DB CPU | RDS | > 70% for 10 min |
| Cache Hit Ratio | ElastiCache | < 80% |
| Cache Memory | ElastiCache | > 80% |
| API Latency | API Gateway | > 2000ms p99 |
| API 5xx Errors | API Gateway | > 1% |

### 8.2 CloudWatch Dashboard

```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "title": "FHIR API Latency",
        "metrics": [
          ["AWS/ApiGateway", "Latency", "ApiId", "xxx", {"stat": "p99"}],
          ["...", {"stat": "Average"}]
        ]
      }
    },
    {
      "type": "metric",
      "properties": {
        "title": "Database Performance",
        "metrics": [
          ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", "fhir4java-prod"],
          [".", "DatabaseConnections", ".", "."]
        ]
      }
    },
    {
      "type": "metric",
      "properties": {
        "title": "Cache Performance",
        "metrics": [
          ["AWS/ElastiCache", "CacheHitRate", "CacheClusterId", "fhir4java-cache"],
          [".", "CurrConnections", ".", "."]
        ]
      }
    }
  ]
}
```

### 8.3 X-Ray Tracing

```java
// Add to application for distributed tracing
@Configuration
@Profile("aws")
public class XRayConfig {

    @Bean
    public Filter tracingFilter() {
        return new AWSXRayServletFilter("fhir4java");
    }
}
```

---

## 9. Cost Estimation

### 9.1 Monthly Cost Estimate (Production)

| Service | Configuration | Estimated Cost (USD) |
|---------|---------------|---------------------|
| RDS PostgreSQL | db.r6g.large, Multi-AZ, 100GB | ~$350 |
| ElastiCache | cache.r6g.large, 2 shards, 1 replica | ~$400 |
| ECS Fargate | 2 tasks, 1 vCPU, 2GB | ~$100 |
| API Gateway | HTTP API, 10M requests | ~$10 |
| NAT Gateway | 1 NAT, 100GB data | ~$50 |
| CloudWatch | Logs, Metrics, Alarms | ~$30 |
| Secrets Manager | 2 secrets | ~$1 |
| **Total** | | **~$940/month** |

### 9.2 Cost Optimization Options

| Optimization | Savings | Trade-off |
|--------------|---------|-----------|
| Reserved Instances (1yr) | ~30% | Commitment |
| Savings Plans (1yr) | ~25% | Commitment |
| ElastiCache Serverless | Variable | Pay-per-use |
| Single-AZ RDS (dev) | ~50% | No HA |
| Smaller instance classes | ~40% | Performance |

---

## 10. Implementation Phases

### Phase 1: Infrastructure Foundation (Week 1-2)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Create AWS CDK project | DevOps | CDK stack skeleton |
| Deploy VPC and networking | DevOps | VPC, subnets, security groups |
| Deploy RDS PostgreSQL | DevOps | RDS instance + Secrets Manager |
| Deploy ElastiCache cluster | DevOps | ElastiCache + auth token |
| Create IAM roles/policies | DevOps | Task role, execution role |

### Phase 2: Application Integration (Week 3-4)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Add AWS SDK dependencies | Backend | Updated pom.xml |
| Create aws profile | Backend | application-aws.yml |
| Implement ElastiCache config | Backend | ElastiCacheConfig.java |
| Implement Secrets Manager integration | Backend | SecretsManagerConfig.java |
| Add health indicators | Backend | ElastiCacheHealthIndicator.java |
| Update Dockerfile | Backend | Production Dockerfile |

### Phase 3: API Gateway Setup (Week 5)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Deploy API Gateway | DevOps | HTTP API with VPC Link |
| Configure routes | DevOps | /fhir/* proxy routes |
| Set up custom domain | DevOps | Route 53 + ACM |
| Configure throttling | DevOps | Rate limits |
| Set up access logging | DevOps | CloudWatch log group |

### Phase 4: CI/CD and Monitoring (Week 6)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Create ECR repository | DevOps | Container registry |
| Deploy ECS cluster | DevOps | Fargate cluster + service |
| Create GitHub Actions workflow | DevOps | CI/CD pipeline |
| Set up CloudWatch dashboard | DevOps | Monitoring dashboard |
| Configure alarms | DevOps | Alert rules |

### Phase 5: Testing and Validation (Week 7-8)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Run Flyway migrations | Backend | Database schema |
| Smoke test endpoints | QA | Test results |
| Load testing | QA | Performance baseline |
| Security scan | Security | Vulnerability report |
| Documentation | All | Runbook, architecture docs |

---

## 11. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Cold start latency | Medium | Low | Pre-warm containers, connection pooling |
| Secrets rotation failure | High | Low | Monitor rotation, alert on failure |
| ElastiCache failover | Medium | Low | Multi-AZ, automatic failover |
| RDS failover delay | High | Low | Multi-AZ, connection retry logic |
| API Gateway throttling | Medium | Medium | Request proper limits, implement backoff |
| Cost overrun | Medium | Medium | Set billing alarms, right-size instances |
| Network connectivity | High | Low | VPC endpoints, proper security groups |

---

## 12. References

### AWS Documentation
- [Amazon ElastiCache Best Practices](https://aws.amazon.com/blogs/database/best-practices-valkey-redis-oss-clients-and-amazon-elasticache/)
- [Spring Boot with ElastiCache](https://aws.amazon.com/blogs/database/integrate-your-spring-boot-application-with-amazon-elasticache/)
- [AWS Secrets Manager JDBC](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_jdbc.html)
- [Spring Boot with RDS](https://aws.amazon.com/blogs/opensource/using-a-postgresql-database-with-amazon-rds-and-spring-boot/)
- [API Gateway HTTP APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api.html)
- [AWS CDK Best Practices](https://docs.aws.amazon.com/cdk/v2/guide/best-practices.html)

### Community Resources
- [RDS Authentication with Spring Boot - Secrets Manager](https://chariotsolutions.com/blog/post/rds-database-authentication-with-spring-boot-part-1-secrets-manager/)
- [Spring Cloud AWS Redis](https://reflectoring.io/spring-cloud-aws-redis/)
- [Spring Boot AWS Secrets Manager Integration](https://www.baeldung.com/spring-boot-integrate-aws-secrets-manager)

### Internal References
- [CLAUDE.md](../CLAUDE.md) - Project context
- [FHIR4JAVA-IMPLEMENTATION-PLAN.md](../FHIR4JAVA-IMPLEMENTATION-PLAN.md) - Full implementation spec

---

## Appendix A: Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `aws` |
| `RDS_ENDPOINT` | RDS instance endpoint | `fhir4java.xxx.rds.amazonaws.com` |
| `RDS_PORT` | RDS port | `5432` |
| `RDS_DATABASE` | Database name | `fhir4java_prod` |
| `RDS_SECRET_NAME` | Secrets Manager secret name | `fhir4java/prod/rds` |
| `ELASTICACHE_ENDPOINT` | ElastiCache primary endpoint | `fhir4java.xxx.cache.amazonaws.com` |
| `ELASTICACHE_PORT` | ElastiCache port | `6379` |
| `ELASTICACHE_AUTH_TOKEN` | ElastiCache auth token (from Secrets Manager) | `<secret>` |
| `API_GATEWAY_DOMAIN` | Custom domain for API Gateway | `api.fhir4java.example.com` |
| `AWS_REGION` | AWS region | `us-east-1` |

---

## Appendix B: Checklist

### Pre-Deployment Checklist

- [ ] VPC and subnets created
- [ ] Security groups configured
- [ ] RDS PostgreSQL deployed and accessible
- [ ] ElastiCache cluster deployed and accessible
- [ ] Secrets Manager secrets created
- [ ] IAM roles and policies created
- [ ] ECR repository created
- [ ] Container image built and pushed
- [ ] ECS cluster and service deployed
- [ ] API Gateway configured
- [ ] Custom domain and SSL certificate
- [ ] CloudWatch alarms configured
- [ ] Flyway migrations executed
- [ ] Smoke tests passed

### Post-Deployment Checklist

- [ ] All health checks passing
- [ ] API endpoints responding correctly
- [ ] Database connections stable
- [ ] Cache operations working
- [ ] Logs flowing to CloudWatch
- [ ] Metrics visible in dashboard
- [ ] Alarms tested
- [ ] Documentation updated
