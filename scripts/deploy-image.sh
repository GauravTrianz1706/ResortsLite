#!/bin/bash

# Deploy to AWS ECS Fargate Script for ResortsLite Application
# This script deploys the Docker image to AWS ECS Fargate

set -e
set -o pipefail

echo "=========================================="
echo "ResortsLite - AWS ECS Fargate Deployment"
echo "=========================================="
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter ECS Cluster Name: " CLUSTER_NAME
read -p "Enter VPC ID: " VPC_ID
read -p "Enter Subnet IDs (comma-separated, at least 2): " SUBNETS_INPUT
read -p "Enter Security Group ID: " SECURITY_GROUP
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI

# Parse subnets
IFS=',' read -ra SUBNETS <<< "$SUBNETS_INPUT"
SUBNET_1=$(echo "${SUBNETS[0]}" | xargs)
SUBNET_2=$(echo "${SUBNETS[1]}" | xargs)

echo ""
echo "Configuration Summary:"
echo "  Region: $AWS_REGION"
echo "  Cluster: $CLUSTER_NAME"
echo "  VPC: $VPC_ID"
echo "  Subnets: $SUBNET_1, $SUBNET_2"
echo "  Security Group: $SECURITY_GROUP"
echo "  Image: $IMAGE_URI"
echo ""

# Get AWS Account ID
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"
echo ""

# Check if ECS cluster exists, create if not
echo "Checking if ECS cluster exists..."
aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || {
    echo "Cluster does not exist. Creating ECS cluster: $CLUSTER_NAME"
    aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
    echo "ECS cluster created successfully"
}
echo ""

# Prompt for Redis configuration
echo "=== Redis Configuration ==="
read -p "Enter Redis Host (e.g., redis.example.com): " REDIS_HOST
read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}
read -sp "Enter Redis Password (leave empty if none): " REDIS_PASSWORD
echo ""
echo ""

# Prompt for S3 bucket names
echo "=== S3 Configuration ==="
read -p "Enter S3 Reports Bucket Name: " S3_REPORTS_BUCKET
read -p "Enter S3 Backups Bucket Name: " S3_BACKUPS_BUCKET
echo ""

# Prompt for external service URLs
echo "=== External Service Configuration ==="
read -p "Enter Payment Service URL (e.g., http://payment-service:9090/payments/charge): " PAYMENT_SERVICE_URL
read -p "Enter Inventory Service URL (e.g., http://inventory-service:8081/rooms): " INVENTORY_SERVICE_URL
read -p "Enter Notification Service URL (e.g., http://notification-service:7070/send): " NOTIFICATION_SERVICE_URL
echo ""

# Prompt for load balancer
read -p "Do you need a load balancer for this service? (y/n): " NEED_LB

if [ "$NEED_LB" == "y" ] || [ "$NEED_LB" == "Y" ]; then
    echo ""
    echo "=== Creating Application Load Balancer ==="
    
    # Create ALB
    ALB_NAME="resortslite-alb"
    echo "Creating Application Load Balancer: $ALB_NAME"
    
    ALB_ARN=$(aws elbv2 create-load-balancer \
        --name "$ALB_NAME" \
        --subnets "$SUBNET_1" "$SUBNET_2" \
        --security-groups "$SECURITY_GROUP" \
        --scheme internet-facing \
        --type application \
        --ip-address-type ipv4 \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].LoadBalancerArn' \
        --output text)
    
    echo "ALB created: $ALB_ARN"
    
    # Get ALB DNS name
    ALB_DNS=$(aws elbv2 describe-load-balancers \
        --load-balancer-arns "$ALB_ARN" \
        --region "$AWS_REGION" \
        --query 'LoadBalancers[0].DNSName' \
        --output text)
    
    echo "ALB DNS: $ALB_DNS"
    echo ""
    
    # Create Target Group
    TG_NAME="resortslite-tg"
    echo "Creating Target Group: $TG_NAME"
    
    TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
        --name "$TG_NAME" \
        --protocol HTTP \
        --port 8080 \
        --vpc-id "$VPC_ID" \
        --target-type ip \
        --health-check-enabled \
        --health-check-protocol HTTP \
        --health-check-path "/actuator/health" \
        --health-check-interval-seconds 30 \
        --health-check-timeout-seconds 5 \
        --healthy-threshold-count 2 \
        --unhealthy-threshold-count 3 \
        --region "$AWS_REGION" \
        --query 'TargetGroups[0].TargetGroupArn' \
        --output text)
    
    echo "Target Group created: $TARGET_GROUP_ARN"
    echo ""
    
    # Create Listener
    echo "Creating ALB Listener..."
    aws elbv2 create-listener \
        --load-balancer-arn "$ALB_ARN" \
        --protocol HTTP \
        --port 80 \
        --default-actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
        --region "$AWS_REGION" >/dev/null
    
    echo "ALB Listener created"
    echo ""
    
    # Update service definition with load balancer
    LOAD_BALANCER_CONFIG=true
else
    echo "Skipping load balancer creation"
    LOAD_BALANCER_CONFIG=false
    TARGET_GROUP_ARN=""
fi

echo ""
echo "=== Preparing ECS Task Definition ==="

# Create temporary task definition with replaced placeholders
TEMP_TASK_DEF=$(mktemp)
cp ecs/task-definition.json "$TEMP_TASK_DEF"

# Replace placeholders in task definition
sed -i "s|{{ACCOUNT_ID}}|$ACCOUNT_ID|g" "$TEMP_TASK_DEF"
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_TASK_DEF"
sed -i "s|{{AWS_REGION}}|$AWS_REGION|g" "$TEMP_TASK_DEF"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_TASK_DEF"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_TASK_DEF"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_TASK_DEF"
sed -i "s|{{S3_REPORTS_BUCKET}}|$S3_REPORTS_BUCKET|g" "$TEMP_TASK_DEF"
sed -i "s|{{S3_BACKUPS_BUCKET}}|$S3_BACKUPS_BUCKET|g" "$TEMP_TASK_DEF"
sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" "$TEMP_TASK_DEF"
sed -i "s|{{INVENTORY_SERVICE_URL}}|$INVENTORY_SERVICE_URL|g" "$TEMP_TASK_DEF"
sed -i "s|{{NOTIFICATION_SERVICE_URL}}|$NOTIFICATION_SERVICE_URL|g" "$TEMP_TASK_DEF"

echo "Task definition prepared"
echo ""

# Create CloudWatch log group
echo "Creating CloudWatch log group..."
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "$AWS_REGION" 2>/dev/null || echo "Log group already exists"
echo ""

# Register task definition
echo "Registering ECS task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
    --cli-input-json file://"$TEMP_TASK_DEF" \
    --region "$AWS_REGION" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)

echo "Task definition registered: $TASK_DEF_ARN"
echo ""

# Clean up temporary file
rm "$TEMP_TASK_DEF"

# Prepare service definition
echo "=== Preparing ECS Service Definition ==="

TEMP_SERVICE_DEF=$(mktemp)
cp ecs/service-definition.json "$TEMP_SERVICE_DEF"

# Replace placeholders in service definition
sed -i "s|{{CLUSTER_NAME}}|$CLUSTER_NAME|g" "$TEMP_SERVICE_DEF"
sed -i "s|{{SUBNET_1}}|$SUBNET_1|g" "$TEMP_SERVICE_DEF"
sed -i "s|{{SUBNET_2}}|$SUBNET_2|g" "$TEMP_SERVICE_DEF"
sed -i "s|{{SECURITY_GROUP}}|$SECURITY_GROUP|g" "$TEMP_SERVICE_DEF"
sed -i "s|{{TARGET_GROUP_ARN}}|$TARGET_GROUP_ARN|g" "$TEMP_SERVICE_DEF"

# Remove load balancer section if not needed
if [ "$LOAD_BALANCER_CONFIG" == "false" ]; then
    # Remove loadBalancers and healthCheckGracePeriodSeconds from service definition
    python3 -c "
import json
import sys

with open('$TEMP_SERVICE_DEF', 'r') as f:
    service_def = json.load(f)

if 'loadBalancers' in service_def:
    del service_def['loadBalancers']
if 'healthCheckGracePeriodSeconds' in service_def:
    del service_def['healthCheckGracePeriodSeconds']

with open('$TEMP_SERVICE_DEF', 'w') as f:
    json.dump(service_def, f, indent=2)
" 2>/dev/null || {
        # Fallback if python3 is not available
        echo "Warning: Could not remove load balancer config. Proceeding anyway..."
    }
fi

echo "Service definition prepared"
echo ""

# Check if service exists
echo "Checking if ECS service exists..."
EXISTING_SERVICE=$(aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "resortslite-service" \
    --region "$AWS_REGION" \
    --query 'services[?status==`ACTIVE`].serviceName' \
    --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" == "None" ]; then
    echo "Service does not exist. Creating new ECS service..."
    
    aws ecs create-service \
        --cli-input-json file://"$TEMP_SERVICE_DEF" \
        --region "$AWS_REGION" >/dev/null
    
    echo "ECS service created successfully"
else
    echo "Service exists. Updating ECS service..."
    
    aws ecs update-service \
        --cluster "$CLUSTER_NAME" \
        --service "resortslite-service" \
        --task-definition "$TASK_DEF_ARN" \
        --desired-count 2 \
        --force-new-deployment \
        --region "$AWS_REGION" >/dev/null
    
    echo "ECS service updated successfully"
fi

echo ""

# Clean up temporary file
rm "$TEMP_SERVICE_DEF"

# Wait for service to stabilize
echo "Waiting for service to stabilize (this may take a few minutes)..."
aws ecs wait services-stable \
    --cluster "$CLUSTER_NAME" \
    --services "resortslite-service" \
    --region "$AWS_REGION"

echo ""
echo "=========================================="
echo "DEPLOYMENT SUCCESSFUL!"
echo "=========================================="
echo ""

# Display service information
echo "Service Details:"
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "resortslite-service" \
    --region "$AWS_REGION" \
    --query 'services[0].[serviceName,status,runningCount,desiredCount]' \
    --output table

echo ""
echo "CloudWatch Logs:"
echo "  Log Group: /ecs/resortslite"
echo "  Region: $AWS_REGION"
echo ""

if [ "$LOAD_BALANCER_CONFIG" == "true" ]; then
    echo "Application Load Balancer:"
    echo "  DNS Name: $ALB_DNS"
    echo "  Access your application at: http://$ALB_DNS"
    echo ""
fi

echo "To view logs:"
echo "  aws logs tail /ecs/resortslite --follow --region $AWS_REGION"
echo ""
echo "To check service status:"
echo "  aws ecs describe-services --cluster $CLUSTER_NAME --services resortslite-service --region $AWS_REGION"
echo ""
echo "=========================================="
