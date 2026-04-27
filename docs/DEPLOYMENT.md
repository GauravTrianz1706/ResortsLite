# ResortsLite - AWS EKS Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Image](#building-and-pushing-docker-image)
5. [AWS EKS Prerequisites](#aws-eks-prerequisites)
6. [EKS Cluster Setup](#eks-cluster-setup)
7. [Deploying to AWS EKS](#deploying-to-aws-eks)
8. [Configuration Management](#configuration-management)
9. [Monitoring and Health Checks](#monitoring-and-health-checks)
10. [Troubleshooting](#troubleshooting)
11. [Scaling and Management](#scaling-and-management)
12. [Security Considerations](#security-considerations)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on AWS EKS (Elastic Kubernetes Service). This guide provides comprehensive instructions for building, deploying, and managing the application in a production Kubernetes environment.

**Technology Stack:**
- Java 8
- Spring Boot 2.7.18
- Maven 3.x
- Spring Boot Actuator (health checks)
- Spring Session with Redis (distributed sessions)
- AWS SDK (S3, Systems Manager)
- H2 Database (development) / External DB (production)

**Application Features:**
- RESTful API for resort booking management
- Distributed session management with Redis
- File storage with AWS S3
- Health check endpoints via Spring Boot Actuator
- External service integrations (payment, inventory, notifications)

---

## Prerequisites

### Required Tools
- **Docker**: Version 20.10 or higher
- **Docker Compose**: Version 2.0 or higher
- **AWS CLI**: Version 2.x
- **kubectl**: Version 1.24 or higher
- **eksctl**: Version 0.140 or higher (optional, for cluster creation)
- **Java**: JDK 8 or higher (for local development)
- **Maven**: Version 3.6 or higher (for local builds)

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with the following permissions:
  - EKS cluster management
  - ECR repository access
  - EC2 instance management
  - VPC and networking configuration
  - IAM role creation and management

### Installation Instructions

#### Install Docker
```bash
# Linux (Ubuntu/Debian)
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# macOS
brew install docker

# Windows
# Download Docker Desktop from https://www.docker.com/products/docker-desktop
```

#### Install AWS CLI
```bash
# Linux/macOS
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download installer from https://aws.amazon.com/cli/
```

#### Install kubectl
```bash
# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# macOS
brew install kubectl

# Windows
choco install kubernetes-cli
```

#### Install eksctl
```bash
# Linux/macOS
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin

# Windows
choco install eksctl
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd RL
```

### 2. Build the Application Locally
```bash
# Using Maven
mvn clean package -DskipTests

# The JAR file will be created in target/resortsLite-1.0.0.jar
```

### 3. Run with Docker Compose
```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

### 4. Access the Application
- **Application URL**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console
- **Health Check**: http://localhost:8080/actuator/health
- **Application Info**: http://localhost:8080/actuator/info

### 5. Local Configuration
Edit `docker-compose.yml` to configure environment variables for local development:
- Database connection strings
- Redis connection details
- External service endpoints
- AWS credentials and S3 bucket names

---

## Building and Pushing Docker Image

### Option 1: Using build-push.sh (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

The script will prompt you for:
1. **Registry Type**: Choose between AWS ECR or Docker Hub
2. **Image Tag**: Specify a version tag (default: latest)
3. **Registry Details**: Provide AWS region/account ID or Docker Hub credentials

### Option 2: Using build-push.bat (Windows)

```cmd
# Run the script
scripts\build-push.bat
```

### Manual Build and Push

#### AWS ECR
```bash
# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Create repository (if not exists)
aws ecr create-repository --repository-name resortslite --region us-east-1

# Build image
docker build -t resortslite:latest .

# Tag image
docker tag resortslite:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest

# Push image
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

#### Docker Hub
```bash
# Login to Docker Hub
docker login

# Build image
docker build -t resortslite:latest .

# Tag image
docker tag resortslite:latest <username>/resortslite:latest

# Push image
docker push <username>/resortslite:latest
```

---

## AWS EKS Prerequisites

### 1. Configure AWS CLI
```bash
# Configure AWS credentials
aws configure

# Verify configuration
aws sts get-caller-identity
```

### 2. Create IAM Roles

#### EKS Cluster Role
```bash
# Create trust policy
cat > eks-cluster-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "eks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create role
aws iam create-role \
  --role-name ResortsLiteEKSClusterRole \
  --assume-role-policy-document file://eks-cluster-trust-policy.json

# Attach policies
aws iam attach-role-policy \
  --role-name ResortsLiteEKSClusterRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
```

#### EKS Node Group Role
```bash
# Create trust policy
cat > eks-node-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create role
aws iam create-role \
  --role-name ResortsLiteEKSNodeRole \
  --assume-role-policy-document file://eks-node-trust-policy.json

# Attach policies
aws iam attach-role-policy \
  --role-name ResortsLiteEKSNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy

aws iam attach-role-policy \
  --role-name ResortsLiteEKSNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy

aws iam attach-role-policy \
  --role-name ResortsLiteEKSNodeRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

---

## EKS Cluster Setup

### Option 1: Using eksctl (Recommended)

```bash
# Create EKS cluster with managed node group
eksctl create cluster \
  --name resortslite-cluster \
  --region us-east-1 \
  --version 1.27 \
  --nodegroup-name resortslite-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 2 \
  --nodes-max 4 \
  --managed

# This command will:
# - Create VPC and subnets
# - Create EKS cluster
# - Create managed node group
# - Configure kubectl automatically
```

### Option 2: Using AWS Console

1. Navigate to EKS in AWS Console
2. Click "Create cluster"
3. Configure cluster settings:
   - **Name**: resortslite-cluster
   - **Kubernetes version**: 1.27
   - **Cluster service role**: ResortsLiteEKSClusterRole
4. Configure networking:
   - Select VPC and subnets
   - Configure security groups
5. Create cluster (takes 10-15 minutes)
6. Add node group:
   - **Name**: resortslite-nodes
   - **Node IAM role**: ResortsLiteEKSNodeRole
   - **Instance type**: t3.medium
   - **Scaling configuration**: Min 2, Max 4, Desired 2

### Configure kubectl

```bash
# Update kubeconfig
aws eks update-kubeconfig --region us-east-1 --name resortslite-cluster

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### Install AWS Load Balancer Controller

```bash
# Create IAM policy
curl -o iam-policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.5.4/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam-policy.json

# Create service account
eksctl create iamserviceaccount \
  --cluster=resortslite-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::<account-id>:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve

# Install controller using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=resortslite-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

---

## Deploying to AWS EKS

### Option 1: Using deploy-image.sh (Linux/macOS)

```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run deployment script
./scripts/deploy-image.sh
```

The script will prompt you for:
1. AWS Region
2. EKS Cluster Name
3. Docker Image URI
4. Environment variables (database, Redis, S3, external services)

### Option 2: Using deploy-image.bat (Windows)

```cmd
# Run deployment script
scripts\deploy-image.bat
```

### Manual Deployment

#### 1. Update Kubernetes Manifests

Edit `kubernetes/deployment.yaml` and replace placeholders:
```yaml
image: <account-id>.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

Update environment variables with actual values:
```yaml
env:
- name: DB_URL
  value: "jdbc:postgresql://db.example.com:5432/resortdb"
- name: REDIS_HOST
  value: "redis.example.com"
# ... etc
```

#### 2. Apply Manifests

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Deploy application
kubectl apply -f kubernetes/deployment.yaml

# Create service
kubectl apply -f kubernetes/service.yaml

# Create ingress
kubectl apply -f kubernetes/ingress.yaml
```

#### 3. Verify Deployment

```bash
# Check deployment status
kubectl rollout status deployment/resortslite -n resortslite

# Check pods
kubectl get pods -n resortslite

# Check services
kubectl get svc -n resortslite

# Check ingress
kubectl get ingress -n resortslite
```

#### 4. Get Application URL

```bash
# Get load balancer URL
kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

---

## Configuration Management

### Environment Variables

The application uses environment variables for configuration. Key variables include:

#### Application Configuration
- `SERVER_PORT`: Application port (default: 8080)
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (production, development)

#### Database Configuration
- `DB_URL`: JDBC connection URL
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

#### Redis Configuration
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port (default: 6379)
- `REDIS_PASSWORD`: Redis authentication password

#### AWS Configuration
- `AWS_REGION`: AWS region for services
- `S3_BUCKET_NAME`: S3 bucket for file storage

#### External Services
- `PAYMENT_ENDPOINT`: Payment service URL
- `INVENTORY_ENDPOINT`: Inventory service URL
- `NOTIFICATION_ENDPOINT`: Notification service URL

### Using Kubernetes Secrets

For sensitive data, use Kubernetes secrets:

```bash
# Create secret for database credentials
kubectl create secret generic db-credentials \
  --from-literal=username=dbuser \
  --from-literal=password=dbpass \
  -n resortslite

# Create secret for Redis password
kubectl create secret generic redis-credentials \
  --from-literal=password=redispass \
  -n resortslite
```

Update `deployment.yaml` to use secrets:
```yaml
env:
- name: DB_USERNAME
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: username
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: password
```

### Using ConfigMaps

For non-sensitive configuration:

```bash
# Create ConfigMap
kubectl create configmap app-config \
  --from-literal=payment.endpoint=http://payment-svc:9090/charge \
  --from-literal=inventory.endpoint=http://inventory-svc:8081/rooms \
  -n resortslite
```

---

## Monitoring and Health Checks

### Spring Boot Actuator Endpoints

The application exposes health check endpoints:

- **Health**: `/actuator/health`
- **Info**: `/actuator/info`

### Kubernetes Health Probes

The deployment includes:

#### Liveness Probe
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

#### Readiness Probe
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

### Viewing Logs

```bash
# View logs for all pods
kubectl logs -n resortslite -l app=resortslite

# Follow logs in real-time
kubectl logs -n resortslite -l app=resortslite -f

# View logs for specific pod
kubectl logs -n resortslite <pod-name>

# View previous container logs (if crashed)
kubectl logs -n resortslite <pod-name> --previous
```

### Monitoring with CloudWatch

Enable CloudWatch Container Insights:

```bash
# Install CloudWatch agent
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluentd-quickstart.yaml
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

**Symptoms**: Pods stuck in `Pending` or `CrashLoopBackOff` state

**Diagnosis**:
```bash
# Check pod status
kubectl get pods -n resortslite

# Describe pod for events
kubectl describe pod <pod-name> -n resortslite

# Check logs
kubectl logs <pod-name> -n resortslite
```

**Common Causes**:
- Insufficient cluster resources
- Image pull errors (check ECR permissions)
- Configuration errors (check environment variables)
- Health check failures (check application startup time)

**Solutions**:
```bash
# Scale down if resource constrained
kubectl scale deployment resortslite -n resortslite --replicas=1

# Check node resources
kubectl top nodes

# Verify image exists
aws ecr describe-images --repository-name resortslite --region us-east-1
```

#### 2. Service Not Accessible

**Symptoms**: Cannot access application via load balancer

**Diagnosis**:
```bash
# Check service
kubectl get svc -n resortslite

# Check ingress
kubectl get ingress -n resortslite

# Describe ingress for events
kubectl describe ingress resortslite-ingress -n resortslite
```

**Common Causes**:
- Load balancer not provisioned
- Security group rules blocking traffic
- Ingress controller not installed

**Solutions**:
```bash
# Verify AWS Load Balancer Controller is running
kubectl get pods -n kube-system | grep aws-load-balancer-controller

# Check security groups allow traffic on port 80/443
aws ec2 describe-security-groups --group-ids <sg-id>
```

#### 3. Database Connection Errors

**Symptoms**: Application logs show database connection failures

**Diagnosis**:
```bash
# Check logs for connection errors
kubectl logs -n resortslite -l app=resortslite | grep -i "database\|connection"

# Verify environment variables
kubectl exec -n resortslite <pod-name> -- env | grep DB_
```

**Solutions**:
- Verify database endpoint is accessible from EKS cluster
- Check security groups allow traffic from EKS nodes
- Verify credentials are correct
- Test connection from pod:
```bash
kubectl exec -n resortslite <pod-name> -it -- /bin/sh
# Inside pod, test connection (if tools available)
```

#### 4. Redis Connection Issues

**Symptoms**: Session management failures, Redis connection errors

**Diagnosis**:
```bash
# Check Redis configuration
kubectl exec -n resortslite <pod-name> -- env | grep REDIS_

# Check logs
kubectl logs -n resortslite -l app=resortslite | grep -i redis
```

**Solutions**:
- Verify Redis endpoint and port
- Check Redis authentication credentials
- Ensure Redis is accessible from EKS cluster
- Verify security groups allow traffic on Redis port (6379)

#### 5. Image Pull Errors

**Symptoms**: `ImagePullBackOff` or `ErrImagePull` status

**Diagnosis**:
```bash
# Describe pod
kubectl describe pod <pod-name> -n resortslite
```

**Solutions**:
```bash
# Verify ECR permissions
aws ecr get-login-password --region us-east-1

# Check if image exists
aws ecr describe-images --repository-name resortslite --region us-east-1

# Verify node IAM role has ECR read permissions
aws iam list-attached-role-policies --role-name ResortsLiteEKSNodeRole
```

### Debugging Commands

```bash
# Get all resources in namespace
kubectl get all -n resortslite

# Describe deployment
kubectl describe deployment resortslite -n resortslite

# Get events
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Execute shell in pod
kubectl exec -n resortslite <pod-name> -it -- /bin/sh

# Port forward for local testing
kubectl port-forward -n resortslite <pod-name> 8080:8080

# Check resource usage
kubectl top pods -n resortslite
kubectl top nodes
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment resortslite -n resortslite --replicas=3

# Verify scaling
kubectl get pods -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA
kubectl autoscale deployment resortslite \
  -n resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10

# Check HPA status
kubectl get hpa -n resortslite

# Describe HPA
kubectl describe hpa resortslite -n resortslite
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=<new-image-uri> \
  -n resortslite

# Check rollout status
kubectl rollout status deployment/resortslite -n resortslite

# View rollout history
kubectl rollout history deployment/resortslite -n resortslite
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout undo deployment/resortslite -n resortslite --to-revision=2
```

### Resource Management

Update resource limits in `deployment.yaml`:
```yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "1Gi"
```

Apply changes:
```bash
kubectl apply -f kubernetes/deployment.yaml
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as non-root user (appuser)
- **Read-only Root Filesystem**: Consider adding:
  ```yaml
  securityContext:
    readOnlyRootFilesystem: true
    runAsNonRoot: true
    runAsUser: 1000
  ```

### 2. Network Security

- **Network Policies**: Implement network policies to restrict pod-to-pod communication
- **Security Groups**: Configure AWS security groups to allow only necessary traffic
- **Private Subnets**: Deploy worker nodes in private subnets

### 3. Secrets Management

- **AWS Secrets Manager**: Use AWS Secrets Manager for sensitive data
- **Kubernetes Secrets**: Encrypt secrets at rest
- **IAM Roles**: Use IAM roles for service accounts (IRSA) for AWS access

### 4. Image Security

- **Scan Images**: Use AWS ECR image scanning
  ```bash
  aws ecr start-image-scan --repository-name resortslite --image-id imageTag=latest
  ```
- **Use Specific Tags**: Avoid using `latest` tag in production
- **Minimal Base Images**: Use minimal base images (Alpine, Distroless)

### 5. RBAC Configuration

Create service account with limited permissions:
```yaml
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

### 6. Logging and Auditing

- Enable EKS control plane logging
- Configure CloudWatch Logs for application logs
- Enable AWS CloudTrail for API auditing

---

## Java-Specific Considerations

### JVM Memory Configuration

The application uses the following JVM settings optimized for containers:

```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

**Explanation**:
- `-Xmx512m`: Maximum heap size
- `-Xms256m`: Initial heap size
- `-XX:+UseContainerSupport`: Enable container awareness
- `-XX:MaxRAMPercentage=75.0`: Use 75% of container memory for heap

### Adjust for Production

For production workloads, adjust based on container memory limits:

```yaml
resources:
  limits:
    memory: "2Gi"
env:
- name: JAVA_OPTS
  value: "-Xmx1536m -Xms1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### Garbage Collection

For better performance, consider using G1GC:
```bash
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Spring Boot Profiles

Use Spring profiles for environment-specific configuration:
```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "production"
```

---

## Additional Resources

- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Documentation](https://docs.docker.com/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update Dependencies**: Regularly update Spring Boot and dependencies
2. **Patch Security Vulnerabilities**: Monitor and patch CVEs
3. **Update Kubernetes**: Keep EKS cluster version up to date
4. **Review Logs**: Regularly review application and cluster logs
5. **Monitor Resources**: Track resource usage and adjust limits
6. **Backup Data**: Implement backup strategy for persistent data

### Getting Help

For issues or questions:
1. Check application logs: `kubectl logs -n resortslite -l app=resortslite`
2. Review Kubernetes events: `kubectl get events -n resortslite`
3. Consult AWS EKS documentation
4. Contact your DevOps team or AWS support

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Application**: ResortsLite v1.0.0  
**Target Platform**: AWS EKS
