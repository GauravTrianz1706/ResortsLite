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

    @Bean
    public Storage googleCloudStorage() {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        
        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }
        
        return builder.build().getService();
    }
}
