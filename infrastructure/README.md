# FHIR4Java Infrastructure

AWS CDK infrastructure for deploying the FHIR4Java server to AWS.

## Architecture Overview

This stack deploys:
- **VPC** with public/private subnets across 2 AZs
- **RDS PostgreSQL** (Multi-AZ) with IAM authentication
- **ElastiCache Redis** (cluster mode) for caching
- **ECS Fargate** services for FHIR API, metadata, actuator, and admin
- **Application Load Balancers** (public, internal, admin)
- **Network Load Balancer** for API Gateway VPC Link
- **API Gateway** (HTTP API) with VPC Link
- **ECR Repository** for container images
- **Route 53** DNS records

## Prerequisites

1. **AWS CLI** configured with appropriate credentials
2. **Node.js** 18+ and npm
3. **CDK CLI**: `npm install -g aws-cdk`
4. **Docker** for building container images
5. **ACM Certificate** for your domain (must be in the deployment region)
6. **Route 53 Hosted Zone** for your domain

## CDK Deploy Parameters

Pass parameters using `-c` or `--context` flag:

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `appName` | No | `fhir4java` | Application name prefix for resources |
| `environment` | No | `prod` | Environment name (e.g., dev, staging, prod) |
| `domainName` | No | `fhir.example.com` | Domain name for the FHIR server |
| `certificateArn` | **Yes** | - | ARN of ACM certificate for HTTPS |
| `hostedZoneId` | **Yes** | - | Route 53 hosted zone ID for DNS records |
| `vpnCidr` | No | `10.100.0.0/16` | CIDR range for VPN access to admin ALB |
| `dbAutoInit` | No | `true` | Enable database schema auto-initialization |
| `skipTaskDeployment` | No | `false` | Skip ECS task deployment (use for initial deploy when ECR is empty) |

### Example Deploy Command

**Initial deployment** (ECR empty, skip task deployment):
```bash
cdk deploy \
  -c appName=fhir4java \
  -c environment=dev \
  -c domainName=dev.openfhir.example.com \
  -c certificateArn=arn:aws:acm:us-east-1:123456789012:certificate/abc123 \
  -c hostedZoneId=Z1234567890ABC \
  -c skipTaskDeployment=true
```

**Subsequent deployments** (after image is in ECR):
```bash
cdk deploy \
  -c appName=fhir4java \
  -c environment=dev \
  -c domainName=dev.openfhir.example.com \
  -c certificateArn=arn:aws:acm:us-east-1:123456789012:certificate/abc123 \
  -c hostedZoneId=Z1234567890ABC \
  -c dbAutoInit=false
```

## Deployment Workflow

### Important: CDK Deploy Does NOT Build Your Application

`cdk deploy` only deploys **infrastructure** (VPC, RDS, ECS services, etc.). It does **not** compile or package your Java application.

### Full Deployment Steps (First Time)

```bash
# 1. Deploy infrastructure with skipTaskDeployment=true (ECR is empty)
cd infrastructure
npm install
cdk deploy -c appName=fhir4java -c environment=dev \
  -c domainName=dev.example.com \
  -c certificateArn=arn:aws:acm:... \
  -c hostedZoneId=Z... \
  -c skipTaskDeployment=true

# 2. Build and package the Java application
cd ..
./mvnw clean package -DskipTests

# 3. Build Docker image
docker compose build

# 4. Tag and push Docker image to ECR
# Get ECR repository URI from stack outputs
ECR_URI=$(aws cloudformation describe-stacks \
  --stack-name fhir4java-dev-stack \
  --query 'Stacks[0].Outputs[?OutputKey==`EcrRepositoryUri`].OutputValue' \
  --output text)

# Login to ECR
aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin $ECR_URI

# Tag and push (image name from docker compose is <project>-fhir-server)
docker tag fhir4java-agents-fhir-server:latest $ECR_URI:latest
docker push $ECR_URI:latest

# 5. Deploy again to start ECS tasks (now that image is in ECR)
cd infrastructure
cdk deploy -c appName=fhir4java -c environment=dev \
  -c domainName=dev.example.com \
  -c certificateArn=arn:aws:acm:... \
  -c hostedZoneId=Z...
```

### Updating an Existing Stack

Yes, you can run `cdk deploy` on an existing stack. CDK compares your current code against the deployed CloudFormation stack and applies only the differences (a "changeset").

**Infrastructure-only changes** (e.g., scaling, security groups):
```bash
cdk deploy -c appName=fhir4java -c environment=dev ...
```

**Application code changes** (requires new container image):
```bash
# Rebuild and push new image
./mvnw clean package -DskipTests
docker compose build
docker tag fhir4java-agents-fhir-server:latest $ECR_URI:latest
docker push $ECR_URI:latest

# Force ECS to redeploy with new image
aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment
```

## Database Initialization

The `dbAutoInit` parameter controls automatic database schema initialization:

- **First deployment**: Set `dbAutoInit=true` (default) to automatically create the FHIR schema
- **Subsequent deployments**: Set `dbAutoInit=false` to skip initialization

The initialization only runs if the `fhir` schema or `fhir_resource` table doesn't exist, so it's safe to leave enabled, but setting to `false` after initial deployment is recommended.

```bash
# First deployment
cdk deploy -c dbAutoInit=true ...

# Subsequent deployments
cdk deploy -c dbAutoInit=false ...
```

**Note:** Only the `fhir-api` service handles database initialization. The other services (`fhir-metadata`, `fhir-actuator`, `fhir-admin`) always have `DB_AUTO_INIT=false` to avoid race conditions when multiple containers start simultaneously.

## Useful Commands

| Command | Description |
|---------|-------------|
| `npm run build` | Compile TypeScript to JavaScript |
| `npm run watch` | Watch for changes and compile |
| `npm run test` | Run Jest unit tests |
| `cdk synth` | Emit synthesized CloudFormation template |
| `cdk diff` | Compare deployed stack with current state |
| `cdk deploy` | Deploy stack to AWS |
| `cdk destroy` | Delete the stack |

## Stack Outputs

After deployment, the stack outputs:

| Output | Description |
|--------|-------------|
| `VpcId` | VPC identifier |
| `RdsEndpoint` | RDS PostgreSQL endpoint |
| `CacheEndpoint` | ElastiCache configuration endpoint |
| `PublicAlbDns` | Public ALB DNS name |
| `ApiGatewayEndpoint` | API Gateway endpoint URL |
| `EcrRepositoryUri` | ECR repository URI for pushing images |

## Troubleshooting

### Bootstrap Required
If you see `SSM parameter /cdk-bootstrap/hnb659fds/version not found`:
```bash
cdk bootstrap aws://<account-id>/<region>
```

### Route 53 Domain Mismatch
Ensure `domainName` is within the hosted zone specified by `hostedZoneId`. For example, if your hosted zone is `example.com`, use a subdomain like `fhir.example.com`.

### RDS Deletion Protection
If stack deletion fails due to RDS deletion protection:
```bash
aws rds modify-db-instance \
  --db-instance-identifier <db-instance-id> \
  --no-deletion-protection
```

### Force Delete CloudWatch Log Groups
If deployment fails because log groups already exist:
```bash
aws logs delete-log-group --log-group-name /ecs/fhir4java-dev
aws logs delete-log-group --log-group-name /aws/lambda/fhir4java-dev-vpce-target-updater
```
