package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AwsConfig — configures AWS SDK v2 clients and enables cloud-native features.
 *
 * - S3Client:              used by ReportService to store reports in Amazon S3
 *                          (replaces local file system — cr-java-0061/0062/0063).
 * - SecretsManagerClient:  used by BookingService to retrieve DB credentials and
 *                          payment endpoint from AWS Secrets Manager
 *                          (replaces hard-coded credentials — cr-java-0069/0090).
 * - SsmClient:             used by ReportService to resolve server port from
 *                          AWS SSM Parameter Store (cr-java-0077).
 * - @EnableRedisHttpSession: backs HttpSession with Amazon ElastiCache for Redis
 *                          via Spring Session (cr-java-0065).
 * - @EnableCaching:        enables Spring Cache abstraction backed by Redis
 *                          (cr-java-0067).
 */
@Configuration
@EnableCaching
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class AwsConfig {

    @Value("${aws.region:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    /**
     * Amazon S3 client — uses DefaultCredentialsProvider which resolves credentials
     * from environment variables, EC2 instance profile, ECS task role, or
     * ~/.aws/credentials (in that order). No hard-coded credentials.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS Secrets Manager client — used to retrieve database credentials and
     * service endpoints at application startup.
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS Systems Manager (SSM) client — used to retrieve parameter values
     * such as server port and service URLs from Parameter Store.
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
