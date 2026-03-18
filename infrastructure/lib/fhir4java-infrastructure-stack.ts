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
  appName: string;
  environment: string;
  computePlatform?: 'ecs' | 'eks';
  domainName: string;
  certificateArn: string;
  vpnCidr: string;
  hostedZoneId: string;
}

export class Fhir4JavaInfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Fhir4JavaInfrastructureStackProps) {
    super(scope, id, props);

    // Resource naming prefix
    const resourcePrefix = `${props.appName}-${props.environment}`;

    // VPC
    const vpcConstruct = new VpcConstruct(this, 'Vpc', {
      resourcePrefix,
    });

    // RDS PostgreSQL
    const rdsConstruct = new RdsConstruct(this, 'Rds', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      iamAuthentication: true,
    });

    // ElastiCache
    const cacheConstruct = new ElastiCacheConstruct(this, 'Cache', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
    });

    // Internal ALB (Service Routing)
    const internalAlbConstruct = new InternalAlbConstruct(this, 'InternalAlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
    });

    // Admin ALB
    const adminAlbConstruct = new AdminAlbConstruct(this, 'AdminAlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      certificateArn: props.certificateArn,
      vpnCidr: props.vpnCidr,
    });

    // NLB (VPC Link Target)
    const nlbConstruct = new NlbConstruct(this, 'Nlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      internalAlb: internalAlbConstruct.alb,
    });

    // Private API Gateway
    const apiGatewayConstruct = new ApiGatewayConstruct(this, 'ApiGateway', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      nlbListener: nlbConstruct.listener,
      domainName: props.domainName,
      certificateArn: props.certificateArn,
    });

    // Public ALB
    const publicAlbConstruct = new PublicAlbConstruct(this, 'PublicAlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      certificateArn: props.certificateArn,
      domainName: props.domainName,
    });

    // VPC Endpoint IP Automation
    new VpcEndpointAutomationConstruct(this, 'VpcEndpointAutomation', {
      resourcePrefix,
      vpcEndpoint: vpcConstruct.apiGatewayEndpoint,
      targetGroup: publicAlbConstruct.targetGroup,
    });

    // ECR Repository
    const ecrRepository = new ecr.Repository(this, 'EcrRepository', {
      repositoryName: `${resourcePrefix}-app`,
      imageScanOnPush: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // ECS Services
    const ecsConstruct = new EcsConstruct(this, 'Ecs', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      ecrRepository,
      rdsEndpoint: rdsConstruct.instance.dbInstanceEndpointAddress,
      rdsSecretName: `${resourcePrefix}/rds`,
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
