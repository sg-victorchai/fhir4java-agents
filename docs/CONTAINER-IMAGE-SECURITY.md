# Container Image Security Vulnerabilities and Remediation

This document details the security vulnerabilities identified in the FHIR4Java container image and the remediation steps taken.

## Summary

| Category | Vulnerabilities Found | Status |
|----------|----------------------|--------|
| Java Dependencies | 17 | Fixed |
| OS Packages (Alpine) | 4 | Eliminated |
| **Total** | **21** | **All Resolved** |

## Scan Details

- **Scan Tool**: Docker Scout
- **Scan Date**: April 2026
- **Image**: fhir4java-server
- **Base Image (Before)**: `eclipse-temurin:25-jre-alpine` / `eclipse-temurin:25-jre`
- **Base Image (After)**: `gcr.io/distroless/java25-debian13:nonroot`

---

## Java Dependency Vulnerabilities

### Critical Severity

#### 1. HAPI FHIR Core Libraries (CVE-2026-34359, CVE-2026-34360, CVE-2026-34361)

| Field | Value |
|-------|-------|
| **Package** | `ca.uhn.hapi.fhir/org.hl7.fhir.*` |
| **Vulnerable Version** | 6.7.9 |
| **Fixed Version** | 6.9.4 |
| **CVSS Score** | 9.3 (Critical) |

**CVE-2026-34361 - SSRF with Authentication Token Theft**
- The FHIR Validator HTTP service exposes an unauthenticated `/loadIG` endpoint that makes outbound HTTP requests to attacker-controlled URLs
- Combined with a `startsWith()` URL prefix matching flaw in the credential provider, an attacker can steal authentication tokens (Bearer, Basic, API keys)

**CVE-2026-34359 - Authentication Credential Leakage**
- `ManagedWebAccessUtils.getServer()` uses `String.startsWith()` to match request URLs against configured server URLs
- An attacker-controlled domain like `http://tx.fhir.org.attacker.com` matches the prefix and receives credentials

**CVE-2026-34360 - Blind SSRF via /loadIG Endpoint**
- The `/loadIG` HTTP endpoint accepts a user-supplied URL and makes server-side HTTP requests without hostname/domain validation
- Enables internal network probing and cloud metadata endpoint access

**Remediation**: Updated `hapi-fhir-core.version` to 6.9.4 in `pom.xml`

---

#### 2. PostgreSQL JDBC Driver (CVE-2025-49146)

| Field | Value |
|-------|-------|
| **Package** | `org.postgresql/postgresql` |
| **Vulnerable Version** | 42.7.4 |
| **Fixed Version** | 42.7.7 |
| **CVSS Score** | High |

**Description**: When the PostgreSQL JDBC driver is configured with channel binding set to `required`, the driver incorrectly allows connections to proceed with authentication methods that do not support channel binding (password, MD5, GSS, SSPI).

**Impact**: Man-in-the-middle attacker could intercept connections believed to be protected by channel binding.

**Remediation**: Updated `postgresql.version` to 42.7.7 in `pom.xml`

---

### High Severity

#### 3. Apache Tomcat (Multiple CVEs)

| Field | Value |
|-------|-------|
| **Package** | `org.apache.tomcat.embed/tomcat-embed-core` |
| **Vulnerable Version** | 10.1.48 |
| **Fixed Version** | 10.1.54 |

**Remediation**: Added `<tomcat.version>10.1.54</tomcat.version>` to override Spring Boot managed version

---

#### 4. Spring Security (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `org.springframework.security/spring-security-web` |
| **Vulnerable Version** | 6.5.6 |
| **Fixed Version** | 6.5.9 |

**Remediation**: Added `<spring-security.version>6.5.9</spring-security.version>` to override Spring Boot managed version

---

#### 5. Spring WebMVC (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `org.springframework/spring-webmvc` |
| **Vulnerable Version** | 6.2.12 |
| **Fixed Version** | 6.2.17 |

**Remediation**: Added `<spring-framework.version>6.2.17</spring-framework.version>` to override Spring Boot managed version

---

#### 6. Netty HTTP/2 Codec (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `io.netty/netty-codec-http2` |
| **Vulnerable Version** | 4.1.128.Final |
| **Fixed Version** | 4.1.132.Final |

**Remediation**: Added `<netty.version>4.1.132.Final</netty.version>` to override Spring Boot managed version

---

#### 7. Thymeleaf (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `org.thymeleaf/thymeleaf` |
| **Vulnerable Version** | 3.1.3.RELEASE |
| **Fixed Version** | 3.1.4.RELEASE |

**Remediation**: Added `<thymeleaf.version>3.1.4.RELEASE</thymeleaf.version>` to override Spring Boot managed version

---

#### 8. Jackson Core (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `com.fasterxml.jackson.core/jackson-core` |
| **Vulnerable Version** | 2.19.2 |
| **Fixed Version** | 2.21.2 |

**Remediation**:
- Updated `<jackson.version>2.21.2</jackson.version>`
- Added Jackson BOM import to ensure all Jackson modules use consistent version

---

#### 9. Logback Core (Security Patches)

| Field | Value |
|-------|-------|
| **Package** | `ch.qos.logback/logback-core` |
| **Vulnerable Version** | 1.5.20 |
| **Fixed Version** | 1.5.25 |

**Remediation**: Added `<logback.version>1.5.25</logback.version>` to override Spring Boot managed version

---

### Medium Severity

#### 10. Apache Commons Lang3

| Field | Value |
|-------|-------|
| **Package** | `org.apache.commons/commons-lang3` |
| **Vulnerable Version** | 3.17.0 |
| **Fixed Version** | 3.18.0 |

**Remediation**: Added explicit dependency management for `commons-lang3` version 3.18.0

---

### Additional Dependency Updates

| Package | Old Version | New Version | Reason |
|---------|-------------|-------------|--------|
| HAPI FHIR | 7.6.0 | 8.8.1 | Latest stable with security fixes |
| Flyway | 10.21.0 | 11.20.3 | CVE-2025-55163, CVE-2025-58057, CVE-2025-24790 |
| AWS SDK | 2.29.0 | 2.42.37 | Security and compatibility updates |
| AWS Secrets Manager JDBC | 1.0.12 | 2.1.0 | Major version security update |
| Cucumber | 7.20.1 | 7.34.3 | Testing framework update |
| Testcontainers | 2.0.2 | 2.0.5 | Bug fixes |
| Lombok | 1.18.42 | 1.18.44 | Minor update |

---

## OS-Level Vulnerabilities (Container Base Image)

### Original Issues (Alpine-based Image)

#### 1. curl (CVE in alpine/curl 8.17.0-r1)

| Field | Value |
|-------|-------|
| **Package** | `alpine/curl` |
| **Vulnerable Version** | 8.17.0-r1 |
| **Status** | **Eliminated** |

**Resolution**: Removed curl package; switched to distroless image

---

#### 2. busybox (CVE in alpine/busybox 1.37.0-r30)

| Field | Value |
|-------|-------|
| **Package** | `alpine/busybox` |
| **Vulnerable Version** | 1.37.0-r30 |
| **Fixed Version** | No fix available |
| **Status** | **Eliminated** |

**Analysis**: busybox is a core Alpine component providing shell and utilities. Cannot be patched within Alpine.

**Resolution**: Switched to Google Distroless image which has no busybox

---

#### 3. nghttp2 (CVE in alpine/nghttp2 1.68.0-r0)

| Field | Value |
|-------|-------|
| **Package** | `alpine/nghttp2` |
| **Vulnerable Version** | 1.68.0-r0 |
| **Fixed Version** | No fix available |
| **Status** | **Eliminated** |

**Analysis**: HTTP/2 library, pulled in as dependency of curl.

**Resolution**: Removed by eliminating curl; switched to distroless image

---

#### 4. Ubuntu Packages (docker/Dockerfile - Previously Ubuntu-based)

| CVE | Package | Fixed Version | Status |
|-----|---------|---------------|--------|
| CVE-2025-66382 | `ubuntu/expat` | No fix | **Eliminated** |
| CVE-2025-45582 | `ubuntu/tar` | No fix | **Eliminated** |
| CVE-2024-2236 | `ubuntu/libgcrypt20` | No fix | **Eliminated** |
| CVE-2024-56433 | `ubuntu/shadow` | No fix | **Eliminated** |

**Resolution**: Switched from `eclipse-temurin:25-jre` (Ubuntu) to `gcr.io/distroless/java25-debian13:nonroot`

---

## Remediation Strategy: Switch to Distroless

### Why Distroless?

Google Distroless images contain only:
- The application
- Runtime dependencies (JRE)
- CA certificates and timezone data

They do NOT contain:
- Shell (`/bin/sh`, `/bin/bash`)
- Package manager (`apt`, `apk`)
- Core utilities (`busybox`, `coreutils`)
- Network tools (`curl`, `wget`)

### Attack Surface Comparison

| Component | Alpine | Ubuntu | Distroless |
|-----------|--------|--------|------------|
| Shell | Yes (busybox) | Yes (bash) | **No** |
| Package Manager | Yes (apk) | Yes (apt) | **No** |
| busybox | Yes | No | **No** |
| curl/wget | Installable | Installable | **No** |
| Total Packages | ~15 | ~100+ | **~3** |

### Dockerfile Changes

**Before (Alpine)**:
```dockerfile
FROM eclipse-temurin:25-jre-alpine

RUN apk update && apk upgrade --no-cache
RUN addgroup -S fhir && adduser -S fhir -G fhir
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R fhir:fhir /app
USER fhir
HEALTHCHECK CMD wget -q -O /dev/null http://localhost:8080/actuator/health
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**After (Distroless)**:
```dockerfile
FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app
COPY --chown=nonroot:nonroot --from=builder /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0"
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Key Changes

| Aspect | Before | After |
|--------|--------|-------|
| Base Image | `eclipse-temurin:25-jre-alpine` | `gcr.io/distroless/java25-debian13:nonroot` |
| User Creation | `addgroup`/`adduser` commands | Built-in `nonroot` user (UID 65532) |
| File Ownership | `chown` command | `COPY --chown=nonroot:nonroot` |
| Health Check | `wget` in container | External orchestrator (K8s probes) |
| Shell | Required for ENTRYPOINT | Not needed (exec form) |
| Package Updates | `apk upgrade` | Not applicable (immutable) |

---

## Health Check Strategy

Since distroless has no shell or tools, health checks must be external:

### Kubernetes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

### Docker Compose

Health checks removed from Dockerfile. Use external monitoring or accept that container restart policy handles failures.

### Spring Boot Actuator Endpoints

- Health: `http://localhost:8080/actuator/health`
- Liveness: `http://localhost:8080/actuator/health/liveness`
- Readiness: `http://localhost:8080/actuator/health/readiness`

---

## Verification

### Verify Java Dependencies

```bash
./mvnw dependency:tree -pl fhir4java-server | grep -E "(logback|tomcat|spring-security|netty|jackson|org.hl7.fhir)"
```

### Build and Scan New Image

```bash
# Build the image
docker build -t fhir4java-server:latest -f docker/Dockerfile .

# Scan with Docker Scout
docker scout cves fhir4java-server:latest

# Or scan with Trivy
trivy image fhir4java-server:latest
```

---

## References

- [Google Distroless GitHub](https://github.com/GoogleContainerTools/distroless)
- [HAPI FHIR Security Advisories](https://hapifhir.io/hapi-fhir/docs/introduction/changelog.html)
- [PostgreSQL JDBC CVE-2025-49146](https://www.postgresql.org/about/news/postgresql-jdbc-4277-security-update-for-cve-2025-49146-3088/)
- [Spring Security Releases](https://spring.io/projects/spring-security#learn)
- [Docker Scout Documentation](https://docs.docker.com/scout/)

---

## Document History

| Date | Version | Author | Changes |
|------|---------|--------|---------|
| 2026-04-21 | 1.0 | Security Team | Initial document |
