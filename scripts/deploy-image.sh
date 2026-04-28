#!/bin/bash
set -e
set -o pipefail

# Deploy to GCP GKE Script for ResortsLite
# This script deploys the application to Google Kubernetes Engine

echo "=========================================="
echo "ResortsLite - GKE Deployment"
echo "=========================================="
echo ""

# Prompt for GCP configuration
read -p "Enter GCP Project ID: " GCP_PROJECT
read -p "Enter GCP Zone (e.g., us-central1-a): " GCP_ZONE
read -p "Enter GKE Cluster Name: " CLUSTER_NAME

# Validate inputs
if [ -z "$GCP_PROJECT" ] || [ -z "$GCP_ZONE" ] || [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: All GCP configuration fields are required"
    exit 1
fi

# Prompt for Docker image URI
echo ""
read -p "Enter Docker Image URI (with tag): " IMAGE_URI

if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required"
    exit 1
fi

# Prompt for environment variables
echo ""
echo "=== Application Configuration ==="
echo "Enter values for environment variables (press Enter to use defaults)"
echo ""

read -p "Database URL (default: jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1): " DB_URL
DB_URL=${DB_URL:-jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}

read -p "Database Username (default: sa): " DB_USERNAME
DB_USERNAME=${DB_USERNAME:-sa}

read -p "Database Password (default: empty): " DB_PASSWORD
DB_PASSWORD=${DB_PASSWORD:-}

read -p "Redis Host (default: redis-service): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-redis-service}

read -p "Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -p "Redis Password (default: empty): " REDIS_PASSWORD
REDIS_PASSWORD=${REDIS_PASSWORD:-}

read -p "Payment Service URL (default: http://payment-svc:9090/charge): " PAYMENT_SERVICE_URL
PAYMENT_SERVICE_URL=${PAYMENT_SERVICE_URL:-http://payment-svc:9090/charge}

read -p "Inventory Service URL (default: http://inventory-svc:8081/rooms): " INVENTORY_SERVICE_URL
INVENTORY_SERVICE_URL=${INVENTORY_SERVICE_URL:-http://inventory-svc:8081/rooms}

read -p "Notification Service URL (default: http://notify-svc:7070/send): " NOTIFICATION_SERVICE_URL
NOTIFICATION_SERVICE_URL=${NOTIFICATION_SERVICE_URL:-http://notify-svc:7070/send}

read -p "GCP Project ID for Storage (default: $GCP_PROJECT): " GCP_STORAGE_PROJECT
GCP_STORAGE_PROJECT=${GCP_STORAGE_PROJECT:-$GCP_PROJECT}

read -p "GCS Bucket Name (default: resortslite-reports): " GCS_BUCKET_NAME
GCS_BUCKET_NAME=${GCS_BUCKET_NAME:-resortslite-reports}

echo ""
echo "=========================================="
echo "Configuring kubectl for GKE cluster..."
echo "=========================================="

# Configure kubectl to use the GKE cluster
gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$GCP_ZONE" --project "$GCP_PROJECT"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for GKE cluster"
    exit 1
fi

# Verify cluster connectivity
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to connect to Kubernetes cluster"
    exit 1
fi

echo ""
echo "=========================================="
echo "Updating Kubernetes manifests..."
echo "=========================================="

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update manifests with actual values using pipe delimiter
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_URL}}|$DB_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_USERNAME}}|$DB_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{INVENTORY_SERVICE_URL}}|$INVENTORY_SERVICE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{NOTIFICATION_SERVICE_URL}}|$NOTIFICATION_SERVICE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCP_PROJECT_ID}}|$GCP_STORAGE_PROJECT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCS_BUCKET_NAME}}|$GCS_BUCKET_NAME|g" "$TEMP_DIR/deployment.yaml"

echo ""
echo "=========================================="
echo "Applying Kubernetes manifests..."
echo "=========================================="

# Apply namespace
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

# Apply deployment
echo "Creating deployment..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

# Apply service
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

# Apply ingress
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "=========================================="
echo "Waiting for deployment to complete..."
echo "=========================================="

# Wait for deployment rollout
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n resortslite
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n resortslite -l app=resortslite --tail=50
    
    # Cleanup temp directory
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo ""
echo "=========================================="
echo "Verifying deployment..."
echo "=========================================="

# Display deployment status
kubectl get pods,svc,ingress -n resortslite

echo ""
echo "=========================================="
echo "SUCCESS!"
echo "=========================================="
echo "Application deployed successfully to GKE"
echo ""
echo "Cluster: $CLUSTER_NAME"
echo "Namespace: resortslite"
echo "Image: $IMAGE_URI"
echo ""
echo "To access the application:"
echo "1. Get the ingress IP: kubectl get ingress -n resortslite"
echo "2. Access via: http://<INGRESS_IP>/"
echo "3. Health check: http://<INGRESS_IP>/actuator/health"
echo ""
echo "To view logs:"
echo "kubectl logs -n resortslite -l app=resortslite -f"
echo ""
echo "To scale the deployment:"
echo "kubectl scale deployment/resortslite -n resortslite --replicas=3"
echo "=========================================="

# Cleanup temp directory
rm -rf "$TEMP_DIR"
