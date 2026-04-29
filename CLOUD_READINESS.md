# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized for AWS cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File Storage Migration (S3)
- **Issue**: Hard-coded file paths and local file system dependencies
- **Fix**: Migrated to Amazon S3 for durable, scalable storage
- **Files Modified**: `ReportService.java`
- **Configuration**: `aws.s3.bucket.reports` in application.properties

### 2. Secrets Management (AWS Secrets Manager)
- **Issue**: Hard-coded database credentials in source code
- **Fix**: Integrated AWS Secrets Manager for secure credential storage
- **Files Modified**: `BookingService.java`, `AwsConfig.java`
- **Configuration**: `aws.secretsmanager.db.secret.name` in application.properties

### 3. Session Management (ElastiCache Redis)
- **Issue**: HTTP session storage preventing horizontal scaling
- **Fix**: Migrated to Amazon ElastiCache for Redis for distributed session management
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **Configuration**: `spring.redis.*` properties in application.properties

### 4. Configuration Externalization
- **Issue**: Hard-coded URLs, ports, and environment-specific values
- **Fix**: Externalized all configuration to environment variables and AWS Systems Manager Parameter Store
- **Files Modified**: All service classes, `application.properties`
- **Configuration**: Environment variables with sensible defaults

### 5. In-Memory Caching with TTL
- **Issue**: Unbounded in-memory caching causing memory issues
- **Fix**: Migrated to Redis with proper TTL policies
- **Files Modified**: `BookingController.java`
- **Configuration**: `app.cache.ttl.minutes` in application.properties

## AWS Services Required

1. **Amazon S3**: For report storage and file operations
2. **AWS Secrets Manager**: For database credentials and sensitive configuration
3. **Amazon ElastiCache (Redis)**: For session management and distributed caching
4. **AWS Systems Manager Parameter Store**: For application configuration
5. **Amazon RDS**: For production database (PostgreSQL/MySQL recommended)

## Environment Variables

See `.env.template` for required environment variables. Key variables:

- `SERVER_PORT`: Application port (default: 8080)
- `AWS_REGION`: AWS region (default: us-east-1)
- `S3_BUCKET_REPORTS`: S3 bucket for reports
- `DB_SECRET_NAME`: Secrets Manager secret name for DB credentials
- `REDIS_HOST`: ElastiCache Redis endpoint
- `REDIS_PORT`: Redis port (default: 6379)

## AWS Secrets Manager Secret Format

Create a secret in AWS Secrets Manager with the following JSON structure:

```json
{
  "host": "resort-db.cluster-xxxxx.us-east-1.rds.amazonaws.com",
  "username": "resort_app_user",
  "password": "your-secure-password"
}
```

## Deployment Checklist

- [ ] Create S3 bucket for reports
- [ ] Create ElastiCache Redis cluster
- [ ] Create RDS database instance
- [ ] Store database credentials in Secrets Manager
- [ ] Configure IAM roles with appropriate permissions:
  - S3 read/write access
  - Secrets Manager read access
  - ElastiCache access
  - Parameter Store read access
- [ ] Set environment variables in ECS/EKS/Elastic Beanstalk
- [ ] Configure security groups for service communication
- [ ] Enable CloudWatch logging

## IAM Permissions Required

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
        "arn:aws:s3:::resort-reports-bucket",
        "arn:aws:s3:::resort-reports-bucket/*"
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

## Local Development

For local development, ensure you have:
1. AWS CLI configured with appropriate credentials
2. Redis running locally or accessible endpoint
3. Environment variables set (copy from .env.template)

## Health Checks

The application exposes health check endpoints via Spring Boot Actuator:
- `/actuator/health` - Overall application health
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

## Security Improvements

1. Replaced MD5 hashing with SHA-256
2. Implemented parameterized SQL queries to prevent SQL injection
3. Removed hard-coded credentials
4. Externalized all sensitive configuration

## Scalability Improvements

1. Stateless application design (no HTTP session dependency)
2. Distributed caching with Redis
3. Externalized file storage to S3
4. Dynamic port configuration for container orchestration
