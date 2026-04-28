# Multi-stage Dockerfile for ResortsLite Spring Boot Application
# Stage 1: Build stage using Maven
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage using explicit base image
FROM eclipse-temurin:8-jdk

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Create directories for reports and backups
RUN mkdir -p /tmp/reports /tmp/backups && \
    chown -R appuser:appuser /app /tmp/reports /tmp/backups

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Set timezone
ENV TZ=UTC

# Set Spring profile for Docker environment
ENV SPRING_PROFILES_ACTIVE=docker

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
