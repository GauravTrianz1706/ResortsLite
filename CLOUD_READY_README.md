# ResortsLite - Cloud-Ready Application for AWS

## Overview
This application has been modernized to be fully cloud-ready and compatible with AWS services. All cloud readiness blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Issues Fixed:**
- Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- Local file system write operations
- Java.io.File usage for data storage

**Solution:**
- Migrated all file operations to Amazon S3 using AWS SDK for Java v2
- Reports and backups now stored in S3 buckets
- Bucket names externalized to configuration

**Configuration:**
```properties
aws.s3.reports.bucket=resort-reports-bucket
aws.s3.backups.bucket=resort-backups-bucket
```

### 2. Hard-coded Credentials → AWS Secrets Manager
**Issues Fixed:**
- Hard-coded database credentials in source code
- File-based authentication

**Solution:**
- Database credentials retrieved from AWS Secrets Manager
- Automatic fallback to environment variables
- Secure credential rotation support

**Configuration:**
```properties
aws.secrets.database.name=resort-db-credentials
```

**Secret Format (JSON):**
```json
{
  "host": "database-endpoint.region.rds.amazonaws.com",
  "username": "admin",
  "password": "secure-password"
}
```

### 3. HTTP Session State → Amazon ElastiCache for Redis
**Issues Fixed:**
- HTTP session state storage preventing horizontal scaling
- In-memory caching without TTL
- Static state variables

**Solution:**
- Migrated to Spring Session with Redis backend
- All session data stored in Amazon ElastiCache for Redis
- Distributed caching with configurable TTL
- Stateless application instances

**Configuration:**
```properties
spring.redis.host=elasticache-endpoint.region.cache.amazonaws.com
spring.redis.port=6379
spring.session.timeout=30m
app.cache.ttl.minutes=30
```

### 4. Hard-coded URLs → Externalized Configuration
**Issues Fixed:**
- Hard-coded environment URLs
- Hard-coded service endpoints

**Solution:**
- All URLs externalized to environment variables
- Support for AWS Systems Manager Parameter Store
- Environment-agnostic deployments

**Configuration:**
```properties
app.payment.endpoint=http://payment-service:9090/payments/charge
app.inventory.endpoint=http://inventory-service:8081/rooms/available
app.notification.endpoint=http://notification-service:7070/send
```

### 5. Hard-coded Ports → Dynamic Port Assignment
**Issues Fixed:**
- Hard-coded port 8080 preventing dynamic assignment

**Solution:**
- Server port externalized to environment variable
- Compatible with ECS, EKS, and Elastic Beanstalk dynamic port assignment

**Configuration:**
```properties
server.port=${SERVER_PORT:8080}
```

## AWS Services Integration

### Required AWS Services
1. **Amazon S3** - Object storage for reports and backups
2. **AWS Secrets Manager** - Secure credential storage
3. **Amazon ElastiCache for Redis** - Distributed session and cache management
4. **AWS Systems Manager Parameter Store** (optional) - Configuration management

### IAM Permissions Required
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

## Environment Variables

### Required Environment Variables
```bash
# AWS Configuration
AWS_REGION=us-east-1

# Server Configuration
SERVER_PORT=8080

# Database Configuration
DB_URL=jdbc:postgresql://database-endpoint.rds.amazonaws.com:5432/resortdb
DB_USERNAME=admin
DB_PASSWORD=secure-password

# Redis Configuration
REDIS_HOST=elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=redis-password

# S3 Configuration
S3_REPORTS_BUCKET=resort-reports-bucket
S3_BACKUPS_BUCKET=resort-backups-bucket

# Secrets Manager
SECRETS_DB_NAME=resort-db-credentials

# External Services
PAYMENT_ENDPOINT=http://payment-service:9090/payments/charge
INVENTORY_ENDPOINT=http://inventory-service:8081/rooms/available
NOTIFICATION_ENDPOINT=http://notification-service:7070/send

# Cache Configuration
CACHE_TTL_MINUTES=30
SESSION_TIMEOUT=30m
```

## Deployment Options

### 1. Amazon ECS (Elastic Container Service)
- Fully stateless application
- Dynamic port assignment supported
- Service discovery for inter-service communication
- Integration with Application Load Balancer

### 2. Amazon EKS (Elastic Kubernetes Service)
- Kubernetes-native deployment
- Horizontal pod autoscaling
- ConfigMaps and Secrets for configuration
- Ingress controller for routing

### 3. AWS Elastic Beanstalk
- Simplified deployment and management
- Automatic scaling and load balancing
- Environment variable configuration
- Integrated monitoring

## Security Improvements

1. **Credential Management**
   - No credentials in source code or version control
   - Automatic credential rotation support
   - Encrypted at rest in AWS Secrets Manager

2. **Secure Hashing**
   - Replaced MD5 with SHA-256 for secure hashing
   - Protection against hash collision attacks

3. **SQL Injection Prevention**
   - Parameterized queries throughout
   - No string concatenation in SQL statements

4. **Dependency Security**
   - Updated Log4j to 2.20.0 (fixes Log4Shell CVE-2021-44228)
   - Updated commons-collections to 4.4 (fixes CVE-2015-6420)

## 12-Factor App Compliance

✅ **I. Codebase** - Single codebase tracked in version control
✅ **II. Dependencies** - Explicitly declared in pom.xml
✅ **III. Config** - Externalized to environment variables
✅ **IV. Backing Services** - S3, Redis, Secrets Manager as attached resources
✅ **V. Build, Release, Run** - Strict separation of stages
✅ **VI. Processes** - Stateless processes with Redis for shared state
✅ **VII. Port Binding** - Self-contained with dynamic port assignment
✅ **VIII. Concurrency** - Horizontal scaling supported
✅ **IX. Disposability** - Fast startup and graceful shutdown
✅ **X. Dev/Prod Parity** - Same backing services across environments
✅ **XI. Logs** - Treat logs as event streams
✅ **XII. Admin Processes** - Run as one-off processes

## Testing

### Local Development
For local development without AWS services:
```bash
# Use H2 in-memory database
DB_URL=jdbc:h2:mem:resortdb

# Use local Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Mock S3 with LocalStack
S3_REPORTS_BUCKET=local-reports
S3_BACKUPS_BUCKET=local-backups
```

### AWS Environment
Deploy to AWS with proper IAM roles and service endpoints configured.

## Monitoring and Observability

### CloudWatch Integration
- Application logs sent to CloudWatch Logs
- Metrics for S3 operations, Redis connections, and API calls
- Alarms for error rates and latency

### Health Checks
- Spring Boot Actuator endpoints for health monitoring
- Redis connection health check
- S3 bucket accessibility check

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Migrate HTTP sessions to Redis
- [x] Externalize all configuration to environment variables
- [x] Remove hard-coded ports
- [x] Update vulnerable dependencies
- [x] Implement secure hashing algorithms
- [x] Add AWS SDK dependencies
- [x] Configure Spring Session with Redis
- [x] Create AWS configuration beans
- [x] Update application.properties with externalized config
- [x] Document deployment requirements

## Support

For issues or questions regarding the cloud-ready implementation, please refer to the AWS documentation:
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Amazon S3](https://docs.aws.amazon.com/s3/)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/)
- [Amazon ElastiCache for Redis](https://docs.aws.amazon.com/elasticache/)
- [Spring Session](https://spring.io/projects/spring-session)
