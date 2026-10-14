package com.demo.resortslite;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * cr-java-0067: Redis configuration for Amazon ElastiCache-backed caching.
 *
 * <p>Provides a {@link RedisTemplate} bean configured with JSON serialization
 * so that {@code Map<String, Object>} booking entries are stored as human-readable
 * JSON in Amazon ElastiCache for Redis. This replaces the unbounded static
 * {@code HashMap} (bookingCache) that caused indefinite memory growth, stale data,
 * and cross-instance inconsistencies in cloud environments.</p>
 *
 * <p>Key design decisions:</p>
 * <ul>
 *   <li>Keys are serialized as plain UTF-8 strings (e.g. {@code booking:BK-12345}).</li>
 *   <li>Values are serialized as JSON with type metadata, enabling safe deserialization
 *       of {@code Map<String, Object>} entries across application instances.</li>
 *   <li>TTL is applied per entry in {@link BookingController} via
 *       {@code RedisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MINUTES)},
 *       ensuring controlled expiration and preventing stale data.</li>
 * </ul>
 *
 * <p>Connection details are resolved from environment variables at runtime:</p>
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: localhost)</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: 6379)</li>
 * </ul>
 * These are bound via {@code spring.redis.host} and {@code spring.redis.port}
 * in {@code application.properties} and injected automatically by Spring Boot's
 * Redis auto-configuration into the {@link RedisConnectionFactory}.
 */
@Configuration
public class RedisConfig {

    /**
     * Configures a {@link RedisTemplate} with:
     * <ul>
     *   <li>String key serializer — keys are stored as plain UTF-8 strings.</li>
     *   <li>JSON value serializer — values are stored as JSON with type metadata,
     *       enabling correct deserialization of {@code Map<String, Object>} entries.</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory auto-configured by
     *                          Spring Boot using {@code REDIS_HOST} / {@code REDIS_PORT}
     * @return a fully configured {@link RedisTemplate}
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys so they are human-readable in Redis CLI
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use Jackson JSON serializer for values with type information so that
        // Map<String, Object> entries can be deserialized correctly on retrieval
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
