# ResortsLite - Cloud-Ready Application

## Overview
This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Issues Fixed:**
- Hard-coded file paths (cr-java-0061)
- Local file system write operations (cr-java-0062)
- Java.io.File usage for data storage (cr-java-0063)

**Changes:**
- Replaced all local file operations with Amazon S3 SDK
- Reports are now stored in S3 bucket instead of `/var/legacy/reports/`
- Backup paths migrated from `C:\ResortBackups\` to S3
- Added S3Client configuration in `AwsConfig.java`
- Updated `ReportService.java` to use S3 for report generation and storage

### 2. Hard-coded Credentials → AWS Secrets Manager
**Issues Fixed:**
- Hard-coded database credentials (cr-java-0069)
- File-based authentication (cr-java-0090)

**Changes:**
- Database credentials now retrieved from AWS Secrets Manager
- Authentication credentials externalized to Secrets Manager
- Added SecretsManagerClient configuration
- Updated `BookingService.java` to load credentials from Secrets Manager
- Removed hard-coded DB_HOST, DB_USER, DB_PASS from source code

### 3. Hard-coded URLs → AWS Parameter Store
**Issues Fixed:**
- Hard-coded environment URLs (cr-java-0071)

**Changes:**
- External service endpoints externalized to environment variables
- Configuration can be managed via AWS Systems Manager Parameter Store
- Updated `application.properties` with environment variable placeholders
- Payment, inventory, and notification endpoints now configurable

### 4. Hard-coded Ports → Environment Variables
**Issues Fixed:**
- Hard-coded ports (cr-java-0077)

**Changes:**
- Server port now configurable via `SERVER_PORT` environment variable
- Supports dynamic port assignment by ECS, EKS, and Elastic Beanstalk
- Default port 8080 maintained for local development

### 5. HTTP Session Storage → Amazon ElastiCache for Redis
**Issues Fixed:**
- HTTP session state storage (cr-java-0065)

**Changes:**
- Migrated session management to Spring Session with Redis
- Session data stored in Amazon ElastiCache for Redis
- Application instances are now stateless
- Added `RedisConfig.java` for distributed session configuration
- Updated `BookingController.java` to use Redis for session storage

### 6. In-Memory Caching → Redis with TTL
**Issues Fixed:**
- In-memory caching without TTL (cr-java-0067)

**Changes:**
- Replaced unbounded in-memory cache with Redis
- Configured TTL policies for cache entries
- Prevents memory leaks and ensures data consistency across instances
- Cache configuration in `application.properties`

## AWS Services Integration

### Required AWS Services
1. **Amazon S3** - File storage for reports and backups
2. **AWS Secrets Manager** - Secure credential storage
3. **AWS Systems Manager Parameter Store** - Configuration management
4. **Amazon ElastiCache for Redis** - Distributed session and cache management
5. **Amazon RDS** (optional) - Managed database service

### Environment Variables

#### Required for Production
```bash
# Server Configuration
SERVER_PORT=8080

# Database Configuration
DB_URL=jdbc:postgresql://your-rds-endpoint:5432/resortdb
DB_USERNAME=admin
DB_PASSWORD=<retrieve-from-secrets-manager>

# Redis Configuration (ElastiCache)
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=<retrieve-from-secrets-manager>
REDIS_SSL=true

# AWS Configuration
AWS_REGION=us-east-1
S3_BUCKET_NAME=resorts-lite-reports

# AWS Secrets Manager
DB_SECRETS_NAME=resorts/db/credentials
AUTH_SECRETS_NAME=resorts/auth/credentials

# External Service Endpoints
PAYMENT_ENDPOINT=https://payment-service.example.com/charge
INVENTORY_ENDPOINT=https://inventory-service.example.com/rooms
NOTIFICATION_ENDPOINT=https://notification-service.example.com/send
```

### AWS Secrets Manager Secret Format

#### Database Credentials Secret
```json
{
  "host": "your-rds-endpoint.rds.amazonaws.com",
  "username": "admin",
  "password": "your-secure-password"
}
```

#### Authentication Credentials Secret
```json
{
  "username": "user@example.com",
  "passwordHash": "sha256-hashed-password",
  "role": "ADMIN"
}
```

## Deployment Architecture

### Recommended AWS Architecture
```
Internet → ALB → ECS/EKS Cluster → Application Containers
                      ↓
                  ElastiCache (Redis)
                      ↓
                  RDS (PostgreSQL)
                      ↓
                  S3 Bucket (Reports)
                      ↓
                  Secrets Manager (Credentials)
                      ↓
                  Parameter Store (Configuration)
```

## Dependencies Added

### AWS SDK v2
- `software.amazon.awssdk:s3` - S3 file storage
- `software.amazon.awssdk:secretsmanager` - Credential management
- `software.amazon.awssdk:ssm` - Parameter Store integration

### Spring Session & Redis
- `spring-session-data-redis` - Distributed session management
- `spring-boot-starter-data-redis` - Redis client (Lettuce)

## Configuration Files

### application.properties
- All hard-coded values replaced with environment variables
- Default values provided for local development
- Production values injected via environment or Parameter Store

### pom.xml
- Added AWS SDK dependencies
- Added Spring Session and Redis dependencies
- Removed vulnerable dependencies (log4j 2.14.1, commons-collections 3.2.1)

## Security Improvements

1. **Credential Management**: All credentials externalized to AWS Secrets Manager
2. **SQL Injection Prevention**: Parameterized queries used throughout
3. **Secure Hashing**: Replaced MD5 with SHA-256 for password hashing
4. **Secret Rotation**: Supports automatic credential rotation via Secrets Manager

## Scalability Improvements

1. **Stateless Design**: No server affinity required
2. **Horizontal Scaling**: Application can scale to multiple instances
3. **Distributed Sessions**: Session data shared across all instances
4. **Distributed Caching**: Cache consistency maintained via Redis

## Local Development

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker (for local Redis)

### Running Locally
```bash
# Start local Redis
docker run -d -p 6379:6379 redis:latest

# Run application
mvn spring-boot:run
```

### Local Configuration
The application uses sensible defaults for local development:
- H2 in-memory database
- Local Redis on port 6379
- Server port 8080
- Mock AWS services (falls back to environment variables)

## Testing

### Health Check Endpoint
```bash
curl http://localhost:8080/api/bookings/availability?roomType=STANDARD
```

### Create Booking
```bash
curl -X POST "http://localhost:8080/api/bookings/create" \
  -d "guestName=John Doe" \
  -d "roomType=SUITE" \
  -d "checkIn=2024-06-01" \
  -d "checkOut=2024-06-05" \
  -H "X-Session-Id: test-session-123"
```

## Monitoring and Observability

### CloudWatch Integration
- Application logs sent to CloudWatch Logs
- Metrics available via CloudWatch Metrics
- Alarms can be configured for error rates and latency

### Recommended Metrics
- HTTP request rate and latency
- Redis connection pool metrics
- S3 operation success/failure rates
- Database connection pool metrics

## Compliance

### 12-Factor App Principles
✅ Codebase - Single codebase tracked in version control
✅ Dependencies - Explicitly declared in pom.xml
✅ Config - Externalized to environment variables
✅ Backing Services - Treated as attached resources
✅ Build, Release, Run - Strictly separated
✅ Processes - Stateless and share-nothing
✅ Port Binding - Self-contained with embedded server
✅ Concurrency - Scales horizontally
✅ Disposability - Fast startup and graceful shutdown
✅ Dev/Prod Parity - Consistent environments
✅ Logs - Treated as event streams
✅ Admin Processes - Run as one-off processes

## Migration Checklist

### Pre-Deployment
- [ ] Create S3 bucket for reports
- [ ] Configure AWS Secrets Manager secrets
- [ ] Set up ElastiCache for Redis cluster
- [ ] Configure RDS database instance
- [ ] Set up IAM roles with appropriate permissions
- [ ] Configure VPC and security groups
- [ ] Set up Parameter Store parameters

### Deployment
- [ ] Build application: `mvn clean package`
- [ ] Create container image (separate workflow)
- [ ] Deploy to ECS/EKS (separate workflow)
- [ ] Configure load balancer
- [ ] Set environment variables
- [ ] Verify health checks

### Post-Deployment
- [ ] Test all endpoints
- [ ] Verify S3 file operations
- [ ] Verify Redis session management
- [ ] Verify database connectivity
- [ ] Monitor CloudWatch logs and metrics
- [ ] Test horizontal scaling

## Support

For issues or questions, contact the cloud migration team.
