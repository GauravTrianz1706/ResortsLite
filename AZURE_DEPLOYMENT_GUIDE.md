# ResortsLite - Cloud-Ready Application for Azure

## Overview
This application has been modernized for Azure cloud deployment with the following cloud-native patterns:

## Cloud Readiness Fixes Applied

### 1. File System Dependencies → Azure Blob Storage
- **Issue**: Hard-coded file paths and local file system operations
- **Fix**: Migrated to Azure Blob Storage using Azure SDK for Java
- **Configuration Required**:
  - `AZURE_STORAGE_CONNECTION_STRING`: Connection string for Azure Storage Account
  - `AZURE_STORAGE_CONTAINER_REPORTS`: Container name for reports (default: reports)
  - `AZURE_STORAGE_CONTAINER_BACKUPS`: Container name for backups (default: backups)

### 2. Hard-coded Credentials → Azure Key Vault
- **Issue**: Database credentials hard-coded in source code
- **Fix**: Integrated Azure Key Vault with DefaultAzureCredential
- **Configuration Required**:
  - `AZURE_KEYVAULT_URI`: Key Vault URI (e.g., https://your-keyvault.vault.azure.net/)
  - Store secrets in Key Vault: `db-host`, `db-user`, `db-password`

### 3. Hard-coded URLs → Azure App Configuration
- **Issue**: Environment-specific URLs hard-coded in code
- **Fix**: Externalized to environment variables and Azure App Configuration
- **Configuration Required**:
  - `PAYMENT_SERVICE_URL`: Payment service endpoint
  - `INVENTORY_SERVICE_URL`: Inventory service endpoint
  - `NOTIFICATION_SERVICE_URL`: Notification service endpoint

### 4. Hard-coded Ports → Environment Variables
- **Issue**: Fixed port numbers preventing dynamic assignment
- **Fix**: Externalized to `PORT` environment variable
- **Configuration Required**:
  - `PORT`: Server port (default: 8080)

### 5. HTTP Session State → Azure Cache for Redis
- **Issue**: In-memory session storage preventing horizontal scaling
- **Fix**: Migrated to Azure Cache for Redis with TTL policies
- **Configuration Required**:
  - `AZURE_REDIS_ENABLED`: Enable Redis (true/false)
  - `AZURE_REDIS_HOST`: Redis host
  - `AZURE_REDIS_PORT`: Redis port (default: 6379)
  - `AZURE_REDIS_PASSWORD`: Redis password
  - `AZURE_REDIS_SSL`: Use SSL (default: true)
  - `AZURE_REDIS_TTL_SECONDS`: Cache TTL in seconds (default: 3600)

### 6. In-Memory Cache → Azure Cache for Redis
- **Issue**: In-memory cache without TTL causing memory issues
- **Fix**: Replaced with distributed Redis cache with TTL
- **Benefits**: Prevents memory exhaustion, enables cache consistency across instances

### 7. File-based Authentication → Azure Active Directory
- **Issue**: Local file-based credential storage
- **Fix**: Integrated Azure AD with Spring Security
- **Configuration Required**:
  - `AZURE_AD_ENABLED`: Enable Azure AD (true/false)
  - `AZURE_AD_TENANT_ID`: Azure AD tenant ID
  - `AZURE_AD_CLIENT_ID`: Application client ID
  - `AZURE_AD_CLIENT_SECRET`: Application client secret

## Environment Variables

### Required for Production
```bash
# Database
DB_URL=jdbc:postgresql://your-db.postgres.database.azure.com:5432/resortdb
DB_USERNAME=dbadmin@your-db
DB_PASSWORD=<from-key-vault>

# Azure Storage
AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=https;AccountName=...

# Azure Key Vault
AZURE_KEYVAULT_URI=https://your-keyvault.vault.azure.net/

# Azure Redis Cache
AZURE_REDIS_ENABLED=true
AZURE_REDIS_HOST=your-redis.redis.cache.windows.net
AZURE_REDIS_PORT=6380
AZURE_REDIS_PASSWORD=<redis-key>
AZURE_REDIS_SSL=true

# Azure Active Directory
AZURE_AD_ENABLED=true
AZURE_AD_TENANT_ID=<tenant-id>
AZURE_AD_CLIENT_ID=<client-id>
AZURE_AD_CLIENT_SECRET=<client-secret>

# Service Endpoints
PAYMENT_SERVICE_URL=https://payment-service.azurewebsites.net/payments/charge
INVENTORY_SERVICE_URL=https://inventory-service.azurewebsites.net/rooms/available
NOTIFICATION_SERVICE_URL=https://notification-service.azurewebsites.net/send

# Server
PORT=8080
```

### Optional for Development
```bash
# H2 Console (disable in production)
H2_CONSOLE_ENABLED=false

# Logging
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=DEBUG
```

## Azure Services Required

1. **Azure Storage Account**: For blob storage (reports, backups)
2. **Azure Key Vault**: For secrets management
3. **Azure Cache for Redis**: For distributed caching and session storage
4. **Azure Active Directory**: For authentication and authorization
5. **Azure App Configuration** (optional): For centralized configuration management
6. **Azure Database for PostgreSQL/MySQL** (production): Replace H2 with production database

## Deployment Steps

1. **Create Azure Resources**:
   ```bash
   # Create resource group
   az group create --name resortslite-rg --location eastus
   
   # Create storage account
   az storage account create --name resortslitestorage --resource-group resortslite-rg
   
   # Create Key Vault
   az keyvault create --name resortslite-kv --resource-group resortslite-rg
   
   # Create Redis Cache
   az redis create --name resortslite-redis --resource-group resortslite-rg --sku Basic --vm-size c0
   ```

2. **Configure Managed Identity**:
   - Enable System-assigned Managed Identity on your Azure App Service
   - Grant Key Vault access to the Managed Identity
   - Grant Storage Blob Data Contributor role to the Managed Identity

3. **Set Environment Variables**:
   - Configure all required environment variables in Azure App Service Configuration

4. **Deploy Application**:
   ```bash
   mvn clean package
   az webapp deploy --resource-group resortslite-rg --name resortslite-app --src-path target/resortsLite-1.0.0.jar
   ```

## Security Improvements

1. **Removed hard-coded credentials**: All secrets now in Azure Key Vault
2. **Replaced MD5 with SHA-256**: Secure hashing for sensitive data
3. **Updated vulnerable dependencies**: 
   - log4j 2.14.1 → 2.23.1 (fixes Log4Shell CVE-2021-44228)
   - commons-collections 3.2.1 → commons-collections4 4.4 (fixes CVE-2015-6420)
4. **Parameterized SQL queries**: Prevents SQL injection attacks
5. **Azure AD integration**: Enterprise-grade authentication

## Scalability Improvements

1. **Stateless architecture**: Session state externalized to Redis
2. **Distributed caching**: Redis cache with TTL policies
3. **Connection pooling**: HikariCP for efficient database connections
4. **Dynamic port assignment**: Supports container orchestration
5. **Horizontal scaling**: No local state dependencies

## Monitoring and Observability

- Spring Boot Actuator endpoints enabled for health checks
- Structured logging for Azure Monitor integration
- Redis health checks included in actuator health endpoint

## Local Development

For local development without Azure services:
```bash
# Use H2 in-memory database
DB_URL=jdbc:h2:mem:resortdb

# Disable Azure services
AZURE_REDIS_ENABLED=false
AZURE_AD_ENABLED=false
H2_CONSOLE_ENABLED=true

# Run application
mvn spring-boot:run
```

## Testing

```bash
# Run tests
mvn test

# Create booking (local)
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-03-01&checkOut=2024-03-05"

# Check availability
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"

# Health check
curl http://localhost:8080/actuator/health
```

## Troubleshooting

### Azure Key Vault Access Issues
- Verify Managed Identity is enabled
- Check Key Vault access policies
- Ensure secrets exist in Key Vault

### Redis Connection Issues
- Verify Redis hostname and port
- Check SSL configuration
- Verify Redis password/access key

### Azure AD Authentication Issues
- Verify tenant ID, client ID, and client secret
- Check app registration in Azure AD
- Verify redirect URIs are configured

## Support

For issues or questions, contact the development team.
