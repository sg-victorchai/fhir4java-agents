package org.fhirframework.mcp.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fhirframework.api.dto.WebhookRequest;
import org.fhirframework.api.dto.WebhookResponse;
import org.fhirframework.core.event.ResourceChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Webhook Handler for AI Agents.
 * <p>
 * Demonstrates how AI agents can:
 * <ul>
 *   <li>Register webhooks to receive event callbacks</li>
 *   <li>Validate HMAC signatures on incoming webhooks</li>
 *   <li>Process events based on action type</li>
 * </ul>
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * AgentWebhookHandler handler = new AgentWebhookHandler(webClient, "tenant-1");
 *
 * // Register a webhook during agent initialization
 * WebhookResponse webhook = handler.registerWebhook(
 *     "https://my-agent.example.com/callback",
 *     List.of("Patient.create", "Observation.create"),
 *     "my-secret-key"
 * );
 *
 * // Handle incoming webhook callbacks
 * handler.handleCallback(signature, eventJson, event -> {
 *     if ("Observation".equals(event.resourceType())) {
 *         // Analyze new lab result
 *         analyzeLabResult(event.resourceId());
 *     }
 * });
 * }</pre>
 */
public class AgentWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebhookHandler.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WebClient webClient;
    private final String tenantId;
    private final ObjectMapper objectMapper;
    private final Map<Long, String> webhookSecrets = new ConcurrentHashMap<>();
    private final Map<String, Consumer<ResourceChangeEvent>> actionHandlers = new ConcurrentHashMap<>();

    /**
     * Creates a new AgentWebhookHandler.
     *
     * @param webClient the WebClient configured with the FHIR server base URL
     * @param tenantId  the tenant ID for this agent
     */
    public AgentWebhookHandler(WebClient webClient, String tenantId) {
        this.webClient = webClient;
        this.tenantId = tenantId;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // For Java 8 time support
    }

    /**
     * Creates a new AgentWebhookHandler.
     *
     * @param serverBaseUrl the base URL of the FHIR server
     * @param tenantId      the tenant ID for this agent
     */
    public AgentWebhookHandler(String serverBaseUrl, String tenantId) {
        this(WebClient.builder().baseUrl(serverBaseUrl).build(), tenantId);
    }

    /**
     * Registers a webhook for the agent to receive callbacks.
     * <p>
     * Called during agent initialization to set up event notifications.
     * </p>
     *
     * @param callbackUrl the URL where webhook events will be POSTed
     * @param topics      list of topics to subscribe to (e.g., ["Patient.create", "Patient.update"])
     * @param hmacSecret  secret for HMAC signature validation (optional)
     * @return the created webhook response
     */
    public WebhookResponse registerWebhook(String callbackUrl, List<String> topics, String hmacSecret) {
        log.info("Registering webhook for callback URL: {}", callbackUrl);

        WebhookRequest request = new WebhookRequest(callbackUrl, topics, hmacSecret);

        WebhookResponse response = webClient.post()
                .uri("/api/webhooks")
                .header("X-Tenant-ID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(WebhookResponse.class)
                .block();

        if (response != null && hmacSecret != null) {
            webhookSecrets.put(response.id(), hmacSecret);
            log.info("Webhook registered with ID: {}", response.id());
        }

        return response;
    }

    /**
     * Registers a webhook asynchronously.
     *
     * @param callbackUrl the callback URL
     * @param topics      the topics to subscribe to
     * @param hmacSecret  the HMAC secret
     * @return a Mono containing the webhook response
     */
    public Mono<WebhookResponse> registerWebhookAsync(String callbackUrl, List<String> topics, String hmacSecret) {
        WebhookRequest request = new WebhookRequest(callbackUrl, topics, hmacSecret);

        return webClient.post()
                .uri("/api/webhooks")
                .header("X-Tenant-ID", tenantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(WebhookResponse.class)
                .doOnSuccess(response -> {
                    if (response != null && hmacSecret != null) {
                        webhookSecrets.put(response.id(), hmacSecret);
                    }
                });
    }

    /**
     * Unregisters a webhook by its ID.
     *
     * @param webhookId the webhook ID to unregister
     * @return true if successfully unregistered
     */
    public boolean unregisterWebhook(Long webhookId) {
        log.info("Unregistering webhook: {}", webhookId);

        try {
            webClient.delete()
                    .uri("/api/webhooks/{id}", webhookId)
                    .header("X-Tenant-ID", tenantId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            webhookSecrets.remove(webhookId);
            return true;
        } catch (Exception e) {
            log.error("Failed to unregister webhook: {}", webhookId, e);
            return false;
        }
    }

    /**
     * Handles an incoming webhook callback.
     * <p>
     * Validates the HMAC signature (if a secret is configured) and processes the event.
     * </p>
     *
     * @param signature    the X-Webhook-Signature header value
     * @param payload      the raw JSON payload
     * @param secret       the HMAC secret for validation
     * @param eventHandler callback to process the event
     * @return HTTP response entity
     */
    public ResponseEntity<Void> handleCallback(String signature, String payload, String secret,
                                               Consumer<ResourceChangeEvent> eventHandler) {
        // Validate HMAC signature if secret is provided
        if (secret != null && !signature.isEmpty()) {
            if (!validateSignature(signature, payload, secret)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        try {
            // Parse the event
            ResourceChangeEvent event = objectMapper.readValue(payload, ResourceChangeEvent.class);

            // Process the event
            log.debug("Processing webhook event: {} {} {}",
                    event.resourceType(), event.resourceId(), event.action());

            // Call the event handler
            eventHandler.accept(event);

            return ResponseEntity.ok().build();

        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to process webhook event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Registers an action handler for specific event actions.
     * <p>
     * These handlers are invoked when processing webhook callbacks.
     * </p>
     *
     * @param action  the action to handle ("create", "update", "delete")
     * @param handler the handler for this action
     */
    public void registerActionHandler(String action, Consumer<ResourceChangeEvent> handler) {
        actionHandlers.put(action, handler);
    }

    /**
     * Processes an event using registered action handlers.
     *
     * @param event the event to process
     */
    public void processEvent(ResourceChangeEvent event) {
        Consumer<ResourceChangeEvent> handler = actionHandlers.get(event.action());
        if (handler != null) {
            handler.accept(event);
        } else {
            log.debug("No handler registered for action: {}", event.action());
        }
    }

    /**
     * Validates an HMAC-SHA256 signature.
     *
     * @param signature the signature header value (format: "sha256=base64encoded")
     * @param payload   the raw payload that was signed
     * @param secret    the HMAC secret
     * @return true if the signature is valid
     */
    public boolean validateSignature(String signature, String payload, String secret) {
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String expectedSignature = computeSignature(payload, secret);
        String actualSignature = signature.substring(SIGNATURE_PREFIX.length());

        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(expectedSignature, actualSignature);
    }

    /**
     * Computes an HMAC-SHA256 signature for a payload.
     *
     * @param payload the payload to sign
     * @param secret  the HMAC secret
     * @return the base64-encoded signature
     */
    public String computeSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Gets the tenant ID for this handler.
     *
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Lists all webhooks for this agent's tenant.
     *
     * @return list of webhook responses
     */
    public List<WebhookResponse> listWebhooks() {
        return webClient.get()
                .uri("/api/webhooks")
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .bodyToFlux(WebhookResponse.class)
                .collectList()
                .block();
    }
}
