package com.reviewpilot.approval.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.approval.domain.ReviewAction;
import com.reviewpilot.approval.infrastructure.JpaReviewActionRepository;
import com.reviewpilot.approval.presentation.ApprovalDtos.ApprovalActionResponse;
import com.reviewpilot.approval.presentation.ApprovalDtos.SubmitApprovalRequest;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.shared.exception.ResourceConflictException;
import com.reviewpilot.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewApprovalService {

    private final JpaReviewExecutionRepository reviewExecutionRepository;
    private final JpaReviewActionRepository reviewActionRepository;
    private final ObjectMapper objectMapper;

    public ReviewApprovalService(
            JpaReviewExecutionRepository reviewExecutionRepository,
            JpaReviewActionRepository reviewActionRepository,
            ObjectMapper objectMapper
    ) {
        this.reviewExecutionRepository = reviewExecutionRepository;
        this.reviewActionRepository = reviewActionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApprovalActionResponse recordHumanApproval(UUID reviewExecutionId, SubmitApprovalRequest request) {
        ReviewExecution execution = reviewExecutionRepository.findById(reviewExecutionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review execution with ID '" + reviewExecutionId + "' was not found."
                ));

        ReviewExecutionStatus status = execution.getStatus();
        if (status != ReviewExecutionStatus.COMPLETED && status != ReviewExecutionStatus.PARTIAL) {
            throw new ResourceConflictException(
                    "Review execution '" + reviewExecutionId + "' is in status " + status
                            + " and cannot receive an approval decision yet."
            );
        }

        String trimmedComment = (request.comment() == null || request.comment().isBlank())
                ? null
                : request.comment().trim();

        String detailsJson = serializeDetails(trimmedComment);
        ReviewAction action = ReviewAction.createHumanDecision(
                reviewExecutionId,
                request.decision(),
                request.actor(),
                detailsJson,
                Instant.now()
        );

        ReviewAction saved = reviewActionRepository.save(action);
        return ApprovalActionResponse.from(saved, trimmedComment);
    }

    private String serializeDetails(String comment) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("publishedToGitHub", false);
        if (comment != null) {
            details.put("comment", comment);
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize review action details.", ex);
        }
    }
}

