package org.fhirframework.api.dto;

import java.util.List;

/**
 * Request DTO for registering a webhook.
 *
 * @param callbackUrl URL to POST event notifications to
 * @param topics List of topics to subscribe to (e.g., ["Patient.create", "Patient.update"])
 * @param secret HMAC secret for signing webhook payloads (optional)
 */
public record WebhookRequest(
        String callbackUrl,
        List<String> topics,
        String secret
) {
    /**
     * Validate the webhook request.
     *
     * @throws IllegalArgumentException if the request is invalid
     */
    public void validate() {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalArgumentException("callbackUrl is required");
        }
        if (!callbackUrl.startsWith("http://") && !callbackUrl.startsWith("https://")) {
            throw new IllegalArgumentException("callbackUrl must be a valid HTTP(S) URL");
        }
    }
}
