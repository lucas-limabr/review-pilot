package com.reviewpilot.approval.infrastructure;

import com.reviewpilot.approval.domain.ApprovalDecision;
import com.reviewpilot.approval.domain.ReviewAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReviewActionRepository extends JpaRepository<ReviewAction, UUID> {

    List<ReviewAction> findByReviewExecutionIdOrderByCreatedAtDesc(UUID reviewExecutionId);

    Optional<ReviewAction> findFirstByReviewExecutionIdOrderByCreatedAtDesc(UUID reviewExecutionId);

    long countByDecision(ApprovalDecision decision);
}

