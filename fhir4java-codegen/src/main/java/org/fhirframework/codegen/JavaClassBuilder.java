package org.fhirframework.codegen;

import com.squareup.javapoet.*;
import org.hl7.fhir.r5.model.*;
import ca.uhn.fhir.model.api.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds Java class files from StructureDefinition metadata using JavaPoet.
 */
public class JavaClassBuilder {

    private static final Logger log = LoggerFactory.getLogger(JavaClassBuilder.class);
    private static final Map<String, ClassName> TYPE_MAPPINGS = new HashMap<>();

    static {
        // FHIR primitive types
        TYPE_MAPPINGS.put("code", ClassName.get(CodeType.class));
        TYPE_MAPPINGS.put("string", ClassName.get(StringType.class));
        TYPE_MAPPINGS.put("boolean", ClassName.get(BooleanType.class));
        TYPE_MAPPINGS.put("integer", ClassName.get(IntegerType.class));
        TYPE_MAPPINGS.put("decimal", ClassName.get(DecimalType.class));
        TYPE_MAPPINGS.put("uri", ClassName.get(UriType.class));
        TYPE_MAPPINGS.put("url", ClassName.get(UrlType.class));
        TYPE_MAPPINGS.put("canonical", ClassName.get(CanonicalType.class));
        TYPE_MAPPINGS.put("dateTime", ClassName.get(DateTimeType.class));
        TYPE_MAPPINGS.put("date", ClassName.get(DateType.class));
        TYPE_MAPPINGS.put("instant", ClassName.get(InstantType.class));
        TYPE_MAPPINGS.put("time", ClassName.get(TimeType.class));
        TYPE_MAPPINGS.put("markdown", ClassName.get(MarkdownType.class));
        TYPE_MAPPINGS.put("id", ClassName.get(IdType.class));
        TYPE_MAPPINGS.put("oid", ClassName.get(OidType.class));
        TYPE_MAPPINGS.put("uuid", ClassName.get(UuidType.class));
        TYPE_MAPPINGS.put("base64Binary", ClassName.get(Base64BinaryType.class));
        TYPE_MAPPINGS.put("unsignedInt", ClassName.get(UnsignedIntType.class));
        TYPE_MAPPINGS.put("positiveInt", ClassName.get(PositiveIntType.class));
        
        // FHIR complex types
        TYPE_MAPPINGS.put("Identifier", ClassName.get(Identifier.class));
        TYPE_MAPPINGS.put("CodeableConcept", ClassName.get(CodeableConcept.class));
        TYPE_MAPPINGS.put("Coding", ClassName.get(Coding.class));
        TYPE_MAPPINGS.put("Quantity", ClassName.get(Quantity.class));
        TYPE_MAPPINGS.put("Reference", ClassName.get(Reference.class));
        TYPE_MAPPINGS.put("Period", ClassName.get(Period.class));
        TYPE_MAPPINGS.put("Range", ClassName.get(Range.class));
        TYPE_MAPPINGS.put("Ratio", ClassName.get(Ratio.class));
        TYPE_MAPPINGS.put("Annotation", ClassName.get(Annotation.class));
        TYPE_MAPPINGS.put("Attachment", ClassName.get(Attachment.class));
        TYPE_MAPPINGS.put("ContactPoint", ClassName.get(ContactPoint.class));
        TYPE_MAPPINGS.put("Address", ClassName.get(Address.class));
        TYPE_MAPPINGS.put("HumanName", ClassName.get(HumanName.class));
        TYPE_MAPPINGS.put("Money", ClassName.get(Money.class));
        TYPE_MAPPINGS.put("Duration", ClassName.get(Duration.class));
        TYPE_MAPPINGS.put("Count", ClassName.get(Count.class));
        TYPE_MAPPINGS.put("Distance", ClassName.get(Distance.class));
        TYPE_MAPPINGS.put("Age", ClassName.get(Age.class));
    }

    /**
     * Build and write a Java class from StructureDefinition metadata.
     *
     * @param metadata      The StructureDefinition metadata
     * @param packageName   The package name for the generated class
     * @param outputDir     The output directory for the generated source file
     * @throws IOException If file cannot be written
     */
    public void build(StructureDefinitionParser.StructureDefinitionMetadata metadata,
                     String packageName, File outputDir) throws IOException {
        log.info("Generating Java class for: {}", metadata.getName());

        // Create the main resource class
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(metadata.getName())
                .addModifiers(Modifier.PUBLIC)
                .superclass(DomainResource.class);

        // Add @ResourceDef annotation
        AnnotationSpec resourceDefAnnotation = AnnotationSpec.builder(ResourceDef.class)
                .addMember("name", "$S", metadata.getName())
                .addMember("profile", "$S", metadata.getUrl())
                .build();
        classBuilder.addAnnotation(resourceDefAnnotation);

        // Add Javadoc
        if (metadata.getDescription() != null) {
            classBuilder.addJavadoc(metadata.getDescription() + "\n");
        }

        // Step 1: Identify backbone elements and group nested children
        Map<String, StructureDefinitionParser.ElementDefinition> backboneElements = new HashMap<>();
        Map<String, List<StructureDefinitionParser.ElementDefinition>> backboneChildren = new HashMap<>();
        List<StructureDefinitionParser.ElementDefinition> rootElements = new ArrayList<>();

        for (StructureDefinitionParser.ElementDefinition element : metadata.getElements()) {
            if (element.isRootElement()) {
                continue; // Skip the resource root itself
            }

            int depth = element.getPathDepth();

            if (depth == 2) {
                // Root-level field
                if (element.isBackboneElement()) {
                    backboneElements.put(element.getPath(), element);
                } else {
                    rootElements.add(element);
                }
            } else if (depth > 2) {
                // Nested element - belongs to a backbone
                String parentPath = element.getParentPath();
                backboneChildren.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(element);
            }
        }

        // Step 2: Generate inner classes for backbone elements
        List<TypeSpec> backboneClasses = new ArrayList<>();
        for (Map.Entry<String, StructureDefinitionParser.ElementDefinition> entry : backboneElements.entrySet()) {
            String backbonePath = entry.getKey();
            StructureDefinitionParser.ElementDefinition backboneElement = entry.getValue();
            List<StructureDefinitionParser.ElementDefinition> children = backboneChildren.getOrDefault(backbonePath, new ArrayList<>());

            TypeSpec backboneClass = generateBackboneClass(
                backboneElement.getElementName(),
                children,
                metadata.getName()
            );
            backboneClasses.add(backboneClass);
        }

        // Add all backbone classes to the main resource class
        for (TypeSpec backboneClass : backboneClasses) {
            classBuilder.addType(backboneClass);
        }

        // Track generated field names to avoid duplicates (e.g., from slices)
        Set<String> generatedFields = new HashSet<>();

        // Step 3: Generate root-level fields (non-backbone elements)
        for (StructureDefinitionParser.ElementDefinition element : rootElements) {
            String fieldName = element.getElementName();
            if (generatedFields.contains(fieldName)) {
                log.debug("Skipping duplicate field '{}' in {}", fieldName, metadata.getName());
                continue;
            }
            generatedFields.add(fieldName);
            addFieldForElement(classBuilder, element, metadata.getName(), packageName);
        }

        // Step 4: Generate fields for backbone elements (using component types)
        for (Map.Entry<String, StructureDefinitionParser.ElementDefinition> entry : backboneElements.entrySet()) {
            StructureDefinitionParser.ElementDefinition backboneElement = entry.getValue();
            String fieldName = backboneElement.getElementName();
            if (generatedFields.contains(fieldName)) continue;
            generatedFields.add(fieldName);

            // Use the inner component class type instead of StringType
            addBackboneFieldForElement(classBuilder, backboneElement, metadata.getName(), packageName);
        }

        // Add fhirType() method
        MethodSpec fhirTypeMethod = MethodSpec.methodBuilder("fhirType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", metadata.getName())
                .build();
        classBuilder.addMethod(fhirTypeMethod);

        // Add getResourceType() method (required by Resource)
        // For custom resources, return null since they don't have an entry in ResourceType enum
        MethodSpec getResourceTypeMethod = MethodSpec.methodBuilder("getResourceType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("org.hl7.fhir.r5.model", "ResourceType"))
                .addStatement("return null") // Custom resources don't have a ResourceType enum entry
                .build();
        classBuilder.addMethod(getResourceTypeMethod);

        // Add copy() method (required by DomainResource)
        ClassName resourceClassName = ClassName.get(packageName, metadata.getName());
        MethodSpec.Builder copyMethodBuilder = MethodSpec.methodBuilder("copy")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(resourceClassName)
                .addStatement("$T dst = new $T()", resourceClassName, resourceClassName);

        // Copy all fields
        for (String fieldName : generatedFields) {
            copyMethodBuilder.addStatement("dst.$N = this.$N", fieldName, fieldName);
        }

        // Call parent copy method to copy base fields
        copyMethodBuilder.addStatement("copyValues(dst)");
        copyMethodBuilder.addStatement("return dst");

        classBuilder.addMethod(copyMethodBuilder.build());

        // Build and write the file
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .build();

        javaFile.writeTo(outputDir);
        log.info("Generated class: {}.{}", packageName, metadata.getName());
    }

    /**
     * Add a field for an element definition.
     */
    private void addFieldForElement(TypeSpec.Builder classBuilder,
                                    StructureDefinitionParser.ElementDefinition element,
                                    String resourceName,
                                    String packageName) {
        String fieldName = element.getElementName();
        TypeName fieldType = getFieldType(element);

        // Build the field
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName)
                .addModifiers(Modifier.PRIVATE);

        // Add @Child annotation
        AnnotationSpec.Builder childAnnotation = AnnotationSpec.builder(Child.class)
                .addMember("name", "$S", fieldName)
                .addMember("min", "$L", element.getMin());

        if (element.getMaxCardinality() == -1) {
            childAnnotation.addMember("max", "Child.MAX_UNLIMITED");
        } else {
            childAnnotation.addMember("max", "$L", element.getMaxCardinality());
        }

        fieldBuilder.addAnnotation(childAnnotation.build());

        // Add @Description annotation
        if (element.getShortDescription() != null) {
            AnnotationSpec descAnnotation = AnnotationSpec.builder(Description.class)
                    .addMember("shortDefinition", "$S", element.getShortDescription())
                    .build();
            fieldBuilder.addAnnotation(descAnnotation);
        }

        classBuilder.addField(fieldBuilder.build());

        // Add getter
        MethodSpec getter = MethodSpec.methodBuilder("get" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType)
                .addStatement("return this.$N", fieldName)
                .build();
        classBuilder.addMethod(getter);

        // Add setter
        MethodSpec setter = MethodSpec.methodBuilder("set" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(packageName, resourceName))
                .addParameter(fieldType, fieldName)
                .addStatement("this.$N = $N", fieldName, fieldName)
                .addStatement("return this")
                .build();
        classBuilder.addMethod(setter);
    }

    /**
     * Determine the Java field type for an element.
     */
    private TypeName getFieldType(StructureDefinitionParser.ElementDefinition element) {
        // Check if this is a backbone element - should use inner component class
        if (element.isBackboneElement()) {
            String componentClassName = capitalize(element.getElementName()) + "Component";
            ClassName componentType = ClassName.get("", componentClassName);

            boolean isList = element.getMaxCardinality() != 1;
            return isList
                ? ParameterizedTypeName.get(ClassName.get(List.class), componentType)
                : componentType;
        }

        // Check if this is a list (max > 1 or max = *)
        boolean isList = element.getMaxCardinality() != 1;

        if (element.getTypes() == null || element.getTypes().isEmpty()) {
            // No type specified, default to String
            TypeName baseType = ClassName.get(String.class);
            return isList ? ParameterizedTypeName.get(ClassName.get(List.class), baseType) : baseType;
        }

        // Get the first type (FHIR elements typically have one type)
        StructureDefinitionParser.TypeInfo typeInfo = element.getTypes().get(0);
        String typeCode = typeInfo.getCode();

        // Map to Java type
        TypeName baseType = TYPE_MAPPINGS.getOrDefault(typeCode, ClassName.get(StringType.class));

        // Wrap in List if needed
        if (isList) {
            return ParameterizedTypeName.get(ClassName.get(List.class), baseType);
        }

        return baseType;
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Generate an inner @Block class for a backbone element.
     *
     * @param componentName The name of the component (e.g., "packaging" -> "PackagingComponent")
     * @param children The child elements of this backbone
     * @param resourceName The parent resource name (for fluent setters)
     * @return TypeSpec for the backbone component class
     */
    private TypeSpec generateBackboneClass(String componentName,
                                           List<StructureDefinitionParser.ElementDefinition> children,
                                           String resourceName) {
        String className = capitalize(componentName) + "Component";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ClassName.get("org.hl7.fhir.r5.model", "BackboneElement"))
                .addAnnotation(ClassName.get("ca.uhn.fhir.model.api.annotation", "Block"));

        // Track field names
        Set<String> generatedFields = new HashSet<>();

        // Generate fields for all child elements
        for (StructureDefinitionParser.ElementDefinition child : children) {
            String fieldName = child.getElementName();
            if (generatedFields.contains(fieldName)) {
                continue; // Skip duplicates from slices
            }
            generatedFields.add(fieldName);

            TypeName fieldType = getFieldType(child);

            // Add field
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName)
                    .addModifiers(Modifier.PRIVATE);

            // Add @Child annotation
            AnnotationSpec.Builder childAnnotation = AnnotationSpec.builder(
                    ClassName.get("ca.uhn.fhir.model.api.annotation", "Child"))
                    .addMember("name", "$S", fieldName)
                    .addMember("min", "$L", child.getMin());

            if (child.getMaxCardinality() == -1) {
                childAnnotation.addMember("max", "Child.MAX_UNLIMITED");
            } else {
                childAnnotation.addMember("max", "$L", child.getMaxCardinality());
            }
            fieldBuilder.addAnnotation(childAnnotation.build());

            // Add @Description annotation
            if (child.getShortDescription() != null) {
                AnnotationSpec descAnnotation = AnnotationSpec.builder(
                        ClassName.get("ca.uhn.fhir.model.api.annotation", "Description"))
                        .addMember("shortDefinition", "$S", child.getShortDescription())
                        .build();
                fieldBuilder.addAnnotation(descAnnotation);
            }

            classBuilder.addField(fieldBuilder.build());

            // Add getter
            MethodSpec getter = MethodSpec.methodBuilder("get" + capitalize(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(fieldType)
                    .addStatement("return this.$N", fieldName)
                    .build();
            classBuilder.addMethod(getter);

            // Add setter (fluent API)
            MethodSpec setter = MethodSpec.methodBuilder("set" + capitalize(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get("", className))
                    .addParameter(fieldType, fieldName)
                    .addStatement("this.$N = $N", fieldName, fieldName)
                    .addStatement("return this")
                    .build();
            classBuilder.addMethod(setter);
        }

        // Add copy() method (required by BackboneElement)
        MethodSpec.Builder copyMethodBuilder = MethodSpec.methodBuilder("copy")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("", className))
                .addStatement("$T dst = new $T()", ClassName.get("", className), ClassName.get("", className));

        // Copy all fields
        for (String fieldName : generatedFields) {
            copyMethodBuilder.addStatement("dst.$N = this.$N", fieldName, fieldName);
        }

        // Call parent copy method
        copyMethodBuilder.addStatement("copyValues(dst)");
        copyMethodBuilder.addStatement("return dst");

        classBuilder.addMethod(copyMethodBuilder.build());

        return classBuilder.build();
    }

    /**
     * Add a field for a backbone element (uses inner component class type).
     */
    private void addBackboneFieldForElement(TypeSpec.Builder classBuilder,
                                            StructureDefinitionParser.ElementDefinition element,
                                            String resourceName,
                                            String packageName) {
        String fieldName = element.getElementName();
        String componentClassName = capitalize(fieldName) + "Component";

        // Check if this is a list
        boolean isList = element.getMaxCardinality() != 1;

        // Build type name: either "PackagingComponent" or "List<PackagingComponent>"
        ClassName componentType = ClassName.get("", componentClassName);
        TypeName fieldType = isList
            ? ParameterizedTypeName.get(ClassName.get(List.class), componentType)
            : componentType;

        // Build the field
        FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName)
                .addModifiers(Modifier.PRIVATE);

        // Add @Child annotation
        AnnotationSpec.Builder childAnnotation = AnnotationSpec.builder(Child.class)
                .addMember("name", "$S", fieldName)
                .addMember("min", "$L", element.getMin());

        if (element.getMaxCardinality() == -1) {
            childAnnotation.addMember("max", "Child.MAX_UNLIMITED");
        } else {
            childAnnotation.addMember("max", "$L", element.getMaxCardinality());
        }
        fieldBuilder.addAnnotation(childAnnotation.build());

        // Add @Description annotation
        if (element.getShortDescription() != null) {
            AnnotationSpec descAnnotation = AnnotationSpec.builder(Description.class)
                    .addMember("shortDefinition", "$S", element.getShortDescription())
                    .build();
            fieldBuilder.addAnnotation(descAnnotation);
        }

        classBuilder.addField(fieldBuilder.build());

        // Add getter
        MethodSpec getter = MethodSpec.methodBuilder("get" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType)
                .addStatement("return this.$N", fieldName)
                .build();
        classBuilder.addMethod(getter);

        // Add setter
        MethodSpec setter = MethodSpec.methodBuilder("set" + capitalize(fieldName))
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(packageName, resourceName))
                .addParameter(fieldType, fieldName)
                .addStatement("this.$N = $N", fieldName, fieldName)
                .addStatement("return this")
                .build();
        classBuilder.addMethod(setter);
    }
}
