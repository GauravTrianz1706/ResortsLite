@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: deploy-image.bat — Deploy ResortsLite to AWS EKS (Windows)
:: Usage: scripts\deploy-image.bat
:: Run from the repository root directory
:: ============================================================

set APP_NAME=resortslite
set NAMESPACE=resortslite
set MANIFESTS_DIR=kubernetes

echo ==============================================
echo   ResortsLite — AWS EKS Deployment Script
echo ==============================================
echo.

:: ── AWS / EKS configuration ──────────────────────────────────
set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Application Environment Variables ---
echo Press Enter to skip any optional variable.
echo.

set /p REDIS_HOST="Enter REDIS_HOST (ElastiCache primary endpoint) [localhost]: "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p PAYMENT_API_URL="Enter PAYMENT_API_URL [http://payment-service:9090/payments/charge]: "
if "!PAYMENT_API_URL!"=="" set PAYMENT_API_URL=http://payment-service:9090/payments/charge

echo.
echo --- Deployment Summary ---
echo   AWS Region   : !AWS_REGION!
echo   EKS Cluster  : !CLUSTER_NAME!
echo   Image URI    : !IMAGE_URI!
echo   Namespace    : !NAMESPACE!
echo   REDIS_HOST   : !REDIS_HOST!
echo   REDIS_PORT   : !REDIS_PORT!
echo.

set /p CONFIRM="Proceed with deployment? [y/N]: "
if /i not "!CONFIRM!"=="y" (
    echo Deployment cancelled.
    exit /b 0
)

:: ── Configure kubectl ────────────────────────────────────────
echo.
echo Configuring kubectl for EKS cluster '!CLUSTER_NAME!'...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl.
    exit /b 1
)
echo kubectl configured.

:: ── Verify cluster connectivity ──────────────────────────────
echo.
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

:: ── Patch manifests with runtime values ──────────────────────
echo.
echo Patching Kubernetes manifests...

copy /Y "!MANIFESTS_DIR!\deployment.yaml" "%TEMP%\deployment-patched.yaml" >nul

powershell -NoProfile -Command ^
  "(Get-Content '%TEMP%\deployment-patched.yaml') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
   -replace '{{PAYMENT_API_URL}}','!PAYMENT_API_URL!' ^
   | Set-Content '%TEMP%\deployment-patched.yaml'"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to patch deployment manifest.
    exit /b 1
)
echo Manifests patched.

:: ── Apply manifests ──────────────────────────────────────────
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f "!MANIFESTS_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo   [2/4] Applying deployment...
kubectl apply -f "%TEMP%\deployment-patched.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo   [3/4] Applying service...
kubectl apply -f "!MANIFESTS_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo   [4/4] Applying ingress...
kubectl apply -f "!MANIFESTS_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

echo All manifests applied.

:: ── Wait for rollout ─────────────────────────────────────────
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed or timed out.
    exit /b 1
)
echo Deployment rollout complete.

:: ── Verify resources ─────────────────────────────────────────
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ==============================================
echo   Deployment Complete!
echo   Namespace : !NAMESPACE!
echo   Image     : !IMAGE_URI!
echo ==============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
exit /b 0
