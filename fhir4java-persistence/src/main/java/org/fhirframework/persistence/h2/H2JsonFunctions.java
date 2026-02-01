package org.fhirframework.persistence.h2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Java implementations of PostgreSQL JSON functions for use as H2 function aliases.
 * <p>
 * These static methods are registered in H2 via CREATE ALIAS statements so that
 * the same JPA criteria queries work against both PostgreSQL and H2.
 * </p>
 */
public final class H2JsonFunctions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private H2JsonFunctions() {
    }

    /**
     * Emulates PostgreSQL's {@code jsonb_extract_path_text(json, path1, path2, ...)}.
     * <p>
     * Navigates the JSON tree using the provided path segments and returns the
     * text value at the final node. Array indices are supported as numeric strings.
     * </p>
     * <p>
     * Uses {@code String...} (not {@code Object...}) because H2 can convert its
     * JSON column type to VARCHAR (String) but cannot convert JSON to JAVA_OBJECT
     * (Object). The first element of {@code args} is the JSON string from the
     * content column; subsequent elements are path segments.
     * </p>
     *
     * @param args the JSON string followed by path segments
     * @return the text value at the path, or null if not found
     */
    public static String jsonb_extract_path_text(String... args) {
        if (args == null || args.length < 1 || args[0] == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(args[0]);
            for (int i = 1; i < args.length; i++) {
                if (node == null || node.isMissingNode()) {
                    return null;
                }
                String path = args[i];
                if (path == null) {
                    return null;
                }
                // Try as array index first if the path is numeric
                if (node.isArray() && path.matches("\\d+")) {
                    node = node.get(Integer.parseInt(path));
                } else {
                    node = node.get(path);
                }
            }
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            return node.isTextual() ? node.textValue() : node.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Emulates PostgreSQL's {@code to_number(text, format)} for H2.
     * <p>
     * Parses the text as a double. The format parameter is ignored since
     * Java's Double.parseDouble handles all standard number formats.
     * </p>
     *
     * @param text   the text to convert to a number
     * @param format the format mask (ignored, for PostgreSQL compatibility)
     * @return the parsed double value, or 0.0 if parsing fails
     */
    public static Double to_number(String text, String format) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
