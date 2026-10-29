# Multi-stage Dockerfile for GitContext Backend
# Stage 1: Build stage with JDK 25
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn/

# Normalize line endings and permissions for mvnw
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw

# Resolve dependencies for Docker layer caching
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and package application jar
COPY src src/
RUN ./mvnw clean package -DskipTests -B

# Compile standalone Java healthcheck utility
COPY HealthCheck.java ./
RUN javac HealthCheck.java

# Stage 2: Runtime stage with minimal JRE 25
FROM eclipse-temurin:25-jre

# Create non-root system group and user
RUN groupadd -g 10001 gitcontext && \
    useradd -u 10001 -g gitcontext -s /bin/bash -m gitcontext

WORKDIR /app

# Create data directory for repository clones, Lucene indices, and model weights owned by gitcontext user
RUN mkdir -p /app/data && chown -R gitcontext:gitcontext /app

# Copy application jar and compiled health check utility from builder
COPY --from=builder /build/target/gitcontext-backend-*.jar /app/app.jar
COPY --from=builder /build/HealthCheck.class /app/HealthCheck.class

# Ensure non-root ownership
RUN chown -R gitcontext:gitcontext /app

# Run container as non-root user
USER gitcontext:gitcontext

ENV JAVA_OPTS=""
EXPOSE 8081

# Docker HEALTHCHECK on /actuator/health using zero-dependency Java client (no curl/wget needed)
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD ["java", "-cp", "/app", "HealthCheck"]

# Entrypoint supporting JAVA_OPTS environment variable
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
