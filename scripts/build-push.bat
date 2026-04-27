@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Build and Push Docker Image Script (Windows)
REM For ResortsLite Spring Boot Application
REM ============================================

echo ==========================================
echo Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=resortslite
set IMAGE_NAME=resortslite

REM Prompt for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo.
echo Select Docker Registry:
echo 1. Google Artifact Registry (GCP)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo.
    echo ==========================================
    echo Google Artifact Registry Configuration
    echo ==========================================
    
    set /p GCP_PROJECT="Enter GCP Project ID: "
    set /p GCP_REGION="Enter GCP Region (e.g., us-central1): "
    set /p ARTIFACT_REPO="Enter Artifact Registry Repository Name: "
    
    set FULL_IMAGE_NAME=!GCP_REGION!-docker.pkg.dev/!GCP_PROJECT!/!ARTIFACT_REPO!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Authenticating with Google Cloud...
    gcloud auth login
    
    echo.
    echo Configuring Docker for Artifact Registry...
    gcloud auth configure-docker !GCP_REGION!-docker.pkg.dev
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Artifact Registry authentication failed
        exit /b 1
    )
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo.
    echo ==========================================
    echo Docker Hub Configuration
    echo ==========================================
    
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo.
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub authentication failed
        exit /b 1
    )
    
) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

echo.
echo ==========================================
echo Building Docker Image
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.

docker build -t "!FULL_IMAGE_NAME!" .

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed
    exit /b 1
)

echo.
echo ==========================================
echo Pushing Docker Image
echo ==========================================
echo.

docker push "!FULL_IMAGE_NAME!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo Build and Push Completed Successfully!
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Use this image URI in your Kubernetes deployment
echo 2. Run deploy-image.bat to deploy to GKE
echo.

endlocal
