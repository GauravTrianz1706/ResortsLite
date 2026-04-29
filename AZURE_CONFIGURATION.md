# Azure Cloud Configuration Guide

This document describes the environment variables required for deploying the ResortsLite application to Azure.

## Required Environment Variables

### Database Configuration
- `AZURE_DATABASE_URL`: JDBC connection string for Azure SQL Database or PostgreSQL
- `AZURE_DATABASE_USERNAME`: Database username
- `AZURE_DATABASE_PASSWORD`: Database password (store in Azure Key Vault)

### Azure Blob Storage
- `AZURE_STORAGE_CONNECTION_STRING`: Connection string for Azure Storage Account
- `AZURE_STORAGE_ACCOUNT_NAME`: Storage account name
- `AZURE_STORAGE_ACCOUNT_KEY`: Storage account access key (store in Azure Key Vault)
- `AZURE_STORAGE_CONTAINER_NAME`: Container name for storing reports (default: resort-reports)

### Azure Key Vault
- `AZURE_KEYVAULT_URI`: Key Vault URI (e.g., https://your-keyvault.vault.azure.net/)
- `AZURE_TENANT_ID`: Azure AD tenant ID
- `AZURE_CLIENT_ID`: Application (client) ID
- `AZURE_CLIENT_SECRET`: Application client secret

### Azure App Configuration
- `AZURE_APPCONFIGURATION_ENDPOINT`: App Configuration endpoint URL

### Azure Cache for Redis
- `AZURE_REDIS_HOST`: Redis cache hostname
- `AZURE_REDIS_PORT`: Redis port (default: 6379)
- `AZURE_REDIS_PASSWORD`: Redis access key (store in Azure Key Vault)
- `AZURE_REDIS_SSL`: Enable SSL for Redis (default: true)

### Azure Active Directory
- `AZURE_AD_TENANT_ID`: Azure AD tenant ID for authentication
- `AZURE_AD_CLIENT_ID`: Application client ID for Azure AD
- `AZURE_AD_CLIENT_SECRET`: Application client secret for Azure AD
- `AZURE_AD_ALLOWED_GROUPS`: Comma-separated list of allowed AD groups

### Service Endpoints
- `PAYMENT_SERVICE_ENDPOINT`: Payment service URL
- `INVENTORY_SERVICE_ENDPOINT`: Inventory service URL
- `NOTIFICATION_SERVICE_ENDPOINT`: Notification service URL

### Application Configuration
- `PORT`: Application port (dynamically assigned by Azure Container Apps)
- `SERVER_PORT`: Internal server port for service communication

## Azure Services Setup

### 1. Azure Storage Account
```bash
az storage account create \
  --name <storage-account-name> \
  --resource-group <resource-group> \
  --location <location> \
  --sku Standard_LRS

az storage container create \
  --name resort-reports \
  --account-name <storage-account-name>
```

### 2. Azure Key Vault
```bash
az keyvault create \
  --name <keyvault-name> \
  --resource-group <resource-group> \
  --location <location>

az keyvault secret set \
  --vault-name <keyvault-name> \
  --name "database-password" \
  --value "<password>"
```

### 3. Azure Cache for Redis
```bash
az redis create \
  --name <redis-name> \
  --resource-group <resource-group> \
  --location <location> \
  --sku Basic \
  --vm-size c0
```

### 4. Azure App Configuration
```bash
az appconfig create \
  --name <appconfig-name> \
  --resource-group <resource-group> \
  --location <location>
```

### 5. Azure Active Directory App Registration
1. Register application in Azure AD
2. Configure API permissions
3. Create client secret
4. Configure redirect URIs

## Deployment Notes

### Cloud Readiness Fixes Applied
1. **File System Dependencies**: Migrated to Azure Blob Storage
2. **Hard-coded Credentials**: Externalized to Azure Key Vault
3. **Hard-coded URLs**: Externalized to Azure App Configuration
4. **Hard-coded Ports**: Using environment variables
5. **HTTP Session State**: Migrated to Azure Cache for Redis
6. **In-Memory Caching**: Replaced with distributed Redis cache with TTL
7. **File-based Authentication**: Migrated to Azure Active Directory

### Security Improvements
- All secrets stored in Azure Key Vault
- BCrypt password hashing
- Azure AD OAuth2 authentication
- SSL/TLS for Redis connections
- Parameterized SQL queries to prevent injection

### Scalability Improvements
- Stateless application design
- Distributed session management
- Distributed caching with TTL
- Dynamic port assignment
- Horizontal scaling ready

## Testing Locally

For local development, create a `.env` file with the following:
```
AZURE_STORAGE_CONNECTION_STRING=UseDevelopmentStorage=true
AZURE_REDIS_HOST=localhost
AZURE_REDIS_PORT=6379
AZURE_REDIS_SSL=false
PORT=8080
```

Run with Docker Compose or configure local Azure services using Azurite for storage emulation.
