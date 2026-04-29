# ResortsLite - Cloud-Ready Application for GCP

## Cloud Readiness Transformations

This application has been transformed to be fully cloud-ready for deployment on Google Cloud Platform (GCP). All cloud compatibility blockers have been resolved.

## Changes Summary

### 1. File System Dependencies → Google Cloud Storage (GCS)
**Files Modified:** `ReportService.java`

**Changes:**
- Replaced hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`) with GCS bucket operations
- Migrated `java.io.File` operations to Google Cloud Storage SDK
- Reports are now stored in GCS buckets instead of local filesystem
- Added configurable bucket names via environment variables

**Configuration:**
```properties
gcs.bucket.reports=${GCS_REPORTS_BUCKET:resort-reports-bucket}
```

### 2. Hard-coded Credentials → GCP Secret Manager
**Files Modified:** `BookingService.java`, `application.properties`

**Changes:**
- Removed hard-coded database credentials (DB_HOST, DB_USER, DB_PASS)
- Externalized all credentials to environment variables
- Integrated Spring Cloud GCP Secret Manager for secure credential retrieval
- Database passwords can now be stored in Secret Manager: `sm://projects/PROJECT_ID/secrets/db-password`

**Configuration:**
```properties
spring.datasource.username=${DATABASE_USERNAME:sa}
spring.datasource.password=${DATABASE_PASSWORD:}
database.host=${DB_HOST:localhost}
```

### 3. HTTP Session State → Redis (Memorystore)
**Files Modified:** `BookingController.java`, `RedisConfig.java` (new)

**Changes:**
- Replaced HTTP session storage with Redis-backed distributed sessions
- Migrated session state to Google Cloud Memorystore for Redis
- Enables stateless application architecture for horizontal scaling
- Session data persists across container restarts and load balancing

**Configuration:**
```properties
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.session.store-type=redis
```

### 4. In-Memory Cache → Redis with TTL
**Files Modified:** `BookingController.java`

**Changes:**
- Replaced unbounded in-memory HashMap cache with Redis cache
- Added configurable TTL (Time-To-Live) for cache entries
- Prevents memory exhaustion and ensures cache consistency across instances

**Configuration:**
```properties
cache.ttl.seconds=${CACHE_TTL_SECONDS:3600}
```

### 5. Hard-coded URLs → Environment Variables
**Files Modified:** `BookingController.java`, `BookingService.java`, `application.properties`

**Changes:**
- Externalized all service endpoint URLs to environment variables
- Removed hard-coded internal service URLs
- Enables environment-specific configuration without code changes

**Configuration:**
```properties
payment.api.url=${PAYMENT_API_URL:http://payment-service:9090/payments/charge}
inventory.service.url=${INVENTORY_SERVICE_URL:http://inventory-service:8081/rooms/available}
```

### 6. Hard-coded Ports → Dynamic Port Binding
**Files Modified:** `ReportService.java`, `application.properties`

**Changes:**
- Replaced hard-coded port 8080 with environment variable
- Supports dynamic port assignment by Cloud Run, GKE, or App Engine
- Uses `${PORT}` environment variable (GCP standard)

**Configuration:**
```properties
server.port=${PORT:8080}
```

### 7. File-based Authentication → Secret Manager
**Files Modified:** `BookingService.java`

**Changes:**
- Removed file-based credential storage patterns
- Migrated to environment variable and Secret Manager based authentication
- Supports Cloud IAM for service-to-service authentication

## Dependencies Added

### Google Cloud Platform
- `google-cloud-storage` (2.22.3) - For GCS file operations
- `spring-cloud-gcp-starter-secretmanager` (3.4.9) - For Secret Manager integration
- `spring-cloud-gcp-starter` (3.4.9) - Core GCP integration

### Redis/Session Management
- `spring-boot-starter-data-redis` - Redis client support
- `spring-session-data-redis` - Distributed session management

### Security Updates
- Updated `log4j-core` from 2.14.1 to 2.17.1 (fixes CVE-2021-44228 Log4Shell)
- Updated `commons-collections` from 3.2.1 to 4.4 (fixes CVE-2015-6420)

## Environment Variables Required

### Database Configuration
```bash
DATABASE_URL=jdbc:postgresql://CLOUD_SQL_CONNECTION_NAME/DATABASE_NAME
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-db-password  # Or use Secret Manager
```

### Redis Configuration (Memorystore)
```bash
REDIS_HOST=10.x.x.x  # Memorystore IP
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password  # If authentication enabled
```

### GCP Configuration
```bash
GCP_PROJECT_ID=your-project-id
GCS_REPORTS_BUCKET=your-reports-bucket
GCS_BACKUPS_BUCKET=your-backups-bucket
```

### Service Endpoints
```bash
PAYMENT_API_URL=http://payment-service:9090/payments/charge
INVENTORY_SERVICE_URL=http://inventory-service:8081/rooms/available
NOTIFICATION_SERVICE_URL=http://notification-service:7070/send
```

### Server Configuration
```bash
PORT=8080  # Cloud Run/GKE will set this automatically
```

## GCP Services Required

1. **Google Cloud Storage (GCS)**
   - Create buckets for reports and backups
   - Grant service account Storage Object Admin role

2. **Cloud Memorystore for Redis**
   - Create Redis instance
   - Configure VPC connectivity

3. **Secret Manager**
   - Store sensitive credentials (database passwords, API keys)
   - Grant service account Secret Manager Secret Accessor role

4. **Cloud SQL** (if using managed database)
   - Create PostgreSQL or MySQL instance
   - Configure private IP or Cloud SQL Proxy

5. **Cloud IAM**
   - Create service account with appropriate roles
   - Assign roles: Storage Admin, Secret Manager Accessor, Cloud SQL Client

## Deployment Options

### Cloud Run
```bash
gcloud run deploy resortslite \
  --image gcr.io/PROJECT_ID/resortslite:latest \
  --platform managed \
  --region us-central1 \
  --set-env-vars "GCP_PROJECT_ID=PROJECT_ID,GCS_REPORTS_BUCKET=reports-bucket" \
  --vpc-connector=redis-connector
```

### Google Kubernetes Engine (GKE)
```bash
kubectl create configmap app-config \
  --from-literal=GCP_PROJECT_ID=your-project \
  --from-literal=GCS_REPORTS_BUCKET=reports-bucket

kubectl create secret generic app-secrets \
  --from-literal=DATABASE_PASSWORD=your-password \
  --from-literal=REDIS_PASSWORD=your-redis-password
```

### App Engine
```yaml
# app.yaml
runtime: java11
env: standard
env_variables:
  GCP_PROJECT_ID: "your-project-id"
  GCS_REPORTS_BUCKET: "reports-bucket"
```

## 12-Factor App Compliance

This application now follows 12-factor app principles:

1. ✅ **Codebase** - Single codebase tracked in version control
2. ✅ **Dependencies** - Explicitly declared in pom.xml
3. ✅ **Config** - Externalized to environment variables
4. ✅ **Backing Services** - Treats GCS, Redis, databases as attached resources
5. ✅ **Build, Release, Run** - Strict separation of stages
6. ✅ **Processes** - Stateless processes (session in Redis)
7. ✅ **Port Binding** - Self-contained with dynamic port binding
8. ✅ **Concurrency** - Scales horizontally via process model
9. ✅ **Disposability** - Fast startup and graceful shutdown
10. ✅ **Dev/Prod Parity** - Same backing services across environments
11. ✅ **Logs** - Treats logs as event streams
12. ✅ **Admin Processes** - Run as one-off processes

## Testing Locally

### Prerequisites
```bash
# Start Redis locally
docker run -d -p 6379:6379 redis:latest

# Set environment variables
export GCP_PROJECT_ID=your-project
export GCS_REPORTS_BUCKET=test-bucket
export REDIS_HOST=localhost
```

### Run Application
```bash
mvn spring-boot:run
```

## Security Considerations

1. **Never commit credentials** - All secrets externalized
2. **Use Secret Manager** - For production credentials
3. **Enable VPC** - For private connectivity to Memorystore and Cloud SQL
4. **Use IAM roles** - Service account with least privilege
5. **Enable audit logging** - Track access to secrets and storage

## Monitoring and Observability

- Application logs are written to stdout (Cloud Logging compatible)
- Configure Cloud Monitoring for metrics
- Use Cloud Trace for distributed tracing
- Set up Cloud Error Reporting for exception tracking

## Next Steps

1. Create GCS buckets for reports and backups
2. Provision Cloud Memorystore for Redis instance
3. Set up Secret Manager secrets for credentials
4. Configure Cloud SQL database (if needed)
5. Create service account with required IAM roles
6. Build container image and push to Container Registry
7. Deploy to Cloud Run, GKE, or App Engine
8. Configure Cloud Load Balancer (if needed)
9. Set up Cloud Monitoring and alerting
10. Configure Cloud CDN for static assets (if applicable)
