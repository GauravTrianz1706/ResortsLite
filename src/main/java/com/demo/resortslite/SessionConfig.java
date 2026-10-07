package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * cz-java-0069: Spring Session configuration backed by Amazon ElastiCache (Redis).
 *
 * Replaces the default in-memory HttpSession store with a Redis-backed distributed
 * session store. This ensures:
 *   - Sessions survive container restarts (no data loss on pod eviction/restart)
 *   - Sessions are shared across all EKS pod replicas (horizontal scaling safe)
 *   - IRSA (IAM Roles for Service Accounts) is used for secure ElastiCache access on EKS
 *
 * cz-java-0070: RedisTemplate bean provides a distributed cache for booking data,
 * replacing the local in-process HashMap cache in BookingController. All EKS pod
 * replicas share the same ElastiCache (Redis) instance, enabling safe horizontal scaling.
 *
 * Required environment variables (set via Kubernetes Secrets / EKS Pod spec):
 *   REDIS_HOST  - ElastiCache primary endpoint (e.g. my-cluster.abc123.ng.0001.use1.cache.amazonaws.com)
 *   REDIS_PORT  - ElastiCache port (default: 6379)
 *
 * maxInactiveIntervalInSeconds: session TTL in seconds (default: 1800 = 30 minutes).
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

    /**
     * Lettuce-based Redis connection factory pointing to Amazon ElastiCache.
     * Host and port are read from environment variables to remain container-portable.
     */
    @Bean
    public LettuceConnectionFactory connectionFactory() {
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    /**
     * cz-java-0070: RedisTemplate for distributed booking cache backed by Amazon ElastiCache.
     * Replaces the local in-process HashMap (bookingCache) in BookingController with a
     * container-safe, horizontally-scalable Redis-backed cache shared across all EKS pods.
     * Uses StringRedisSerializer for keys and GenericJackson2JsonRedisSerializer for values
     * to ensure correct serialization of Map<String, Object> booking entries.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
