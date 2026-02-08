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

        // Group elements by parent path for backbone elements
        Map<String, List<StructureDefinitionParser.ElementDefinition>> elementsByParent = new HashMap<>();
        List<StructureDefinitionParser.ElementDefinition> rootElements = new ArrayList<>();

        for (StructureDefinitionParser.ElementDefinition element : metadata.getElements()) {
            if (element.isRootElement()) {
                continue; // Skip the root element itself
            }

            String[] pathParts = element.getPath().split("\\.");
            if (pathParts.length == 2) {
                // Direct child of resource
                rootElements.add(element);
            } else if (pathParts.length > 2) {
                // Nested element (backbone element child)
                String parentPath = String.join(".", Arrays.copyOf(pathParts, pathParts.length - 1));
                elementsByParent.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(element);
            }
        }

        // Generate fields and methods for root elements
        for (StructureDefinitionParser.ElementDefinition element : rootElements) {
            addFieldForElement(classBuilder, element, metadata.getName());
        }

        // Add fhirType() method
        MethodSpec fhirTypeMethod = MethodSpec.methodBuilder("fhirType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", metadata.getName())
                .build();
        classBuilder.addMethod(fhirTypeMethod);

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
                                    String resourceName) {
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
                .returns(ClassName.get("", resourceName))
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
}
