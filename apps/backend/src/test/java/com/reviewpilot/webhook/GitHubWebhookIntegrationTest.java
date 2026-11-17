package com.reviewpilot.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reviewpilot.repository.domain.TrackedRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.domain.ReviewExecutionStatus;
import com.reviewpilot.review.infrastructure.JpaPullRequestRecordRepository;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.shared.exception.UpstreamServiceUnavailableException;
import com.reviewpilot.webhook.application.GitHubWebhookSignatureVerifier;
import com.reviewpilot.webhook.application.ReviewDispatchGateway;
import com.reviewpilot.webhook.application.ReviewDispatchGateway.ReviewDispatchPayload;
import com.reviewpilot.webhook.domain.WebhookDelivery;
import com.reviewpilot.webhook.domain.WebhookProcessingStatus;
import com.reviewpilot.webhook.infrastructure.JpaWebhookDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubWebhookIntegrationTest {

        private static final String TEST_INTERNAL_TOKEN = "test-internal-service-token-32-bytes-long-0001";
        private static final String HEAD_SHA = "0123456789abcdef0123456789abcdef01234567";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private GitHubWebhookSignatureVerifier signatureVerifier;

        @Autowired
        private JpaTrackedRepositoryRepository repositoryRepository;

        @Autowired
        private JpaPullRequestRecordRepository pullRequestRepository;

        @Autowired
        private JpaReviewExecutionRepository reviewExecutionRepository;

        @Autowired
        private JpaWebhookDeliveryRepository webhookDeliveryRepository;

        @MockitoBean
        private ReviewDispatchGateway dispatchGateway;

        @BeforeEach
        void setUp() {
                reset(dispatchGateway);
                webhookDeliveryRepository.deleteAll();
                reviewExecutionRepository.deleteAll();
                pullRequestRepository.deleteAll();
                repositoryRepository.deleteAll();
        }

        @Test
        void shouldRejectWebhookWithInvalidOrMissingSignature() throws Exception {
                byte[] rawPayload = samplePullRequestPayload("opened", "acme/auto-elite", 10, HEAD_SHA);

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-missing-sig")
                                .header("X-GitHub-Event", "pull_request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-invalid-sig")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256",
                                                "sha256=0000000000000000000000000000000000000000000000000000000000000000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isForbidden());

                verifyNoInteractions(dispatchGateway);
                assertThat(reviewExecutionRepository.count()).isZero();
        }

        @Test
        void shouldRejectUnregisteredRepositoryNotInAllowlist() throws Exception {
                byte[] rawPayload = samplePullRequestPayload("opened", "unknown/untrusted-repo", 10, HEAD_SHA);
                String signature = signatureVerifier.computeExpectedSignature(rawPayload);

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-unlisted-01")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.detail")
                                                .value(org.hamcrest.Matchers.containsString("allowlist")));

                verifyNoInteractions(dispatchGateway);
                assertThat(reviewExecutionRepository.count()).isZero();
        }

        @Test
        void shouldIgnoreUnsupportedPullRequestActionWithoutCreatingExecution() throws Exception {
                saveAllowlistedRepo("acme", "auto-elite", "sonar-acme-sandbox");
                byte[] rawPayload = samplePullRequestPayload("closed", "acme/auto-elite", 10, HEAD_SHA);
                String signature = signatureVerifier.computeExpectedSignature(rawPayload);

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-closed-01")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deliveryStatus").value("IGNORED"))
                                .andExpect(jsonPath("$.reviewExecutionId").isEmpty());

                verifyNoInteractions(dispatchGateway);
                assertThat(reviewExecutionRepository.count()).isZero();
                WebhookDelivery delivery = webhookDeliveryRepository.findByDeliveryId("deliv-closed-01").orElseThrow();
                assertThat(delivery.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.IGNORED);
        }

        @Test
        void shouldCreateExecutionDispatchToN8nAndHandleIdempotentRedelivery() throws Exception {
                saveAllowlistedRepo("acme", "auto-elite", "sonar-acme-sandbox");
                doNothing().when(dispatchGateway).dispatchReviewRequest(any());

                byte[] rawPayload = samplePullRequestPayload("opened", "acme/auto-elite", 15, HEAD_SHA);
                String signature = signatureVerifier.computeExpectedSignature(rawPayload);

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-valid-15")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.deliveryStatus").value("PROCESSED"))
                                .andExpect(jsonPath("$.reviewExecutionStatus").value("RUNNING"))
                                .andExpect(jsonPath("$.reviewExecutionId").isNotEmpty());

                ArgumentCaptor<ReviewDispatchPayload> captor = ArgumentCaptor.forClass(ReviewDispatchPayload.class);
                verify(dispatchGateway, times(1)).dispatchReviewRequest(captor.capture());
                ReviewDispatchPayload dispatched = captor.getValue();
                assertThat(dispatched.repositoryFullName()).isEqualTo("acme/auto-elite");
                assertThat(dispatched.pullRequestNumber()).isEqualTo(15);
                assertThat(dispatched.expectedHeadSha()).isEqualTo(HEAD_SHA);
                assertThat(dispatched.baseBranch()).isEqualTo("main");
                assertThat(dispatched.sonarProjectKey()).isEqualTo("sonar-acme-sandbox");

                // Re-deliver the exact same X-GitHub-Delivery ID: must not duplicate execution
                // or re-dispatch
                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-valid-15")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deliveryStatus").value("PROCESSED"))
                                .andExpect(jsonPath("$.reviewExecutionId")
                                                .value(dispatched.reviewExecutionId().toString()))
                                .andExpect(jsonPath("$.message").value(
                                                org.hamcrest.Matchers.containsString("Duplicate webhook delivery")));

                verify(dispatchGateway, times(1)).dispatchReviewRequest(any());
                assertThat(reviewExecutionRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldPreserveEventWhenN8nIsUnavailableAndAllowControlledRetryWithoutDuplication() throws Exception {
                saveAllowlistedRepo("acme", "auto-elite", "sonar-acme-sandbox");
                doThrow(new UpstreamServiceUnavailableException("Connection refused to n8n"))
                                .when(dispatchGateway)
                                .dispatchReviewRequest(any());

                byte[] rawPayload = samplePullRequestPayload("synchronize", "acme/auto-elite", 21, HEAD_SHA);
                String signature = signatureVerifier.computeExpectedSignature(rawPayload);

                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-n8n-down-21")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawPayload))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.deliveryStatus").value("DISPATCH_FAILED"))
                                .andExpect(jsonPath("$.reviewExecutionStatus").value("PENDING"))
                                .andExpect(jsonPath("$.reviewExecutionId").isNotEmpty());

                assertThat(reviewExecutionRepository.count()).isEqualTo(1);
                ReviewExecution savedExecution = reviewExecutionRepository.findAll().getFirst();
                assertThat(savedExecution.getStatus()).isEqualTo(ReviewExecutionStatus.PENDING);
                assertThat(savedExecution.getFailureCode()).isEqualTo("N8N_DISPATCH_FAILED");

                WebhookDelivery savedDelivery = webhookDeliveryRepository.findByDeliveryId("deliv-n8n-down-21")
                                .orElseThrow();
                assertThat(savedDelivery.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.DISPATCH_FAILED);

                // Now n8n recovers; controlled retry via internal endpoint transitions the same
                // execution to RUNNING
                reset(dispatchGateway);
                doNothing().when(dispatchGateway).dispatchReviewRequest(any());

                mockMvc.perform(post("/api/internal/v1/reviews/" + savedExecution.getId() + "/dispatch")
                                .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.reviewExecutionId").value(savedExecution.getId().toString()))
                                .andExpect(jsonPath("$.reviewExecutionStatus").value("RUNNING"));

                assertThat(reviewExecutionRepository.count()).isEqualTo(1);
                ReviewExecution retriedExecution = reviewExecutionRepository.findById(savedExecution.getId())
                                .orElseThrow();
                assertThat(retriedExecution.getStatus()).isEqualTo(ReviewExecutionStatus.RUNNING);
                assertThat(retriedExecution.getFailureCode()).isNull();
                verify(dispatchGateway, times(1)).dispatchReviewRequest(any());
        }

        @Test
        void shouldSupersedeObsoleteExecutionWhenNewerShaArrivesAndCompleteEndToEndFlow() throws Exception {
                saveAllowlistedRepo("acme", "auto-elite", "auto-elite");
                doNothing().when(dispatchGateway).dispatchReviewRequest(any());

                String sha1 = "1111111111111111111111111111111111111111";
                String sha2 = "2222222222222222222222222222222222222222";

                // 1. PR #30 opened at sha1 -> creates execution E1 in RUNNING state
                byte[] payloadSha1 = samplePullRequestPayload("opened", "acme/auto-elite", 30, sha1);
                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-pr30-sha1")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signatureVerifier.computeExpectedSignature(payloadSha1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payloadSha1))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.reviewExecutionStatus").value("RUNNING"));

                ReviewExecution execSha1 = reviewExecutionRepository.findAll().stream()
                                .filter(e -> e.getHeadSha().equals(sha1))
                                .findFirst()
                                .orElseThrow();
                assertThat(execSha1.getStatus()).isEqualTo(ReviewExecutionStatus.RUNNING);

                // 2. PR #30 receives new commit sha2 while E1 is still running -> E1 is superseded (CANCELLED), E2 is RUNNING
                byte[] payloadSha2 = samplePullRequestPayload("synchronize", "acme/auto-elite", 30, sha2);
                mockMvc.perform(post("/api/v1/webhooks/github")
                                .header("X-GitHub-Delivery", "deliv-pr30-sha2")
                                .header("X-GitHub-Event", "pull_request")
                                .header("X-Hub-Signature-256", signatureVerifier.computeExpectedSignature(payloadSha2))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payloadSha2))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.reviewExecutionStatus").value("RUNNING"));

                ReviewExecution updatedExecSha1 = reviewExecutionRepository.findById(execSha1.getId()).orElseThrow();
                assertThat(updatedExecSha1.getStatus()).isEqualTo(ReviewExecutionStatus.CANCELLED);
                assertThat(updatedExecSha1.getFailureCode()).isEqualTo("SUPERSEDED_BY_NEWER_SHA");

                ReviewExecution execSha2 = reviewExecutionRepository.findAll().stream()
                                .filter(e -> e.getHeadSha().equals(sha2))
                                .findFirst()
                                .orElseThrow();
                assertThat(execSha2.getStatus()).isEqualTo(ReviewExecutionStatus.RUNNING);

                // 3. Delayed result callback from obsolete E1 must be rejected with 409 Conflict
                String obsoleteResultPayload = """
                                {
                                  "schemaVersion": "1.0",
                                  "reviewExecutionId": "%s",
                                  "repositoryFullName": "acme/auto-elite",
                                  "pullRequestNumber": 30,
                                  "analyzedHeadSha": "%s",
                                  "model": "models/gemini-3.5-flash-lite",
                                  "promptVersion": "1.0.0",
                                  "summary": "Resultado atrasado de SHA obsoleto.",
                                  "findings": [],
                                  "limitations": [],
                                  "usage": { "inputTokens": 500, "outputTokens": 120 }
                                }
                                """.formatted(execSha1.getId(), sha1);

                mockMvc.perform(post("/api/internal/v1/reviews/" + execSha1.getId() + "/results")
                                .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(obsoleteResultPayload))
                                .andExpect(status().isConflict());

                // 4. Subsequent failure callback from E1's error branch is handled idempotently preserving CANCELLED status
                String fallbackFailurePayload = """
                                {
                                  "stage": "AI_AGENT_EXECUTION",
                                  "errorCode": "MODEL_OR_WORKFLOW_FAILURE",
                                  "message": "Callback de resultado rejeitado por execucao obsoleta.",
                                  "retryable": false
                                }
                                """;

                mockMvc.perform(post("/api/internal/v1/reviews/" + execSha1.getId() + "/failure")
                                .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(fallbackFailurePayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"))
                                .andExpect(jsonPath("$.failureCode").value("SUPERSEDED_BY_NEWER_SHA"));

                // 5. Active execution E2 completes with correlated GitHub + SonarQube findings (profile quality active)
                String validSha2ResultPayload = """
                                {
                                  "schemaVersion": "1.0",
                                  "reviewExecutionId": "%s",
                                  "repositoryFullName": "acme/auto-elite",
                                  "pullRequestNumber": 30,
                                  "analyzedHeadSha": "%s",
                                  "model": "models/gemini-3.5-flash-lite",
                                  "promptVersion": "1.0.0",
                                  "summary": "Revisao concluida com contexto GitHub MCP e SonarQube MCP ativo.",
                                  "findings": [
                                    {
                                      "fingerprint": "BUG-src/service/OrderPricing.java-54-div-zero",
                                      "category": "BUG",
                                      "severity": "HIGH",
                                      "title": "Divisao por zero quando lista de itens esta vazia",
                                      "description": "O calculo de desconto divide pelo total de itens sem validar lista vazia.",
                                      "filePath": "src/service/OrderPricing.java",
                                      "startLine": 54,
                                      "endLine": 57,
                                      "evidence": "return totalDiscount / items.size();",
                                      "recommendation": "Retornar BigDecimal.ZERO quando items.isEmpty() antes de dividir.",
                                      "confidence": 0.950,
                                      "origin": "CORRELATED",
                                      "externalReference": "java:S3518"
                                    }
                                  ],
                                  "limitations": [],
                                  "usage": { "inputTokens": 1850, "outputTokens": 410 }
                                }
                                """.formatted(execSha2.getId(), sha2);

                mockMvc.perform(post("/api/internal/v1/reviews/" + execSha2.getId() + "/results")
                                .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validSha2ResultPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.findings.length()").value(1))
                                .andExpect(jsonPath("$.findings[0].origin").value("CORRELATED"))
                                .andExpect(jsonPath("$.findings[0].externalReference").value("java:S3518"));
        }

        private void saveAllowlistedRepo(String owner, String name, String sonarProjectKey) {
                repositoryRepository.save(TrackedRepository.createGitHubRepository(
                                9001L,
                                owner,
                                name,
                                "main",
                                sonarProjectKey,
                                Instant.now()));
        }

        private byte[] samplePullRequestPayload(String action, String repoFullName, int prNumber, String headSha) {
                String json = """
                                {
                                  "action": "%s",
                                  "number": %d,
                                  "repository": {
                                    "id": 9001,
                                    "full_name": "%s"
                                  },
                                  "pull_request": {
                                    "id": 70001,
                                    "number": %d,
                                    "title": "Add input validation for checkout flow",
                                    "html_url": "https://github.com/%s/pull/%d",
                                    "state": "open",
                                    "user": {
                                      "login": "octocat"
                                    },
                                    "base": {
                                      "ref": "main"
                                    },
                                    "head": {
                                      "ref": "feat/checkout-validation",
                                      "sha": "%s"
                                    }
                                  }
                                }
                                """.formatted(action, prNumber, repoFullName, prNumber, repoFullName, prNumber,
                                headSha);
                return json.getBytes(StandardCharsets.UTF_8);
        }
}
