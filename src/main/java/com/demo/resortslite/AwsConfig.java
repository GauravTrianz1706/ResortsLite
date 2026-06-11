package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AwsConfig — Spring configuration class that registers AWS SDK v2 clients as beans.
 *
 * Provides:
 *  - S3Client          : Amazon S3 for cloud-native file storage (cr-java-0061, cr-java-0062, cr-java-0063)
 *  - S3Presigner       : Pre-signed URL generation for S3 objects
 *  - SecretsManagerClient : AWS Secrets Manager for DB credentials (cr-java-0069, cr-java-0090)
 *  - SsmClient         : AWS SSM Parameter Store for environment URLs and ports (cr-java-0071, cr-java-0077)
 *  - RedisTemplate     : Amazon ElastiCache for Redis for session and cache (cr-java-0065, cr-java-0067)
 *
 * All clients use DefaultCredentialsProvider which resolves credentials from:
 *  1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 *  2. ECS task role / EC2 instance profile (recommended for cloud deployments)
 *  3. ~/.aws/credentials (local development)
 */
@Configuration
public class AwsConfig {

    @Value("${cloud.aws.region.static:us-east-1}")
    private String awsRegion;

    /**
     * Amazon S3 client — replaces java.io.File and local FileWriter operations.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * S3 Pre-signer — generates time-limited pre-signed URLs for report downloads.
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS Secrets Manager client — retrieves DB credentials and app user passwords
     * replacing hard-coded DB_USER, DB_PASS constants and file-based auth (cr-java-0069, cr-java-0090).
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * AWS SSM Parameter Store client — retrieves environment-specific URLs and port numbers
     * replacing hard-coded PAYMENT_API, inventory URL, and SERVER_PORT (cr-java-0071, cr-java-0077).
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * RedisTemplate configured for Amazon ElastiCache for Redis.
     * Replaces static in-memory HashMap cache (cr-java-0067) and HttpSession storage (cr-java-0065).
     * Uses JSON serialization for cross-instance compatibility.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
