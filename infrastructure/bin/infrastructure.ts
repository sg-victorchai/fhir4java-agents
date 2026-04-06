#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { Fhir4JavaInfrastructureStack } from '../lib/fhir4java-infrastructure-stack';

const app = new cdk.App();

// Application naming parameters
const appName = app.node.tryGetContext('appName') || 'fhir4java';
const environment = app.node.tryGetContext('environment') || 'prod';

// Infrastructure parameters
// hostedZoneName: The base domain name of the Route 53 hosted zone (e.g., 'example.com')
const hostedZoneName = app.node.tryGetContext('hostedZoneName');
// hostedZoneId: The Route 53 hosted zone ID
const hostedZoneId = app.node.tryGetContext('hostedZoneId');
// subdomainPrefix: Optional subdomain prefix (defaults to '{appName}-{environment}', e.g., 'fhir4java-dev')
const subdomainPrefix = app.node.tryGetContext('subdomainPrefix') || `${appName}-${environment}`;
// Full domain name is constructed as: {subdomainPrefix}.{hostedZoneName}
const domainName = `${subdomainPrefix}.${hostedZoneName}`;

const certificateArn = app.node.tryGetContext('certificateArn');

// Database initialization (default: false, set to true only for first deployment)
const dbAutoInit = app.node.tryGetContext('dbAutoInit') === 'true';

// Skip ECS task deployment (set to true for initial deployment when ECR is empty)
const skipTaskDeployment = app.node.tryGetContext('skipTaskDeployment') === 'true';

const stackName = `${appName}-${environment}-stack`;

new Fhir4JavaInfrastructureStack(app, stackName, {
  stackName,
  appName,
  environment,
  domainName,
  hostedZoneName,
  certificateArn,
  hostedZoneId,
  dbAutoInit,
  skipTaskDeployment,
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
  },
});
