import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as wafv2 from 'aws-cdk-lib/aws-wafv2';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

export interface PublicAlbConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
  certificateArn: string;
  domainName: string;
}

export class PublicAlbConstruct extends Construct {
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly targetGroup: elbv2.ApplicationTargetGroup;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: PublicAlbConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    this.securityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-public-alb-sg`,
      description: `Security group for ${prefix} Public ALB`,
      allowAllOutbound: true,
    });

    this.securityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(443),
      'Allow HTTPS from internet'
    );

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'PublicAlb', {
      loadBalancerName: `${prefix}-public-alb`,
      vpc: props.vpc,
      internetFacing: true,
      securityGroup: this.securityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // Target group for VPC Endpoint ENI IPs (managed by Lambda)
    this.targetGroup = new elbv2.ApplicationTargetGroup(this, 'VpcEndpointTargetGroup', {
      targetGroupName: `${prefix}-vpce-tg`,
      vpc: props.vpc,
      targetType: elbv2.TargetType.IP,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      port: 443,
      healthCheck: {
        protocol: elbv2.Protocol.HTTPS,
        path: '/fhir/r5/metadata',
        healthyHttpCodes: '200,403',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
      },
    });

    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this, 'Certificate', props.certificateArn
    );

    this.listener = this.alb.addListener('HttpsListener', {
      port: 443,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      certificates: [certificate],
      defaultTargetGroups: [this.targetGroup],
    });

    // WAF WebACL
    const webAcl = new wafv2.CfnWebACL(this, 'WebAcl', {
      name: `${prefix}-waf`,
      defaultAction: { allow: {} },
      scope: 'REGIONAL',
      visibilityConfig: {
        cloudWatchMetricsEnabled: true,
        metricName: `${prefix}-waf`,
        sampledRequestsEnabled: true,
      },
      rules: [
        {
          name: 'AWSManagedRulesCommonRuleSet',
          priority: 1,
          statement: {
            managedRuleGroupStatement: {
              vendorName: 'AWS',
              name: 'AWSManagedRulesCommonRuleSet',
            },
          },
          overrideAction: { none: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'AWSManagedRulesCommonRuleSet',
            sampledRequestsEnabled: true,
          },
        },
        {
          name: 'AWSManagedRulesKnownBadInputsRuleSet',
          priority: 2,
          statement: {
            managedRuleGroupStatement: {
              vendorName: 'AWS',
              name: 'AWSManagedRulesKnownBadInputsRuleSet',
            },
          },
          overrideAction: { none: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'AWSManagedRulesKnownBadInputsRuleSet',
            sampledRequestsEnabled: true,
          },
        },
        {
          name: 'RateLimitRule',
          priority: 3,
          statement: {
            rateBasedStatement: {
              limit: 2000,
              aggregateKeyType: 'IP',
            },
          },
          action: { block: {} },
          visibilityConfig: {
            cloudWatchMetricsEnabled: true,
            metricName: 'RateLimitRule',
            sampledRequestsEnabled: true,
          },
        },
      ],
    });

    new wafv2.CfnWebACLAssociation(this, 'WebAclAssociation', {
      resourceArn: this.alb.loadBalancerArn,
      webAclArn: webAcl.attrArn,
    });
  }
}
