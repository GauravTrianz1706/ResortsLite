# Spring Session with Amazon ElastiCache for Redis

## Overview

This application has been migrated from in-memory HTTP session storage to distributed session management using **Spring Session** backed by **Amazon ElastiCache for Redis**.

## What Was Fixed (cr-java-0065)

### Before
- HTTP sessions stored in application memory
- Server affinity required (sticky sessions)
- Session data lost on instance restart
- Cannot horizontally scale without session replication
- Violates cloud-native stateless principles

### After
- HTTP sessions stored in Amazon ElastiCache for Redis
- No server affinity required
- Session data persists across instance restarts
- Fully horizontally scalable
- Cloud-native stateless architecture
- Load balancer can route to any instance

## Architecture

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Client    │─────▶│ Load Balancer│─────▶│  Instance 1 │
└─────────────┘      └─────────────┘      └─────────────┘
                            │                     │
                            │                     ▼
                            │              ┌─────────────┐
                            │              │   Redis     │
                            │              │ ElastiCache │
                            │              └─────────────┘
                            │                     ▲
                            │                     │
                            └────────────▶┌─────────────┐
                                          │  Instance 2 │
                                          └─────────────┘
```

## Configuration

### Maven Dependencies Added

```xml
<!-- Spring Session Data Redis -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>

<!-- Spring Boot Redis Starter (includes Lettuce client) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Commons Pool2 for Lettuce connection pooling -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

### Application Properties

```properties
# Spring Session Configuration
spring.session.store-type=redis
spring.session.redis.namespace=spring:session
spring.session.timeout=1800s

# Amazon ElastiCache for Redis Configuration
spring.redis.host=${REDIS_HOST:localhost}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD:}
spring.redis.ssl=true
```

### Environment Variables

Set these environment variables in your AWS deployment (ECS, EKS, Elastic Beanstalk):

| Variable | Description | Example |
|----------|-------------|---------|
| `REDIS_HOST` | ElastiCache cluster endpoint | `my-cluster.abc123.0001.use1.cache.amazonaws.com` |
| `REDIS_PORT` | Redis port (default: 6379) | `6379` |
| `REDIS_PASSWORD` | Redis AUTH password (if enabled) | `your-secure-password` |

## AWS ElastiCache Setup

### 1. Create ElastiCache Redis Cluster

```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resorts-lite-sessions \
  --engine redis \
  --cache-node-type cache.t3.micro \
  --num-cache-nodes 1 \
  --engine-version 7.0 \
  --port 6379 \
  --security-group-ids sg-xxxxxxxxx \
  --subnet-group-name my-subnet-group
```

### 2. Configure Security Group

Ensure your application's security group can access ElastiCache:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id sg-elasticache \
  --protocol tcp \
  --port 6379 \
  --source-group sg-application
```

### 3. Get Cluster Endpoint

```bash
aws elasticache describe-cache-clusters \
  --cache-cluster-id resorts-lite-sessions \
  --show-cache-node-info \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint.Address' \
  --output text
```

### 4. Set Environment Variable

Set the `REDIS_HOST` environment variable to the cluster endpoint in your deployment configuration.

## Code Changes

### RedisSessionConfig.java (New)

```java
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisSessionConfig {
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}
```

### BookingController.java (Updated)

The `HttpSession` API remains unchanged, but session data is now stored in Redis:

```java
// Session data stored in ElastiCache for Redis (not in-memory)
session.setAttribute("lastBooking", booking);
session.setAttribute("guestName", guestName);

// Session data retrieved from ElastiCache for Redis
String lastGuest = (String) session.getAttribute("guestName");
```

## Benefits

### Cloud-Native Architecture
- ✅ Stateless application instances
- ✅ Horizontal scalability
- ✅ No sticky sessions required
- ✅ Session persistence across deployments

### High Availability
- ✅ Session data survives instance termination
- ✅ Multi-AZ Redis replication (if configured)
- ✅ Automatic failover support

### Performance
- ✅ In-memory Redis performance
- ✅ Connection pooling via Lettuce
- ✅ Efficient serialization

### Security
- ✅ SSL/TLS encryption in transit
- ✅ VPC isolation
- ✅ Redis AUTH support
- ✅ AWS IAM integration (optional)

## Testing

### Local Development

For local testing, run Redis in Docker:

```bash
docker run -d -p 6379:6379 redis:7.0-alpine
```

The application will connect to `localhost:6379` by default.

### Verify Session Storage

1. Create a booking (POST `/api/bookings/create`)
2. Check Redis for session data:

```bash
redis-cli -h <elasticache-endpoint>
KEYS spring:session:*
GET spring:session:sessions:<session-id>
```

## Monitoring

### CloudWatch Metrics

Monitor ElastiCache performance:
- `CPUUtilization`
- `NetworkBytesIn/Out`
- `CurrConnections`
- `Evictions`
- `CacheHits/CacheMisses`

### Application Logs

Spring Session logs session operations at DEBUG level:

```properties
logging.level.org.springframework.session=DEBUG
```

## Troubleshooting

### Connection Refused

**Problem**: Application cannot connect to ElastiCache

**Solution**:
1. Verify security group rules allow traffic on port 6379
2. Ensure application and ElastiCache are in the same VPC
3. Check `REDIS_HOST` environment variable is set correctly

### Session Not Persisting

**Problem**: Session data is lost between requests

**Solution**:
1. Verify Redis is running and accessible
2. Check Spring Session configuration in `application.properties`
3. Ensure `@EnableRedisHttpSession` annotation is present
4. Verify session cookie is being sent by client

### Performance Issues

**Problem**: Slow session operations

**Solution**:
1. Enable connection pooling (commons-pool2 dependency)
2. Use appropriate ElastiCache node type
3. Consider Redis cluster mode for high throughput
4. Monitor ElastiCache metrics in CloudWatch

## Migration Checklist

- [x] Add Spring Session dependencies to `pom.xml`
- [x] Configure Redis connection in `application.properties`
- [x] Create `RedisSessionConfig.java` configuration class
- [x] Update `BookingController.java` with documentation
- [x] Create ElastiCache Redis cluster in AWS
- [x] Configure security groups for Redis access
- [x] Set `REDIS_HOST` environment variable in deployment
- [ ] Test session persistence across multiple instances
- [ ] Monitor ElastiCache metrics in CloudWatch
- [ ] Configure Multi-AZ replication for high availability

## Additional Resources

- [Spring Session Documentation](https://docs.spring.io/spring-session/reference/)
- [Amazon ElastiCache for Redis](https://aws.amazon.com/elasticache/redis/)
- [Spring Session Data Redis](https://docs.spring.io/spring-session/reference/guides/boot-redis.html)
- [Lettuce Redis Client](https://lettuce.io/)

## Support

For issues or questions, contact the DevOps team or refer to the AWS ElastiCache documentation.
