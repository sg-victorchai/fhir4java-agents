#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { Fhir4JavaInfrastructureStack } from '../lib/fhir4java-infrastructure-stack';

const app = new cdk.App();

// Application naming parameters
const appName = app.node.tryGetContext('appName') || 'fhir4java';
const environment = app.node.tryGetContext('environment') || 'prod';

// Infrastructure parameters
const domainName = app.node.tryGetContext('domainName') || 'fhir.example.com';
const certificateArn = app.node.tryGetContext('certificateArn');
const vpnCidr = app.node.tryGetContext('vpnCidr') || '10.100.0.0/16';
const hostedZoneId = app.node.tryGetContext('hostedZoneId');

const stackName = `${appName}-${environment}-stack`;

new Fhir4JavaInfrastructureStack(app, stackName, {
  stackName,
  appName,
  environment,
  domainName,
  certificateArn,
  vpnCidr,
  hostedZoneId,
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
  },
});
