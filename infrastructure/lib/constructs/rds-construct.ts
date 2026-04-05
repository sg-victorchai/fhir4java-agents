import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface RdsConstructProps {
  resourcePrefix: string;
  vpc: ec2.IVpc;
  instanceClass?: ec2.InstanceClass;
  instanceSize?: ec2.InstanceSize;
  databaseName?: string;
  multiAz?: boolean;
  iamAuthentication?: boolean;
}

export class RdsConstruct extends Construct {
  public readonly instance: rds.DatabaseInstance;
  public readonly secret: secretsmanager.ISecret;
  public readonly securityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props: RdsConstructProps) {
    super(scope, id);

    const prefix = props.resourcePrefix;

    this.securityGroup = new ec2.SecurityGroup(this, 'RdsSecurityGroup', {
      vpc: props.vpc,
      securityGroupName: `${prefix}-rds-sg`,
      description: `Security group for ${prefix} RDS PostgreSQL`,
      allowAllOutbound: false,
    });

    // Master user for RDS administration (separate from IAM app user)
    // Master user: fhir4java_dev_admin (password auth)
    // IAM app user: fhir4java_dev_app (IAM auth, created by bootstrap)
    this.secret = new secretsmanager.Secret(this, 'RdsSecret', {
      secretName: `${prefix}/rds`,
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: `${prefix.replace(/-/g, '_')}_admin` }),
        generateStringKey: 'password',
        excludeCharacters: '/@"\\\'',
        passwordLength: 32,
      },
    });

    this.instance = new rds.DatabaseInstance(this, 'PostgresInstance', {
      instanceIdentifier: `${prefix}-postgres`,
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_18_2,
      }),
      instanceType: ec2.InstanceType.of(
        props.instanceClass ?? ec2.InstanceClass.R7G,
        props.instanceSize ?? ec2.InstanceSize.LARGE,
      ),
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [this.securityGroup],
      credentials: rds.Credentials.fromSecret(this.secret),
      databaseName: props.databaseName ?? `${prefix.replace(/-/g, '_')}_db`,
      multiAz: props.multiAz ?? true,
      storageEncrypted: true,
      deletionProtection: true,
      iamAuthentication: props.iamAuthentication ?? true,
      backupRetention: cdk.Duration.days(7),
      allocatedStorage: 100,
      storageType: rds.StorageType.IO2,
      parameterGroup: new rds.ParameterGroup(this, 'ParameterGroup', {
        engine: rds.DatabaseInstanceEngine.postgres({
          version: rds.PostgresEngineVersion.VER_18_2,
        }),
        parameters: {
          'max_connections': '200',
        },
      }),
    });
  }
}
