@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: build-push.bat — Build and push ResortsLite Docker image
:: Usage: scripts\build-push.bat
:: Run from the repository root directory
:: ============================================================

set PROJECT_NAME=resortslite
set DOCKERFILE_PATH=Dockerfile

echo ==============================================
echo   ResortsLite — Docker Build ^& Push Script
echo ==============================================
echo.

:: ── Sanitize image name ──────────────────────────────────────
:: Use PowerShell for tag sanitization
for /f "delims=" %%i in ('powershell -NoProfile -Command "$n = '%PROJECT_NAME%'.ToLower() -replace '[^a-z0-9]','-'; $n = $n.Trim('-'); if ($n -eq '') { $n = 'app' }; Write-Output $n"') do set IMAGE_NAME=%%i

:: ── Prompt for image tag ─────────────────────────────────────
set /p RAW_TAG="Enter image tag [latest]: "
if "!RAW_TAG!"=="" set RAW_TAG=latest

for /f "delims=" %%i in ('powershell -NoProfile -Command "$t = '!RAW_TAG!'.ToLower() -replace '[^a-z0-9._-]','-'; $t = $t.Trim('-'); if ($t -eq '') { $t = 'latest' }; Write-Output $t"') do set IMAGE_TAG=%%i
echo Using image tag: !IMAGE_TAG!
echo.

:: ── Registry selection ───────────────────────────────────────
echo Select container registry:
echo   1) AWS ECR
echo   2) Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1]: "
if "!REGISTRY_CHOICE!"=="" set REGISTRY_CHOICE=1

if "!REGISTRY_CHOICE!"=="1" goto :ecr_setup
if "!REGISTRY_CHOICE!"=="2" goto :dockerhub_setup
echo ERROR: Invalid registry choice '!REGISTRY_CHOICE!'.
exit /b 1

:: ── AWS ECR ──────────────────────────────────────────────────
:ecr_setup
echo.
echo --- AWS ECR Configuration ---
set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
if "!AWS_ACCOUNT_ID!"=="" (
    echo ERROR: AWS Account ID is required.
    exit /b 1
)

set ECR_REPO=!IMAGE_NAME!
set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

echo.
echo Authenticating with AWS ECR...
aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
if !ERRORLEVEL! neq 0 (
    echo ERROR: ECR login failed.
    exit /b 1
)
echo ECR login successful.

echo Checking ECR repository '!ECR_REPO!'...
aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository '!ECR_REPO!'...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECR repository.
        exit /b 1
    )
)
echo ECR repository ready.
goto :build

:: ── Docker Hub ───────────────────────────────────────────────
:dockerhub_setup
echo.
echo --- Docker Hub Configuration ---
set /p DOCKER_USERNAME="Enter Docker Hub username: "
if "!DOCKER_USERNAME!"=="" (
    echo ERROR: Docker Hub username is required.
    exit /b 1
)

set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
if "!DOCKER_PASSWORD!"=="" (
    echo ERROR: Docker Hub password is required.
    exit /b 1
)

set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!

echo Authenticating with Docker Hub...
echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker Hub login failed.
    exit /b 1
)
echo Docker Hub login successful.
goto :build

:: ── Build ────────────────────────────────────────────────────
:build
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
echo Build context: . (repository root)
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)
echo Docker build successful.

:: ── Push ─────────────────────────────────────────────────────
echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)
echo Image pushed successfully.

echo.
echo ==============================================
echo   Build ^& Push Complete
echo   Image: !FULL_IMAGE_NAME!
echo ==============================================

endlocal
exit /b 0
