# ResortsLite - Cloud-Ready Application for AWS

## Overview
This application has been transformed to be fully cloud-ready and compatible with AWS cloud environments. All cloud readiness blockers have been resolved to enable successful deployment on AWS services like ECS, EKS, Elastic Beanstalk, or Lambda.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Amazon S3
**Issues Fixed:**
- Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- Local file system write operations
- Java.io.File usage for data storage

**Solution:**
- Replaced all file operations with Amazon S3 using AWS SDK for Java v2
- Reports are now stored in S3 bucket with configurable bucket name
- S3 keys follow organized structure: `reports/{year}/{month}/{filename}`
- Durable, scalable storage that survives container restarts

**Configuration:**
```properties
aws.s3.bucket.name=${S3_BUCKET_NAME:resorts-lite-reports}
```

### 2. Hard-coded Credentials → AWS Secrets Manager
**Issues Fixed:**
- Hard-coded database credentials (host, username, password)
- File-based authentication

**Solution:**
- Database credentials stored in AWS Secrets Manager
- Authentication credentials stored in AWS Secrets Manager
- Credentials loaded at application startup and cached
- Supports automatic credential rotation
- Replaced MD5 hashing with SHA-256 for security

**Configuration:**
```properties
aws.secrets.db-credentials=${AWS_SECRET_DB_CREDENTIALS:resorts/db/credentials}
aws.secrets.auth-credentials=${AWS_SECRET_AUTH_CREDENTIALS:resorts/auth/credentials}
```

**Secret Format (JSON):**
```json
{
  "host": "db-prod.resorts.com",
  "username": "admin",
  "password": "secure-password",
  "port": "5432"
}
```

### 3. Hard-coded URLs → AWS Systems Manager Parameter Store
**Issues Fixed:**
- Hard-coded environment URLs (`http://inventory-service.internal:8081/rooms/available`)
- Hard-coded service endpoints

**Solution:**
- All environment-specific URLs stored in AWS Systems Manager Parameter Store
- Parameters retrieved at runtime with fallback to environment variables
- Enables environment-agnostic deployments

**Configuration:**
```properties
aws.ssm.payment.endpoint=${AWS_SSM_PAYMENT_ENDPOINT:/resorts/config/payment-endpoint}
aws.ssm.inventory.endpoint=${AWS_SSM_INVENTORY_ENDPOINT:/resorts/config/inventory-endpoint}
aws.ssm.notification.endpoint=${AWS_SSM_NOTIFICATION_ENDPOINT:/resorts/config/notification-endpoint}
```

### 4. Hard-coded Ports → Environment Variables
**Issues Fixed:**
- Hard-coded port 8080 preventing dynamic port assignment

**Solution:**
- Server port configurable via environment variable
- Port configuration also available from Parameter Store
- Compatible with ECS/EKS dynamic port mapping

**Configuration:**
```properties
server.port=${SERVER_PORT:8080}
```

### 5. HTTP Session Storage → Amazon ElastiCache for Redis
**Issues Fixed:**
- HTTP session state storage preventing horizontal scaling
- Server affinity issues
- Session data loss on container restart

**Solution:**
- Migrated to Spring Session with Redis backend
- Session data stored in Amazon ElastiCache for Redis
- Stateless application instances
- Distributed session management across multiple instances
- 30-minute session timeout with automatic expiration

**Configuration:**
```properties
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD:}
spring.session.store-type=redis
```

### 6. In-Memory Caching → Redis with TTL
**Issues Fixed:**
- Unbounded in-memory cache causing memory growth
- Cache inconsistency across multiple instances

**Solution:**
- Replaced in-memory HashMap with Redis-based caching
- Configurable TTL (default 1 hour) prevents indefinite growth
- Centralized cache shared across all application instances
- Automatic expiration and eviction policies

**Configuration:**
```properties
spring.cache.type=redis
spring.cache.redis.time-to-live=${CACHE_TTL:3600000}
```

### 7. Database Connection Pooling
**Solution:**
- HikariCP connection pooling configured (included in spring-boot-starter-jdbc)
- Optimized pool settings for cloud environments
- Prevents connection exhaustion

**Configuration:**
```properties
spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}
spring.datasource.hikari.minimum-idle=${DB_POOL_MIN_IDLE:2}
spring.datasource.hikari.connection-timeout=${DB_CONNECTION_TIMEOUT:30000}
```

## AWS Services Used

### Required Services
1. **Amazon S3** - Object storage for reports and files
2. **AWS Secrets Manager** - Secure credential storage
3. **AWS Systems Manager Parameter Store** - Configuration management
4. **Amazon ElastiCache for Redis** - Distributed session and cache storage

### Optional Services
5. **Amazon Cognito** - User authentication and identity management
6. **Amazon RDS** - Managed database service (when replacing H2)

## Environment Variables

### Required
```bash
# AWS Configuration
AWS_REGION=us-east-1

# S3 Configuration
S3_BUCKET_NAME=resorts-lite-reports

# Redis Configuration (ElastiCache endpoint)
REDIS_HOST=resorts-redis.abc123.ng.0001.use1.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL=true

# Database Configuration
DB_URL=jdbc:postgresql://resorts-db.abc123.us-east-1.rds.amazonaws.com:5432/resorts
DB_USERNAME=admin
DB_PASSWORD=use-secrets-manager

# Server Configuration
SERVER_PORT=8080
```

### Optional
```bash
# AWS Secrets Manager
AWS_SECRET_DB_CREDENTIALS=resorts/db/credentials
AWS_SECRET_AUTH_CREDENTIALS=resorts/auth/credentials

# AWS Parameter Store Paths
AWS_SSM_PAYMENT_ENDPOINT=/resorts/config/payment-endpoint
AWS_SSM_INVENTORY_ENDPOINT=/resorts/config/inventory-endpoint
AWS_SSM_SERVER_PORT=/resorts/config/server-port

# Service Endpoints (fallback if Parameter Store unavailable)
PAYMENT_ENDPOINT=http://payment-svc:9090/charge
INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

# Cache Configuration
CACHE_TTL=3600000

# Cognito Configuration
COGNITO_USER_POOL_ID=us-east-1_ABC123
COGNITO_CLIENT_ID=abc123def456
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
        "arn:aws:secretsmanager:us-east-1:*:secret:resorts/db/credentials-*",
        "arn:aws:secretsmanager:us-east-1:*:secret:resorts/auth/credentials-*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": [
        "arn:aws:ssm:us-east-1:*:parameter/resorts/config/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "cognito-idp:*"
      ],
      "Resource": [
        "arn:aws:cognito-idp:us-east-1:*:userpool/*"
      ]
    }
  ]
}
```

## Deployment Options

### 1. Amazon ECS (Elastic Container Service)
- Use task definition with environment variables
- Configure task role with required IAM permissions
- Use ElastiCache Redis cluster endpoint
- Enable dynamic port mapping

### 2. Amazon EKS (Elastic Kubernetes Service)
- Use ConfigMaps for non-sensitive configuration
- Use Secrets for sensitive data
- Configure service account with IAM role (IRSA)
- Use Redis StatefulSet or ElastiCache

### 3. AWS Elastic Beanstalk
- Configure environment properties in Beanstalk console
- Attach IAM instance profile with required permissions
- Use ElastiCache Redis for session storage

### 4. AWS Lambda (with API Gateway)
- Configure environment variables in Lambda function
- Attach execution role with required permissions
- Use ElastiCache Redis in VPC
- Consider cold start implications

## Setup Instructions

### 1. Create S3 Bucket
```bash
aws s3 mb s3://resorts-lite-reports --region us-east-1
```

### 2. Create Secrets in Secrets Manager
```bash
# Database credentials
aws secretsmanager create-secret \
  --name resorts/db/credentials \
  --secret-string '{"host":"db-host","username":"admin","password":"secure-pass"}' \
  --region us-east-1

# Auth credentials
aws secretsmanager create-secret \
  --name resorts/auth/credentials \
  --secret-string '{"apiKey":"your-api-key"}' \
  --region us-east-1
```

### 3. Create Parameters in Parameter Store
```bash
aws ssm put-parameter \
  --name /resorts/config/payment-endpoint \
  --value "http://payment-svc:9090/charge" \
  --type String \
  --region us-east-1

aws ssm put-parameter \
  --name /resorts/config/inventory-endpoint \
  --value "http://inventory-svc:8081/rooms" \
  --type String \
  --region us-east-1
```

### 4. Create ElastiCache Redis Cluster
```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region us-east-1
```

## Testing Locally

### Using LocalStack (AWS Services Emulator)
```bash
# Start LocalStack
docker run -d -p 4566:4566 localstack/localstack

# Configure AWS CLI for LocalStack
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

### Using Local Redis
```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Configure application
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

## Migration Checklist

- [x] Replace file system operations with S3
- [x] Externalize database credentials to Secrets Manager
- [x] Externalize service URLs to Parameter Store
- [x] Replace hard-coded ports with environment variables
- [x] Migrate HTTP sessions to Redis
- [x] Replace in-memory cache with Redis + TTL
- [x] Configure HikariCP connection pooling
- [x] Replace file-based authentication with Secrets Manager
- [x] Update security hashing from MD5 to SHA-256
- [x] Add AWS SDK dependencies
- [x] Add Spring Session Redis dependencies
- [x] Create AWS configuration beans
- [x] Create Redis configuration

## 12-Factor App Compliance

This application now follows 12-factor app principles:

1. ✅ **Codebase** - Single codebase tracked in version control
2. ✅ **Dependencies** - Explicitly declared in pom.xml
3. ✅ **Config** - Externalized via environment variables and Parameter Store
4. ✅ **Backing Services** - S3, Redis, Secrets Manager treated as attached resources
5. ✅ **Build, Release, Run** - Strict separation maintained
6. ✅ **Processes** - Stateless processes with shared-nothing architecture
7. ✅ **Port Binding** - Self-contained with configurable port
8. ✅ **Concurrency** - Horizontally scalable with Redis session storage
9. ✅ **Disposability** - Fast startup and graceful shutdown
10. ✅ **Dev/Prod Parity** - Same backing services across environments
11. ✅ **Logs** - Logs to stdout (Spring Boot default)
12. ✅ **Admin Processes** - Can run as one-off processes

## Security Improvements

1. **Credentials Management**
   - No hard-coded credentials in source code
   - Secrets stored encrypted in AWS Secrets Manager
   - Automatic credential rotation support

2. **Hashing Algorithm**
   - Replaced MD5 with SHA-256
   - Added salt for additional security
   - Base64 encoding for storage

3. **SQL Injection Prevention**
   - Replaced string concatenation with parameterized queries
   - Using JdbcTemplate with prepared statements

4. **Dependency Updates**
   - Updated log4j from 2.14.1 to 2.17.1 (fixes Log4Shell CVE-2021-44228)
   - Updated commons-collections from 3.2.1 to 4.4 (fixes CVE-2015-6420)

## Monitoring and Observability

### CloudWatch Integration
- Application logs automatically sent to CloudWatch Logs
- Configure log group: `/aws/ecs/resorts-lite` or `/aws/elasticbeanstalk/resorts-lite`

### Metrics
- Spring Boot Actuator endpoints for health checks
- Redis connection metrics
- S3 operation metrics
- Database connection pool metrics

### Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# Redis health
curl http://localhost:8080/actuator/health/redis
```

## Troubleshooting

### Issue: Cannot connect to Redis
**Solution:** Verify security group allows inbound traffic on port 6379 from application security group

### Issue: Access denied to S3 bucket
**Solution:** Verify IAM role has s3:PutObject and s3:GetObject permissions

### Issue: Cannot retrieve secrets
**Solution:** Verify IAM role has secretsmanager:GetSecretValue permission

### Issue: Parameter not found in Parameter Store
**Solution:** Create parameter or verify parameter name matches configuration

## Performance Considerations

1. **Redis Connection Pooling** - Lettuce client provides automatic connection pooling
2. **S3 Multipart Upload** - Consider for large files (>5MB)
3. **Secrets Caching** - Secrets loaded once at startup and cached
4. **Parameter Store Caching** - Consider caching parameters with TTL
5. **Database Connection Pool** - HikariCP configured with optimal settings

## Cost Optimization

1. **S3 Lifecycle Policies** - Archive old reports to S3 Glacier
2. **ElastiCache Node Size** - Start with t3.micro and scale as needed
3. **Secrets Manager** - Consolidate secrets to reduce API calls
4. **Parameter Store** - Use Standard parameters (free tier available)

## Support and Documentation

- AWS SDK for Java v2: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/
- Spring Session: https://docs.spring.io/spring-session/reference/
- Spring Data Redis: https://docs.spring.io/spring-data/redis/docs/current/reference/html/
- AWS Secrets Manager: https://docs.aws.amazon.com/secretsmanager/
- AWS Systems Manager Parameter Store: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
