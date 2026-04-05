import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as elbv2_targets from 'aws-cdk-lib/aws-elasticloadbalancingv2-targets';
import { Construct } from 'constructs';

export interface NlbConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
  internalAlb: elbv2.IApplicationLoadBalancer;
}

export class NlbConstruct extends Construct {
  public readonly nlb: elbv2.NetworkLoadBalancer;
  public readonly listener: elbv2.NetworkListener;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: NlbConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    // Security group for NLB
    this.securityGroup = new ec2.SecurityGroup(this, 'NlbSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-nlb-sg`,
      description: `Security group for ${prefix} NLB`,
      allowAllOutbound: true,
    });

    // Allow inbound HTTP traffic on port 80
    this.securityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'Allow HTTP inbound'
    );

    this.nlb = new elbv2.NetworkLoadBalancer(this, 'Nlb', {
      loadBalancerName: `${prefix}-nlb`,
      vpc: props.vpc,
      internetFacing: false,
      crossZoneEnabled: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [this.securityGroup],
    });

    // Target group pointing to Internal ALB
    // Using TCP health check for basic connectivity verification
    const targetGroup = new elbv2.NetworkTargetGroup(this, 'AlbTargetGroup', {
      targetGroupName: `${prefix}-nlb-alb-tg`,
      vpc: props.vpc,
      targetType: elbv2.TargetType.ALB,
      port: 80,
      protocol: elbv2.Protocol.TCP,
      healthCheck: {
        enabled: true,
        protocol: elbv2.Protocol.TCP,
        port: 'traffic-port',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(10),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 2,
      },
    });

    // Use CDK's built-in AlbTarget for proper ALB target registration
    targetGroup.addTarget(new elbv2_targets.AlbTarget(props.internalAlb, 80));

    this.listener = this.nlb.addListener('TcpListener', {
      port: 80,
      protocol: elbv2.Protocol.TCP,
      defaultTargetGroups: [targetGroup],
    });
  }
}
