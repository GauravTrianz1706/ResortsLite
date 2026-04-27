# Cloud Readiness Fixes - GCP Deployment Guide

## Overview
This application has been transformed to be cloud-ready for Google Cloud Platform (GCP). All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Fix**: Migrated to Google Cloud Storage (GCS)
- Replaced `java.io.File` operations with GCS Java SDK
- All report generation now writes to GCS bucket
- File paths externalized via environment variables

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Fix**: Externalized to environment variables and Secret Manager
- Database URL, username, and password now use environment variables
- Integration with Google Secret Manager for secure credential storage
- Credentials can be rotated without code changes

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Fixed URLs for external services
**Fix**: Externalized via environment variables
- Payment, inventory, and notification service URLs configurable
- Different URLs can be used per environment (dev/staging/prod)

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Fixed port 8080 prevents dynamic port assignment
**Fix**: Dynamic port binding
- Server port now uses `${PORT:8080}` environment variable
- Compatible with Cloud Run and GKE dynamic port assignment

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: Session data stored in memory, preventing horizontal scaling
**Fix**: Migrated to Redis (Memorystore for Redis)
- Spring Session with Redis backend
- Session data distributed across all instances
- Stateless application architecture

### 6. File-based Authentication (Blocker 17)
**Issue**: Credentials stored in local files
**Fix**: Migrated to Secret Manager and Cloud IAM
- Credentials retrieved from Google Secret Manager
- Service-to-service authentication via Cloud IAM
- No file dependencies for authentication

### 7. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Unbounded in-memory cache causing memory issues
**Fix**: Migrated to Redis with TTL
- Spring Cache with Redis backend
- Configurable TTL (default 1 hour)
- Consistent cache across all instances

## GCP Services Required

### 1. Google Cloud Storage (GCS)
- **Purpose**: File storage for reports and documents
- **Configuration**: Set `GCS_BUCKET_NAME` environment variable
- **Setup**: Create a GCS bucket and grant service account access

### 2. Google Secret Manager
- **Purpose**: Secure storage of database credentials and API keys
- **Configuration**: Set `SECRET_MANAGER_ENABLED=true` and `GCP_PROJECT_ID`
- **Setup**: Store secrets in Secret Manager and reference via `${sm://...}` syntax

### 3. Memorystore for Redis
- **Purpose**: Distributed session storage and caching
- **Configuration**: Set `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- **Setup**: Create a Memorystore for Redis instance

### 4. Cloud SQL (Optional)
- **Purpose**: Managed database service
- **Configuration**: Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- **Setup**: Create Cloud SQL instance and configure connection

## Environment Variables

### Required Variables
```bash
# GCP Configuration
GCP_PROJECT_ID=your-project-id
GCS_BUCKET_NAME=your-bucket-name

# Redis Configuration
REDIS_HOST=10.0.0.3
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# Database Configuration
DATABASE_URL=jdbc:postgresql://host:5432/dbname
DATABASE_USERNAME=dbuser
DATABASE_PASSWORD=dbpassword
```

### Optional Variables
```bash
# Port Configuration (Cloud Run sets this automatically)
PORT=8080

# External Service URLs
PAYMENT_SERVICE_URL=https://payment-api.example.com
INVENTORY_SERVICE_URL=https://inventory-api.example.com
NOTIFICATION_SERVICE_URL=https://notification-api.example.com

# Secret Manager
SECRET_MANAGER_ENABLED=true
```

## Deployment Options

### Option 1: Cloud Run
```bash
gcloud run deploy resortslite \
  --image gcr.io/PROJECT_ID/resortslite:latest \
  --platform managed \
  --region us-central1 \
  --set-env-vars GCP_PROJECT_ID=PROJECT_ID,GCS_BUCKET_NAME=BUCKET_NAME \
  --set-env-vars REDIS_HOST=REDIS_IP,REDIS_PORT=6379 \
  --allow-unauthenticated
```

### Option 2: Google Kubernetes Engine (GKE)
```bash
kubectl create configmap resortslite-config \
  --from-literal=GCP_PROJECT_ID=PROJECT_ID \
  --from-literal=GCS_BUCKET_NAME=BUCKET_NAME

kubectl create secret generic resortslite-secrets \
  --from-literal=REDIS_PASSWORD=PASSWORD \
  --from-literal=DATABASE_PASSWORD=PASSWORD

kubectl apply -f deployment.yaml
```

### Option 3: Compute Engine
```bash
# Set environment variables in /etc/environment or systemd service file
export GCP_PROJECT_ID=your-project-id
export GCS_BUCKET_NAME=your-bucket-name
export REDIS_HOST=10.0.0.3

java -jar resortsLite-1.0.0.jar
```

## IAM Permissions Required

The service account running the application needs:
- `roles/storage.objectAdmin` - For GCS operations
- `roles/secretmanager.secretAccessor` - For Secret Manager access
- `roles/cloudsql.client` - For Cloud SQL connection (if using Cloud SQL)

## Testing Cloud Readiness

### 1. Test GCS Integration
```bash
curl -X POST "http://localhost:8080/api/bookings/report/download?month=March"
# Should return GCS path: gs://bucket-name/reports/...
```

### 2. Test Redis Session
```bash
# Create booking with session ID
curl -X POST "http://localhost:8080/api/bookings/create" \
  -H "X-Session-Id: test-session-123" \
  -d "guestName=John&roomType=SUITE&checkIn=2024-03-01&checkOut=2024-03-05"

# Retrieve session data
curl "http://localhost:8080/api/bookings/status/BK-XXX" \
  -H "X-Session-Id: test-session-123"
```

### 3. Test Environment Configuration
```bash
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
# Should return externalized inventory endpoint URL
```

## Migration Checklist

- [ ] Create GCS bucket and configure access
- [ ] Set up Memorystore for Redis instance
- [ ] Store database credentials in Secret Manager
- [ ] Configure environment variables
- [ ] Grant IAM permissions to service account
- [ ] Test application locally with GCP credentials
- [ ] Deploy to Cloud Run/GKE/Compute Engine
- [ ] Verify all endpoints work correctly
- [ ] Monitor logs for any errors

## Security Improvements

1. **No Hard-coded Credentials**: All credentials externalized
2. **Secret Manager Integration**: Secure credential storage and rotation
3. **Parameterized SQL Queries**: Protection against SQL injection
4. **Secure Hashing**: Replaced MD5 with SHA-256
5. **Updated Dependencies**: Fixed Log4Shell and other CVEs

## Scalability Improvements

1. **Stateless Architecture**: No server affinity required
2. **Distributed Sessions**: Redis-backed session storage
3. **Distributed Caching**: Redis-backed cache with TTL
4. **Dynamic Port Binding**: Compatible with container orchestration
5. **Cloud Storage**: Scalable file storage with GCS

## Monitoring and Observability

- Application logs are written to stdout (Cloud Logging compatible)
- Redis operations are logged for debugging
- GCS operations include error handling and logging
- Health check endpoint available at `/actuator/health` (if Spring Actuator enabled)

## Troubleshooting

### Issue: GCS operations fail
- Verify `GCS_BUCKET_NAME` is set correctly
- Check service account has `storage.objectAdmin` role
- Ensure bucket exists and is accessible

### Issue: Redis connection fails
- Verify `REDIS_HOST` and `REDIS_PORT` are correct
- Check network connectivity to Memorystore instance
- Verify `REDIS_PASSWORD` if authentication is enabled

### Issue: Secret Manager access denied
- Verify `GCP_PROJECT_ID` is set correctly
- Check service account has `secretmanager.secretAccessor` role
- Ensure secrets exist in Secret Manager

## Next Steps

1. **Containerization**: Create Dockerfile for container deployment
2. **CI/CD Pipeline**: Set up automated build and deployment
3. **Infrastructure as Code**: Create Terraform/Deployment Manager templates
4. **Monitoring**: Set up Cloud Monitoring dashboards and alerts
5. **Load Testing**: Verify horizontal scaling capabilities
