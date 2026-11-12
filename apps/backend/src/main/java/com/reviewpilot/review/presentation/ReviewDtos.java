package com.reviewpilot.review.presentation;

import com.reviewpilot.approval.domain.ApprovalDecision;
import com.reviewpilot.review.domain.Finding;
import com.reviewpilot.review.domain.FindingCategory;
import com.reviewpilot.review.domain.FindingOrigin;
import com.reviewpilot.review.domain.FindingSeverity;
import com.reviewpilot.review.domain.PullRequestRecord;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record AgentReviewResultPayload(
            @NotBlank(message = "schemaVersion is required")
            @Pattern(regexp = "^1\\.0$", message = "schemaVersion must be '1.0'")
            String schemaVersion,

            @NotNull(message = "reviewExecutionId is required")
            UUID reviewExecutionId,

            @NotBlank(message = "repositoryFullName is required")
            @Size(max = 255, message = "repositoryFullName must not exceed 255 characters")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", message = "repositoryFullName must match owner/repository")
            String repositoryFullName,

            @NotNull(message = "pullRequestNumber is required")
            @Min(value = 1, message = "pullRequestNumber must be >= 1")
            Integer pullRequestNumber,

            @NotBlank(message = "analyzedHeadSha is required")
            @Pattern(regexp = "^[0-9a-fA-F]{40}$", message = "analyzedHeadSha must be a 40-character hex SHA")
            String analyzedHeadSha,

            @NotBlank(message = "model is required")
            @Size(max = 128, message = "model must not exceed 128 characters")
            String model,

            @NotBlank(message = "promptVersion is required")
            @Size(max = 32, message = "promptVersion must not exceed 32 characters")
            String promptVersion,

            @NotBlank(message = "summary is required")
            @Size(max = 4000, message = "summary must not exceed 4000 characters")
            String summary,

            @NotNull(message = "findings list is required")
            @Size(max = 50, message = "findings must not exceed 50 items")
            List<@Valid AgentFindingPayload> findings,

            @NotNull(message = "limitations list is required")
            @Size(max = 20, message = "limitations must not exceed 20 items")
            List<@NotBlank @Size(max = 1000) String> limitations,

            @NotNull(message = "usage is required")
            @Valid
            TokenUsagePayload usage
    ) {
    }

    public record AgentFindingPayload(
            @NotBlank(message = "fingerprint is required")
            @Size(max = 128, message = "fingerprint must not exceed 128 characters")
            String fingerprint,

            @NotNull(message = "category is required")
            FindingCategory category,

            @NotNull(message = "severity is required")
            FindingSeverity severity,

            @NotBlank(message = "title is required")
            @Size(max = 255, message = "title must not exceed 255 characters")
            String title,

            @NotBlank(message = "description is required")
            @Size(max = 4000, message = "description must not exceed 4000 characters")
            String description,

            @Size(max = 1024, message = "filePath must not exceed 1024 characters")
            String filePath,

            Integer startLine,

            Integer endLine,

            @NotBlank(message = "evidence is required")
            @Size(max = 4000, message = "evidence must not exceed 4000 characters")
            String evidence,

            @NotBlank(message = "recommendation is required")
            @Size(max = 4000, message = "recommendation must not exceed 4000 characters")
            String recommendation,

            @DecimalMin(value = "0.0", message = "confidence must be >= 0.0")
            @DecimalMax(value = "1.0", message = "confidence must be <= 1.0")
            BigDecimal confidence,

            @NotNull(message = "origin is required")
            FindingOrigin origin,

            @Size(max = 255, message = "externalReference must not exceed 255 characters")
            String externalReference
    ) {
    }

    public record TokenUsagePayload(
            @Min(value = 0, message = "inputTokens must be >= 0")
            Long inputTokens,

            @Min(value = 0, message = "outputTokens must be >= 0")
            Long outputTokens
    ) {
    }

    public record AgentReviewFailurePayload(
            @NotBlank(message = "stage is required")
            @Size(max = 64, message = "stage must not exceed 64 characters")
            String stage,

            @NotBlank(message = "errorCode is required")
            @Size(max = 128, message = "errorCode must not exceed 128 characters")
            String errorCode,

            @NotBlank(message = "message is required")
            @Size(max = 2000, message = "message must not exceed 2000 characters")
            String message,

            boolean retryable
    ) {
    }

    public record ReviewSummaryItemResponse(
            UUID id,
            UUID repositoryId,
            String repositoryFullName,
            UUID pullRequestId,
            Integer pullRequestNumber,
            String pullRequestTitle,
            String headSha,
            ReviewExecutionStatus status,
            String modelName,
            String promptVersion,
            long findingsCount,
            ApprovalDecision latestApprovalDecision,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt
    ) {
    }

    public record FindingResponse(
            UUID id,
            String fingerprint,
            FindingCategory category,
            FindingSeverity severity,
            String title,
            String description,
            String filePath,
            Integer startLine,
            Integer endLine,
            String evidence,
            String recommendation,
            BigDecimal confidence,
            FindingOrigin origin,
            String externalReference,
            Instant createdAt
    ) {
        public static FindingResponse from(Finding entity) {
            return new FindingResponse(
                    entity.getId(),
                    entity.getFingerprint(),
                    entity.getCategory(),
                    entity.getSeverity(),
                    entity.getTitle(),
                    entity.getDescription(),
                    entity.getFilePath(),
                    entity.getStartLine(),
                    entity.getEndLine(),
                    entity.getEvidence(),
                    entity.getRecommendation(),
                    entity.getConfidence(),
                    entity.getOrigin(),
                    entity.getExternalReference(),
                    entity.getCreatedAt()
            );
        }
    }

    public record ReviewDetailResponse(
            UUID id,
            UUID repositoryId,
            String repositoryFullName,
            UUID pullRequestId,
            Integer pullRequestNumber,
            String pullRequestTitle,
            String pullRequestHtmlUrl,
            String authorLogin,
            String baseRef,
            String headRef,
            String headSha,
            ReviewExecutionStatus status,
            String modelName,
            String promptVersion,
            String schemaVersion,
            String summary,
            List<String> limitations,
            Long inputTokens,
            Long outputTokens,
            String failureCode,
            String failureMessage,
            ApprovalDecision latestApprovalDecision,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            List<FindingResponse> findings
    ) {
        public static ReviewDetailResponse of(
                ReviewExecution execution,
                String repositoryFullName,
                PullRequestRecord pullRequest,
                List<String> limitations,
                ApprovalDecision latestDecision,
                List<FindingResponse> findings
        ) {
            return new ReviewDetailResponse(
                    execution.getId(),
                    execution.getRepositoryId(),
                    repositoryFullName,
                    execution.getPullRequestId(),
                    pullRequest.getNumber(),
                    pullRequest.getTitle(),
                    pullRequest.getHtmlUrl(),
                    pullRequest.getAuthorLogin(),
                    pullRequest.getBaseRef(),
                    pullRequest.getHeadRef(),
                    execution.getHeadSha(),
                    execution.getStatus(),
                    execution.getModelName(),
                    execution.getPromptVersion(),
                    execution.getSchemaVersion(),
                    execution.getSummary(),
                    limitations,
                    execution.getInputTokens(),
                    execution.getOutputTokens(),
                    execution.getFailureCode(),
                    execution.getFailureMessage(),
                    latestDecision,
                    execution.getStartedAt(),
                    execution.getCompletedAt(),
                    execution.getCreatedAt(),
                    findings
            );
        }
    }

    public record DashboardSummaryResponse(
            long activeRepositoriesCount,
            long totalReviewsCount,
            Map<ReviewExecutionStatus, Long> reviewsByStatus,
            long totalFindingsCount,
            Map<FindingSeverity, Long> findingsBySeverity,
            Map<FindingCategory, Long> findingsByCategory,
            long approvedReviewsCount,
            long rejectedReviewsCount
    ) {
    }
}

