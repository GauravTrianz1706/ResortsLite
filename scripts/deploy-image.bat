@echo off
setlocal enabledelayedexpansion

REM Deploy to GCP GKE Script for ResortsLite (Windows)
REM This script deploys the application to Google Kubernetes Engine

echo ==========================================
echo ResortsLite - GKE Deployment
echo ==========================================
echo.

REM Prompt for GCP configuration
set /p GCP_PROJECT="Enter GCP Project ID: "
set /p GCP_ZONE="Enter GCP Zone (e.g., us-central1-a): "
set /p CLUSTER_NAME="Enter GKE Cluster Name: "

REM Validate inputs
if "!GCP_PROJECT!"=="" (
    echo ERROR: GCP Project ID is required
    exit /b 1
)
if "!GCP_ZONE!"=="" (
    echo ERROR: GCP Zone is required
    exit /b 1
)
if "!CLUSTER_NAME!"=="" (
    echo ERROR: GKE Cluster Name is required
    exit /b 1
)

REM Prompt for Docker image URI
echo.
set /p IMAGE_URI="Enter Docker Image URI (with tag): "

if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required
    exit /b 1
)

REM Prompt for environment variables
echo.
echo === Application Configuration ===
echo Enter values for environment variables (press Enter to use defaults)
echo.

set /p DB_URL="Database URL (default: jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1): "
if "!DB_URL!"=="" set DB_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1

set /p DB_USERNAME="Database Username (default: sa): "
if "!DB_USERNAME!"=="" set DB_USERNAME=sa

set /p DB_PASSWORD="Database Password (default: empty): "
if "!DB_PASSWORD!"=="" set DB_PASSWORD=

set /p REDIS_HOST="Redis Host (default: redis-service): "
if "!REDIS_HOST!"=="" set REDIS_HOST=redis-service

set /p REDIS_PORT="Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Redis Password (default: empty): "
if "!REDIS_PASSWORD!"=="" set REDIS_PASSWORD=

set /p PAYMENT_SERVICE_URL="Payment Service URL (default: http://payment-svc:9090/charge): "
if "!PAYMENT_SERVICE_URL!"=="" set PAYMENT_SERVICE_URL=http://payment-svc:9090/charge

set /p INVENTORY_SERVICE_URL="Inventory Service URL (default: http://inventory-svc:8081/rooms): "
if "!INVENTORY_SERVICE_URL!"=="" set INVENTORY_SERVICE_URL=http://inventory-svc:8081/rooms

set /p NOTIFICATION_SERVICE_URL="Notification Service URL (default: http://notify-svc:7070/send): "
if "!NOTIFICATION_SERVICE_URL!"=="" set NOTIFICATION_SERVICE_URL=http://notify-svc:7070/send

set /p GCP_STORAGE_PROJECT="GCP Project ID for Storage (default: !GCP_PROJECT!): "
if "!GCP_STORAGE_PROJECT!"=="" set GCP_STORAGE_PROJECT=!GCP_PROJECT!

set /p GCS_BUCKET_NAME="GCS Bucket Name (default: resortslite-reports): "
if "!GCS_BUCKET_NAME!"=="" set GCS_BUCKET_NAME=resortslite-reports

echo.
echo ==========================================
echo Configuring kubectl for GKE cluster...
echo ==========================================

REM Configure kubectl to use the GKE cluster
call gcloud container clusters get-credentials "!CLUSTER_NAME!" --zone "!GCP_ZONE!" --project "!GCP_PROJECT!"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for GKE cluster
    exit /b 1
)

REM Verify cluster connectivity
echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to connect to Kubernetes cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes manifests...
echo ==========================================

REM Create temporary directory for processed manifests
set TEMP_DIR=%TEMP%\resortslite-deploy-%RANDOM%
mkdir "!TEMP_DIR!"
xcopy /E /I /Q kubernetes "!TEMP_DIR!"

REM Update manifests with actual values using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_URL}}', '!DB_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_USERNAME}}', '!DB_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{INVENTORY_SERVICE_URL}}', '!INVENTORY_SERVICE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{NOTIFICATION_SERVICE_URL}}', '!NOTIFICATION_SERVICE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCP_PROJECT_ID}}', '!GCP_STORAGE_PROJECT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GCS_BUCKET_NAME}}', '!GCS_BUCKET_NAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo.
echo ==========================================
echo Applying Kubernetes manifests...
echo ==========================================

REM Apply namespace
echo Creating namespace...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"

REM Apply deployment
echo Creating deployment...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"

REM Apply service
echo Creating service...
kubectl apply -f "!TEMP_DIR!\service.yaml"

REM Apply ingress
echo Creating ingress...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"

echo.
echo ==========================================
echo Waiting for deployment to complete...
echo ==========================================

REM Wait for deployment rollout
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo.
    echo Checking pod status...
    kubectl get pods -n resortslite
    echo.
    echo Checking pod logs...
    kubectl logs -n resortslite -l app=resortslite --tail=50
    
    REM Cleanup temp directory
    rmdir /S /Q "!TEMP_DIR!"
    exit /b 1
)

echo.
echo ==========================================
echo Verifying deployment...
echo ==========================================

REM Display deployment status
kubectl get pods,svc,ingress -n resortslite

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo Application deployed successfully to GKE
echo.
echo Cluster: !CLUSTER_NAME!
echo Namespace: resortslite
echo Image: !IMAGE_URI!
echo.
echo To access the application:
echo 1. Get the ingress IP: kubectl get ingress -n resortslite
echo 2. Access via: http://^<INGRESS_IP^>/
echo 3. Health check: http://^<INGRESS_IP^>/actuator/health
echo.
echo To view logs:
echo kubectl logs -n resortslite -l app=resortslite -f
echo.
echo To scale the deployment:
echo kubectl scale deployment/resortslite -n resortslite --replicas=3
echo ==========================================

REM Cleanup temp directory
rmdir /S /Q "!TEMP_DIR!"

endlocal
