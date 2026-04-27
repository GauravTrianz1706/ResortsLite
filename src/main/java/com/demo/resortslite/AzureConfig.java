package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfig {

    @Value("${azure.keyvault.uri:}")
    private String keyVaultUri;

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    @Value("${azure.appconfiguration.endpoint:}")
    private String appConfigEndpoint;

    /**
     * Creates a DefaultAzureCredential bean that can be used across the application.
     * This credential supports multiple authentication mechanisms:
     * - Managed Identity (for Azure-hosted applications)
     * - Azure CLI (for local development)
     * - Environment variables
     * - IntelliJ/VS Code Azure plugins
     */
    @Bean
    public DefaultAzureCredential azureCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    /**
     * Validates Azure configuration on startup
     */
    public void validateConfiguration() {
        if (keyVaultUri == null || keyVaultUri.isEmpty()) {
            System.out.println("WARNING: Azure Key Vault URI not configured. Using fallback credentials.");
        }
        if (blobEndpoint == null || blobEndpoint.isEmpty()) {
            System.out.println("WARNING: Azure Blob Storage endpoint not configured. File operations will use fallback.");
        }
        if (appConfigEndpoint == null || appConfigEndpoint.isEmpty()) {
            System.out.println("WARNING: Azure App Configuration endpoint not configured. Using local configuration.");
        }
    }
}
