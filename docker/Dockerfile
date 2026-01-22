# Build stage
FROM eclipse-temurin:21-jdk AS builder

# Install unzip for Maven wrapper
RUN apt-get update && apt-get install -y unzip && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Maven wrapper and POM files first for better caching
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY fhir4java-core/pom.xml fhir4java-core/
COPY fhir4java-persistence/pom.xml fhir4java-persistence/
COPY fhir4java-plugin/pom.xml fhir4java-plugin/
COPY fhir4java-api/pom.xml fhir4java-api/
COPY fhir4java-server/pom.xml fhir4java-server/

# Download dependencies
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY fhir4java-core/src fhir4java-core/src
COPY fhir4java-persistence/src fhir4java-persistence/src
COPY fhir4java-plugin/src fhir4java-plugin/src
COPY fhir4java-api/src fhir4java-api/src
COPY fhir4java-server/src fhir4java-server/src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre

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
