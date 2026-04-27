package com.demo.resortslite;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcpConfig {

    @Value("${gcp.storage.project-id:}")
    private String projectId;

    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    /**
     * Google Cloud Storage client bean
     * Uses Application Default Credentials in GCP environment
     */
    @Bean
    public Storage googleCloudStorage() {
        try {
            if (projectId != null && !projectId.isEmpty()) {
                return StorageOptions.newBuilder()
                        .setProjectId(projectId)
                        .build()
                        .getService();
            } else {
                // Use default project from environment
                return StorageOptions.getDefaultInstance().getService();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize Google Cloud Storage client: " + e.getMessage());
            System.err.println("File operations will use fallback behavior.");
            return null;
        }
    }

    /**
     * Bucket name configuration
     */
    @Bean
    public String gcsBucketName() {
        return bucketName;
    }
}
