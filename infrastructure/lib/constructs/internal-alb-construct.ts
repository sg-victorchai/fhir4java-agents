import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface InternalAlbConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
}

export class InternalAlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly targetGroup: elbv2.ApplicationTargetGroup;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: InternalAlbConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    this.securityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-internal-alb-sg`,
      description: `Security group for ${prefix} Internal ALB`,
      allowAllOutbound: true,
    });

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'InternalAlb', {
      loadBalancerName: `${prefix}-internal-alb`,
      vpc: props.vpc,
      internetFacing: false,
      securityGroup: this.securityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // Single target group for all ECS traffic (fhir, metadata, actuator, admin)
    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'EcsTargetGroup', {
      targetGroupName: `${prefix}-ecs-tg`,
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      healthCheck: {
        path: '/fhir/r5/metadata',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
      },
    });

    // Single listener forwarding all traffic to the target group
    this.listener = this.alb.addListener('HttpListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultTargetGroups: [this.targetGroup],
    });
  }
}
