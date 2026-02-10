# Build stage
FROM eclipse-temurin:25-jdk AS builder

# Install unzip and curl for Maven wrapper
RUN apt-get update && apt-get install -y unzip curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Maven wrapper and POM files first for better caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY fhir4java-codegen/pom.xml fhir4java-codegen/
COPY fhir4java-core/pom.xml fhir4java-core/
COPY fhir4java-persistence/pom.xml fhir4java-persistence/
COPY fhir4java-plugin/pom.xml fhir4java-plugin/
COPY fhir4java-api/pom.xml fhir4java-api/
COPY fhir4java-server/pom.xml fhir4java-server/

# Make mvnw executable and create unzip wrapper to handle overwrites
RUN chmod +x mvnw && \
    mv /usr/bin/unzip /usr/bin/unzip-orig && \
    echo '#!/bin/sh\n/usr/bin/unzip-orig -o "$@"' > /usr/bin/unzip && \
    chmod +x /usr/bin/unzip

# Download dependencies (for better layer caching)
# Retry up to 3 times to handle transient network errors
RUN for i in 1 2 3; do ./mvnw dependency:go-offline -B && break || sleep 10; done

# Copy source code
COPY fhir4java-codegen/src fhir4java-codegen/src
COPY fhir4java-core/src fhir4java-core/src
COPY fhir4java-persistence/src fhir4java-persistence/src
COPY fhir4java-plugin/src fhir4java-plugin/src
COPY fhir4java-api/src fhir4java-api/src
COPY fhir4java-server/src fhir4java-server/src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre

# Install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create non-root user
RUN groupadd -r fhir && useradd -r -g fhir fhir

# Copy the built artifact
COPY --from=builder /app/fhir4java-server/target/*.jar app.jar

# Set ownership
RUN chown -R fhir:fhir /app

USER fhir

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
