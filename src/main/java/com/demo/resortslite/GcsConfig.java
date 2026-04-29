package com.demo.resortslite;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class GcsConfig {

    @Value("${spring.cloud.gcp.storage.project-id:}")
    private String projectId;

    @Value("${spring.cloud.gcp.storage.credentials.location:}")
    private String credentialsLocation;

    @Bean
    public Storage storage() throws IOException {
        StorageOptions.Builder builder = StorageOptions.newBuilder();
        
        if (projectId != null && !projectId.isEmpty()) {
            builder.setProjectId(projectId);
        }
        
        // Use credentials file if provided, otherwise use default credentials
        if (credentialsLocation != null && !credentialsLocation.isEmpty()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(credentialsLocation)
            );
            builder.setCredentials(credentials);
        }
        
        return builder.build().getService();
    }
}
