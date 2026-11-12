package com.reviewpilot.webhook.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.repository.domain.TrackedRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.review.domain.PullRequestRecord;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.review.infrastructure.JpaPullRequestRecordRepository;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.shared.exception.ForbiddenOperationException;
import com.reviewpilot.shared.exception.ResourceConflictException;
import com.reviewpilot.shared.exception.ResourceNotFoundException;
import com.reviewpilot.shared.exception.SemanticValidationException;
import com.reviewpilot.shared.exception.UpstreamServiceUnavailableException;
import com.reviewpilot.webhook.application.ReviewDispatchGateway.ReviewDispatchPayload;
import com.reviewpilot.webhook.domain.WebhookDelivery;
import com.reviewpilot.webhook.domain.WebhookProcessingStatus;
import com.reviewpilot.webhook.infrastructure.JpaWebhookDeliveryRepository;
import com.reviewpilot.webhook.presentation.WebhookDtos.GitHubWebhookResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final String PULL_REQUEST_EVENT = "pull_request";
    private static final Set<String> SUPPORTED_PR_ACTIONS = Set.of("opened", "synchronize", "reopened");
    private static final String SHA_REGEX = "^[0-9a-fA-F]{40}$";

    private final GitHubWebhookSignatureVerifier signatureVerifier;
    private final JpaWebhookDeliveryRepository webhookDeliveryRepository;
    private final JpaTrackedRepositoryRepository repositoryRepository;
    private final JpaPullRequestRecordRepository pullRequestRepository;
    private final JpaReviewExecutionRepository reviewExecutionRepository;
    private final ReviewDispatchGateway dispatchGateway;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(
            GitHubWebhookSignatureVerifier signatureVerifier,
            JpaWebhookDeliveryRepository webhookDeliveryRepository,
            JpaTrackedRepositoryRepository repositoryRepository,
            JpaPullRequestRecordRepository pullRequestRepository,
            JpaReviewExecutionRepository reviewExecutionRepository,
            ReviewDispatchGateway dispatchGateway,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.repositoryRepository = repositoryRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewExecutionRepository = reviewExecutionRepository;
        this.dispatchGateway = dispatchGateway;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    public GitHubWebhookResponse processWebhook(
            String deliveryIdHeader,
            String eventHeader,
            String signatureHeader,
            byte[] rawBody
    ) {
        signatureVerifier.verifySignature(signatureHeader, rawBody);

        if (!StringUtils.hasText(deliveryIdHeader)) {
            throw new IllegalArgumentException("Missing required X-GitHub-Delivery header.");
        }
        if (!StringUtils.hasText(eventHeader)) {
            throw new IllegalArgumentException("Missing required X-GitHub-Event header.");
        }
        if (rawBody == null || rawBody.length == 0) {
            throw new IllegalArgumentException("Webhook request body must not be empty.");
        }

        String deliveryId = deliveryIdHeader.trim();
        String eventType = eventHeader.trim();
        JsonNode root = parseJsonPayload(rawBody);

        String action = textOrDefault(root.get("action"), "none");
        String repositoryFullName = extractRepositoryFullName(root);
        Integer pullRequestNumber = extractPullRequestNumber(root);

        Optional<WebhookDelivery> existingDeliveryOpt = webhookDeliveryRepository.findByDeliveryId(deliveryId);
        if (existingDeliveryOpt.isPresent()) {
            WebhookDelivery existingDelivery = existingDeliveryOpt.get();
            if (existingDelivery.getProcessingStatus() == WebhookProcessingStatus.PROCESSED
                    || existingDelivery.getProcessingStatus() == WebhookProcessingStatus.IGNORED) {
                return buildDuplicateResponse(existingDelivery, root);
            }
        }

        if (!PULL_REQUEST_EVENT.equalsIgnoreCase(eventType) || !SUPPORTED_PR_ACTIONS.contains(action.toLowerCase())) {
            Instant now = Instant.now();
            WebhookDelivery ignoredDelivery = transactionTemplate.execute(status -> {
                WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId(deliveryId)
                        .orElseGet(() -> WebhookDelivery.createReceived(
                                deliveryId,
                                eventType,
                                action,
                                repositoryFullName,
                                pullRequestNumber,
                                now
                        ));
                delivery.markProcessed(WebhookProcessingStatus.IGNORED, now);
                return webhookDeliveryRepository.save(delivery);
            });

            return new GitHubWebhookResponse(
                    deliveryId,
                    eventType,
                    action,
                    ignoredDelivery.getProcessingStatus(),
                    null,
                    null,
                    "Webhook event/action '" + eventType + "." + action + "' is ignored by policy.",
                    ignoredDelivery.getProcessedAt()
            );
        }

        PreparedReviewDispatch prepared = transactionTemplate.execute(
                status -> preparePullRequestReview(deliveryId, eventType, action, repositoryFullName, root)
        );

        if (!prepared.shouldDispatch()) {
            return new GitHubWebhookResponse(
                    deliveryId,
                    eventType,
                    action,
                    WebhookProcessingStatus.PROCESSED,
                    prepared.reviewExecutionId(),
                    prepared.currentExecutionStatus(),
                    "Review execution for repository/PR/SHA already exists in status " + prepared.currentExecutionStatus() + ".",
                    Instant.now()
            );
        }

        return executeDispatchAndRecordState(
                deliveryId,
                eventType,
                action,
                prepared.dispatchPayload()
        );
    }

    public GitHubWebhookResponse retryPendingDispatch(UUID reviewExecutionId) {
        ReviewDispatchPayload payload = transactionTemplate.execute(status -> {
            ReviewExecution execution = reviewExecutionRepository.findById(reviewExecutionId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Review execution with ID '" + reviewExecutionId + "' was not found."
                    ));

            if (execution.getStatus() != ReviewExecutionStatus.PENDING) {
                throw new ResourceConflictException(
                        "Review execution '" + reviewExecutionId + "' is in status " + execution.getStatus()
                                + "; only PENDING executions can be re-dispatched."
                );
            }

            TrackedRepository repository = repositoryRepository.findById(execution.getRepositoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Repository with ID '" + execution.getRepositoryId() + "' was not found."
                    ));
            PullRequestRecord pullRequest = pullRequestRepository.findById(execution.getPullRequestId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Pull Request with ID '" + execution.getPullRequestId() + "' was not found."
                    ));

            return new ReviewDispatchPayload(
                    UUID.randomUUID(),
                    execution.getId(),
                    repository.getFullName(),
                    pullRequest.getNumber(),
                    execution.getHeadSha(),
                    pullRequest.getBaseRef(),
                    repository.getSonarProjectKey()
            );
        });

        try {
            dispatchGateway.dispatchReviewRequest(payload);
            Instant now = Instant.now();
            ReviewExecution updated = transactionTemplate.execute(status -> {
                ReviewExecution execution = reviewExecutionRepository.findById(reviewExecutionId).orElseThrow();
                execution.markRunning(now);
                return reviewExecutionRepository.save(execution);
            });
            return new GitHubWebhookResponse(
                    payload.eventId().toString(),
                    PULL_REQUEST_EVENT,
                    "retry_dispatch",
                    WebhookProcessingStatus.PROCESSED,
                    updated.getId(),
                    updated.getStatus(),
                    "Pending review execution successfully dispatched to n8n.",
                    now
            );
        } catch (UpstreamServiceUnavailableException ex) {
            Instant now = Instant.now();
            transactionTemplate.executeWithoutResult(status -> {
                ReviewExecution execution = reviewExecutionRepository.findById(reviewExecutionId).orElseThrow();
                execution.recordDispatchFailure(ex.getMessage(), now);
                reviewExecutionRepository.save(execution);
            });
            throw ex;
        }
    }

    private PreparedReviewDispatch preparePullRequestReview(
            String deliveryId,
            String eventType,
            String action,
            String repositoryFullName,
            JsonNode root
    ) {
        TrackedRepository repository = repositoryRepository.findByFullNameIgnoreCase(repositoryFullName)
                .filter(TrackedRepository::isActive)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Repository '" + repositoryFullName + "' is not registered or active in the ReviewPilot allowlist."
                ));

        JsonNode prNode = root.get("pull_request");
        if (prNode == null || prNode.isNull()) {
            throw new SemanticValidationException("Missing 'pull_request' object in webhook payload.");
        }

        long externalPrId = requiredLong(prNode, "id", "pull_request.id");
        int prNumber = requiredInt(prNode, "number", "pull_request.number");
        String title = requiredText(prNode, "title", "pull_request.title");
        String htmlUrl = requiredText(prNode, "html_url", "pull_request.html_url");
        String state = textOrDefault(prNode.get("state"), "OPEN").toUpperCase();
        String authorLogin = prNode.hasNonNull("user")
                ? textOrDefault(prNode.get("user").get("login"), "unknown")
                : "unknown";

        JsonNode baseNode = prNode.get("base");
        JsonNode headNode = prNode.get("head");
        if (baseNode == null || headNode == null) {
            throw new SemanticValidationException("Missing 'pull_request.base' or 'pull_request.head' in webhook payload.");
        }

        String baseRef = requiredText(baseNode, "ref", "pull_request.base.ref");
        String headRef = requiredText(headNode, "ref", "pull_request.head.ref");
        String headSha = requiredText(headNode, "sha", "pull_request.head.sha").toLowerCase();
        if (!headSha.matches(SHA_REGEX)) {
            throw new SemanticValidationException("Invalid 40-character commit SHA in 'pull_request.head.sha'.");
        }

        Instant now = Instant.now();
        WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId(deliveryId)
                .orElseGet(() -> WebhookDelivery.createReceived(
                        deliveryId,
                        eventType,
                        action,
                        repository.getFullName(),
                        prNumber,
                        now
                ));
        webhookDeliveryRepository.save(delivery);

        PullRequestRecord pullRequest = pullRequestRepository.findByRepositoryIdAndNumber(repository.getId(), prNumber)
                .map(existing -> {
                    existing.updateHead(title, baseRef, headRef, headSha, state, now);
                    return pullRequestRepository.save(existing);
                })
                .orElseGet(() -> pullRequestRepository.save(PullRequestRecord.create(
                        repository.getId(),
                        externalPrId,
                        prNumber,
                        title,
                        authorLogin,
                        htmlUrl,
                        baseRef,
                        headRef,
                        headSha,
                        state,
                        now
                )));

        Optional<ReviewExecution> existingExecutionOpt = reviewExecutionRepository
                .findByRepositoryIdAndPullRequestIdAndHeadSha(repository.getId(), pullRequest.getId(), headSha);

        if (existingExecutionOpt.isPresent()) {
            ReviewExecution existingExecution = existingExecutionOpt.get();
            if (existingExecution.getStatus() != ReviewExecutionStatus.PENDING) {
                delivery.markProcessed(WebhookProcessingStatus.PROCESSED, now);
                webhookDeliveryRepository.save(delivery);
                return new PreparedReviewDispatch(
                        false,
                        existingExecution.getId(),
                        existingExecution.getStatus(),
                        null
                );
            }

            ReviewDispatchPayload payload = new ReviewDispatchPayload(
                    delivery.getId(),
                    existingExecution.getId(),
                    repository.getFullName(),
                    pullRequest.getNumber(),
                    headSha,
                    baseRef,
                    repository.getSonarProjectKey()
            );
            return new PreparedReviewDispatch(
                    true,
                    existingExecution.getId(),
                    existingExecution.getStatus(),
                    payload
            );
        }

        ReviewExecution newExecution = reviewExecutionRepository.save(
                ReviewExecution.createPending(repository.getId(), pullRequest.getId(), headSha, now)
        );

        ReviewDispatchPayload payload = new ReviewDispatchPayload(
                delivery.getId(),
                newExecution.getId(),
                repository.getFullName(),
                pullRequest.getNumber(),
                headSha,
                baseRef,
                repository.getSonarProjectKey()
        );
        return new PreparedReviewDispatch(true, newExecution.getId(), newExecution.getStatus(), payload);
    }

    private GitHubWebhookResponse executeDispatchAndRecordState(
            String deliveryId,
            String eventType,
            String action,
            ReviewDispatchPayload payload
    ) {
        try {
            dispatchGateway.dispatchReviewRequest(payload);
            Instant now = Instant.now();
            ReviewExecution runningExecution = transactionTemplate.execute(status -> {
                WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId(deliveryId).orElseThrow();
                delivery.markProcessed(WebhookProcessingStatus.PROCESSED, now);
                webhookDeliveryRepository.save(delivery);

                ReviewExecution execution = reviewExecutionRepository.findById(payload.reviewExecutionId()).orElseThrow();
                execution.markRunning(now);
                return reviewExecutionRepository.save(execution);
            });

            return new GitHubWebhookResponse(
                    deliveryId,
                    eventType,
                    action,
                    WebhookProcessingStatus.PROCESSED,
                    runningExecution.getId(),
                    runningExecution.getStatus(),
                    "Pull Request review execution created and dispatched to n8n.",
                    now
            );
        } catch (UpstreamServiceUnavailableException ex) {
            Instant now = Instant.now();
            log.warn(
                    "n8n dispatch failed for deliveryId={}, reviewExecutionId={}: {}",
                    deliveryId,
                    payload.reviewExecutionId(),
                    ex.getMessage()
            );
            ReviewExecution pendingExecution = transactionTemplate.execute(status -> {
                WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId(deliveryId).orElseThrow();
                delivery.markProcessed(WebhookProcessingStatus.DISPATCH_FAILED, now);
                webhookDeliveryRepository.save(delivery);

                ReviewExecution execution = reviewExecutionRepository.findById(payload.reviewExecutionId()).orElseThrow();
                execution.recordDispatchFailure(ex.getMessage(), now);
                return reviewExecutionRepository.save(execution);
            });

            return new GitHubWebhookResponse(
                    deliveryId,
                    eventType,
                    action,
                    WebhookProcessingStatus.DISPATCH_FAILED,
                    pendingExecution.getId(),
                    pendingExecution.getStatus(),
                    "Webhook recorded and review execution created in PENDING state; n8n dispatch failed and can be retried.",
                    now
            );
        }
    }

    private GitHubWebhookResponse buildDuplicateResponse(WebhookDelivery existingDelivery, JsonNode root) {
        UUID existingExecutionId = null;
        ReviewExecutionStatus existingStatus = null;

        Optional<TrackedRepository> repoOpt = repositoryRepository
                .findByFullNameIgnoreCase(existingDelivery.getRepositoryFullName());
        Integer prNumber = existingDelivery.getPullRequestNumber();
        String headSha = extractHeadShaOrNull(root);

        if (repoOpt.isPresent() && prNumber != null && headSha != null) {
            Optional<PullRequestRecord> prOpt = pullRequestRepository
                    .findByRepositoryIdAndNumber(repoOpt.get().getId(), prNumber);
            if (prOpt.isPresent()) {
                Optional<ReviewExecution> execOpt = reviewExecutionRepository
                        .findByRepositoryIdAndPullRequestIdAndHeadSha(
                                repoOpt.get().getId(),
                                prOpt.get().getId(),
                                headSha.toLowerCase()
                        );
                if (execOpt.isPresent()) {
                    existingExecutionId = execOpt.get().getId();
                    existingStatus = execOpt.get().getStatus();
                }
            }
        }

        return new GitHubWebhookResponse(
                existingDelivery.getDeliveryId(),
                existingDelivery.getEventType(),
                existingDelivery.getAction(),
                existingDelivery.getProcessingStatus(),
                existingExecutionId,
                existingStatus,
                "Duplicate webhook delivery '" + existingDelivery.getDeliveryId() + "' ignored idempotently.",
                existingDelivery.getProcessedAt()
        );
    }

    private JsonNode parseJsonPayload(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Malformed JSON in GitHub webhook payload.", ex);
        }
    }

    private String extractRepositoryFullName(JsonNode root) {
        JsonNode repoNode = root.get("repository");
        if (repoNode == null || !repoNode.hasNonNull("full_name")) {
            throw new SemanticValidationException("Missing 'repository.full_name' in GitHub webhook payload.");
        }
        return repoNode.get("full_name").asText().trim();
    }

    private Integer extractPullRequestNumber(JsonNode root) {
        if (root.hasNonNull("number") && root.get("number").canConvertToInt()) {
            return root.get("number").asInt();
        }
        JsonNode prNode = root.get("pull_request");
        if (prNode != null && prNode.hasNonNull("number") && prNode.get("number").canConvertToInt()) {
            return prNode.get("number").asInt();
        }
        return null;
    }

    private String extractHeadShaOrNull(JsonNode root) {
        JsonNode prNode = root.get("pull_request");
        if (prNode != null && prNode.hasNonNull("head") && prNode.get("head").hasNonNull("sha")) {
            return prNode.get("head").get("sha").asText().trim();
        }
        return null;
    }

    private String requiredText(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            throw new SemanticValidationException("Missing required field '" + path + "' in webhook payload.");
        }
        return node.asText().trim();
    }

    private long requiredLong(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.canConvertToLong()) {
            throw new SemanticValidationException("Missing or invalid numeric field '" + path + "' in webhook payload.");
        }
        return node.asLong();
    }

    private int requiredInt(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.canConvertToInt()) {
            throw new SemanticValidationException("Missing or invalid integer field '" + path + "' in webhook payload.");
        }
        return node.asInt();
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return defaultValue;
        }
        return node.asText().trim();
    }

    private record PreparedReviewDispatch(
            boolean shouldDispatch,
            UUID reviewExecutionId,
            ReviewExecutionStatus currentExecutionStatus,
            ReviewDispatchPayload dispatchPayload
    ) {
    }
}

