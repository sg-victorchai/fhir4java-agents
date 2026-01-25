package org.fhirframework.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC configuration for FHIR API.
 * <p>
 * Configures content negotiation, CORS, message converters, and other web settings.
 * </p>
 */
@Configuration
public class FhirWebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                // Don't use path extension for content type
                .favorParameter(true)
                .parameterName("_format")
                // Default to FHIR JSON
                .defaultContentType(FhirMediaType.APPLICATION_FHIR_JSON, MediaType.APPLICATION_JSON)
                // Support _format parameter values
                .mediaType("json", FhirMediaType.APPLICATION_FHIR_JSON)
                .mediaType("xml", FhirMediaType.APPLICATION_FHIR_XML)
                .mediaType("application/fhir+json", FhirMediaType.APPLICATION_FHIR_JSON)
                .mediaType("application/fhir+xml", FhirMediaType.APPLICATION_FHIR_XML)
                .mediaType("application/json", MediaType.APPLICATION_JSON)
                .mediaType("application/xml", MediaType.APPLICATION_XML);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Add FHIR media types to StringHttpMessageConverter
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                List<MediaType> mediaTypes = new java.util.ArrayList<>(stringConverter.getSupportedMediaTypes());
                mediaTypes.add(FhirMediaType.APPLICATION_FHIR_JSON);
                mediaTypes.add(FhirMediaType.APPLICATION_FHIR_XML);
                stringConverter.setSupportedMediaTypes(mediaTypes);
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/fhir/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Location",
                        "Content-Location",
                        "ETag",
                        "Last-Modified",
                        "X-FHIR-Version",
                        "X-Request-Id"
                )
                .maxAge(3600);
    }
}
