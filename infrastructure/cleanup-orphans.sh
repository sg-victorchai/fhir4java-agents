#!/bin/bash
set -e

REGION="ap-southeast-1"

echo "=== Deleting orphan AWS resources ==="

# 1. Delete ECS cluster (must be empty first)
echo "Deleting ECS cluster..."
aws ecs delete-cluster --cluster fhir4java-dev-cluster --region $REGION || true

# 2. Delete RDS instance (disable deletion protection first)
echo "Disabling RDS deletion protection..."
aws rds modify-db-instance \
  --db-instance-identifier fhir4java-dev-postgres \
  --no-deletion-protection \
  --region $REGION || true

echo "Deleting RDS instance (this takes several minutes)..."
aws rds delete-db-instance \
  --db-instance-identifier fhir4java-dev-postgres \
  --skip-final-snapshot \
  --region $REGION || true

echo "Waiting for RDS instance to be deleted..."
aws rds wait db-instance-deleted \
  --db-instance-identifier fhir4java-dev-postgres \
  --region $REGION || true

# 3. Delete VPC endpoints
echo "Deleting VPC endpoints..."
for vpce in vpce-03bf07c84628e44bd vpce-0de76981450f97fd8 vpce-00883502891dfc437 vpce-08579e59acc0f9908 vpce-0f4ea7b3c7c439c38 vpce-059c75c1410face02; do
  aws ec2 delete-vpc-endpoints --vpc-endpoint-ids $vpce --region $REGION || true
done

# 4. Delete RDS subnet group
echo "Deleting RDS subnet group..."
aws rds delete-db-subnet-group \
  --db-subnet-group-name fhir4java-dev-stack-rdspostgresinstancesubnetgroup77e9dfbc-jhdiacsijy28 \
  --region $REGION || true

# 5. Delete RDS parameter group
echo "Deleting RDS parameter group..."
aws rds delete-db-parameter-group \
  --db-parameter-group-name fhir4java-dev-stack-rdsparametergroupc1ec0880-wrigzkck9q2i \
  --region $REGION || true

# 6. Delete subnets
echo "Deleting subnets..."
for subnet in subnet-0f2833ae77aa3618c subnet-07498cd3679500aad subnet-0a98bfcc40801bc06; do
  aws ec2 delete-subnet --subnet-id $subnet --region $REGION || true
done

# 7. Delete security group
echo "Deleting security group..."
aws ec2 delete-security-group --group-id sg-0403f741b227347c1 --region $REGION || true

# 8. Delete VPC (must be last)
echo "Deleting VPC..."
aws ec2 delete-vpc --vpc-id vpc-0630bdff2fa8d021a --region $REGION || true

echo "=== Cleanup complete ==="
