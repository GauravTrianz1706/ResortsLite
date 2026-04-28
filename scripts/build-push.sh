#!/bin/bash
set -e

# Build and Push Script for ResortsLite
# This script builds the Docker image and pushes it to the selected registry

echo "=========================================="
echo "ResortsLite - Docker Build and Push"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="ResortsLite"

# Sanitize project name for Docker tag (lowercase, hyphenate)
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Prompt for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Sanitize tag
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9.-' '-' | sed 's/^-*//;s/-*$//')

echo ""
echo "Select Docker Registry:"
echo "1. Google Artifact Registry"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    echo ""
    echo "=== Google Artifact Registry Configuration ==="
    
    # Prompt for GCP details
    read -p "Enter GCP Project ID: " GCP_PROJECT
    read -p "Enter GCP Region (e.g., us-central1): " GCP_REGION
    read -p "Enter Artifact Registry Repository Name: " AR_REPO
    
    # Authenticate with GCP
    echo ""
    echo "Authenticating with Google Cloud..."
    gcloud auth login
    
    if [ $? -ne 0 ]; then
        echo "ERROR: GCP authentication failed"
        exit 1
    fi
    
    # Configure Docker for Artifact Registry
    echo "Configuring Docker for Artifact Registry..."
    gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Artifact Registry Docker configuration failed"
        exit 1
    fi
    
    # Build full image name
    FULL_IMAGE_NAME="${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${AR_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    echo ""
    echo "=== Docker Hub Configuration ==="
    
    # Prompt for Docker Hub credentials
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub password/token: " DOCKER_PASSWORD
    echo ""
    
    # Authenticate with Docker Hub
    echo "Authenticating with Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker Hub authentication failed"
        exit 1
    fi
    
    # Build full image name
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    
else
    echo "ERROR: Invalid choice. Please select 1 or 2."
    exit 1
fi

echo ""
echo "=========================================="
echo "Building Docker image..."
echo "Image: $FULL_IMAGE_NAME"
echo "=========================================="

# Build Docker image
docker build -t "$FULL_IMAGE_NAME" .

if [ $? -ne 0 ]; then
    echo "ERROR: Docker build failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Pushing Docker image to registry..."
echo "=========================================="

# Push Docker image
docker push "$FULL_IMAGE_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Docker push failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "SUCCESS!"
echo "=========================================="
echo "Image: $FULL_IMAGE_NAME"
echo ""
echo "Use this image URI for deployment:"
echo "$FULL_IMAGE_NAME"
echo "=========================================="
