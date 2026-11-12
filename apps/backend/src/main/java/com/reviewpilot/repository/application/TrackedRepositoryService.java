package com.reviewpilot.repository.application;

import com.reviewpilot.repository.domain.RepositoryProvider;
import com.reviewpilot.repository.domain.TrackedRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.repository.presentation.RepositoryDtos.RegisterRepositoryRequest;
import com.reviewpilot.repository.presentation.RepositoryDtos.RepositoryResponse;
import com.reviewpilot.shared.exception.ResourceConflictException;
import com.reviewpilot.shared.exception.ResourceNotFoundException;
import com.reviewpilot.shared.presentation.PagedResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackedRepositoryService {

    private final JpaTrackedRepositoryRepository repository;

    public TrackedRepositoryService(JpaTrackedRepositoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RepositoryResponse registerRepository(RegisterRepositoryRequest request) {
        String fullName = request.owner().trim() + "/" + request.name().trim();
        if (repository.existsByFullNameIgnoreCase(fullName)) {
            throw new ResourceConflictException("Repository '" + fullName + "' is already registered.");
        }
        if (repository.existsByProviderAndExternalRepositoryId(RepositoryProvider.GITHUB, request.externalRepositoryId())) {
            throw new ResourceConflictException(
                    "GitHub repository with external ID " + request.externalRepositoryId() + " is already registered."
            );
        }

        TrackedRepository entity = TrackedRepository.createGitHubRepository(
                request.externalRepositoryId(),
                request.owner(),
                request.name(),
                request.defaultBranch(),
                request.sonarProjectKey(),
                Instant.now()
        );

        TrackedRepository saved = repository.save(entity);
        return RepositoryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RepositoryResponse> listRepositories(Pageable pageable) {
        Page<RepositoryResponse> page = repository.findAll(pageable).map(RepositoryResponse::from);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public TrackedRepository getRequiredById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository with ID '" + id + "' was not found."));
    }
}

