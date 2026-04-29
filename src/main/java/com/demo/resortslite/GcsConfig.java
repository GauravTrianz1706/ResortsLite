package com.demo.resortslite;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Value("${gcp.storage.project-id:}")
    private String projectId;

    @Bean
    public Storage googleCloudStorage() {
        try {
            if (projectId != null && !projectId.isEmpty()) {
                return StorageOptions.newBuilder()
                        .setProjectId(projectId)
                        .build()
                        .getService();
            } else {
                // Use default credentials and project
                return StorageOptions.getDefaultInstance().getService();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize GCS client: " + e.getMessage());
            return null;
        }
    }
}
