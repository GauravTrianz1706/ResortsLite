# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized to be fully cloud-ready and compatible with AWS cloud environments. All cloud readiness blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Resolution**: 
- Replaced all local file operations with Amazon S3 storage
- Implemented AWS SDK for Java v2 S3 client
- Reports are now generated and stored in S3 buckets
- File paths are externalized to environment variables

**Files Modified**:
- `ReportService.java`: Migrated from `java.io.File` to S3Client
- `BookingController.java`: Updated report download to use S3 URLs

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Resolution**:
- Integrated AWS Secrets Manager for credential storage
- Credentials are retrieved at runtime from Secrets Manager
- Supports automatic credential rotation
- Fallback to environment variables for local development

**Files Modified**:
- `BookingService.java`: Implemented Secrets Manager integration
- `application.properties`: Externalized database configuration

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Environment-specific URLs hard-coded in source code
**Resolution**:
- Integrated AWS Systems Manager Parameter Store
- All service endpoints externalized to environment variables
- Runtime retrieval of configuration parameters
- Environment-agnostic deployment support

**Files Modified**:
- `BookingController.java`: Added Parameter Store integration
- `application.properties`: Externalized all service endpoints

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Fixed port numbers preventing dynamic assignment
**Resolution**:
- Server port externalized to environment variable `SERVER_PORT`
- Compatible with ECS, EKS, and Elastic Beanstalk dynamic port assignment
- Default value provided for local development

**Files Modified**:
- `application.properties`: Port configuration externalized
- `ReportService.java`: Port injected via Spring properties

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: Session data stored in HTTP session preventing horizontal scaling
**Resolution**:
- Migrated to Amazon ElastiCache for Redis
- Implemented Spring Session with Redis backend
- Distributed session management across instances
- Session data stored with TTL (30 minutes)

**Files Modified**:
- `BookingController.java`: Replaced HttpSession with RedisTemplate
- `RedisConfig.java`: New configuration for Redis and Spring Session
- `pom.xml`: Added Spring Session and Redis dependencies

### 6. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Unbounded in-memory cache causing memory issues
**Resolution**:
- Replaced in-memory HashMap with Redis-backed cache
- Implemented TTL policies (1 hour for bookings)
- Centralized cache management via ElastiCache
- Spring Cache abstraction with Redis

**Files Modified**:
- `BookingController.java`: Migrated to Redis cache with TTL
- `RedisConfig.java`: Enabled caching with @EnableCaching
- `application.properties`: Cache TTL configuration

### 7. File-based Authentication (Blocker 17)
**Issue**: Authentication credentials stored in local files
**Resolution**:
- Integrated AWS Secrets Manager for authentication credentials
- Centralized credential management
- Support for Amazon Cognito integration (future enhancement)
- Encrypted and auditable credential storage

**Files Modified**:
- `BookingService.java`: Authentication credentials from Secrets Manager

## AWS Services Integrated

### 1. Amazon S3
- **Purpose**: Durable, scalable file storage
- **Usage**: Report generation and storage
- **Configuration**: `aws.s3.bucket.name`, `aws.s3.region`

### 2. AWS Secrets Manager
- **Purpose**: Secure credential storage with rotation
- **Usage**: Database credentials, authentication secrets
- **Configuration**: `aws.secrets.db.secret.name`, `aws.secrets.auth.secret.name`

### 3. AWS Systems Manager Parameter Store
- **Purpose**: Centralized configuration management
- **Usage**: Service endpoints, application configuration
- **Configuration**: `aws.ssm.parameter.prefix`

### 4. Amazon ElastiCache for Redis
- **Purpose**: Distributed session and cache management
- **Usage**: HTTP session storage, application caching
- **Configuration**: `spring.redis.host`, `spring.redis.port`

## Environment Variables

### Required for AWS Deployment
```bash
# Server Configuration
SERVER_PORT=8080

# Database Configuration (or use Secrets Manager)
DB_URL=jdbc:postgresql://your-rds-endpoint:5432/resortdb
DB_USERNAME=dbuser
DB_PASSWORD=dbpassword

# AWS Configuration
AWS_REGION=us-east-1
S3_BUCKET_NAME=resorts-lite-reports

# AWS Secrets Manager
DB_SECRET_NAME=resorts-lite/db/credentials
AUTH_SECRET_NAME=resorts-lite/auth/credentials

# AWS Systems Manager Parameter Store
SSM_PARAMETER_PREFIX=/resorts-lite

# Redis Configuration (ElastiCache)
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL=true

# Service Endpoints
PAYMENT_ENDPOINT=http://payment-service:9090/charge
INVENTORY_ENDPOINT=http://inventory-service:8081/rooms
NOTIFICATION_ENDPOINT=http://notification-service:7070/send
```

## AWS IAM Permissions Required

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
        "arn:aws:s3:::resorts-lite-reports",
        "arn:aws:s3:::resorts-lite-reports/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:resorts-lite/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:*:*:parameter/resorts-lite/*"
      ]
    }
  ]
}
```

## Deployment Architecture

### Recommended AWS Services
1. **Compute**: Amazon ECS (Fargate) or Amazon EKS
2. **Database**: Amazon RDS (PostgreSQL/MySQL)
3. **Cache**: Amazon ElastiCache for Redis
4. **Storage**: Amazon S3
5. **Secrets**: AWS Secrets Manager
6. **Configuration**: AWS Systems Manager Parameter Store
7. **Load Balancer**: Application Load Balancer (ALB)

### 12-Factor App Compliance
- ✅ Codebase: Single codebase tracked in version control
- ✅ Dependencies: Explicitly declared in pom.xml
- ✅ Config: Externalized to environment variables
- ✅ Backing Services: Treated as attached resources
- ✅ Build, Release, Run: Strictly separated
- ✅ Processes: Stateless with distributed session storage
- ✅ Port Binding: Self-contained with dynamic port binding
- ✅ Concurrency: Horizontally scalable
- ✅ Disposability: Fast startup and graceful shutdown
- ✅ Dev/Prod Parity: Environment-agnostic configuration
- ✅ Logs: Structured logging to stdout
- ✅ Admin Processes: Run as one-off processes

## Local Development

For local development without AWS services:

1. Use H2 in-memory database (default)
2. Install Redis locally or use Docker:
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```
3. Set minimal environment variables:
   ```bash
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   export S3_BUCKET_NAME=local-bucket
   ```

## Security Improvements

1. **Credentials**: No hard-coded credentials in source code
2. **SQL Injection**: Parameterized queries throughout
3. **Hashing**: Replaced MD5 with SHA-256
4. **Dependencies**: Updated vulnerable libraries (log4j, commons-collections)
5. **Secrets**: Encrypted storage in AWS Secrets Manager

## Testing

Run the application:
```bash
mvn spring-boot:run
```

Test endpoints:
```bash
# Create booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-03-01&checkOut=2024-03-05" \
  -H "X-Session-Id: test-session-123"

# Check booking status
curl "http://localhost:8080/api/bookings/status/BK-12345678" \
  -H "X-Session-Id: test-session-123"

# Check availability
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Externalize service endpoints to Parameter Store
- [x] Replace HTTP session with Redis
- [x] Implement distributed caching with TTL
- [x] Remove hard-coded ports
- [x] Update vulnerable dependencies
- [x] Implement parameterized SQL queries
- [x] Replace weak hashing algorithms
- [x] Add AWS SDK dependencies
- [x] Create configuration classes
- [x] Update application properties

## Next Steps

1. Set up AWS infrastructure (ECS/EKS, RDS, ElastiCache, S3)
2. Create AWS Secrets Manager secrets
3. Configure Systems Manager parameters
4. Set up IAM roles and policies
5. Deploy application to AWS
6. Configure Application Load Balancer
7. Set up CloudWatch monitoring and logging
8. Implement CI/CD pipeline
