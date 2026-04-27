# ResortsLite - Cloud-Native Azure Migration

## Overview
This application has been migrated to be fully cloud-ready for Azure deployment. All cloud readiness blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (Blockers 1-7)
**Issue**: Hard-coded file paths and local file system operations
**Solution**: Migrated to Azure Blob Storage
- Replaced all `java.io.File` operations with Azure Blob Storage SDK
- Reports are now stored in Azure Blob Storage containers
- No dependency on local file system paths
- Files persist across container restarts and scaling events

### 2. Hard-coded Database Credentials (Blockers 8-9)
**Issue**: Database credentials embedded in source code
**Solution**: Migrated to Azure Key Vault
- All credentials now retrieved from Azure Key Vault at runtime
- Supports automatic credential rotation
- Uses DefaultAzureCredential for authentication (supports Managed Identity)
- Fallback to environment variables for local development

### 3. Hard-coded Environment URLs (Blocker 10)
**Issue**: Environment-specific URLs hard-coded in source
**Solution**: Externalized to Azure App Configuration
- All service endpoints now configurable via environment variables
- Supports Azure App Configuration for centralized configuration
- Environment-agnostic deployment

### 4. Hard-coded Ports (Blocker 11)
**Issue**: Fixed port numbers preventing dynamic assignment
**Solution**: Dynamic port configuration
- Server port now reads from `PORT` environment variable
- Compatible with Azure Container Apps and AKS dynamic port assignment
- Defaults to 8080 for local development

### 5. HTTP Session State Storage (Blockers 12-16)
**Issue**: In-memory session storage preventing horizontal scaling
**Solution**: Migrated to Azure Cache for Redis
- All session data now stored in Azure Cache for Redis
- Enables stateless application architecture
- Supports horizontal scaling across multiple instances
- Session data persists across container restarts

### 6. File-based Authentication (Blocker 17)
**Issue**: Local file-based credential storage
**Solution**: Azure Active Directory integration
- Spring Security configured for Azure AD authentication
- Supports OAuth2/OpenID Connect
- Centralized identity management
- Ready for MSAL integration

### 7. In-Memory Caching Without TTL (Blocker 18)
**Issue**: In-memory cache causing memory growth and stale data
**Solution**: Azure Cache for Redis with TTL
- Replaced in-memory HashMap with Redis-backed cache
- All cache entries have TTL (30 minutes default)
- Distributed cache ensures consistency across instances
- Prevents memory exhaustion

## Azure Services Used

### Azure Blob Storage
- **Purpose**: Persistent file storage for reports
- **Configuration**: `azure.storage.blob-endpoint`, `azure.storage.container-name`
- **Authentication**: DefaultAzureCredential (Managed Identity)

### Azure Key Vault
- **Purpose**: Secure storage for database credentials and secrets
- **Configuration**: `azure.keyvault.uri`
- **Authentication**: DefaultAzureCredential (Managed Identity)

### Azure Cache for Redis
- **Purpose**: Distributed session storage and caching
- **Configuration**: `spring.redis.host`, `spring.redis.port`, `spring.redis.password`
- **Features**: Session persistence, distributed caching with TTL

### Azure App Configuration
- **Purpose**: Centralized configuration management
- **Configuration**: `azure.appconfiguration.endpoint`
- **Benefits**: Environment-agnostic configuration

### Azure Active Directory
- **Purpose**: Authentication and authorization
- **Configuration**: `azure.activedirectory.tenant-id`, `azure.activedirectory.client-id`
- **Integration**: Spring Security with OAuth2

## Environment Variables Required

### Azure Storage
```
AZURE_STORAGE_BLOB_ENDPOINT=https://<account>.blob.core.windows.net
AZURE_STORAGE_CONTAINER_NAME=resort-reports
```

### Azure Key Vault
```
AZURE_KEYVAULT_URI=https://<vault-name>.vault.azure.net/
```

### Azure Redis Cache
```
REDIS_HOST=<cache-name>.redis.cache.windows.net
REDIS_PORT=6380
REDIS_PASSWORD=<access-key>
REDIS_SSL=true
```

### Azure Active Directory
```
AZURE_AD_TENANT_ID=<tenant-id>
AZURE_AD_CLIENT_ID=<client-id>
AZURE_AD_CLIENT_SECRET=<client-secret>
```

### Database Configuration
```
SPRING_DATASOURCE_URL=jdbc:postgresql://<server>.postgres.database.azure.com:5432/<database>
SPRING_DATASOURCE_USERNAME=<username>
SPRING_DATASOURCE_PASSWORD=<password>
```

### Service Endpoints
```
PAYMENT_ENDPOINT=https://payment-service.azurewebsites.net/charge
INVENTORY_ENDPOINT=https://inventory-service.azurewebsites.net/rooms
NOTIFICATION_ENDPOINT=https://notification-service.azurewebsites.net/send
```

## Authentication Methods

The application uses **DefaultAzureCredential** which supports multiple authentication methods in order:
1. **Managed Identity** (recommended for Azure-hosted apps)
2. **Azure CLI** (for local development)
3. **Environment Variables** (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
4. **IntelliJ/VS Code Azure plugins**

## Local Development

For local development without Azure services:
1. The application will use fallback configurations
2. H2 in-memory database is used by default
3. File operations will use in-memory storage
4. Session management will work with local Redis or in-memory fallback

## Deployment to Azure

### Azure Container Apps (Recommended)
```bash
az containerapp create \
  --name resortslite \
  --resource-group <resource-group> \
  --environment <environment> \
  --image <container-registry>/resortslite:latest \
  --target-port 8080 \
  --ingress external \
  --env-vars \
    AZURE_KEYVAULT_URI=<vault-uri> \
    AZURE_STORAGE_BLOB_ENDPOINT=<blob-endpoint> \
    REDIS_HOST=<redis-host>
```

### Azure Kubernetes Service (AKS)
Deploy using Kubernetes manifests with ConfigMaps and Secrets for environment variables.

### Azure App Service
Deploy as a JAR file with application settings configured in the Azure Portal.

## Security Improvements

1. **No hard-coded credentials** - All secrets in Azure Key Vault
2. **Parameterized SQL queries** - Prevents SQL injection
3. **SHA-256 hashing** - Replaced weak MD5 hashing
4. **Azure AD authentication** - Centralized identity management
5. **Updated dependencies** - Fixed Log4Shell and other CVEs

## 12-Factor App Compliance

✅ **I. Codebase** - Single codebase tracked in version control
✅ **II. Dependencies** - Explicitly declared in pom.xml
✅ **III. Config** - Configuration externalized to environment variables
✅ **IV. Backing Services** - Azure services treated as attached resources
✅ **V. Build, Release, Run** - Strict separation of stages
✅ **VI. Processes** - Stateless processes with Redis for session state
✅ **VII. Port Binding** - Dynamic port binding via environment variable
✅ **VIII. Concurrency** - Horizontal scaling enabled
✅ **IX. Disposability** - Fast startup and graceful shutdown
✅ **X. Dev/Prod Parity** - Same backing services in all environments
✅ **XI. Logs** - Logs written to stdout (cloud-native logging)
✅ **XII. Admin Processes** - Management tasks as one-off processes

## Next Steps

1. **Configure Azure Resources**: Set up Key Vault, Blob Storage, Redis Cache
2. **Enable Managed Identity**: Assign managed identity to the application
3. **Configure Azure AD**: Register application in Azure AD
4. **Deploy to Azure**: Use Container Apps, AKS, or App Service
5. **Monitor**: Enable Application Insights for monitoring and diagnostics

## Support

For issues or questions, refer to Azure documentation:
- [Azure Blob Storage](https://docs.microsoft.com/azure/storage/blobs/)
- [Azure Key Vault](https://docs.microsoft.com/azure/key-vault/)
- [Azure Cache for Redis](https://docs.microsoft.com/azure/azure-cache-for-redis/)
- [Azure Active Directory](https://docs.microsoft.com/azure/active-directory/)
