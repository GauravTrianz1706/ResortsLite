# ResortsLite - Cloud-Ready Application

## Overview
This application has been modernized for Azure cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File System & Local Storage Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Solution**: Migrated to Azure Blob Storage
- Replaced `/var/legacy/reports/` and `C:\\ResortBackups\\nightly\\` with Azure Blob Storage
- Implemented `AzureBlobStorageService` for cloud-native file operations
- Updated `ReportService` to use Azure Blob Storage for report generation and storage
- All file operations now use cloud storage with proper fallback for development

**Files Modified**:
- `ReportService.java` - Replaced File I/O with Azure Blob Storage
- `AzureBlobStorageService.java` - New service for blob operations
- `pom.xml` - Added `azure-storage-blob` dependency

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Solution**: Migrated to Azure Key Vault
- Removed hard-coded `DB_HOST`, `DB_USER`, and `DB_PASS` constants
- Implemented Azure Key Vault integration using `SecretClient`
- Added `DefaultAzureCredential` for secure authentication
- Database credentials now retrieved from Key Vault at runtime

**Files Modified**:
- `BookingService.java` - Integrated Azure Key Vault for secrets
- `pom.xml` - Added `azure-security-keyvault-secrets` and `azure-identity` dependencies

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Hard-coded service endpoints in source code
**Solution**: Externalized to Azure App Configuration
- Replaced hard-coded `http://inventory-service.internal:8081/rooms/available` with environment variables
- Updated `BookingController` to use `@Value` annotations for endpoint configuration
- All service URLs now configurable via `application.properties` or environment variables

**Files Modified**:
- `BookingController.java` - Externalized inventory service endpoint
- `application.properties` - Added configurable service endpoints

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Hard-coded port 8080 preventing dynamic assignment
**Solution**: Externalized port configuration
- Replaced `SERVER_PORT = 8080` constant with `@Value("${server.port:8080}")`
- Updated `application.properties` to use `${PORT:8080}` for dynamic port assignment
- Application now supports container orchestration platform port assignment

**Files Modified**:
- `ReportService.java` - Externalized server port
- `application.properties` - Dynamic port configuration

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: In-memory session storage preventing horizontal scaling
**Solution**: Migrated to Azure Cache for Redis
- Replaced HTTP session storage with Redis-backed distributed sessions
- Implemented `RedisConfig` for Azure Cache for Redis integration
- Added Spring Session Data Redis for automatic session externalization
- Session data now persisted in Redis with TTL policies

**Files Modified**:
- `BookingController.java` - Integrated Redis for session management
- `RedisConfig.java` - New configuration for distributed sessions
- `pom.xml` - Added Spring Session and Redis dependencies

### 6. File-based Authentication (Blocker 17)
**Issue**: Local file-based credential storage
**Solution**: Migrated to Azure Active Directory
- Implemented Azure AD integration with Spring Security
- Created `AzureSecurityConfig` for OAuth2/OIDC authentication
- Added `authenticateUser` method using Azure Key Vault for password verification
- Replaced weak MD5 hashing with secure SHA-256

**Files Modified**:
- `BookingService.java` - Added Azure AD authentication support
- `AzureSecurityConfig.java` - New security configuration
- `pom.xml` - Added `azure-spring-boot-starter-active-directory` dependency

### 7. In-Memory Caching Without TTL (Blocker 18)
**Issue**: Static HashMap cache causing memory growth
**Solution**: Replaced with Azure Cache for Redis
- Removed static `bookingCache` HashMap
- Implemented Redis-based caching with TTL policies
- Cache now distributed across instances with automatic expiration

**Files Modified**:
- `BookingController.java` - Removed in-memory cache
- `RedisConfig.java` - Configured distributed caching

## Azure Services Integration

### Required Azure Services
1. **Azure Blob Storage** - For file storage and reports
2. **Azure Key Vault** - For secrets management
3. **Azure Cache for Redis** - For distributed sessions and caching
4. **Azure Active Directory** - For authentication and authorization
5. **Azure App Configuration** (Optional) - For centralized configuration

### Environment Variables Required

```bash
# Server Configuration
PORT=8080

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://your-db.postgres.database.azure.com:5432/resortdb
SPRING_DATASOURCE_USERNAME=dbadmin
SPRING_DATASOURCE_PASSWORD=<from-key-vault>

# Azure Blob Storage
AZURE_STORAGE_ACCOUNT_NAME=yourstorageaccount
AZURE_STORAGE_ACCOUNT_KEY=<your-storage-key>
AZURE_STORAGE_BLOB_ENDPOINT=https://yourstorageaccount.blob.core.windows.net
AZURE_STORAGE_CONTAINER_NAME=resort-reports

# Azure Key Vault
AZURE_KEYVAULT_URI=https://your-keyvault.vault.azure.net/
AZURE_TENANT_ID=<your-tenant-id>
AZURE_CLIENT_ID=<your-client-id>
AZURE_CLIENT_SECRET=<your-client-secret>

# Azure Redis Cache
AZURE_REDIS_HOST=your-redis.redis.cache.windows.net
AZURE_REDIS_PORT=6380
AZURE_REDIS_PASSWORD=<your-redis-key>
AZURE_REDIS_SSL=true

# Azure Active Directory
AZURE_AD_TENANT_ID=<your-tenant-id>
AZURE_AD_CLIENT_ID=<your-app-client-id>
AZURE_AD_CLIENT_SECRET=<your-app-secret>

# Service Endpoints
PAYMENT_SERVICE_ENDPOINT=https://payment-svc.azurewebsites.net/charge
INVENTORY_SERVICE_ENDPOINT=https://inventory-svc.azurewebsites.net/rooms
NOTIFICATION_SERVICE_ENDPOINT=https://notify-svc.azurewebsites.net/send
```

## Deployment Instructions

### 1. Azure Resource Setup
```bash
# Create Resource Group
az group create --name resortslite-rg --location eastus

# Create Storage Account
az storage account create --name resortslitestorage --resource-group resortslite-rg --location eastus --sku Standard_LRS

# Create Key Vault
az keyvault create --name resortslite-kv --resource-group resortslite-rg --location eastus

# Create Redis Cache
az redis create --name resortslite-redis --resource-group resortslite-rg --location eastus --sku Basic --vm-size c0

# Create App Service
az appservice plan create --name resortslite-plan --resource-group resortslite-rg --sku B1 --is-linux
az webapp create --name resortslite-app --resource-group resortslite-rg --plan resortslite-plan --runtime "JAVA|8-jre8"
```

### 2. Configure Managed Identity
```bash
# Enable system-assigned managed identity
az webapp identity assign --name resortslite-app --resource-group resortslite-rg

# Grant Key Vault access
az keyvault set-policy --name resortslite-kv --object-id <managed-identity-id> --secret-permissions get list
```

### 3. Deploy Application
```bash
# Build application
mvn clean package

# Deploy to Azure App Service
az webapp deploy --name resortslite-app --resource-group resortslite-rg --src-path target/resortsLite-1.0.0.jar
```

## 12-Factor App Compliance

✅ **I. Codebase** - Single codebase tracked in version control
✅ **II. Dependencies** - Explicitly declared in pom.xml
✅ **III. Config** - Externalized to environment variables
✅ **IV. Backing Services** - Azure services treated as attached resources
✅ **V. Build, Release, Run** - Strict separation maintained
✅ **VI. Processes** - Stateless with externalized session storage
✅ **VII. Port Binding** - Dynamic port assignment supported
✅ **VIII. Concurrency** - Horizontal scaling enabled
✅ **IX. Disposability** - Fast startup and graceful shutdown
✅ **X. Dev/Prod Parity** - Same Azure services across environments
✅ **XI. Logs** - Stdout logging for Azure Monitor integration
✅ **XII. Admin Processes** - Separate management tasks

## Testing

### Local Development
For local development without Azure services:
```bash
# Use H2 in-memory database
# Redis and Azure services will gracefully fallback
mvn spring-boot:run
```

### With Azure Services
```bash
# Set environment variables
export AZURE_STORAGE_ACCOUNT_NAME=yourstorageaccount
export AZURE_KEYVAULT_URI=https://your-keyvault.vault.azure.net/
export AZURE_REDIS_HOST=your-redis.redis.cache.windows.net

# Run application
mvn spring-boot:run
```

## Security Improvements
- ✅ Removed hard-coded credentials
- ✅ Integrated Azure Key Vault for secrets
- ✅ Replaced MD5 with SHA-256 hashing
- ✅ Added Azure AD authentication support
- ✅ Implemented parameterized SQL queries
- ✅ Enabled SSL for Redis connections

## Monitoring and Observability
- Application logs to stdout for Azure Monitor integration
- Redis session metrics available in Azure Portal
- Blob Storage metrics tracked automatically
- Key Vault access logs enabled

## Next Steps
1. Configure Azure Application Insights for APM
2. Set up Azure Monitor alerts
3. Implement Azure Front Door for global distribution
4. Enable Azure CDN for static content
5. Configure Azure Backup for data protection
