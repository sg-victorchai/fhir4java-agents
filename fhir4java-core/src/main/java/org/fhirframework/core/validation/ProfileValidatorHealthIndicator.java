package org.fhirframework.core.validation;

import org.fhirframework.core.version.FhirVersion;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for ProfileValidator initialization status.
 * Reports on which FHIR version validators are available and operational.
 */
@Component
public class ProfileValidatorHealthIndicator implements HealthIndicator {

    private final ProfileValidator profileValidator;
    private final ValidationConfig validationConfig;

    public ProfileValidatorHealthIndicator(ProfileValidator profileValidator, 
                                          ValidationConfig validationConfig) {
        this.profileValidator = profileValidator;
        this.validationConfig = validationConfig;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // Check if profile validator is disabled
        if (!validationConfig.isProfileValidatorEnabled()) {
            details.put("disabled", true);
            details.put("reason", "Profile validator disabled by configuration");
            return Health.up().withDetails(details).build();
        }

        // Get initialization status
        Map<FhirVersion, Boolean> initStatus = profileValidator.getInitializationStatus();
        Map<FhirVersion, Long> initDurations = profileValidator.getInitializationDurations();
        
        details.put("enabled", true);
        details.put("totalInitializationTime", profileValidator.getTotalInitializationTime() + "ms");
        
        // Add per-version status
        Map<String, Object> versionStatus = new HashMap<>();
        int successCount = 0;
        int totalCount = 0;
        
        for (FhirVersion version : FhirVersion.values()) {
            totalCount++;
            Map<String, Object> versionDetails = new HashMap<>();
            
            Boolean initialized = initStatus.get(version);
            Long duration = initDurations.get(version);
            
            if (initialized != null && initialized) {
                successCount++;
                versionDetails.put("status", "initialized");
                versionDetails.put("available", true);
            } else {
                versionDetails.put("status", "failed");
                versionDetails.put("available", false);
            }
            
            if (duration != null) {
                versionDetails.put("initializationTime", duration + "ms");
            }
            
            versionStatus.put(version.getCode(), versionDetails);
        }
        
        details.put("versions", versionStatus);
        details.put("successCount", successCount);
        details.put("totalCount", totalCount);
        
        // Determine health status based on health-check-mode
        ValidationConfig.HealthCheckMode mode = validationConfig.getHealthCheckMode();
        
        if (successCount == 0) {
            // No validators initialized successfully
            return switch (mode) {
                case STRICT -> Health.down()
                        .withDetails(details)
                        .withDetail("reason", "No validators initialized successfully")
                        .build();
                case WARN -> Health.up()
                        .withDetails(details)
                        .withDetail("warning", "No validators initialized successfully")
                        .build();
                case DISABLED -> Health.up().withDetails(details).build();
            };
        } else if (successCount < totalCount) {
            // Some validators failed
            details.put("warning", "Some validators failed to initialize");
            return Health.up().withDetails(details).build();
        } else {
            // All validators initialized successfully
            return Health.up().withDetails(details).build();
        }
    }
}
