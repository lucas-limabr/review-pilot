package com.reviewpilot.review.infrastructure;

import com.reviewpilot.review.domain.ToolExecution;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaToolExecutionRepository extends JpaRepository<ToolExecution, UUID> {

    List<ToolExecution> findByReviewExecutionIdOrderByCreatedAtAsc(UUID reviewExecutionId);
}

