# Multi-stage build for FHIR4Java
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY fhir4java-core/pom.xml fhir4java-core/
COPY fhir4java-persistence/pom.xml fhir4java-persistence/
COPY fhir4java-plugin/pom.xml fhir4java-plugin/
COPY fhir4java-api/pom.xml fhir4java-api/
COPY fhir4java-server/pom.xml fhir4java-server/

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY . .
RUN ./mvnw clean package -DskipTests -pl fhir4java-server -am

# Extract layers for better caching
RUN java -Djarmode=layertools -jar fhir4java-server/target/fhir4java-server-*.jar extract --destination /app/extracted

# Runtime stage - Google Distroless for minimal attack surface
# No shell, no package manager, no busybox - minimal CVE exposure
FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app

# Copy extracted layers with nonroot ownership (UID 65532)
# Distroless nonroot user is built-in, no need to create
COPY --chown=nonroot:nonroot --from=builder /app/extracted/dependencies/ ./
COPY --chown=nonroot:nonroot --from=builder /app/extracted/spring-boot-loader/ ./
COPY --chown=nonroot:nonroot --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --chown=nonroot:nonroot --from=builder /app/extracted/application/ ./

# JVM options for container (set via JAVA_TOOL_OPTIONS)
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Distroless entrypoint is "java -jar", but we use Spring Boot layered launcher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
