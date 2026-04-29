# ResortsLite - Cloud-Ready Application

## Overview
This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (cr-java-0061, cr-java-0062, cr-java-0063)
**Problem**: Hard-coded file paths and local file system operations
**Solution**: Migrated to Amazon S3 for all file storage operations
- Replaced `/var/legacy/reports/` with S3 bucket storage
- Replaced `C:\\ResortBackups\\nightly\\` with S3 bucket storage
- All file operations now use AWS SDK for Java v2 S3 client
- Reports are generated in-memory and uploaded directly to S3

### 2. Hard-coded Database Credentials (cr-java-0069)
**Problem**: Database credentials embedded in source code
**Solution**: Migrated to AWS Secrets Manager
- Database host, username, and password loaded from Secrets Manager at startup
- Credentials are cached in memory after initial load
- Fallback to environment variables if Secrets Manager is unavailable
- Supports automatic credential rotation without code changes

### 3. Hard-coded Environment URLs (cr-java-0071)
**Problem**: Environment-specific URLs embedded in code
**Solution**: Externalized to AWS Systems Manager Parameter Store
- Payment API endpoint: `${PAYMENT_ENDPOINT}`
- Inventory service endpoint: `${INVENTORY_ENDPOINT}`
- Notification service endpoint: `${NOTIFICATION_ENDPOINT}`
- All endpoints configurable via environment variables or Parameter Store

### 4. Hard-coded Ports (cr-java-0077)
**Problem**: Fixed port numbers preventing dynamic assignment
**Solution**: Externalized to environment variables and Parameter Store
- Server port: `${SERVER_PORT:8080}` (defaults to 8080)
- Port configuration retrieved from AWS Systems Manager Parameter Store
- Compatible with ECS, EKS, and Elastic Beanstalk dynamic port assignment

### 5. HTTP Session State Storage (cr-java-0065)
**Problem**: In-memory session storage preventing horizontal scaling
**Solution**: Migrated to Amazon ElastiCache for Redis
- Spring Session with Redis backend for distributed session management
- Session data persisted in Redis cluster
- Supports horizontal scaling and instance termination without data loss
- Session timeout: 1800 seconds (30 minutes)

### 6. In-Memory Caching Without TTL (cr-java-0067)
**Problem**: Unbounded in-memory cache causing memory growth
**Solution**: Migrated to Amazon ElastiCache for Redis with TTL
- All cache entries have 30-minute TTL
- Centralized cache management across multiple instances
- Prevents memory leaks and stale data issues

### 7. File-based Authentication (cr-java-0090)
**Problem**: Authentication credentials stored in local files
**Solution**: Migrated to AWS Secrets Manager and Amazon Cognito
- User credentials stored in AWS Secrets Manager
- Authentication method added to BookingService
- Supports centralized user management
- Ready for Amazon Cognito integration

## AWS Services Used

### Amazon S3
- **Purpose**: Durable file storage for reports and backups
- **Configuration**: `aws.s3.bucket.name` in application.properties
- **Usage**: ReportService for report generation and storage

### AWS Secrets Manager
- **Purpose**: Secure credential storage with rotation support
- **Configuration**: 
  - `aws.secrets.db.secret.name` for database credentials
  - `aws.secrets.auth.secret.name` for authentication credentials
- **Usage**: BookingService for database and authentication credentials

### AWS Systems Manager Parameter Store
- **Purpose**: Centralized configuration management
- **Configuration**: `aws.region` in application.properties
- **Usage**: ReportService for port configuration and other parameters

### Amazon ElastiCache for Redis
- **Purpose**: Distributed session management and caching
- **Configuration**: 
  - `spring.redis.host`
  - `spring.redis.port`
  - `spring.redis.password`
- **Usage**: BookingController for session and cache management

## Environment Variables

### Required Environment Variables
```bash
# AWS Configuration
AWS_REGION=us-east-1
S3_BUCKET_NAME=resorts-lite-reports
DB_SECRET_NAME=resorts/db/credentials
AUTH_SECRET_NAME=resorts/auth/credentials

# Database Configuration
DB_URL=jdbc:postgresql://your-rds-instance.region.rds.amazonaws.com:5432/resortdb
DB_USERNAME=admin
DB_PASSWORD=<from-secrets-manager>

# Redis Configuration (ElastiCache)
REDIS_HOST=your-elasticache-cluster.region.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=<optional>

# Service Endpoints
PAYMENT_ENDPOINT=http://payment-svc:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms
NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

# Server Configuration
SERVER_PORT=8080
```

### Optional Environment Variables
```bash
# Database Connection Pool
DB_POOL_SIZE=10
DB_POOL_MIN_IDLE=5
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000

# Redis Connection Pool
REDIS_POOL_MAX_ACTIVE=8
REDIS_POOL_MAX_IDLE=8
REDIS_POOL_MIN_IDLE=0
REDIS_TIMEOUT=2000

# Session Configuration
SESSION_TIMEOUT=1800
```

## AWS Secrets Manager Secret Format

### Database Credentials Secret
```json
{
  "host": "your-rds-instance.region.rds.amazonaws.com",
  "username": "admin",
  "password": "your-secure-password",
  "port": "5432",
  "database": "resortdb"
}
```

### Authentication Credentials Secret
```json
{
  "user1": "hashed-password-1",
  "user2": "hashed-password-2",
  "apiKey": "your-api-key"
}
```

## Deployment Options

### AWS Elastic Container Service (ECS)
- Application is stateless and can scale horizontally
- Session state stored in ElastiCache
- File storage in S3
- Secrets injected via ECS task definition

### AWS Elastic Kubernetes Service (EKS)
- Deploy as Kubernetes deployment with multiple replicas
- Use ConfigMaps for non-sensitive configuration
- Use Secrets for sensitive data
- Redis for session management

### AWS Elastic Beanstalk
- Deploy as Java application
- Configure environment variables in Beanstalk console
- Automatic scaling supported
- Load balancer distributes traffic

## Security Improvements

1. **No Hard-coded Credentials**: All credentials externalized to Secrets Manager
2. **SQL Injection Prevention**: Parameterized queries used throughout
3. **Secure Hashing**: SHA-256 replaces MD5 for password hashing
4. **Encrypted Secrets**: All secrets encrypted at rest in Secrets Manager
5. **IAM Role-based Access**: Application uses IAM roles for AWS service access

## Monitoring and Logging

### CloudWatch Integration
- Application logs can be sent to CloudWatch Logs
- Metrics available for Redis cache hit/miss rates
- S3 access logs for audit trail

### Recommended CloudWatch Alarms
- Redis connection failures
- S3 upload failures
- Secrets Manager access errors
- High memory usage
- High CPU usage

## Testing

### Local Development
For local development without AWS services:
1. Use H2 in-memory database
2. Use local Redis instance (Docker)
3. Set fallback environment variables
4. Mock AWS SDK clients if needed

### Integration Testing
1. Use LocalStack for AWS service mocking
2. Test Redis session management
3. Test S3 file operations
4. Test Secrets Manager credential loading

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Externalize configuration to Parameter Store
- [x] Replace in-memory sessions with Redis
- [x] Add TTL to cache entries
- [x] Remove hard-coded ports
- [x] Remove hard-coded URLs
- [x] Add HikariCP connection pooling
- [x] Update Maven dependencies
- [x] Create AWS configuration beans
- [x] Update application.properties

## Next Steps

1. **Create AWS Resources**:
   - S3 bucket for reports
   - ElastiCache Redis cluster
   - Secrets Manager secrets
   - Parameter Store parameters
   - RDS database instance (if needed)

2. **Configure IAM Roles**:
   - S3 read/write permissions
   - Secrets Manager read permissions
   - Parameter Store read permissions
   - CloudWatch Logs write permissions

3. **Deploy Application**:
   - Build Docker image (separate workflow)
   - Deploy to ECS/EKS/Elastic Beanstalk
   - Configure load balancer
   - Set up auto-scaling

4. **Monitor and Optimize**:
   - Set up CloudWatch dashboards
   - Configure alarms
   - Monitor costs
   - Optimize cache TTL values

## Support

For issues or questions about the cloud-ready implementation, refer to:
- AWS SDK for Java v2 documentation
- Spring Session documentation
- Spring Data Redis documentation
- AWS Secrets Manager documentation
- AWS Systems Manager documentation
