package com.reviewpilot.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "review_executions")
public class ReviewExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "pull_request_id", nullable = false, updatable = false)
    private UUID pullRequestId;

    @Column(name = "head_sha", nullable = false, updatable = false, length = 40)
    private String headSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReviewExecutionStatus status;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(name = "schema_version", length = 32)
    private String schemaVersion;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "limitations_json", columnDefinition = "TEXT")
    private String limitationsJson;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewExecution() {
    }

    public static ReviewExecution createPending(
            UUID repositoryId,
            UUID pullRequestId,
            String headSha,
            Instant now
    ) {
        ReviewExecution execution = new ReviewExecution();
        execution.id = UUID.randomUUID();
        execution.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId is required");
        execution.pullRequestId = Objects.requireNonNull(pullRequestId, "pullRequestId is required");
        execution.headSha = Objects.requireNonNull(headSha, "headSha is required");
        execution.status = ReviewExecutionStatus.PENDING;
        execution.createdAt = Objects.requireNonNull(now, "now is required");
        return execution;
    }

    public void markRunning(Instant now) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException("Cannot transition terminal execution from " + this.status + " to RUNNING.");
        }
        this.status = ReviewExecutionStatus.RUNNING;
        if (this.startedAt == null) {
            this.startedAt = Objects.requireNonNull(now, "now is required");
        }
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void recordDispatchFailure(String sanitizedMessage, Instant now) {
        if (this.status != ReviewExecutionStatus.PENDING) {
            throw new IllegalStateException("Can only record dispatch failure when execution is PENDING.");
        }
        this.failureCode = "N8N_DISPATCH_FAILED";
        this.failureMessage = Objects.requireNonNull(sanitizedMessage, "sanitizedMessage is required");
    }

    public void markCompleted(
            ReviewExecutionStatus completionStatus,
            String schemaVersion,
            String modelName,
            String promptVersion,
            String summary,
            String limitationsJson,
            Long inputTokens,
            Long outputTokens,
            Instant now
    ) {
        if (completionStatus != ReviewExecutionStatus.COMPLETED && completionStatus != ReviewExecutionStatus.PARTIAL) {
            throw new IllegalArgumentException("Completion status must be COMPLETED or PARTIAL.");
        }
        this.status = completionStatus;
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion is required");
        this.modelName = Objects.requireNonNull(modelName, "modelName is required");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion is required");
        this.summary = Objects.requireNonNull(summary, "summary is required");
        this.limitationsJson = limitationsJson;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.completedAt = Objects.requireNonNull(now, "now is required");
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String failureCode, String sanitizedMessage, Instant now) {
        this.status = ReviewExecutionStatus.FAILED;
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode is required");
        this.failureMessage = Objects.requireNonNull(sanitizedMessage, "sanitizedMessage is required");
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.completedAt = Objects.requireNonNull(now, "now is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getPullRequestId() {
        return pullRequestId;
    }

    public String getHeadSha() {
        return headSha;
    }

    public ReviewExecutionStatus getStatus() {
        return status;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getSummary() {
        return summary;
    }

    public String getLimitationsJson() {
        return limitationsJson;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
