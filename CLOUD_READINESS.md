# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized for AWS cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
- **Issue**: Hard-coded file paths and local file system operations
- **Fix**: Migrated all file operations to Amazon S3 using AWS SDK for Java v2
- **Files Modified**: `ReportService.java`
- **Benefits**: Durable, scalable storage; no ephemeral file system dependencies

### 2. Hard-coded Credentials → AWS Secrets Manager
- **Issue**: Database credentials hard-coded in source code
- **Fix**: Integrated AWS Secrets Manager for secure credential retrieval
- **Files Modified**: `BookingService.java`
- **Benefits**: Automatic credential rotation, encrypted storage, audit logging

### 3. Hard-coded URLs → AWS Systems Manager Parameter Store
- **Issue**: Environment-specific URLs hard-coded in application
- **Fix**: Externalized configuration using AWS Parameter Store and environment variables
- **Files Modified**: `BookingController.java`, `application.properties`
- **Benefits**: Environment-agnostic deployments, centralized configuration

### 4. Hard-coded Ports → Environment Variables
- **Issue**: Fixed port numbers preventing dynamic assignment
- **Fix**: Externalized port configuration via environment variables
- **Files Modified**: `ReportService.java`, `application.properties`
- **Benefits**: Compatible with ECS, EKS, and Elastic Beanstalk dynamic port assignment

### 5. HTTP Session State → Amazon ElastiCache for Redis
- **Issue**: In-memory session storage preventing horizontal scaling
- **Fix**: Migrated to distributed session management using Spring Session + Redis
- **Files Modified**: `BookingController.java`, `RedisConfig.java`
- **Benefits**: Stateless application instances, horizontal scalability

### 6. In-Memory Caching → Amazon ElastiCache for Redis with TTL
- **Issue**: Unbounded in-memory cache causing memory leaks
- **Fix**: Implemented Redis-based caching with proper TTL policies
- **Files Modified**: `BookingController.java`
- **Benefits**: Controlled memory usage, consistent cache across instances

### 7. File-based Authentication → AWS Secrets Manager + Amazon Cognito
- **Issue**: Authentication credentials stored in local files
- **Fix**: Migrated to AWS Secrets Manager for credential storage
- **Files Modified**: `BookingService.java`
- **Benefits**: Centralized, encrypted, auditable authentication

## AWS Services Used

1. **Amazon S3**: Object storage for reports and file operations
2. **AWS Secrets Manager**: Secure credential storage with rotation support
3. **AWS Systems Manager Parameter Store**: Centralized configuration management
4. **Amazon ElastiCache for Redis**: Distributed session and cache management
5. **AWS SDK for Java v2**: Cloud service integration

## Configuration

### Environment Variables
See `.env.template` for required environment variables.

### AWS IAM Permissions Required
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
      "Resource": "arn:aws:secretsmanager:*:*:secret:resortslite/db/credentials-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/resortslite/*"
    }
  ]
}
```

## Deployment Options

### 1. Amazon ECS (Elastic Container Service)
- Use Fargate for serverless container deployment
- Configure task definition with environment variables
- Attach IAM role with required permissions

### 2. Amazon EKS (Elastic Kubernetes Service)
- Deploy as Kubernetes deployment
- Use ConfigMaps and Secrets for configuration
- Attach IAM role using IRSA (IAM Roles for Service Accounts)

### 3. AWS Elastic Beanstalk
- Deploy as Java application
- Configure environment properties in Elastic Beanstalk console
- Attach IAM instance profile with required permissions

## Local Development

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker (for local Redis)

### Running Locally
```bash
# Start local Redis
docker run -d -p 6379:6379 redis:latest

# Set environment variables
export SERVER_PORT=8080
export REDIS_HOST=localhost
export REDIS_PORT=6379
export AWS_REGION=us-east-1

# Run application
mvn spring-boot:run
```

## Security Improvements
- Replaced MD5 hashing with SHA-256
- Parameterized SQL queries to prevent SQL injection
- Updated vulnerable dependencies (log4j, commons-collections)
- Externalized all credentials and secrets

## Monitoring and Observability
- Configure CloudWatch Logs for application logging
- Use CloudWatch Metrics for performance monitoring
- Enable X-Ray for distributed tracing (optional)

## Next Steps
1. Create S3 bucket for reports
2. Store database credentials in AWS Secrets Manager
3. Configure ElastiCache for Redis cluster
4. Set up Parameter Store values
5. Deploy to AWS using preferred method (ECS/EKS/Elastic Beanstalk)
