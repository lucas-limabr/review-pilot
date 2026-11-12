package com.reviewpilot.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "pull_requests")
public class PullRequestRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private UUID repositoryId;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "author_login", nullable = false, length = 255)
    private String authorLogin;

    @Column(name = "html_url", nullable = false, columnDefinition = "TEXT")
    private String htmlUrl;

    @Column(name = "base_ref", nullable = false, length = 255)
    private String baseRef;

    @Column(name = "head_ref", nullable = false, length = 255)
    private String headRef;

    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PullRequestRecord() {
    }

    public static PullRequestRecord create(
            UUID repositoryId,
            Long externalId,
            Integer number,
            String title,
            String authorLogin,
            String htmlUrl,
            String baseRef,
            String headRef,
            String headSha,
            String state,
            Instant now
    ) {
        PullRequestRecord pr = new PullRequestRecord();
        pr.id = UUID.randomUUID();
        pr.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId is required");
        pr.externalId = Objects.requireNonNull(externalId, "externalId is required");
        pr.number = Objects.requireNonNull(number, "number is required");
        pr.title = Objects.requireNonNull(title, "title is required");
        pr.authorLogin = Objects.requireNonNull(authorLogin, "authorLogin is required");
        pr.htmlUrl = Objects.requireNonNull(htmlUrl, "htmlUrl is required");
        pr.baseRef = Objects.requireNonNull(baseRef, "baseRef is required");
        pr.headRef = Objects.requireNonNull(headRef, "headRef is required");
        pr.headSha = Objects.requireNonNull(headSha, "headSha is required");
        pr.state = Objects.requireNonNull(state, "state is required");
        pr.createdAt = Objects.requireNonNull(now, "now is required");
        pr.updatedAt = now;
        return pr;
    }

    public void updateHead(String title, String baseRef, String headRef, String headSha, String state, Instant now) {
        this.title = Objects.requireNonNull(title, "title is required");
        this.baseRef = Objects.requireNonNull(baseRef, "baseRef is required");
        this.headRef = Objects.requireNonNull(headRef, "headRef is required");
        this.headSha = Objects.requireNonNull(headSha, "headSha is required");
        this.state = Objects.requireNonNull(state, "state is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public Long getExternalId() {
        return externalId;
    }

    public Integer getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthorLogin() {
        return authorLogin;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public String getHeadRef() {
        return headRef;
    }

    public String getHeadSha() {
        return headSha;
    }

    public String getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

