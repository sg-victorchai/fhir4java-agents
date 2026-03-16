import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
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
            secretsmanager.Secret.fromSecretCompleteArn(this, `${svc.name}CacheSecret`, props.cacheSecretArn),
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
