package org.fhirframework.core.resource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to investigate how HAPI discovers backbone elements for standard vs custom resources.
 */
class BackboneElementDiscoveryTest {

    @Test
    void testPatientContactBackboneDiscovery() {
        FhirContext ctx = FhirContext.forR5();

        // Examine Patient resource definition
        RuntimeResourceDefinition patientDef = ctx.getResourceDefinition(Patient.class);

        System.out.println("=== Patient Resource Definition ===");
        System.out.println("Name: " + patientDef.getName());
        System.out.println("Implementation class: " + patientDef.getImplementingClass());

        // Find the 'contact' child (backbone element)
        System.out.println("\n=== Child Definitions ===");
        BaseRuntimeChildDefinition contactChild = null;
        for (BaseRuntimeChildDefinition child : patientDef.getChildren()) {
            System.out.println("Child: " + child.getElementName() +
                " -> " + child.getClass().getSimpleName());

            if ("contact".equals(child.getElementName())) {
                contactChild = child;
                System.out.println("  Found 'contact' backbone element!");
                for (String validChildName : child.getValidChildNames()) {
                    System.out.println("    Valid child name: " + validChildName);
                    BaseRuntimeElementDefinition<?> childDef = child.getChildByName(validChildName);
                    if (childDef != null) {
                        System.out.println("    Child def type: " + childDef.getClass().getSimpleName());
                        System.out.println("    Implementation: " + childDef.getImplementingClass());
                    }
                }
            }
        }

        assertNotNull(contactChild, "Patient.contact backbone element should be discovered");
    }

    @Test
    void testMedicationInventoryBackboneDiscovery() throws Exception {
        FhirContext ctx = FhirContext.forR5();

        System.out.println("\n=== Testing MedicationInventory ===");

        Class<?> medInvClass = Class.forName("org.fhirframework.generated.resources.MedicationInventory");
        System.out.println("Class found: " + medInvClass.getName());

        // Check inner classes
        System.out.println("\n=== Inner Classes ===");
        for (Class<?> innerClass : medInvClass.getDeclaredClasses()) {
            System.out.println("Inner class: " + innerClass.getSimpleName());
            System.out.println("  Superclass: " + innerClass.getSuperclass().getSimpleName());
            System.out.println("  Interfaces: " + java.util.Arrays.toString(innerClass.getInterfaces()));
            System.out.println("  Annotations: " + java.util.Arrays.toString(innerClass.getAnnotations()));
        }

        // Register with HAPI
        @SuppressWarnings("unchecked")
        Class<? extends IBaseResource> typedClass = (Class<? extends IBaseResource>) medInvClass;
        RuntimeResourceDefinition medInvDef = ctx.getResourceDefinition(typedClass);

        System.out.println("\nMedicationInventory registered with HAPI!");
        System.out.println("Name: " + medInvDef.getName());

        // Find packaging child
        System.out.println("\n=== MedicationInventory Children ===");
        BaseRuntimeChildDefinition packagingChild = null;
        for (BaseRuntimeChildDefinition child : medInvDef.getChildren()) {
            System.out.println("Child: " + child.getElementName() +
                " -> " + child.getClass().getSimpleName());

            if ("packaging".equals(child.getElementName())) {
                packagingChild = child;
                System.out.println("  Found 'packaging' backbone element!");
                for (String validChildName : child.getValidChildNames()) {
                    System.out.println("    Valid child name: " + validChildName);
                    BaseRuntimeElementDefinition<?> childDef = child.getChildByName(validChildName);
                    if (childDef != null) {
                        System.out.println("    Child def type: " + childDef.getClass().getSimpleName());
                        System.out.println("    Implementation: " + childDef.getImplementingClass());
                    }
                }
            }
        }

        assertNotNull(packagingChild, "MedicationInventory.packaging backbone element should be discovered");
    }

    @Test
    void testMedicationInventoryParsing() throws Exception {
        FhirContext ctx = FhirContext.forR5();

        // Register the custom resource first
        Class<?> medInvClass = Class.forName("org.fhirframework.generated.resources.MedicationInventory");
        @SuppressWarnings("unchecked")
        Class<? extends IBaseResource> typedClass = (Class<? extends IBaseResource>) medInvClass;
        ctx.getResourceDefinition(typedClass);

        String json = """
            {
                "resourceType": "MedicationInventory",
                "status": "active",
                "medication": {"reference": "Medication/123"},
                "quantity": {"value": 100, "unit": "tablets"},
                "packaging": [
                    {
                        "type": {"coding": [{"code": "box"}]},
                        "unitsPerPackage": 10,
                        "packageCount": 10
                    }
                ]
            }
            """;

        System.out.println("\n=== Testing Parsing ===");
        System.out.println("Input JSON:\n" + json);

        // Use LENIENT error handler to see what gets parsed
        IParser parser = ctx.newJsonParser().setPrettyPrint(true);
        parser.setParserErrorHandler(new ca.uhn.fhir.parser.LenientErrorHandler());

        IBaseResource parsed = parser.parseResource(json);
        System.out.println("Parsed class: " + parsed.getClass().getName());

        // Check the packaging field directly via reflection
        try {
            java.lang.reflect.Method getPackaging = parsed.getClass().getMethod("getPackaging");
            Object packaging = getPackaging.invoke(parsed);
            System.out.println("getPackaging() returned: " + packaging);
            if (packaging instanceof java.util.List<?> list) {
                System.out.println("  List size: " + list.size());
                for (Object item : list) {
                    System.out.println("  Item: " + item + " (class: " + item.getClass().getName() + ")");
                    // Inspect the PackagingComponent fields
                    try {
                        java.lang.reflect.Method getType = item.getClass().getMethod("getType");
                        Object type = getType.invoke(item);
                        System.out.println("    getType() = " + type);

                        java.lang.reflect.Method getUnitsPerPackage = item.getClass().getMethod("getUnitsPerPackage");
                        Object unitsPerPackage = getUnitsPerPackage.invoke(item);
                        System.out.println("    getUnitsPerPackage() = " + unitsPerPackage);

                        java.lang.reflect.Method getPackageCount = item.getClass().getMethod("getPackageCount");
                        Object packageCount = getPackageCount.invoke(item);
                        System.out.println("    getPackageCount() = " + packageCount);

                        // Check if any children have values
                        if (item instanceof org.hl7.fhir.r5.model.BackboneElement be) {
                            System.out.println("    BackboneElement.isEmpty() = " + be.isEmpty());
                        }
                    } catch (Exception e) {
                        System.out.println("    Error inspecting item: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error accessing packaging: " + e.getMessage());
        }

        // Re-serialize
        String output = parser.encodeResourceToString(parsed);
        System.out.println("\nRe-serialized JSON:\n" + output);

        // Check if packaging was preserved (just report, don't fail)
        System.out.println("\n=== Analysis ===");
        System.out.println("Contains 'packaging': " + output.contains("packaging"));
        System.out.println("Contains 'unitsPerPackage': " + output.contains("unitsPerPackage"));

        // For now, skip assertion to see output
        // assertTrue(output.contains("packaging"), "packaging should be preserved in output");
    }

    @Test
    void testPatientContactParsing() throws Exception {
        FhirContext ctx = FhirContext.forR5();

        String json = """
            {
                "resourceType": "Patient",
                "active": true,
                "name": [{"family": "Smith", "given": ["John"]}],
                "contact": [
                    {
                        "relationship": [{"coding": [{"code": "spouse"}]}],
                        "name": {"family": "Smith", "given": ["Jane"]}
                    }
                ]
            }
            """;

        System.out.println("\n=== Testing Patient Parsing ===");
        System.out.println("Input JSON:\n" + json);

        IParser parser = ctx.newJsonParser().setPrettyPrint(true);
        IBaseResource parsed = parser.parseResource(json);

        String output = parser.encodeResourceToString(parsed);
        System.out.println("\nRe-serialized JSON:\n" + output);

        System.out.println("\n=== Analysis ===");
        System.out.println("Contains 'contact': " + output.contains("contact"));
        System.out.println("Contains 'relationship': " + output.contains("relationship"));

        assertTrue(output.contains("contact"), "Patient.contact should be preserved");
    }

    @Test
    void testBackboneElementChildrenComparison() throws Exception {
        FhirContext ctx = FhirContext.forR5();

        // Get Patient.ContactComponent definition
        RuntimeResourceDefinition patientDef = ctx.getResourceDefinition(Patient.class);
        BaseRuntimeChildDefinition patientContactChild = null;
        for (BaseRuntimeChildDefinition child : patientDef.getChildren()) {
            if ("contact".equals(child.getElementName())) {
                patientContactChild = child;
                break;
            }
        }

        System.out.println("=== Patient.ContactComponent Children ===");
        if (patientContactChild != null) {
            BaseRuntimeElementDefinition<?> contactDef = patientContactChild.getChildByName("contact");
            if (contactDef instanceof ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition<?> compositeDef) {
                for (BaseRuntimeChildDefinition contactChildDef : compositeDef.getChildren()) {
                    System.out.println("  Child: " + contactChildDef.getElementName() +
                        " -> " + contactChildDef.getClass().getSimpleName());
                }
            }
        }

        // Get MedicationInventory.PackagingComponent definition
        Class<?> medInvClass = Class.forName("org.fhirframework.generated.resources.MedicationInventory");
        @SuppressWarnings("unchecked")
        Class<? extends IBaseResource> typedClass = (Class<? extends IBaseResource>) medInvClass;
        RuntimeResourceDefinition medInvDef = ctx.getResourceDefinition(typedClass);

        BaseRuntimeChildDefinition packagingChild = null;
        for (BaseRuntimeChildDefinition child : medInvDef.getChildren()) {
            if ("packaging".equals(child.getElementName())) {
                packagingChild = child;
                break;
            }
        }

        System.out.println("\n=== MedicationInventory.PackagingComponent Children ===");
        if (packagingChild != null) {
            BaseRuntimeElementDefinition<?> packagingDef = packagingChild.getChildByName("packaging");
            System.out.println("PackagingComponent definition class: " + packagingDef.getClass().getSimpleName());
            if (packagingDef instanceof ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition<?> compositeDef) {
                System.out.println("Children count: " + compositeDef.getChildren().size());
                for (BaseRuntimeChildDefinition packagingChildDef : compositeDef.getChildren()) {
                    System.out.println("  Child: " + packagingChildDef.getElementName() +
                        " -> " + packagingChildDef.getClass().getSimpleName());
                }
            } else {
                System.out.println("NOT a composite definition! This is the problem.");
            }
        }

        // Manually check what HAPI knows about PackagingComponent
        System.out.println("\n=== Direct PackagingComponent Element Definition ===");
        Class<?> packagingComponentClass = Class.forName(
            "org.fhirframework.generated.resources.MedicationInventory$PackagingComponent");
        @SuppressWarnings("unchecked")
        Class<? extends org.hl7.fhir.instance.model.api.IBase> typedBlockClass =
            (Class<? extends org.hl7.fhir.instance.model.api.IBase>) packagingComponentClass;
        BaseRuntimeElementDefinition<?> directDef = ctx.getElementDefinition(typedBlockClass);
        System.out.println("Element definition class: " + directDef.getClass().getSimpleName());
        System.out.println("Name: " + directDef.getName());
        if (directDef instanceof ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition<?> compositeDef) {
            System.out.println("Children count: " + compositeDef.getChildren().size());
            for (BaseRuntimeChildDefinition childDef : compositeDef.getChildren()) {
                System.out.println("  Child: " + childDef.getElementName() +
                    " -> " + childDef.getClass().getSimpleName());
            }
        }
    }
}
