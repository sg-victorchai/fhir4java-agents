import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as cr from 'aws-cdk-lib/custom-resources';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';
import * as path from 'path';

export interface VpcEndpointAutomationConstructProps {
  resourcePrefix: string;
  vpcEndpoint: ec2.IInterfaceVpcEndpoint;
  targetGroup: elbv2.IApplicationTargetGroup;
}

export class VpcEndpointAutomationConstruct extends Construct {
  public readonly lambda: lambda.Function;

  constructor(scope: Construct, id: string, props: VpcEndpointAutomationConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    // Explicit log group so it's deleted with the stack
    const logGroup = new logs.LogGroup(this, 'LambdaLogGroup', {
      logGroupName: `/aws/lambda/${prefix}-vpce-target-updater`,
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.lambda = new lambda.Function(this, 'UpdateTargetsLambda', {
      functionName: `${prefix}-vpce-target-updater`,
      runtime: lambda.Runtime.PYTHON_3_12,
      handler: 'index.handler',
      code: lambda.Code.fromAsset(path.join(__dirname, '../../lambda/update-targets')),
      environment: {
        VPC_ENDPOINT_ID: props.vpcEndpoint.vpcEndpointId,
        TARGET_GROUP_ARN: props.targetGroup.targetGroupArn,
        TARGET_PORT: '443',
      },
      timeout: cdk.Duration.seconds(30),
      logGroup,
    });

    // Grant permissions
    this.lambda.addToRolePolicy(new iam.PolicyStatement({
      actions: [
        'ec2:DescribeVpcEndpoints',
        'ec2:DescribeNetworkInterfaces',
      ],
      resources: ['*'],
    }));

    this.lambda.addToRolePolicy(new iam.PolicyStatement({
      actions: [
        'elasticloadbalancing:DescribeTargetHealth',
        'elasticloadbalancing:RegisterTargets',
        'elasticloadbalancing:DeregisterTargets',
      ],
      resources: [props.targetGroup.targetGroupArn],
    }));

    // EventBridge rule for VPC Endpoint changes
    const rule = new events.Rule(this, 'VpcEndpointChangeRule', {
      eventPattern: {
        source: ['aws.ec2'],
        detailType: ['AWS API Call via CloudTrail'],
        detail: {
          eventSource: ['ec2.amazonaws.com'],
          eventName: ['CreateVpcEndpoint', 'ModifyVpcEndpoint'],
        },
      },
    });

    rule.addTarget(new targets.LambdaFunction(this.lambda));

    // Initialize targets on stack deployment
    new cr.AwsCustomResource(this, 'InitTargets', {
      onCreate: {
        service: 'Lambda',
        action: 'invoke',
        parameters: {
          FunctionName: this.lambda.functionName,
          Payload: JSON.stringify({ init: true }),
        },
        physicalResourceId: cr.PhysicalResourceId.of('InitTargets'),
      },
      policy: cr.AwsCustomResourcePolicy.fromStatements([
        new iam.PolicyStatement({
          actions: ['lambda:InvokeFunction'],
          resources: [this.lambda.functionArn],
        }),
      ]),
    });
  }
}
