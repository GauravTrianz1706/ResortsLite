package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AWS SDK configuration for cloud-native services
 * Provides centralized configuration for S3, Secrets Manager, and Systems Manager
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Configure S3 client for file storage operations
     * Fixes: cr-java-0061, cr-java-0062, cr-java-0063 (File system dependencies)
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Configure Secrets Manager client for credential management
     * Fixes: cr-java-0069 (Hard-coded database credentials)
     * Fixes: cr-java-0090 (File-based authentication)
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Configure Systems Manager Parameter Store client for configuration management
     * Fixes: cr-java-0071 (Hard-coded environment URLs)
     * Fixes: cr-java-0077 (Hard-coded ports)
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
