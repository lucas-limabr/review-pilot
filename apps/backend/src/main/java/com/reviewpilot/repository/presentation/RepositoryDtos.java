package com.reviewpilot.repository.presentation;

import com.reviewpilot.repository.domain.RepositoryProvider;
import com.reviewpilot.repository.domain.TrackedRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RepositoryDtos {

    private RepositoryDtos() {
    }

    public record RegisterRepositoryRequest(
            @NotNull(message = "externalRepositoryId is required")
            @Min(value = 1, message = "externalRepositoryId must be positive")
            Long externalRepositoryId,

            @NotBlank(message = "owner is required")
            @Size(max = 255, message = "owner must not exceed 255 characters")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "owner contains invalid characters")
            String owner,

            @NotBlank(message = "name is required")
            @Size(max = 255, message = "name must not exceed 255 characters")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "name contains invalid characters")
            String name,

            @NotBlank(message = "defaultBranch is required")
            @Size(max = 255, message = "defaultBranch must not exceed 255 characters")
            String defaultBranch,

            @Size(max = 255, message = "sonarProjectKey must not exceed 255 characters")
            String sonarProjectKey
    ) {
    }

    public record RepositoryResponse(
            UUID id,
            RepositoryProvider provider,
            Long externalRepositoryId,
            String owner,
            String name,
            String fullName,
            String defaultBranch,
            String sonarProjectKey,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RepositoryResponse from(TrackedRepository entity) {
            return new RepositoryResponse(
                    entity.getId(),
                    entity.getProvider(),
                    entity.getExternalRepositoryId(),
                    entity.getOwner(),
                    entity.getName(),
                    entity.getFullName(),
                    entity.getDefaultBranch(),
                    entity.getSonarProjectKey(),
                    entity.isActive(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}

