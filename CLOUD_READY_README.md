# ResortsLite - Cloud-Ready Application for GCP

## Overview
This application has been modernized to be fully cloud-ready and compatible with Google Cloud Platform (GCP). All cloud readiness blockers have been resolved to enable seamless deployment on GCP services like Cloud Run, GKE, and App Engine.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies Eliminated
**Issues Fixed:**
- Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- Local file system write operations
- Java.io.File usage for data storage

**Solution:**
- Migrated to **Google Cloud Storage (GCS)** for all file operations
- Reports are now stored in GCS buckets with configurable bucket names
- All file paths are externalized to environment variables

**Configuration:**
```properties
gcp.storage.bucket-name=${GCS_BUCKET_NAME:resorts-reports-bucket}
gcp.storage.project-id=${GCP_PROJECT_ID:}
```

### 2. Hard-coded Database Credentials Removed
**Issues Fixed:**
- Hard-coded database host, username, and password in source code
- Security vulnerabilities from credentials in version control

**Solution:**
- Integrated **Google Secret Manager** for secure credential storage
- All database credentials externalized to environment variables
- Supports automatic credential rotation without redeployment

**Configuration:**
```properties
spring.datasource.url=${DATABASE_URL:jdbc:h2:mem:resortdb}
spring.datasource.username=${DB_USERNAME:sa}
spring.datasource.password=${DB_PASSWORD:}
spring.cloud.gcp.secretmanager.enabled=true
```

### 3. Hard-coded Environment URLs Externalized
**Issues Fixed:**
- Hard-coded service endpoints (payment, inventory, notification services)
- Prevents portability across environments

**Solution:**
- All service URLs externalized to environment variables
- Supports different endpoints per environment (dev, staging, prod)

**Configuration:**
```properties
app.payment.endpoint=${PAYMENT_SERVICE_URL:http://payment-svc:9090/charge}
app.inventory.endpoint=${INVENTORY_SERVICE_URL:http://inventory-svc:8081/rooms}
app.notification.endpoint=${NOTIFICATION_SERVICE_URL:http://notify-svc:7070/send}
```

### 4. Hard-coded Ports Replaced
**Issues Fixed:**
- Hard-coded port 8080 prevents dynamic port assignment
- Incompatible with Cloud Run and container orchestration

**Solution:**
- Server port now uses environment variable `PORT`
- Supports dynamic port binding by cloud platforms

**Configuration:**
```properties
server.port=${PORT:8080}
```

### 5. HTTP Session State Externalized
**Issues Fixed:**
- In-memory session storage prevents horizontal scaling
- Session data lost on instance termination

**Solution:**
- Migrated to **Google Cloud Memorystore for Redis**
- Distributed session management across all instances
- Session data persists across container restarts

**Configuration:**
```properties
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.session.store-type=redis
```

### 6. In-Memory Caching Replaced
**Issues Fixed:**
- Unbounded in-memory cache causes memory exhaustion
- Cache inconsistency across multiple instances

**Solution:**
- Migrated to **Redis-backed caching** with TTL
- Consistent cache state across all instances
- Automatic cache expiration prevents memory issues

**Configuration:**
```properties
spring.cache.type=redis
spring.cache.redis.time-to-live=${CACHE_TTL:3600000}
```

### 7. File-based Authentication Removed
**Issues Fixed:**
- Local file-based credential storage doesn't scale horizontally
- Security and consistency issues in distributed environments

**Solution:**
- Migrated to **Google Secret Manager** for credential storage
- Supports **Cloud IAM** for service-to-service authentication
- No file dependencies for authentication

### 8. Connection Pooling Implemented
**Solution:**
- **HikariCP** connection pooling configured (included in Spring Boot)
- Optimized for cloud database connections
- Configurable pool sizes via environment variables

**Configuration:**
```properties
spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}
spring.datasource.hikari.minimum-idle=${DB_POOL_MIN_IDLE:2}
```

## Required Environment Variables

### Essential Variables
```bash
# Server Configuration
PORT=8080

# Database Configuration
DATABASE_URL=jdbc:postgresql://your-cloud-sql-instance/resortdb
DB_USERNAME=your-db-user
DB_PASSWORD=your-db-password

# Google Cloud Storage
GCS_BUCKET_NAME=your-reports-bucket
GCP_PROJECT_ID=your-gcp-project-id

# Redis (Memorystore)
REDIS_HOST=your-memorystore-ip
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# External Services
PAYMENT_SERVICE_URL=https://payment-service.example.com/charge
INVENTORY_SERVICE_URL=https://inventory-service.example.com/rooms
NOTIFICATION_SERVICE_URL=https://notification-service.example.com/send
```

## GCP Services Required

1. **Google Cloud Storage (GCS)**
   - Create a bucket for report storage
   - Configure IAM permissions for the service account

2. **Google Secret Manager**
   - Store sensitive credentials (database passwords, API keys)
   - Grant Secret Manager Secret Accessor role to service account

3. **Google Cloud Memorystore for Redis**
   - Create a Redis instance for session and cache storage
   - Configure VPC connectivity

4. **Cloud SQL (Optional)**
   - For production database (PostgreSQL/MySQL)
   - Configure connection pooling

## Deployment Options

### Cloud Run
```bash
gcloud run deploy resortslite \
  --image gcr.io/PROJECT_ID/resortslite:latest \
  --platform managed \
  --region us-central1 \
  --set-env-vars GCS_BUCKET_NAME=your-bucket,GCP_PROJECT_ID=your-project
```

### Google Kubernetes Engine (GKE)
```bash
kubectl create configmap resortslite-config \
  --from-literal=GCS_BUCKET_NAME=your-bucket \
  --from-literal=GCP_PROJECT_ID=your-project

kubectl create secret generic resortslite-secrets \
  --from-literal=DB_PASSWORD=your-password \
  --from-literal=REDIS_PASSWORD=your-redis-password
```

### App Engine
Configure in `app.yaml`:
```yaml
env_variables:
  GCS_BUCKET_NAME: "your-bucket"
  GCP_PROJECT_ID: "your-project"
  REDIS_HOST: "your-memorystore-ip"
```

## Security Improvements

1. **No hard-coded credentials** - All secrets in Secret Manager
2. **Parameterized SQL queries** - Prevents SQL injection
3. **Secure hashing** - Replaced MD5 with SHA-256
4. **Updated dependencies** - Fixed Log4Shell and other CVEs
5. **IAM-based authentication** - Service-to-service security

## 12-Factor App Compliance

✅ **I. Codebase** - Single codebase tracked in version control  
✅ **II. Dependencies** - Explicitly declared in pom.xml  
✅ **III. Config** - Externalized to environment variables  
✅ **IV. Backing Services** - Attached resources (GCS, Redis, Cloud SQL)  
✅ **V. Build, Release, Run** - Strict separation of stages  
✅ **VI. Processes** - Stateless processes with Redis for state  
✅ **VII. Port Binding** - Self-contained with dynamic port binding  
✅ **VIII. Concurrency** - Horizontal scaling enabled  
✅ **IX. Disposability** - Fast startup and graceful shutdown  
✅ **X. Dev/Prod Parity** - Same backing services across environments  
✅ **XI. Logs** - Treat logs as event streams  
✅ **XII. Admin Processes** - Run as one-off processes  

## Testing Locally

1. **Start Redis locally:**
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```

2. **Set environment variables:**
   ```bash
   export GCS_BUCKET_NAME=test-bucket
   export GCP_PROJECT_ID=test-project
   export REDIS_HOST=localhost
   ```

3. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

## Migration Checklist

- [x] Replace file system operations with GCS
- [x] Externalize all credentials to Secret Manager
- [x] Remove hard-coded URLs and ports
- [x] Implement distributed session management
- [x] Add Redis-backed caching with TTL
- [x] Configure HikariCP connection pooling
- [x] Update to secure dependencies
- [x] Remove file-based authentication
- [x] Implement parameterized SQL queries
- [x] Add comprehensive configuration documentation

## Support

For issues or questions about the cloud-ready implementation, refer to:
- Google Cloud Storage: https://cloud.google.com/storage/docs
- Secret Manager: https://cloud.google.com/secret-manager/docs
- Memorystore for Redis: https://cloud.google.com/memorystore/docs/redis
- Spring Cloud GCP: https://spring.io/projects/spring-cloud-gcp
