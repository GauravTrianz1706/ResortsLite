# ResortsLite - Cloud-Ready Application for GCP

## Overview
This application has been modernized for cloud deployment on Google Cloud Platform (GCP). All cloud readiness blockers have been resolved to ensure seamless deployment and operation in cloud environments.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (cr-java-0061, cr-java-0062, cr-java-0063)
**Problem**: Hard-coded file paths and local file system operations
**Solution**: 
- Replaced all local file operations with Google Cloud Storage (GCS)
- Reports and backups now stored in GCS buckets
- Configurable bucket names via environment variables

### 2. Hard-coded Database Credentials (cr-java-0069)
**Problem**: Database credentials embedded in source code
**Solution**:
- Externalized all database configuration to environment variables
- Integrated with GCP Secret Manager for secure credential storage
- Supports Cloud SQL connection with socket factory

### 3. Hard-coded Environment URLs (cr-java-0071)
**Problem**: Fixed URLs for external services
**Solution**:
- All service endpoints externalized to environment variables
- Supports dynamic service discovery in GCP environments

### 4. Hard-coded Ports (cr-java-0077)
**Problem**: Fixed port numbers preventing dynamic assignment
**Solution**:
- Server port now uses PORT environment variable (Cloud Run standard)
- Supports dynamic port binding by container orchestration

### 5. HTTP Session State Storage (cr-java-0065)
**Problem**: In-memory session storage preventing horizontal scaling
**Solution**:
- Migrated to Redis-backed distributed session management
- Uses Google Cloud Memorystore for Redis
- Sessions persist across instance restarts and scale-out

### 6. In-Memory Caching Without TTL (cr-java-0067)
**Problem**: Unbounded in-memory cache causing memory issues
**Solution**:
- Replaced with Redis-backed distributed cache
- Configured with proper TTL (1 hour for bookings)
- Consistent cache state across all instances

### 7. File-based Authentication (cr-java-0090)
**Problem**: Local file-based credential storage
**Solution**:
- Integrated with GCP Secret Manager
- Credentials retrieved at runtime from secure storage
- Supports automatic credential rotation

## Architecture Changes

### New Dependencies Added
- `google-cloud-storage`: For GCS file operations
- `spring-cloud-gcp-starter-secretmanager`: For Secret Manager integration
- `spring-session-data-redis`: For distributed session management
- `spring-boot-starter-data-redis`: For Redis operations
- `spring-boot-starter-cache`: For distributed caching

### New Configuration Classes
- `RedisConfig.java`: Configures Redis for caching and session management
- `GcpConfig.java`: Configures GCP services (Storage, Secret Manager)

### Modified Files
- `ReportService.java`: Now uses GCS instead of local file system
- `BookingService.java`: Credentials externalized to environment variables
- `BookingController.java`: Uses Redis for session and cache management
- `application.properties`: All hard-coded values replaced with environment variables
- `pom.xml`: Added GCP and Redis dependencies

## Deployment Prerequisites

### 1. GCP Resources Required
- **Cloud Storage Buckets**: For reports and backups
- **Memorystore for Redis**: For session and cache management
- **Secret Manager**: For storing sensitive credentials
- **Cloud SQL** (optional): For production database
- **Cloud Run or GKE**: For application hosting

### 2. Environment Variables
See `.env.template` for complete list of required environment variables.

Key variables:
- `GCP_PROJECT_ID`: Your GCP project ID
- `REDIS_HOST`: Memorystore Redis instance IP
- `GCS_REPORTS_BUCKET`: Bucket name for reports
- `GCS_BACKUPS_BUCKET`: Bucket name for backups
- `DB_URL`, `DB_USER`, `DB_PASS`: Database configuration

## Deployment Instructions

### Option 1: Cloud Run Deployment

```bash
# 1. Build the application
mvn clean package

# 2. Build container image
gcloud builds submit --tag gcr.io/PROJECT_ID/resortslite:latest

# 3. Deploy to Cloud Run
gcloud run deploy resortslite \
  --image gcr.io/PROJECT_ID/resortslite:latest \
  --platform managed \
  --region us-central1 \
  --set-env-vars="GCP_PROJECT_ID=PROJECT_ID,REDIS_HOST=REDIS_IP,GCS_REPORTS_BUCKET=reports-bucket,GCS_BACKUPS_BUCKET=backups-bucket" \
  --set-secrets="DB_PASS=db-password:latest,REDIS_PASSWORD=redis-password:latest" \
  --allow-unauthenticated
```

### Option 2: GKE Deployment

```bash
# 1. Create GKE cluster
gcloud container clusters create resortslite-cluster \
  --num-nodes=3 \
  --region=us-central1

# 2. Build and push image
docker build -t gcr.io/PROJECT_ID/resortslite:latest .
docker push gcr.io/PROJECT_ID/resortslite:latest

# 3. Deploy using kubectl
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

### Setting Up GCP Resources

#### Create GCS Buckets
```bash
gsutil mb gs://your-reports-bucket
gsutil mb gs://your-backups-bucket
```

#### Create Memorystore Redis Instance
```bash
gcloud redis instances create resortslite-cache \
  --size=1 \
  --region=us-central1 \
  --redis-version=redis_6_x
```

#### Store Secrets in Secret Manager
```bash
echo -n "your-db-password" | gcloud secrets create db-password --data-file=-
echo -n "your-redis-password" | gcloud secrets create redis-password --data-file=-
```

#### Grant Secret Access
```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:SERVICE_ACCOUNT@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

## Local Development

### Running Locally with Docker Compose
```bash
# Start Redis locally
docker run -d -p 6379:6379 redis:6-alpine

# Set environment variables
export REDIS_HOST=localhost
export REDIS_PORT=6379
export GCS_REPORTS_BUCKET=local-reports
export GCS_BACKUPS_BUCKET=local-backups

# Run application
mvn spring-boot:run
```

### Testing GCS Integration Locally
Use GCS emulator or configure application default credentials:
```bash
gcloud auth application-default login
```

## Monitoring and Observability

### Cloud Logging
Application logs are automatically sent to Cloud Logging when deployed on GCP.

### Cloud Monitoring
Set up monitoring for:
- Redis connection health
- GCS operation latency
- Application response times
- Error rates

### Health Checks
The application exposes standard Spring Boot actuator endpoints:
- `/actuator/health`: Application health status
- `/actuator/info`: Application information

## Security Considerations

1. **Secrets Management**: All sensitive data stored in Secret Manager
2. **IAM Roles**: Service account has minimal required permissions
3. **Network Security**: Use VPC Service Controls for additional security
4. **Encryption**: Data encrypted at rest in GCS and Cloud SQL
5. **Authentication**: Consider adding Cloud IAP for user authentication

## Troubleshooting

### Redis Connection Issues
- Verify Memorystore instance is in same VPC
- Check firewall rules allow Redis port (6379)
- Verify REDIS_HOST environment variable is correct

### GCS Access Issues
- Verify service account has Storage Object Admin role
- Check bucket names are correct
- Ensure buckets exist in the same project

### Secret Manager Issues
- Verify Secret Manager API is enabled
- Check service account has secretAccessor role
- Ensure secrets exist and are in latest version

## Performance Optimization

1. **Redis Connection Pooling**: Configured with Lettuce connection pool
2. **Cache TTL**: Set to 1 hour for booking data
3. **Session Timeout**: Set to 30 minutes
4. **GCS Operations**: Use batch operations for multiple files

## Compliance and Best Practices

✅ 12-Factor App Principles
✅ Stateless application design
✅ Externalized configuration
✅ Cloud-native storage patterns
✅ Distributed session management
✅ Horizontal scalability
✅ Environment parity

## Support and Maintenance

For issues or questions:
1. Check Cloud Logging for error messages
2. Verify all environment variables are set correctly
3. Ensure GCP resources are properly configured
4. Review Secret Manager for credential issues

## License
[Your License Here]
