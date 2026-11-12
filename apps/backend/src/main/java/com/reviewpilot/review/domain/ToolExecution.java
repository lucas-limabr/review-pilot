package com.reviewpilot.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tool_executions")
public class ToolExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "review_execution_id", nullable = false, updatable = false)
    private UUID reviewExecutionId;

    @Column(name = "server_name", nullable = false, length = 64)
    private String serverName;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "arguments_summary", columnDefinition = "TEXT")
    private String argumentsSummary;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ToolExecution() {
    }

    public static ToolExecution create(
            UUID reviewExecutionId,
            String serverName,
            String toolName,
            String status,
            Long durationMs,
            String argumentsSummary,
            String errorCode,
            Instant now
    ) {
        ToolExecution execution = new ToolExecution();
        execution.id = UUID.randomUUID();
        execution.reviewExecutionId = Objects.requireNonNull(reviewExecutionId, "reviewExecutionId is required");
        execution.serverName = Objects.requireNonNull(serverName, "serverName is required");
        execution.toolName = Objects.requireNonNull(toolName, "toolName is required");
        execution.status = Objects.requireNonNull(status, "status is required");
        execution.durationMs = Objects.requireNonNull(durationMs, "durationMs is required");
        execution.argumentsSummary = argumentsSummary;
        execution.errorCode = errorCode;
        execution.createdAt = Objects.requireNonNull(now, "now is required");
        return execution;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewExecutionId() {
        return reviewExecutionId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getToolName() {
        return toolName;
    }

    public String getStatus() {
        return status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getArgumentsSummary() {
        return argumentsSummary;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

