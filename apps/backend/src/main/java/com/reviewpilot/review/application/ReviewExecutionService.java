package com.reviewpilot.review.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.approval.domain.ApprovalDecision;
import com.reviewpilot.approval.domain.ReviewAction;
import com.reviewpilot.approval.infrastructure.JpaReviewActionRepository;
import com.reviewpilot.repository.domain.TrackedRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.review.application.FindingLocationValidator.ValidatedLocation;
import com.reviewpilot.review.domain.Finding;
import com.reviewpilot.review.domain.FindingCategory;
import com.reviewpilot.review.domain.FindingSeverity;
import com.reviewpilot.review.domain.PullRequestRecord;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.review.infrastructure.JpaFindingRepository;
import com.reviewpilot.review.infrastructure.JpaPullRequestRecordRepository;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.review.presentation.ReviewDtos.AgentFindingPayload;
import com.reviewpilot.review.presentation.ReviewDtos.AgentReviewFailurePayload;
import com.reviewpilot.review.presentation.ReviewDtos.AgentReviewResultPayload;
import com.reviewpilot.review.presentation.ReviewDtos.DashboardSummaryResponse;
import com.reviewpilot.review.presentation.ReviewDtos.FindingResponse;
import com.reviewpilot.review.presentation.ReviewDtos.ReviewDetailResponse;
import com.reviewpilot.review.presentation.ReviewDtos.ReviewSummaryItemResponse;
import com.reviewpilot.shared.exception.ResourceConflictException;
import com.reviewpilot.shared.exception.ResourceNotFoundException;
import com.reviewpilot.shared.exception.SemanticValidationException;
import com.reviewpilot.shared.presentation.PagedResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewExecutionService {

    private static final Pattern SECRET_REDACTION_PATTERN =
            Pattern.compile("(?i)(bearer\\s+[A-Za-z0-9._\\-]+|gh[pousr]_[A-Za-z0-9_]+|AIza[0-9A-Za-z\\-_]{20,})");

    private final JpaReviewExecutionRepository reviewExecutionRepository;
    private final JpaPullRequestRecordRepository pullRequestRepository;
    private final JpaFindingRepository findingRepository;
    private final JpaTrackedRepositoryRepository trackedRepositoryRepository;
    private final JpaReviewActionRepository reviewActionRepository;
    private final FindingLocationValidator locationValidator;
    private final ObjectMapper objectMapper;

    public ReviewExecutionService(
            JpaReviewExecutionRepository reviewExecutionRepository,
            JpaPullRequestRecordRepository pullRequestRepository,
            JpaFindingRepository findingRepository,
            JpaTrackedRepositoryRepository trackedRepositoryRepository,
            JpaReviewActionRepository reviewActionRepository,
            FindingLocationValidator locationValidator,
            ObjectMapper objectMapper
    ) {
        this.reviewExecutionRepository = reviewExecutionRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.findingRepository = findingRepository;
        this.trackedRepositoryRepository = trackedRepositoryRepository;
        this.reviewActionRepository = reviewActionRepository;
        this.locationValidator = locationValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewSummaryItemResponse> listReviewsByRepository(UUID repositoryId, Pageable pageable) {
        TrackedRepository repository = trackedRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository with ID '" + repositoryId + "' was not found."));

        Page<ReviewSummaryItemResponse> page = reviewExecutionRepository.findByRepositoryId(repositoryId, pageable)
                .map(execution -> toSummaryItem(execution, repository.getFullName()));
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ReviewDetailResponse getReviewDetails(UUID reviewExecutionId) {
        ReviewExecution execution = getRequiredExecution(reviewExecutionId);
        TrackedRepository repository = trackedRepositoryRepository.findById(execution.getRepositoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Repository with ID '" + execution.getRepositoryId() + "' was not found."
                ));
        PullRequestRecord pullRequest = pullRequestRepository.findById(execution.getPullRequestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pull Request with ID '" + execution.getPullRequestId() + "' was not found."
                ));

        List<FindingResponse> findings = findingRepository.findByReviewExecutionIdOrderByCreatedAtAsc(reviewExecutionId)
                .stream()
                .map(FindingResponse::from)
                .toList();

        ApprovalDecision latestDecision = reviewActionRepository
                .findFirstByReviewExecutionIdOrderByCreatedAtDesc(reviewExecutionId)
                .map(ReviewAction::getDecision)
                .orElse(null);

        List<String> limitations = deserializeLimitations(execution.getLimitationsJson());
        return ReviewDetailResponse.of(
                execution,
                repository.getFullName(),
                pullRequest,
                limitations,
                latestDecision,
                findings
        );
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary() {
        long activeRepositories = trackedRepositoryRepository.countByActiveTrue();
        long totalReviews = reviewExecutionRepository.count();

        Map<ReviewExecutionStatus, Long> reviewsByStatus = new EnumMap<>(ReviewExecutionStatus.class);
        for (ReviewExecutionStatus status : ReviewExecutionStatus.values()) {
            reviewsByStatus.put(status, reviewExecutionRepository.countByStatus(status));
        }

        long totalFindings = findingRepository.count();
        Map<FindingSeverity, Long> findingsBySeverity = new EnumMap<>(FindingSeverity.class);
        for (FindingSeverity severity : FindingSeverity.values()) {
            findingsBySeverity.put(severity, findingRepository.countBySeverity(severity));
        }

        Map<FindingCategory, Long> findingsByCategory = new EnumMap<>(FindingCategory.class);
        for (FindingCategory category : FindingCategory.values()) {
            findingsByCategory.put(category, findingRepository.countByCategory(category));
        }

        long approvedCount = reviewActionRepository.countByDecision(ApprovalDecision.APPROVED);
        long rejectedCount = reviewActionRepository.countByDecision(ApprovalDecision.REJECTED);

        return new DashboardSummaryResponse(
                activeRepositories,
                totalReviews,
                reviewsByStatus,
                totalFindings,
                findingsBySeverity,
                findingsByCategory,
                approvedCount,
                rejectedCount
        );
    }

    @Transactional
    public ReviewDetailResponse recordAgentResult(UUID pathExecutionId, AgentReviewResultPayload payload) {
        if (!pathExecutionId.equals(payload.reviewExecutionId())) {
            throw new SemanticValidationException(
                    "Path reviewExecutionId '" + pathExecutionId
                            + "' does not match payload reviewExecutionId '" + payload.reviewExecutionId() + "'."
            );
        }

        ReviewExecution execution = getRequiredExecution(pathExecutionId);
        TrackedRepository repository = trackedRepositoryRepository.findById(execution.getRepositoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Repository with ID '" + execution.getRepositoryId() + "' was not found."
                ));
        PullRequestRecord pullRequest = pullRequestRepository.findById(execution.getPullRequestId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pull Request with ID '" + execution.getPullRequestId() + "' was not found."
                ));

        if (!repository.getFullName().equalsIgnoreCase(payload.repositoryFullName().trim())) {
            throw new SemanticValidationException(
                    "Payload repositoryFullName '" + payload.repositoryFullName()
                            + "' does not match execution repository '" + repository.getFullName() + "'."
            );
        }

        if (!pullRequest.getNumber().equals(payload.pullRequestNumber())) {
            throw new SemanticValidationException(
                    "Payload pullRequestNumber " + payload.pullRequestNumber()
                            + " does not match execution pullRequestNumber " + pullRequest.getNumber() + "."
            );
        }

        if (!execution.getHeadSha().equalsIgnoreCase(payload.analyzedHeadSha().trim())) {
            throw new SemanticValidationException(
                    "Payload analyzedHeadSha '" + payload.analyzedHeadSha()
                            + "' does not match execution expectedHeadSha '" + execution.getHeadSha() + "'."
            );
        }

        if (execution.getStatus() == ReviewExecutionStatus.COMPLETED
                || execution.getStatus() == ReviewExecutionStatus.PARTIAL) {
            return getReviewDetails(pathExecutionId);
        }

        if (execution.getStatus() == ReviewExecutionStatus.FAILED
                || execution.getStatus() == ReviewExecutionStatus.CANCELLED) {
            throw new ResourceConflictException(
                    "Review execution '" + pathExecutionId + "' is already in terminal status " + execution.getStatus() + "."
            );
        }

        Instant now = Instant.now();
        Set<String> seenFingerprints = new HashSet<>();
        List<Finding> findingsToPersist = new ArrayList<>();

        for (AgentFindingPayload findingDto : payload.findings()) {
            String normalizedFingerprint = findingDto.fingerprint().trim();
            if (!seenFingerprints.add(normalizedFingerprint)) {
                throw new SemanticValidationException(
                        "Duplicate finding fingerprint '" + normalizedFingerprint + "' within the same review execution."
                );
            }

            ValidatedLocation location = locationValidator.validateAndNormalize(
                    findingDto.filePath(),
                    findingDto.startLine(),
                    findingDto.endLine()
            );

            String normalizedExternalRef = (findingDto.externalReference() == null || findingDto.externalReference().isBlank())
                    ? null
                    : findingDto.externalReference().trim();

            findingsToPersist.add(Finding.create(
                    execution.getId(),
                    normalizedFingerprint,
                    findingDto.category(),
                    findingDto.severity(),
                    findingDto.title().trim(),
                    findingDto.description().trim(),
                    location.filePath(),
                    location.startLine(),
                    location.endLine(),
                    findingDto.evidence().trim(),
                    findingDto.recommendation().trim(),
                    findingDto.confidence(),
                    findingDto.origin(),
                    normalizedExternalRef,
                    now
            ));
        }

        List<String> sanitizedLimitations = payload.limitations().stream()
                .map(String::trim)
                .map(this::redactSecrets)
                .toList();

        ReviewExecutionStatus targetStatus = sanitizedLimitations.isEmpty()
                ? ReviewExecutionStatus.COMPLETED
                : ReviewExecutionStatus.PARTIAL;

        execution.markCompleted(
                targetStatus,
                payload.schemaVersion().trim(),
                payload.model().trim(),
                payload.promptVersion().trim(),
                redactSecrets(payload.summary().trim()),
                serializeLimitations(sanitizedLimitations),
                payload.usage().inputTokens(),
                payload.usage().outputTokens(),
                now
        );

        reviewExecutionRepository.save(execution);
        findingRepository.deleteByReviewExecutionId(execution.getId());
        findingRepository.saveAll(findingsToPersist);

        return getReviewDetails(pathExecutionId);
    }

    @Transactional
    public ReviewDetailResponse recordAgentFailure(UUID executionId, AgentReviewFailurePayload payload) {
        ReviewExecution execution = getRequiredExecution(executionId);

        if (execution.getStatus() == ReviewExecutionStatus.COMPLETED
                || execution.getStatus() == ReviewExecutionStatus.PARTIAL) {
            throw new ResourceConflictException(
                    "Cannot mark execution '" + executionId + "' as FAILED because it is already " + execution.getStatus() + "."
            );
        }

        String sanitizedMessage = redactSecrets(
                "[" + payload.stage().trim() + "] " + payload.message().trim()
        );
        execution.markFailed(payload.errorCode().trim(), sanitizedMessage, Instant.now());
        reviewExecutionRepository.save(execution);

        return getReviewDetails(executionId);
    }

    private ReviewExecution getRequiredExecution(UUID id) {
        return reviewExecutionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review execution with ID '" + id + "' was not found."
                ));
    }

    private ReviewSummaryItemResponse toSummaryItem(ReviewExecution execution, String repositoryFullName) {
        PullRequestRecord pr = pullRequestRepository.findById(execution.getPullRequestId()).orElse(null);
        long findingsCount = findingRepository.countByReviewExecutionId(execution.getId());
        ApprovalDecision latestDecision = reviewActionRepository
                .findFirstByReviewExecutionIdOrderByCreatedAtDesc(execution.getId())
                .map(ReviewAction::getDecision)
                .orElse(null);

        return new ReviewSummaryItemResponse(
                execution.getId(),
                execution.getRepositoryId(),
                repositoryFullName,
                execution.getPullRequestId(),
                pr != null ? pr.getNumber() : null,
                pr != null ? pr.getTitle() : null,
                execution.getHeadSha(),
                execution.getStatus(),
                execution.getModelName(),
                execution.getPromptVersion(),
                findingsCount,
                latestDecision,
                execution.getStartedAt(),
                execution.getCompletedAt(),
                execution.getCreatedAt()
        );
    }

    private String serializeLimitations(List<String> limitations) {
        try {
            return objectMapper.writeValueAsString(limitations);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize review limitations.", ex);
        }
    }

    private List<String> deserializeLimitations(String limitationsJson) {
        if (limitationsJson == null || limitationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(limitationsJson, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String redactSecrets(String rawText) {
        if (rawText == null) {
            return null;
        }
        return SECRET_REDACTION_PATTERN.matcher(rawText).replaceAll("[REDACTED]");
    }
}

