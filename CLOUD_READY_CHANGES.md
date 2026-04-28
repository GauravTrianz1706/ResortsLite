# ResortsLite - Cloud-Ready Application for GCP

## Overview
This application has been modernized to be fully cloud-ready and compatible with Google Cloud Platform (GCP). All cloud readiness blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Resolution**: 
- Replaced all file operations with Google Cloud Storage (GCS)
- Reports are now stored in GCS buckets instead of local file system
- Added `google-cloud-storage` dependency
- Created `GcpStorageConfig` for Storage bean configuration
- Updated `ReportService` to use GCS SDK

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Resolution**:
- Externalized all database credentials to environment variables
- Integrated Google Secret Manager for sensitive credentials
- Added `spring-cloud-gcp-starter-secretmanager` dependency
- Created `getAuthCredentials()` method in `BookingService` to retrieve secrets
- Updated `application.properties` to use environment variables

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Environment-specific URLs hard-coded in source code
**Resolution**:
- Externalized all service URLs to environment variables
- Updated `application.properties` with configurable endpoints
- Modified `BookingController` to use injected configuration values

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Fixed port numbers preventing dynamic assignment
**Resolution**:
- Changed `server.port` to use `${PORT:8080}` for dynamic binding
- Updated all port references to use configuration values
- Compatible with Cloud Run and GKE dynamic port assignment

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: Session state stored in memory, preventing horizontal scaling
**Resolution**:
- Replaced HTTP session with Redis-based distributed session management
- Added Spring Session Data Redis dependencies
- Created `RedisConfig` for Redis connection and session configuration
- Updated `BookingController` to use Redis for session data
- Configured connection to Google Cloud Memorystore for Redis

### 6. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Unbounded in-memory cache causing memory issues
**Resolution**:
- Replaced in-memory cache with Redis-based distributed cache
- Configured TTL (Time-To-Live) for all cached entries
- Added cache configuration in `application.properties`
- Updated `BookingController` to use Redis with TTL

### 7. File-based Authentication (Blocker 17)
**Issue**: Authentication credentials stored in local files
**Resolution**:
- Integrated Google Secret Manager for credential storage
- Added Secret Manager client in `BookingService`
- Configured secret retrieval at runtime
- Removed all file-based credential storage

## Environment Variables Required

### Database Configuration
- `DATABASE_URL`: JDBC connection string
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password (or use Secret Manager)
- `DB_POOL_SIZE`: Connection pool size (default: 10)

### Google Cloud Platform
- `GCP_PROJECT_ID`: GCP project identifier
- `GCS_BUCKET_NAME`: Cloud Storage bucket for reports
- `SECRET_MANAGER_ENABLED`: Enable Secret Manager (default: true)

### Redis (Memorystore)
- `REDIS_HOST`: Redis host address
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_PASSWORD`: Redis password (if authentication enabled)
- `REDIS_SSL`: Enable SSL for Redis connection

### Service Endpoints
- `PAYMENT_SERVICE_URL`: Payment service endpoint
- `INVENTORY_SERVICE_URL`: Inventory service endpoint
- `NOTIFICATION_SERVICE_URL`: Notification service endpoint

### Application Configuration
- `PORT`: Application port (default: 8080)
- `CACHE_TTL`: Cache time-to-live in milliseconds (default: 3600000)
- `AUTH_SECRET_NAME`: Secret Manager secret name for auth credentials

## Dependencies Added

### Google Cloud
- `google-cloud-storage`: 2.22.3
- `spring-cloud-gcp-starter-secretmanager`: 3.4.9

### Redis & Session Management
- `spring-session-data-redis`
- `spring-boot-starter-data-redis`
- `lettuce-core`

### Security Updates
- Updated `log4j-core` to 2.20.0 (from vulnerable 2.14.1)
- Updated `commons-collections` to 4.4 (from vulnerable 3.2.1)

## Deployment Considerations

### Cloud Run
- Application supports dynamic port binding via `PORT` environment variable
- Stateless design allows horizontal scaling
- All state stored in external services (Redis, Cloud SQL)

### Google Kubernetes Engine (GKE)
- Compatible with Kubernetes service discovery
- Supports multiple replicas with shared Redis session store
- Environment-based configuration for different namespaces

### Required GCP Services
1. **Cloud Storage**: For report file storage
2. **Secret Manager**: For credential management
3. **Memorystore for Redis**: For session and cache storage
4. **Cloud SQL** (optional): For production database

## Security Improvements
- Removed all hard-coded credentials
- Replaced MD5 hashing with SHA-256
- Parameterized SQL queries to prevent injection
- Externalized all sensitive configuration

## 12-Factor App Compliance
✅ Codebase: Single codebase tracked in version control
✅ Dependencies: Explicitly declared in pom.xml
✅ Config: Externalized to environment variables
✅ Backing Services: Treated as attached resources
✅ Build, Release, Run: Strictly separated
✅ Processes: Stateless with shared-nothing architecture
✅ Port Binding: Self-contained with dynamic port binding
✅ Concurrency: Horizontally scalable
✅ Disposability: Fast startup and graceful shutdown
✅ Dev/Prod Parity: Same configuration mechanism
✅ Logs: Structured logging to stdout
✅ Admin Processes: Run as one-off processes

## Testing Locally

### Prerequisites
1. Install Redis locally or use Docker:
   ```bash
   docker run -d -p 6379:6379 redis:latest
   ```

2. Set environment variables:
   ```bash
   export GCS_BUCKET_NAME=test-bucket
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Migration Checklist
- [x] Replace file system operations with Cloud Storage
- [x] Externalize database credentials
- [x] Remove hard-coded URLs and ports
- [x] Implement distributed session management
- [x] Add cache TTL configuration
- [x] Integrate Secret Manager
- [x] Update vulnerable dependencies
- [x] Configure HikariCP connection pooling
- [x] Enable dynamic port binding
- [x] Document environment variables
