package org.fhirframework.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses FHIR StructureDefinition JSON files and extracts information needed for code generation.
 */
public class StructureDefinitionParser {

    private static final Logger log = LoggerFactory.getLogger(StructureDefinitionParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse a StructureDefinition from a JSON file.
     *
     * @param file The StructureDefinition JSON file
     * @return Parsed structure definition metadata
     * @throws IOException If file cannot be read or parsed
     */
    public StructureDefinitionMetadata parse(File file) throws IOException {
        log.info("Parsing StructureDefinition from: {}", file.getName());
        
        JsonNode root = objectMapper.readTree(file);
        
        // Extract basic metadata
        String id = getTextValue(root, "id");
        String url = getTextValue(root, "url");
        String name = getTextValue(root, "name");
        String type = getTextValue(root, "type");
        String baseDefinition = getTextValue(root, "baseDefinition");
        String description = getTextValue(root, "description");
        String fhirVersion = getTextValue(root, "fhirVersion");
        String derivation = getTextValue(root, "derivation");
        String kind = getTextValue(root, "kind");
        boolean isAbstract = getBooleanValue(root, "abstract", false);
        
        // Parse elements from differential
        List<ElementDefinition> elements = new ArrayList<>();
        JsonNode differential = root.path("differential");
        if (!differential.isMissingNode()) {
            JsonNode elementArray = differential.path("element");
            if (elementArray.isArray()) {
                for (JsonNode elementNode : elementArray) {
                    ElementDefinition element = parseElement(elementNode);
                    elements.add(element);
                }
            }
        }
        
        StructureDefinitionMetadata metadata = new StructureDefinitionMetadata();
        metadata.setId(id);
        metadata.setUrl(url);
        metadata.setName(name);
        metadata.setType(type);
        metadata.setBaseDefinition(baseDefinition);
        metadata.setDescription(description);
        metadata.setFhirVersion(fhirVersion);
        metadata.setDerivation(derivation);
        metadata.setKind(kind);
        metadata.setAbstractResource(isAbstract);
        metadata.setElements(elements);
        metadata.setFileName(file.getName());
        
        log.info("Parsed StructureDefinition '{}' with {} elements", name, elements.size());
        return metadata;
    }

    /**
     * Parse an individual element definition.
     */
    private ElementDefinition parseElement(JsonNode elementNode) {
        String id = getTextValue(elementNode, "id");
        String path = getTextValue(elementNode, "path");
        String shortDesc = getTextValue(elementNode, "short");
        String definition = getTextValue(elementNode, "definition");
        int min = getIntValue(elementNode, "min", 0);
        String max = getTextValue(elementNode, "max");
        
        // Parse types
        List<TypeInfo> types = new ArrayList<>();
        JsonNode typeArray = elementNode.path("type");
        if (typeArray.isArray()) {
            for (JsonNode typeNode : typeArray) {
                String code = getTextValue(typeNode, "code");
                List<String> targetProfiles = new ArrayList<>();
                JsonNode profileArray = typeNode.path("targetProfile");
                if (profileArray.isArray()) {
                    for (JsonNode profileNode : profileArray) {
                        targetProfiles.add(profileNode.asText());
                    }
                }
                types.add(new TypeInfo(code, targetProfiles));
            }
        }
        
        // Parse binding
        BindingInfo binding = null;
        JsonNode bindingNode = elementNode.path("binding");
        if (!bindingNode.isMissingNode()) {
            String strength = getTextValue(bindingNode, "strength");
            String valueSet = getTextValue(bindingNode, "valueSet");
            String bindingDesc = getTextValue(bindingNode, "description");
            binding = new BindingInfo(strength, valueSet, bindingDesc);
        }
        
        boolean isModifier = getBooleanValue(elementNode, "isModifier", false);
        boolean isSummary = getBooleanValue(elementNode, "isSummary", false);
        
        ElementDefinition element = new ElementDefinition();
        element.setId(id);
        element.setPath(path);
        element.setShortDescription(shortDesc);
        element.setDefinition(definition);
        element.setMin(min);
        element.setMax(max);
        element.setTypes(types);
        element.setBinding(binding);
        element.setModifier(isModifier);
        element.setSummary(isSummary);
        
        return element;
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? null : fieldNode.asText();
    }

    private int getIntValue(JsonNode node, String field, int defaultValue) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? defaultValue : fieldNode.asInt();
    }

    private boolean getBooleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? defaultValue : fieldNode.asBoolean();
    }

    /**
     * Metadata extracted from a StructureDefinition.
     */
    @Data
    public static class StructureDefinitionMetadata {
        private String id;
        private String url;
        private String name;
        private String type;
        private String baseDefinition;
        private String description;
        private String fhirVersion;
        private String derivation;
        private String kind;
        private boolean abstractResource;
        private List<ElementDefinition> elements;
        private String fileName;

        /**
         * Check if this is a custom resource (specialization of DomainResource/Resource).
         * Custom resources have derivation="specialization", are resource kinds, and are not abstract.
         */
        public boolean isCustomResource() {
            return "specialization".equals(derivation) && "resource".equals(kind) && !abstractResource;
        }
    }

    /**
     * Metadata for a single element in a StructureDefinition.
     */
    @Data
    public static class ElementDefinition {
        private String id;
        private String path;
        private String shortDescription;
        private String definition;
        private int min;
        private String max;
        private List<TypeInfo> types;
        private BindingInfo binding;
        private boolean modifier;
        private boolean summary;
        
        /**
         * Get the element name (last part of path).
         */
        public String getElementName() {
            if (path == null) return null;
            int lastDot = path.lastIndexOf('.');
            return lastDot >= 0 ? path.substring(lastDot + 1) : path;
        }
        
        /**
         * Check if this is the root element (path equals type).
         */
        public boolean isRootElement() {
            return path != null && !path.contains(".");
        }
        
        /**
         * Check if this is a backbone element (nested complex type).
         */
        public boolean isBackboneElement() {
            return path != null && path.split("\\.").length == 2;
        }
        
        /**
         * Get the maximum cardinality as an integer (-1 for unlimited).
         */
        public int getMaxCardinality() {
            if (max == null) return 1;
            if ("*".equals(max)) return -1;
            try {
                return Integer.parseInt(max);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
    }

    /**
     * Type information for an element.
     */
    @Data
    public static class TypeInfo {
        private String code;
        private List<String> targetProfiles;
        
        public TypeInfo(String code, List<String> targetProfiles) {
            this.code = code;
            this.targetProfiles = targetProfiles;
        }
    }

    /**
     * Terminology binding information.
     */
    @Data
    public static class BindingInfo {
        private String strength;
        private String valueSet;
        private String description;
        
        public BindingInfo(String strength, String valueSet, String description) {
            this.strength = strength;
            this.valueSet = valueSet;
            this.description = description;
        }
    }
}
