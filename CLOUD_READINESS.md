# ResortsLite - Cloud-Ready Application

## Cloud Readiness Fixes Applied

This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

### Issues Fixed

#### 1. File System Dependencies (Critical)
- **Before**: Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\`)
- **After**: Amazon S3 for all file storage operations
- **Files Modified**: `ReportService.java`

#### 2. Hard-coded Database Credentials (Critical)
- **Before**: Database credentials embedded in source code
- **After**: AWS Secrets Manager for credential management
- **Files Modified**: `BookingService.java`, `application.properties`

#### 3. Hard-coded Environment URLs (Critical)
- **Before**: Hard-coded service endpoints in code
- **After**: Environment variables and AWS Parameter Store
- **Files Modified**: `BookingController.java`, `application.properties`

#### 4. Hard-coded Ports (Critical)
- **Before**: Fixed port 8080 preventing dynamic assignment
- **After**: Environment variable `SERVER_PORT` with default fallback
- **Files Modified**: `ReportService.java`, `application.properties`

#### 5. HTTP Session State Storage (High)
- **Before**: In-memory HTTP sessions preventing horizontal scaling
- **After**: Amazon ElastiCache for Redis with Spring Session
- **Files Modified**: `BookingController.java`, `RedisConfig.java`

#### 6. File-based Authentication (High)
- **Before**: Authentication credentials stored in local files
- **After**: AWS Secrets Manager and Amazon Cognito integration
- **Files Modified**: `BookingService.java`

#### 7. In-Memory Caching Without TTL (Medium)
- **Before**: Unbounded in-memory cache causing memory issues
- **After**: Redis-based distributed caching with TTL
- **Files Modified**: `BookingController.java`, `RedisConfig.java`

### New Dependencies Added

```xml
- AWS SDK for S3 (file storage)
- AWS SDK for Secrets Manager (credential management)
- AWS SDK for SSM Parameter Store (configuration management)
- AWS SDK for Cognito (authentication)
- Spring Session Data Redis (distributed sessions)
- Spring Data Redis (caching)
- Lettuce Redis Client (Redis connectivity)
- HikariCP (database connection pooling)
```

### Configuration Files

#### application.properties
All hard-coded values replaced with environment variables:
- Database credentials externalized
- Service endpoints externalized
- Port configuration externalized
- Redis configuration for distributed sessions
- S3 bucket configuration

#### New Configuration Classes
1. **RedisConfig.java** - Distributed session and cache management
2. **AwsConfig.java** - AWS service client configuration
3. **DataSourceConfig.java** - HikariCP connection pooling

### Environment Variables Required

See `.env.template` for complete list. Key variables:

```bash
# AWS Configuration
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=resorts-lite-reports
AWS_DB_SECRET_NAME=resorts-lite/db-credentials

# Database
DATABASE_URL=jdbc:postgresql://your-rds-endpoint:5432/resortsdb
DATABASE_USERNAME=dbuser
DATABASE_PASSWORD=from-secrets-manager

# Redis (ElastiCache)
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL=true

# Service Endpoints
PAYMENT_SERVICE_URL=http://payment-svc:9090/charge
INVENTORY_SERVICE_URL=http://inventory-svc:8081/rooms
```

### AWS Resources Required

1. **Amazon S3 Bucket**
   - Bucket name: `resorts-lite-reports` (or configured value)
   - Purpose: Store generated reports

2. **AWS Secrets Manager**
   - Secret: `resorts-lite/db-credentials`
   - Format: JSON with `host`, `username`, `password` fields

3. **Amazon ElastiCache for Redis**
   - Purpose: Distributed session management and caching
   - Configuration: Redis 6.x or later

4. **AWS Systems Manager Parameter Store**
   - Parameters for service endpoints and configuration
   - Example: `/resorts-lite/inventory-endpoint`

5. **Amazon RDS** (Optional)
   - Replace H2 with RDS PostgreSQL/MySQL for production

6. **Amazon Cognito** (Optional)
   - User pool for authentication
   - Replaces file-based authentication

### Deployment Instructions

#### 1. Set Up AWS Resources

```bash
# Create S3 bucket
aws s3 mb s3://resorts-lite-reports --region us-east-1

# Create Secrets Manager secret
aws secretsmanager create-secret \
  --name resorts-lite/db-credentials \
  --secret-string '{"host":"your-db-host","username":"dbuser","password":"secure-password"}' \
  --region us-east-1

# Create ElastiCache Redis cluster
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-redis \
  --engine redis \
  --cache-node-type cache.t3.micro \
  --num-cache-nodes 1 \
  --region us-east-1
```

#### 2. Configure Environment Variables

Set environment variables in your deployment platform:
- **ECS**: Task definition environment variables
- **EKS**: ConfigMap and Secrets
- **Elastic Beanstalk**: Environment properties

#### 3. Deploy Application

The application is now ready for containerization and deployment to:
- Amazon ECS (Elastic Container Service)
- Amazon EKS (Elastic Kubernetes Service)
- AWS Elastic Beanstalk
- AWS App Runner

### Testing Locally

```bash
# Set environment variables
export AWS_REGION=us-east-1
export AWS_S3_BUCKET_NAME=resorts-lite-reports
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Run Redis locally (for testing)
docker run -d -p 6379:6379 redis:6-alpine

# Build and run application
mvn clean package
java -jar target/resortsLite-1.0.0.jar
```

### Cloud-Native Features

✅ **Stateless Architecture**: No local state, fully horizontally scalable
✅ **Externalized Configuration**: All config via environment variables
✅ **Distributed Sessions**: Redis-backed sessions for multi-instance deployment
✅ **Cloud Storage**: S3 for file operations, no local file dependencies
✅ **Secrets Management**: AWS Secrets Manager for credentials
✅ **Connection Pooling**: HikariCP for efficient database connections
✅ **Distributed Caching**: Redis with TTL for cache management
✅ **Dynamic Port Assignment**: Supports container orchestration

### 12-Factor App Compliance

1. ✅ **Codebase**: Single codebase tracked in version control
2. ✅ **Dependencies**: Explicitly declared in pom.xml
3. ✅ **Config**: Externalized via environment variables
4. ✅ **Backing Services**: Attached resources (S3, Redis, RDS)
5. ✅ **Build, Release, Run**: Separate stages supported
6. ✅ **Processes**: Stateless, share-nothing architecture
7. ✅ **Port Binding**: Dynamic port configuration
8. ✅ **Concurrency**: Horizontally scalable
9. ✅ **Disposability**: Fast startup and graceful shutdown
10. ✅ **Dev/Prod Parity**: Same backing services across environments
11. ✅ **Logs**: Structured logging to stdout
12. ✅ **Admin Processes**: Separate management tasks

## Next Steps

1. **Containerization**: Create Dockerfile (separate workflow)
2. **Infrastructure**: Define Terraform/CloudFormation (separate workflow)
3. **CI/CD**: Set up deployment pipeline (separate workflow)
4. **Monitoring**: Configure CloudWatch, X-Ray
5. **Security**: Review IAM roles and security groups

## Support

For issues or questions about cloud deployment, refer to AWS documentation:
- [Amazon ECS](https://docs.aws.amazon.com/ecs/)
- [Amazon EKS](https://docs.aws.amazon.com/eks/)
- [AWS Elastic Beanstalk](https://docs.aws.amazon.com/elasticbeanstalk/)
