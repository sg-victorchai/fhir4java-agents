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
    // Note: allowAllOutbound is false; explicit egress rules are added in the infrastructure stack
    this.securityGroup = new ec2.SecurityGroup(this, 'NlbSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-nlb-sg`,
      description: `Security group for ${prefix} NLB`,
      allowAllOutbound: false,
    });

    // Allow inbound HTTP traffic on port 80 from all sources (IPv4 and IPv6)
    this.securityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'Allow HTTP inbound from IPv4'
    );
    this.securityGroup.addIngressRule(
      ec2.Peer.anyIpv6(),
      ec2.Port.tcp(80),
      'Allow HTTP inbound from IPv6'
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
    // Note: CloudFormation requires HTTP/HTTPS health check for ALB target type (TCP not supported)
    // Using /fhir/r5/metadata as a lightweight health check endpoint
    const cfnTargetGroup = new elbv2.CfnTargetGroup(this, 'AlbTargetGroup', {
      name: `${prefix}-nlb-alb-tg`,
      vpcId: props.vpc.vpcId,
      targetType: 'alb',
      port: 80,
      protocol: 'TCP',
      healthCheckEnabled: true,
      healthCheckProtocol: 'HTTP',
      healthCheckPort: '80',
      healthCheckPath: '/fhir/r5/metadata',
      healthCheckIntervalSeconds: 30,
      healthCheckTimeoutSeconds: 6,
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 2,
      matcher: {
        httpCode: '200',
      },
      targets: [{
        id: props.internalAlb.loadBalancerArn,
        port: 80,
      }],
    });

    // Create L2 wrapper for the target group to use with listener
    const targetGroup = elbv2.NetworkTargetGroup.fromTargetGroupAttributes(this, 'AlbTargetGroupL2', {
      targetGroupArn: cfnTargetGroup.ref,
    });

    this.listener = this.nlb.addListener('TcpListener', {
      port: 80,
      protocol: elbv2.Protocol.TCP,
      defaultTargetGroups: [targetGroup],
    });
  }
}
