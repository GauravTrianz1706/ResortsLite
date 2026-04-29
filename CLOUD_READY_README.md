# ResortsLite - Cloud-Ready Application for AWS

## Overview
This application has been modernized to be fully cloud-ready and compatible with AWS services. All cloud readiness blockers have been resolved to enable successful deployment on AWS ECS, EKS, or Elastic Beanstalk.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Issues Fixed:**
- Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- Local file system write operations
- Java.io.File usage for data storage

**Solution:**
- Migrated all file operations to Amazon S3 using AWS SDK for Java v2
- Reports are now stored in S3 bucket with configurable bucket name
- File paths are externalized to environment variables and AWS Parameter Store

**Files Modified:**
- `ReportService.java`: Replaced File I/O with S3Client operations
- `application.properties`: Added S3 configuration parameters

### 2. Hard-coded Database Credentials → AWS Secrets Manager
**Issues Fixed:**
- Hard-coded database host, username, and password in source code
- Security vulnerability from credentials in version control

**Solution:**
- Migrated database credentials to AWS Secrets Manager
- Credentials are loaded at runtime from Secrets Manager
- Supports automatic credential rotation without redeployment

**Files Modified:**
- `BookingService.java`: Added Secrets Manager integration
- `application.properties`: Added Secrets Manager configuration

### 3. Hard-coded Environment URLs → AWS Parameter Store
**Issues Fixed:**
- Hard-coded service endpoints (payment, inventory, notification)
- Environment-specific URLs preventing portability

**Solution:**
- Externalized all service URLs to environment variables
- Configuration can be managed via AWS Systems Manager Parameter Store
- Supports different values per environment (dev, staging, prod)

**Files Modified:**
- `BookingController.java`: Uses externalized endpoint configuration
- `application.properties`: Added environment variable placeholders

### 4. Hard-coded Ports → Dynamic Port Assignment
**Issues Fixed:**
- Hard-coded port 8080 preventing dynamic assignment by ECS/EKS

**Solution:**
- Server port is now configurable via `SERVER_PORT` environment variable
- Defaults to 8080 for local development
- Cloud platforms can assign ports dynamically

**Files Modified:**
- `ReportService.java`: Removed hard-coded port references
- `application.properties`: Added dynamic port configuration

### 5. HTTP Session State → Amazon ElastiCache for Redis
**Issues Fixed:**
- HTTP session storage creating server affinity
- Session data loss during instance termination
- Inability to scale horizontally

**Solution:**
- Migrated to Spring Session with Redis backend
- Session data stored in Amazon ElastiCache for Redis
- Enables stateless application instances with distributed session management

**Files Modified:**
- `BookingController.java`: Uses Redis-backed sessions
- `RedisConfig.java`: New configuration for Spring Session
- `pom.xml`: Added Spring Session and Redis dependencies

### 6. In-Memory Caching Without TTL → Redis with TTL
**Issues Fixed:**
- Unbounded in-memory cache causing memory growth
- Stale data across multiple instances
- No cache expiration policy

**Solution:**
- Replaced in-memory HashMap with Redis cache
- Configured TTL (Time-To-Live) for all cached entries
- Centralized cache management across all instances

**Files Modified:**
- `BookingController.java`: Replaced HashMap with RedisTemplate
- `RedisConfig.java`: Enabled caching with TTL configuration
- `application.properties`: Added cache TTL settings

### 7. File-based Authentication → AWS Secrets Manager + Cognito
**Issues Fixed:**
- Authentication credentials stored in local files
- No horizontal scaling support
- Security and consistency issues

**Solution:**
- Migrated authentication to AWS Secrets Manager
- Added support for Amazon Cognito integration
- Centralized, encrypted, and auditable authentication

**Files Modified:**
- `BookingService.java`: Added authenticateUser method using Secrets Manager

## AWS Services Integration

### Required AWS Services
1. **Amazon S3**: File storage for reports and backups
2. **AWS Secrets Manager**: Secure credential storage
3. **AWS Systems Manager Parameter Store**: Configuration management
4. **Amazon ElastiCache for Redis**: Session and cache storage
5. **Amazon RDS** (optional): Managed database service

### Environment Variables
```bash
# Server Configuration
SERVER_PORT=8080

# Database Configuration
DB_URL=jdbc:postgresql://your-rds-endpoint:5432/resortdb
DB_USERNAME=admin
DB_PASSWORD=your-password
DB_POOL_SIZE=10

# Redis Configuration (ElastiCache)
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL=true

# AWS Configuration
AWS_REGION=us-east-1
S3_BUCKET_NAME=resorts-lite-reports
DB_SECRET_NAME=resorts-lite/db-credentials
AUTH_SECRET_NAME=resorts-lite/auth-credentials
SSM_PARAMETER_PREFIX=/resorts-lite

# Service Endpoints
PAYMENT_ENDPOINT=http://payment-svc:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms
NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

# Cache Configuration
CACHE_TTL=3600000
SESSION_TIMEOUT=1800
```

### AWS Secrets Manager Secret Format

**Database Credentials Secret** (`resorts-lite/db-credentials`):
```json
{
  "host": "your-rds-endpoint.rds.amazonaws.com",
  "port": "5432",
  "username": "admin",
  "password": "your-secure-password",
  "database": "resortdb"
}
```

**Authentication Credentials Secret** (`resorts-lite/auth-credentials`):
```json
{
  "admin": "hashed-password",
  "user1": "hashed-password"
}
```

## Deployment Instructions

### 1. AWS ECS Deployment
```bash
# Set environment variables in ECS task definition
# Configure IAM role with permissions for S3, Secrets Manager, SSM, ElastiCache
# Deploy container to ECS cluster
```

### 2. AWS EKS Deployment
```bash
# Create Kubernetes secrets for sensitive data
# Configure service account with IAM role (IRSA)
# Deploy using Kubernetes manifests
```

### 3. AWS Elastic Beanstalk Deployment
```bash
# Configure environment properties in Elastic Beanstalk console
# Deploy JAR file or Docker container
# Elastic Beanstalk handles load balancing and auto-scaling
```

## IAM Permissions Required

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
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

## Testing Locally

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker (for local Redis)

### Start Local Redis
```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### Run Application
```bash
mvn clean package
java -jar target/resortsLite-1.0.0.jar
```

### Test Endpoints
```bash
# Create booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-03-01&checkOut=2024-03-05"

# Check booking status
curl "http://localhost:8080/api/bookings/status/BK-12345678"

# Check availability
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

## Security Improvements
1. Replaced MD5 hashing with SHA-256
2. Parameterized SQL queries to prevent SQL injection
3. Credentials stored in AWS Secrets Manager with encryption
4. Updated vulnerable dependencies (Log4j, commons-collections)

## Monitoring and Logging
- Application logs are output to stdout for CloudWatch Logs integration
- Structured logging format for better parsing
- Health check endpoints for load balancer integration

## Next Steps
1. Configure AWS resources (S3 bucket, ElastiCache cluster, Secrets Manager secrets)
2. Set up IAM roles and policies
3. Deploy to AWS environment
4. Configure CloudWatch alarms and dashboards
5. Set up AWS X-Ray for distributed tracing (optional)

## Support
For issues or questions, contact the cloud migration team.
