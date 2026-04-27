# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized for AWS cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
- **Issue**: Hard-coded file paths and local file system operations
- **Fix**: Migrated to AWS S3 for durable, scalable storage
- **Files Modified**: `ReportService.java`
- **Configuration**: Set `S3_REPORTS_BUCKET` environment variable

### 2. Hard-coded Credentials → AWS Secrets Manager
- **Issue**: Database credentials embedded in source code
- **Fix**: Integrated AWS Secrets Manager for secure credential storage
- **Files Modified**: `BookingService.java`, `AwsConfig.java`
- **Configuration**: Set `SECRETS_DATABASE_NAME` environment variable

### 3. HTTP Session Storage → Amazon ElastiCache for Redis
- **Issue**: In-memory session storage preventing horizontal scaling
- **Fix**: Migrated to Redis-backed distributed session management
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **Configuration**: Set Redis connection parameters in environment variables

### 4. In-Memory Caching → Redis with TTL
- **Issue**: Unbounded in-memory cache causing memory issues
- **Fix**: Implemented Redis-based caching with configurable TTL
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **Configuration**: Set `CACHE_TTL_MINUTES` environment variable

### 5. Hard-coded Ports → Environment Variables
- **Issue**: Fixed port numbers preventing dynamic assignment
- **Fix**: Externalized port configuration to environment variables
- **Files Modified**: `ReportService.java`, `application.properties`
- **Configuration**: Set `SERVER_PORT` environment variable

### 6. Hard-coded URLs → Externalized Configuration
- **Issue**: Environment-specific URLs embedded in code
- **Fix**: Externalized all service endpoints to environment variables
- **Files Modified**: `BookingController.java`, `application.properties`
- **Configuration**: Set service endpoint environment variables

## AWS Services Required

### Core Services
1. **Amazon S3**: File storage for reports and documents
2. **AWS Secrets Manager**: Secure credential storage
3. **Amazon ElastiCache for Redis**: Session management and caching
4. **Amazon RDS**: Database (PostgreSQL/MySQL recommended)

### Optional Services
5. **AWS Systems Manager Parameter Store**: Additional configuration management
6. **Amazon CloudWatch**: Logging and monitoring
7. **AWS IAM**: Role-based access control

## Environment Variables

See `.env.template` for complete list of required environment variables.

### Critical Variables
```bash
# AWS Configuration
AWS_REGION=us-east-1

# S3 Configuration
S3_REPORTS_BUCKET=your-resort-reports-bucket

# Secrets Manager
SECRETS_DATABASE_NAME=resort-db-credentials

# Redis Configuration
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379

# Database Configuration
DATABASE_URL=jdbc:postgresql://your-rds-endpoint:5432/resortdb
```

## AWS Secrets Manager Setup

Create a secret in AWS Secrets Manager with the following structure:

```json
{
  "host": "your-rds-endpoint.region.rds.amazonaws.com",
  "username": "your_db_username",
  "password": "your_db_password"
}
```

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
        "arn:aws:s3:::your-resort-reports-bucket",
        "arn:aws:s3:::your-resort-reports-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:region:account:secret:resort-db-credentials*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:region:account:parameter/resort/*"
    }
  ]
}
```

## Deployment Checklist

- [ ] Create S3 bucket for reports
- [ ] Create ElastiCache Redis cluster
- [ ] Create RDS database instance
- [ ] Store database credentials in Secrets Manager
- [ ] Configure IAM role with required permissions
- [ ] Set all required environment variables
- [ ] Deploy application to ECS/EKS/Elastic Beanstalk
- [ ] Configure health check endpoint: `/actuator/health`
- [ ] Set up CloudWatch logging

## Health Check

The application exposes health check endpoints via Spring Boot Actuator:

- **Health**: `/actuator/health`
- **Info**: `/actuator/info`
- **Metrics**: `/actuator/metrics`

## Security Improvements

1. Replaced MD5 hashing with SHA-256
2. Implemented parameterized SQL queries to prevent SQL injection
3. Externalized all credentials to AWS Secrets Manager
4. Removed hard-coded authentication from file system

## 12-Factor App Compliance

This application now follows 12-factor app principles:

1. ✅ **Codebase**: Single codebase tracked in version control
2. ✅ **Dependencies**: Explicitly declared in pom.xml
3. ✅ **Config**: Externalized to environment variables
4. ✅ **Backing Services**: Treats AWS services as attached resources
5. ✅ **Build, Release, Run**: Strictly separated stages
6. ✅ **Processes**: Stateless with externalized session storage
7. ✅ **Port Binding**: Self-contained with configurable port
8. ✅ **Concurrency**: Horizontally scalable
9. ✅ **Disposability**: Fast startup and graceful shutdown
10. ✅ **Dev/Prod Parity**: Same backing services across environments
11. ✅ **Logs**: Treats logs as event streams
12. ✅ **Admin Processes**: Run as one-off processes

## Support

For issues or questions, contact the cloud migration team.
