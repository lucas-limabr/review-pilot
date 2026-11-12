package com.reviewpilot.repository.infrastructure;

import com.reviewpilot.repository.domain.RepositoryProvider;
import com.reviewpilot.repository.domain.TrackedRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTrackedRepositoryRepository extends JpaRepository<TrackedRepository, UUID> {

    boolean existsByFullNameIgnoreCase(String fullName);

    boolean existsByProviderAndExternalRepositoryId(RepositoryProvider provider, Long externalRepositoryId);

    Optional<TrackedRepository> findByFullNameIgnoreCase(String fullName);

    long countByActiveTrue();
}

