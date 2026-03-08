# Plan: BDD Tests with H2 and PostgreSQL Profile Switching

## Summary

Enable BDD tests to run against both H2 (fast, in-memory) and PostgreSQL (via TestContainers) with profile-based switching. Includes upgrading TestContainers to fix Docker Engine 29.x compatibility.

---

## Problem Analysis

### Current State
- BDD tests use H2 in-memory database with `test` profile
- TestContainers 1.20.4 dependencies exist but not actively used
- H2SchemaInitializer provides programmatic schema creation (Flyway disabled)

### Docker Engine 29.x Compatibility Issue
- **User's Docker version**: v29.1.3
- **TestContainers 1.20.4** uses Docker API client version **1.32**
- **Docker Engine 29** requires minimum API version **1.44**
- **Result**: TestContainers 1.20.4 is INCOMPATIBLE with Docker Engine 29.x

### Error Observed
```
EnvironmentAndSystemPropertyClientProviderStrategy:
"client version 1.32 is too old. Minimum supported API version is 1.44"
```

---

## Solution Options

| Option | Approach | Pros | Cons |
|--------|----------|------|------|
| **A** | `docker-java.properties` with `api.version=1.44` | Quick fix, minimal changes | Workaround, not permanent |
| **B** | Upgrade to TestContainers 2.0.2+ | Native Docker 29 support, recommended path | Breaking changes in artifact names |

### Recommended: Option B (Upgrade to TestContainers 2.0.2)

**Rationale:**
- TestContainers 2.0.2 uses docker-java 3.7.0 with native Docker 29.x support
- Default API version is 1.44 (no workaround needed)
- Breaking changes are manageable (artifact name changes)

---

## Implementation Steps

### Step 1: Update TestContainers Version

**File:** `pom.xml` (parent)

```xml
<!-- Line ~53: Update version -->
<testcontainers.version>2.0.2</testcontainers.version>
```

### Step 2: Update TestContainers Dependencies

**File:** `fhir4java-server/pom.xml`

Replace existing dependencies (artifact names changed in 2.x):

```xml
<!-- TestContainers 2.x (new artifact naming) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Spring Boot TestContainers Support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

### Step 3: Create PostgreSQL Test Profile

**Create file:** `fhir4java-server/src/main/resources/application-test-postgres.yml`

```yaml
spring:
  config:
    activate:
      on-profile: test-postgres

  datasource:
    # Overridden by TestContainers @ServiceConnection
    url: jdbc:postgresql://localhost:5432/fhir4java_test
    driver-class-name: org.postgresql.Driver
    username: fhir4java
    password: fhir4java

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: fhir

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    schemas: fhir

  data:
    redis:
      repositories:
        enabled: false

  cache:
    type: simple

fhir4java:
  validation:
    profile-validator-enabled: false
```

### Step 4: Create Schema Init Script

**Create file:** `fhir4java-server/src/test/resources/db/init-schemas.sql`

```sql
-- Create required schemas for FHIR4Java
CREATE SCHEMA IF NOT EXISTS fhir;
CREATE SCHEMA IF NOT EXISTS careplan;
```

### Step 5: Create TestContainers Configuration

**Create file:** `fhir4java-server/src/test/java/org/fhirframework/server/bdd/PostgresTestContainersConfig.java`

```java
package org.fhirframework.server.bdd;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration
@Profile("test-postgres")
public class PostgresTestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("fhir4java_test")
                .withUsername("fhir4java")
                .withPassword("fhir4java")
                .withInitScript("db/init-schemas.sql");
    }
}
```

### Step 6: Update CucumberSpringConfig

**File:** `fhir4java-server/src/test/java/org/fhirframework/server/bdd/CucumberSpringConfig.java`

Add import for TestContainers config:

```java
@CucumberContextConfiguration
@SpringBootTest(
    classes = Fhir4JavaApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(PostgresTestContainersConfig.class)  // ADD THIS LINE
public class CucumberSpringConfig {
    // ... existing code
}
```

### Step 7: Add Maven Profiles

**File:** `fhir4java-server/pom.xml`

Add profiles section:

```xml
<profiles>
    <profile>
        <id>test-h2</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <spring.profiles.active>test</spring.profiles.active>
        </properties>
    </profile>
    <profile>
        <id>test-postgres</id>
        <properties>
            <spring.profiles.active>test-postgres</spring.profiles.active>
        </properties>
    </profile>
</profiles>
```

Update failsafe plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <systemPropertyVariables>
            <spring.profiles.active>${spring.profiles.active}</spring.profiles.active>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

---

## Files to Modify

| File | Change |
|------|--------|
| `pom.xml` | Update `testcontainers.version` to `2.0.2` |
| `fhir4java-server/pom.xml` | Update dependencies, add Maven profiles |
| `fhir4java-server/src/main/resources/application-test-postgres.yml` | **Create** - PostgreSQL test config |
| `fhir4java-server/src/test/resources/db/init-schemas.sql` | **Create** - Schema init script |
| `fhir4java-server/src/test/java/.../PostgresTestContainersConfig.java` | **Create** - TestContainers config |
| `fhir4java-server/src/test/java/.../CucumberSpringConfig.java` | Add `@Import` annotation |

---

## Test Commands

### Run with H2 (Default - Fast)

```bash
# Default behavior
./mvnw test -pl fhir4java-server -Dtest=CucumberIT

# Explicit H2 profile
./mvnw test -pl fhir4java-server -Dtest=CucumberIT -Ptest-h2
```

### Run with PostgreSQL (TestContainers)

```bash
# PostgreSQL profile
./mvnw test -pl fhir4java-server -Dtest=CucumberIT -Ptest-postgres

# Run specific feature
./mvnw test -pl fhir4java-server -Dtest=CucumberIT \
  -Dcucumber.filter.tags="@crud" -Ptest-postgres
```

---

## Verification

1. **Build succeeds** with new dependencies
2. **H2 tests pass** (regression check): `./mvnw test -pl fhir4java-server -Dtest=CucumberIT`
3. **PostgreSQL container starts**: Docker ps shows postgres:16-alpine running
4. **PostgreSQL tests pass**: `./mvnw test -pl fhir4java-server -Dtest=CucumberIT -Ptest-postgres`

---

## Performance Comparison

| Aspect | H2 | PostgreSQL |
|--------|----|----|
| Startup | ~2-3 sec | ~10-15 sec |
| Test speed | Fast | Slightly slower |
| Accuracy | Good for logic | Production-like |
| Docker required | No | Yes |

---

## References

- [TestContainers Docker 29 Issue #11210](https://github.com/testcontainers/testcontainers-java/issues/11210)
- [TestContainers Releases](https://github.com/testcontainers/testcontainers-java/releases)
- [Spring Boot TestContainers Docs](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
