@echo off
setlocal enabledelayedexpansion

REM Build and Push Script for ResortsLite (Windows)
REM This script builds the Docker image and pushes it to the selected registry

echo ==========================================
echo ResortsLite - Docker Build and Push
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=ResortsLite

REM Sanitize project name for Docker tag (lowercase, hyphenate)
set IMAGE_NAME=resortslite

REM Prompt for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo.
echo Select Docker Registry:
echo 1. Google Artifact Registry
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo.
    echo === Google Artifact Registry Configuration ===
    
    REM Prompt for GCP details
    set /p GCP_PROJECT="Enter GCP Project ID: "
    set /p GCP_REGION="Enter GCP Region (e.g., us-central1): "
    set /p AR_REPO="Enter Artifact Registry Repository Name: "
    
    REM Authenticate with GCP
    echo.
    echo Authenticating with Google Cloud...
    call gcloud auth login
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: GCP authentication failed
        exit /b 1
    )
    
    REM Configure Docker for Artifact Registry
    echo Configuring Docker for Artifact Registry...
    call gcloud auth configure-docker !GCP_REGION!-docker.pkg.dev
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Artifact Registry Docker configuration failed
        exit /b 1
    )
    
    REM Build full image name
    set FULL_IMAGE_NAME=!GCP_REGION!-docker.pkg.dev/!GCP_PROJECT!/!AR_REPO!/!IMAGE_NAME!:!IMAGE_TAG!
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo.
    echo === Docker Hub Configuration ===
    
    REM Prompt for Docker Hub credentials
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    
    REM Authenticate with Docker Hub
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub authentication failed
        exit /b 1
    )
    
    REM Build full image name
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
    
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

echo.
echo ==========================================
echo Building Docker image...
echo Image: !FULL_IMAGE_NAME!
echo ==========================================

REM Build Docker image
docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo ==========================================
echo Pushing Docker image to registry...
echo ==========================================

REM Push Docker image
docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.
echo Use this image URI for deployment:
echo !FULL_IMAGE_NAME!
echo ==========================================

endlocal
