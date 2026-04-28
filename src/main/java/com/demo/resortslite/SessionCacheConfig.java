package com.demo.resortslite;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Configuration for distributed session management and caching.
 * Enables Spring Session with Redis for stateless container scaling.
 */
@Configuration
@EnableRedisHttpSession
@EnableCaching
public class SessionCacheConfig {
    // Spring Boot auto-configuration handles Redis connection setup
    // based on application.properties
}
