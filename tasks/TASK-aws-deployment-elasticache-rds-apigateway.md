# Implementation Plan: AWS Deployment with ElastiCache, RDS, and API Gateway

## Executive Summary

This plan outlines the implementation strategy for deploying FHIR4Java to AWS production environment using:
- **Amazon ElastiCache (Redis/Valkey)** as cache provider
- **Amazon RDS PostgreSQL** as database provider
- **Amazon API Gateway (Private)** for API management
- **ECS Fargate or EKS Fargate** as compute platform (configurable)
- **Multi-service architecture** with 4 isolated services for security and scaling

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
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                                    AWS Cloud                                          │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                              Route 53                                            │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐    │ │
│  │  │  fhir.example.com ────────────────────► Public ALB                      │    │ │
│  │  │  fhir-admin.internal.example.com ─────► Internal ALB (Admin)           │    │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘    │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                         │                                             │
│  ┌──────────────────────────────────────┼──────────────────────────────────────────┐ │
│  │                              Public Subnets                                      │ │
│  │                                      │                                           │ │
│  │  ┌───────────────────────────────────▼───────────────────────────────────────┐  │ │
│  │  │                    Public ALB (Internet-facing)                            │  │ │
│  │  │                    + AWS WAF (OWASP, Rate Limiting, Geo-blocking)          │  │ │
│  │  │                    + ACM Certificate (TLS 1.3)                             │  │ │
│  │  │                    Target: VPC Endpoint ENI IPs (IP type, HTTPS:443)       │  │ │
│  │  └───────────────────────────────────┬───────────────────────────────────────┘  │ │
│  │                                      │                                           │ │
│  │  ┌─────────────┐                     │                                           │ │
│  │  │ NAT Gateway │                     │                                           │ │
│  │  └─────────────┘                     │                                           │ │
│  └──────────────────────────────────────┼──────────────────────────────────────────┘ │
│                                         │                                             │
│  ┌──────────────────────────────────────┼──────────────────────────────────────────┐ │
│  │                             Private Subnets                                      │ │
│  │                                      │                                           │ │
│  │  ┌───────────────────────────────────▼───────────────────────────────────────┐  │ │
│  │  │              API Gateway VPC Endpoint (Interface)                          │  │ │
│  │  │              ENIs: 10.0.11.x, 10.0.12.x, 10.0.13.x                         │  │ │
│  │  │              (Managed by IP Change Automation Lambda)                      │  │ │
│  │  └───────────────────────────────────┬───────────────────────────────────────┘  │ │
│  │                                      │                                           │ │
│  │  ┌───────────────────────────────────▼───────────────────────────────────────┐  │ │
│  │  │                    Private API Gateway (HTTP API)                          │  │ │
│  │  │                    Custom Domain: fhir.example.com                         │  │ │
│  │  │                    Routes:                                                 │  │ │
│  │  │                      /fhir/{version}/metadata → fhir-metadata service      │  │ │
│  │  │                      /fhir/{proxy+}          → fhir-api service            │  │ │
│  │  │                    Features: Throttling, Request Validation, API Keys      │  │ │
│  │  │                    VPC Link Target: NLB                                    │  │ │
│  │  └───────────────────────────────────┬───────────────────────────────────────┘  │ │
│  │                                      │                                           │ │
│  │  ┌───────────────────────────────────▼───────────────────────────────────────┐  │ │
│  │  │                         NLB (Internal)                                     │  │ │
│  │  │                         Static IPs, Cross-zone LB enabled                  │  │ │
│  │  │                         Target: ALB Ingress IPs                            │  │ │
│  │  └───────────────────────────────────┬───────────────────────────────────────┘  │ │
│  │                                      │                                           │ │
│  │  ┌───────────────────────────────────▼───────────────────────────────────────┐  │ │
│  │  │                    ALB Ingress Controller (Internal ALB)                   │  │ │
│  │  │                    Path-based routing to services:                         │  │ │
│  │  │                      /fhir/*/metadata → fhir-metadata (1-2 tasks)          │  │ │
│  │  │                      /fhir/*          → fhir-api (2-10 tasks)              │  │ │
│  │  └───────────────────────────────────┬───────────────────────────────────────┘  │ │
│  │                                      │                                           │ │
│  │          ┌───────────────────────────┼───────────────────────────────┐          │ │
│  │          │                           │                               │          │ │
│  │          ▼                           ▼                               ▼          │ │
│  │  ┌──────────────┐           ┌──────────────┐                ┌──────────────┐   │ │
│  │  │  fhir-api    │           │fhir-metadata │                │fhir-actuator │   │ │
│  │  │  (2-10)      │           │   (1-2)      │                │    (1)       │   │ │
│  │  │  /fhir/*     │           │  /metadata   │                │  /actuator/* │   │ │
│  │  └──────────────┘           └──────────────┘                └──────────────┘   │ │
│  │                                                                                 │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  │ │
│  │  │                    Internal ALB (Admin/Ops - VPN Only)                    │  │ │
│  │  │                    fhir-admin.internal.example.com                        │  │ │
│  │  │                    Routes: /actuator/*, /api/admin/*                      │  │ │
│  │  └───────────────────────────────────┬──────────────────────────────────────┘  │ │
│  │                                      │                                          │ │
│  │                               ┌──────┴──────┐                                   │ │
│  │                               ▼             ▼                                   │ │
│  │                        ┌────────────┐ ┌────────────┐                           │ │
│  │                        │fhir-admin  │ │fhir-actuator│                          │ │
│  │                        │   (1)      │ │   (1)       │                          │ │
│  │                        │/api/admin/*│ │ /actuator/* │                          │ │
│  │                        └────────────┘ └─────────────┘                          │ │
│  │                                                                                 │ │
│  │                    ECS Fargate or EKS Fargate (Configurable)                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                           Database Subnets (Isolated)                           │ │
│  │                                                                                  │ │
│  │  ┌──────────────────────────────┐    ┌──────────────────────────────┐          │ │
│  │  │      RDS PostgreSQL          │    │      Amazon ElastiCache      │          │ │
│  │  │      (Multi-AZ)              │    │      (Redis/Valkey Cluster)  │          │ │
│  │  │      db.r6g.large            │    │      cache.r6g.large         │          │ │
│  │  │      Auth: IAM or Secrets    │    │      2 shards, 1 replica     │          │ │
│  │  └──────────────────────────────┘    └──────────────────────────────┘          │ │
│  │                                                                                  │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Component Overview

| Component | AWS Service | Configuration |
|-----------|-------------|---------------|
| Edge Protection | Public ALB + AWS WAF | Internet-facing, OWASP rules |
| API Gateway | Amazon API Gateway (Private HTTP API) | Custom domain, VPC Link to NLB |
| Load Balancer (API GW) | NLB (Internal) | Static IPs, VPC Link target |
| Load Balancer (Services) | ALB Ingress (Internal) | Path-based routing |
| Compute | ECS Fargate or EKS Fargate | Toggle via CDK parameter |
| Database | Amazon RDS PostgreSQL | Multi-AZ, IAM auth or Secrets Manager |
| Cache | Amazon ElastiCache (Valkey/Redis) | Cluster Mode, r6g.large |
| Secrets | AWS Secrets Manager | Auto-rotation enabled |
| DNS | Amazon Route 53 | Custom domains |
| Monitoring | CloudWatch, X-Ray | Full observability |

### 2.3 Multi-Service Architecture

Single Docker image deployed as 4 isolated services with endpoint filtering:

| Service | Endpoints | Network Access | Scaling | Purpose |
|---------|-----------|----------------|---------|---------|
| `fhir-api` | `/fhir/*` | Public (via API GW) | 2-10 tasks | Main FHIR CRUD operations |
| `fhir-metadata` | `/fhir/*/metadata` | Public (via API GW) | 1-2 tasks | CapabilityStatement |
| `fhir-actuator` | `/actuator/*` | Private (VPN only) | 1 task | Health, metrics, prometheus |
| `fhir-admin` | `/api/admin/*` | Private (VPN only) | 1 task | Admin operations |

**Benefits:**
- **Security isolation**: Admin/actuator never exposed publicly
- **Independent scaling**: Scale FHIR API separately from metadata
- **Blast radius reduction**: Service failure doesn't affect others
- **Single codebase**: Same image, different runtime configurations

### 2.4 Compute Platform Toggle (ECS vs EKS)

CDK parameter allows choosing compute platform at deployment time:

```typescript
// CDK context parameter
const computePlatform = this.node.tryGetContext('computePlatform') || 'ecs';
// Values: 'ecs' | 'eks'
```

| Aspect | ECS Fargate | EKS Fargate |
|--------|-------------|-------------|
| Complexity | Lower | Higher |
| Kubernetes ecosystem | No | Yes |
| Service mesh | AWS App Mesh (optional) | Istio, Linkerd, etc. |
| Ingress | ALB via target groups | AWS Load Balancer Controller |
| Config management | Task definitions | Kubernetes manifests |
| Best for | Simpler deployments | K8s-native teams |

---

## 3. AWS Infrastructure Setup

### 3.1 VPC and Networking

#### 3.1.1 VPC Configuration

```yaml
VPC:
  cidr: 10.0.0.0/16
  azs: [us-east-1a, us-east-1b, us-east-1c]

  publicSubnets:
    - 10.0.1.0/24  # NAT Gateway, Public ALB
    - 10.0.2.0/24
    - 10.0.3.0/24

  privateSubnets:
    - 10.0.11.0/24  # ECS/EKS, NLB, Internal ALB, VPC Endpoints
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
| `sg-public-alb` | HTTPS (443) from Internet | HTTPS (443) to `sg-vpc-endpoint` |
| `sg-vpc-endpoint` | HTTPS (443) from `sg-public-alb` | All to VPC |
| `sg-nlb` | TCP (80/443) from API Gateway | TCP (80/443) to `sg-internal-alb` |
| `sg-internal-alb` | HTTP (80) from `sg-nlb`, `sg-admin-alb` | HTTP (8080) to `sg-ecs-tasks` |
| `sg-admin-alb` | HTTPS (443) from VPN CIDR only | HTTP (8080) to `sg-ecs-tasks` |
| `sg-ecs-tasks` | HTTP (8080) from `sg-internal-alb` | All to VPC |
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

  # Security: Enable IAM authentication
  iamDatabaseAuthenticationEnabled: true

  database:
    name: fhir4java_prod
    port: 5432

  backup:
    retentionPeriod: 7  # days
    preferredWindow: "03:00-04:00"

  maintenance:
    preferredWindow: "Sun:04:00-Sun:05:00"

  parameters:
    shared_buffers: "{DBInstanceClassMemory/4}"
    max_connections: "200"
    work_mem: "64MB"
    maintenance_work_mem: "512MB"
    effective_cache_size: "{DBInstanceClassMemory*3/4}"
    random_page_cost: "1.1"
```

#### 3.2.2 RDS Authentication Options

**Option A: IAM Database Authentication (Recommended)**

```yaml
# No stored passwords, short-lived tokens (15 min), CloudTrail audit
RDS:
  iamDatabaseAuthenticationEnabled: true

# IAM Policy for ECS Task Role
{
  "Effect": "Allow",
  "Action": "rds-db:connect",
  "Resource": "arn:aws:rds-db:us-east-1:*:dbuser:*/fhir4java_app"
}
```

**Option B: Secrets Manager with Auto-Rotation**

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

#### 3.2.3 Database Initialization Script

```sql
-- Execute after RDS creation via Lambda or bastion host

-- Create required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create schemas
CREATE SCHEMA IF NOT EXISTS fhir;
CREATE SCHEMA IF NOT EXISTS careplan;

-- Create application user
CREATE USER fhir4java_app WITH LOGIN;

-- For IAM auth: Grant rds_iam role
GRANT rds_iam TO fhir4java_app;

-- Grant permissions
GRANT USAGE ON SCHEMA fhir, careplan TO fhir4java_app;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA fhir, careplan TO fhir4java_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA fhir, careplan TO fhir4java_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir, careplan
    GRANT ALL PRIVILEGES ON TABLES TO fhir4java_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA fhir, careplan
    GRANT ALL PRIVILEGES ON SEQUENCES TO fhir4java_app;
```

### 3.3 Amazon ElastiCache Setup

#### 3.3.1 ElastiCache Cluster Configuration

```yaml
ElastiCache:
  engine: valkey  # or redis
  engineVersion: "7.2"

  # Cluster Mode (recommended for production)
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

### 3.4 Private API Gateway Setup

#### 3.4.1 Private API Gateway with Custom Domain

```yaml
APIGateway:
  type: HTTP_API
  endpointType: PRIVATE  # Private API Gateway

  # Custom Domain (enables Host header matching)
  customDomain:
    domainName: fhir.example.com
    certificateArn: arn:aws:acm:us-east-1:xxx:certificate/xxx
    securityPolicy: TLS_1_2

  # VPC Endpoint for private access
  vpcEndpoint:
    service: execute-api
    privateDnsEnabled: true
    subnets: [private-subnet-1, private-subnet-2, private-subnet-3]

  # VPC Link to NLB
  vpcLink:
    name: fhir4java-vpc-link
    targetArn: arn:aws:elasticloadbalancing:...:loadbalancer/net/fhir4java-nlb/xxx

  # Routes
  routes:
    - path: "/fhir/{version}/metadata"
      method: GET
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${NLB_DNS}/fhir/{version}/metadata"

    - path: "/fhir/{proxy+}"
      method: ANY
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${NLB_DNS}/fhir/{proxy}"

    - path: "/fhir"
      method: ANY
      integration:
        type: HTTP_PROXY
        connectionType: VPC_LINK
        uri: "http://${NLB_DNS}/fhir"

  # CORS
  corsConfiguration:
    allowOrigins:
      - "https://your-frontend-domain.com"
    allowMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
    allowHeaders: [Content-Type, Authorization, X-Tenant-ID, X-Request-Id]
    exposeHeaders: [ETag, Location, Content-Location, X-FHIR-Version]
    maxAge: 3600

  # Throttling
  throttlingBurstLimit: 1000
  throttlingRateLimit: 500

  # Access Logging
  accessLogSettings:
    destinationArn: arn:aws:logs:...:log-group:api-gateway-logs
    format: '{"requestId":"$context.requestId","ip":"$context.identity.sourceIp","requestTime":"$context.requestTime","httpMethod":"$context.httpMethod","path":"$context.path","status":"$context.status","responseLength":"$context.responseLength"}'
```

#### 3.4.2 VPC Endpoint IP Change Automation

Since VPC Endpoint ENI IPs can change, automation is required to keep ALB targets updated.

**Lambda Function:**

```python
# update_alb_targets.py
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

    # Step 1: Get VPC Endpoint details
    response = ec2.describe_vpc_endpoints(
        VpcEndpointIds=[VPC_ENDPOINT_ID]
    )

    if not response['VpcEndpoints']:
        logger.error(f"VPC Endpoint {VPC_ENDPOINT_ID} not found")
        return {'statusCode': 404, 'body': 'VPC Endpoint not found'}

    vpc_endpoint = response['VpcEndpoints'][0]
    eni_ids = vpc_endpoint.get('NetworkInterfaceIds', [])

    if not eni_ids:
        logger.error("No ENIs found for VPC Endpoint")
        return {'statusCode': 404, 'body': 'No ENIs found'}

    # Step 2: Get ENI private IPs
    enis_response = ec2.describe_network_interfaces(
        NetworkInterfaceIds=eni_ids
    )

    new_ips = []
    for eni in enis_response['NetworkInterfaces']:
        private_ip = eni['PrivateIpAddress']
        new_ips.append(private_ip)
        logger.info(f"Found ENI {eni['NetworkInterfaceId']} with IP {private_ip}")

    # Step 3: Get current targets in ALB target group
    current_targets_response = elbv2.describe_target_health(
        TargetGroupArn=TARGET_GROUP_ARN
    )

    current_ips = set()
    for target in current_targets_response['TargetHealthDescriptions']:
        current_ips.add(target['Target']['Id'])

    new_ips_set = set(new_ips)

    # Step 4: Calculate IPs to add and remove
    ips_to_add = new_ips_set - current_ips
    ips_to_remove = current_ips - new_ips_set

    logger.info(f"Current IPs: {current_ips}")
    logger.info(f"New IPs: {new_ips_set}")
    logger.info(f"IPs to add: {ips_to_add}")
    logger.info(f"IPs to remove: {ips_to_remove}")

    # Step 5: Deregister old IPs
    if ips_to_remove:
        elbv2.deregister_targets(
            TargetGroupArn=TARGET_GROUP_ARN,
            Targets=[{'Id': ip, 'Port': TARGET_PORT} for ip in ips_to_remove]
        )
        logger.info(f"Deregistered IPs: {ips_to_remove}")

    # Step 6: Register new IPs
    if ips_to_add:
        elbv2.register_targets(
            TargetGroupArn=TARGET_GROUP_ARN,
            Targets=[{'Id': ip, 'Port': TARGET_PORT} for ip in ips_to_add]
        )
        logger.info(f"Registered IPs: {ips_to_add}")

    return {
        'statusCode': 200,
        'body': f"Updated targets. Added: {ips_to_add}, Removed: {ips_to_remove}"
    }
```

**EventBridge Rule:**

```json
{
  "source": ["aws.ec2"],
  "detail-type": ["AWS API Call via CloudTrail"],
  "detail": {
    "eventSource": ["ec2.amazonaws.com"],
    "eventName": [
      "CreateVpcEndpoint",
      "ModifyVpcEndpoint",
      "DeleteVpcEndpoint"
    ]
  }
}
```

**CDK Implementation:**

```typescript
// Lambda function
const updateTargetsLambda = new lambda.Function(this, 'UpdateTargetsLambda', {
  runtime: lambda.Runtime.PYTHON_3_12,
  handler: 'update_alb_targets.handler',
  code: lambda.Code.fromAsset('lambda/update-targets'),
  environment: {
    VPC_ENDPOINT_ID: vpcEndpoint.vpcEndpointId,
    TARGET_GROUP_ARN: apiGwTargetGroup.targetGroupArn,
    TARGET_PORT: '443',
  },
  timeout: cdk.Duration.seconds(30),
});

// Grant permissions
updateTargetsLambda.addToRolePolicy(new iam.PolicyStatement({
  actions: [
    'ec2:DescribeVpcEndpoints',
    'ec2:DescribeNetworkInterfaces',
  ],
  resources: ['*'],
}));

updateTargetsLambda.addToRolePolicy(new iam.PolicyStatement({
  actions: [
    'elasticloadbalancing:DescribeTargetHealth',
    'elasticloadbalancing:RegisterTargets',
    'elasticloadbalancing:DeregisterTargets',
  ],
  resources: [apiGwTargetGroup.targetGroupArn],
}));

// EventBridge rule
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

rule.addTarget(new targets.LambdaFunction(updateTargetsLambda));

// Initialize targets on stack deployment
const initTargets = new cr.AwsCustomResource(this, 'InitTargets', {
  onCreate: {
    service: 'Lambda',
    action: 'invoke',
    parameters: {
      FunctionName: updateTargetsLambda.functionName,
      Payload: JSON.stringify({ init: true }),
    },
    physicalResourceId: cr.PhysicalResourceId.of('InitTargets'),
  },
  policy: cr.AwsCustomResourcePolicy.fromStatements([
    new iam.PolicyStatement({
      actions: ['lambda:InvokeFunction'],
      resources: [updateTargetsLambda.functionArn],
    }),
  ]),
});
```

### 3.5 Load Balancer Configuration

#### 3.5.1 Public ALB (Internet-facing)

```yaml
PublicALB:
  scheme: internet-facing
  securityGroups: [sg-public-alb]
  subnets: [public-subnet-1, public-subnet-2, public-subnet-3]

  # WAF Integration
  webAclArn: arn:aws:wafv2:...:webacl/fhir4java-waf/xxx

  listeners:
    - port: 443
      protocol: HTTPS
      certificate: arn:aws:acm:...:certificate/xxx
      defaultAction:
        type: forward
        targetGroup: api-gw-vpc-endpoint-tg

  targetGroups:
    - name: api-gw-vpc-endpoint-tg
      targetType: ip
      protocol: HTTPS
      port: 443
      healthCheck:
        protocol: HTTPS
        path: /fhir/r5/metadata
        matcher: "200"
        interval: 30
        timeout: 5
      # Targets managed by Lambda automation
```

#### 3.5.2 NLB (VPC Link Target)

```yaml
NLB:
  scheme: internal
  type: network
  crossZoneLoadBalancing: true
  subnets: [private-subnet-1, private-subnet-2, private-subnet-3]

  listeners:
    - port: 80
      protocol: TCP
      defaultAction:
        type: forward
        targetGroup: alb-ingress-tg

  targetGroups:
    - name: alb-ingress-tg
      targetType: alb  # ALB as target
      protocol: TCP
      port: 80
      target: internal-alb-arn
```

#### 3.5.3 Internal ALB (Service Routing)

```yaml
InternalALB:
  scheme: internal
  securityGroups: [sg-internal-alb]
  subnets: [private-subnet-1, private-subnet-2, private-subnet-3]

  listeners:
    - port: 80
      protocol: HTTP
      rules:
        - priority: 10
          conditions:
            - pathPattern: /fhir/*/metadata
          action:
            type: forward
            targetGroup: fhir-metadata-tg

        - priority: 20
          conditions:
            - pathPattern: /fhir/*
          action:
            type: forward
            targetGroup: fhir-api-tg

        - priority: 100
          conditions:
            - pathPattern: /*
          action:
            type: fixed-response
            statusCode: 404

  targetGroups:
    - name: fhir-api-tg
      targetType: ip
      protocol: HTTP
      port: 8080
      healthCheck:
        path: /actuator/health/liveness

    - name: fhir-metadata-tg
      targetType: ip
      protocol: HTTP
      port: 8080
      healthCheck:
        path: /actuator/health/liveness
```

#### 3.5.4 Admin ALB (VPN Only)

```yaml
AdminALB:
  scheme: internal
  securityGroups: [sg-admin-alb]
  subnets: [private-subnet-1, private-subnet-2, private-subnet-3]

  listeners:
    - port: 443
      protocol: HTTPS
      certificate: arn:aws:acm:...:certificate/xxx
      rules:
        - priority: 10
          conditions:
            - pathPattern: /actuator/*
          action:
            type: forward
            targetGroup: fhir-actuator-tg

        - priority: 20
          conditions:
            - pathPattern: /api/admin/*
          action:
            type: forward
            targetGroup: fhir-admin-tg

  targetGroups:
    - name: fhir-actuator-tg
      targetType: ip
      protocol: HTTP
      port: 8080
      healthCheck:
        path: /actuator/health/liveness

    - name: fhir-admin-tg
      targetType: ip
      protocol: HTTP
      port: 8080
      healthCheck:
        path: /actuator/health/liveness
```

### 3.6 Infrastructure as Code (AWS CDK)

#### 3.6.1 CDK Project Structure

```
infrastructure/
├── cdk.json
├── package.json
├── tsconfig.json
├── bin/
│   └── fhir4java-infra.ts
├── lib/
│   ├── fhir4java-stack.ts              # Main stack
│   ├── constructs/
│   │   ├── vpc-construct.ts            # VPC, subnets, NAT
│   │   ├── rds-construct.ts            # RDS PostgreSQL
│   │   ├── elasticache-construct.ts    # ElastiCache cluster
│   │   ├── api-gateway-construct.ts    # Private API Gateway
│   │   ├── load-balancer-construct.ts  # ALB, NLB configuration
│   │   ├── compute-construct.ts        # ECS/EKS abstraction
│   │   ├── ecs-construct.ts            # ECS Fargate services
│   │   ├── eks-construct.ts            # EKS Fargate services
│   │   └── vpc-endpoint-automation.ts  # IP change Lambda
│   └── config/
│       ├── dev.ts
│       ├── staging.ts
│       └── prod.ts
├── lambda/
│   └── update-targets/
│       └── update_alb_targets.py
└── test/
    └── fhir4java-stack.test.ts
```

#### 3.6.2 Compute Platform Abstraction

```typescript
// lib/constructs/compute-construct.ts
import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { EcsConstruct } from './ecs-construct';
import { EksConstruct } from './eks-construct';

export interface ComputeConstructProps {
  vpc: ec2.IVpc;
  platform: 'ecs' | 'eks';
  services: ServiceDefinition[];
}

export interface ServiceDefinition {
  name: string;
  enabledEndpoints: string;
  desiredCount: number;
  minCount: number;
  maxCount: number;
  cpu: number;
  memory: number;
  targetGroup: elbv2.IApplicationTargetGroup;
}

export class ComputeConstruct extends Construct {
  public readonly services: Map<string, any>;

  constructor(scope: Construct, id: string, props: ComputeConstructProps) {
    super(scope, id);

    if (props.platform === 'ecs') {
      const ecs = new EcsConstruct(this, 'ECS', {
        vpc: props.vpc,
        services: props.services,
      });
      this.services = ecs.services;
    } else {
      const eks = new EksConstruct(this, 'EKS', {
        vpc: props.vpc,
        services: props.services,
      });
      this.services = eks.services;
    }
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
    <!-- AWS Secrets Manager JDBC Driver (for Secrets Manager auth) -->
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

    <!-- AWS SDK for RDS (for IAM auth) -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>rds</artifactId>
    </dependency>

    <!-- AWS SDK for STS (assume role) -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sts</artifactId>
    </dependency>
</dependencies>
```

### 4.2 Endpoint Filtering Configuration

#### 4.2.1 EndpointFilterConfiguration.java (New File)

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

            // Always allow health checks
            if (path.startsWith("/actuator/health")) {
                chain.doFilter(request, response);
                return;
            }

            // Check if path is allowed for this service
            if ("all".equals(enabledEndpoints) || isPathAllowed(path)) {
                chain.doFilter(request, response);
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                httpResponse.getWriter().write("Endpoint not available on this service");
            }
        }

        private boolean isPathAllowed(String path) {
            return allowedPrefixes.stream().anyMatch(path::startsWith);
        }
    }
}
```

### 4.3 Application Profiles

#### 4.3.1 application-aws.yml (AWS Base Profile)

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

#### 4.3.2 application-aws-iam.yml (IAM Database Auth)

```yaml
# AWS with IAM Database Authentication
spring:
  config:
    activate:
      on-profile: aws-iam

  datasource:
    url: jdbc:postgresql://${RDS_ENDPOINT}:${RDS_PORT:5432}/${RDS_DATABASE:fhir4java_prod}
    username: ${RDS_USERNAME:fhir4java_app}
    # Password generated dynamically via IAM token
    hikari:
      pool-name: fhir4java-iam-pool
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      # Token refresh before expiry (tokens last 15 min)
      max-lifetime: 840000  # 14 minutes
```

#### 4.3.3 application-aws-secrets.yml (Secrets Manager Auth)

```yaml
# AWS with Secrets Manager Authentication
spring:
  config:
    activate:
      on-profile: aws-secrets

  datasource:
    url: jdbc-secretsmanager:postgresql://${RDS_ENDPOINT}:${RDS_PORT:5432}/${RDS_DATABASE:fhir4java_prod}
    driver-class-name: com.amazonaws.secretsmanager.sql.AWSSecretsManagerPostgreSQLDriver
    username: ${RDS_SECRET_NAME}  # Secret name, not actual username
    hikari:
      pool-name: fhir4java-secrets-pool
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 4.4 IAM Database Authentication

#### 4.4.1 RdsIamAuthConfig.java (New File)

```java
package org.fhirframework.server.config;

import com.zaxxer.hikari.HikariDataSource;
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

@Configuration
@Profile("aws-iam")
public class RdsIamAuthConfig {

    @Value("${RDS_ENDPOINT}")
    private String rdsEndpoint;

    @Value("${RDS_PORT:5432}")
    private int rdsPort;

    @Value("${RDS_USERNAME:fhir4java_app}")
    private String rdsUsername;

    @Value("${AWS_REGION:us-east-1}")
    private String awsRegion;

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Generate initial IAM auth token
        String authToken = generateAuthToken();
        dataSource.setPassword(authToken);

        // Set up token refresh
        dataSource.setConnectionInitSql("SELECT 1");

        return new IamAuthDataSourceWrapper(dataSource, this::generateAuthToken);
    }

    private String generateAuthToken() {
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

### 4.5 ElastiCache Configuration

#### 4.5.1 ElastiCacheConfig.java

```java
package org.fhirframework.server.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.SocketOptions;
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

---

## 5. Security Configuration

### 5.1 Container Security Hardening

#### 5.1.1 Security Requirements

| Control | Implementation |
|---------|---------------|
| Immutable containers | Read-only root filesystem |
| Non-root execution | Run as UID 1001, drop all capabilities |
| No privilege escalation | `allowPrivilegeEscalation: false` |
| Minimal base image | `gcr.io/distroless/java21-debian12` |
| No secrets in images | All secrets via Secrets Manager/IAM |
| Image scanning | ECR scan-on-push enabled |
| Signed images | AWS Signer (optional) |

#### 5.1.2 Hardened Dockerfile

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
RUN java -Djarmode=layertools -jar fhir4java-server/target/fhir4java-server-*.jar extract

# Runtime stage - Distroless for minimal attack surface
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Copy extracted layers
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Run as non-root user (UID 65532 in distroless)
USER nonroot:nonroot

# JVM options for container
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

#### 5.1.3 ECS Task Security Context

```json
{
  "containerDefinitions": [
    {
      "name": "fhir4java",
      "readonlyRootFilesystem": true,
      "user": "65532:65532",
      "linuxParameters": {
        "capabilities": {
          "drop": ["ALL"]
        },
        "initProcessEnabled": true
      },
      "mountPoints": [
        {
          "sourceVolume": "tmp",
          "containerPath": "/tmp",
          "readOnly": false
        }
      ]
    }
  ],
  "volumes": [
    {
      "name": "tmp",
      "host": {}
    }
  ]
}
```

#### 5.1.4 EKS Pod Security Context

```yaml
apiVersion: v1
kind: Pod
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    runAsGroup: 65532
    fsGroup: 65532
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: fhir4java
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop:
            - ALL
      volumeMounts:
        - name: tmp
          mountPath: /tmp
  volumes:
    - name: tmp
      emptyDir: {}
```

### 5.2 IAM Roles and Policies

#### 5.2.1 ECS Task Role (Least Privilege)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SecretsManagerAccess",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:*:secret:fhir4java/prod/*"
      ]
    },
    {
      "Sid": "RdsIamAuth",
      "Effect": "Allow",
      "Action": "rds-db:connect",
      "Resource": "arn:aws:rds-db:us-east-1:*:dbuser:*/fhir4java_app"
    },
    {
      "Sid": "KmsDecrypt",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:us-east-1:*:key/*",
      "Condition": {
        "StringEquals": {
          "kms:ViaService": "secretsmanager.us-east-1.amazonaws.com"
        }
      }
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:us-east-1:*:log-group:/ecs/fhir4java:*"
    },
    {
      "Sid": "XRayTracing",
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

#### 5.2.2 ECS Execution Role

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:us-east-1:*:log-group:/ecs/fhir4java:*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:*:secret:fhir4java/prod/elasticache*"
      ]
    }
  ]
}
```

### 5.3 Network Security

#### 5.3.1 VPC Endpoints (Recommended)

```yaml
VPCEndpoints:
  - service: execute-api  # For Private API Gateway
    type: Interface
    privateDnsEnabled: true

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

### 5.4 Encryption

| Resource | Encryption Type | Key Management |
|----------|-----------------|----------------|
| RDS | At-rest (AES-256) | AWS KMS CMK |
| ElastiCache | At-rest + In-transit | AWS KMS CMK |
| Secrets Manager | At-rest (AES-256) | AWS KMS CMK |
| ALB | TLS 1.3 | ACM Certificate |
| API Gateway | TLS 1.2+ | ACM Certificate |
| ECS Task | In-transit (TLS) | N/A |
| ECR Images | At-rest | AWS KMS |

---

## 6. Deployment Strategy

### 6.1 Multi-Service ECS Task Definitions

#### 6.1.1 fhir-api Service

```json
{
  "family": "fhir-api",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::xxx:role/fhir4java-execution-role",
  "taskRoleArn": "arn:aws:iam::xxx:role/fhir4java-task-role",
  "containerDefinitions": [
    {
      "name": "fhir-api",
      "image": "xxx.dkr.ecr.us-east-1.amazonaws.com/fhir4java:latest",
      "essential": true,
      "readonlyRootFilesystem": true,
      "user": "65532:65532",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "aws,aws-iam"},
        {"name": "FHIR4JAVA_ENDPOINTS_ENABLED", "value": "fhir"},
        {"name": "RDS_ENDPOINT", "value": "fhir4java-prod.xxx.rds.amazonaws.com"},
        {"name": "RDS_PORT", "value": "5432"},
        {"name": "RDS_DATABASE", "value": "fhir4java_prod"},
        {"name": "RDS_USERNAME", "value": "fhir4java_app"},
        {"name": "ELASTICACHE_ENDPOINT", "value": "fhir4java-cache.xxx.cache.amazonaws.com"},
        {"name": "ELASTICACHE_PORT", "value": "6379"},
        {"name": "API_GATEWAY_DOMAIN", "value": "fhir.example.com"},
        {"name": "AWS_REGION", "value": "us-east-1"}
      ],
      "secrets": [
        {
          "name": "ELASTICACHE_AUTH_TOKEN",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxx:secret:fhir4java/prod/elasticache:authToken::"
        }
      ],
      "linuxParameters": {
        "capabilities": {
          "drop": ["ALL"]
        },
        "initProcessEnabled": true
      },
      "mountPoints": [
        {
          "sourceVolume": "tmp",
          "containerPath": "/tmp",
          "readOnly": false
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/fhir4java",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "fhir-api"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health/liveness || exit 1"],
        "interval": 30,
        "timeout": 10,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ],
  "volumes": [
    {
      "name": "tmp",
      "host": {}
    }
  ]
}
```

#### 6.1.2 Service-Specific Environment Variables

| Service | `FHIR4JAVA_ENDPOINTS_ENABLED` | Target Group |
|---------|-------------------------------|--------------|
| fhir-api | `fhir` | fhir-api-tg |
| fhir-metadata | `metadata` | fhir-metadata-tg |
| fhir-actuator | `actuator` | fhir-actuator-tg |
| fhir-admin | `admin` | fhir-admin-tg |

### 6.2 EKS Kubernetes Manifests

#### 6.2.1 Deployment (fhir-api)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fhir-api
  namespace: fhir4java
spec:
  replicas: 2
  selector:
    matchLabels:
      app: fhir-api
  template:
    metadata:
      labels:
        app: fhir-api
    spec:
      serviceAccountName: fhir4java-sa
      securityContext:
        runAsNonRoot: true
        runAsUser: 65532
        runAsGroup: 65532
        fsGroup: 65532
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: fhir-api
          image: xxx.dkr.ecr.us-east-1.amazonaws.com/fhir4java:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "aws,aws-iam"
            - name: FHIR4JAVA_ENDPOINTS_ENABLED
              value: "fhir"
          envFrom:
            - configMapRef:
                name: fhir4java-config
            - secretRef:
                name: fhir4java-secrets
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop:
                - ALL
          volumeMounts:
            - name: tmp
              mountPath: /tmp
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "1000m"
              memory: "2Gi"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
      volumes:
        - name: tmp
          emptyDir: {}
```

#### 6.2.2 Ingress (AWS Load Balancer Controller)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: fhir-ingress
  namespace: fhir4java
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internal
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health/liveness
spec:
  rules:
    - http:
        paths:
          - path: /fhir/r4b/metadata
            pathType: Exact
            backend:
              service:
                name: fhir-metadata
                port:
                  number: 8080
          - path: /fhir/r5/metadata
            pathType: Exact
            backend:
              service:
                name: fhir-metadata
                port:
                  number: 8080
          - path: /fhir
            pathType: Prefix
            backend:
              service:
                name: fhir-api
                port:
                  number: 8080
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

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    outputs:
      image-tag: ${{ steps.meta.outputs.tags }}

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

      - name: Scan image for vulnerabilities
        run: |
          aws ecr start-image-scan \
            --repository-name $ECR_REPOSITORY \
            --image-id imageTag=${{ github.sha }}

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    strategy:
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

fhir4java:
  validation:
    profile-validator-enabled: false
  endpoints:
    enabled: all
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
| ALB Target Health | ALB | < 2 healthy targets |

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
        "title": "Service Health",
        "metrics": [
          ["AWS/ECS", "CPUUtilization", "ServiceName", "fhir-api", "ClusterName", "fhir4java-cluster"],
          [".", "MemoryUtilization", ".", ".", ".", "."]
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
| ECS Fargate | 6 tasks total (all services), 1 vCPU, 2GB each | ~$200 |
| Public ALB | Internet-facing, WAF | ~$50 |
| NLB | Internal | ~$25 |
| Internal ALBs | 2x Internal | ~$50 |
| API Gateway | HTTP API, 10M requests | ~$10 |
| NAT Gateway | 1 NAT, 100GB data | ~$50 |
| CloudWatch | Logs, Metrics, Alarms | ~$30 |
| Secrets Manager | 3 secrets | ~$2 |
| **Total** | | **~$1,170/month** |

### 9.2 Cost Optimization Options

| Optimization | Savings | Trade-off |
|--------------|---------|-----------|
| Reserved Instances (1yr) | ~30% | Commitment |
| Savings Plans (1yr) | ~25% | Commitment |
| Single-AZ RDS (dev) | ~50% | No HA |
| Smaller instance classes | ~40% | Performance |
| Combine services (dev) | ~50% on compute | Less isolation |

---

## 10. Implementation Phases

### Phase 1: Infrastructure Foundation (Week 1-2)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Create AWS CDK project | DevOps | CDK stack skeleton |
| Deploy VPC and networking | DevOps | VPC, subnets, security groups |
| Deploy RDS PostgreSQL | DevOps | RDS instance + IAM auth enabled |
| Deploy ElastiCache cluster | DevOps | ElastiCache + auth token |
| Create IAM roles/policies | DevOps | Task role, execution role |
| Deploy VPC Endpoints | DevOps | execute-api, secretsmanager, etc. |

### Phase 2: Application Integration (Week 3-4)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Add AWS SDK dependencies | Backend | Updated pom.xml |
| Implement endpoint filtering | Backend | EndpointFilterConfiguration.java |
| Create AWS profiles | Backend | application-aws*.yml |
| Implement IAM DB auth | Backend | RdsIamAuthConfig.java |
| Implement ElastiCache config | Backend | ElastiCacheConfig.java |
| Update Dockerfile (hardened) | Backend | Distroless Dockerfile |

### Phase 3: Load Balancer & API Gateway Setup (Week 5-6)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Deploy Public ALB + WAF | DevOps | Internet-facing ALB |
| Deploy Private API Gateway | DevOps | HTTP API with custom domain |
| Deploy NLB (VPC Link) | DevOps | Internal NLB |
| Deploy Internal ALBs | DevOps | Service routing ALB, Admin ALB |
| Implement VPC Endpoint IP automation | DevOps | Lambda + EventBridge |
| Configure Route 53 | DevOps | DNS records |

### Phase 4: Multi-Service Deployment (Week 7-8)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Create ECR repository | DevOps | Container registry |
| Deploy ECS cluster | DevOps | Fargate cluster |
| Deploy 4 ECS services | DevOps | fhir-api, fhir-metadata, fhir-actuator, fhir-admin |
| Configure auto-scaling | DevOps | Scaling policies |
| Create GitHub Actions workflow | DevOps | CI/CD pipeline |

### Phase 5: Testing and Validation (Week 9-10)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Run Flyway migrations | Backend | Database schema |
| Smoke test all endpoints | QA | Test results |
| Security scan (images, endpoints) | Security | Vulnerability report |
| Load testing | QA | Performance baseline |
| Set up CloudWatch dashboard | DevOps | Monitoring dashboard |
| Configure alarms | DevOps | Alert rules |

---

## 11. Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| VPC Endpoint IP change breaks routing | High | Low | Lambda automation with EventBridge |
| Cold start latency | Medium | Low | Pre-warm containers, keep-alive |
| IAM token refresh failure | High | Low | Connection retry with exponential backoff |
| Secrets rotation failure | High | Low | Monitor rotation, alert on failure |
| ElastiCache failover | Medium | Low | Multi-AZ, automatic failover |
| RDS failover delay | High | Low | Multi-AZ, connection retry logic |
| API Gateway throttling | Medium | Medium | Request proper limits, implement backoff |
| Cost overrun | Medium | Medium | Set billing alarms, right-size instances |
| Service isolation breach | High | Low | Strict security groups, endpoint filtering |

---

## 12. References

### AWS Documentation
- [Amazon ElastiCache Best Practices](https://aws.amazon.com/blogs/database/best-practices-valkey-redis-oss-clients-and-amazon-elasticache/)
- [Spring Boot with ElastiCache](https://aws.amazon.com/blogs/database/integrate-your-spring-boot-application-with-amazon-elasticache/)
- [AWS Secrets Manager JDBC](https://docs.aws.amazon.com/secretsmanager/latest/userguide/retrieving-secrets_jdbc.html)
- [RDS IAM Database Authentication](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.html)
- [Private API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-private-apis.html)
- [AWS CDK Best Practices](https://docs.aws.amazon.com/cdk/v2/guide/best-practices.html)
- [Distroless Container Images](https://github.com/GoogleContainerTools/distroless)

### Community Resources
- [RDS Authentication with Spring Boot - IAM Auth](https://chariotsolutions.com/blog/post/rds-database-authentication-with-spring-boot-part-2-iam-authentication/)
- [Spring Cloud AWS Redis](https://reflectoring.io/spring-cloud-aws-redis/)
- [EKS Security Best Practices](https://aws.github.io/aws-eks-best-practices/security/docs/)

### Internal References
- [CLAUDE.md](../CLAUDE.md) - Project context
- [FHIR4JAVA-IMPLEMENTATION-PLAN.md](../FHIR4JAVA-IMPLEMENTATION-PLAN.md) - Full implementation spec

---

## Appendix A: Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `aws,aws-iam` |
| `FHIR4JAVA_ENDPOINTS_ENABLED` | Endpoint filter | `fhir`, `metadata`, `actuator`, `admin`, `all` |
| `RDS_ENDPOINT` | RDS instance endpoint | `fhir4java.xxx.rds.amazonaws.com` |
| `RDS_PORT` | RDS port | `5432` |
| `RDS_DATABASE` | Database name | `fhir4java_prod` |
| `RDS_USERNAME` | Database user (IAM auth) | `fhir4java_app` |
| `RDS_SECRET_NAME` | Secrets Manager secret (Secrets auth) | `fhir4java/prod/rds` |
| `ELASTICACHE_ENDPOINT` | ElastiCache primary endpoint | `fhir4java.xxx.cache.amazonaws.com` |
| `ELASTICACHE_PORT` | ElastiCache port | `6379` |
| `ELASTICACHE_AUTH_TOKEN` | ElastiCache auth token | `<from-secrets>` |
| `API_GATEWAY_DOMAIN` | Custom domain | `fhir.example.com` |
| `AWS_REGION` | AWS region | `us-east-1` |

---

## Appendix B: Checklist

### Pre-Deployment Checklist

- [ ] VPC and subnets created
- [ ] Security groups configured
- [ ] VPC Endpoints deployed (execute-api, secretsmanager, etc.)
- [ ] RDS PostgreSQL deployed with IAM auth enabled
- [ ] ElastiCache cluster deployed
- [ ] Secrets Manager secrets created
- [ ] IAM roles and policies created (least privilege)
- [ ] VPC Endpoint IP automation Lambda deployed
- [ ] Public ALB + WAF deployed
- [ ] Private API Gateway with custom domain configured
- [ ] NLB (VPC Link target) deployed
- [ ] Internal ALBs deployed (service routing, admin)
- [ ] Route 53 DNS records configured
- [ ] ECR repository created with scan-on-push
- [ ] Container image built and pushed (distroless)
- [ ] ECS cluster created
- [ ] All 4 ECS services deployed
- [ ] Auto-scaling policies configured
- [ ] CloudWatch alarms configured
- [ ] Flyway migrations executed
- [ ] Smoke tests passed

### Post-Deployment Checklist

- [ ] All health checks passing
- [ ] fhir-api endpoints responding via public URL
- [ ] fhir-metadata endpoints responding
- [ ] fhir-actuator accessible only via VPN
- [ ] fhir-admin accessible only via VPN
- [ ] Database connections stable (IAM tokens refreshing)
- [ ] Cache operations working
- [ ] Logs flowing to CloudWatch
- [ ] Metrics visible in dashboard
- [ ] Alarms tested and working
- [ ] Security scan passed (no critical vulnerabilities)
- [ ] Documentation updated
