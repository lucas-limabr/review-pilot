package com.reviewpilot.review.infrastructure;

import com.reviewpilot.review.domain.Finding;
import com.reviewpilot.review.domain.FindingCategory;
import com.reviewpilot.review.domain.FindingSeverity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByReviewExecutionIdOrderByCreatedAtAsc(UUID reviewExecutionId);

    void deleteByReviewExecutionId(UUID reviewExecutionId);

    long countByReviewExecutionId(UUID reviewExecutionId);

    long countBySeverity(FindingSeverity severity);

    long countByCategory(FindingCategory category);
}

