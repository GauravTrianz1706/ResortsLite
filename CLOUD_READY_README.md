# ResortsLite - Cloud-Ready Application for GCP

## Overview
This application has been transformed to be fully cloud-ready and compatible with Google Cloud Platform (GCP). All cloud readiness blockers have been addressed.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Google Cloud Storage
**Blockers Fixed:** cr-java-0061, cr-java-0062, cr-java-0063

- **Before:** Hard-coded file paths (`/var/legacy/reports/`, `C:\\ResortBackups\\nightly\\`)
- **After:** Google Cloud Storage integration using GCS Java SDK
- **Implementation:**
  - Reports are now stored in GCS buckets
  - File operations use in-memory buffers and upload to GCS
  - Configurable bucket names via environment variables

### 2. Hard-coded Database Credentials → Secret Manager
**Blockers Fixed:** cr-java-0069

- **Before:** Hard-coded credentials in source code (`DB_USER`, `DB_PASS`)
- **After:** Google Secret Manager integration
- **Implementation:**
  - Credentials retrieved from Secret Manager at runtime
  - Fallback to environment variables for local development
  - No credentials stored in code or version control

### 3. Hard-coded URLs → Environment Variables
**Blockers Fixed:** cr-java-0071

- **Before:** Hard-coded service URLs (`http://10.0.1.45:9090/payments/charge`)
- **After:** Externalized configuration via environment variables
- **Implementation:**
  - All service endpoints configurable via environment variables
  - Default values provided for local development
  - Supports dynamic service discovery in cloud environments

### 4. Hard-coded Ports → Dynamic Port Binding
**Blockers Fixed:** cr-java-0077

- **Before:** Fixed port 8080 hard-coded in source
- **After:** Dynamic port assignment via `${PORT}` environment variable
- **Implementation:**
  - Server port reads from `PORT` environment variable
  - Defaults to 8080 for local development
  - Compatible with Cloud Run, GKE, and other GCP services

### 5. HTTP Session State → Redis (Memorystore)
**Blockers Fixed:** cr-java-0065 (5 instances)

- **Before:** HTTP session storage preventing horizontal scaling
- **After:** Google Cloud Memorystore for Redis
- **Implementation:**
  - Spring Session Data Redis integration
  - Session data stored in distributed Redis cache
  - Stateless application architecture enabling horizontal scaling
  - Custom session ID via `X-Session-Id` header

### 6. In-Memory Cache → Redis with TTL
**Blockers Fixed:** cr-java-0067

- **Before:** Unbounded in-memory cache causing memory leaks
- **After:** Redis-based caching with TTL
- **Implementation:**
  - Configurable TTL (default 30 minutes)
  - Distributed cache shared across instances
  - Prevents memory exhaustion

### 7. File-based Authentication → Secret Manager + Cloud IAM
**Blockers Fixed:** cr-java-0090

- **Before:** Local file-based credential storage
- **After:** Google Secret Manager integration
- **Implementation:**
  - Credentials stored in Secret Manager
  - Service-to-service authentication via Cloud IAM
  - No file dependencies for authentication

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | 8080 |
| `GCP_PROJECT_ID` | GCP Project ID | default-project |
| `GCS_REPORTS_BUCKET` | GCS bucket for reports | resort-reports-bucket |
| `DATABASE_URL` | Database connection URL | jdbc:h2:mem:resortdb |
| `DATABASE_USERNAME` | Database username | sa |
| `DATABASE_PASSWORD` | Database password | (empty) |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `DB_HOST_SECRET` | Secret Manager secret name for DB host | db-host-secret |
| `DB_USER_SECRET` | Secret Manager secret name for DB user | db-user-secret |
| `DB_PASSWORD_SECRET` | Secret Manager secret name for DB password | db-password-secret |
| `PAYMENT_API_URL` | Payment service endpoint | http://payment-service:9090/payments/charge |
| `INVENTORY_API_URL` | Inventory service endpoint | http://inventory-service:8081/rooms/available |
| `NOTIFICATION_API_URL` | Notification service endpoint | http://notification-service:7070/send |

### GCP Services Required

1. **Google Cloud Storage**
   - Create bucket for reports (e.g., `resort-reports-bucket`)
   - Grant Storage Object Admin role to service account

2. **Google Secret Manager**
   - Create secrets for database credentials
   - Grant Secret Manager Secret Accessor role to service account

3. **Google Cloud Memorystore for Redis**
   - Create Redis instance
   - Configure VPC connectivity
   - Note host and port for configuration

4. **Cloud IAM**
   - Create service account with appropriate roles
   - Assign to Cloud Run service or GKE workload

## Dependencies Added

- `google-cloud-storage` - GCS integration
- `google-cloud-secretmanager` - Secret Manager integration
- `spring-cloud-gcp-starter` - Spring Cloud GCP support
- `spring-cloud-gcp-starter-secretmanager` - Secret Manager Spring integration
- `spring-boot-starter-data-redis` - Redis support
- `spring-session-data-redis` - Distributed session management

## Security Improvements

1. **Vulnerable Dependencies Updated:**
   - log4j-core: 2.14.1 → 2.17.1 (fixes Log4Shell CVE-2021-44228)
   - commons-collections: 3.2.1 → commons-collections4 4.4 (fixes CVE-2015-6420)

2. **Secure Hashing:**
   - MD5 → SHA-256 for confirmation codes

3. **SQL Injection Prevention:**
   - Parameterized queries instead of string concatenation

## Deployment

### Local Development
```bash
# Set environment variables
export GCP_PROJECT_ID=your-project-id
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Run application
mvn spring-boot:run
```

### GCP Cloud Run
```bash
gcloud run deploy resortslite \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars GCP_PROJECT_ID=your-project-id,GCS_REPORTS_BUCKET=your-bucket,REDIS_HOST=your-redis-host
```

### GCP GKE
Deploy using Kubernetes manifests with ConfigMaps and Secrets for environment variables.

## Architecture Compliance

This application now follows cloud-native 12-factor app principles:

1. ✅ **Codebase:** Single codebase tracked in version control
2. ✅ **Dependencies:** Explicitly declared in pom.xml
3. ✅ **Config:** Externalized via environment variables
4. ✅ **Backing Services:** Treats databases, caches as attached resources
5. ✅ **Build, Release, Run:** Strict separation of stages
6. ✅ **Processes:** Stateless, share-nothing architecture
7. ✅ **Port Binding:** Self-contained with dynamic port binding
8. ✅ **Concurrency:** Horizontally scalable
9. ✅ **Disposability:** Fast startup and graceful shutdown
10. ✅ **Dev/Prod Parity:** Same backing services across environments
11. ✅ **Logs:** Treats logs as event streams
12. ✅ **Admin Processes:** Run as one-off processes

## Testing

All endpoints remain functional with the same API contracts. Session management now requires `X-Session-Id` header for distributed session tracking.

## Support

For issues or questions, contact the cloud migration team.
