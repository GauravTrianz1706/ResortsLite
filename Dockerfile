# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy dependency manifests first for layer caching
COPY pom.xml .

# Download all dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy full source tree (excludes files listed in .dockerignore)
COPY src ./src

# Build the application JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM amazoncorretto:8

# Metadata
LABEL maintainer="ResortsLite Team" \
      application="resortsLite" \
      version="1.0.0"

# Timezone configuration
ENV TZ=UTC

# Create a non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -s /sbin/nologin appuser

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Create directories for reports and backups
RUN mkdir -p /var/reports /var/backups/nightly && \
    chown -R appuser:appgroup /app /var/reports /var/backups/nightly

# Switch to non-root user
USER appuser

# Application port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=UTC"

# Spring profile
ENV SPRING_PROFILES_ACTIVE=docker

# Redis defaults (override via Kubernetes Secret / env vars)
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379

# Report / backup paths
ENV REPORT_BASE_PATH=/var/reports
ENV BACKUP_PATH=/var/backups/nightly

# Payment API URL
ENV PAYMENT_API_URL=http://payment-service:9090/payments/charge

# Entrypoint with graceful shutdown support
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
