# MVP Implementation Plan: AI-Ready Platform (Phases 1-3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable AI agents to authenticate, discover FHIR capabilities, query/mutate data via MCP protocol, and receive real-time events.

**Architecture:** New `fhir4java-mcp` module exposes 3 unified MCP tools (fhir_discover, fhir_query, fhir_mutate) backed by existing FHIR4Java services. OAuth2/API key auth secures all endpoints. SSE provides real-time event streaming.

**Tech Stack:** Spring Boot 3.4, Spring Security OAuth2, MCP SDK (or custom implementation), Spring WebFlux (SSE)

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md`

---

## File Structure

### Phase 1: Foundation
```
fhir4java-core/src/main/java/org/fhirframework/core/
├── discovery/
│   ├── DiscoveryService.java           # Main discovery facade
│   ├── DiscoveryResponse.java          # Response DTOs
│   └── DiscoveryTopic.java             # Enum: resources, searchParams, operations, all

fhir4java-server/src/main/java/org/fhirframework/server/
├── security/
│   ├── OAuth2ResourceServerConfig.java # Spring Security OAuth2 config
│   ├── ApiKeyAuthFilter.java           # API key authentication filter
│   ├── ApiKeyAuthProvider.java         # Validates API keys
│   └── AgentPrincipal.java             # Authenticated agent identity

fhir4java-persistence/src/main/java/org/fhirframework/persistence/
├── entity/
│   └── AgentApiKeyEntity.java          # API key storage
├── repository/
│   └── AgentApiKeyRepository.java      # API key CRUD
```

### Phase 2: MCP Integration
```
fhir4java-mcp/                           # NEW MODULE
├── pom.xml
├── src/main/java/org/fhirframework/mcp/
│   ├── McpAutoConfiguration.java        # Spring Boot auto-config
│   ├── McpProperties.java               # Configuration properties
│   ├── transport/
│   │   └── StreamableHttpTransport.java # MCP HTTP transport handler
│   ├── tool/
│   │   ├── McpTool.java                 # Tool interface
│   │   ├── FhirDiscoverTool.java        # fhir_discover implementation
│   │   ├── FhirQueryTool.java           # fhir_query implementation
│   │   ├── FhirMutateTool.java          # fhir_mutate implementation
│   │   └── ToolRegistry.java            # Tool registration
│   ├── dto/
│   │   ├── McpRequest.java              # MCP protocol request
│   │   ├── McpResponse.java             # MCP protocol response
│   │   ├── ToolCallRequest.java         # Tool invocation request
│   │   └── ToolCallResponse.java        # Tool invocation response
│   └── hint/
│       └── ResponseHintGenerator.java   # Smart response hints
```

### Phase 3: Real-Time Events
```
fhir4java-api/src/main/java/org/fhirframework/api/
├── event/
│   ├── EventStreamController.java       # SSE endpoint
│   ├── ResourceChangeEvent.java         # Event payload
│   └── EventPublisher.java              # Publishes events from plugins

fhir4java-core/src/main/java/org/fhirframework/core/
├── subscription/
│   ├── SubscriptionManager.java         # Manages subscriptions
│   ├── SubscriptionTopic.java           # Topic definition
│   └── WebhookRegistry.java             # Webhook URL registry

fhir4java-plugin/src/main/java/org/fhirframework/plugin/
├── event/
│   └── ResourceChangePlugin.java        # AFTER plugin emits events
```

---

## Phase 1: Foundation (Weeks 1-3)

### Task 1: DiscoveryService

**Files:**
- Create: `fhir4java-core/src/main/java/org/fhirframework/core/discovery/DiscoveryService.java`
- Create: `fhir4java-core/src/main/java/org/fhirframework/core/discovery/DiscoveryResponse.java`
- Test: `fhir4java-core/src/test/java/org/fhirframework/core/discovery/DiscoveryServiceTest.java`

- [ ] **Step 1: Write failing test for resource discovery**

```java
@Test
void discoverResources_returnsEnabledResources() {
    DiscoveryResponse response = discoveryService.discover(DiscoveryTopic.RESOURCES, null, FhirVersion.R5);
    assertThat(response.getResources()).isNotEmpty();
    assertThat(response.getResources()).anyMatch(r -> r.getResourceType().equals("Patient"));
}
```

- [ ] **Step 2: Implement DiscoveryService**

```java
@Service
public class DiscoveryService {
    private final ResourceRegistry resourceRegistry;
    private final SearchParameterRegistry searchParamRegistry;
    private final OperationRegistry operationRegistry;

    public DiscoveryResponse discover(DiscoveryTopic topic, String resourceType, FhirVersion version) {
        return switch (topic) {
            case RESOURCES -> discoverResources(version);
            case SEARCH_PARAMS -> discoverSearchParams(resourceType, version);
            case OPERATIONS -> discoverOperations(resourceType, version);
            case ALL -> discoverAll(resourceType, version);
        };
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(core): add DiscoveryService for MCP tool backing"`

---

### Task 2: OAuth2 Resource Server

**Files:**
- Create: `fhir4java-server/src/main/java/org/fhirframework/server/security/OAuth2ResourceServerConfig.java`
- Modify: `fhir4java-server/src/main/resources/application.yml`
- Test: `fhir4java-server/src/test/java/org/fhirframework/server/security/OAuth2SecurityTest.java`

- [ ] **Step 1: Write failing security test**

```java
@Test
void unauthenticatedRequest_returns401() {
    webTestClient.get().uri("/fhir/r5/Patient/123")
        .exchange()
        .expectStatus().isUnauthorized();
}

@Test
void validJwtToken_allowsAccess() {
    webTestClient.get().uri("/fhir/r5/Patient/123")
        .header("Authorization", "Bearer " + validJwt)
        .exchange()
        .expectStatus().isOk();
}
```

- [ ] **Step 2: Add Spring Security OAuth2 dependency to pom.xml**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

- [ ] **Step 3: Implement OAuth2ResourceServerConfig**

```java
@Configuration
@EnableWebSecurity
public class OAuth2ResourceServerConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/fhir/**/metadata").permitAll()
                .requestMatchers("/api/mcp/**").authenticated()
                .requestMatchers("/fhir/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

- [ ] **Step 4: Configure JWT issuer in application.yml**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI:http://localhost:8080/realms/fhir4java}
```

- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(security): add OAuth2 resource server configuration"`

---

### Task 3: API Key Authentication

**Files:**
- Create: `fhir4java-persistence/src/main/java/org/fhirframework/persistence/entity/AgentApiKeyEntity.java`
- Create: `fhir4java-server/src/main/java/org/fhirframework/server/security/ApiKeyAuthFilter.java`
- Test: `fhir4java-server/src/test/java/org/fhirframework/server/security/ApiKeyAuthTest.java`

- [ ] **Step 1: Write failing test for API key auth**

```java
@Test
void validApiKey_allowsAccess() {
    webTestClient.get().uri("/api/mcp/tools")
        .header("X-API-Key", "valid-test-key")
        .exchange()
        .expectStatus().isOk();
}
```

- [ ] **Step 2: Create AgentApiKeyEntity**

```java
@Entity
@Table(name = "agent_api_key")
public class AgentApiKeyEntity {
    @Id @GeneratedValue
    private Long id;
    private String keyHash;        // SHA-256 hash of API key
    private String agentName;
    private String tenantId;
    private String scopes;         // Comma-separated SMART scopes
    private boolean enabled;
    private LocalDateTime expiresAt;
}
```

- [ ] **Step 3: Implement ApiKeyAuthFilter**

```java
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            AgentApiKeyEntity key = repository.findByKeyHash(hash(apiKey));
            if (key != null && key.isEnabled() && !key.isExpired()) {
                Authentication auth = new ApiKeyAuthentication(key);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Add filter to security chain** (before OAuth2)
- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(security): add API key authentication for agents"`

---

## Phase 2: MCP Integration (Weeks 4-7)

### Task 4: Create fhir4java-mcp Module

**Files:**
- Create: `fhir4java-mcp/pom.xml`
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/McpAutoConfiguration.java`

- [ ] **Step 1: Create module pom.xml**

```xml
<project>
    <artifactId>fhir4java-mcp</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.fhirframework</groupId>
            <artifactId>fhir4java-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.fhirframework</groupId>
            <artifactId>fhir4java-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent pom.xml**
- [ ] **Step 3: Create McpAutoConfiguration**
- [ ] **Step 4: Verify module builds** `mvn clean compile -pl fhir4java-mcp`
- [ ] **Step 5: Commit** `git commit -m "feat(mcp): create fhir4java-mcp module"`

---

### Task 5: MCP Transport Layer

**Files:**
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/transport/McpEndpoint.java`
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/dto/McpRequest.java`
- Test: `fhir4java-mcp/src/test/java/org/fhirframework/mcp/transport/McpEndpointTest.java`

- [ ] **Step 1: Write failing test for MCP endpoint**

```java
@Test
void mcpEndpoint_handleToolList() {
    webTestClient.post().uri("/api/mcp")
        .bodyValue(new McpRequest("tools/list", null))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.tools").isArray()
        .jsonPath("$.tools.length()").isEqualTo(3);
}
```

- [ ] **Step 2: Implement McpEndpoint controller**

```java
@RestController
@RequestMapping("/api/mcp")
public class McpEndpoint {
    @PostMapping
    public McpResponse handle(@RequestBody McpRequest request) {
        return switch (request.getMethod()) {
            case "tools/list" -> listTools();
            case "tools/call" -> callTool(request.getParams());
            default -> McpResponse.error("Unknown method");
        };
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(mcp): add MCP HTTP endpoint"`

---

### Task 6: fhir_discover Tool

**Files:**
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/tool/FhirDiscoverTool.java`
- Test: `fhir4java-mcp/src/test/java/org/fhirframework/mcp/tool/FhirDiscoverToolTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
void fhirDiscover_returnsResourceList() {
    ToolCallRequest request = new ToolCallRequest("fhir_discover",
        Map.of("topic", "resources"));
    ToolCallResponse response = tool.execute(request);
    assertThat(response.getContent()).contains("Patient");
}
```

- [ ] **Step 2: Implement FhirDiscoverTool**

```java
@Component
public class FhirDiscoverTool implements McpTool {
    private final DiscoveryService discoveryService;

    @Override
    public String getName() { return "fhir_discover"; }

    @Override
    public ToolCallResponse execute(ToolCallRequest request) {
        DiscoveryTopic topic = DiscoveryTopic.valueOf(request.getParam("topic", "ALL"));
        String resourceType = request.getParam("resourceType", null);
        FhirVersion version = FhirVersion.valueOf(request.getParam("fhirVersion", "R5"));

        DiscoveryResponse result = discoveryService.discover(topic, resourceType, version);
        return ToolCallResponse.success(toJson(result));
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(mcp): implement fhir_discover tool"`

---

### Task 7: fhir_query Tool

**Files:**
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/tool/FhirQueryTool.java`
- Test: `fhir4java-mcp/src/test/java/org/fhirframework/mcp/tool/FhirQueryToolTest.java`

- [ ] **Step 1: Write failing tests for read, search, operation**

```java
@Test
void fhirQuery_read_returnsResource() {
    ToolCallRequest request = new ToolCallRequest("fhir_query",
        Map.of("action", "read", "resourceType", "Patient", "id", "123"));
    ToolCallResponse response = tool.execute(request);
    assertThat(response.isSuccess()).isTrue();
}

@Test
void fhirQuery_search_returnsBundle() {
    ToolCallRequest request = new ToolCallRequest("fhir_query",
        Map.of("action", "search", "resourceType", "Patient",
               "searchParams", Map.of("family", "Smith")));
    ToolCallResponse response = tool.execute(request);
    assertThat(response.getContent()).contains("Bundle");
}
```

- [ ] **Step 2: Implement FhirQueryTool**

```java
@Component
public class FhirQueryTool implements McpTool {
    private final FhirResourceService resourceService;

    @Override
    public ToolCallResponse execute(ToolCallRequest request) {
        String action = request.getParam("action");
        String resourceType = request.getParam("resourceType");

        return switch (action) {
            case "read" -> doRead(resourceType, request.getParam("id"));
            case "search" -> doSearch(resourceType, request.getParam("searchParams"));
            case "history" -> doHistory(resourceType, request.getParam("id"));
            case "operation" -> doOperation(resourceType, request);
            default -> ToolCallResponse.error("Unknown action: " + action);
        };
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(mcp): implement fhir_query tool"`

---

### Task 8: fhir_mutate Tool with Dry-Run

**Files:**
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/tool/FhirMutateTool.java`
- Test: `fhir4java-mcp/src/test/java/org/fhirframework/mcp/tool/FhirMutateToolTest.java`

- [ ] **Step 1: Write failing tests for create, update, dry-run**

```java
@Test
void fhirMutate_create_persistsResource() {
    ToolCallRequest request = new ToolCallRequest("fhir_mutate",
        Map.of("action", "create", "resourceType", "Patient",
               "body", Map.of("resourceType", "Patient", "name", List.of(...))));
    ToolCallResponse response = tool.execute(request);
    assertThat(response.isSuccess()).isTrue();
}

@Test
void fhirMutate_dryRun_doesNotPersist() {
    ToolCallRequest request = new ToolCallRequest("fhir_mutate",
        Map.of("action", "create", "resourceType", "Patient", "dryRun", true,
               "body", Map.of("resourceType", "Patient")));
    ToolCallResponse response = tool.execute(request);
    assertThat(response.getContent()).contains("dryRun");
    // Verify not persisted
}
```

- [ ] **Step 2: Implement FhirMutateTool with dry-run support**

```java
@Component
public class FhirMutateTool implements McpTool {
    @Override
    public ToolCallResponse execute(ToolCallRequest request) {
        boolean dryRun = request.getParam("dryRun", false);

        if (dryRun) {
            ValidationResult validation = validate(request);
            return ToolCallResponse.success(Map.of(
                "dryRun", true,
                "valid", validation.isValid(),
                "issues", validation.getIssues()
            ));
        }

        return switch (request.getParam("action")) {
            case "create" -> doCreate(request);
            case "update" -> doUpdate(request);
            case "patch" -> doPatch(request);
            case "delete" -> doDelete(request);
            default -> ToolCallResponse.error("Unknown action");
        };
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(mcp): implement fhir_mutate tool with dry-run mode"`

---

### Task 9: Smart Response Hints

**Files:**
- Create: `fhir4java-mcp/src/main/java/org/fhirframework/mcp/hint/ResponseHintGenerator.java`
- Test: `fhir4java-mcp/src/test/java/org/fhirframework/mcp/hint/ResponseHintGeneratorTest.java`

- [ ] **Step 1: Write test for hint generation**

```java
@Test
void generateHint_afterSearch_suggestsRelated() {
    var context = new HintContext("fhir_query", "search", "Patient", List.of(patient));
    String hint = hintGenerator.generate(context);
    assertThat(hint).contains("fhir_query");
    assertThat(hint).contains("Observation");
}
```

- [ ] **Step 2: Implement ResponseHintGenerator**
- [ ] **Step 3: Integrate hints into tool responses**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(mcp): add smart response hints"`

---

## Phase 3: Real-Time Events (Weeks 8-11)

### Task 10: Event Publisher Plugin

**Files:**
- Create: `fhir4java-plugin/src/main/java/org/fhirframework/plugin/event/ResourceChangePlugin.java`
- Create: `fhir4java-api/src/main/java/org/fhirframework/api/event/EventPublisher.java`
- Test: `fhir4java-plugin/src/test/java/org/fhirframework/plugin/event/ResourceChangePluginTest.java`

- [ ] **Step 1: Write test for event publication**

```java
@Test
void resourceCreate_publishesEvent() {
    resourceService.create(patient);
    verify(eventPublisher).publish(argThat(event ->
        event.getResourceType().equals("Patient") &&
        event.getAction().equals("create")
    ));
}
```

- [ ] **Step 2: Implement ResourceChangePlugin (AFTER phase)**

```java
@Component
public class ResourceChangePlugin implements FhirPlugin {
    @Override
    public PluginPhase getPhase() { return PluginPhase.AFTER; }

    @Override
    public PluginResult execute(PluginContext context) {
        eventPublisher.publish(new ResourceChangeEvent(
            context.getResourceType(),
            context.getResourceId(),
            context.getOperationType().name().toLowerCase()
        ));
        return PluginResult.continueProcessing();
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(plugin): add ResourceChangePlugin for event emission"`

---

### Task 11: SSE Event Stream

**Files:**
- Create: `fhir4java-api/src/main/java/org/fhirframework/api/event/EventStreamController.java`
- Test: `fhir4java-api/src/test/java/org/fhirframework/api/event/EventStreamControllerTest.java`

- [ ] **Step 1: Write test for SSE endpoint**

```java
@Test
void sseEndpoint_streamsEvents() {
    Flux<ServerSentEvent<String>> events = webTestClient.get()
        .uri("/api/events/stream?topics=Patient")
        .exchange()
        .expectStatus().isOk()
        .returnResult(ServerSentEvent.class)
        .getResponseBody();

    // Trigger an event
    resourceService.create(patient);

    StepVerifier.create(events.take(1))
        .expectNextMatches(e -> e.data().contains("Patient"))
        .verifyComplete();
}
```

- [ ] **Step 2: Implement EventStreamController**

```java
@RestController
@RequestMapping("/api/events")
public class EventStreamController {
    private final Sinks.Many<ResourceChangeEvent> eventSink =
        Sinks.many().multicast().onBackpressureBuffer();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ResourceChangeEvent>> stream(
            @RequestParam(required = false) List<String> topics) {
        return eventSink.asFlux()
            .filter(e -> topics == null || topics.contains(e.getResourceType()))
            .map(e -> ServerSentEvent.builder(e)
                .event("resource-change")
                .build());
    }
}
```

- [ ] **Step 3: Run tests, verify pass**
- [ ] **Step 4: Commit** `git commit -m "feat(api): add SSE event stream endpoint"`

---

### Task 12: Webhook Registry

**Files:**
- Create: `fhir4java-core/src/main/java/org/fhirframework/core/subscription/WebhookRegistry.java`
- Create: `fhir4java-persistence/src/main/java/org/fhirframework/persistence/entity/WebhookEntity.java`
- Create: `fhir4java-api/src/main/java/org/fhirframework/api/controller/WebhookController.java`
- Test: `fhir4java-api/src/test/java/org/fhirframework/api/controller/WebhookControllerTest.java`

- [ ] **Step 1: Write test for webhook registration**

```java
@Test
void registerWebhook_savesAndReturnsId() {
    WebhookRequest request = new WebhookRequest(
        "https://agent.example.com/callback",
        List.of("Patient.create", "Patient.update"),
        "hmac-secret"
    );

    webTestClient.post().uri("/api/webhooks")
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isNotEmpty();
}
```

- [ ] **Step 2: Implement WebhookEntity and repository**
- [ ] **Step 3: Implement WebhookController**
- [ ] **Step 4: Implement webhook dispatch in EventPublisher**
- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(api): add webhook registry for agent callbacks"`

---

### Task 13: FHIR Subscription Support

**Files:**
- Create: `fhir4java-core/src/main/java/org/fhirframework/core/subscription/SubscriptionManager.java`
- Create: `fhir4java-core/src/main/java/org/fhirframework/core/subscription/SubscriptionTopic.java`
- Test: `fhir4java-core/src/test/java/org/fhirframework/core/subscription/SubscriptionManagerTest.java`

- [ ] **Step 1: Write test for subscription matching**

```java
@Test
void subscriptionManager_matchesEventToSubscribers() {
    Subscription sub = createSubscription("Patient", "rest-hook", "http://callback");
    subscriptionManager.register(sub);

    ResourceChangeEvent event = new ResourceChangeEvent("Patient", "123", "create");
    List<Subscription> matched = subscriptionManager.findMatching(event);

    assertThat(matched).hasSize(1);
}
```

- [ ] **Step 2: Implement SubscriptionManager**
- [ ] **Step 3: Integrate with EventPublisher**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(core): add FHIR Subscription support"`

---

## Integration Tests

### Task 14: End-to-End MCP Integration Test

**Files:**
- Create: `fhir4java-server/src/test/java/org/fhirframework/server/McpIntegrationTest.java`

- [ ] **Step 1: Write E2E test for full MCP workflow**

```java
@Test
void mcpWorkflow_discoverQueryMutate() {
    // 1. Discover
    var discoverResponse = mcpClient.callTool("fhir_discover",
        Map.of("topic", "resources"));
    assertThat(discoverResponse).contains("Patient");

    // 2. Query
    var queryResponse = mcpClient.callTool("fhir_query",
        Map.of("action", "search", "resourceType", "Patient"));
    assertThat(queryResponse).contains("Bundle");

    // 3. Mutate (dry-run)
    var dryRunResponse = mcpClient.callTool("fhir_mutate",
        Map.of("action", "create", "dryRun", true, ...));
    assertThat(dryRunResponse).contains("valid");

    // 4. Mutate (real)
    var createResponse = mcpClient.callTool("fhir_mutate",
        Map.of("action", "create", ...));
    assertThat(createResponse).contains("id");
}
```

- [ ] **Step 2: Run integration test**
- [ ] **Step 3: Commit** `git commit -m "test: add E2E MCP integration test"`

---

## Summary

| Phase | Tasks | Key Deliverables |
|-------|-------|------------------|
| Phase 1 | 1-3 | DiscoveryService, OAuth2, API Key Auth |
| Phase 2 | 4-9 | fhir4java-mcp module, 3 MCP tools, dry-run, hints |
| Phase 3 | 10-13 | SSE streaming, webhooks, FHIR Subscriptions |
| Integration | 14 | E2E test |

**Total: 14 tasks**
