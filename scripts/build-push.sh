#!/bin/bash
set -e

# ============================================
# Build and Push Docker Image Script
# For ResortsLite Spring Boot Application
# ============================================

echo "=========================================="
echo "Docker Build and Push Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="resortslite"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')

echo ""
echo "Select Docker Registry:"
echo "1. Google Artifact Registry (GCP)"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    echo ""
    echo "=========================================="
    echo "Google Artifact Registry Configuration"
    echo "=========================================="
    
    read -p "Enter GCP Project ID: " GCP_PROJECT
    read -p "Enter GCP Region (e.g., us-central1): " GCP_REGION
    read -p "Enter Artifact Registry Repository Name: " ARTIFACT_REPO
    
    FULL_IMAGE_NAME="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Authenticating with Google Cloud..."
    gcloud auth login
    
    echo ""
    echo "Configuring Docker for Artifact Registry..."
    gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Artifact Registry authentication failed"
        exit 1
    fi
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    echo ""
    echo "=========================================="
    echo "Docker Hub Configuration"
    echo "=========================================="
    
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub password/token: " DOCKER_PASSWORD
    echo ""
    
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "Authenticating with Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker Hub authentication failed"
        exit 1
    fi
    
else
    echo "ERROR: Invalid choice. Please select 1 or 2."
    exit 1
fi

echo ""
echo "=========================================="
echo "Building Docker Image"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""

docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Pushing Docker Image"
echo "=========================================="
echo ""

docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Build and Push Completed Successfully!"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""
echo "Next steps:"
echo "1. Use this image URI in your Kubernetes deployment"
echo "2. Run deploy-image.sh to deploy to GKE"
echo ""
