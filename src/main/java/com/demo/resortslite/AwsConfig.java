package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AWS SDK configuration for cloud-native services.
 * Provides centralized configuration for S3, Secrets Manager, and Systems Manager.
 */
@Configuration
public class AwsConfig {

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Bean
    public Region awsRegion() {
        return Region.of(awsRegion);
    }

    @Bean
    public S3Client s3Client(Region region) {
        return S3Client.builder()
                .region(region)
                .build();
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(Region region) {
        return SecretsManagerClient.builder()
                .region(region)
                .build();
    }

    @Bean
    public SsmClient ssmClient(Region region) {
        return SsmClient.builder()
                .region(region)
                .build();
    }
}
