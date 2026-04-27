package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AWS SDK configuration for cloud-native services
 * FIXED cr-java-0061, cr-java-0062, cr-java-0063: S3 client for file storage
 * FIXED cr-java-0069, cr-java-0090: Secrets Manager for credential management
 * FIXED cr-java-0071: Systems Manager Parameter Store for configuration
 */
@Configuration
public class AwsConfig {

    @Value("${aws.s3.region}")
    private String awsRegion;

    /**
     * S3 Client bean for file storage operations
     * Replaces local file system dependencies
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Secrets Manager Client bean for secure credential storage
     * Replaces hard-coded credentials and file-based authentication
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Systems Manager Client bean for parameter store access
     * Replaces hard-coded configuration values
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
