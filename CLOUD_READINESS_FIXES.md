# ResortsLite - Cloud-Ready Application

## Overview
This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Issues Fixed:**
- Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- Local file system write operations
- Java.io.File usage for data storage

**Solution:**
- Replaced all file operations with Amazon S3 using AWS SDK for Java v2
- Reports are now stored in S3 bucket (configurable via `aws.s3.bucket.reports`)
- In-memory CSV generation before S3 upload
- No dependency on local file system

**Files Modified:**
- `ReportService.java` - Complete rewrite to use S3Client

### 2. Hard-coded Database Credentials → AWS Secrets Manager
**Issues Fixed:**
- Hard-coded database host, username, and password in source code
- Security vulnerability from credentials in version control

**Solution:**
- Integrated AWS Secrets Manager for secure credential storage
- Credentials loaded at application startup from Secrets Manager
- Fallback to environment variables for local development
- Supports automatic credential rotation

**Files Modified:**
- `BookingService.java` - Added Secrets Manager integration
- `application.properties` - Externalized all database configuration

### 3. Hard-coded Environment URLs → AWS Systems Manager Parameter Store
**Issues Fixed:**
- Hard-coded service endpoints (`http://inventory-service.internal:8081`)
- Hard-coded report download URLs

**Solution:**
- All service endpoints externalized to environment variables
- AWS Systems Manager Parameter Store integration for dynamic configuration
- Configuration retrieved at runtime from Parameter Store
- Fallback to environment variables

**Files Modified:**
- `BookingController.java` - Uses externalized inventory endpoint
- `ReportService.java` - Retrieves base URL from Parameter Store
- `application.properties` - All endpoints configurable

### 4. Hard-coded Ports → Dynamic Port Configuration
**Issues Fixed:**
- Hard-coded port 8080 preventing dynamic assignment by ECS/EKS

**Solution:**
- Server port now configurable via `SERVER_PORT` environment variable
- Default value of 8080 for local development
- Compatible with container orchestration platforms

**Files Modified:**
- `application.properties` - `server.port=${SERVER_PORT:8080}`

### 5. HTTP Session State → Amazon ElastiCache for Redis
**Issues Fixed:**
- HTTP session storage preventing horizontal scaling
- Server affinity issues
- Session data loss on instance termination

**Solution:**
- Migrated to Spring Session with Redis backend
- All session data stored in Amazon ElastiCache for Redis
- Stateless application instances
- Session data survives instance restarts
- Custom session ID via `X-Session-Id` header

**Files Modified:**
- `BookingController.java` - Replaced HttpSession with RedisTemplate
- `RedisConfig.java` - New configuration class for Redis and Spring Session
- `application.properties` - Redis connection configuration
- `pom.xml` - Added Spring Session and Redis dependencies

### 6. In-Memory Caching Without TTL → Redis with TTL
**Issues Fixed:**
- Unbounded in-memory cache causing memory growth
- Cache inconsistency across multiple instances

**Solution:**
- Replaced in-memory HashMap with Redis-backed cache
- Configurable TTL (default 1 hour)
- Distributed cache shared across all instances
- Spring Cache abstraction with Redis backend

**Files Modified:**
- `BookingController.java` - Uses RedisTemplate with TTL
- `RedisConfig.java` - Enables caching with @EnableCaching
- `application.properties` - Cache TTL configuration

### 7. File-based Authentication → AWS Secrets Manager
**Issues Fixed:**
- Authentication credentials stored in local files
- Not scalable in distributed environments

**Solution:**
- Credentials managed through AWS Secrets Manager
- Centralized, encrypted credential storage
- Supports credential rotation
- Auditable access logs

**Files Modified:**
- `BookingService.java` - Integrated Secrets Manager for authentication

## AWS Services Used

### Amazon S3
- **Purpose:** Durable, scalable object storage for reports
- **Configuration:** `aws.s3.bucket.reports`
- **Benefits:** No local file system dependency, automatic replication, versioning support

### AWS Secrets Manager
- **Purpose:** Secure storage for database credentials and sensitive data
- **Configuration:** `aws.secrets.db-credentials`
- **Benefits:** Automatic rotation, encryption at rest, audit logging

### AWS Systems Manager Parameter Store
- **Purpose:** Centralized configuration management
- **Configuration:** `aws.ssm.parameter.prefix`
- **Benefits:** Hierarchical parameters, versioning, change notifications

### Amazon ElastiCache for Redis
- **Purpose:** Distributed session storage and caching
- **Configuration:** `spring.redis.host`, `spring.redis.port`
- **Benefits:** High availability, automatic failover, sub-millisecond latency

## Configuration

### Environment Variables
All configuration is externalized via environment variables:

```bash
# Server Configuration
SERVER_PORT=8080

# Database Configuration
DB_URL=jdbc:postgresql://mydb.region.rds.amazonaws.com:5432/resortdb
DB_USERNAME=dbuser
DB_PASSWORD=dbpass
DB_POOL_SIZE=10

# Redis Configuration (ElastiCache)
REDIS_HOST=my-elasticache.region.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=myredispass
REDIS_SSL=true

# AWS Configuration
AWS_REGION=us-east-1
S3_REPORTS_BUCKET=my-resort-reports-bucket
SECRETS_DB_CREDENTIALS=resort/db/credentials
SSM_PARAMETER_PREFIX=/resort/config

# External Service Endpoints
PAYMENT_ENDPOINT=http://payment-svc:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms
NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

# Cache Configuration
CACHE_TTL=3600000

# Logging
LOG_LEVEL=INFO
APP_LOG_LEVEL=DEBUG
```

### AWS Secrets Manager Secret Format
Database credentials secret should be in JSON format:

```json
{
  "host": "mydb.region.rds.amazonaws.com",
  "username": "dbuser",
  "password": "securepassword"
}
```

### AWS Systems Manager Parameters
Create parameters with the following structure:

```
/resort/config/report-base-url = https://reports.myresort.com
/resort/config/payment-endpoint = http://payment-svc:9090/charge
/resort/config/inventory-endpoint = http://inventory-svc:8081/rooms
```

## Deployment Considerations

### ECS/Fargate
- All environment variables can be injected via task definition
- Redis endpoint should point to ElastiCache cluster
- S3 bucket access via IAM task role
- Secrets Manager access via IAM task role

### EKS (Kubernetes)
- Use ConfigMaps for non-sensitive configuration
- Use Secrets for sensitive data
- Consider using External Secrets Operator for Secrets Manager integration
- Redis can be deployed as StatefulSet or use ElastiCache

### Elastic Beanstalk
- Configure environment variables in Elastic Beanstalk console
- Attach IAM instance profile with necessary permissions
- Redis endpoint from ElastiCache

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
        "arn:aws:s3:::resort-reports-bucket",
        "arn:aws:s3:::resort-reports-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:region:account:secret:resort/db/credentials*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": [
        "arn:aws:ssm:region:account:parameter/resort/config/*"
      ]
    }
  ]
}
```

## Dependencies Added

### AWS SDK for Java v2
- `software.amazon.awssdk:s3` - S3 client
- `software.amazon.awssdk:secretsmanager` - Secrets Manager client
- `software.amazon.awssdk:ssm` - Systems Manager client

### Spring Session & Redis
- `spring-session-data-redis` - Distributed session management
- `spring-boot-starter-data-redis` - Redis integration

### Connection Pooling
- HikariCP (included in spring-boot-starter-jdbc)

## Testing Locally

### Prerequisites
1. Install Redis locally or use Docker:
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```

2. Set environment variables:
   ```bash
   export AWS_REGION=us-east-1
   export S3_REPORTS_BUCKET=local-test-bucket
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   ```

3. For local testing without AWS services, the application falls back to:
   - Environment variables for credentials
   - Local Redis for session/cache
   - In-memory H2 database

### Running the Application
```bash
mvn spring-boot:run
```

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Externalize service endpoints to Parameter Store
- [x] Make server port configurable
- [x] Replace HTTP session with Redis
- [x] Add TTL to caching mechanism
- [x] Remove hard-coded paths and URLs
- [x] Add AWS SDK dependencies
- [x] Add Redis and Spring Session dependencies
- [x] Configure connection pooling (HikariCP)
- [x] Update application.properties with externalized config
- [x] Create AWS configuration beans
- [x] Create Redis configuration

## Cloud-Native Principles Applied

1. **12-Factor App Compliance**
   - ✅ Externalized configuration
   - ✅ Stateless processes
   - ✅ Backing services as attached resources
   - ✅ Port binding via environment variables

2. **Scalability**
   - ✅ Horizontal scaling support
   - ✅ No server affinity
   - ✅ Distributed session management
   - ✅ Distributed caching

3. **Security**
   - ✅ No credentials in source code
   - ✅ Encrypted secrets storage
   - ✅ IAM-based access control
   - ✅ Parameterized SQL queries

4. **Resilience**
   - ✅ Connection pooling
   - ✅ Graceful fallbacks
   - ✅ Configurable timeouts
   - ✅ Distributed state management

## Next Steps

1. **Create AWS Resources:**
   - S3 bucket for reports
   - ElastiCache Redis cluster
   - Secrets Manager secrets
   - Parameter Store parameters
   - RDS database (if not using H2)

2. **Configure IAM:**
   - Create IAM role with required permissions
   - Attach role to ECS task or EC2 instance

3. **Deploy Application:**
   - Build Docker image (separate workflow)
   - Deploy to ECS/EKS/Elastic Beanstalk
   - Configure environment variables
   - Test all endpoints

4. **Monitor:**
   - Set up CloudWatch logs
   - Configure CloudWatch metrics
   - Set up alarms for errors and performance
