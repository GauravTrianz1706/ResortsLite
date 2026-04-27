# ResortsLite - GCP GKE Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Architecture](#project-architecture)
4. [Local Development Setup](#local-development-setup)
5. [Building and Pushing Docker Image](#building-and-pushing-docker-image)
6. [GCP GKE Setup](#gcp-gke-setup)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [Configuration Management](#configuration-management)
9. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
10. [Security Considerations](#security-considerations)
11. [Scaling and Management](#scaling-and-management)

---

## Overview

ResortsLite is a Spring Boot 2.7.18 application built with Java 8, designed for containerized deployment on Google Kubernetes Engine (GKE). This guide provides comprehensive instructions for building, deploying, and managing the application in a production GKE environment.

### Technology Stack
- **Framework**: Spring Boot 2.7.18
- **Java Version**: Java 8 (1.8)
- **Build Tool**: Maven 3.8.6
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes (GKE)
- **Cloud Platform**: Google Cloud Platform (GCP)

### Key Features
- Spring Boot Actuator for health monitoring
- Redis integration for session management and caching
- Google Cloud Storage integration for file storage
- H2 in-memory database (development/testing)
- RESTful API endpoints
- Distributed session management

---

## Prerequisites

### Required Software
1. **Docker Desktop** (v20.10+)
   - Download: https://www.docker.com/products/docker-desktop
   - Verify: `docker --version`

2. **Google Cloud SDK** (gcloud CLI)
   - Download: https://cloud.google.com/sdk/docs/install
   - Verify: `gcloud --version`
   - Initialize: `gcloud init`

3. **kubectl** (Kubernetes CLI)
   - Install via gcloud: `gcloud components install kubectl`
   - Verify: `kubectl version --client`

4. **Maven** (v3.8+) - Optional for local builds
   - Download: https://maven.apache.org/download.cgi
   - Verify: `mvn --version`

### GCP Account Requirements
- Active GCP account with billing enabled
- Project with appropriate permissions:
  - Kubernetes Engine Admin
  - Storage Admin
  - Artifact Registry Administrator
  - Service Account User

### External Services
- **Redis Instance**: Memorystore for Redis or external Redis service
- **Google Cloud Storage**: Bucket for report storage
- **External APIs** (optional):
  - Payment service endpoint
  - Inventory service endpoint
  - Notification service endpoint

---

## Project Architecture

### Application Structure
```
Comp1/
├── src/
│   ├── main/
│   │   ├── java/com/demo/resortslite/
│   │   │   ├── ResortsLiteApplication.java
│   │   │   ├── BookingController.java
│   │   │   ├── BookingService.java
│   │   │   ├── ReportService.java
│   │   │   ├── GcsStorageService.java
│   │   │   └── SessionCacheConfig.java
│   │   └── resources/
│   │       └── application.properties
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
├── pom.xml
├── scripts/
│   ├── build-push.sh
│   ├── build-push.bat
│   ├── deploy-image.sh
│   └── deploy-image.bat
├── kubernetes/
│   ├── namespace.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
└── docs/
    └── DEPLOYMENT.md
```

### Container Architecture
- **Multi-stage Dockerfile**:
  - Stage 1 (Builder): Maven build with dependency caching
  - Stage 2 (Runtime): Lightweight JRE with application JAR
- **Base Images**:
  - Builder: `maven:3.8.6-openjdk-8-slim`
  - Runtime: `eclipse-temurin:8-jdk`
- **Security**: Non-root user execution

### Kubernetes Architecture
- **Namespace**: `resortslite` (isolated environment)
- **Deployment**: 2 replicas for high availability
- **Service**: ClusterIP for internal communication
- **Ingress**: GCE Ingress Controller for external access
- **Health Checks**: Spring Boot Actuator endpoints

---

## Local Development Setup

### 1. Clone and Build Locally

```bash
# Navigate to project directory
cd /path/to/Comp1

# Build with Maven
mvn clean package -DskipTests

# Run locally
java -jar target/resortsLite-1.0.0.jar
```

### 2. Run with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Access the application
# Application: http://localhost:8080
# H2 Console: http://localhost:8080/h2-console
# Health Check: http://localhost:8080/actuator/health

# Stop the application
docker-compose down
```

### 3. Environment Variables for Local Development

Create a `.env` file (not committed to Git):

```env
# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# GCS Configuration
GCS_BUCKET_NAME=resortslite-reports-dev
GCP_PROJECT_ID=your-dev-project

# External Services
PAYMENT_ENDPOINT=http://localhost:9090/charge
INVENTORY_ENDPOINT=http://localhost:8081/rooms
NOTIFICATION_ENDPOINT=http://localhost:7070/send
```

---

## Building and Pushing Docker Image

### Option 1: Using Build Script (Recommended)

#### Linux/macOS:
```bash
cd /path/to/Comp1
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

#### Windows:
```cmd
cd C:\path\to\Comp1
scripts\build-push.bat
```

### Script Workflow:
1. Prompts for image tag (default: `latest`)
2. Select registry type:
   - **Option 1**: Google Artifact Registry
   - **Option 2**: Docker Hub
3. Provide registry credentials
4. Build Docker image
5. Push to selected registry

### Option 2: Manual Build and Push

#### Google Artifact Registry:

```bash
# Set variables
export GCP_PROJECT="your-gcp-project"
export GCP_REGION="us-central1"
export ARTIFACT_REPO="resortslite-repo"
export IMAGE_TAG="v1.0.0"

# Authenticate
gcloud auth login
gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev

# Build
docker build -t ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/resortslite:${IMAGE_TAG} .

# Push
docker push ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/resortslite:${IMAGE_TAG}
```

#### Docker Hub:

```bash
# Set variables
export DOCKER_USERNAME="your-username"
export IMAGE_TAG="v1.0.0"

# Authenticate
docker login

# Build
docker build -t ${DOCKER_USERNAME}/resortslite:${IMAGE_TAG} .

# Push
docker push ${DOCKER_USERNAME}/resortslite:${IMAGE_TAG}
```

---

## GCP GKE Setup

### 1. Create GKE Cluster

```bash
# Set variables
export GCP_PROJECT="your-gcp-project"
export GCP_REGION="us-central1"
export GCP_ZONE="us-central1-a"
export CLUSTER_NAME="resortslite-cluster"

# Set project
gcloud config set project ${GCP_PROJECT}

# Create GKE cluster
gcloud container clusters create ${CLUSTER_NAME} \
  --zone ${GCP_ZONE} \
  --num-nodes 3 \
  --machine-type n1-standard-2 \
  --enable-autoscaling \
  --min-nodes 2 \
  --max-nodes 5 \
  --enable-autorepair \
  --enable-autoupgrade \
  --enable-ip-alias \
  --network "default" \
  --subnetwork "default"

# Get credentials
gcloud container clusters get-credentials ${CLUSTER_NAME} --zone ${GCP_ZONE}

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### 2. Create Artifact Registry Repository

```bash
# Create repository
gcloud artifacts repositories create ${ARTIFACT_REPO} \
  --repository-format=docker \
  --location=${GCP_REGION} \
  --description="ResortsLite Docker images"

# Configure Docker authentication
gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
```

### 3. Set Up External Services

#### Create Memorystore for Redis:

```bash
gcloud redis instances create resortslite-redis \
  --size=1 \
  --region=${GCP_REGION} \
  --redis-version=redis_6_x \
  --tier=basic

# Get Redis host
gcloud redis instances describe resortslite-redis --region=${GCP_REGION} --format="value(host)"
```

#### Create GCS Bucket:

```bash
gsutil mb -p ${GCP_PROJECT} -l ${GCP_REGION} gs://resortslite-reports/
gsutil iam ch serviceAccount:${SERVICE_ACCOUNT}:objectAdmin gs://resortslite-reports/
```

---

## Kubernetes Deployment

### Option 1: Using Deployment Script (Recommended)

#### Linux/macOS:
```bash
cd /path/to/Comp1
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows:
```cmd
cd C:\path\to\Comp1
scripts\deploy-image.bat
```

### Script Workflow:
1. Prompts for GCP project, zone, and cluster name
2. Prompts for Docker image URI
3. Prompts for application configuration:
   - Redis connection details
   - GCS bucket name
   - External service endpoints
4. Configures kubectl for GKE
5. Updates Kubernetes manifests with provided values
6. Applies manifests in order:
   - Namespace
   - Deployment
   - Service
   - Ingress
7. Waits for deployment rollout
8. Displays deployment status

### Option 2: Manual Deployment

#### Step 1: Update Manifests

Edit `kubernetes/deployment.yaml` and replace placeholders:
- `{{IMAGE_URI}}`: Your Docker image URI
- `{{REDIS_HOST}}`: Redis instance host
- `{{REDIS_PORT}}`: Redis port (default: 6379)
- `{{REDIS_PASSWORD}}`: Redis password
- `{{GCS_BUCKET_NAME}}`: GCS bucket name
- `{{GCP_PROJECT_ID}}`: GCP project ID
- `{{PAYMENT_ENDPOINT}}`: Payment service URL
- `{{INVENTORY_ENDPOINT}}`: Inventory service URL
- `{{NOTIFICATION_ENDPOINT}}`: Notification service URL

#### Step 2: Apply Manifests

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Deploy application
kubectl apply -f kubernetes/deployment.yaml

# Create service
kubectl apply -f kubernetes/service.yaml

# Create ingress
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/resortslite -n resortslite

# Verify deployment
kubectl get pods,svc,ingress -n resortslite
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SERVER_PORT` | Application port | 8080 | No |
| `REDIS_HOST` | Redis server host | localhost | Yes |
| `REDIS_PORT` | Redis server port | 6379 | No |
| `REDIS_PASSWORD` | Redis password | (empty) | No |
| `GCS_BUCKET_NAME` | GCS bucket for reports | resortslite-reports | Yes |
| `GCP_PROJECT_ID` | GCP project ID | (empty) | Yes |
| `PAYMENT_ENDPOINT` | Payment service URL | http://payment-svc:9090/charge | No |
| `INVENTORY_ENDPOINT` | Inventory service URL | http://inventory-svc:8081/rooms | No |
| `NOTIFICATION_ENDPOINT` | Notification service URL | http://notify-svc:7070/send | No |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | docker | No |
| `JAVA_OPTS` | JVM options | -Xmx512m -Xms256m | No |

### Using Kubernetes Secrets

For sensitive data, use Kubernetes Secrets:

```bash
# Create secret for Redis password
kubectl create secret generic redis-credentials \
  --from-literal=password='your-redis-password' \
  -n resortslite

# Update deployment.yaml to use secret
# Replace:
#   - name: REDIS_PASSWORD
#     value: "{{REDIS_PASSWORD}}"
# With:
#   - name: REDIS_PASSWORD
#     valueFrom:
#       secretKeyRef:
#         name: redis-credentials
#         key: password
```

### Using ConfigMaps

For non-sensitive configuration:

```bash
# Create ConfigMap
kubectl create configmap app-config \
  --from-literal=gcs.bucket.name='resortslite-reports' \
  --from-literal=payment.endpoint='http://payment-svc:9090/charge' \
  -n resortslite

# Reference in deployment
# - name: GCS_BUCKET_NAME
#   valueFrom:
#     configMapKeyRef:
#       name: app-config
#       key: gcs.bucket.name
```

---

## Monitoring and Troubleshooting

### Health Checks

```bash
# Check application health
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP","groups":["liveness","readiness"]}
```

### View Logs

```bash
# View logs from all pods
kubectl logs -f deployment/resortslite -n resortslite

# View logs from specific pod
kubectl logs -f <pod-name> -n resortslite

# View previous container logs (if crashed)
kubectl logs <pod-name> -n resortslite --previous

# Stream logs with timestamps
kubectl logs -f deployment/resortslite -n resortslite --timestamps
```

### Common Issues and Solutions

#### 1. Pods Not Starting (ImagePullBackOff)

**Symptoms**: Pods stuck in `ImagePullBackOff` state

**Diagnosis**:
```bash
kubectl describe pod <pod-name> -n resortslite
```

**Solutions**:
- Verify image URI is correct
- Check Artifact Registry permissions
- Ensure Docker authentication is configured:
  ```bash
  gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
  ```

#### 2. Pods Crashing (CrashLoopBackOff)

**Symptoms**: Pods repeatedly restarting

**Diagnosis**:
```bash
kubectl logs <pod-name> -n resortslite --previous
kubectl describe pod <pod-name> -n resortslite
```

**Common Causes**:
- Redis connection failure
- Missing environment variables
- Insufficient memory/CPU
- Application startup errors

**Solutions**:
- Verify Redis connectivity
- Check environment variables in deployment
- Increase resource limits
- Review application logs for errors

#### 3. Service Not Accessible

**Symptoms**: Cannot access application via service

**Diagnosis**:
```bash
kubectl get svc -n resortslite
kubectl get endpoints -n resortslite
kubectl describe svc resortslite-service -n resortslite
```

**Solutions**:
- Verify pods are running and ready
- Check service selector matches pod labels
- Test internal connectivity:
  ```bash
  kubectl run -it --rm debug --image=busybox --restart=Never -- wget -O- http://resortslite-service.resortslite.svc.cluster.local/actuator/health
  ```

#### 4. Ingress Not Working

**Symptoms**: External access fails

**Diagnosis**:
```bash
kubectl get ingress -n resortslite
kubectl describe ingress resortslite-ingress -n resortslite
```

**Solutions**:
- Wait for ingress IP assignment (can take 5-10 minutes)
- Verify DNS configuration
- Check GCE Ingress Controller logs
- Ensure backend service is healthy

#### 5. High Memory Usage

**Symptoms**: Pods being OOMKilled

**Diagnosis**:
```bash
kubectl top pods -n resortslite
kubectl describe pod <pod-name> -n resortslite
```

**Solutions**:
- Increase memory limits in deployment
- Adjust JVM heap size:
  ```yaml
  env:
  - name: JAVA_OPTS
    value: "-Xmx768m -Xms384m -XX:MaxRAMPercentage=75.0"
  ```
- Review application for memory leaks

### Debugging Commands

```bash
# Execute shell in running pod
kubectl exec -it <pod-name> -n resortslite -- /bin/sh

# Port forward to local machine
kubectl port-forward <pod-name> 8080:8080 -n resortslite

# Get pod resource usage
kubectl top pod <pod-name> -n resortslite

# Get events
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Describe deployment
kubectl describe deployment resortslite -n resortslite
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as non-root user `appuser`
- **Minimal Base Image**: Uses slim/alpine images to reduce attack surface
- **No Shell Access**: Runtime image doesn't include unnecessary tools
- **Read-only Filesystem**: Consider adding `readOnlyRootFilesystem: true`

### 2. Network Security

```yaml
# Add NetworkPolicy to restrict traffic
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: resortslite-netpol
  namespace: resortslite
spec:
  podSelector:
    matchLabels:
      app: resortslite
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 6379  # Redis
    - protocol: TCP
      port: 443   # HTTPS
```

### 3. Secrets Management

- Use Kubernetes Secrets for sensitive data
- Consider Google Secret Manager integration
- Enable encryption at rest for secrets
- Rotate credentials regularly

### 4. RBAC Configuration

```yaml
# Create service account with minimal permissions
apiVersion: v1
kind: ServiceAccount
metadata:
  name: resortslite-sa
  namespace: resortslite
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: resortslite-role
  namespace: resortslite
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: resortslite-rolebinding
  namespace: resortslite
subjects:
- kind: ServiceAccount
  name: resortslite-sa
roleRef:
  kind: Role
  name: resortslite-role
  apiGroup: rbac.authorization.k8s.io
```

### 5. Image Security

- Scan images for vulnerabilities:
  ```bash
  gcloud container images scan ${IMAGE_URI}
  gcloud container images describe ${IMAGE_URI} --show-package-vulnerability
  ```
- Use signed images
- Implement image pull policies
- Regularly update base images

---

## Scaling and Management

### Horizontal Pod Autoscaling (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: resortslite-hpa
  namespace: resortslite
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resortslite
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

Apply HPA:
```bash
kubectl apply -f hpa.yaml
kubectl get hpa -n resortslite
```

### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment resortslite --replicas=5 -n resortslite

# Verify scaling
kubectl get pods -n resortslite
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite resortslite=new-image:tag -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite

# Check rollout history
kubectl rollout history deployment/resortslite -n resortslite
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout undo deployment/resortslite --to-revision=2 -n resortslite

# Verify rollback
kubectl rollout status deployment/resortslite -n resortslite
```

### Resource Management

Update resource limits:
```yaml
resources:
  requests:
    cpu: "500m"
    memory: "768Mi"
  limits:
    cpu: "1000m"
    memory: "1536Mi"
```

Apply changes:
```bash
kubectl apply -f kubernetes/deployment.yaml
```

---

## Java-Specific Considerations

### JVM Tuning for Containers

The application uses container-aware JVM settings:

```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

**Recommendations**:
- Set `-Xmx` to ~75% of container memory limit
- Use `-XX:+UseContainerSupport` for container awareness
- Enable GC logging for troubleshooting:
  ```bash
  -Xlog:gc*:file=/app/logs/gc.log:time,uptime:filecount=5,filesize=10M
  ```

### Spring Boot Actuator Endpoints

Available endpoints:
- `/actuator/health` - Health check
- `/actuator/info` - Application info
- `/actuator/metrics` - Application metrics

Enable additional endpoints in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

### Performance Optimization

1. **Connection Pooling**: Configure HikariCP for database connections
2. **Caching**: Leverage Redis for distributed caching
3. **Async Processing**: Use `@Async` for non-blocking operations
4. **Compression**: Enable response compression:
   ```properties
   server.compression.enabled=true
   server.compression.mime-types=application/json,application/xml,text/html,text/xml,text/plain
   ```

---

## Maintenance and Operations

### Backup and Disaster Recovery

1. **Application State**: Stored in Redis (configure Redis persistence)
2. **File Storage**: GCS buckets (enable versioning)
3. **Configuration**: Store manifests in Git repository

### Monitoring Setup

Integrate with Google Cloud Monitoring:

```bash
# Enable monitoring
gcloud container clusters update ${CLUSTER_NAME} \
  --enable-cloud-monitoring \
  --zone ${GCP_ZONE}
```

### Log Aggregation

Configure Cloud Logging:

```bash
# Enable logging
gcloud container clusters update ${CLUSTER_NAME} \
  --enable-cloud-logging \
  --zone ${GCP_ZONE}
```

View logs in Cloud Console:
```
https://console.cloud.google.com/logs/query
```

### Cost Optimization

1. **Right-size Resources**: Monitor actual usage and adjust limits
2. **Use Preemptible Nodes**: For non-critical workloads
3. **Enable Cluster Autoscaling**: Scale down during low traffic
4. **Use Committed Use Discounts**: For predictable workloads

---

## Additional Resources

### Documentation
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [GKE Documentation](https://cloud.google.com/kubernetes-engine/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

### Support
- Application Issues: Check application logs and Spring Boot documentation
- Infrastructure Issues: Consult GKE documentation and GCP support
- Security Concerns: Review GCP security best practices

### Useful Links
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/actuator.html)
- [GKE Best Practices](https://cloud.google.com/kubernetes-engine/docs/best-practices)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ResortsLite to GCP GKE. Follow the steps carefully, and refer to the troubleshooting section for common issues. For production deployments, ensure all security considerations are addressed and monitoring is properly configured.

For questions or issues, consult the documentation links provided or contact your DevOps team.
