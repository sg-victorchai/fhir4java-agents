package org.fhirframework.core.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.version.FhirVersion;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads custom FHIR conformance resources from the configuration directories.
 * <p>
 * Scans the following directories for each FHIR version:
 * <ul>
 *   <li>{@code fhir-config/{version}/profiles/} - StructureDefinition JSON files</li>
 *   <li>{@code fhir-config/{version}/terminology/} - CodeSystem and ValueSet JSON files</li>
 * </ul>
 * </p>
 */
@Component
public class CustomResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceLoader.class);

    private final FhirContextFactory contextFactory;
    private final ResourcePatternResolver resourceResolver;

    @Value("${fhir4java.config.base-path:classpath:fhir-config}")
    private String configBasePath;

    public CustomResourceLoader(FhirContextFactory contextFactory) {
        this.contextFactory = contextFactory;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * Load all custom conformance resources for a FHIR version.
     *
     * @param version The FHIR version to load resources for
     * @return CustomResourceBundle containing all loaded resources
     */
    public CustomResourceBundle loadCustomResources(FhirVersion version) {
        log.info("Loading custom resources for FHIR {}", version.getCode());

        FhirContext ctx = contextFactory.getContext(version);
        String versionPath = version.name().toLowerCase(); // "r5" or "r4b"

        // Load StructureDefinitions
        Map<String, StructureDefinition> structDefs = loadStructureDefinitions(ctx, versionPath);

        // Load CodeSystems
        Map<String, CodeSystem> codeSystems = loadCodeSystems(ctx, versionPath);

        // Load ValueSets
        Map<String, ValueSet> valueSets = loadValueSets(ctx, versionPath);

        CustomResourceBundle bundle = CustomResourceBundle.builder()
                .structureDefinitions(structDefs)
                .codeSystems(codeSystems)
                .valueSets(valueSets)
                .build();

        log.info("Loaded custom resources for FHIR {}: {} StructureDefinitions, {} CodeSystems, {} ValueSets",
                version.getCode(), structDefs.size(), codeSystems.size(), valueSets.size());

        return bundle;
    }

    /**
     * Load StructureDefinitions from the profiles directory.
     */
    private Map<String, StructureDefinition> loadStructureDefinitions(FhirContext ctx, String versionPath) {
        Map<String, StructureDefinition> result = new HashMap<>();
        String pattern = configBasePath + "/" + versionPath + "/profiles/StructureDefinition-*.json";

        try {
            Resource[] resources = resourceResolver.getResources(pattern);
            IParser parser = ctx.newJsonParser();

            for (Resource resource : resources) {
                try {
                    String json = readResource(resource);
                    StructureDefinition sd = parser.parseResource(StructureDefinition.class, json);

                    if (sd.getUrl() != null) {
                        result.put(sd.getUrl(), sd);
                        log.debug("Loaded StructureDefinition: {} ({})", sd.getName(), sd.getUrl());
                    } else {
                        log.warn("StructureDefinition in {} has no URL, skipping", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse StructureDefinition from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("No StructureDefinitions found at pattern {}: {}", pattern, e.getMessage());
        }

        return result;
    }

    /**
     * Load CodeSystems from the terminology directory.
     */
    private Map<String, CodeSystem> loadCodeSystems(FhirContext ctx, String versionPath) {
        Map<String, CodeSystem> result = new HashMap<>();
        String pattern = configBasePath + "/" + versionPath + "/terminology/CodeSystem-*.json";

        try {
            Resource[] resources = resourceResolver.getResources(pattern);
            IParser parser = ctx.newJsonParser();

            for (Resource resource : resources) {
                try {
                    String json = readResource(resource);
                    CodeSystem cs = parser.parseResource(CodeSystem.class, json);

                    if (cs.getUrl() != null) {
                        result.put(cs.getUrl(), cs);
                        log.debug("Loaded CodeSystem: {} ({})", cs.getName(), cs.getUrl());
                    } else {
                        log.warn("CodeSystem in {} has no URL, skipping", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse CodeSystem from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("No CodeSystems found at pattern {}: {}", pattern, e.getMessage());
        }

        return result;
    }

    /**
     * Load ValueSets from the terminology directory.
     */
    private Map<String, ValueSet> loadValueSets(FhirContext ctx, String versionPath) {
        Map<String, ValueSet> result = new HashMap<>();
        String pattern = configBasePath + "/" + versionPath + "/terminology/ValueSet-*.json";

        try {
            Resource[] resources = resourceResolver.getResources(pattern);
            IParser parser = ctx.newJsonParser();

            for (Resource resource : resources) {
                try {
                    String json = readResource(resource);
                    ValueSet vs = parser.parseResource(ValueSet.class, json);

                    if (vs.getUrl() != null) {
                        result.put(vs.getUrl(), vs);
                        log.debug("Loaded ValueSet: {} ({})", vs.getName(), vs.getUrl());
                    } else {
                        log.warn("ValueSet in {} has no URL, skipping", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse ValueSet from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("No ValueSets found at pattern {}: {}", pattern, e.getMessage());
        }

        return result;
    }

    /**
     * Read a Spring Resource as a String.
     */
    private String readResource(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
