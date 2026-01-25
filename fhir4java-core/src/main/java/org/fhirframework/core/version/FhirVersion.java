package org.fhirframework.core.version;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of supported FHIR versions.
 * <p>
 * Supports multi-version FHIR with R5 as primary and R4B for backward compatibility.
 * </p>
 */
public enum FhirVersion {

    R5("r5", "5.0.0"),
    R4B("r4b", "4.3.0");

    private final String code;
    private final String version;

    private static final Map<String, FhirVersion> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(v -> v.code.toLowerCase(), Function.identity()));

    FhirVersion(String code, String version) {
        this.code = code;
        this.version = version;
    }

    /**
     * Returns the lowercase code for URL path matching (e.g., "r5", "r4b").
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the full FHIR version string (e.g., "5.0.0", "4.3.0").
     */
    public String getVersion() {
        return version;
    }

    /**
     * Parses a FHIR version from its code (case-insensitive).
     *
     * @param code the version code (e.g., "r5", "R5", "r4b", "R4B")
     * @return the corresponding FhirVersion
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static FhirVersion fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("FHIR version code cannot be null or blank");
        }

        FhirVersion version = CODE_MAP.get(code.toLowerCase());
        if (version == null) {
            throw new IllegalArgumentException("Unknown FHIR version code: " + code +
                    ". Supported versions: " + Arrays.toString(values()));
        }
        return version;
    }

    /**
     * Checks if the given code represents a valid FHIR version.
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return CODE_MAP.containsKey(code.toLowerCase());
    }

    /**
     * Returns the default FHIR version (R5).
     */
    public static FhirVersion getDefault() {
        return R5;
    }
}
