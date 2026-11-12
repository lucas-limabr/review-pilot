package com.reviewpilot.review.presentation;

import com.reviewpilot.review.application.ReviewExecutionService;
import com.reviewpilot.review.presentation.ReviewDtos.AgentReviewFailurePayload;
import com.reviewpilot.review.presentation.ReviewDtos.AgentReviewResultPayload;
import com.reviewpilot.review.presentation.ReviewDtos.ReviewDetailResponse;
import com.reviewpilot.shared.config.OpenApiConfig;
import com.reviewpilot.webhook.application.GitHubWebhookService;
import com.reviewpilot.webhook.presentation.WebhookDtos.GitHubWebhookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/reviews")
@Tag(name = "Internal n8n Callbacks", description = "Authenticated internal endpoints called by the n8n workflow or internal retry")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_TOKEN_SCHEME)
public class InternalReviewCallbackController {

    private final ReviewExecutionService reviewExecutionService;
    private final GitHubWebhookService gitHubWebhookService;

    public InternalReviewCallbackController(
            ReviewExecutionService reviewExecutionService,
            GitHubWebhookService gitHubWebhookService
    ) {
        this.reviewExecutionService = reviewExecutionService;
        this.gitHubWebhookService = gitHubWebhookService;
    }

    @PostMapping("/{id}/results")
    @Operation(summary = "Receive structured review findings and summary from the n8n AI Agent workflow")
    public ReviewDetailResponse submitReviewResults(
            @PathVariable("id") UUID reviewExecutionId,
            @Valid @RequestBody AgentReviewResultPayload payload
    ) {
        return reviewExecutionService.recordAgentResult(reviewExecutionId, payload);
    }

    @PostMapping("/{id}/failure")
    @Operation(summary = "Record a controlled failure from the n8n AI Agent workflow")
    public ReviewDetailResponse submitReviewFailure(
            @PathVariable("id") UUID reviewExecutionId,
            @Valid @RequestBody AgentReviewFailurePayload payload
    ) {
        return reviewExecutionService.recordAgentFailure(reviewExecutionId, payload);
    }

    @PostMapping("/{id}/dispatch")
    @Operation(summary = "Retry dispatching a PENDING review execution to n8n in a controlled and idempotent manner")
    public GitHubWebhookResponse retryPendingDispatch(@PathVariable("id") UUID reviewExecutionId) {
        return gitHubWebhookService.retryPendingDispatch(reviewExecutionId);
    }
}
