# ResortsLite - Cloud-Ready Application

## Overview
This application has been transformed to be fully cloud-ready for deployment on AWS. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Resolution**: 
- Replaced all local file operations with Amazon S3
- Reports are now stored in S3 bucket instead of `/var/legacy/reports/`
- Removed hard-coded backup path `C:\ResortBackups\nightly\`
- All file operations use AWS SDK for Java v2 S3 client

**Files Modified**:
- `ReportService.java`: Migrated to S3 for report storage
- `application.properties`: Added S3 bucket configuration

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials hard-coded in source code
**Resolution**:
- Migrated to AWS Secrets Manager for credential storage
- Credentials are loaded at runtime from Secrets Manager
- Supports automatic credential rotation
- Fallback to environment variables if Secrets Manager unavailable

**Files Modified**:
- `BookingService.java`: Integrated AWS Secrets Manager client
- `application.properties`: Added secret name configuration

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Hard-coded service endpoints in source code
**Resolution**:
- Externalized all service URLs to environment variables
- URLs can be managed via AWS Systems Manager Parameter Store
- Supports different endpoints per environment (dev/staging/prod)

**Files Modified**:
- `BookingController.java`: Uses injected endpoint URLs
- `application.properties`: Externalized endpoint configuration

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Hard-coded port 8080 prevents dynamic port assignment
**Resolution**:
- Port configuration externalized to environment variable `SERVER_PORT`
- Supports dynamic port assignment by ECS/EKS/Elastic Beanstalk
- Default fallback to 8080 for local development

**Files Modified**:
- `ReportService.java`: Uses injected port value
- `application.properties`: Port from environment variable

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: Session data stored in local memory prevents horizontal scaling
**Resolution**:
- Migrated to Amazon ElastiCache for Redis for distributed session management
- Implemented Spring Session with Redis backend
- All session data automatically synchronized across instances
- Enables stateless application instances

**Files Modified**:
- `BookingController.java`: Uses Spring Session with Redis
- `RedisConfig.java`: New configuration class for Redis
- `pom.xml`: Added Spring Session and Redis dependencies
- `application.properties`: Added Redis configuration

### 6. File-based Authentication (Blocker 17)
**Issue**: Authentication credentials stored in local files
**Resolution**:
- Integrated AWS Secrets Manager for credential storage
- Credentials retrieved at runtime from centralized secret store
- Supports AWS Cognito integration for user identity management
- Encrypted storage with audit logging

**Files Modified**:
- `BookingService.java`: Uses Secrets Manager for credentials

### 7. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Unbounded in-memory cache causes memory leaks
**Resolution**:
- Replaced in-memory HashMap cache with Redis cache
- Implemented TTL (1 hour) for all cached entries
- Centralized cache management across instances
- Prevents memory growth and stale data issues

**Files Modified**:
- `BookingController.java`: Uses Redis cache with TTL
- `RedisConfig.java`: Configured RedisTemplate with serializers

## New Dependencies Added

### AWS SDK for Java v2
- `software.amazon.awssdk:s3` - S3 client for file storage
- `software.amazon.awssdk:secretsmanager` - Secrets Manager client
- `software.amazon.awssdk:ssm` - Systems Manager client

### Spring Session & Redis
- `spring-session-data-redis` - Distributed session management
- `spring-boot-starter-data-redis` - Redis integration
- `lettuce-core` - Redis client

### JSON Processing
- `jackson-databind` - JSON serialization for Redis

## Environment Variables Required

### AWS Configuration
- `AWS_REGION` - AWS region (default: us-east-1)
- `S3_BUCKET_NAME` - S3 bucket for reports (default: resorts-reports)
- `DB_CREDENTIALS_SECRET_NAME` - Secrets Manager secret name

### Server Configuration
- `SERVER_PORT` - Application port (default: 8080)

### Database Configuration
- `DB_URL` - Database connection URL
- `DB_USERNAME` - Database username (fallback)
- `DB_PASSWORD` - Database password (fallback)

### Redis Configuration
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)

### Service Endpoints
- `PAYMENT_ENDPOINT` - Payment service URL
- `INVENTORY_ENDPOINT` - Inventory service URL
- `NOTIFICATION_ENDPOINT` - Notification service URL

## AWS Resources Required

### 1. Amazon S3
- Create S3 bucket for report storage
- Configure bucket policy for application access
- Enable versioning and encryption

### 2. AWS Secrets Manager
- Create secret for database credentials
- Format: JSON with keys: `host`, `username`, `password`
- Enable automatic rotation (optional)

### 3. Amazon ElastiCache for Redis
- Create Redis cluster (single node or cluster mode)
- Configure security group for application access
- Note connection endpoint and port

### 4. AWS Systems Manager Parameter Store (Optional)
- Store configuration parameters
- Example: `/resorts/config/backup-policy`

### 5. IAM Role/Policy
Application requires IAM permissions for:
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
        "arn:aws:s3:::resorts-reports/*",
        "arn:aws:s3:::resorts-reports"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:resorts/db/credentials-*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/resorts/*"
    }
  ]
}
```

## Deployment Options

### AWS Elastic Container Service (ECS)
- Deploy as Fargate task or EC2-backed service
- Configure task definition with environment variables
- Attach IAM task role with required permissions
- Connect to ElastiCache Redis via VPC

### AWS Elastic Kubernetes Service (EKS)
- Deploy as Kubernetes deployment
- Use ConfigMaps for configuration
- Use Secrets for sensitive data
- Attach IAM role via IRSA (IAM Roles for Service Accounts)

### AWS Elastic Beanstalk
- Deploy as Java application
- Configure environment properties
- Attach IAM instance profile
- Connect to ElastiCache via VPC

## Local Development

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker (for local Redis)

### Running Locally
1. Start local Redis:
   ```bash
   docker run -d -p 6379:6379 redis:7-alpine
   ```

2. Set environment variables:
   ```bash
   export AWS_REGION=us-east-1
   export S3_BUCKET_NAME=resorts-reports-dev
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   ```

3. Build and run:
   ```bash
   mvn clean package
   java -jar target/resortsLite-1.0.0.jar
   ```

### Testing Without AWS
For local testing without AWS services:
- Redis is required (use Docker)
- AWS SDK will use default credentials or fail gracefully
- Set fallback environment variables for DB credentials

## Migration Checklist

- [x] Replace hard-coded file paths with S3
- [x] Replace local file writes with S3 uploads
- [x] Migrate java.io.File operations to S3
- [x] Replace hard-coded database credentials with Secrets Manager
- [x] Externalize environment URLs to Parameter Store
- [x] Replace hard-coded ports with environment variables
- [x] Migrate HTTP session storage to Redis
- [x] Replace file-based authentication with Secrets Manager
- [x] Replace unbounded in-memory cache with Redis cache with TTL

## Architecture Improvements

### Before (Legacy)
- Local file system for reports
- Hard-coded credentials in source code
- In-memory session storage (single instance)
- Unbounded in-memory cache
- Hard-coded configuration values

### After (Cloud-Ready)
- Amazon S3 for durable file storage
- AWS Secrets Manager for credential management
- Amazon ElastiCache for Redis (distributed sessions)
- Redis cache with TTL policies
- Externalized configuration via environment variables
- Stateless application instances
- Horizontal scaling support
- 12-factor app compliance

## Monitoring and Observability

### CloudWatch Integration
- Application logs sent to CloudWatch Logs
- Metrics for S3 operations, Redis connections
- Alarms for error rates and latency

### X-Ray Integration (Future)
- Distributed tracing across services
- Performance analysis and bottleneck identification

## Security Enhancements

1. **Credentials Management**: All credentials in Secrets Manager
2. **Encryption**: Data encrypted at rest (S3, Secrets Manager)
3. **Network Security**: VPC isolation for Redis and databases
4. **IAM Policies**: Least privilege access for AWS resources
5. **Audit Logging**: CloudTrail logs for all AWS API calls

## Performance Considerations

1. **S3 Operations**: Asynchronous uploads for large files
2. **Redis Caching**: 1-hour TTL reduces database load
3. **Connection Pooling**: Reuse AWS SDK clients
4. **Session Management**: Redis provides fast session access

## Cost Optimization

1. **S3 Lifecycle Policies**: Archive old reports to Glacier
2. **Redis Instance Sizing**: Right-size based on session volume
3. **Secrets Manager**: Minimize API calls with caching
4. **Parameter Store**: Free tier for standard parameters

## Support and Troubleshooting

### Common Issues

**Issue**: Application cannot connect to Redis
**Solution**: Check security group rules, verify REDIS_HOST and REDIS_PORT

**Issue**: S3 access denied
**Solution**: Verify IAM role has s3:PutObject and s3:GetObject permissions

**Issue**: Secrets Manager secret not found
**Solution**: Verify secret name matches DB_CREDENTIALS_SECRET_NAME

**Issue**: Session data not persisting
**Solution**: Verify Redis connection and Spring Session configuration

## Next Steps

1. Set up AWS infrastructure (S3, ElastiCache, Secrets Manager)
2. Configure IAM roles and policies
3. Deploy application to ECS/EKS/Elastic Beanstalk
4. Configure monitoring and alerting
5. Set up CI/CD pipeline for automated deployments
6. Implement health checks and auto-scaling policies
