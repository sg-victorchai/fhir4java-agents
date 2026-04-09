# Phase 9: Observability & Explainability Implementation Plan (Weeks 48-55)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Full observability stack with audit query API, metrics collection, Grafana dashboards, and trace correlation for debugging agent interactions.

**Architecture:** Micrometer metrics exposed to Prometheus. Grafana dashboards for MCP tool usage and performance. Audit query API for compliance. Trace correlation linking MCP calls to FHIR operations.

**Tech Stack:** Micrometer, Prometheus, Grafana, OpenTelemetry (tracing)

**Spec Reference:** `docs/superpowers/specs/2026-03-22-ai-data-platform.md` - Pillar 10

---

## File Structure

```
fhir4java-api/src/main/java/org/fhirframework/api/
├── audit/
│   └── AuditQueryController.java        # GET /api/audit/mcp - query audit logs

fhir4java-mcp/src/main/java/org/fhirframework/mcp/
├── metrics/
│   ├── McpMetricsService.java           # Micrometer metrics collection
│   └── McpMetricsInterceptor.java       # Intercepts tool calls for metrics
├── tracing/
│   └── McpTracingInterceptor.java       # OpenTelemetry span creation

fhir4java-server/src/main/resources/
├── grafana/
│   └── mcp-dashboard.json               # Pre-built Grafana dashboard

docker/
├── docker-compose-observability.yml     # Prometheus + Grafana stack
├── prometheus/
│   └── prometheus.yml                   # Scrape config
└── grafana/
    └── provisioning/                    # Auto-provision dashboards
```

---

## Tasks

### Task 1: Audit Query API

**Files:** `fhir4java-api/.../audit/AuditQueryController.java`

- [ ] **Step 1: Write test for audit query endpoint**

```java
@Test
void auditQuery_filtersByAgentAndTimeRange() {
    // Insert test audit logs
    insertAuditLog("agent-1", "fhir_query", Instant.now().minus(1, ChronoUnit.HOURS));
    insertAuditLog("agent-2", "fhir_mutate", Instant.now());

    webTestClient.get()
        .uri("/api/audit/mcp?agentId=agent-1&from=2026-01-01T00:00:00Z")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.entries.length()").isEqualTo(1)
        .jsonPath("$.entries[0].agentId").isEqualTo("agent-1");
}
```

- [ ] **Step 2: Implement AuditQueryController**

```java
@RestController
@RequestMapping("/api/audit")
public class AuditQueryController {
    private final McpAuditLogRepository auditRepository;

    @GetMapping("/mcp")
    public AuditQueryResponse queryMcpAudit(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Specification<McpAuditLogEntity> spec = buildSpec(agentId, toolName, resourceType, from, to);
        Page<McpAuditLogEntity> results = auditRepository.findAll(spec, PageRequest.of(page, size));

        return new AuditQueryResponse(results.getContent(), results.getTotalElements());
    }
}
```

- [ ] **Step 3: Add authorization (admin/audit roles only)**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(api): add MCP audit query endpoint"`

---

### Task 2: Metrics Collection Service

**Files:** `fhir4java-mcp/.../metrics/McpMetricsService.java`

- [ ] **Step 1: Add Micrometer dependencies**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 2: Write test for metrics recording**

```java
@Test
void metricsService_recordsToolLatency() {
    metricsService.recordToolCall("fhir_query", "search", 150, true);

    MeterRegistry registry = metricsService.getRegistry();
    Timer timer = registry.find("mcp.tool.latency")
        .tag("tool", "fhir_query")
        .tag("action", "search")
        .timer();

    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.mean(TimeUnit.MILLISECONDS)).isCloseTo(150, within(1.0));
}
```

- [ ] **Step 3: Implement McpMetricsService**

```java
@Service
public class McpMetricsService {
    private final MeterRegistry registry;

    public void recordToolCall(String toolName, String action, long latencyMs, boolean success) {
        Timer.builder("mcp.tool.latency")
            .tag("tool", toolName)
            .tag("action", action != null ? action : "unknown")
            .tag("success", String.valueOf(success))
            .register(registry)
            .record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder("mcp.tool.calls")
            .tag("tool", toolName)
            .tag("action", action != null ? action : "unknown")
            .tag("success", String.valueOf(success))
            .register(registry)
            .increment();
    }

    public void recordError(String toolName, String errorType) {
        Counter.builder("mcp.tool.errors")
            .tag("tool", toolName)
            .tag("error_type", errorType)
            .register(registry)
            .increment();
    }
}
```

- [ ] **Step 4: Integrate into McpEndpoint**
- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(mcp): add Micrometer metrics collection"`

---

### Task 3: Prometheus & Grafana Stack

**Files:** `docker/docker-compose-observability.yml`, `docker/prometheus/prometheus.yml`

- [ ] **Step 1: Create docker-compose-observability.yml**

```yaml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:v2.47.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.1.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
```

- [ ] **Step 2: Create prometheus.yml scrape config**

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'fhir4java'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

- [ ] **Step 3: Enable Prometheus actuator endpoint in application.yml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

- [ ] **Step 4: Verify stack starts** `docker-compose -f docker/docker-compose-observability.yml up`
- [ ] **Step 5: Commit** `git commit -m "infra: add Prometheus and Grafana observability stack"`

---

### Task 4: MCP Dashboard

**Files:** `fhir4java-server/src/main/resources/grafana/mcp-dashboard.json`

- [ ] **Step 1: Create Grafana dashboard JSON**

```json
{
  "title": "MCP Tool Metrics",
  "panels": [
    {
      "title": "Tool Call Rate",
      "type": "graph",
      "targets": [
        {"expr": "rate(mcp_tool_calls_total[5m])", "legendFormat": "{{tool}}-{{action}}"}
      ]
    },
    {
      "title": "Tool Latency (p95)",
      "type": "graph",
      "targets": [
        {"expr": "histogram_quantile(0.95, rate(mcp_tool_latency_seconds_bucket[5m]))", "legendFormat": "{{tool}}"}
      ]
    },
    {
      "title": "Error Rate",
      "type": "graph",
      "targets": [
        {"expr": "rate(mcp_tool_errors_total[5m])", "legendFormat": "{{tool}}-{{error_type}}"}
      ]
    },
    {
      "title": "Active Agents",
      "type": "stat",
      "targets": [
        {"expr": "count(count by (agent_id) (mcp_tool_calls_total))"}
      ]
    }
  ]
}
```

- [ ] **Step 2: Add Grafana provisioning config**

```yaml
# grafana/provisioning/dashboards/dashboards.yml
apiVersion: 1
providers:
  - name: 'default'
    folder: 'FHIR4Java'
    type: file
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] **Step 3: Copy dashboard to provisioning folder**
- [ ] **Step 4: Verify dashboard loads in Grafana**
- [ ] **Step 5: Commit** `git commit -m "feat(observability): add MCP metrics Grafana dashboard"`

---

### Task 5: OpenTelemetry Tracing

**Files:** `fhir4java-mcp/.../tracing/McpTracingInterceptor.java`

- [ ] **Step 1: Add OpenTelemetry dependencies**

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 2: Write test for trace propagation**

```java
@Test
void mcpToolCall_createsSpan() {
    // Call tool
    mcpEndpoint.handle(new McpRequest("tools/call",
        Map.of("name", "fhir_query", "arguments", Map.of("action", "read"))));

    // Verify span created
    List<SpanData> spans = testExporter.getFinishedSpanItems();
    assertThat(spans).anyMatch(span ->
        span.getName().equals("mcp.tool.fhir_query") &&
        span.getAttributes().get(AttributeKey.stringKey("action")).equals("read")
    );
}
```

- [ ] **Step 3: Implement McpTracingInterceptor**

```java
@Component
public class McpTracingInterceptor {
    private final Tracer tracer;

    public <T> T traceToolCall(String toolName, Map<String, Object> params, Supplier<T> execution) {
        Span span = tracer.spanBuilder("mcp.tool." + toolName)
            .setAttribute("tool", toolName)
            .setAttribute("action", (String) params.get("action"))
            .setAttribute("resourceType", (String) params.get("resourceType"))
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = execution.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

- [ ] **Step 4: Integrate into McpEndpoint**
- [ ] **Step 5: Run tests, verify pass**
- [ ] **Step 6: Commit** `git commit -m "feat(mcp): add OpenTelemetry tracing for MCP tools"`

---

### Task 6: Trace Correlation

**Files:** `fhir4java-mcp/.../tracing/`, `fhir4java-mcp/.../audit/McpAuditPlugin.java`

- [ ] **Step 1: Write test for trace ID in audit logs**

```java
@Test
void auditLog_containsTraceId() {
    // Call tool with active trace
    Span span = tracer.spanBuilder("test").startSpan();
    try (Scope scope = span.makeCurrent()) {
        mcpEndpoint.handle(request);
    }

    McpAuditLogEntity log = auditRepository.findAll().get(0);
    assertThat(log.getTraceId()).isNotNull();
    assertThat(log.getTraceId()).isEqualTo(span.getSpanContext().getTraceId());
}
```

- [ ] **Step 2: Update McpAuditPlugin to capture trace ID**

```java
public void logToolCall(...) {
    McpAuditLogEntity log = new McpAuditLogEntity();
    // ... existing fields ...

    // Capture trace ID from current span
    Span currentSpan = Span.current();
    if (currentSpan.getSpanContext().isValid()) {
        log.setTraceId(currentSpan.getSpanContext().getTraceId());
    }

    auditRepository.save(log);
}
```

- [ ] **Step 3: Add trace ID to audit query response**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit** `git commit -m "feat(mcp): add trace correlation to audit logs"`

---

## Summary

| Task | Deliverable |
|------|-------------|
| 1 | Audit query API for compliance |
| 2 | Micrometer metrics collection |
| 3 | Prometheus & Grafana stack |
| 4 | Pre-built MCP dashboard |
| 5 | OpenTelemetry tracing |
| 6 | Trace correlation |

**Total: 6 tasks**
