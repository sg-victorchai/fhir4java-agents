import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface ElastiCacheConstructProps {
  vpc: ec2.IVpc;
  nodeType?: string;
  numNodeGroups?: number;
  replicasPerNodeGroup?: number;
}

export class ElastiCacheConstruct extends Construct {
  public readonly cluster: elasticache.CfnReplicationGroup;
  public readonly secret: secretsmanager.Secret;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: ElastiCacheConstructProps) {
    super(scope, id);

    this.securityGroup = new ec2.SecurityGroup(this, 'CacheSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for ElastiCache',
      allowAllOutbound: false,
    });

    this.secret = new secretsmanager.Secret(this, 'CacheSecret', {
      secretName: 'fhir4java/prod/elasticache',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({}),
        generateStringKey: 'authToken',
        excludeCharacters: '/@"\\\'',
        passwordLength: 64,
      },
    });

    const subnetGroup = new elasticache.CfnSubnetGroup(this, 'SubnetGroup', {
      description: 'FHIR4Java ElastiCache Subnet Group',
      subnetIds: props.vpc.selectSubnets({
        subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
      }).subnetIds,
    });

    this.cluster = new elasticache.CfnReplicationGroup(this, 'ReplicationGroup', {
      replicationGroupDescription: 'FHIR4Java ElastiCache Cluster',
      engine: 'valkey',
      cacheNodeType: props.nodeType ?? 'cache.r6g.large',
      numNodeGroups: props.numNodeGroups ?? 2,
      replicasPerNodeGroup: props.replicasPerNodeGroup ?? 1,
      cacheSubnetGroupName: subnetGroup.ref,
      securityGroupIds: [this.securityGroup.securityGroupId],
      transitEncryptionEnabled: true,
      atRestEncryptionEnabled: true,
      authToken: this.secret.secretValueFromJson('authToken').unsafeUnwrap(),
      automaticFailoverEnabled: true,
      multiAzEnabled: true,
    });

    this.cluster.addDependency(subnetGroup);
  }
}
