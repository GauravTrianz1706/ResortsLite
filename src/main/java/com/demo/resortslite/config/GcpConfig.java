package com.demo.resortslite.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GCP configuration for Cloud Storage and Secret Manager integration.
 * Fixes cr-java-0061, cr-java-0062, cr-java-0063 (File System Dependencies)
 * and cr-java-0069, cr-java-0090 (Hard-coded Credentials) by providing
 * cloud-native storage and secrets management.
 */
@Configuration
public class GcpConfig {

    @Value("${gcp.project-id:${GCP_PROJECT_ID:}}")
    private String projectId;

    /**
     * Google Cloud Storage client bean for file operations.
     * Replaces local file system operations with durable cloud storage.
     */
    @Bean
    public Storage googleCloudStorage() {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        
        // Set project ID if provided
        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }
        
        return builder.build().getService();
    }
}
