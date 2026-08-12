# AWS Secrets Manager Configuration for ResortsLite

## Overview
This application now uses AWS Secrets Manager to securely manage database credentials instead of hard-coding them in source code. This follows cloud-native security best practices and enables automatic credential rotation.

## AWS Secrets Manager Setup

### 1. Create Secret in AWS Secrets Manager

Use the AWS CLI to create a secret with your database credentials:

```bash
aws secretsmanager create-secret \
    --name resorts-lite/db-credentials \
    --description "Database credentials for ResortsLite application" \
    --secret-string '{
        "host": "db-prod.resorts-internal.com",
        "port": "3306",
        "database": "resortdb",
        "username": "admin",
        "password": "YourSecurePassword"
    }' \
    --region us-east-1
```

### 2. IAM Permissions

Ensure your application's IAM role has the following permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue",
                "secretsmanager:DescribeSecret"
            ],
            "Resource": "arn:aws:secretsmanager:us-east-1:*:secret:resorts-lite/db-credentials-*"
        }
    ]
}
```

### 3. Environment Variables

Configure the following environment variables for your application:

- `AWS_SECRETS_MANAGER_SECRET_NAME`: Name of the secret in AWS Secrets Manager (default: `resorts-lite/db-credentials`)
- `AWS_REGION`: AWS region where the secret is stored (default: `us-east-1`)

### 4. Secret JSON Format

The secret must be stored as a JSON string with the following structure:

```json
{
    "host": "database-hostname",
    "port": "3306",
    "database": "database-name",
    "username": "db-username",
    "password": "db-password"
}
```

## Local Development

For local development, the application will fall back to default H2 in-memory database credentials if AWS Secrets Manager is not accessible. This allows developers to run the application locally without AWS credentials.

## Credential Rotation

To rotate credentials:

1. Update the secret value in AWS Secrets Manager:
```bash
aws secretsmanager update-secret \
    --secret-id resorts-lite/db-credentials \
    --secret-string '{
        "host": "db-prod.resorts-internal.com",
        "port": "3306",
        "database": "resortdb",
        "username": "admin",
        "password": "NewSecurePassword"
    }' \
    --region us-east-1
```

2. Restart the application to pick up the new credentials

## Security Benefits

- **No credentials in source code**: Credentials are never committed to version control
- **No credentials in container images**: Credentials are retrieved at runtime
- **Centralized management**: All credentials managed in one secure location
- **Audit trail**: AWS CloudTrail logs all secret access
- **Automatic rotation**: Can be configured for automatic credential rotation
- **Encryption at rest**: Secrets are encrypted using AWS KMS

## Troubleshooting

If the application cannot retrieve secrets:

1. Check IAM role permissions
2. Verify the secret name matches the configuration
3. Ensure the AWS region is correct
4. Check CloudWatch logs for detailed error messages
5. Verify network connectivity to AWS Secrets Manager endpoint

The application will log warnings and fall back to default credentials for local development if AWS Secrets Manager is not accessible.
