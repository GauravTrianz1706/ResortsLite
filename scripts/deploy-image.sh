#!/bin/bash
set -e
set -o pipefail

# ============================================
# Deploy to GCP GKE Script
# For ResortsLite Spring Boot Application
# ============================================

echo "=========================================="
echo "GKE Deployment Script"
echo "=========================================="
echo ""

# Prompt for GCP configuration
read -p "Enter GCP Project ID: " GCP_PROJECT
if [ -z "$GCP_PROJECT" ]; then
    echo "ERROR: GCP Project ID is required"
    exit 1
fi

read -p "Enter GCP Zone (e.g., us-central1-a): " GCP_ZONE
if [ -z "$GCP_ZONE" ]; then
    echo "ERROR: GCP Zone is required"
    exit 1
fi

read -p "Enter GKE Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: GKE Cluster Name is required"
    exit 1
fi

read -p "Enter Docker Image URI (with tag): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required"
    exit 1
fi

echo ""
echo "=========================================="
echo "Application Configuration"
echo "=========================================="
echo "Please provide configuration values (press Enter to use defaults):"
echo ""

# Redis Configuration
read -p "Enter Redis Host (default: redis.example.com): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-redis.example.com}

read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -p "Enter Redis Password (press Enter if none): " REDIS_PASSWORD

# GCS Configuration
read -p "Enter GCS Bucket Name (default: resortslite-reports): " GCS_BUCKET_NAME
GCS_BUCKET_NAME=${GCS_BUCKET_NAME:-resortslite-reports}

read -p "Enter GCP Project ID for GCS (default: $GCP_PROJECT): " GCP_PROJECT_ID
GCP_PROJECT_ID=${GCP_PROJECT_ID:-$GCP_PROJECT}

# External Service Endpoints
read -p "Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): " PAYMENT_ENDPOINT
PAYMENT_ENDPOINT=${PAYMENT_ENDPOINT:-http://payment-svc:9090/charge}

read -p "Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): " INVENTORY_ENDPOINT
INVENTORY_ENDPOINT=${INVENTORY_ENDPOINT:-http://inventory-svc:8081/rooms}

read -p "Enter Notification Service Endpoint (default: http://notify-svc:7070/send): " NOTIFICATION_ENDPOINT
NOTIFICATION_ENDPOINT=${NOTIFICATION_ENDPOINT:-http://notify-svc:7070/send}

echo ""
echo "=========================================="
echo "Configuring kubectl for GKE"
echo "=========================================="
echo ""

gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$GCP_ZONE" --project "$GCP_PROJECT"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for GKE cluster"
    exit 1
fi

echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to connect to Kubernetes cluster"
    exit 1
fi

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests"
echo "=========================================="
echo ""

# Create temporary directory for modified manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update deployment.yaml with actual values
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCS_BUCKET_NAME}}|$GCS_BUCKET_NAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{GCP_PROJECT_ID}}|$GCP_PROJECT_ID|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{PAYMENT_ENDPOINT}}|$PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{INVENTORY_ENDPOINT}}|$INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{NOTIFICATION_ENDPOINT}}|$NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"

echo ""
echo "=========================================="
echo "Deploying to GKE"
echo "=========================================="
echo ""

# Apply namespace
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

# Apply deployment
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

# Apply service
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

# Apply ingress
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout"
echo "=========================================="
echo ""

kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "WARNING: Deployment rollout did not complete successfully"
    echo "Check pod status with: kubectl get pods -n resortslite"
fi

echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="
echo ""

kubectl get pods,svc,ingress -n resortslite

echo ""
echo "=========================================="
echo "Deployment Completed!"
echo "=========================================="
echo ""
echo "Application Details:"
echo "  Namespace: resortslite"
echo "  Deployment: resortslite"
echo "  Service: resortslite-service"
echo "  Ingress: resortslite-ingress"
echo ""
echo "Useful Commands:"
echo "  View pods:        kubectl get pods -n resortslite"
echo "  View logs:        kubectl logs -f deployment/resortslite -n resortslite"
echo "  Describe pod:     kubectl describe pod <pod-name> -n resortslite"
echo "  Port forward:     kubectl port-forward svc/resortslite-service 8080:80 -n resortslite"
echo ""
echo "Access the application:"
echo "  Internal: http://resortslite-service.resortslite.svc.cluster.local"
echo "  External: Configure DNS for resortslite.example.com to point to ingress IP"
echo ""

# Cleanup temporary directory
rm -rf "$TEMP_DIR"
