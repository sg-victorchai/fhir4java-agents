package org.fhirframework.core.context;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.fhirframework.core.validation.ValidationConfig;
import org.fhirframework.core.version.FhirVersion;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Default implementation of FhirContextFactory.
 * <p>
 * Creates and caches FHIR contexts for each supported version.
 * Contexts are created lazily on first access for performance.
 * </p>
 */
@Component
public class FhirContextFactoryImpl implements FhirContextFactory {

    private static final Logger log = LoggerFactory.getLogger(FhirContextFactoryImpl.class);

    private final Map<FhirVersion, FhirContext> contexts = new EnumMap<>(FhirVersion.class);
    private final ValidationConfig validationConfig;

    public FhirContextFactoryImpl(ValidationConfig validationConfig) {
        this.validationConfig = validationConfig;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing FHIR context factory for versions: R5, R4B");
        // Pre-initialize R5 context as it's the default and most commonly used
        getContext(FhirVersion.R5);
    }

    @Override
    public FhirContext getContext(FhirVersion version) {
        return contexts.computeIfAbsent(version, this::createContext);
    }

    private FhirContext createContext(FhirVersion version) {
        log.info("Creating FHIR context for version: {}", version.getCode());
        long startTime = System.currentTimeMillis();

        FhirContext context = switch (version) {
            case R5 -> FhirContext.forR5();
            case R4B -> FhirContext.forR4B();
        };

        // Configure parser error handler
        if (validationConfig.getParserErrorHandler() == ValidationConfig.ParserErrorMode.STRICT) {
            context.setParserErrorHandler(new StrictErrorHandler());
            log.info("FhirContext configured with STRICT error handling for version: {}", version.getCode());
        } else {
            context.setParserErrorHandler(new LenientErrorHandler());
            log.info("FhirContext configured with LENIENT error handling for version: {}", version.getCode());
        }

        // Configure context settings
        context.setPerformanceOptions(ca.uhn.fhir.context.PerformanceOptionsEnum.DEFERRED_MODEL_SCANNING);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created FHIR context for version {} in {} ms", version.getCode(), duration);

        return context;
    }

    /**
     * Converts FhirVersion enum to HAPI FhirVersionEnum.
     */
    public static FhirVersionEnum toHapiVersion(FhirVersion version) {
        return switch (version) {
            case R5 -> FhirVersionEnum.R5;
            case R4B -> FhirVersionEnum.R4B;
        };
    }

    /**
     * Converts HAPI FhirVersionEnum to FhirVersion enum.
     */
    public static FhirVersion fromHapiVersion(FhirVersionEnum hapiVersion) {
        return switch (hapiVersion) {
            case R5 -> FhirVersion.R5;
            case R4B -> FhirVersion.R4B;
            default -> throw new IllegalArgumentException("Unsupported HAPI FHIR version: " + hapiVersion);
        };
    }
}
