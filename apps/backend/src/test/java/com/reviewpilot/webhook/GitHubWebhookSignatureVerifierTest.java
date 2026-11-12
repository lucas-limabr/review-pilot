package com.reviewpilot.webhook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import com.reviewpilot.shared.exception.ForbiddenOperationException;
import com.reviewpilot.shared.exception.UnauthorizedOperationException;
import com.reviewpilot.webhook.application.GitHubWebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubWebhookSignatureVerifierTest {

    private GitHubWebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        ReviewPilotProperties properties = new ReviewPilotProperties();
        properties.getSecurity().setGithubWebhookSecret("unit-test-webhook-secret-key-0001");
        properties.getSecurity().setInternalServiceToken("unit-test-internal-token-0001");
        properties.getN8n().setInternalWebhookUrl("http://n8n:5678/webhook/reviewpilot-dispatch");
        verifier = new GitHubWebhookSignatureVerifier(properties);
    }

    @Test
    void shouldAcceptValidHmacSha256SignatureOverRawBytes() {
        byte[] rawBody = "{\"action\":\"opened\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        String validSignature = verifier.computeExpectedSignature(rawBody);

        assertThatCode(() -> verifier.verifySignature(validSignature, rawBody))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingSignatureHeader() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verifySignature(null, rawBody))
                .isInstanceOf(UnauthorizedOperationException.class)
                .hasMessageContaining("Missing X-Hub-Signature-256");
    }

    @Test
    void shouldRejectMalformedSignaturePrefix() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verifySignature("sha1=123456", rawBody))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Invalid X-Hub-Signature-256 header format");
    }

    @Test
    void shouldRejectTamperedPayloadBytes() {
        byte[] originalBody = "{\"action\":\"opened\",\"number\":42}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"action\":\"opened\",\"number\":43}".getBytes(StandardCharsets.UTF_8);
        String signatureForOriginal = verifier.computeExpectedSignature(originalBody);

        assertThatThrownBy(() -> verifier.verifySignature(signatureForOriginal, tamperedBody))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Invalid X-Hub-Signature-256 HMAC digest");
    }
}

