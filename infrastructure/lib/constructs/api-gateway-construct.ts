import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as apigatewayv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as apigatewayv2_integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export interface ApiGatewayConstructProps {
  vpc: ec2.IVpc;
  nlb: elbv2.INetworkLoadBalancer;
  domainName: string;
  certificateArn: string;
}

export class ApiGatewayConstruct extends Construct {
  public readonly httpApi: apigatewayv2.HttpApi;
  public readonly vpcLink: apigatewayv2.VpcLink;
  public readonly customDomain: apigatewayv2.DomainName;

  constructor(scope: Construct, id: string, props: ApiGatewayConstructProps) {
    super(scope, id);

    // VPC Link for private integration
    this.vpcLink = new apigatewayv2.VpcLink(this, 'VpcLink', {
      vpc: props.vpc,
      subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
    });

    // HTTP API (Private)
    this.httpApi = new apigatewayv2.HttpApi(this, 'HttpApi', {
      apiName: 'fhir4java-api',
      description: 'FHIR4Java Private API Gateway',
      corsPreflight: {
        allowOrigins: ['https://' + props.domainName],
        allowMethods: [
          apigatewayv2.CorsHttpMethod.GET,
          apigatewayv2.CorsHttpMethod.POST,
          apigatewayv2.CorsHttpMethod.PUT,
          apigatewayv2.CorsHttpMethod.PATCH,
          apigatewayv2.CorsHttpMethod.DELETE,
          apigatewayv2.CorsHttpMethod.OPTIONS,
        ],
        allowHeaders: ['Content-Type', 'Authorization', 'X-Tenant-ID', 'X-Request-Id'],
        exposeHeaders: ['ETag', 'Location', 'Content-Location', 'X-FHIR-Version'],
        maxAge: cdk.Duration.hours(1),
      },
    });

    // NLB integration
    const nlbIntegration = new apigatewayv2_integrations.HttpNlbIntegration(
      'NlbIntegration',
      props.nlb.listeners[0],
      { vpcLink: this.vpcLink }
    );

    // Routes
    this.httpApi.addRoutes({
      path: '/fhir/{proxy+}',
      methods: [apigatewayv2.HttpMethod.ANY],
      integration: nlbIntegration,
    });

    this.httpApi.addRoutes({
      path: '/fhir',
      methods: [apigatewayv2.HttpMethod.ANY],
      integration: nlbIntegration,
    });

    // Custom Domain
    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this, 'Certificate', props.certificateArn
    );

    this.customDomain = new apigatewayv2.DomainName(this, 'CustomDomain', {
      domainName: props.domainName,
      certificate: certificate,
    });

    new apigatewayv2.ApiMapping(this, 'ApiMapping', {
      api: this.httpApi,
      domainName: this.customDomain,
    });

    // Throttling
    const stage = this.httpApi.defaultStage?.node.defaultChild as apigatewayv2.CfnStage;
    if (stage) {
      stage.defaultRouteSettings = {
        throttlingBurstLimit: 1000,
        throttlingRateLimit: 500,
      };
    }
  }
}
