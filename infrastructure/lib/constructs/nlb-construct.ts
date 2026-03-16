import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface NlbConstructProps {
  vpc: ec2.IVpc;
  internalAlb: elbv2.IApplicationLoadBalancer;
}

export class NlbConstruct extends Construct {
  public readonly nlb: elbv2.NetworkLoadBalancer;
  public readonly listener: elbv2.NetworkListener;

  constructor(scope: Construct, id: string, props: NlbConstructProps) {
    super(scope, id);

    this.nlb = new elbv2.NetworkLoadBalancer(this, 'Nlb', {
      vpc: props.vpc,
      internetFacing: false,
      crossZoneEnabled: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // Target group pointing to Internal ALB
    const targetGroup = new elbv2.NetworkTargetGroup(this, 'AlbTargetGroup', {
      vpc: props.vpc,
      targetType: elbv2.TargetType.ALB,
      port: 80,
      protocol: elbv2.Protocol.TCP,
      healthCheck: {
        protocol: elbv2.Protocol.HTTP,
        path: '/actuator/health/liveness',
        port: '80',
      },
    });

    targetGroup.addTarget(
      new (class implements elbv2.INetworkLoadBalancerTarget {
        attachToNetworkTargetGroup(targetGroup: elbv2.INetworkTargetGroup): elbv2.LoadBalancerTargetProps {
          return {
            targetType: elbv2.TargetType.ALB,
            targetJson: { id: props.internalAlb.loadBalancerArn, port: 80 },
          };
        }
      })()
    );

    this.listener = this.nlb.addListener('TcpListener', {
      port: 80,
      protocol: elbv2.Protocol.TCP,
      defaultTargetGroups: [targetGroup],
    });
  }
}
