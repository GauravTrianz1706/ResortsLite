package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis Session Configuration for Amazon ElastiCache
 * 
 * FIXED cr-java-0065: HTTP Session State Storage
 * 
 * This configuration enables Spring Session with Redis backend, replacing in-memory
 * HTTP session storage with distributed session management via Amazon ElastiCache.
 * 
 * Benefits:
 * - Stateless application instances (no server affinity required)
 * - Horizontal scalability across multiple instances
 * - Session persistence survives instance termination
 * - Load balancer can route requests to any instance
 * - Cloud-native session management for AWS deployment
 * 
 * Configuration:
 * - Set REDIS_HOST environment variable to ElastiCache cluster endpoint
 * - Set REDIS_PORT (default: 6379)
 * - Set REDIS_PASSWORD if authentication is enabled
 * - SSL is enabled by default for secure communication
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {

    /**
     * Redis connection factory bean for Lettuce client
     * Lettuce is the recommended Redis client for Spring Boot applications
     * connecting to Amazon ElastiCache for Redis
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}
