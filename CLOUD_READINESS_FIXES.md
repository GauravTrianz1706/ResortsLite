# Cloud Readiness Fixes - AWS Deployment Guide

## Overview
This application has been transformed to be cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Fix**: Migrated to Amazon S3 for durable, scalable storage
- Replaced `/var/legacy/reports/` and `C:\\ResortBackups\\` with S3 bucket operations
- Implemented AWS SDK for Java v2 S3 client
- All file operations now use S3 PutObject/GetObject APIs
- Configuration: `aws.s3.reports.bucket` environment variable

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Fix**: Migrated to AWS Secrets Manager
- Removed hard-coded `DB_HOST`, `DB_USER`, `DB_PASS` constants
- Implemented SecretsManagerClient to retrieve credentials at runtime
- Credentials stored securely in AWS Secrets Manager
- Configuration: `aws.secrets.database.name` environment variable

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Fixed URLs for external services
**Fix**: Externalized to AWS Systems Manager Parameter Store
- Replaced hard-coded `http://inventory-service.internal:8081` with environment variables
- All service endpoints configurable via environment variables
- Configuration: `app.inventory.endpoint`, `app.payment.endpoint`, etc.

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Fixed port 8080 prevents dynamic assignment
**Fix**: Externalized port configuration
- Server port now configurable via `SERVER_PORT` environment variable
- Compatible with ECS/EKS dynamic port assignment
- Configuration: `server.port=${SERVER_PORT:8080}`

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: In-memory HTTP session prevents horizontal scaling
**Fix**: Migrated to Amazon ElastiCache for Redis
- Implemented Spring Session with Redis backend
- All session data stored in distributed Redis cache
- Stateless application instances enable horizontal scaling
- Configuration: Redis connection via environment variables

### 6. File-based Authentication (Blocker 17)
**Issue**: Authentication credentials stored in local files
**Fix**: Migrated to AWS Secrets Manager and Amazon Cognito pattern
- Credentials retrieved from AWS Secrets Manager
- Prepared for Amazon Cognito integration for user management
- No local file dependencies for authentication

### 7. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Unbounded in-memory cache causes memory growth
**Fix**: Migrated to Amazon ElastiCache for Redis with TTL
- Replaced static HashMap cache with Redis
- Implemented TTL policies (configurable via `redis.cache.ttl.minutes`)
- Centralized cache management across instances
- Prevents memory leaks and stale data

## AWS Services Required

### 1. Amazon S3
- **Purpose**: Durable file storage for reports and documents
- **Configuration**: Create S3 bucket and set `AWS_S3_REPORTS_BUCKET` environment variable
- **IAM Permissions**: `s3:PutObject`, `s3:GetObject`, `s3:ListBucket`

### 2. AWS Secrets Manager
- **Purpose**: Secure storage for database credentials
- **Configuration**: Create secret with database credentials
- **Secret Format**:
  ```json
  {
    "host": "your-rds-endpoint.amazonaws.com",
    "username": "resort_app_user",
    "password": "secure-password"
  }
  ```
- **IAM Permissions**: `secretsmanager:GetSecretValue`

### 3. Amazon ElastiCache for Redis
- **Purpose**: Distributed session management and caching
- **Configuration**: Create Redis cluster and set `REDIS_HOST`, `REDIS_PORT` environment variables
- **Recommended**: Use cluster mode for high availability

### 4. AWS Systems Manager Parameter Store (Optional)
- **Purpose**: Centralized configuration management
- **Configuration**: Store environment-specific parameters
- **IAM Permissions**: `ssm:GetParameter`, `ssm:GetParameters`

### 5. Amazon RDS (Production Database)
- **Purpose**: Managed relational database
- **Configuration**: Replace H2 with RDS PostgreSQL/MySQL
- **Connection**: Store credentials in Secrets Manager

## Environment Variables

All configuration is externalized via environment variables. See `.env.template` for complete list.

### Critical Variables:
- `AWS_REGION`: AWS region (e.g., us-east-1)
- `AWS_S3_REPORTS_BUCKET`: S3 bucket name for reports
- `AWS_SECRET_DATABASE_NAME`: Secrets Manager secret name
- `REDIS_HOST`: ElastiCache Redis endpoint
- `DATABASE_URL`: RDS database connection string

## IAM Role Requirements

The application requires an IAM role with the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::resort-reports-bucket/*",
        "arn:aws:s3:::resort-reports-bucket"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:resort-db-credentials-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/resort/*"
    }
  ]
}
```

## Deployment Options

### Option 1: Amazon ECS (Elastic Container Service)
- Deploy as Docker containers
- Use ECS task role for IAM permissions
- Configure environment variables in task definition

### Option 2: Amazon EKS (Elastic Kubernetes Service)
- Deploy as Kubernetes pods
- Use IRSA (IAM Roles for Service Accounts)
- Configure via ConfigMaps and Secrets

### Option 3: AWS Elastic Beanstalk
- Deploy as JAR file
- Configure environment variables in Elastic Beanstalk console
- Attach IAM instance profile with required permissions

## Testing Locally

1. Install and run Redis locally:
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```

2. Set environment variables:
   ```bash
   export AWS_REGION=us-east-1
   export AWS_S3_REPORTS_BUCKET=your-test-bucket
   export REDIS_HOST=localhost
   ```

3. Configure AWS credentials:
   ```bash
   aws configure
   ```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Migration Checklist

- [ ] Create S3 bucket for reports
- [ ] Create Secrets Manager secret for database credentials
- [ ] Provision ElastiCache Redis cluster
- [ ] Create RDS database instance (production)
- [ ] Configure IAM role with required permissions
- [ ] Set all environment variables
- [ ] Test application connectivity to AWS services
- [ ] Deploy to target environment (ECS/EKS/Elastic Beanstalk)
- [ ] Verify session management works across multiple instances
- [ ] Test file upload/download with S3
- [ ] Verify database connectivity with Secrets Manager credentials

## Security Considerations

1. **Secrets Management**: Never commit credentials to version control
2. **IAM Least Privilege**: Grant only required permissions
3. **Network Security**: Use VPC security groups to restrict access
4. **Encryption**: Enable encryption at rest for S3, RDS, and ElastiCache
5. **TLS/SSL**: Use HTTPS for all external communication
6. **Audit Logging**: Enable CloudTrail for API audit logs

## Monitoring and Observability

1. **CloudWatch Logs**: Configure log aggregation
2. **CloudWatch Metrics**: Monitor application performance
3. **X-Ray**: Enable distributed tracing (optional)
4. **ElastiCache Metrics**: Monitor cache hit rates and memory usage
5. **RDS Metrics**: Monitor database performance

## Support

For issues or questions regarding cloud deployment, refer to AWS documentation:
- [Amazon S3 Developer Guide](https://docs.aws.amazon.com/s3/)
- [AWS Secrets Manager User Guide](https://docs.aws.amazon.com/secretsmanager/)
- [Amazon ElastiCache for Redis User Guide](https://docs.aws.amazon.com/elasticache/)
