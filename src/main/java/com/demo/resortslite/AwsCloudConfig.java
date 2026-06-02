package com.demo.resortslite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AwsCloudConfig — provides AWS SDK v2 and Redis beans for cloud-native operation.
 *
 * - S3Client:              used by ReportService to store reports in Amazon S3.
 * - SecretsManagerClient:  used by BookingService to retrieve DB and auth credentials.
 * - SsmClient:             available for SSM Parameter Store lookups (URLs, ports).
 * - RedisTemplate:         used by BookingController for distributed caching with TTL.
 * - RedisConnectionFactory: connects to Amazon ElastiCache for Redis for Spring Session.
 */
@Configuration
public class AwsCloudConfig {

    /**
     * AWS region — injected from the AWS_REGION environment variable
     * (automatically set by ECS/EKS/Lambda/Beanstalk).
     */
    @Value("${cloud.aws.region.static:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    /**
     * ElastiCache Redis endpoint — injected from REDIS_HOST environment variable
     * (set via ECS task definition or EKS ConfigMap pointing to ElastiCache cluster).
     */
    @Value("${spring.redis.host:${REDIS_HOST:localhost}}")
    private String redisHost;

    /**
     * ElastiCache Redis port — injected from REDIS_PORT environment variable.
     */
    @Value("${spring.redis.port:${REDIS_PORT:6379}}")
    private int redisPort;

    /**
     * Amazon S3 client (AWS SDK v2).
     * Uses the default credential provider chain (IAM role, environment variables,
     * ~/.aws/credentials) — no hard-coded credentials.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * AWS Secrets Manager client (AWS SDK v2).
     * Credentials resolved via IAM role attached to ECS task / EC2 instance.
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * AWS Systems Manager (SSM) client for Parameter Store lookups.
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Redis connection factory pointing to Amazon ElastiCache for Redis.
     * Replaces in-process session storage and unbounded in-memory caching.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    /**
     * RedisTemplate with JSON serialization for distributed booking cache.
     * TTL is enforced per entry in BookingController to prevent unbounded growth.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
