package com.reviewpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.review.domain.PullRequestRecord;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.infrastructure.JpaFindingRepository;
import com.reviewpilot.review.infrastructure.JpaPullRequestRecordRepository;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.approval.infrastructure.JpaReviewActionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewPilotIntegrationTest {

    private static final String TEST_INTERNAL_TOKEN = "test-internal-service-token-32-bytes-long-0001";
    private static final String VALID_SHA = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JpaTrackedRepositoryRepository repositoryRepository;

    @Autowired
    private JpaPullRequestRecordRepository pullRequestRepository;

    @Autowired
    private JpaReviewExecutionRepository reviewExecutionRepository;

    @Autowired
    private JpaFindingRepository findingRepository;

    @Autowired
    private JpaReviewActionRepository reviewActionRepository;

    @BeforeEach
    void cleanDatabase() {
        reviewActionRepository.deleteAll();
        findingRepository.deleteAll();
        reviewExecutionRepository.deleteAll();
        pullRequestRepository.deleteAll();
        repositoryRepository.deleteAll();
    }

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("reviewpilot-backend"))
                .andExpect(jsonPath("$.environment").value("test"));
    }

    @Test
    void shouldRegisterListAndPreventDuplicateRepositories() throws Exception {
        String payload = """
                {
                  "externalRepositoryId": 1001,
                  "owner": "acme",
                  "name": "reviewpilot-sandbox",
                  "defaultBranch": "main",
                  "sonarProjectKey": "acme_reviewpilot-sandbox"
                }
                """;

        mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("acme/reviewpilot-sandbox"))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"));

        mockMvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].fullName").value("acme/reviewpilot-sandbox"));
    }

    @Test
    void internalEndpointsShouldRequireValidServiceToken() throws Exception {
        UUID fakeId = UUID.randomUUID();
        String failurePayload = """
                {
                  "stage": "AI_AGENT_EXECUTION",
                  "errorCode": "TIMEOUT",
                  "message": "Agent timed out",
                  "retryable": true
                }
                """;

        mockMvc.perform(post("/api/internal/v1/reviews/" + fakeId + "/failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/internal/v1/reviews/" + fakeId + "/failure")
                        .header("X-Internal-Service-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldPersistAgentResultsValidateLocationsAndRecordHumanApproval() throws Exception {
        UUID repositoryId = registerTestRepository("octo", "demo-repo", 2002L);
        Instant now = Instant.now();

        PullRequestRecord pr = pullRequestRepository.save(PullRequestRecord.create(
                repositoryId,
                5001L,
                42,
                "Fix payment calculation null check",
                "dev-user",
                "https://github.com/octo/demo-repo/pull/42",
                "main",
                "feat/payment-fix",
                VALID_SHA,
                "OPEN",
                now
        ));

        ReviewExecution execution = reviewExecutionRepository.save(ReviewExecution.createPending(
                repositoryId,
                pr.getId(),
                VALID_SHA,
                now
        ));

        String mismatchedShaPayload = """
                {
                  "schemaVersion": "1.0",
                  "reviewExecutionId": "%s",
                  "repositoryFullName": "octo/demo-repo",
                  "pullRequestNumber": 42,
                  "analyzedHeadSha": "ffffffffffffffffffffffffffffffffffffffff",
                  "model": "gemini-2.5-pro",
                  "promptVersion": "1.0.0",
                  "summary": "Mismatched SHA test",
                  "findings": [],
                  "limitations": [],
                  "usage": { "inputTokens": 100, "outputTokens": 50 }
                }
                """.formatted(execution.getId());

        mockMvc.perform(post("/api/internal/v1/reviews/" + execution.getId() + "/results")
                        .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatchedShaPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("analyzedHeadSha")));

        String validResultPayload = """
                {
                  "schemaVersion": "1.0",
                  "reviewExecutionId": "%s",
                  "repositoryFullName": "octo/demo-repo",
                  "pullRequestNumber": 42,
                  "analyzedHeadSha": "%s",
                  "model": "gemini-2.5-pro",
                  "promptVersion": "1.0.0",
                  "summary": "Encontrada possivel dereferencia nula no servico de pagamento.",
                  "findings": [
                    {
                      "fingerprint": "fp-null-check-01",
                      "category": "BUG",
                      "severity": "HIGH",
                      "title": "Possivel NullPointerException ao calcular desconto",
                      "description": "O objeto discount pode ser nulo quando o cupom expira.",
                      "filePath": "src/main/java/com/octo/PaymentService.java",
                      "startLine": 88,
                      "endLine": 92,
                      "evidence": "BigDecimal value = discount.getAmount();",
                      "recommendation": "Validar se discount != null antes de invocar getAmount().",
                      "confidence": 0.920,
                      "origin": "AI",
                      "externalReference": null
                    }
                  ],
                  "limitations": [
                    "SonarQube MCP nao configurado para este repositorio."
                  ],
                  "usage": {
                    "inputTokens": 1240,
                    "outputTokens": 310
                  }
                }
                """.formatted(execution.getId(), VALID_SHA);

        mockMvc.perform(post("/api/internal/v1/reviews/" + execution.getId() + "/results")
                        .header("Authorization", "Bearer " + TEST_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validResultPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].fingerprint").value("fp-null-check-01"))
                .andExpect(jsonPath("$.findings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.limitations[0]").value("SonarQube MCP nao configurado para este repositorio."));

        String approvalPayload = """
                {
                  "decision": "APPROVED",
                  "actor": "lucaslima",
                  "comment": "Revisao validada localmente."
                }
                """;

        mockMvc.perform(post("/api/v1/reviews/" + execution.getId() + "/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.actor").value("lucaslima"));

        mockMvc.perform(get("/api/v1/reviews/" + execution.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestApprovalDecision").value("APPROVED"))
                .andExpect(jsonPath("$.findings.length()").value(1));

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRepositoriesCount").value(1))
                .andExpect(jsonPath("$.totalReviewsCount").value(1))
                .andExpect(jsonPath("$.reviewsByStatus.PARTIAL").value(1))
                .andExpect(jsonPath("$.totalFindingsCount").value(1))
                .andExpect(jsonPath("$.findingsBySeverity.HIGH").value(1))
                .andExpect(jsonPath("$.findingsByCategory.BUG").value(1))
                .andExpect(jsonPath("$.approvedReviewsCount").value(1));
    }

    @Test
    void shouldRecordControlledFailureAndRedactSecrets() throws Exception {
        UUID repositoryId = registerTestRepository("octo", "fail-repo", 3003L);
        Instant now = Instant.now();

        PullRequestRecord pr = pullRequestRepository.save(PullRequestRecord.create(
                repositoryId,
                6001L,
                7,
                "Test PR",
                "dev-user",
                "https://github.com/octo/fail-repo/pull/7",
                "main",
                "feat/test",
                VALID_SHA,
                "OPEN",
                now
        ));

        ReviewExecution execution = reviewExecutionRepository.save(ReviewExecution.createPending(
                repositoryId,
                pr.getId(),
                VALID_SHA,
                now
        ));

        String failurePayload = """
                {
                  "stage": "GITHUB_MCP_TOOL",
                  "errorCode": "MCP_UPSTREAM_ERROR",
                  "message": "Upstream failed with header Bearer ghp_1234567890abcdefghijklmnopqrstuv",
                  "retryable": false
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/internal/v1/reviews/" + execution.getId() + "/failure")
                        .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("MCP_UPSTREAM_ERROR"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("ghp_1234567890abcdefghijklmnopqrstuv");
        assertThat(responseBody).contains("[REDACTED]");
    }

    private UUID registerTestRepository(String owner, String name, long externalId) throws Exception {
        String payload = """
                {
                  "externalRepositoryId": %d,
                  "owner": "%s",
                  "name": "%s",
                  "defaultBranch": "main"
                }
                """.formatted(externalId, owner, name);

        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }
}

