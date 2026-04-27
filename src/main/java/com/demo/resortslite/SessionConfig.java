package com.demo.resortslite;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Spring Session Configuration for distributed session management
 * Enables Redis-backed HTTP sessions for containerized environments
 * 
 * Fixes blockers:
 * - cz-java-0063: Server-side Sessions
 * - cz-java-0069: In-Memory Session Storage
 * 
 * Sessions are now stored in Amazon ElastiCache (Redis) instead of in-memory,
 * enabling session persistence across container restarts and horizontal scaling.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class SessionConfig {
    // Spring Session automatically configures Redis session repository
    // using properties from application.properties:
    // - spring.redis.host
    // - spring.redis.port
    // - spring.redis.password
    // - spring.session.redis.namespace
}
