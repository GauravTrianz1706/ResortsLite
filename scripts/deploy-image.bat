@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Deploy to GCP GKE Script (Windows)
REM For ResortsLite Spring Boot Application
REM ============================================

echo ==========================================
echo GKE Deployment Script
echo ==========================================
echo.

REM Prompt for GCP configuration
set /p GCP_PROJECT="Enter GCP Project ID: "
if "!GCP_PROJECT!"=="" (
    echo ERROR: GCP Project ID is required
    exit /b 1
)

set /p GCP_ZONE="Enter GCP Zone (e.g., us-central1-a): "
if "!GCP_ZONE!"=="" (
    echo ERROR: GCP Zone is required
    exit /b 1
)

set /p CLUSTER_NAME="Enter GKE Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: GKE Cluster Name is required
    exit /b 1
)

set /p IMAGE_URI="Enter Docker Image URI (with tag): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required
    exit /b 1
)

echo.
echo ==========================================
echo Application Configuration
echo ==========================================
echo Please provide configuration values (press Enter to use defaults):
echo.

REM Redis Configuration
set /p REDIS_HOST="Enter Redis Host (default: redis.example.com): "
if "!REDIS_HOST!"=="" set REDIS_HOST=redis.example.com

set /p REDIS_PORT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter Redis Password (press Enter if none): "

REM GCS Configuration
set /p GCS_BUCKET_NAME="Enter GCS Bucket Name (default: resortslite-reports): "
if "!GCS_BUCKET_NAME!"=="" set GCS_BUCKET_NAME=resortslite-reports

set /p GCP_PROJECT_ID="Enter GCP Project ID for GCS (default: !GCP_PROJECT!): "
if "!GCP_PROJECT_ID!"=="" set GCP_PROJECT_ID=!GCP_PROJECT!

REM External Service Endpoints
set /p PAYMENT_ENDPOINT="Enter Payment Service Endpoint (default: http://payment-svc:9090/charge): "
if "!PAYMENT_ENDPOINT!"=="" set PAYMENT_ENDPOINT=http://payment-svc:9090/charge

set /p INVENTORY_ENDPOINT="Enter Inventory Service Endpoint (default: http://inventory-svc:8081/rooms): "
if "!INVENTORY_ENDPOINT!"=="" set INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

set /p NOTIFICATION_ENDPOINT="Enter Notification Service Endpoint (default: http://notify-svc:7070/send): "
if "!NOTIFICATION_ENDPOINT!"=="" set NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

echo.
echo ==========================================
echo Configuring kubectl for GKE
echo ==========================================
echo.

gcloud container clusters get-credentials "!CLUSTER_NAME!" --zone "!GCP_ZONE!" --project "!GCP_PROJECT!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for GKE cluster
    exit /b 1
)

echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to connect to Kubernetes cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes Manifests
echo ==========================================
echo.

REM Create temporary directory for modified manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir "!TEMP_DIR!"
xcopy /E /I /Q kubernetes "!TEMP_DIR!"

REM Update deployment.yaml with actual values using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCS_BUCKET_NAME}}', '!GCS_BUCKET_NAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCP_PROJECT_ID}}', '!GCP_PROJECT_ID!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_ENDPOINT}}', '!PAYMENT_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{INVENTORY_ENDPOINT}}', '!INVENTORY_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{NOTIFICATION_ENDPOINT}}', '!NOTIFICATION_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated successfully

echo.
echo ==========================================
echo Deploying to GKE
echo ==========================================
echo.

REM Apply namespace
echo Creating namespace...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"

REM Apply deployment
echo Deploying application...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"

REM Apply service
echo Creating service...
kubectl apply -f "!TEMP_DIR!\service.yaml"

REM Apply ingress
echo Creating ingress...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"

echo.
echo ==========================================
echo Waiting for Deployment Rollout
echo ==========================================
echo.

kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo WARNING: Deployment rollout did not complete successfully
    echo Check pod status with: kubectl get pods -n resortslite
)

echo.
echo ==========================================
echo Deployment Status
echo ==========================================
echo.

kubectl get pods,svc,ingress -n resortslite

echo.
echo ==========================================
echo Deployment Completed!
echo ==========================================
echo.
echo Application Details:
echo   Namespace: resortslite
echo   Deployment: resortslite
echo   Service: resortslite-service
echo   Ingress: resortslite-ingress
echo.
echo Useful Commands:
echo   View pods:        kubectl get pods -n resortslite
echo   View logs:        kubectl logs -f deployment/resortslite -n resortslite
echo   Describe pod:     kubectl describe pod ^<pod-name^> -n resortslite
echo   Port forward:     kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
echo.
echo Access the application:
echo   Internal: http://resortslite-service.resortslite.svc.cluster.local
echo   External: Configure DNS for resortslite.example.com to point to ingress IP
echo.

REM Cleanup temporary directory
rmdir /S /Q "!TEMP_DIR!"

endlocal
