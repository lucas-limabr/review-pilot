package com.reviewpilot.approval.presentation;

import com.reviewpilot.approval.domain.ApprovalDecision;
import com.reviewpilot.approval.domain.ReviewAction;
import com.reviewpilot.approval.domain.ReviewActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record SubmitApprovalRequest(
            @NotNull(message = "decision is required")
            ApprovalDecision decision,

            @Size(max = 255, message = "actor must not exceed 255 characters")
            String actor,

            @Size(max = 2000, message = "comment must not exceed 2000 characters")
            String comment
    ) {
    }

    public record ApprovalActionResponse(
            UUID id,
            UUID reviewExecutionId,
            ReviewActionType actionType,
            ApprovalDecision decision,
            String actor,
            String comment,
            Instant createdAt
    ) {
        public static ApprovalActionResponse from(ReviewAction action, String comment) {
            return new ApprovalActionResponse(
                    action.getId(),
                    action.getReviewExecutionId(),
                    action.getActionType(),
                    action.getDecision(),
                    action.getActor(),
                    comment,
                    action.getCreatedAt()
            );
        }
    }
}

