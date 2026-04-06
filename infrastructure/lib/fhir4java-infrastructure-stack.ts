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
import { ApiGatewayConstruct } from './constructs/api-gateway-construct';
import { VpcEndpointAutomationConstruct } from './constructs/vpc-endpoint-automation-construct';
import { EcsConstruct } from './constructs/ecs-construct';

export interface Fhir4JavaInfrastructureStackProps extends cdk.StackProps {
  appName: string;
  environment: string;
  computePlatform?: 'ecs' | 'eks';
  /** Full domain name constructed from subdomainPrefix + hostedZoneName (e.g., 'fhir4java-dev.example.com') */
  domainName: string;
  /** Base domain name of the Route 53 hosted zone (e.g., 'example.com') */
  hostedZoneName: string;
  certificateArn: string;
  hostedZoneId: string;
  /** Enable database auto-initialization on first deployment. Default: false */
  dbAutoInit?: boolean;
  /** Skip ECS task deployment (set desiredCount to 0). Use for initial deployment when ECR is empty. */
  skipTaskDeployment?: boolean;
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

    // Internal ALB (Service Routing for all endpoints: fhir, metadata, actuator, admin)
    const internalAlbConstruct = new InternalAlbConstruct(this, 'InternalAlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
    });

    // NLB (VPC Link Target)
    const nlbConstruct = new NlbConstruct(this, 'Nlb', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      internalAlb: internalAlbConstruct.alb,
    });

    // Private API Gateway (REST API)
    const apiGatewayConstruct = new ApiGatewayConstruct(this, 'ApiGateway', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      vpcEndpoint: vpcConstruct.apiGatewayEndpoint,
      nlb: nlbConstruct.nlb,
      domainName: props.domainName,
      certificateArn: props.certificateArn,
      environment: props.environment,
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
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      emptyOnDelete: true,
    });

    // Database name follows the same pattern as RDS construct
    const rdsDatabase = `${resourcePrefix.replace(/-/g, '_')}_db`;

    // ECS Services
    const ecsConstruct = new EcsConstruct(this, 'Ecs', {
      resourcePrefix,
      vpc: vpcConstruct.vpc,
      ecrRepository,
      rdsEndpoint: rdsConstruct.instance.dbInstanceEndpointAddress,
      rdsDatabase,
      rdsSecretName: `${resourcePrefix}/rds`,
      rdsSecretArn: rdsConstruct.secret.secretArn,
      cacheEndpoint: cacheConstruct.cluster.attrConfigurationEndPointAddress,
      cacheSecretArn: cacheConstruct.secret.secretArn,
      apiGatewayDomain: props.domainName,
      dbAutoInit: props.dbAutoInit ?? false,
      skipTaskDeployment: props.skipTaskDeployment ?? false,
      services: [
        {
          name: 'fhir-api',
          enabledEndpoints: 'fhir,metadata,actuator,admin',  // All endpoints in one container
          desiredCount: 2,
          minCount: 2,
          maxCount: 10,
          cpu: 4096,   // 4 vCPU
          memory: 8192,  // 8 GB - single container handling all endpoints
          targetGroup: internalAlbConstruct.targetGroup,
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

    // NLB needs to send traffic to Internal ALB on port 80 (egress)
    nlbConstruct.securityGroup.addEgressRule(
      internalAlbConstruct.securityGroup,
      ec2.Port.tcp(80),
      'Allow HTTP to Internal ALB'
    );

    // Internal ALB needs to receive traffic from NLB security group (ingress)
    internalAlbConstruct.securityGroup.addIngressRule(
      nlbConstruct.securityGroup,
      ec2.Port.tcp(80),
      'Allow HTTP from NLB'
    );

    // ECS tasks need to receive traffic from Internal ALB on port 8080
    ecsConstruct.taskSecurityGroup.addIngressRule(
      internalAlbConstruct.securityGroup,
      ec2.Port.tcp(8080),
      'Allow traffic from Internal ALB'
    );

    // Route 53
    const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'HostedZone', {
      hostedZoneId: props.hostedZoneId,
      zoneName: props.hostedZoneName,
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
    new cdk.CfnOutput(this, 'CacheEndpoint', { value: cacheConstruct.cluster.attrConfigurationEndPointAddress });
    new cdk.CfnOutput(this, 'PublicAlbDns', { value: publicAlbConstruct.alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'ApiGatewayEndpoint', { value: apiGatewayConstruct.restApi.url });
    new cdk.CfnOutput(this, 'EcrRepositoryUri', { value: ecrRepository.repositoryUri });
  }
}
