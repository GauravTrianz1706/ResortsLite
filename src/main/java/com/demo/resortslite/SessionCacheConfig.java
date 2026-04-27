package com.demo.resortslite;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Configuration for distributed session management and caching.
 * Enables Spring Session with Redis for stateless container scaling.
 * Enables distributed caching with Redis for horizontal scalability.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
@EnableCaching
public class SessionCacheConfig {
    // Spring Boot auto-configuration handles Redis connection
    // Configuration is externalized via application.properties
}
