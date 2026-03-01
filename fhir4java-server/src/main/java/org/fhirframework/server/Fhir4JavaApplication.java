package org.fhirframework.server;

import org.fhirframework.core.tenant.TenantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * FHIR4Java Agents - Main Application Entry Point
 *
 * An enterprise-grade HL7 FHIR R5 server implementation with AI-powered plugin architecture.
 */
@SpringBootApplication(scanBasePackages = "org.fhirframework")
@EnableJpaRepositories(basePackages = "org.fhirframework.persistence.repository")
@EntityScan(basePackages = "org.fhirframework.persistence.entity")
@EnableJpaAuditing
@EnableCaching
@EnableConfigurationProperties(TenantProperties.class)
public class Fhir4JavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Fhir4JavaApplication.class, args);
    }
}
