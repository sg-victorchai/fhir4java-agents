import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export interface RdsConstructProps {
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

    this.securityGroup = new ec2.SecurityGroup(this, 'RdsSecurityGroup', {
      vpc: props.vpc,
      description: 'Security group for RDS PostgreSQL',
      allowAllOutbound: false,
    });

    this.secret = new secretsmanager.Secret(this, 'RdsSecret', {
      secretName: 'fhir4java/prod/rds',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'fhir4java_app' }),
        generateStringKey: 'password',
        excludeCharacters: '/@"\\\'',
        passwordLength: 32,
      },
    });

    this.instance = new rds.DatabaseInstance(this, 'PostgresInstance', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_4,
      }),
      instanceType: ec2.InstanceType.of(
        props.instanceClass ?? ec2.InstanceClass.R6G,
        props.instanceSize ?? ec2.InstanceSize.LARGE
      ),
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [this.securityGroup],
      credentials: rds.Credentials.fromSecret(this.secret),
      databaseName: props.databaseName ?? 'fhir4java_prod',
      multiAz: props.multiAz ?? true,
      storageEncrypted: true,
      deletionProtection: true,
      iamAuthentication: props.iamAuthentication ?? true,
      backupRetention: cdk.Duration.days(7),
      allocatedStorage: 100,
      storageType: rds.StorageType.GP3,
      parameterGroup: new rds.ParameterGroup(this, 'ParameterGroup', {
        engine: rds.DatabaseInstanceEngine.postgres({
          version: rds.PostgresEngineVersion.VER_16_4,
        }),
        parameters: {
          'max_connections': '200',
        },
      }),
    });
  }
}
