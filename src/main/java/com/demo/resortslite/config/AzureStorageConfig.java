package com.demo.resortslite.config;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Blob Storage configuration
 * Provides cloud-native file storage for reports and documents
 * Fixes: cr-java-0061 (Hard-coded File Paths)
 * Fixes: cr-java-0062 (Local File System Write Operations)
 * Fixes: cr-java-0063 (Java.io.File Usage for Data Storage)
 */
@Configuration
public class AzureStorageConfig {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    /**
     * Create BlobServiceClient bean for Azure Blob Storage operations
     */
    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }
}
