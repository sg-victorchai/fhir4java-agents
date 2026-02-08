package org.fhirframework.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for generating Java classes from FHIR StructureDefinitions.
 */
public class FhirResourceGenerator {

    private static final Logger log = LoggerFactory.getLogger(FhirResourceGenerator.class);

    private final StructureDefinitionParser parser;
    private final JavaClassBuilder classBuilder;

    public FhirResourceGenerator() {
        this.parser = new StructureDefinitionParser();
        this.classBuilder = new JavaClassBuilder();
    }

    /**
     * Generate Java classes from all StructureDefinition files in a directory.
     *
     * @param structureDefinitionDir Directory containing StructureDefinition JSON files
     * @param outputDirectory        Output directory for generated Java sources
     * @param packageName            Package name for generated classes
     * @return List of generated class names
     * @throws IOException If files cannot be read or written
     */
    public List<String> generateClasses(File structureDefinitionDir, File outputDirectory, String packageName)
            throws IOException {
        
        if (!structureDefinitionDir.exists() || !structureDefinitionDir.isDirectory()) {
            log.warn("StructureDefinition directory does not exist or is not a directory: {}",
                    structureDefinitionDir.getAbsolutePath());
            return new ArrayList<>();
        }

        log.info("Scanning for StructureDefinitions in: {}", structureDefinitionDir.getAbsolutePath());
        log.info("Output directory: {}", outputDirectory.getAbsolutePath());
        log.info("Package name: {}", packageName);

        List<String> generatedClasses = new ArrayList<>();
        File[] files = structureDefinitionDir.listFiles((dir, name) ->
                name.startsWith("StructureDefinition-") && name.endsWith(".json"));

        if (files == null || files.length == 0) {
            log.warn("No StructureDefinition files found matching pattern: StructureDefinition-*.json");
            return generatedClasses;
        }

        log.info("Found {} StructureDefinition file(s)", files.length);

        for (File file : files) {
            try {
                log.info("Processing: {}", file.getName());

                // Parse the StructureDefinition
                StructureDefinitionParser.StructureDefinitionMetadata metadata = parser.parse(file);

                // Generate Java class
                classBuilder.build(metadata, packageName, outputDirectory);

                String generatedClassName = packageName + "." + metadata.getName();
                generatedClasses.add(generatedClassName);

                log.info("Successfully generated class: {}", generatedClassName);

            } catch (Exception e) {
                log.error("Failed to generate class from {}: {}", file.getName(), e.getMessage(), e);
                // Continue processing other files
            }
        }

        log.info("Code generation complete. Generated {} class(es)", generatedClasses.size());
        return generatedClasses;
    }

    /**
     * Generate a single Java class from a StructureDefinition file.
     *
     * @param structureDefinitionFile The StructureDefinition JSON file
     * @param outputDirectory         Output directory for generated Java source
     * @param packageName             Package name for generated class
     * @return The generated class name, or null if generation failed
     * @throws IOException If file cannot be read or written
     */
    public String generateClass(File structureDefinitionFile, File outputDirectory, String packageName)
            throws IOException {
        
        log.info("Generating class from: {}", structureDefinitionFile.getName());

        // Parse the StructureDefinition
        StructureDefinitionParser.StructureDefinitionMetadata metadata = parser.parse(structureDefinitionFile);

        // Generate Java class
        classBuilder.build(metadata, packageName, outputDirectory);

        String generatedClassName = packageName + "." + metadata.getName();
        log.info("Successfully generated class: {}", generatedClassName);

        return generatedClassName;
    }
}
