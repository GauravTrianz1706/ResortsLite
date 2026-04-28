# ResortsLite - Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Docker Deployment](#docker-deployment)
5. [GCP GKE Deployment](#gcp-gke-deployment)
6. [Configuration Management](#configuration-management)
7. [Troubleshooting](#troubleshooting)
8. [Security Considerations](#security-considerations)
9. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

ResortsLite is a Spring Boot 2.7.18 application built with Java 8, designed for containerized deployment on Google Kubernetes Engine (GKE). This guide provides comprehensive instructions for building, deploying, and managing the application.

**Technology Stack:**
- Java 8
- Spring Boot 2.7.18
- Maven 3.8.6+
- Docker
- Kubernetes (GKE)
- Redis (for session management and caching)
- Google Cloud Storage (for file storage)

---

## Prerequisites

### Required Tools

#### For Local Development:
- **Java Development Kit (JDK) 8** or higher
- **Maven 3.8.6+** (do not use Maven wrapper)
- **Docker Desktop** (latest version)
- **Docker Compose** (included with Docker Desktop)

#### For GCP GKE Deployment:
- **Google Cloud SDK (gcloud CLI)** - [Install Guide](https://cloud.google.com/sdk/docs/install)
- **kubectl** - Kubernetes command-line tool
- **Docker** - For building and pushing images
- **GCP Account** with appropriate permissions

### GCP Prerequisites

1. **GCP Project**: Create or have access to a GCP project
2. **GKE Cluster**: Create a GKE cluster or have access to an existing one
3. **Artifact Registry**: Set up Google Artifact Registry repository (or use Docker Hub)
4. **IAM Permissions**: Ensure you have the following roles:
   - `roles/container.developer` - For GKE access
   - `roles/artifactregistry.writer` - For pushing images
   - `roles/storage.admin` - For GCS bucket access

### External Services

The application requires the following external services:

1. **Redis** - For distributed session management and caching
   - Host: Configurable via `REDIS_HOST`
   - Port: Default 6379
   - Password: Optional

2. **Google Cloud Storage** - For file storage
   - Bucket: Configurable via `GCS_BUCKET_NAME`
   - Project: Configurable via `GCP_PROJECT_ID`

3. **External Service Endpoints** (Optional):
   - Payment Service: `PAYMENT_SERVICE_URL`
   - Inventory Service: `INVENTORY_SERVICE_URL`
   - Notification Service: `NOTIFICATION_SERVICE_URL`

---

## Local Development Setup

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd ResortsLiteComp
```

### Step 2: Build the Application

```bash
# Using Maven (do NOT use ./mvnw)
mvn clean package -DskipTests
```

The built JAR file will be located at: `target/resortsLite-1.0.0.jar`

### Step 3: Run Locally (Without Docker)

```bash
# Set environment variables
export SERVER_PORT=8080
export DB_URL=jdbc:h2:mem:resortdb
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Run the application
java -jar target/resortsLite-1.0.0.jar
```

Access the application:
- Application: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health
- H2 Console: http://localhost:8080/h2-console

### Step 4: Run with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

---

## Docker Deployment

### Step 1: Build Docker Image

#### Option A: Using Docker CLI

```bash
# Build the image
docker build -t resortslite:latest .

# Run the container
docker run -d \
  -p 8080:8080 \
  -e REDIS_HOST=redis-host \
  -e REDIS_PORT=6379 \
  --name resortslite \
  resortslite:latest
```

#### Option B: Using Build Script

**Linux/macOS:**
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

**Windows:**
```cmd
scripts\build-push.bat
```

The script will:
1. Prompt for registry selection (Google Artifact Registry or Docker Hub)
2. Prompt for registry credentials
3. Build the Docker image
4. Push the image to the selected registry

### Step 2: Verify Docker Image

```bash
# List images
docker images | grep resortslite

# Test the image locally
docker run -p 8080:8080 resortslite:latest

# Check health
curl http://localhost:8080/actuator/health
```

---

## GCP GKE Deployment

### Step 1: Set Up GCP Environment

#### 1.1 Authenticate with GCP

```bash
# Login to GCP
gcloud auth login

# Set your project
gcloud config set project YOUR_PROJECT_ID

# Configure Docker for Artifact Registry
gcloud auth configure-docker us-central1-docker.pkg.dev
```

#### 1.2 Create GKE Cluster (if not exists)

```bash
# Create a GKE cluster
gcloud container clusters create resortslite-cluster \
  --zone us-central1-a \
  --num-nodes 3 \
  --machine-type n1-standard-2 \
  --enable-autoscaling \
  --min-nodes 2 \
  --max-nodes 5

# Get cluster credentials
gcloud container clusters get-credentials resortslite-cluster \
  --zone us-central1-a
```

#### 1.3 Create Artifact Registry Repository

```bash
# Create repository
gcloud artifacts repositories create resortslite-repo \
  --repository-format=docker \
  --location=us-central1 \
  --description="ResortsLite Docker repository"
```

#### 1.4 Create GCS Bucket

```bash
# Create bucket for reports
gsutil mb -p YOUR_PROJECT_ID -l us-central1 gs://resortslite-reports
```

### Step 2: Build and Push Docker Image

#### Using Build Script (Recommended)

**Linux/macOS:**
```bash
./scripts/build-push.sh
```

**Windows:**
```cmd
scripts\build-push.bat
```

Select **Google Artifact Registry** and provide:
- GCP Project ID
- GCP Region (e.g., us-central1)
- Artifact Registry Repository Name

The script will output the full image URI, for example:
```
us-central1-docker.pkg.dev/YOUR_PROJECT_ID/resortslite-repo/resortslite:latest
```

**Save this image URI** - you'll need it for deployment.

### Step 3: Deploy to GKE

#### Using Deployment Script (Recommended)

**Linux/macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will prompt for:
1. **GCP Configuration:**
   - GCP Project ID
   - GCP Zone
   - GKE Cluster Name

2. **Docker Image URI** (from Step 2)

3. **Application Configuration:**
   - Database URL (default: H2 in-memory)
   - Database credentials
   - Redis host, port, and password
   - External service URLs
   - GCS bucket name

The script will:
- Configure kubectl for your GKE cluster
- Update Kubernetes manifests with your configuration
- Apply all manifests (namespace, deployment, service, ingress)
- Wait for deployment to complete
- Display deployment status and access information

#### Manual Deployment

If you prefer manual deployment:

```bash
# 1. Configure kubectl
gcloud container clusters get-credentials resortslite-cluster \
  --zone us-central1-a \
  --project YOUR_PROJECT_ID

# 2. Update deployment.yaml with your image URI
sed -i 's|{{IMAGE_URI}}|us-central1-docker.pkg.dev/YOUR_PROJECT_ID/resortslite-repo/resortslite:latest|g' kubernetes/deployment.yaml

# 3. Update environment variables in deployment.yaml
# Edit kubernetes/deployment.yaml and replace all {{PLACEHOLDER}} values

# 4. Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 5. Wait for deployment
kubectl rollout status deployment/resortslite -n resortslite

# 6. Verify deployment
kubectl get pods,svc,ingress -n resortslite
```

### Step 4: Access the Application

#### Get Ingress IP Address

```bash
kubectl get ingress -n resortslite
```

Wait for the `ADDRESS` field to be populated (may take 5-10 minutes).

#### Access Endpoints

- **Application**: http://INGRESS_IP/
- **Health Check**: http://INGRESS_IP/actuator/health
- **Actuator Info**: http://INGRESS_IP/actuator/info

#### Configure DNS (Optional)

Update your DNS records to point `resortslite.example.com` to the ingress IP address.

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

#### Server Configuration
- `SERVER_PORT` - Application port (default: 8080)

#### Database Configuration
- `DB_URL` - JDBC connection URL
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password

#### Redis Configuration
- `REDIS_HOST` - Redis server host
- `REDIS_PORT` - Redis server port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)

#### External Services
- `PAYMENT_SERVICE_URL` - Payment service endpoint
- `INVENTORY_SERVICE_URL` - Inventory service endpoint
- `NOTIFICATION_SERVICE_URL` - Notification service endpoint

#### Google Cloud Storage
- `GCP_PROJECT_ID` - GCP project ID
- `GCS_BUCKET_NAME` - GCS bucket name for reports
- `GCP_CREDENTIALS_PATH` - Path to GCP credentials JSON (optional)

#### File Storage
- `REPORT_BASE_PATH` - Base path for reports (default: /tmp/reports)
- `BACKUP_PATH` - Path for backups (default: /tmp/backups)

### Kubernetes ConfigMaps and Secrets

For production deployments, use ConfigMaps and Secrets:

#### Create ConfigMap

```bash
kubectl create configmap resortslite-config \
  --from-literal=SERVER_PORT=8080 \
  --from-literal=REDIS_HOST=redis-service \
  --from-literal=REDIS_PORT=6379 \
  -n resortslite
```

#### Create Secret

```bash
kubectl create secret generic resortslite-secrets \
  --from-literal=DB_PASSWORD=your-db-password \
  --from-literal=REDIS_PASSWORD=your-redis-password \
  -n resortslite
```

#### Update Deployment to Use ConfigMap/Secret

Edit `kubernetes/deployment.yaml`:

```yaml
env:
- name: SERVER_PORT
  valueFrom:
    configMapKeyRef:
      name: resortslite-config
      key: SERVER_PORT
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: resortslite-secrets
      key: DB_PASSWORD
```

---

## Troubleshooting

### Common Issues

#### 1. Pod Fails to Start

**Symptoms:**
- Pods in `CrashLoopBackOff` or `Error` state

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n resortslite

# View pod logs
kubectl logs -n resortslite -l app=resortslite --tail=100

# Describe pod for events
kubectl describe pod <pod-name> -n resortslite
```

**Common Causes:**
- Missing or incorrect environment variables
- Redis connection failure
- Database connection issues
- Insufficient memory/CPU resources

**Solutions:**
- Verify all environment variables are set correctly
- Ensure Redis is accessible from the cluster
- Check resource limits in deployment.yaml
- Review application logs for specific errors

#### 2. Service Not Accessible

**Symptoms:**
- Cannot access application via ingress IP
- Connection timeout or refused

**Diagnosis:**
```bash
# Check service
kubectl get svc -n resortslite

# Check ingress
kubectl get ingress -n resortslite

# Check endpoints
kubectl get endpoints -n resortslite
```

**Solutions:**
- Verify ingress has an IP address assigned
- Check firewall rules allow traffic to GKE nodes
- Ensure service selector matches pod labels
- Verify health checks are passing

#### 3. Health Check Failures

**Symptoms:**
- Pods restarting frequently
- Readiness probe failures

**Diagnosis:**
```bash
# Check pod events
kubectl describe pod <pod-name> -n resortslite

# Test health endpoint from within pod
kubectl exec -it <pod-name> -n resortslite -- wget -O- http://localhost:8080/actuator/health
```

**Solutions:**
- Increase `initialDelaySeconds` in probes (JVM startup time)
- Verify actuator endpoints are enabled
- Check application logs for startup errors
- Ensure sufficient memory for JVM

#### 4. Image Pull Errors

**Symptoms:**
- `ImagePullBackOff` or `ErrImagePull` status

**Diagnosis:**
```bash
kubectl describe pod <pod-name> -n resortslite
```

**Solutions:**
- Verify image URI is correct
- Ensure Artifact Registry authentication is configured
- Check image exists in registry: `gcloud artifacts docker images list us-central1-docker.pkg.dev/PROJECT_ID/REPO_NAME`
- Create image pull secret if using private registry

#### 5. Redis Connection Issues

**Symptoms:**
- Application logs show Redis connection errors
- Session management not working

**Solutions:**
- Verify Redis host and port are correct
- Ensure Redis is accessible from GKE cluster
- Check Redis password if authentication is enabled
- Test Redis connectivity: `kubectl run -it --rm redis-test --image=redis:alpine --restart=Never -- redis-cli -h REDIS_HOST ping`

### Debugging Commands

```bash
# View all resources in namespace
kubectl get all -n resortslite

# View pod logs (live)
kubectl logs -f -n resortslite -l app=resortslite

# Execute command in pod
kubectl exec -it <pod-name> -n resortslite -- /bin/sh

# Port forward to local machine
kubectl port-forward -n resortslite svc/resortslite-service 8080:80

# View events
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Check resource usage
kubectl top pods -n resortslite
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as non-root user `appuser`
- **Read-only Filesystem**: Consider adding `readOnlyRootFilesystem: true` to security context
- **Drop Capabilities**: Remove unnecessary Linux capabilities

### 2. Network Security

- **Network Policies**: Implement Kubernetes Network Policies to restrict pod-to-pod communication
- **TLS/SSL**: Enable HTTPS on ingress with SSL certificates
- **Private GKE Cluster**: Use private GKE clusters for production

### 3. Secrets Management

- **Never commit secrets**: Use Kubernetes Secrets or Google Secret Manager
- **Rotate credentials**: Regularly rotate database and Redis passwords
- **Service Accounts**: Use GCP Workload Identity for GCS access

### 4. Image Security

- **Scan images**: Use `gcloud artifacts docker images scan` to scan for vulnerabilities
- **Minimal base images**: Use distroless or alpine images for smaller attack surface
- **Update dependencies**: Regularly update base images and dependencies

### 5. RBAC

```bash
# Create service account with minimal permissions
kubectl create serviceaccount resortslite-sa -n resortslite

# Create role with specific permissions
kubectl create role resortslite-role \
  --verb=get,list,watch \
  --resource=configmaps,secrets \
  -n resortslite

# Bind role to service account
kubectl create rolebinding resortslite-binding \
  --role=resortslite-role \
  --serviceaccount=resortslite:resortslite-sa \
  -n resortslite
```

---

## Technology-Specific Notes

### Spring Boot Configuration

#### Actuator Endpoints

The application exposes the following actuator endpoints:
- `/actuator/health` - Health check (used by Kubernetes probes)
- `/actuator/info` - Application information

To expose additional endpoints, update `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

#### Spring Profiles

The application uses Spring profiles for environment-specific configuration:
- `docker` - Docker/Kubernetes environment (set via `SPRING_PROFILES_ACTIVE`)

Create profile-specific configuration files:
- `application-docker.properties`
- `application-prod.properties`

#### JVM Tuning

The Dockerfile sets JVM options for containerized environments:
```
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

Adjust based on your resource limits:
- For 1Gi memory limit: `-Xmx768m -Xms512m`
- For 2Gi memory limit: `-Xmx1536m -Xms1024m`

### Redis Session Management

The application uses Spring Session with Redis for distributed session management.

**Configuration:**
```properties
spring.session.store-type=redis
spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT}
spring.session.redis.namespace=resortslite:session
```

**Deploy Redis on GKE:**
```bash
# Using Helm
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install redis bitnami/redis \
  --namespace resortslite \
  --set auth.password=your-password
```

### Google Cloud Storage

The application uses Google Cloud Storage for file storage.

**Authentication Options:**

1. **Workload Identity (Recommended for GKE):**
```bash
# Enable Workload Identity on cluster
gcloud container clusters update resortslite-cluster \
  --workload-pool=PROJECT_ID.svc.id.goog

# Create GCP service account
gcloud iam service-accounts create resortslite-gcs-sa

# Grant storage permissions
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:resortslite-gcs-sa@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/storage.objectAdmin"

# Bind Kubernetes SA to GCP SA
gcloud iam service-accounts add-iam-policy-binding \
  resortslite-gcs-sa@PROJECT_ID.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:PROJECT_ID.svc.id.goog[resortslite/resortslite-sa]"

# Annotate Kubernetes service account
kubectl annotate serviceaccount resortslite-sa \
  iam.gke.io/gcp-service-account=resortslite-gcs-sa@PROJECT_ID.iam.gserviceaccount.com \
  -n resortslite
```

2. **Service Account Key (Not recommended for production):**
- Create and download service account key JSON
- Mount as secret in Kubernetes
- Set `GCP_CREDENTIALS_PATH` environment variable

### Scaling

#### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA based on CPU utilization
kubectl autoscale deployment resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n resortslite

# View HPA status
kubectl get hpa -n resortslite
```

#### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment/resortslite --replicas=5 -n resortslite
```

### Monitoring and Logging

#### View Logs

```bash
# View logs from all pods
kubectl logs -n resortslite -l app=resortslite --tail=100 -f

# View logs from specific pod
kubectl logs -n resortslite <pod-name> -f
```

#### Google Cloud Logging

Logs are automatically sent to Google Cloud Logging (formerly Stackdriver).

View logs in GCP Console:
1. Navigate to **Logging** > **Logs Explorer**
2. Filter by resource: `k8s_container` and namespace: `resortslite`

#### Metrics

Enable Prometheus metrics:
```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.metrics.export.prometheus.enabled=true
```

Access metrics: `http://INGRESS_IP/actuator/prometheus`

---

## Rolling Updates and Rollbacks

### Perform Rolling Update

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=us-central1-docker.pkg.dev/PROJECT_ID/REPO/resortslite:v2 \
  -n resortslite

# Watch rollout status
kubectl rollout status deployment/resortslite -n resortslite
```

### Rollback Deployment

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout undo deployment/resortslite --to-revision=2 -n resortslite

# View rollout history
kubectl rollout history deployment/resortslite -n resortslite
```

---

## Cleanup

### Delete Kubernetes Resources

```bash
# Delete all resources in namespace
kubectl delete namespace resortslite

# Or delete individual resources
kubectl delete -f kubernetes/
```

### Delete GKE Cluster

```bash
gcloud container clusters delete resortslite-cluster \
  --zone us-central1-a
```

### Delete Artifact Registry Repository

```bash
gcloud artifacts repositories delete resortslite-repo \
  --location=us-central1
```

### Delete GCS Bucket

```bash
gsutil rm -r gs://resortslite-reports
```

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Google Kubernetes Engine Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Google Artifact Registry Documentation](https://cloud.google.com/artifact-registry/docs)

---

## Support

For issues or questions:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review application logs: `kubectl logs -n resortslite -l app=resortslite`
3. Check Kubernetes events: `kubectl get events -n resortslite`
4. Contact your DevOps team or platform administrator

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Application Version**: ResortsLite 1.0.0
