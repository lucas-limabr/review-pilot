package com.reviewpilot.webhook.presentation;

import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.webhook.application.GitHubWebhookService;
import com.reviewpilot.webhook.domain.WebhookProcessingStatus;
import com.reviewpilot.webhook.presentation.WebhookDtos.GitHubWebhookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/github")
@Tag(name = "GitHub Webhooks", description = "Receive and validate signed GitHub Pull Request webhooks")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    public GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @Operation(summary = "Receive signed GitHub webhook event and dispatch Pull Request review to n8n")
    public ResponseEntity<GitHubWebhookResponse> receiveGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature256,
            @RequestBody(required = false) byte[] rawBody
    ) {
        GitHubWebhookResponse response = webhookService.processWebhook(
                deliveryId,
                eventType,
                signature256,
                rawBody
        );

        boolean newlyAccepted = (response.deliveryStatus() == WebhookProcessingStatus.DISPATCH_FAILED)
                || (response.deliveryStatus() == WebhookProcessingStatus.PROCESSED
                && response.reviewExecutionStatus() == ReviewExecutionStatus.RUNNING
                && response.message().startsWith("Pull Request review execution created"));

        HttpStatus status = newlyAccepted ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}

