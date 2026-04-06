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
  /** Target group for ALB routing */
  targetGroup: elbv2.IApplicationTargetGroup;
}

export interface EcsConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
  services: ServiceConfig[];
  ecrRepository: ecr.IRepository;
  rdsEndpoint: string;
  rdsDatabase: string;
  rdsSecretName: string;
  rdsSecretArn: string;
  cacheEndpoint: string;
  cacheSecretArn: string;
  apiGatewayDomain: string;
  /** Enable database auto-initialization. Default: false. Set to true only for first deployment. */
  dbAutoInit?: boolean;
  /** Set to true for initial deployment when ECR is empty. Sets desiredCount to 0. */
  skipTaskDeployment?: boolean;
}

export class EcsConstruct extends Construct {
  public readonly cluster: ecs.Cluster;
  public readonly services: Map<string, ecs.FargateService>;
  public readonly taskSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: EcsConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    this.cluster = new ecs.Cluster(this, 'Cluster', {
      clusterName: `${prefix}-cluster`,
      vpc: props.vpc,
      containerInsights: true,
    });

    this.taskSecurityGroup = new ec2.SecurityGroup(this, 'TaskSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-ecs-tasks-sg`,
      description: `Security group for ${prefix} ECS tasks`,
      allowAllOutbound: true,
    });

    // Task execution role
    const executionRole = new iam.Role(this, 'ExecutionRole', {
      roleName: `${prefix}-ecs-execution-role`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy'),
      ],
    });

    // Task role with least privilege
    const taskRole = new iam.Role(this, 'TaskRole', {
      roleName: `${prefix}-ecs-task-role`,
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });

    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['rds-db:connect'],
      resources: [`arn:aws:rds-db:*:*:dbuser:*/${prefix.replace(/-/g, '_')}_app`],
    }));

    taskRole.addToPolicy(new iam.PolicyStatement({
      actions: ['secretsmanager:GetSecretValue'],
      resources: [props.cacheSecretArn, props.rdsSecretArn],
    }));

    // Log group
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: `/ecs/${prefix}`,
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
        runtimePlatform: {
          cpuArchitecture: ecs.CpuArchitecture.ARM64,
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        },
      });

      // Linux parameters with tmpfs for writable /tmp (works with read-only root filesystem)
      const linuxParameters = new ecs.LinuxParameters(this, `${svc.name}LinuxParams`);
      linuxParameters.addTmpfs({
        containerPath: '/tmp',
        size: 256, // 256 MiB
        mountOptions: [ecs.TmpfsMountOption.RW],
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
          RDS_DATABASE: props.rdsDatabase,
          RDS_USERNAME: `${props.resourcePrefix.replace(/-/g, '_')}_app`,
          ELASTICACHE_ENDPOINT: props.cacheEndpoint,
          ELASTICACHE_PORT: '6379',
          API_GATEWAY_DOMAIN: props.apiGatewayDomain,
          AWS_REGION: cdk.Stack.of(this).region,
          // Only fhir-api service handles DB initialization to avoid race conditions
          DB_AUTO_INIT: (svc.name === 'fhir-api' && props.dbAutoInit) ? 'true' : 'false',
          // RDS secret name for bootstrap config to create IAM user (only needed for fhir-api with auto-init)
          FHIR4JAVA_DB_RDS_SECRET_NAME: (svc.name === 'fhir-api' && props.dbAutoInit) ? props.rdsSecretName : '',
        },
        secrets: {
          // ElastiCache secret is stored as plain text (not JSON), so no field extraction needed
          ELASTICACHE_AUTH_TOKEN: ecs.Secret.fromSecretsManager(
            secretsmanager.Secret.fromSecretCompleteArn(this, `${svc.name}CacheSecret`, props.cacheSecretArn)
          ),
        },
        portMappings: [{ containerPort: 8080 }],
        readonlyRootFilesystem: true,
        linuxParameters,
        healthCheck: {
          command: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
          interval: cdk.Duration.seconds(30),
          timeout: cdk.Duration.seconds(10),
          retries: 3,
          startPeriod: cdk.Duration.seconds(120),
        },
      });

      const service = new ecs.FargateService(this, `${svc.name}Service`, {
        serviceName: `${prefix}-${svc.name}`,
        cluster: this.cluster,
        taskDefinition,
        // Set to 0 on initial deployment when ECR is empty
        desiredCount: props.skipTaskDeployment ? 0 : svc.desiredCount,
        securityGroups: [this.taskSecurityGroup],
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        enableExecuteCommand: true,
      });

      // Attach to target group
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
