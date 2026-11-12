package com.reviewpilot.webhook.presentation;

import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.webhook.domain.WebhookProcessingStatus;
import java.time.Instant;
import java.util.UUID;

public final class WebhookDtos {

    private WebhookDtos() {
    }

    public record GitHubWebhookResponse(
            String deliveryId,
            String eventType,
            String action,
            WebhookProcessingStatus deliveryStatus,
            UUID reviewExecutionId,
            ReviewExecutionStatus reviewExecutionStatus,
            String message,
            Instant processedAt
    ) {
    }
}

