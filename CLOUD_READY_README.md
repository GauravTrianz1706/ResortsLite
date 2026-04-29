# ResortsLite - Cloud-Ready Application

## Overview
This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Blockers Fixed:** cr-java-0061, cr-java-0062, cr-java-0063

- **Before:** Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- **After:** All file operations migrated to Amazon S3
- **Implementation:**
  - Added AWS SDK for Java v2 (S3 client)
  - ReportService now uploads reports directly to S3
  - S3 bucket name and region configurable via environment variables
  - No local file system dependencies

### 2. Hard-coded Database Credentials → AWS Secrets Manager
**Blockers Fixed:** cr-java-0069, cr-java-0090

- **Before:** Database credentials hard-coded in source code
- **After:** Credentials retrieved from AWS Secrets Manager at runtime
- **Implementation:**
  - Added AWS Secrets Manager client
  - BookingService loads credentials on initialization
  - Supports automatic credential rotation
  - Authentication data moved from file-based to Secrets Manager

### 3. Hard-coded Environment URLs → AWS Systems Manager Parameter Store
**Blockers Fixed:** cr-java-0071

- **Before:** Hard-coded service URLs (`http://inventory-service.internal:8081`)
- **After:** URLs externalized to environment variables and Parameter Store
- **Implementation:**
  - All service endpoints configurable via environment variables
  - ReportService retrieves URLs from Parameter Store
  - Supports environment-specific configuration without code changes

### 4. Hard-coded Ports → Environment Variables
**Blockers Fixed:** cr-java-0077

- **Before:** Hard-coded port 8080
- **After:** Port configurable via `SERVER_PORT` environment variable
- **Implementation:**
  - `server.port=${SERVER_PORT:8080}` in application.properties
  - Compatible with ECS, EKS, and Elastic Beanstalk dynamic port assignment

### 5. HTTP Session State → Amazon ElastiCache for Redis
**Blockers Fixed:** cr-java-0065 (5 instances)

- **Before:** HTTP session state stored in-memory (non-scalable)
- **After:** Distributed session management with Spring Session + Redis
- **Implementation:**
  - Added Spring Session Data Redis dependency
  - RedisConfig enables distributed session storage
  - Sessions persist across instance restarts and scale horizontally
  - Session timeout: 30 minutes (configurable)

### 6. In-Memory Caching Without TTL → ElastiCache for Redis
**Blockers Fixed:** cr-java-0067

- **Before:** Unbounded in-memory HashMap cache
- **After:** Redis-backed cache with TTL policies
- **Implementation:**
  - Spring Cache abstraction with Redis backend
  - Default TTL: 1 hour (configurable)
  - Prevents memory leaks and ensures data consistency across instances

## AWS Services Used

1. **Amazon S3** - Durable object storage for reports and files
2. **AWS Secrets Manager** - Encrypted credential storage with rotation support
3. **AWS Systems Manager Parameter Store** - Centralized configuration management
4. **Amazon ElastiCache for Redis** - Distributed caching and session management

## Configuration

### Environment Variables

```bash
# Server Configuration
SERVER_PORT=8080

# Database Configuration
DB_URL=jdbc:postgresql://your-rds-instance.region.rds.amazonaws.com:5432/resorts
DB_USERNAME=admin
DB_PASSWORD=your-password

# AWS Configuration
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=resorts-reports-bucket
AWS_DB_SECRET_NAME=resorts/db/credentials
AWS_AUTH_SECRET_NAME=resorts/auth/credentials
AWS_SSM_PARAMETER_PREFIX=/resorts/config

# Redis Configuration (ElastiCache)
REDIS_HOST=your-elasticache-cluster.region.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Service Endpoints
PAYMENT_ENDPOINT=https://payment-api.example.com/charge
INVENTORY_ENDPOINT=https://inventory-api.example.com/rooms
NOTIFICATION_ENDPOINT=https://notification-api.example.com/send
```

### AWS Secrets Manager Secret Format

**Database Credentials Secret (`resorts/db/credentials`):**
```json
{
  "host": "your-rds-instance.region.rds.amazonaws.com",
  "username": "admin",
  "password": "your-secure-password",
  "database": "resorts"
}
```

**Authentication Credentials Secret (`resorts/auth/credentials`):**
```json
{
  "username": "admin",
  "passwordHash": "sha256-hash-of-password"
}
```

### AWS Systems Manager Parameters

Create the following parameters in Parameter Store:

- `/resorts/config/report/base-url` - Base URL for report downloads

## Deployment Readiness

### AWS ECS/Fargate
- ✅ No local file system dependencies
- ✅ Dynamic port assignment supported
- ✅ Stateless application design
- ✅ Externalized configuration
- ✅ IAM role-based AWS service access

### AWS EKS (Kubernetes)
- ✅ Horizontal scaling supported
- ✅ Distributed session management
- ✅ No server affinity required
- ✅ ConfigMaps/Secrets integration ready

### AWS Elastic Beanstalk
- ✅ Environment variable configuration
- ✅ Auto-scaling compatible
- ✅ Load balancer ready

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
        "arn:aws:s3:::resorts-reports-bucket",
        "arn:aws:s3:::resorts-reports-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:resorts/db/credentials-*",
        "arn:aws:secretsmanager:*:*:secret:resorts/auth/credentials-*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:*:*:parameter/resorts/config/*"
      ]
    }
  ]
}
```

## Testing

### Local Development
For local development, you can use LocalStack or AWS credentials:

```bash
# Set AWS credentials
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1

# Run Redis locally
docker run -d -p 6379:6379 redis:latest

# Run application
mvn spring-boot:run
```

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Externalize service URLs to environment variables
- [x] Replace hard-coded ports with environment variables
- [x] Migrate HTTP sessions to Redis
- [x] Implement distributed caching with TTL
- [x] Add AWS SDK dependencies
- [x] Configure Spring Session with Redis
- [x] Create AWS configuration beans
- [x] Update application.properties for cloud deployment

## Next Steps

1. **Create AWS Resources:**
   - S3 bucket for reports
   - ElastiCache Redis cluster
   - Secrets Manager secrets
   - Parameter Store parameters

2. **Configure IAM:**
   - Create IAM role with required permissions
   - Attach role to ECS task or EC2 instance

3. **Deploy Application:**
   - Build Docker image (separate workflow)
   - Deploy to ECS/EKS/Elastic Beanstalk
   - Configure environment variables
   - Test all endpoints

## Support

For issues or questions about the cloud-ready implementation, refer to:
- AWS SDK for Java v2 documentation
- Spring Session documentation
- Spring Cache documentation
