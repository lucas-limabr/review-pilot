package com.reviewpilot.review.infrastructure;

import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReviewExecutionRepository extends JpaRepository<ReviewExecution, UUID> {

    Page<ReviewExecution> findByRepositoryId(UUID repositoryId, Pageable pageable);

    Optional<ReviewExecution> findByRepositoryIdAndPullRequestIdAndHeadSha(
            UUID repositoryId,
            UUID pullRequestId,
            String headSha
    );

    List<ReviewExecution> findByRepositoryIdAndPullRequestIdAndStatusIn(
            UUID repositoryId,
            UUID pullRequestId,
            Collection<ReviewExecutionStatus> statuses
    );

    long countByStatus(ReviewExecutionStatus status);
}

