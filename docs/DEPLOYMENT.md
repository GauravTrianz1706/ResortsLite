# ResortsLite - AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Fargate Setup](#ecs-fargate-setup)
7. [Deployment to AWS ECS Fargate](#deployment-to-aws-ecs-fargate)
8. [Configuration Management](#configuration-management)
9. [Monitoring and Logging](#monitoring-and-logging)
10. [Troubleshooting](#troubleshooting)
11. [Scaling and Management](#scaling-and-management)
12. [Security Considerations](#security-considerations)

---

## Overview

ResortsLite is a Spring Boot 2.7.18 application built with Java 8, designed for containerized deployment on AWS ECS Fargate. This guide provides comprehensive instructions for building, deploying, and managing the application in a cloud-native environment.

**Technology Stack:**
- Java 8 (Eclipse Temurin)
- Spring Boot 2.7.18
- Maven 3.9.4
- Spring Boot Actuator (Health checks)
- AWS SDK (S3, SSM)
- Redis (Session management)
- H2 Database (In-memory)

**Target Platform:**
- AWS ECS Fargate
- Application Load Balancer (Optional)
- CloudWatch Logs
- AWS ECR (Container Registry)

---

## Prerequisites

### Required Software
- **Docker**: Version 20.10 or higher
  - [Install Docker Desktop](https://www.docker.com/products/docker-desktop)
- **AWS CLI**: Version 2.x
  - [Install AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
- **Git**: For version control
- **Text Editor**: VS Code, IntelliJ IDEA, or similar

### AWS Account Requirements
- Active AWS account with appropriate permissions
- IAM user with programmatic access
- AWS credentials configured locally

### Configure AWS CLI
```bash
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region (e.g., us-east-1)
# Enter default output format (json)
```

Verify configuration:
```bash
aws sts get-caller-identity
```

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd ResortsLiteComp
```

### 2. Build the Application Locally (Optional)
```bash
mvn clean package -DskipTests
```

### 3. Run with Docker Compose

**Start the application:**
```bash
docker-compose up -d
```

**View logs:**
```bash
docker-compose logs -f resortslite-app
```

**Stop the application:**
```bash
docker-compose down
```

### 4. Access the Application

- **Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **H2 Console**: http://localhost:8080/h2-console

### 5. Local Environment Variables

Create a `.env` file for local development:
```env
# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# AWS Configuration
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
S3_REPORTS_BUCKET=resort-reports-bucket
S3_BACKUPS_BUCKET=resort-backups-bucket

# External Services
PAYMENT_SERVICE_URL=http://payment-service:9090/payments/charge
INVENTORY_SERVICE_URL=http://inventory-service:8081/rooms
NOTIFICATION_SERVICE_URL=http://notification-service:7070/send
```

---

## Building and Pushing Docker Images

### Option 1: Using build-push.sh (Linux/macOS)

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

**Interactive Prompts:**
1. Enter image tag (default: latest)
2. Select registry type:
   - **1**: AWS ECR
   - **2**: Docker Hub
3. Provide registry-specific credentials

**For AWS ECR:**
- AWS Region (e.g., us-east-1)
- AWS Account ID
- ECR Repository Name

**For Docker Hub:**
- Docker Hub username
- Docker Hub password/token

### Option 2: Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

Follow the same interactive prompts as the Linux version.

### Manual Build and Push

**Build the image:**
```bash
docker build -t resortslite:latest .
```

**Tag for ECR:**
```bash
docker tag resortslite:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

**Login to ECR:**
```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com
```

**Push to ECR:**
```bash
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. VPC and Networking Setup

**Create VPC (if not exists):**
```bash
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --region us-east-1
```

**Create Subnets (at least 2 in different AZs):**
```bash
# Subnet 1
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.1.0/24 --availability-zone us-east-1a

# Subnet 2
aws ec2 create-subnet --vpc-id vpc-xxxxx --cidr-block 10.0.2.0/24 --availability-zone us-east-1b
```

**Create Internet Gateway:**
```bash
aws ec2 create-internet-gateway
aws ec2 attach-internet-gateway --vpc-id vpc-xxxxx --internet-gateway-id igw-xxxxx
```

**Update Route Table:**
```bash
aws ec2 create-route --route-table-id rtb-xxxxx --destination-cidr-block 0.0.0.0/0 --gateway-id igw-xxxxx
```

### 2. Security Group Configuration

**Create Security Group:**
```bash
aws ec2 create-security-group \
  --group-name resortslite-sg \
  --description "Security group for ResortsLite ECS tasks" \
  --vpc-id vpc-xxxxx
```

**Add Inbound Rules:**
```bash
# Allow HTTP traffic on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow HTTPS traffic (if needed)
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxx \
  --protocol tcp \
  --port 443 \
  --cidr 0.0.0.0/0
```

### 3. IAM Roles Setup

**Create ECS Task Execution Role:**

Create trust policy file `ecs-trust-policy.json`:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

Create the role:
```bash
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document file://ecs-trust-policy.json

aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

**Create ECS Task Role (for application permissions):**

Create task role policy file `ecs-task-policy.json`:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::resort-reports-bucket/*",
        "arn:aws:s3:::resort-backups-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters"
      ],
      "Resource": "arn:aws:ssm:*:*:parameter/*"
    }
  ]
}
```

Create the role:
```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document file://ecs-trust-policy.json

aws iam put-role-policy \
  --role-name ecsTaskRole \
  --policy-name ResortsLiteTaskPolicy \
  --policy-document file://ecs-task-policy.json
```

### 4. CloudWatch Log Group

**Create log group:**
```bash
aws logs create-log-group --log-group-name /ecs/resortslite --region us-east-1
```

**Set retention policy (optional):**
```bash
aws logs put-retention-policy \
  --log-group-name /ecs/resortslite \
  --retention-in-days 7
```

### 5. ECR Repository

**Create ECR repository:**
```bash
aws ecr create-repository \
  --repository-name resortslite \
  --region us-east-1
```

---

## ECS Fargate Setup

### Understanding ECS Task Definition

The task definition (`ecs/task-definition.json`) defines:

**Launch Type Configuration:**
- `requiresCompatibilities`: ["FARGATE"]
- `networkMode`: "awsvpc" (required for Fargate)

**CPU and Memory:**
- Valid Fargate combinations:
  - CPU: "256" (.25 vCPU) → Memory: 512, 1024, 2048 MB
  - CPU: "512" (.5 vCPU) → Memory: 1024, 2048, 3072, 4096 MB
  - CPU: "1024" (1 vCPU) → Memory: 2048-8192 MB
  - CPU: "2048" (2 vCPU) → Memory: 4096-16384 MB
  - CPU: "4096" (4 vCPU) → Memory: 8192-30720 MB

**Default Configuration:**
- CPU: "512" (.5 vCPU)
- Memory: "1024" MB

**Container Definition:**
- Image URI (replaced during deployment)
- Port mappings (8080)
- Environment variables
- Health check configuration
- CloudWatch logging

### Understanding ECS Service Definition

The service definition (`ecs/service-definition.json`) defines:

**Service Configuration:**
- Desired count: 2 (for high availability)
- Launch type: FARGATE
- Network configuration (awsvpc mode)

**Deployment Configuration:**
- Maximum percent: 200 (allows rolling updates)
- Minimum healthy percent: 50
- Circuit breaker enabled with rollback

**Load Balancer (Optional):**
- Target group ARN
- Container name and port
- Health check grace period: 300 seconds

**Tags:**
- Environment, Application, ManagedBy

---

## Deployment to AWS ECS Fargate

### Option 1: Using deploy-image.sh (Linux/macOS)

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Interactive Prompts:**
1. AWS Region
2. ECS Cluster Name
3. VPC ID
4. Subnet IDs (comma-separated)
5. Security Group ID
6. Docker Image URI
7. Redis configuration (host, port, password)
8. S3 bucket names
9. External service URLs
10. Load balancer requirement (y/n)

### Option 2: Using deploy-image.bat (Windows)

```cmd
scripts\deploy-image.bat
```

Follow the same interactive prompts as the Linux version.

### Deployment Process

The deployment script performs the following steps:

1. **Validates AWS credentials and configuration**
2. **Creates or verifies ECS cluster**
3. **Creates Application Load Balancer (if requested)**
   - Creates ALB with internet-facing scheme
   - Creates target group with health checks
   - Creates listener on port 80
4. **Prepares task definition**
   - Replaces placeholders with actual values
   - Registers task definition with ECS
5. **Creates CloudWatch log group**
6. **Prepares service definition**
   - Replaces placeholders
   - Removes load balancer config if not needed
7. **Creates or updates ECS service**
   - Creates new service if doesn't exist
   - Updates existing service with new task definition
8. **Waits for service stability**
9. **Displays deployment information**

### Verify Deployment

**Check service status:**
```bash
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1
```

**Check running tasks:**
```bash
aws ecs list-tasks \
  --cluster resortslite-cluster \
  --service-name resortslite-service \
  --region us-east-1
```

**View task details:**
```bash
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks <task-arn> \
  --region us-east-1
```

---

## Configuration Management

### Environment Variables

The application uses environment variables for configuration:

**Server Configuration:**
- `SERVER_PORT`: Application port (default: 8080)
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (docker)

**Database Configuration:**
- `DB_URL`: Database connection URL
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

**Redis Configuration:**
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port (default: 6379)
- `REDIS_PASSWORD`: Redis password

**AWS Configuration:**
- `AWS_REGION`: AWS region
- `S3_REPORTS_BUCKET`: S3 bucket for reports
- `S3_BACKUPS_BUCKET`: S3 bucket for backups

**External Services:**
- `PAYMENT_SERVICE_URL`: Payment service endpoint
- `INVENTORY_SERVICE_URL`: Inventory service endpoint
- `NOTIFICATION_SERVICE_URL`: Notification service endpoint

**JVM Configuration:**
- `JAVA_OPTS`: JVM options for memory and performance

### Updating Configuration

**Update task definition:**
1. Modify `ecs/task-definition.json`
2. Update environment variables
3. Re-run deployment script

**Update service:**
```bash
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --force-new-deployment \
  --region us-east-1
```

---

## Monitoring and Logging

### CloudWatch Logs

**View logs in real-time:**
```bash
aws logs tail /ecs/resortslite --follow --region us-east-1
```

**View logs for specific time range:**
```bash
aws logs filter-log-events \
  --log-group-name /ecs/resortslite \
  --start-time 1609459200000 \
  --end-time 1609545600000 \
  --region us-east-1
```

**Search logs:**
```bash
aws logs filter-log-events \
  --log-group-name /ecs/resortslite \
  --filter-pattern "ERROR" \
  --region us-east-1
```

### CloudWatch Metrics

**View ECS service metrics:**
- CPU utilization
- Memory utilization
- Network in/out
- Task count

**Access metrics:**
1. Open CloudWatch console
2. Navigate to Metrics → ECS
3. Select cluster and service
4. View available metrics

### Application Health Checks

**Health endpoint:**
```bash
curl http://<alb-dns>/actuator/health
```

**Expected response:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    },
    "redis": {
      "status": "UP"
    }
  }
}
```

### Setting Up CloudWatch Alarms

**Create CPU alarm:**
```bash
aws cloudwatch put-metric-alarm \
  --alarm-name resortslite-high-cpu \
  --alarm-description "Alert when CPU exceeds 80%" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=resortslite-service Name=ClusterName,Value=resortslite-cluster
```

**Create memory alarm:**
```bash
aws cloudwatch put-metric-alarm \
  --alarm-name resortslite-high-memory \
  --alarm-description "Alert when memory exceeds 80%" \
  --metric-name MemoryUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 2 \
  --dimensions Name=ServiceName,Value=resortslite-service Name=ClusterName,Value=resortslite-cluster
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Task Fails to Start

**Symptoms:**
- Tasks transition to STOPPED state immediately
- No logs in CloudWatch

**Possible Causes:**
- Invalid CPU/memory combination
- Image pull errors
- Missing IAM permissions

**Solutions:**
```bash
# Check task stopped reason
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks <task-arn> \
  --query 'tasks[0].stoppedReason'

# Verify IAM roles
aws iam get-role --role-name ecsTaskExecutionRole
aws iam get-role --role-name ecsTaskRole

# Check ECR permissions
aws ecr get-login-password --region us-east-1
```

#### 2. Network Connectivity Issues

**Symptoms:**
- Cannot access application via ALB
- Health checks failing

**Possible Causes:**
- Security group misconfiguration
- Subnet routing issues
- Target group health check misconfiguration

**Solutions:**
```bash
# Verify security group rules
aws ec2 describe-security-groups --group-ids sg-xxxxx

# Check target group health
aws elbv2 describe-target-health \
  --target-group-arn <target-group-arn>

# Verify subnet route tables
aws ec2 describe-route-tables --filters "Name=association.subnet-id,Values=subnet-xxxxx"
```

#### 3. Application Crashes or OOM Errors

**Symptoms:**
- Tasks restart frequently
- Out of memory errors in logs

**Possible Causes:**
- Insufficient memory allocation
- Memory leaks
- JVM heap size misconfiguration

**Solutions:**
```bash
# Increase task memory in task definition
# Update JAVA_OPTS in task definition:
"JAVA_OPTS": "-Xmx768m -Xms384m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Monitor memory usage
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=resortslite-service \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-01T23:59:59Z \
  --period 300 \
  --statistics Average
```

#### 4. Service Update Failures

**Symptoms:**
- Service update fails
- Circuit breaker triggered

**Possible Causes:**
- Health check failures
- Deployment configuration issues
- Resource constraints

**Solutions:**
```bash
# Check service events
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --query 'services[0].events[0:10]'

# Force new deployment
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --force-new-deployment

# Rollback to previous task definition
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --task-definition resortslite-task:1
```

#### 5. Redis Connection Issues

**Symptoms:**
- Application fails to start
- Redis health check fails

**Possible Causes:**
- Incorrect Redis host/port
- Network connectivity issues
- Authentication failures

**Solutions:**
```bash
# Test Redis connectivity from task
aws ecs execute-command \
  --cluster resortslite-cluster \
  --task <task-id> \
  --container resortslite \
  --interactive \
  --command "/bin/sh"

# Inside container:
# telnet $REDIS_HOST $REDIS_PORT

# Verify environment variables
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks <task-arn> \
  --query 'tasks[0].containers[0].environment'
```

### Debug Mode

**Enable debug logging:**

Update task definition environment variables:
```json
{
  "name": "LOGGING_LEVEL_ROOT",
  "value": "DEBUG"
}
```

**View detailed logs:**
```bash
aws logs tail /ecs/resortslite --follow --filter-pattern "DEBUG" --region us-east-1
```

---

## Scaling and Management

### Manual Scaling

**Update desired count:**
```bash
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --desired-count 4 \
  --region us-east-1
```

### Auto Scaling

**Create auto scaling target:**
```bash
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10 \
  --region us-east-1
```

**Create scaling policy (CPU-based):**
```bash
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cpu-scaling-policy \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration file://scaling-policy.json \
  --region us-east-1
```

**scaling-policy.json:**
```json
{
  "TargetValue": 70.0,
  "PredefinedMetricSpecification": {
    "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
  },
  "ScaleInCooldown": 300,
  "ScaleOutCooldown": 60
}
```

### Blue/Green Deployments

**Using AWS CodeDeploy:**

1. Create CodeDeploy application
2. Create deployment group
3. Configure deployment settings
4. Trigger deployment

**Example deployment configuration:**
```json
{
  "deploymentConfigName": "CodeDeployDefault.ECSAllAtOnce",
  "minimumHealthyHosts": {
    "type": "FLEET_PERCENT",
    "value": 50
  }
}
```

### Rolling Updates

**Update service with rolling deployment:**
```bash
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --task-definition resortslite-task:2 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=50" \
  --region us-east-1
```

---

## Security Considerations

### 1. Container Security

**Best Practices:**
- Use non-root user in Dockerfile ✓
- Scan images for vulnerabilities
- Keep base images updated
- Minimize image size

**Scan image with AWS ECR:**
```bash
aws ecr start-image-scan \
  --repository-name resortslite \
  --image-id imageTag=latest \
  --region us-east-1

aws ecr describe-image-scan-findings \
  --repository-name resortslite \
  --image-id imageTag=latest \
  --region us-east-1
```

### 2. Network Security

**Best Practices:**
- Use security groups to restrict traffic
- Enable VPC Flow Logs
- Use private subnets for tasks (with NAT Gateway)
- Enable AWS WAF for ALB

**Enable VPC Flow Logs:**
```bash
aws ec2 create-flow-logs \
  --resource-type VPC \
  --resource-ids vpc-xxxxx \
  --traffic-type ALL \
  --log-destination-type cloud-watch-logs \
  --log-group-name /aws/vpc/flowlogs
```

### 3. Secrets Management

**Use AWS Secrets Manager:**

**Store secret:**
```bash
aws secretsmanager create-secret \
  --name resortslite/redis-password \
  --secret-string "your-redis-password" \
  --region us-east-1
```

**Update task definition to use secrets:**
```json
{
  "secrets": [
    {
      "name": "REDIS_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:resortslite/redis-password"
    }
  ]
}
```

**Grant task execution role access:**
```bash
aws iam put-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-name SecretsManagerAccess \
  --policy-document file://secrets-policy.json
```

**secrets-policy.json:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789:secret:resortslite/*"
    }
  ]
}
```

### 4. IAM Best Practices

**Principle of Least Privilege:**
- Grant only necessary permissions
- Use separate roles for execution and task
- Regularly audit IAM policies

**Enable CloudTrail:**
```bash
aws cloudtrail create-trail \
  --name resortslite-trail \
  --s3-bucket-name my-cloudtrail-bucket \
  --is-multi-region-trail
```

### 5. Compliance and Auditing

**Enable AWS Config:**
```bash
aws configservice put-configuration-recorder \
  --configuration-recorder name=default,roleARN=arn:aws:iam::123456789:role/config-role \
  --recording-group allSupported=true,includeGlobalResourceTypes=true
```

**Enable GuardDuty:**
```bash
aws guardduty create-detector --enable
```

---

## Technology-Specific Notes

### Java 8 and Spring Boot 2.7.18

**JVM Tuning for Containers:**
- Use `-XX:+UseContainerSupport` for container awareness
- Set `-XX:MaxRAMPercentage=75.0` for memory limits
- Enable garbage collection logging for troubleshooting

**Spring Boot Actuator:**
- Health endpoint: `/actuator/health`
- Info endpoint: `/actuator/info`
- Metrics endpoint: `/actuator/metrics`

**Spring Profiles:**
- `docker`: Container-specific configuration
- Use environment variables for profile-specific settings

**Session Management:**
- Redis-backed sessions for distributed deployment
- Configure session timeout in application.properties

**AWS SDK Integration:**
- Use IAM roles for authentication (no hardcoded credentials)
- Configure region via environment variable
- Enable SDK retry logic for resilience

### Performance Optimization

**JVM Heap Sizing:**
```
-Xms256m -Xmx512m
```

**Garbage Collection:**
```
-XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**Connection Pooling:**
- Configure HikariCP for database connections
- Set appropriate pool sizes based on load

---

## Additional Resources

### AWS Documentation
- [ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html)
- [ECS Service Auto Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html)

### Spring Boot Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/actuator.html)

### Docker Documentation
- [Dockerfile Best Practices](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Docker Compose](https://docs.docker.com/compose/)

---

## Support and Maintenance

### Regular Maintenance Tasks

1. **Update base images monthly**
2. **Review and rotate secrets quarterly**
3. **Audit IAM permissions quarterly**
4. **Review CloudWatch logs and metrics weekly**
5. **Test disaster recovery procedures quarterly**

### Backup and Recovery

**Backup Strategy:**
- Application state stored in Redis (ephemeral)
- Database backups to S3 (if using persistent database)
- Configuration stored in version control

**Recovery Procedure:**
1. Restore configuration from version control
2. Deploy latest stable image
3. Verify health checks
4. Restore data from S3 backups (if needed)

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ResortsLite to AWS ECS Fargate. Follow the steps carefully, and refer to the troubleshooting section for common issues. For additional support, consult the AWS documentation or contact your DevOps team.

**Quick Start Summary:**
1. Build and push Docker image: `./scripts/build-push.sh`
2. Deploy to ECS Fargate: `./scripts/deploy-image.sh`
3. Monitor via CloudWatch Logs: `aws logs tail /ecs/resortslite --follow`
4. Access application via ALB DNS name

**Happy Deploying! 🚀**
