package org.fhirframework.api.config;

import org.springframework.http.MediaType;

/**
 * FHIR-specific media types for content negotiation.
 */
public final class FhirMediaType {

    private FhirMediaType() {
    }

    // FHIR JSON media types
    public static final String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";
    public static final MediaType APPLICATION_FHIR_JSON = MediaType.valueOf(APPLICATION_FHIR_JSON_VALUE);

    // FHIR XML media types
    public static final String APPLICATION_FHIR_XML_VALUE = "application/fhir+xml";
    public static final MediaType APPLICATION_FHIR_XML = MediaType.valueOf(APPLICATION_FHIR_XML_VALUE);

    // JSON patch media type
    public static final String APPLICATION_JSON_PATCH_VALUE = "application/json-patch+json";
    public static final MediaType APPLICATION_JSON_PATCH = MediaType.valueOf(APPLICATION_JSON_PATCH_VALUE);

    // Standard JSON/XML that FHIR also accepts
    public static final String APPLICATION_JSON_VALUE = "application/json";
    public static final String APPLICATION_XML_VALUE = "application/xml";
    public static final String TEXT_XML_VALUE = "text/xml";

    /**
     * Checks if a media type is a FHIR JSON type.
     */
    public static boolean isFhirJson(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return APPLICATION_FHIR_JSON.isCompatibleWith(mediaType)
                || MediaType.APPLICATION_JSON.isCompatibleWith(mediaType);
    }

    /**
     * Checks if a media type is a FHIR XML type.
     */
    public static boolean isFhirXml(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return APPLICATION_FHIR_XML.isCompatibleWith(mediaType)
                || MediaType.APPLICATION_XML.isCompatibleWith(mediaType)
                || MediaType.TEXT_XML.isCompatibleWith(mediaType);
    }

    /**
     * Checks if a media type is JSON patch.
     */
    public static boolean isJsonPatch(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return APPLICATION_JSON_PATCH.isCompatibleWith(mediaType);
    }

    /**
     * Determines if the media type indicates JSON format should be used.
     */
    public static boolean shouldUseJson(MediaType mediaType) {
        if (mediaType == null) {
            return true; // Default to JSON
        }
        return isFhirJson(mediaType) || mediaType.equals(MediaType.ALL);
    }

    /**
     * Determines if the media type indicates XML format should be used.
     */
    public static boolean shouldUseXml(MediaType mediaType) {
        return isFhirXml(mediaType);
    }
}
