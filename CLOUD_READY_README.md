# ResortsLite - Cloud-Ready Application for GCP

## Overview
This application has been transformed to be fully cloud-ready and compatible with Google Cloud Platform (GCP). All cloud readiness blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Google Cloud Storage (GCS)
**Blockers Fixed:** cr-java-0061, cr-java-0062, cr-java-0063

- **Before:** Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- **After:** Google Cloud Storage integration using GCS Java SDK
- **Implementation:**
  - Reports are now stored in GCS buckets
  - File operations use `google-cloud-storage` library
  - Bucket names are externalized via environment variables
  - Files are uploaded to GCS with proper content types

### 2. Hard-coded Database Credentials → Secret Manager
**Blockers Fixed:** cr-java-0069

- **Before:** Hard-coded credentials in source code (`DB_USER`, `DB_PASS`)
- **After:** Google Secret Manager integration
- **Implementation:**
  - Credentials retrieved from Secret Manager at runtime
  - Secret names configurable via environment variables
  - Supports credential rotation without code changes
  - SQL injection vulnerabilities fixed with parameterized queries

### 3. Hard-coded URLs → Environment Variables
**Blockers Fixed:** cr-java-0071

- **Before:** Hard-coded service URLs (`http://inventory-service.internal:8081`)
- **After:** Externalized configuration via environment variables
- **Implementation:**
  - All service endpoints configurable via `application.properties`
  - Environment variables override default values
  - Supports different URLs per environment (dev/staging/prod)

### 4. Hard-coded Ports → Dynamic Port Binding
**Blockers Fixed:** cr-java-0077

- **Before:** Hard-coded port 8080
- **After:** Dynamic port assignment via `${PORT}` environment variable
- **Implementation:**
  - Server port reads from `PORT` environment variable
  - Falls back to 8080 for local development
  - Compatible with Cloud Run and GKE dynamic port assignment

### 5. HTTP Session State → Redis (Memorystore)
**Blockers Fixed:** cr-java-0065

- **Before:** HTTP session storage (non-scalable, stateful)
- **After:** Redis-backed session management
- **Implementation:**
  - Spring Session Data Redis integration
  - Sessions stored in Google Cloud Memorystore for Redis
  - Stateless application architecture
  - Horizontal scaling enabled
  - Session TTL configured (default: 30 minutes)

### 6. In-Memory Cache → Redis with TTL
**Blockers Fixed:** cr-java-0067

- **Before:** Unbounded in-memory cache (`HashMap`)
- **After:** Redis cache with TTL
- **Implementation:**
  - Cache entries stored in Redis with expiration
  - Configurable TTL via environment variables
  - Prevents memory exhaustion
  - Consistent cache across all instances

### 7. File-based Authentication → Secret Manager + Cloud IAM
**Blockers Fixed:** cr-java-0090

- **Before:** File-based credential storage
- **After:** Google Secret Manager integration
- **Implementation:**
  - Credentials stored in Secret Manager
  - Service-to-service authentication via Cloud IAM
  - No file dependencies for authentication
  - Supports distributed environments

## Dependencies Added

### GCP Libraries
- `google-cloud-storage` (2.22.3) - Cloud Storage integration
- `google-cloud-secretmanager` (2.23.0) - Secret Manager integration
- `spring-cloud-gcp-starter` (3.8.0) - Spring Cloud GCP support
- `spring-cloud-gcp-starter-secretmanager` (3.8.0) - Secret Manager Spring integration

### Redis/Session Management
- `spring-boot-starter-data-redis` - Redis support
- `spring-session-data-redis` - Redis-backed session management

### Security Updates
- Removed vulnerable `log4j-core` 2.14.1 (CVE-2021-44228)
- Replaced `commons-collections` 3.2.1 with `commons-collections4` 4.4

## Environment Variables Required

### GCP Configuration
```bash
GCP_PROJECT_ID=your-gcp-project-id
GCS_REPORTS_BUCKET=resort-reports-bucket
GCS_BACKUPS_BUCKET=resort-backups-bucket
```

### Database Configuration
```bash
DATABASE_URL=jdbc:postgresql://your-db-host:5432/resortdb
DATABASE_USERNAME=your-db-user
DATABASE_PASSWORD=your-db-password
DATABASE_DRIVER=org.postgresql.Driver
```

### Redis Configuration (Memorystore)
```bash
REDIS_HOST=your-memorystore-ip
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
REDIS_SSL=true
```

### Secret Manager Configuration
```bash
SECRET_DB_HOST=db-host-secret
SECRET_DB_USER=db-user-secret
SECRET_DB_PASSWORD=db-password-secret
SECRET_API_KEY=api-key-secret
```

### Service Endpoints
```bash
PAYMENT_ENDPOINT=http://payment-service:9090/charge
INVENTORY_ENDPOINT=http://inventory-service:8081/rooms
NOTIFICATION_ENDPOINT=http://notification-service:7070/send
```

### Server Configuration
```bash
PORT=8080
LOG_LEVEL=INFO
APP_LOG_LEVEL=DEBUG
```

## Deployment to GCP

### Cloud Run
```bash
gcloud run deploy resortslite \
  --image gcr.io/${GCP_PROJECT_ID}/resortslite:latest \
  --platform managed \
  --region us-central1 \
  --set-env-vars GCP_PROJECT_ID=${GCP_PROJECT_ID} \
  --set-env-vars GCS_REPORTS_BUCKET=${GCS_REPORTS_BUCKET} \
  --set-env-vars REDIS_HOST=${REDIS_HOST}
```

### GKE (Google Kubernetes Engine)
```bash
kubectl create secret generic app-secrets \
  --from-literal=database-url=${DATABASE_URL} \
  --from-literal=redis-password=${REDIS_PASSWORD}

kubectl apply -f deployment.yaml
```

## Local Development

### Prerequisites
- Java 8 or higher
- Maven 3.6+
- Docker (for local Redis)

### Run Local Redis
```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### Build and Run
```bash
mvn clean package
java -jar target/resortsLite-1.0.0.jar
```

## Architecture Improvements

### 12-Factor App Compliance
✅ **I. Codebase** - Single codebase tracked in version control
✅ **II. Dependencies** - Explicitly declared dependencies in pom.xml
✅ **III. Config** - Configuration externalized via environment variables
✅ **IV. Backing Services** - GCS, Redis, Secret Manager as attached resources
✅ **V. Build, Release, Run** - Strict separation of stages
✅ **VI. Processes** - Stateless processes (session in Redis)
✅ **VII. Port Binding** - Dynamic port binding via environment variable
✅ **VIII. Concurrency** - Horizontal scaling enabled
✅ **IX. Disposability** - Fast startup and graceful shutdown
✅ **X. Dev/Prod Parity** - Same backing services across environments
✅ **XI. Logs** - Logs to stdout for cloud log aggregation
✅ **XII. Admin Processes** - One-off admin tasks as separate processes

### Cloud-Native Patterns
- **Externalized Configuration** - All config via environment variables
- **Stateless Architecture** - No local state, session in Redis
- **Cloud Storage** - Persistent data in GCS
- **Secret Management** - Credentials in Secret Manager
- **Service Discovery** - Configurable service endpoints
- **Health Checks** - Spring Actuator endpoints for liveness/readiness
- **Horizontal Scaling** - Stateless design enables auto-scaling

## Testing

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Create Booking
```bash
curl -X POST "http://localhost:8080/api/bookings/create" \
  -d "guestName=John Doe" \
  -d "roomType=SUITE" \
  -d "checkIn=2024-05-01" \
  -d "checkOut=2024-05-05" \
  -d "sessionId=test-session-123"
```

### Check Availability
```bash
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

## Security Enhancements
- ✅ Removed vulnerable dependencies (Log4Shell, Commons Collections)
- ✅ SQL injection prevention with parameterized queries
- ✅ Credentials stored in Secret Manager (not in code)
- ✅ Replaced MD5 with SHA-256 for hashing
- ✅ HTTPS support for Redis connections
- ✅ Environment-based configuration (no secrets in code)

## Monitoring and Observability
- Spring Actuator endpoints enabled
- Health checks for Redis connectivity
- Structured logging for cloud log aggregation
- Metrics exposed for Prometheus/Cloud Monitoring

## Next Steps
1. Set up GCP Secret Manager secrets
2. Create GCS buckets for reports and backups
3. Provision Memorystore for Redis instance
4. Configure Cloud SQL or external database
5. Deploy to Cloud Run or GKE
6. Set up Cloud Monitoring and Logging
7. Configure Cloud Load Balancer
8. Enable Cloud CDN for static assets
