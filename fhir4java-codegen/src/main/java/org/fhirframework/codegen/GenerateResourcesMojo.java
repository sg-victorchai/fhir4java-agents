package org.fhirframework.codegen;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Maven plugin for generating Java classes from FHIR StructureDefinitions.
 * <p>
 * This goal is bound to the generate-sources phase by default and will scan
 * a directory for StructureDefinition JSON files, generating corresponding
 * Java resource classes.
 * </p>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateResourcesMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing StructureDefinition JSON files.
     * <p>
     * Files matching the pattern StructureDefinition-*.json will be processed.
     * </p>
     */
    @Parameter(property = "fhir.codegen.structureDefinitionDir", required = true)
    private File structureDefinitionDir;

    /**
     * Output directory for generated Java source files.
     * <p>
     * Defaults to target/generated-sources/fhir
     * </p>
     */
    @Parameter(
            property = "fhir.codegen.outputDirectory",
            defaultValue = "${project.build.directory}/generated-sources/fhir"
    )
    private File outputDirectory;

    /**
     * Package name for generated classes.
     * <p>
     * Defaults to org.fhirframework.generated.resources
     * </p>
     */
    @Parameter(
            property = "fhir.codegen.packageName",
            defaultValue = "org.fhirframework.generated.resources"
    )
    private String packageName;

    /**
     * Skip code generation.
     * <p>
     * Useful for disabling generation in certain profiles or environments.
     * </p>
     */
    @Parameter(property = "fhir.codegen.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("FHIR code generation is skipped");
            return;
        }

        getLog().info("========================================");
        getLog().info("FHIR Resource Code Generation");
        getLog().info("========================================");
        getLog().info("StructureDefinition directory: " + structureDefinitionDir.getAbsolutePath());
        getLog().info("Output directory: " + outputDirectory.getAbsolutePath());
        getLog().info("Package name: " + packageName);
        getLog().info("========================================");

        // Validate inputs
        if (!structureDefinitionDir.exists()) {
            getLog().warn("StructureDefinition directory does not exist: " +
                    structureDefinitionDir.getAbsolutePath());
            getLog().warn("Skipping code generation");
            return;
        }

        if (!structureDefinitionDir.isDirectory()) {
            throw new MojoFailureException("StructureDefinition path is not a directory: " +
                    structureDefinitionDir.getAbsolutePath());
        }

        // Create output directory if it doesn't exist
        if (!outputDirectory.exists()) {
            boolean created = outputDirectory.mkdirs();
            if (!created) {
                throw new MojoFailureException("Failed to create output directory: " +
                        outputDirectory.getAbsolutePath());
            }
        }

        try {
            // Generate classes
            FhirResourceGenerator generator = new FhirResourceGenerator();
            List<String> generatedClasses = generator.generateClasses(
                    structureDefinitionDir,
                    outputDirectory,
                    packageName
            );

            if (generatedClasses.isEmpty()) {
                getLog().warn("No classes were generated. Check that StructureDefinition files exist " +
                        "and match the pattern: StructureDefinition-*.json");
            } else {
                getLog().info("========================================");
                getLog().info("Generated " + generatedClasses.size() + " class(es):");
                for (String className : generatedClasses) {
                    getLog().info("  - " + className);
                }
                getLog().info("========================================");

                // Add the output directory to the project's compile source roots
                // so that the generated classes are compiled
                project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
                getLog().info("Added generated sources to compile path: " + outputDirectory.getAbsolutePath());
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate FHIR resource classes", e);
        }

        getLog().info("FHIR code generation complete");
    }
}
