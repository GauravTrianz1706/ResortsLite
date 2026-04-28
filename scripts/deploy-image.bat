@echo off
setlocal enabledelayedexpansion

REM Deploy to AWS ECS Fargate Script for ResortsLite Application (Windows)
REM This script deploys the Docker image to AWS ECS Fargate

echo ==========================================
echo ResortsLite - AWS ECS Fargate Deployment
echo ==========================================
echo.

REM Prompt for AWS configuration
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter ECS Cluster Name: "
set /p VPC_ID="Enter VPC ID: "
set /p SUBNETS_INPUT="Enter Subnet IDs (comma-separated, at least 2): "
set /p SECURITY_GROUP="Enter Security Group ID: "
set /p IMAGE_URI="Enter Docker Image URI: "

REM Parse subnets
for /f "tokens=1,2 delims=," %%a in ("!SUBNETS_INPUT!") do (
    set SUBNET_1=%%a
    set SUBNET_2=%%b
)
set SUBNET_1=!SUBNET_1: =!
set SUBNET_2=!SUBNET_2: =!

echo.
echo Configuration Summary:
echo   Region: !AWS_REGION!
echo   Cluster: !CLUSTER_NAME!
echo   VPC: !VPC_ID!
echo   Subnets: !SUBNET_1!, !SUBNET_2!
echo   Security Group: !SECURITY_GROUP!
echo   Image: !IMAGE_URI!
echo.

REM Get AWS Account ID
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!
echo.

REM Check if ECS cluster exists, create if not
echo Checking if ECS cluster exists...
aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Cluster does not exist. Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS cluster
        exit /b 1
    )
    echo ECS cluster created successfully
)
echo.

REM Prompt for Redis configuration
echo === Redis Configuration ===
set /p REDIS_HOST="Enter Redis Host: "
set /p REDIS_PORT="Enter Redis Port (default 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379
set /p REDIS_PASSWORD="Enter Redis Password (leave empty if none): "
echo.

REM Prompt for S3 bucket names
echo === S3 Configuration ===
set /p S3_REPORTS_BUCKET="Enter S3 Reports Bucket Name: "
set /p S3_BACKUPS_BUCKET="Enter S3 Backups Bucket Name: "
echo.

REM Prompt for external service URLs
echo === External Service Configuration ===
set /p PAYMENT_SERVICE_URL="Enter Payment Service URL: "
set /p INVENTORY_SERVICE_URL="Enter Inventory Service URL: "
set /p NOTIFICATION_SERVICE_URL="Enter Notification Service URL: "
echo.

REM Prompt for load balancer
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "

if /i "!NEED_LB!"=="y" (
    echo.
    echo === Creating Application Load Balancer ===
    
    set ALB_NAME=resortslite-alb
    echo Creating Application Load Balancer: !ALB_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name "!ALB_NAME!" --subnets "!SUBNET_1!" "!SUBNET_2!" --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    
    echo ALB created: !ALB_ARN!
    
    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i
    
    echo ALB DNS: !ALB_DNS!
    echo.
    
    set TG_NAME=resortslite-tg
    echo Creating Target Group: !TG_NAME!
    
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name "!TG_NAME!" --protocol HTTP --port 8080 --vpc-id "!VPC_ID!" --target-type ip --health-check-enabled --health-check-protocol HTTP --health-check-path "/actuator/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 5 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    
    echo Target Group created: !TARGET_GROUP_ARN!
    echo.
    
    echo Creating ALB Listener...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn="!TARGET_GROUP_ARN!" --region "!AWS_REGION!" >nul
    
    echo ALB Listener created
    echo.
    
    set LOAD_BALANCER_CONFIG=true
) else (
    echo Skipping load balancer creation
    set LOAD_BALANCER_CONFIG=false
    set TARGET_GROUP_ARN=
)

echo.
echo === Preparing ECS Task Definition ===

REM Create temporary task definition
set TEMP_TASK_DEF=%TEMP%\task-def-%RANDOM%.json
copy ecs\task-definition.json "!TEMP_TASK_DEF!" >nul

REM Replace placeholders using PowerShell
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{ACCOUNT_ID}}', '!ACCOUNT_ID!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{AWS_REGION}}', '!AWS_REGION!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{S3_REPORTS_BUCKET}}', '!S3_REPORTS_BUCKET!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{S3_BACKUPS_BUCKET}}', '!S3_BACKUPS_BUCKET!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{INVENTORY_SERVICE_URL}}', '!INVENTORY_SERVICE_URL!' | Set-Content '!TEMP_TASK_DEF!'"
powershell -Command "(Get-Content '!TEMP_TASK_DEF!') -replace '{{NOTIFICATION_SERVICE_URL}}', '!NOTIFICATION_SERVICE_URL!' | Set-Content '!TEMP_TASK_DEF!'"

echo Task definition prepared
echo.

REM Create CloudWatch log group
echo Creating CloudWatch log group...
aws logs create-log-group --log-group-name "/ecs/resortslite" --region "!AWS_REGION!" 2>nul
echo.

REM Register task definition
echo Registering ECS task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json file://!TEMP_TASK_DEF! --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to register task definition
    del "!TEMP_TASK_DEF!"
    exit /b 1
)

echo Task definition registered: !TASK_DEF_ARN!
echo.

REM Clean up temporary file
del "!TEMP_TASK_DEF!"

REM Prepare service definition
echo === Preparing ECS Service Definition ===

set TEMP_SERVICE_DEF=%TEMP%\service-def-%RANDOM%.json
copy ecs\service-definition.json "!TEMP_SERVICE_DEF!" >nul

REM Replace placeholders
powershell -Command "(Get-Content '!TEMP_SERVICE_DEF!') -replace '{{CLUSTER_NAME}}', '!CLUSTER_NAME!' | Set-Content '!TEMP_SERVICE_DEF!'"
powershell -Command "(Get-Content '!TEMP_SERVICE_DEF!') -replace '{{SUBNET_1}}', '!SUBNET_1!' | Set-Content '!TEMP_SERVICE_DEF!'"
powershell -Command "(Get-Content '!TEMP_SERVICE_DEF!') -replace '{{SUBNET_2}}', '!SUBNET_2!' | Set-Content '!TEMP_SERVICE_DEF!'"
powershell -Command "(Get-Content '!TEMP_SERVICE_DEF!') -replace '{{SECURITY_GROUP}}', '!SECURITY_GROUP!' | Set-Content '!TEMP_SERVICE_DEF!'"
powershell -Command "(Get-Content '!TEMP_SERVICE_DEF!') -replace '{{TARGET_GROUP_ARN}}', '!TARGET_GROUP_ARN!' | Set-Content '!TEMP_SERVICE_DEF!'"

REM Remove load balancer section if not needed
if "!LOAD_BALANCER_CONFIG!"=="false" (
    powershell -Command "$json = Get-Content '!TEMP_SERVICE_DEF!' | ConvertFrom-Json; $json.PSObject.Properties.Remove('loadBalancers'); $json.PSObject.Properties.Remove('healthCheckGracePeriodSeconds'); $json | ConvertTo-Json -Depth 10 | Set-Content '!TEMP_SERVICE_DEF!'"
)

echo Service definition prepared
echo.

REM Check if service exists
echo Checking if ECS service exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "resortslite-service" --region "!AWS_REGION!" --query "services[?status==`ACTIVE`].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%i

if "!EXISTING_SERVICE!"=="" (
    echo Service does not exist. Creating new ECS service...
    
    aws ecs create-service --cli-input-json file://!TEMP_SERVICE_DEF! --region "!AWS_REGION!" >nul
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create ECS service
        del "!TEMP_SERVICE_DEF!"
        exit /b 1
    )
    
    echo ECS service created successfully
) else (
    echo Service exists. Updating ECS service...
    
    aws ecs update-service --cluster "!CLUSTER_NAME!" --service "resortslite-service" --task-definition "!TASK_DEF_ARN!" --desired-count 2 --force-new-deployment --region "!AWS_REGION!" >nul
    
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to update ECS service
        del "!TEMP_SERVICE_DEF!"
        exit /b 1
    )
    
    echo ECS service updated successfully
)

echo.

REM Clean up temporary file
del "!TEMP_SERVICE_DEF!"

REM Wait for service to stabilize
echo Waiting for service to stabilize (this may take a few minutes)...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "resortslite-service" --region "!AWS_REGION!"

echo.
echo ==========================================
echo DEPLOYMENT SUCCESSFUL!
echo ==========================================
echo.

REM Display service information
echo Service Details:
aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "resortslite-service" --region "!AWS_REGION!" --query "services[0].[serviceName,status,runningCount,desiredCount]" --output table

echo.
echo CloudWatch Logs:
echo   Log Group: /ecs/resortslite
echo   Region: !AWS_REGION!
echo.

if "!LOAD_BALANCER_CONFIG!"=="true" (
    echo Application Load Balancer:
    echo   DNS Name: !ALB_DNS!
    echo   Access your application at: http://!ALB_DNS!
    echo.
)

echo To view logs:
echo   aws logs tail /ecs/resortslite --follow --region !AWS_REGION!
echo.
echo To check service status:
echo   aws ecs describe-services --cluster !CLUSTER_NAME! --services resortslite-service --region !AWS_REGION!
echo.
echo ==========================================

endlocal
