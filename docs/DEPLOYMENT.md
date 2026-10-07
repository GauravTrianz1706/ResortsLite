# ResortsLite — Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Analysis](#project-analysis)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build and Push Docker Image](#build-and-push-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration Management](#configuration-management)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)

---

## Overview

**Application**: ResortsLite  
**Framework**: Spring Boot 2.7.18  
**Java Version**: 8 (Amazon Corretto 8 runtime)  
**Build Tool**: Maven  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Application Port**: 8080  
**Health Endpoint**: `/actuator/health`  
**Session Store**: Amazon ElastiCache (Redis) via Spring Session  

ResortsLite is a legacy resort booking REST API modernised for cloud-native deployment on AWS EKS. It uses Spring Session backed by Amazon ElastiCache (Redis) for distributed, container-safe session management.

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Container build and run |
| Docker Compose | 2.x | Local multi-container orchestration |
| Java | 8+ | Local development (optional) |
| Maven | 3.8+ | Local build (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR operations |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |
| Docker | 20.10+ | Image build and push |

### AWS IAM Permissions Required
```
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:GetDownloadUrlForLayer
ecr:BatchGetImage
ecr:PutImage
ecr:InitiateLayerUpload
ecr:UploadLayerPart
ecr:CompleteLayerUpload
ecr:CreateRepository
ecr:DescribeRepositories
eks:DescribeCluster
eks:ListClusters
```

---

## Project Analysis

| Property | Value |
|----------|-------|
| Artifact ID | resortsLite |
| Group ID | com.demo |
| Version | 1.0.0 |
| Spring Boot | 2.7.18 |
| Java | 8 |
| Packaging | JAR |
| Application Port | 8080 |
| Management Port | 8080 (same, `/actuator` base path) |
| Health Endpoint | `/actuator/health` |
| Session Store | Redis (ElastiCache) |
| Database | H2 in-memory |
| Builder Image | `maven:3.8.6-openjdk-8-slim` |
| Runtime Image | `amazoncorretto:8` |

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root (never commit this file):

```bash
# Redis connection (use a local Redis container or ElastiCache endpoint)
REDIS_HOST=redis
REDIS_PORT=6379

# Payment service endpoint
PAYMENT_API_URL=http://payment-service:9090/payments/charge

# Report and backup paths
REPORT_BASE_PATH=/var/reports
BACKUP_PATH=/var/backups/nightly
```

> **Note**: The `docker-compose.yml` contains only the application service. You must provide a Redis instance separately (e.g., run `docker run -d -p 6379:6379 redis:7-alpine` and set `REDIS_HOST=host.docker.internal`).

### 2. Build and Start the Application

```bash
# Build and start
docker compose up --build

# Start in background
docker compose up --build -d

# View logs
docker compose logs -f resortslite

# Stop
docker compose down
```

### 3. Verify the Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create a booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-06-01&checkOut=2024-06-05"

# Check availability
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run from repository root
./scripts/build-push.sh
```

The script will prompt for:
1. Image tag (default: `latest`)
2. Registry type: `1` for AWS ECR, `2` for Docker Hub
3. Registry-specific credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (AWS ECR)

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
IMAGE_TAG=1.0.0

# Authenticate
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Create repository (first time only)
aws ecr create-repository --repository-name resortslite --region $AWS_REGION

# Build
docker build -t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/resortslite:${IMAGE_TAG} .

# Push
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/resortslite:${IMAGE_TAG}
```

---

## AWS EKS Deployment

### Step 1: Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### Step 2: Create or Connect to EKS Cluster

```bash
# Create a new cluster (if needed)
eksctl create cluster \
  --name resortslite-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4

# Or connect to an existing cluster
aws eks update-kubeconfig --region us-east-1 --name <your-cluster-name>
```

### Step 3: Install AWS Load Balancer Controller (for Ingress)

```bash
# Add the EKS chart repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<your-cluster-name> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4: Run the Deployment Script

```bash
# Linux / macOS
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh

# Windows
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region
- EKS Cluster Name
- Full Docker image URI (with tag)
- `REDIS_HOST` — ElastiCache primary endpoint
- `REDIS_PORT` — ElastiCache port (default: 6379)
- `PAYMENT_API_URL` — Payment service endpoint

### Step 5: Verify Deployment

```bash
# Check pods
kubectl get pods -n resortslite

# Check services
kubectl get svc -n resortslite

# Check ingress (wait for ALB provisioning ~2-3 minutes)
kubectl get ingress -n resortslite

# View pod logs
kubectl logs -l app=resortslite -n resortslite --tail=100

# Health check via port-forward (before ingress is ready)
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
curl http://localhost:8080/actuator/health
```

### Step 6: Manual Manifest Deployment (Alternative)

```bash
# Apply in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml   # Update {{IMAGE_URI}} placeholder first
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/resortslite -n resortslite
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `resortslite` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (horizontal scaling)
- **Image**: Placeholder `{{IMAGE_URI}}` — replaced by deploy script
- **Resources**: requests `250m CPU / 512Mi RAM`, limits `500m CPU / 1Gi RAM`
- **Liveness Probe**: `GET /actuator/health` — initial delay 60s (JVM warm-up)
- **Readiness Probe**: `GET /actuator/health` — initial delay 30s
- **Volumes**: `emptyDir` for `/var/reports` and `/var/backups/nightly`
- **Environment Placeholders**: `{{REDIS_HOST}}`, `{{REDIS_PORT}}`, `{{PAYMENT_API_URL}}`

### service.yaml
- **Type**: `ClusterIP` — internal cluster access only
- **Port**: 80 → 8080 (application port)

### ingress.yaml
- **Class**: `alb` (AWS Load Balancer Controller)
- **Scheme**: `internet-facing`
- **Host**: `resortslite.example.com` — update to your actual domain
- **Health Check Path**: `/actuator/health`

---

## Configuration Management

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `REDIS_HOST` | `localhost` | ElastiCache primary endpoint |
| `REDIS_PORT` | `6379` | ElastiCache port |
| `PAYMENT_API_URL` | `http://payment-service:9090/payments/charge` | Payment service URL |
| `REPORT_BASE_PATH` | `/var/reports` | Report file directory |
| `BACKUP_PATH` | `/var/backups/nightly` | Backup directory |
| `JAVA_OPTS` | `-Xms256m -Xmx512m ...` | JVM options |

### Using Kubernetes Secrets for Sensitive Values

```bash
# Create a secret for Redis credentials
kubectl create secret generic resortslite-secrets \
  --from-literal=REDIS_HOST=my-cluster.abc123.ng.0001.use1.cache.amazonaws.com \
  --from-literal=REDIS_PORT=6379 \
  -n resortslite
```

Then reference in `deployment.yaml`:
```yaml
env:
  - name: REDIS_HOST
    valueFrom:
      secretKeyRef:
        name: resortslite-secrets
        key: REDIS_HOST
```

### Amazon ElastiCache Setup

```bash
# Create ElastiCache Redis cluster (AWS CLI)
aws elasticache create-cache-cluster \
  --cache-cluster-id resortslite-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region us-east-1
```

---

## Scaling and Management

### Horizontal Pod Autoscaling

```bash
# Create HPA
kubectl autoscale deployment resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n resortslite

# Check HPA status
kubectl get hpa -n resortslite
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=<new-image-uri> \
  -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout history deployment/resortslite -n resortslite
kubectl rollout undo deployment/resortslite --to-revision=2 -n resortslite
```

### Scale Manually

```bash
kubectl scale deployment resortslite --replicas=4 -n resortslite
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Describe pod for events
kubectl describe pod -l app=resortslite -n resortslite

# Check logs
kubectl logs -l app=resortslite -n resortslite --previous
```

**Common causes**:
- `CrashLoopBackOff`: JVM OOM — increase memory limits or `JAVA_OPTS` heap
- `ImagePullBackOff`: ECR authentication issue — verify IAM role for node group
- `Pending`: Insufficient cluster resources — scale node group

### Redis Connection Failure

```bash
# Check Redis env vars in pod
kubectl exec -it <pod-name> -n resortslite -- env | grep REDIS

# Test Redis connectivity from pod
kubectl exec -it <pod-name> -n resortslite -- sh -c "nc -zv $REDIS_HOST $REDIS_PORT"
```

**Common causes**:
- Wrong `REDIS_HOST` — verify ElastiCache endpoint
- Security group blocking port 6379 — add inbound rule from EKS node security group
- VPC peering not configured between EKS and ElastiCache

### Health Check Failing

```bash
# Port-forward and test manually
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
curl -v http://localhost:8080/actuator/health
```

**Common causes**:
- JVM startup time > `initialDelaySeconds` — increase to 90s for slow starts
- Redis not reachable — Spring Boot health includes Redis by default

### Ingress Not Getting External IP

```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Verify ingress class annotation
kubectl describe ingress resortslite-ingress -n resortslite
```

**Common causes**:
- AWS Load Balancer Controller not installed
- Missing IAM permissions for ALB controller
- Subnets not tagged with `kubernetes.io/role/elb: 1`

### JVM Memory Issues

```bash
# Check memory usage
kubectl top pods -n resortslite

# Adjust JVM heap in deployment env
# JAVA_OPTS: "-Xms256m -Xmx768m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

---

## Security Considerations

1. **Non-root container**: The application runs as `appuser` (non-root) inside the container.
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for `REDIS_HOST`, credentials, and API keys — never hardcode in manifests.
3. **Network policies**: Apply Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning on push to detect vulnerabilities.
5. **IRSA (IAM Roles for Service Accounts)**: Use IRSA for secure, credential-free access to AWS services (ElastiCache, Secrets Manager) from EKS pods.
6. **TLS**: Configure HTTPS on the ALB Ingress using ACM certificates:
   ```yaml
   annotations:
     alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS": 443}]'
     alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
   ```
7. **Log4j**: The project includes `log4j-core:2.14.1` which has CVE-2021-44228 (Log4Shell). **Upgrade to 2.17.2+** before production deployment.
8. **Commons Collections**: `commons-collections:3.2.1` has CVE-2015-6420. **Upgrade to 3.2.2+**.
9. **Read-only filesystem**: Consider adding `readOnlyRootFilesystem: true` with explicit writable volume mounts.

---

## Java-Specific Notes

### JVM Container Awareness
The Dockerfile sets `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` to ensure the JVM respects container memory limits rather than using host memory as the basis for heap sizing. This is critical for Java 8u191+ running in containers.

### Spring Boot Actuator
The `/actuator/health` endpoint is exposed and used for both Kubernetes liveness and readiness probes. The `management.endpoint.health.show-details=always` setting provides detailed health information including Redis connectivity status.

### Spring Session with ElastiCache
Sessions are stored in Amazon ElastiCache (Redis) with a 30-minute TTL. This enables:
- **Zero-downtime deployments**: Sessions survive pod restarts
- **Horizontal scaling**: All replicas share the same session store
- **EKS pod eviction safety**: No session loss on node failure

### Graceful Shutdown
The container uses `exec java ...` as the entrypoint to ensure the JVM receives `SIGTERM` directly, enabling Spring Boot's graceful shutdown (configured via `server.shutdown=graceful` if needed).
