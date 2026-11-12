package com.reviewpilot.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "review_execution_id", nullable = false, updatable = false)
    private UUID reviewExecutionId;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private FindingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private FindingSeverity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommendation", nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 32)
    private FindingOrigin origin;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Finding() {
    }

    public static Finding create(
            UUID reviewExecutionId,
            String fingerprint,
            FindingCategory category,
            FindingSeverity severity,
            String title,
            String description,
            String filePath,
            Integer startLine,
            Integer endLine,
            String evidence,
            String recommendation,
            BigDecimal confidence,
            FindingOrigin origin,
            String externalReference,
            Instant now
    ) {
        Finding finding = new Finding();
        finding.id = UUID.randomUUID();
        finding.reviewExecutionId = Objects.requireNonNull(reviewExecutionId, "reviewExecutionId is required");
        finding.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint is required");
        finding.category = Objects.requireNonNull(category, "category is required");
        finding.severity = Objects.requireNonNull(severity, "severity is required");
        finding.title = Objects.requireNonNull(title, "title is required");
        finding.description = Objects.requireNonNull(description, "description is required");
        finding.filePath = filePath;
        finding.startLine = startLine;
        finding.endLine = endLine;
        finding.evidence = Objects.requireNonNull(evidence, "evidence is required");
        finding.recommendation = Objects.requireNonNull(recommendation, "recommendation is required");
        finding.confidence = confidence;
        finding.origin = Objects.requireNonNull(origin, "origin is required");
        finding.externalReference = externalReference;
        finding.createdAt = Objects.requireNonNull(now, "now is required");
        return finding;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewExecutionId() {
        return reviewExecutionId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public FindingCategory getCategory() {
        return category;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public FindingOrigin getOrigin() {
        return origin;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

