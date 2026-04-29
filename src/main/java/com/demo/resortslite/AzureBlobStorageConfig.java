package com.demo.resortslite;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Blob Storage configuration
 * Provides centralized blob storage client for file operations
 */
@Configuration
public class AzureBlobStorageConfig {

    @Value("${azure.storage.account-name:}")
    private String storageAccountName;

    @Value("${azure.storage.account-key:}")
    private String storageAccountKey;

    @Value("${azure.storage.container-name:resort-reports}")
    private String containerName;

    @Bean
    public BlobServiceClient blobServiceClient() {
        if (storageAccountName == null || storageAccountName.isEmpty()) {
            // Return null if not configured - service will handle gracefully
            return null;
        }

        String connectionString = String.format(
            "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
            storageAccountName, storageAccountKey
        );

        return new BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient();
    }

    @Bean
    public BlobContainerClient blobContainerClient(BlobServiceClient blobServiceClient) {
        if (blobServiceClient == null) {
            return null;
        }

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }
        
        return containerClient;
    }
}
