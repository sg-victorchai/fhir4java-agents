package org.fhirframework.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.fhirframework.api.config.FhirMediaType;
import org.fhirframework.api.interceptor.FhirVersionFilter;
import org.fhirframework.core.config.ResourceConfiguration;
import org.fhirframework.core.context.FhirContextFactory;
import org.fhirframework.core.interaction.InteractionType;
import org.fhirframework.core.operation.OperationConfigRegistry;
import org.fhirframework.core.operation.OperationHandler;
import org.fhirframework.core.operation.OperationRegistry;
import org.fhirframework.core.operation.OperationScope;
import org.fhirframework.core.resource.ResourceRegistry;
import org.fhirframework.core.searchparam.SearchParameterRegistry;
import org.fhirframework.core.version.FhirVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.CapabilityStatement.*;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * Controller for FHIR server metadata endpoints.
 * <p>
 * Provides the CapabilityStatement (metadata) endpoint that describes
 * the server's capabilities including search parameters, operations,
 * and resource profiles.
 * </p>
 */
@RestController
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    private final FhirContextFactory contextFactory;
    private final ResourceRegistry resourceRegistry;
    private final SearchParameterRegistry searchParameterRegistry;
    private final OperationRegistry operationRegistry;
    private final OperationConfigRegistry operationConfigRegistry;

    @Value("${fhir4java.server.base-url:http://localhost:8080/fhir}")
    private String baseUrl;

    @Value("${fhir4java.server.name:FHIR4Java Server}")
    private String serverName;

    @Value("${fhir4java.server.version:1.0.0}")
    private String serverVersion;

    public MetadataController(FhirContextFactory contextFactory,
                              ResourceRegistry resourceRegistry,
                              SearchParameterRegistry searchParameterRegistry,
                              OperationRegistry operationRegistry,
                              OperationConfigRegistry operationConfigRegistry) {
        this.contextFactory = contextFactory;
        this.resourceRegistry = resourceRegistry;
        this.searchParameterRegistry = searchParameterRegistry;
        this.operationRegistry = operationRegistry;
        this.operationConfigRegistry = operationConfigRegistry;
    }

    /**
     * Get server metadata (versioned path).
     */
    @GetMapping(
            path = "/fhir/{version}/metadata",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> metadataVersioned(
            @PathVariable String version,
            HttpServletRequest request) {
        return metadata(request);
    }

    /**
     * Get server metadata (unversioned path).
     */
    @GetMapping(
            path = "/fhir/metadata",
            produces = {FhirMediaType.APPLICATION_FHIR_JSON_VALUE, FhirMediaType.APPLICATION_FHIR_XML_VALUE}
    )
    public ResponseEntity<String> metadataUnversioned(HttpServletRequest request) {
        return metadata(request);
    }

    private ResponseEntity<String> metadata(HttpServletRequest request) {
        FhirVersion version = FhirVersionFilter.getVersion(request);
        log.debug("GET metadata (version={})", version);

        FhirContext context = contextFactory.getContext(version);
        IParser parser = context.newJsonParser().setPrettyPrint(true);

        CapabilityStatement capability = buildCapabilityStatement(version);

        return ResponseEntity
                .ok()
                .contentType(FhirMediaType.APPLICATION_FHIR_JSON)
                .body(parser.encodeResourceToString(capability));
    }

    private CapabilityStatement buildCapabilityStatement(FhirVersion version) {
        CapabilityStatement cs = new CapabilityStatement();

        // Basic metadata
        cs.setId("fhir4java-" + version.getCode());
        cs.setUrl(baseUrl + "/" + version.getCode() + "/metadata");
        cs.setVersion(serverVersion);
        cs.setName("FHIR4JavaCapabilityStatement" + version.name());
        cs.setTitle(serverName + " Capability Statement (" + version.name() + ")");
        cs.setStatus(PublicationStatus.ACTIVE);
        cs.setExperimental(false);
        cs.setDate(new Date());
        cs.setPublisher("FHIR4Java");
        cs.setDescription("FHIR4Java server capability statement for FHIR " + version.name());
        cs.setKind(Enumerations.CapabilityStatementKind.INSTANCE);
        cs.setFhirVersion(mapToFhirVersionEnum(version));
        cs.addFormat("json");
        cs.addFormat("xml");
        cs.addPatchFormat("application/json-patch+json");

        // Software information
        CapabilityStatementSoftwareComponent software = new CapabilityStatementSoftwareComponent();
        software.setName(serverName);
        software.setVersion(serverVersion);
        cs.setSoftware(software);

        // Implementation information
        CapabilityStatementImplementationComponent implementation = new CapabilityStatementImplementationComponent();
        implementation.setDescription("FHIR4Java - Enterprise-grade HL7 FHIR Server");
        implementation.setUrl(baseUrl);
        cs.setImplementation(implementation);

        // REST capabilities
        CapabilityStatementRestComponent rest = new CapabilityStatementRestComponent();
        rest.setMode(RestfulCapabilityMode.SERVER);
        rest.setDocumentation("RESTful FHIR " + version.name() + " Server");

        // Security
        CapabilityStatementRestSecurityComponent security = new CapabilityStatementRestSecurityComponent();
        security.setCors(true);
        security.setDescription("Security is configurable via plugins. SMART-on-FHIR OAuth2 is supported when enabled.");
        rest.setSecurity(security);

        // Add resource capabilities
        List<ResourceConfiguration> resources = resourceRegistry.getResourcesForVersion(version);
        for (ResourceConfiguration resourceConfig : resources) {
            CapabilityStatementRestResourceComponent resourceComponent = buildResourceComponent(resourceConfig, version);
            rest.addResource(resourceComponent);
        }

        // System-level interactions
        rest.addInteraction(new SystemInteractionComponent().setCode(SystemRestfulInteraction.TRANSACTION));
        rest.addInteraction(new SystemInteractionComponent().setCode(SystemRestfulInteraction.BATCH));
        rest.addInteraction(new SystemInteractionComponent().setCode(SystemRestfulInteraction.SEARCHSYSTEM));
        rest.addInteraction(new SystemInteractionComponent().setCode(SystemRestfulInteraction.HISTORYSYSTEM));

        // System-level operations
        addSystemOperations(rest, version);

        cs.addRest(rest);

        return cs;
    }

    private CapabilityStatementRestResourceComponent buildResourceComponent(ResourceConfiguration config, FhirVersion version) {
        CapabilityStatementRestResourceComponent resource = new CapabilityStatementRestResourceComponent();

        resource.setType(config.getResourceType());
        resource.setVersioning(ResourceVersionPolicy.VERSIONED);
        resource.setReadHistory(true);
        resource.setUpdateCreate(true);
        resource.setConditionalCreate(false);
        resource.setConditionalUpdate(false);
        resource.setConditionalDelete(ConditionalDeleteStatus.NOTSUPPORTED);

        // Add enabled interactions
        for (InteractionType interaction : config.getEnabledInteractions()) {
            TypeRestfulInteraction fhirInteraction = mapToFhirInteraction(interaction);
            if (fhirInteraction != null) {
                resource.addInteraction(new ResourceInteractionComponent().setCode(fhirInteraction));
            }
        }

        // Add all allowed search parameters from SearchParameterRegistry
        List<org.hl7.fhir.r5.model.SearchParameter> allowedParams =
                searchParameterRegistry.getAllowedSearchParameters(version, config.getResourceType(), resourceRegistry);
        for (org.hl7.fhir.r5.model.SearchParameter sp : allowedParams) {
            resource.addSearchParam(new CapabilityStatementRestResourceSearchParamComponent()
                    .setName(sp.getCode())
                    .setType(sp.getType())
                    .setDocumentation(sp.getDescription()));
        }

        // Add supported operations per resource
        addResourceOperations(resource, config.getResourceType(), version);

        // Add resource profiles
        for (String profileUrl : config.getRequiredProfiles()) {
        	//TODO - Manual fix
        	resource.getSupportedProfile().add(new CanonicalType(profileUrl));
        }

        return resource;
    }

    private void addResourceOperations(CapabilityStatementRestResourceComponent resource,
                                        String resourceType, FhirVersion version) {
        // Type-level operations
        List<OperationHandler> typeHandlers = operationRegistry.getHandlers(OperationScope.TYPE, resourceType);
        for (OperationHandler handler : typeHandlers) {
            if (handler.supportsVersion(version) &&
                    operationConfigRegistry.isOperationEnabled(handler.getOperationName(), version)) {
                resource.addOperation(new CapabilityStatementRestResourceOperationComponent()
                        .setName("$" + handler.getOperationName())
                        .setDefinition("OperationDefinition/" + handler.getOperationName()));
            }
        }

        // Instance-level operations
        List<OperationHandler> instanceHandlers = operationRegistry.getHandlers(OperationScope.INSTANCE, resourceType);
        for (OperationHandler handler : instanceHandlers) {
            if (handler.supportsVersion(version) &&
                    operationConfigRegistry.isOperationEnabled(handler.getOperationName(), version)) {
                // Avoid duplicates (handler may appear in both type and instance)
                boolean alreadyAdded = resource.getOperation().stream()
                        .anyMatch(op -> op.getName().equals("$" + handler.getOperationName()));
                if (!alreadyAdded) {
                    resource.addOperation(new CapabilityStatementRestResourceOperationComponent()
                            .setName("$" + handler.getOperationName())
                            .setDefinition("OperationDefinition/" + handler.getOperationName()));
                }
            }
        }
    }

    private void addSystemOperations(CapabilityStatementRestComponent rest, FhirVersion version) {
        List<OperationHandler> systemHandlers = operationRegistry.getHandlers(OperationScope.SYSTEM, null);
        for (OperationHandler handler : systemHandlers) {
            if (handler.supportsVersion(version) &&
                    operationConfigRegistry.isOperationEnabled(handler.getOperationName(), version)) {
                rest.addOperation(new CapabilityStatementRestResourceOperationComponent()
                        .setName("$" + handler.getOperationName())
                        .setDefinition("OperationDefinition/" + handler.getOperationName()));
            }
        }
    }

    private TypeRestfulInteraction mapToFhirInteraction(InteractionType type) {
        return switch (type) {
            case READ -> TypeRestfulInteraction.READ;
            case VREAD -> TypeRestfulInteraction.VREAD;
            case CREATE -> TypeRestfulInteraction.CREATE;
            case UPDATE -> TypeRestfulInteraction.UPDATE;
            case PATCH -> TypeRestfulInteraction.PATCH;
            case DELETE -> TypeRestfulInteraction.DELETE;
            case SEARCH -> TypeRestfulInteraction.SEARCHTYPE;
            case HISTORY -> TypeRestfulInteraction.HISTORYINSTANCE;
        };
    }

    private Enumerations.FHIRVersion mapToFhirVersionEnum(FhirVersion version) {
        return switch (version) {
            case R5 -> Enumerations.FHIRVersion._5_0_0;
            case R4B -> Enumerations.FHIRVersion._4_3_0;
        };
    }
}
