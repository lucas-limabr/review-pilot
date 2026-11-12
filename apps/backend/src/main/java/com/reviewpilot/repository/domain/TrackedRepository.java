package com.reviewpilot.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "repositories")
public class TrackedRepository {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private RepositoryProvider provider;

    @Column(name = "external_repository_id", nullable = false)
    private Long externalRepositoryId;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "full_name", nullable = false, unique = true, length = 255)
    private String fullName;

    @Column(name = "default_branch", nullable = false, length = 255)
    private String defaultBranch;

    @Column(name = "sonar_project_key", length = 255)
    private String sonarProjectKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrackedRepository() {
    }

    private TrackedRepository(
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
        this.id = Objects.requireNonNull(id, "id is required");
        this.provider = Objects.requireNonNull(provider, "provider is required");
        this.externalRepositoryId = Objects.requireNonNull(externalRepositoryId, "externalRepositoryId is required");
        this.owner = Objects.requireNonNull(owner, "owner is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.fullName = Objects.requireNonNull(fullName, "fullName is required");
        this.defaultBranch = Objects.requireNonNull(defaultBranch, "defaultBranch is required");
        this.sonarProjectKey = sonarProjectKey;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static TrackedRepository createGitHubRepository(
            Long externalRepositoryId,
            String owner,
            String name,
            String defaultBranch,
            String sonarProjectKey,
            Instant now
    ) {
        String trimmedOwner = owner.trim();
        String trimmedName = name.trim();
        String normalizedSonarKey = (sonarProjectKey == null || sonarProjectKey.isBlank())
                ? null
                : sonarProjectKey.trim();
        return new TrackedRepository(
                UUID.randomUUID(),
                RepositoryProvider.GITHUB,
                externalRepositoryId,
                trimmedOwner,
                trimmedName,
                trimmedOwner + "/" + trimmedName,
                defaultBranch.trim(),
                normalizedSonarKey,
                true,
                now,
                now
        );
    }

    public UUID getId() {
        return id;
    }

    public RepositoryProvider getProvider() {
        return provider;
    }

    public Long getExternalRepositoryId() {
        return externalRepositoryId;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getSonarProjectKey() {
        return sonarProjectKey;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

