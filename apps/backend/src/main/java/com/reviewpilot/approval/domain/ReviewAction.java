package com.reviewpilot.approval.domain;

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
@Table(name = "review_actions")
public class ReviewAction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "review_execution_id", nullable = false, updatable = false)
    private UUID reviewExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private ReviewActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 32)
    private ApprovalDecision decision;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewAction() {
    }

    public static ReviewAction createHumanDecision(
            UUID reviewExecutionId,
            ApprovalDecision decision,
            String actor,
            String detailsJson,
            Instant now
    ) {
        ReviewAction action = new ReviewAction();
        action.id = UUID.randomUUID();
        action.reviewExecutionId = Objects.requireNonNull(reviewExecutionId, "reviewExecutionId is required");
        action.actionType = ReviewActionType.HUMAN_APPROVAL_DECISION;
        action.decision = Objects.requireNonNull(decision, "decision is required");
        action.actor = (actor == null || actor.isBlank()) ? "local-reviewer" : actor.trim();
        action.details = detailsJson;
        action.createdAt = Objects.requireNonNull(now, "now is required");
        return action;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewExecutionId() {
        return reviewExecutionId;
    }

    public ReviewActionType getActionType() {
        return actionType;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

