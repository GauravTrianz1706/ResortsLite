# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized for AWS cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
- **Issue**: Hard-coded file paths and local file system writes
- **Fix**: Migrated to Amazon S3 for durable, scalable storage
- **Files Modified**: `ReportService.java`
- **AWS Services**: Amazon S3

### 2. Hard-coded Credentials → AWS Secrets Manager
- **Issue**: Database credentials embedded in source code
- **Fix**: Integrated AWS Secrets Manager for secure credential storage and rotation
- **Files Modified**: `BookingService.java`
- **AWS Services**: AWS Secrets Manager

### 3. HTTP Session State → Amazon ElastiCache for Redis
- **Issue**: In-memory session storage preventing horizontal scaling
- **Fix**: Migrated to Redis-backed distributed sessions using Spring Session
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **AWS Services**: Amazon ElastiCache for Redis

### 4. Unbounded In-Memory Cache → Redis with TTL
- **Issue**: In-memory caching without expiration causing memory leaks
- **Fix**: Replaced with Redis cache with configurable TTL
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **AWS Services**: Amazon ElastiCache for Redis

### 5. Hard-coded URLs → AWS Systems Manager Parameter Store
- **Issue**: Environment-specific URLs embedded in code
- **Fix**: Externalized to environment variables and Parameter Store
- **Files Modified**: `BookingController.java`, `application.properties`
- **AWS Services**: AWS Systems Manager Parameter Store

### 6. Hard-coded Ports → Environment Variables
- **Issue**: Fixed port numbers preventing dynamic assignment
- **Fix**: Externalized to environment variables for ECS/EKS compatibility
- **Files Modified**: `ReportService.java`, `application.properties`

### 7. File-based Authentication → AWS Secrets Manager + Cognito
- **Issue**: Authentication credentials stored in local files
- **Fix**: Migrated to AWS Secrets Manager with SHA-256 hashing
- **Files Modified**: `BookingService.java`
- **AWS Services**: AWS Secrets Manager, Amazon Cognito (recommended)

## AWS Services Required

### Core Services
1. **Amazon S3**: File storage for reports and backups
2. **AWS Secrets Manager**: Secure credential storage
3. **Amazon ElastiCache for Redis**: Distributed session and cache management
4. **AWS Systems Manager Parameter Store**: Configuration management

### Deployment Options
- **Amazon ECS**: Container orchestration
- **Amazon EKS**: Kubernetes-based deployment
- **AWS Elastic Beanstalk**: Platform-as-a-Service deployment

## Configuration

### Environment Variables
All configuration is externalized via environment variables. See `.env.template` for required variables.

### AWS Secrets Manager Setup
Create a secret named `resort-db-credentials` with the following JSON structure:
```json
{
  "host": "your-rds-instance.region.rds.amazonaws.com",
  "username": "resort_app",
  "password": "your-secure-password"
}
```

### Amazon ElastiCache Setup
1. Create a Redis cluster in ElastiCache
2. Configure security groups for application access
3. Set `REDIS_HOST` and `REDIS_PORT` environment variables

### Amazon S3 Setup
1. Create S3 buckets for reports and backups
2. Configure IAM roles with appropriate S3 permissions
3. Set `S3_REPORT_BUCKET` and `S3_BACKUP_BUCKET` environment variables

## IAM Permissions Required

The application requires the following IAM permissions:

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
        "arn:aws:s3:::resort-backups-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:resort-db-credentials-*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:*:*:parameter/resort/*"
      ]
    }
  ]
}
```

## Local Development

For local development without AWS services:
1. Use H2 in-memory database (default configuration)
2. Run local Redis instance: `docker run -p 6379:6379 redis:alpine`
3. Set minimal environment variables from `.env.template`

## Deployment Checklist

- [ ] Create S3 buckets for reports and backups
- [ ] Set up Amazon ElastiCache for Redis cluster
- [ ] Create AWS Secrets Manager secret for database credentials
- [ ] Configure IAM roles with required permissions
- [ ] Set all required environment variables
- [ ] Deploy application to ECS/EKS/Elastic Beanstalk
- [ ] Verify connectivity to all AWS services
- [ ] Test session persistence across multiple instances
- [ ] Validate file uploads to S3
- [ ] Confirm credential retrieval from Secrets Manager

## Architecture Benefits

### Scalability
- Stateless application instances enable horizontal scaling
- Distributed session management supports multi-instance deployments
- S3 provides unlimited storage capacity

### Reliability
- Redis-backed sessions prevent data loss during instance failures
- S3 provides 99.999999999% durability for stored files
- Secrets Manager enables automatic credential rotation

### Security
- No credentials in source code or container images
- Encrypted storage for secrets and sensitive data
- IAM-based access control for all AWS services

### Portability
- Environment-specific configuration externalized
- No hard-coded infrastructure dependencies
- Compatible with any AWS region

## Monitoring and Observability

Recommended CloudWatch metrics to monitor:
- Application logs (structured JSON format)
- Redis connection pool metrics
- S3 operation latency
- Secrets Manager API calls
- Application performance metrics

## Support

For issues or questions about cloud deployment, refer to AWS documentation:
- [Amazon ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/)
- [Amazon ElastiCache Documentation](https://docs.aws.amazon.com/elasticache/)
- [Amazon S3 Documentation](https://docs.aws.amazon.com/s3/)
