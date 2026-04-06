# FHIR4Java AWS Deployment Architecture

## Overview

This document describes the simplified AWS deployment architecture for FHIR4Java, using a single ECS Fargate service behind a private API Gateway with VPC Link integration.

## Architecture Diagram

```
                                    ┌─────────────────────────────────────────────────────────────────┐
                                    │                           AWS VPC                               │
                                    │                        (10.0.0.0/16)                            │
┌──────────┐                        │                                                                 │
│          │                        │  ┌─────────────────────────────────────────────────────────┐   │
│ Internet │                        │  │                    Public Subnets                        │   │
│          │                        │  │                                                          │   │
└────┬─────┘                        │  │   ┌─────────────────┐                                    │   │
     │                              │  │   │   Public ALB    │◄── Route 53 A Record (ALIAS)       │   │
     │ HTTPS (443)                  │  │   │  (Internet-     │    (fhir4java-dev.example.com)     │   │
     │                              │  │   │   facing)       │                                    │   │
     ▼                              │  │   └────────┬────────┘                                    │   │
┌─────────────┐                     │  │            │                                             │   │
│   Route 53  │                     │  │            │ HTTPS (443)                                 │   │
│  DNS Record │                     │  │            ▼                                             │   │
└─────────────┘                     │  │   ┌─────────────────┐                                    │   │
                                    │  │   │  VPC Endpoint   │◄── Lambda updates target IPs       │   │
                                    │  │   │  Target Group   │                                    │   │
                                    │  │   │  (vpce-tg)      │                                    │   │
                                    │  │   └────────┬────────┘                                    │   │
                                    │  └────────────┼─────────────────────────────────────────────┘   │
                                    │               │                                                 │
                                    │  ┌────────────┼─────────────────────────────────────────────┐   │
                                    │  │            │           Private Subnets                    │   │
                                    │  │            ▼                                              │   │
                                    │  │   ┌─────────────────┐                                     │   │
                                    │  │   │  VPC Endpoint   │◄── API Gateway VPC Endpoint         │   │
                                    │  │   │  (execute-api)  │    (Private DNS enabled)            │   │
                                    │  │   └────────┬────────┘                                     │   │
                                    │  │            │                                              │   │
                                    │  │            ▼                                              │   │
                                    │  │   ┌─────────────────┐                                     │   │
                                    │  │   │  API Gateway    │◄── REST API (PRIVATE)               │   │
                                    │  │   │  (Private)      │    Routes: /fhir/*, /actuator/*,    │   │
                                    │  │   │                 │           /api/admin/*              │   │
                                    │  │   └────────┬────────┘                                     │   │
                                    │  │            │                                              │   │
                                    │  │            │ VPC Link (Legacy)                            │   │
                                    │  │            ▼                                              │   │
                                    │  │   ┌─────────────────┐                                     │   │
                                    │  │   │      NLB        │◄── Network Load Balancer            │   │
                                    │  │   │   (Internal)    │    TCP Listener on port 80          │   │
                                    │  │   │                 │                                     │   │
                                    │  │   └────────┬────────┘                                     │   │
                                    │  │            │                                              │   │
                                    │  │            │ TCP (80)                                     │   │
                                    │  │            ▼                                              │   │
                                    │  │   ┌─────────────────┐                                     │   │
                                    │  │   │  Internal ALB   │◄── Application Load Balancer        │   │
                                    │  │   │   (Internal)    │    HTTP Listener on port 80         │   │
                                    │  │   │                 │                                     │   │
                                    │  │   └────────┬────────┘                                     │   │
                                    │  │            │                                              │   │
                                    │  │            │ HTTP (8080)                                  │   │
                                    │  │            ▼                                              │   │
                                    │  │   ┌─────────────────┐                                     │   │
                                    │  │   │   ECS Fargate   │◄── Single Service: fhir-api         │   │
                                    │  │   │    Service      │    4 vCPU, 8 GB Memory              │   │
                                    │  │   │                 │    Endpoints: fhir, metadata,       │   │
                                    │  │   │                 │               actuator, admin       │   │
                                    │  │   └────────┬────────┘                                     │   │
                                    │  │            │                                              │   │
                                    │  └────────────┼──────────────────────────────────────────────┘   │
                                    │               │                                                  │
                                    │  ┌────────────┼──────────────────────────────────────────────┐   │
                                    │  │            │          Isolated Subnets                     │   │
                                    │  │            ▼                                               │   │
                                    │  │   ┌─────────────────┐    ┌─────────────────┐               │   │
                                    │  │   │  RDS PostgreSQL │    │   ElastiCache   │               │   │
                                    │  │   │    (Primary)    │    │     Redis       │               │   │
                                    │  │   │                 │    │   (Cluster)     │               │   │
                                    │  │   └─────────────────┘    └─────────────────┘               │   │
                                    │  │                                                            │   │
                                    │  └────────────────────────────────────────────────────────────┘   │
                                    │                                                                   │
                                    └───────────────────────────────────────────────────────────────────┘
```

## Traffic Flow

### Inbound Request Flow

1. **DNS Resolution**: Client resolves `fhir4java-dev.example.com` via Route 53
2. **Public ALB**: Request hits the internet-facing Public ALB (HTTPS:443)
3. **VPC Endpoint Target Group**: Public ALB forwards to VPC Endpoint ENI IPs (HTTPS:443)
4. **API Gateway VPC Endpoint**: Traffic enters the VPC through the execute-api endpoint
5. **Private REST API**: API Gateway processes the request and routes based on path
6. **VPC Link (Legacy)**: API Gateway uses VPC Link to forward to NLB
7. **NLB**: Network Load Balancer receives TCP traffic on port 80
8. **Internal ALB**: Application Load Balancer receives HTTP traffic on port 80
9. **ECS Service**: Request reaches the FHIR4Java container on port 8080

### Health Check Flows

| Component | Target | Protocol | Port | Path |
|-----------|--------|----------|------|------|
| Public ALB → VPC Endpoint | VPC Endpoint ENI IPs | HTTPS | 443 | /fhir/r5/metadata |
| NLB → Internal ALB | Internal ALB | TCP | 80 | - |
| Internal ALB → ECS | ECS Tasks | HTTP | 8080 | /actuator/health |
| ECS Container | localhost | HTTP | 8080 | /actuator/health |

## Components

### Networking

| Component | Name | Type | Description |
|-----------|------|------|-------------|
| VPC | fhir4java-dev-vpc | 10.0.0.0/16 | Main VPC with 3 AZs |
| Public Subnets | fhir4java-dev-public-* | 10.0.0.0/24, etc. | For Public ALB, NAT Gateway |
| Private Subnets | fhir4java-dev-private-* | 10.0.3.0/24, etc. | For NLB, Internal ALB, ECS, VPC Endpoints |
| Isolated Subnets | fhir4java-dev-database-* | 10.0.6.0/24, etc. | For RDS, ElastiCache |

### Load Balancers

| Component | Name | Type | Scheme | Listeners |
|-----------|------|------|--------|-----------|
| Public ALB | fhir4java-dev-public-alb | Application | Internet-facing | HTTPS:443 |
| Internal ALB | fhir4java-dev-internal-alb | Application | Internal | HTTP:80 |
| NLB | fhir4java-dev-nlb | Network | Internal | TCP:80 |

### Target Groups

| Name | Type | Port | Protocol | Targets |
|------|------|------|----------|---------|
| fhir4java-dev-vpce-tg | IP | 443 | HTTPS | VPC Endpoint ENI IPs |
| fhir4java-dev-nlb-alb-tg | ALB | 80 | TCP | Internal ALB |
| fhir4java-dev-ecs-tg | IP | 8080 | HTTP | ECS Task IPs |

### API Gateway

| Setting | Value |
|---------|-------|
| Type | REST API |
| Endpoint Type | PRIVATE |
| VPC Endpoint | execute-api VPC Endpoint |
| VPC Link | VPC Link (Legacy) targeting NLB |
| Stage | Matches environment (e.g., `dev`, `prod`) |
| Routes | /fhir/*, /actuator/*, /api/admin/* |

#### Custom Domain Configuration

| Setting | Value |
|---------|-------|
| Domain Name | `{appName}-{environment}.{hostedZoneName}` (e.g., `fhir4java-dev.seedideation.com`) |
| Endpoint Type | REGIONAL |
| Security Policy | TLS 1.2 |
| Certificate | ACM certificate (same as `certificateArn` context parameter) |
| Base Path Mapping | Maps to REST API deployment stage |

The custom domain is configured with a REGIONAL endpoint type and mapped to the PRIVATE REST API via base path mapping. Route 53 can point to the custom domain's regional domain name.

### ECS Service

| Setting | Value |
|---------|-------|
| Service Name | fhir4java-dev-fhir-api |
| Launch Type | Fargate |
| CPU Architecture | ARM64 |
| CPU | 4096 (4 vCPU) |
| Memory | 8192 MiB (8 GB) |
| Desired Count | 2 |
| Min Count | 2 |
| Max Count | 10 |
| Container Port | 8080 |
| Enabled Endpoints | fhir, metadata, actuator, admin |

### Database & Cache

| Component | Name | Type | Description |
|-----------|------|------|-------------|
| RDS | fhir4java-dev-postgres | PostgreSQL 16 | Primary database |
| ElastiCache | fhir4java-dev-cache | Redis 7.x Cluster | Session/cache storage |

## Security Groups

### Security Group Rules

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Security Group Matrix                               │
├──────────────────────┬──────────────────────┬───────┬────────────────────────┤
│ Source               │ Destination          │ Port  │ Description            │
├──────────────────────┼──────────────────────┼───────┼────────────────────────┤
│ 0.0.0.0/0            │ Public ALB SG        │ 443   │ HTTPS from internet    │
│ Public ALB SG        │ VPC Endpoint SG      │ 443   │ To API Gateway         │
│ 0.0.0.0/0            │ NLB SG               │ 80    │ HTTP from VPC Link     │
│ ::/0                 │ NLB SG               │ 80    │ HTTP from VPC Link     │
│ NLB SG               │ Internal ALB SG      │ 80    │ NLB to ALB (egress)    │
│ Internal ALB SG      │ ECS Tasks SG         │ 8080  │ ALB to ECS             │
│ ECS Tasks SG         │ RDS SG               │ 5432  │ ECS to PostgreSQL      │
│ ECS Tasks SG         │ ElastiCache SG       │ 6379  │ ECS to Redis           │
└──────────────────────┴──────────────────────┴───────┴────────────────────────┘
```

> **Note:** NLB security group has `allowAllOutbound: false` with explicit egress rule to Internal ALB security group on port 80.

## VPC Endpoints

| Service | Type | Description |
|---------|------|-------------|
| execute-api | Interface | API Gateway private endpoint |
| secretsmanager | Interface | Secrets Manager access |
| ecr.api | Interface | ECR API access |
| ecr.dkr | Interface | ECR Docker registry access |
| logs | Interface | CloudWatch Logs access |
| s3 | Gateway | S3 access |

## Deployment

### Prerequisites

1. AWS CLI configured with appropriate credentials
2. Node.js 18+ and npm installed
3. Docker installed (for building container images)
4. Route 53 hosted zone with domain
5. ACM certificate for the domain

### CDK Context Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| appName | No | fhir4java | Application name prefix |
| environment | No | prod | Environment (dev, staging, prod) |
| hostedZoneName | Yes | - | Route 53 hosted zone name (e.g., example.com) |
| hostedZoneId | Yes | - | Route 53 hosted zone ID |
| certificateArn | Yes | - | ACM certificate ARN |
| subdomainPrefix | No | {appName}-{environment} | Subdomain prefix |
| dbAutoInit | No | false | Initialize database on first deployment |
| skipTaskDeployment | No | false | Skip ECS task deployment (for initial ECR setup) |

### Deployment Commands

```bash
# Initial deployment (ECR empty, skip ECS tasks)
npx cdk deploy \
  --context appName=fhir4java \
  --context environment=dev \
  --context hostedZoneName=example.com \
  --context hostedZoneId=Z1234567890ABC \
  --context certificateArn=arn:aws:acm:us-east-1:123456789012:certificate/abc-123 \
  --context skipTaskDeployment=true \
  --context dbAutoInit=true

# Build and push Docker image
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
docker build -t fhir4java-dev-app -f docker/Dockerfile .
docker tag fhir4java-dev-app:latest <account>.dkr.ecr.<region>.amazonaws.com/fhir4java-dev-app:latest
docker push <account>.dkr.ecr.<region>.amazonaws.com/fhir4java-dev-app:latest

# Full deployment with ECS tasks
npx cdk deploy \
  --context appName=fhir4java \
  --context environment=dev \
  --context hostedZoneName=example.com \
  --context hostedZoneId=Z1234567890ABC \
  --context certificateArn=arn:aws:acm:us-east-1:123456789012:certificate/abc-123
```

### Verification Commands

```bash
# Check ECS service status
aws ecs describe-services \
  --cluster fhir4java-dev-cluster \
  --services fhir4java-dev-fhir-api \
  --query 'services[0].{desired:desiredCount,running:runningCount,status:status}'

# Check target group health
aws elbv2 describe-target-health \
  --target-group-arn $(aws elbv2 describe-target-groups \
    --names fhir4java-dev-ecs-tg \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text)

# Check NLB to ALB health
aws elbv2 describe-target-health \
  --target-group-arn $(aws elbv2 describe-target-groups \
    --names fhir4java-dev-nlb-alb-tg \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text)

# Test endpoint
curl https://fhir4java-dev.example.com/actuator/health
curl https://fhir4java-dev.example.com/fhir/r5/metadata
```

## Scaling

### Auto-Scaling Configuration

| Metric | Target | Scale Out Cooldown | Scale In Cooldown |
|--------|--------|-------------------|-------------------|
| CPU Utilization | 70% | 60 seconds | 60 seconds |

### Manual Scaling

```bash
# Scale ECS service
aws ecs update-service \
  --cluster fhir4java-dev-cluster \
  --service fhir4java-dev-fhir-api \
  --desired-count 4
```

## Monitoring

### CloudWatch Log Groups

| Log Group | Description |
|-----------|-------------|
| /ecs/fhir4java-dev | ECS container logs |
| /aws/lambda/fhir4java-dev-vpce-target-updater | VPC Endpoint IP updater Lambda |

### Key Metrics

| Namespace | Metric | Description |
|-----------|--------|-------------|
| AWS/ApplicationELB | TargetResponseTime | Response time from ECS |
| AWS/ApplicationELB | HTTPCode_Target_2XX_Count | Successful requests |
| AWS/ECS | CPUUtilization | ECS CPU usage |
| AWS/ECS | MemoryUtilization | ECS memory usage |
| AWS/RDS | DatabaseConnections | RDS connection count |

## Cost Optimization

### Estimated Monthly Costs (us-east-1)

| Component | Configuration | Estimated Cost |
|-----------|---------------|----------------|
| ECS Fargate | 2 tasks x 4 vCPU x 8 GB | ~$290/month |
| NAT Gateway | 1 per VPC | ~$45/month |
| RDS PostgreSQL | db.t3.medium | ~$50/month |
| ElastiCache Redis | cache.t3.micro cluster | ~$25/month |
| Application Load Balancers | 2 (Public + Internal) | ~$40/month |
| Network Load Balancer | 1 | ~$20/month |
| API Gateway | REST API | ~$3.50/million requests |
| **Total (baseline)** | | **~$470/month** |

## Troubleshooting

### Common Issues

1. **NLB Target Unhealthy**
   - Check NLB security group allows port 80
   - Check Internal ALB security group allows traffic from NLB SG
   - Verify both load balancers are in same subnets

2. **ECS Tasks Not Starting**
   - Check ECR has the image
   - Verify ECS task execution role has ECR permissions
   - Check CloudWatch logs for container errors

3. **API Gateway 500 Errors**
   - Check VPC Link status
   - Verify NLB listener is healthy
   - Check API Gateway execution logs

4. **Health Check Failures**
   - Verify /actuator/health endpoint returns 200
   - Check container health check command uses `curl` (not `wget`)
   - Increase health check start period for slow-starting apps

### Diagnostic Commands

```bash
# Check VPC Endpoint IPs
aws ec2 describe-network-interfaces \
  --filters "Name=description,Values=*execute-api*" \
  --query 'NetworkInterfaces[*].PrivateIpAddress'

# Check NLB ENI IPs
aws ec2 describe-network-interfaces \
  --filters "Name=description,Values=*fhir4java-dev-nlb*" \
  --query 'NetworkInterfaces[*].PrivateIpAddress'

# View ECS task logs
aws logs tail /ecs/fhir4java-dev --since 1h --follow

# Execute command in container
aws ecs execute-command \
  --cluster fhir4java-dev-cluster \
  --task <task-id> \
  --container fhir-apiContainer \
  --interactive \
  --command "/bin/sh"
```
