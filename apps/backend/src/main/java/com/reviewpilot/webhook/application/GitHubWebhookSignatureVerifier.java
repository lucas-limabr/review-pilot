package com.reviewpilot.webhook.application;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import com.reviewpilot.shared.exception.ForbiddenOperationException;
import com.reviewpilot.shared.exception.UnauthorizedOperationException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GitHubWebhookSignatureVerifier {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final ReviewPilotProperties properties;

    public GitHubWebhookSignatureVerifier(ReviewPilotProperties properties) {
        this.properties = properties;
    }

    public void verifySignature(String signatureHeader, byte[] rawBody) {
        if (!StringUtils.hasText(signatureHeader)) {
            throw new UnauthorizedOperationException("Missing X-Hub-Signature-256 header.");
        }

        String trimmedHeader = signatureHeader.trim();
        if (!trimmedHeader.startsWith(SIGNATURE_PREFIX) || trimmedHeader.length() != SIGNATURE_PREFIX.length() + 64) {
            throw new ForbiddenOperationException("Invalid X-Hub-Signature-256 header format.");
        }

        byte[] bodyBytes = (rawBody == null) ? new byte[0] : rawBody;
        String expectedSignature = computeExpectedSignature(bodyBytes);

        boolean matches = MessageDigest.isEqual(
                trimmedHeader.toLowerCase().getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            throw new ForbiddenOperationException("Invalid X-Hub-Signature-256 HMAC digest.");
        }
    }

    public String computeExpectedSignature(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            byte[] secretBytes = properties.getSecurity().getGithubWebhookSecret().getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, HMAC_SHA256_ALGORITHM));
            byte[] digest = mac.doFinal(rawBody);
            return SIGNATURE_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to initialize HMAC-SHA256 verifier.", ex);
        }
    }
}

