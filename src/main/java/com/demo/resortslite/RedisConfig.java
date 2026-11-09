package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisConfig — cr-java-0067 remediation support.
 *
 * Configures a {@link RedisTemplate}{@code <String, Object>} bean that uses:
 * <ul>
 *   <li>{@link StringRedisSerializer} for keys — human-readable Redis keys
 *       (e.g., {@code resortslite:booking:BK-XXXXXXXX}).</li>
 *   <li>{@link GenericJackson2JsonRedisSerializer} for values — stores booking
 *       {@link java.util.Map} objects as JSON in Amazon ElastiCache for Redis,
 *       enabling cross-instance deserialization without class-path coupling.</li>
 * </ul>
 *
 * This bean is injected into {@link BookingController} to replace the unbounded
 * static {@code HashMap} in-memory cache with a distributed, TTL-controlled
 * Redis cache (rule cr-java-0067: In-Memory Caching Without TTL).
 */
@Configuration
public class RedisConfig {

    /**
     * Provides a {@link RedisTemplate} configured with JSON serialization for
     * booking cache entries stored in Amazon ElastiCache for Redis.
     *
     * @param connectionFactory auto-configured by Spring Boot using the
     *                          {@code spring.redis.host} / {@code spring.redis.port}
     *                          properties (or their {@code REDIS_HOST} / {@code REDIS_PORT}
     *                          environment variable equivalents).
     * @return configured {@link RedisTemplate} instance
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys so Redis keys are human-readable.
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values to support cross-instance deserialization
        // of booking Map<String, Object> entries stored in ElastiCache.
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
