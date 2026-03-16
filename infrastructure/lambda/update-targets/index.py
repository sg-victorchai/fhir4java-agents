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
