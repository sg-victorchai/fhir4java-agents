#!/bin/bash
# Cleanup orphan resources from fhir4java-dev-stack
# This script removes resources that were created by CDK but are no longer managed
# after simplifying to single ECS service and single target group architecture

set -e

# Configuration
STACK_NAME="${1:-fhir4java-dev-stack}"
RESOURCE_PREFIX="${2:-fhir4java-dev}"
REGION="${AWS_REGION:-ap-southeast-1}"
DRY_RUN="${DRY_RUN:-true}"

echo "=============================================="
echo "Orphan Resource Cleanup Script"
echo "=============================================="
echo "Stack Name: $STACK_NAME"
echo "Resource Prefix: $RESOURCE_PREFIX"
echo "Region: $REGION"
echo "Dry Run: $DRY_RUN"
echo "=============================================="
echo ""

if [ "$DRY_RUN" = "true" ]; then
    echo "*** DRY RUN MODE - No resources will be deleted ***"
    echo "Set DRY_RUN=false to actually delete resources"
    echo ""
fi

# Get ECS cluster name
CLUSTER_NAME="${RESOURCE_PREFIX}-cluster"

echo "=== Checking ECS Services ==="
echo ""

# Orphan ECS services (removed in simplified architecture)
ORPHAN_SERVICES=(
    "${RESOURCE_PREFIX}-fhir-metadata"
    "${RESOURCE_PREFIX}-fhir-actuator"
    "${RESOURCE_PREFIX}-fhir-admin"
)

# Check if cluster exists
if ! aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$REGION" --query 'clusters[0].clusterArn' --output text 2>/dev/null | grep -q "arn:aws"; then
    echo "Cluster $CLUSTER_NAME not found. Skipping ECS cleanup."
else
    echo "Cluster: $CLUSTER_NAME"
    echo ""

    # List all services in the cluster
    echo "Current services in cluster:"
    aws ecs list-services --cluster "$CLUSTER_NAME" --region "$REGION" --query 'serviceArns[*]' --output table 2>/dev/null || echo "  (none)"
    echo ""

    # Delete orphan ECS services
    for SERVICE in "${ORPHAN_SERVICES[@]}"; do
        echo "Checking service: $SERVICE"

        # Check if service exists
        SERVICE_STATUS=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE" --region "$REGION" --query 'services[0].status' --output text 2>/dev/null || echo "NOT_FOUND")

        if [ "$SERVICE_STATUS" != "NOT_FOUND" ] && [ "$SERVICE_STATUS" != "None" ] && [ "$SERVICE_STATUS" != "INACTIVE" ]; then
            echo "  Found orphan service: $SERVICE (status: $SERVICE_STATUS)"

            if [ "$DRY_RUN" = "false" ]; then
                echo "  Scaling down service to 0..."
                aws ecs update-service --cluster "$CLUSTER_NAME" --service "$SERVICE" --desired-count 0 --region "$REGION" > /dev/null

                echo "  Waiting for tasks to stop..."
                aws ecs wait services-stable --cluster "$CLUSTER_NAME" --services "$SERVICE" --region "$REGION" 2>/dev/null || true

                echo "  Deleting service..."
                aws ecs delete-service --cluster "$CLUSTER_NAME" --service "$SERVICE" --region "$REGION" > /dev/null
                echo "  Deleted: $SERVICE"
            else
                echo "  [DRY RUN] Would delete service: $SERVICE"
            fi
        else
            echo "  Service not found or already inactive"
        fi
        echo ""
    done
fi

echo "=== Checking Orphan Task Definitions ==="
echo ""

# List task definitions with our prefix
TASK_DEF_FAMILIES=$(aws ecs list-task-definition-families --family-prefix "$RESOURCE_PREFIX" --status ACTIVE --region "$REGION" --query 'families[*]' --output text 2>/dev/null || echo "")

for FAMILY in $TASK_DEF_FAMILIES; do
    # Check for orphan service task definitions
    IS_ORPHAN=false
    for ORPHAN in "fhir-metadata" "fhir-actuator" "fhir-admin"; do
        if [[ "$FAMILY" == *"$ORPHAN"* ]]; then
            IS_ORPHAN=true
            break
        fi
    done

    if [ "$IS_ORPHAN" = true ]; then
        echo "Found orphan task definition family: $FAMILY"

        # Get all revisions
        REVISIONS=$(aws ecs list-task-definitions --family-prefix "$FAMILY" --status ACTIVE --region "$REGION" --query 'taskDefinitionArns[*]' --output text 2>/dev/null || echo "")

        for REVISION in $REVISIONS; do
            if [ "$DRY_RUN" = "false" ]; then
                echo "  Deregistering: $REVISION"
                aws ecs deregister-task-definition --task-definition "$REVISION" --region "$REGION" > /dev/null
            else
                echo "  [DRY RUN] Would deregister: $REVISION"
            fi
        done
        echo ""
    fi
done

echo "=== Checking Orphan Target Groups ==="
echo ""

# Target groups that should exist (simplified architecture)
KEEP_TARGET_GROUPS=(
    "${RESOURCE_PREFIX}-ecs-tg"      # New single target group for Internal ALB
    "${RESOURCE_PREFIX}-public-tg"   # Public ALB target group
)

# Orphan target groups (removed in simplified architecture)
ORPHAN_TARGET_GROUPS=(
    "${RESOURCE_PREFIX}-fhir-api-tg"   # Old Internal ALB target group
    "${RESOURCE_PREFIX}-fhir-meta-tg"  # Old Internal ALB target group
    "${RESOURCE_PREFIX}-actuator-tg"   # Old Admin ALB target group
    "${RESOURCE_PREFIX}-admin-tg"      # Old Admin ALB target group
)

for TG_NAME in "${ORPHAN_TARGET_GROUPS[@]}"; do
    echo "Checking target group: $TG_NAME"

    # Get target group ARN
    TG_ARN=$(aws elbv2 describe-target-groups --names "$TG_NAME" --region "$REGION" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo "NOT_FOUND")

    if [ "$TG_ARN" != "NOT_FOUND" ] && [ "$TG_ARN" != "None" ]; then
        echo "  Found orphan target group: $TG_NAME"

        if [ "$DRY_RUN" = "false" ]; then
            echo "  Deleting target group..."
            aws elbv2 delete-target-group --target-group-arn "$TG_ARN" --region "$REGION" 2>/dev/null && echo "  Deleted: $TG_NAME" || echo "  Failed to delete (may be in use by listener rules)"
        else
            echo "  [DRY RUN] Would delete target group: $TG_NAME"
        fi
    else
        echo "  Target group not found"
    fi
    echo ""
done

echo "=== Checking Orphan Load Balancers ==="
echo ""

# Admin ALB (removed in simplified architecture)
ADMIN_ALB_NAME="${RESOURCE_PREFIX}-admin-alb"

echo "Checking load balancer: $ADMIN_ALB_NAME"

ALB_ARN=$(aws elbv2 describe-load-balancers --names "$ADMIN_ALB_NAME" --region "$REGION" --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo "NOT_FOUND")

if [ "$ALB_ARN" != "NOT_FOUND" ] && [ "$ALB_ARN" != "None" ]; then
    echo "  Found orphan load balancer: $ADMIN_ALB_NAME"

    if [ "$DRY_RUN" = "false" ]; then
        # First delete listeners
        echo "  Deleting listeners..."
        LISTENERS=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" --region "$REGION" --query 'Listeners[*].ListenerArn' --output text 2>/dev/null || echo "")
        for LISTENER in $LISTENERS; do
            aws elbv2 delete-listener --listener-arn "$LISTENER" --region "$REGION" 2>/dev/null || true
        done

        # Then delete load balancer
        echo "  Deleting load balancer..."
        aws elbv2 delete-load-balancer --load-balancer-arn "$ALB_ARN" --region "$REGION" 2>/dev/null && echo "  Deleted: $ADMIN_ALB_NAME" || echo "  Failed to delete"
    else
        echo "  [DRY RUN] Would delete load balancer: $ADMIN_ALB_NAME"
    fi
else
    echo "  Load balancer not found"
fi
echo ""

echo "=== Checking Orphan Security Groups ==="
echo ""

# Admin ALB security group (removed in simplified architecture)
ADMIN_SG_NAME="${RESOURCE_PREFIX}-admin-alb-sg"

echo "Checking security group: $ADMIN_SG_NAME"

SG_ID=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$ADMIN_SG_NAME" --region "$REGION" --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "NOT_FOUND")

if [ "$SG_ID" != "NOT_FOUND" ] && [ "$SG_ID" != "None" ]; then
    echo "  Found orphan security group: $ADMIN_SG_NAME ($SG_ID)"

    if [ "$DRY_RUN" = "false" ]; then
        echo "  Deleting security group..."
        # Wait a bit for ALB deletion to propagate
        sleep 5
        aws ec2 delete-security-group --group-id "$SG_ID" --region "$REGION" 2>/dev/null && echo "  Deleted: $ADMIN_SG_NAME" || echo "  Failed to delete (may still be in use)"
    else
        echo "  [DRY RUN] Would delete security group: $ADMIN_SG_NAME"
    fi
else
    echo "  Security group not found"
fi
echo ""

echo "=== Checking CloudWatch Log Streams ==="
echo ""

LOG_GROUP="/ecs/${RESOURCE_PREFIX}"

# Check if log group exists
if aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --region "$REGION" --query 'logGroups[0].logGroupName' --output text 2>/dev/null | grep -q "$LOG_GROUP"; then
    echo "Log group: $LOG_GROUP"

    # List log streams for orphan services
    for ORPHAN in "fhir-metadata" "fhir-actuator" "fhir-admin"; do
        STREAMS=$(aws logs describe-log-streams --log-group-name "$LOG_GROUP" --log-stream-name-prefix "$ORPHAN" --region "$REGION" --query 'logStreams[*].logStreamName' --output text 2>/dev/null || echo "")

        STREAM_COUNT=$(echo "$STREAMS" | wc -w | tr -d ' ')
        if [ "$STREAM_COUNT" -gt 0 ] && [ -n "$STREAMS" ]; then
            echo "Found $STREAM_COUNT log streams for orphan service: $ORPHAN"

            if [ "$DRY_RUN" = "false" ]; then
                for STREAM in $STREAMS; do
                    echo "  Deleting log stream: $STREAM"
                    aws logs delete-log-stream --log-group-name "$LOG_GROUP" --log-stream-name "$STREAM" --region "$REGION" 2>/dev/null || true
                done
            else
                echo "  [DRY RUN] Would delete $STREAM_COUNT log streams"
            fi
        fi
    done
else
    echo "Log group $LOG_GROUP not found"
fi

echo ""
echo "=============================================="
echo "Cleanup Summary"
echo "=============================================="
echo ""
echo "Resources checked for cleanup:"
echo "  - ECS Services: fhir-metadata, fhir-actuator, fhir-admin"
echo "  - Task Definitions: orphan families"
echo "  - Target Groups: fhir-api-tg, fhir-meta-tg, actuator-tg, admin-tg"
echo "  - Load Balancers: admin-alb"
echo "  - Security Groups: admin-alb-sg"
echo "  - Log Streams: orphan service logs"
echo ""

if [ "$DRY_RUN" = "true" ]; then
    echo "This was a DRY RUN. To actually delete resources, run:"
    echo ""
    echo "  DRY_RUN=false $0 $STACK_NAME $RESOURCE_PREFIX"
    echo ""
fi

echo "Done!"
