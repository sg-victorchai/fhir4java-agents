import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface ApiGatewayConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
  vpcEndpoint: ec2.IInterfaceVpcEndpoint;
  nlb: elbv2.INetworkLoadBalancer;
  domainName: string;
  certificateArn: string;
}

export class ApiGatewayConstruct extends Construct {
  public readonly restApi: apigateway.RestApi;
  public readonly vpcLink: apigateway.VpcLink;

  constructor(scope: Construct, id: string, props: ApiGatewayConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    // VPC Link (Legacy) - Points to NLB for REST API integration
    this.vpcLink = new apigateway.VpcLink(this, 'VpcLink', {
      vpcLinkName: `${prefix}-vpc-link`,
      targets: [props.nlb],
      description: `VPC Link for ${prefix} REST API to NLB`,
    });

    // REST API (PRIVATE) - Only accessible through VPC Endpoint
    this.restApi = new apigateway.RestApi(this, 'RestApi', {
      restApiName: `${prefix}-api`,
      description: `${prefix} Private REST API`,
      endpointConfiguration: {
        types: [apigateway.EndpointType.PRIVATE],
        vpcEndpoints: [props.vpcEndpoint],
      },
      // Resource policy to allow access only from VPC Endpoint
      policy: new cdk.aws_iam.PolicyDocument({
        statements: [
          new cdk.aws_iam.PolicyStatement({
            effect: cdk.aws_iam.Effect.ALLOW,
            principals: [new cdk.aws_iam.AnyPrincipal()],
            actions: ['execute-api:Invoke'],
            resources: ['execute-api:/*'],
          }),
          new cdk.aws_iam.PolicyStatement({
            effect: cdk.aws_iam.Effect.DENY,
            principals: [new cdk.aws_iam.AnyPrincipal()],
            actions: ['execute-api:Invoke'],
            resources: ['execute-api:/*'],
            conditions: {
              StringNotEquals: {
                'aws:SourceVpce': props.vpcEndpoint.vpcEndpointId,
              },
            },
          }),
        ],
      }),
      deployOptions: {
        stageName: 'prod',
        throttlingBurstLimit: 1000,
        throttlingRateLimit: 500,
        loggingLevel: apigateway.MethodLoggingLevel.INFO,
        dataTraceEnabled: false,
        metricsEnabled: true,
      },
      defaultCorsPreflightOptions: {
        allowOrigins: ['https://' + props.domainName],
        allowMethods: apigateway.Cors.ALL_METHODS,
        allowHeaders: ['Content-Type', 'Authorization', 'X-Tenant-ID', 'X-Request-Id'],
        exposeHeaders: ['ETag', 'Location', 'Content-Location', 'X-FHIR-Version'],
        maxAge: cdk.Duration.hours(1),
      },
    });

    // NLB Integration via VPC Link
    const nlbIntegration = new apigateway.Integration({
      type: apigateway.IntegrationType.HTTP_PROXY,
      integrationHttpMethod: 'ANY',
      uri: `http://${props.nlb.loadBalancerDnsName}/{proxy}`,
      options: {
        connectionType: apigateway.ConnectionType.VPC_LINK,
        vpcLink: this.vpcLink,
        requestParameters: {
          'integration.request.path.proxy': 'method.request.path.proxy',
        },
      },
    });

    // Integration for root paths (without proxy)
    const nlbRootIntegration = new apigateway.Integration({
      type: apigateway.IntegrationType.HTTP_PROXY,
      integrationHttpMethod: 'ANY',
      uri: `http://${props.nlb.loadBalancerDnsName}/`,
      options: {
        connectionType: apigateway.ConnectionType.VPC_LINK,
        vpcLink: this.vpcLink,
      },
    });

    // Create specific integration for each path
    const createProxyIntegration = (basePath: string) => {
      return new apigateway.Integration({
        type: apigateway.IntegrationType.HTTP_PROXY,
        integrationHttpMethod: 'ANY',
        uri: `http://${props.nlb.loadBalancerDnsName}${basePath}/{proxy}`,
        options: {
          connectionType: apigateway.ConnectionType.VPC_LINK,
          vpcLink: this.vpcLink,
          requestParameters: {
            'integration.request.path.proxy': 'method.request.path.proxy',
          },
        },
      });
    };

    const createRootIntegration = (basePath: string) => {
      return new apigateway.Integration({
        type: apigateway.IntegrationType.HTTP_PROXY,
        integrationHttpMethod: 'ANY',
        uri: `http://${props.nlb.loadBalancerDnsName}${basePath}`,
        options: {
          connectionType: apigateway.ConnectionType.VPC_LINK,
          vpcLink: this.vpcLink,
        },
      });
    };

    // /fhir routes
    const fhirResource = this.restApi.root.addResource('fhir');
    fhirResource.addMethod('ANY', createRootIntegration('/fhir'));
    const fhirProxy = fhirResource.addResource('{proxy+}');
    fhirProxy.addMethod('ANY', createProxyIntegration('/fhir'), {
      requestParameters: {
        'method.request.path.proxy': true,
      },
    });

    // /actuator routes
    const actuatorResource = this.restApi.root.addResource('actuator');
    actuatorResource.addMethod('ANY', createRootIntegration('/actuator'));
    const actuatorProxy = actuatorResource.addResource('{proxy+}');
    actuatorProxy.addMethod('ANY', createProxyIntegration('/actuator'), {
      requestParameters: {
        'method.request.path.proxy': true,
      },
    });

    // /api/admin routes
    const apiResource = this.restApi.root.addResource('api');
    const adminResource = apiResource.addResource('admin');
    adminResource.addMethod('ANY', createRootIntegration('/api/admin'));
    const adminProxy = adminResource.addResource('{proxy+}');
    adminProxy.addMethod('ANY', createProxyIntegration('/api/admin'), {
      requestParameters: {
        'method.request.path.proxy': true,
      },
    });

    // Output the API endpoint
    new cdk.CfnOutput(this, 'PrivateApiEndpoint', {
      value: this.restApi.url,
      description: 'Private REST API endpoint (accessible via VPC Endpoint)',
    });
  }
}
