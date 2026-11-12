package com.reviewpilot.review.infrastructure;

import com.reviewpilot.review.domain.PullRequestRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPullRequestRecordRepository extends JpaRepository<PullRequestRecord, UUID> {

    Optional<PullRequestRecord> findByRepositoryIdAndNumber(UUID repositoryId, Integer number);
}

