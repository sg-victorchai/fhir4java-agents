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
COPY fhir4java-mcp/pom.xml fhir4java-mcp/
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
COPY fhir4java-mcp/src fhir4java-mcp/src
COPY fhir4java-server/src fhir4java-server/src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage - Google Distroless for minimal attack surface
# No shell, no package manager, no busybox - minimal CVE exposure
FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app

# Copy the built artifact with nonroot ownership (UID 65532)
# Distroless nonroot user is built-in, no need to create
COPY --chown=nonroot:nonroot --from=builder /app/fhir4java-server/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check note: Use orchestrator-level health checks (K8s livenessProbe, Docker Compose healthcheck)
# Distroless has no shell or wget, so HEALTHCHECK directive cannot be used here
# Spring Boot Actuator endpoint: /actuator/health

# JVM options for containers via JAVA_TOOL_OPTIONS (picked up automatically by JVM)
# MaxRAMPercentage=60.0 leaves ~40% for metaspace, thread stacks, native memory
# This is important for HAPI FHIR which uses significant non-heap memory
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -XX:+UseZGC -XX:MaxMetaspaceSize=256m"

# Distroless default entrypoint is equivalent to "java -jar"
ENTRYPOINT ["java", "-jar", "app.jar"]