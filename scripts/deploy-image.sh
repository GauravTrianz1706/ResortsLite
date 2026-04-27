#!/bin/bash
set -e
set -o pipefail

# ============================================
# Deploy to AWS EKS Script
# ResortsLite Spring Boot Application
# ============================================

echo "=========================================="
echo "AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "ERROR: AWS Region is required"
    exit 1
fi

read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: EKS Cluster Name is required"
    exit 1
fi

# Prompt for Docker image URI
echo ""
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required"
    exit 1
fi

# Prompt for environment variables
echo ""
echo "=== Application Configuration ==="
echo "Enter values for environment variables (press Enter to use defaults)"
echo ""

read -p "Database URL (default: jdbc:h2:mem:resortdb): " DB_URL
DB_URL=${DB_URL:-jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}

read -p "Database Username (default: sa): " DB_USERNAME
DB_USERNAME=${DB_USERNAME:-sa}

read -sp "Database Password (default: empty): " DB_PASSWORD
echo ""
DB_PASSWORD=${DB_PASSWORD:-}

read -p "Payment Endpoint (default: http://payment-svc.internal:9090/charge): " PAYMENT_ENDPOINT
PAYMENT_ENDPOINT=${PAYMENT_ENDPOINT:-http://payment-svc.internal:9090/charge}

read -p "Inventory Endpoint (default: http://inventory-svc.internal:8081/rooms): " INVENTORY_ENDPOINT
INVENTORY_ENDPOINT=${INVENTORY_ENDPOINT:-http://inventory-svc.internal:8081/rooms}

read -p "Notification Endpoint (default: http://notify.internal:7070/send): " NOTIFICATION_ENDPOINT
NOTIFICATION_ENDPOINT=${NOTIFICATION_ENDPOINT:-http://notify.internal:7070/send}

read -p "Redis Host (default: redis.example.com): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-redis.example.com}

read -p "Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -sp "Redis Password (default: empty): " REDIS_PASSWORD
echo ""
REDIS_PASSWORD=${REDIS_PASSWORD:-}

read -p "S3 Bucket Name (default: resortslite-reports): " S3_BUCKET_NAME
S3_BUCKET_NAME=${S3_BUCKET_NAME:-resortslite-reports}

read -p "AWS Region for S3 (default: $AWS_REGION): " S3_AWS_REGION
S3_AWS_REGION=${S3_AWS_REGION:-$AWS_REGION}

echo ""
echo "=========================================="
echo "Configuring kubectl for EKS"
echo "=========================================="
echo ""

# Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for EKS cluster"
    exit 1
fi

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
}

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests"
echo "=========================================="
echo ""

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Copy manifests to temp directory
cp -r kubernetes/* "$TEMP_DIR/"

# Replace placeholders in deployment.yaml
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_URL}}|$DB_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_USERNAME}}|$DB_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{PAYMENT_ENDPOINT}}|$PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{INVENTORY_ENDPOINT}}|$INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{NOTIFICATION_ENDPOINT}}|$NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{S3_BUCKET_NAME}}|$S3_BUCKET_NAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{AWS_REGION}}|$S3_AWS_REGION|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"

echo ""
echo "=========================================="
echo "Deploying to AWS EKS"
echo "=========================================="
echo ""

# Apply Kubernetes manifests in order
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

echo ""
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

echo ""
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

echo ""
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout"
echo "=========================================="
echo ""

# Wait for deployment to complete
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n resortslite
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n resortslite -l app=resortslite --tail=50
    exit 1
fi

echo ""
echo "=========================================="
echo "Deployment Verification"
echo "=========================================="
echo ""

# Display deployment status
echo "Pods:"
kubectl get pods -n resortslite

echo ""
echo "Services:"
kubectl get svc -n resortslite

echo ""
echo "Ingress:"
kubectl get ingress -n resortslite

echo ""
echo "=========================================="
echo "Deployment Completed Successfully!"
echo "=========================================="
echo ""

# Get ingress URL
INGRESS_URL=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Pending...")

echo "Application Details:"
echo "  Namespace: resortslite"
echo "  Deployment: resortslite"
echo "  Service: resortslite-service"
echo "  Ingress URL: $INGRESS_URL"
echo ""
echo "Access your application at: http://$INGRESS_URL"
echo ""
echo "To check application logs:"
echo "  kubectl logs -n resortslite -l app=resortslite -f"
echo ""
echo "To check application health:"
echo "  kubectl exec -n resortslite -it \$(kubectl get pod -n resortslite -l app=resortslite -o jsonpath='{.items[0].metadata.name}') -- curl localhost:8080/actuator/health"
echo ""
