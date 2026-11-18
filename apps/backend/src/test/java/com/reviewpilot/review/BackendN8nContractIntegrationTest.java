package com.reviewpilot.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.approval.infrastructure.JpaReviewActionRepository;
import com.reviewpilot.repository.domain.TrackedRepository;
import com.reviewpilot.repository.infrastructure.JpaTrackedRepositoryRepository;
import com.reviewpilot.review.domain.PullRequestRecord;
import com.reviewpilot.review.domain.ReviewExecution;
import com.reviewpilot.review.infrastructure.JpaFindingRepository;
import com.reviewpilot.review.infrastructure.JpaPullRequestRecordRepository;
import com.reviewpilot.review.infrastructure.JpaReviewExecutionRepository;
import com.reviewpilot.webhook.application.ReviewDispatchGateway.ReviewDispatchPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendN8nContractIntegrationTest {

    private static final String TEST_INTERNAL_TOKEN = "test-internal-service-token-32-bytes-long-0001";
    private static final Pattern FORBIDDEN_SECRET_PATTERN =
            Pattern.compile("(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|AIza[0-9A-Za-z\\-_]{20,}|squ_[A-Za-z0-9_]+|CHANGE_ME_LOCALLY)");

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
    void dispatchPayloadAndPinnedFixtureShouldConformToReviewRequestJsonSchema() throws Exception {
        Path repoRoot = resolveRepoRoot();
        JsonNode requestSchema = objectMapper.readTree(repoRoot.resolve("contracts/review-request.schema.json").toFile());
        Path pinnedFixturePath = repoRoot.resolve("infra/n8n/fixtures/n8n-pinned-dispatch-example.json");
        assertThat(pinnedFixturePath).exists();

        String rawFixture = Files.readString(pinnedFixturePath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawFixture).find()).isFalse();

        JsonNode fixtureNode = objectMapper.readTree(rawFixture);
        ReviewDispatchPayload backendPayload = new ReviewDispatchPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lucaslima/auto-elite",
                42,
                "0123456789abcdef0123456789abcdef01234567",
                "main",
                "auto-elite"
        );
        JsonNode serializedBackendPayload = objectMapper.valueToTree(backendPayload);

        Set<String> allowedProperties = fieldNames(requestSchema.path("properties"));
        Set<String> requiredProperties = arrayValues(requestSchema.path("required"));

        for (JsonNode candidate : new JsonNode[]{fixtureNode, serializedBackendPayload}) {
            Set<String> candidateFields = fieldNames(candidate);
            assertThat(candidateFields).containsAll(requiredProperties);
            assertThat(allowedProperties).containsAll(candidateFields);
            assertThat(candidate.path("repositoryFullName").asText()).matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
            assertThat(candidate.path("pullRequestNumber").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(candidate.path("expectedHeadSha").asText()).matches("^[0-9a-fA-F]{40}$");
            assertThat(candidate.path("baseBranch").asText()).isNotBlank();
        }
    }

    @Test
    void controlledDemoScenariosFixtureShouldCoverAllFiveDefectCategoriesAndPersistCleanly() throws Exception {
        Path repoRoot = resolveRepoRoot();
        Path scenariosPath = repoRoot.resolve("infra/n8n/fixtures/demo-pr-scenarios.json");
        assertThat(scenariosPath).exists();

        String rawScenarios = Files.readString(scenariosPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawScenarios).find()).isFalse();

        JsonNode root = objectMapper.readTree(rawScenarios);
        JsonNode scenarios = root.withArray("scenarios");
        assertThat(scenarios).hasSize(5);

        TrackedRepository repository = repositoryRepository.save(TrackedRepository.createGitHubRepository(
                99001L,
                "lucaslima",
                "auto-elite",
                "main",
                "auto-elite",
                Instant.now()
        ));

        Set<String> coveredCategories = new HashSet<>();

        for (JsonNode scenario : scenarios) {
            int prNumber = scenario.path("pullRequestNumber").asInt();
            String headSha = scenario.path("headSha").asText();
            String title = scenario.path("title").asText();
            String filePath = scenario.path("filePath").asText();
            String diffPatch = scenario.path("diffPatch").asText();
            JsonNode expectedFinding = scenario.path("expectedFinding");
            String category = expectedFinding.path("category").asText();
            String severity = expectedFinding.path("severity").asText();
            int startLine = expectedFinding.path("startLine").asInt();
            int endLine = expectedFinding.path("endLine").asInt();
            String summaryMustMention = expectedFinding.path("summaryMustMention").asText();

            coveredCategories.add(category);

            Instant now = Instant.now();
            PullRequestRecord pr = pullRequestRepository.save(PullRequestRecord.create(
                    repository.getId(),
                    80000L + prNumber,
                    prNumber,
                    title,
                    "lucaslima",
                    "https://github.com/lucaslima/auto-elite/pull/" + prNumber,
                    "main",
                    "feat/scenario-" + prNumber,
                    headSha,
                    "OPEN",
                    now
            ));

            ReviewExecution execution = reviewExecutionRepository.save(ReviewExecution.createPending(
                    repository.getId(),
                    pr.getId(),
                    headSha,
                    now
            ));

            String resultPayload = """
                    {
                      "schemaVersion": "1.0",
                      "reviewExecutionId": "%s",
                      "repositoryFullName": "lucaslima/auto-elite",
                      "pullRequestNumber": %d,
                      "analyzedHeadSha": "%s",
                      "model": "models/gemini-3.5-flash-lite",
                      "promptVersion": "1.0.0",
                      "summary": "%s",
                      "findings": [
                        {
                          "fingerprint": "%s-%s-%d",
                          "category": "%s",
                          "severity": "%s",
                          "title": "%s",
                          "description": "%s",
                          "filePath": "%s",
                          "startLine": %d,
                          "endLine": %d,
                          "evidence": %s,
                          "recommendation": "Corrigir defeito apontado antes do merge.",
                          "confidence": 0.930,
                          "origin": "AI",
                          "externalReference": null
                        }
                      ],
                      "limitations": [],
                      "usage": { "inputTokens": 980, "outputTokens": 240 }
                    }
                    """.formatted(
                    execution.getId(),
                    prNumber,
                    headSha,
                    summaryMustMention,
                    category,
                    filePath,
                    startLine,
                    category,
                    severity,
                    title,
                    summaryMustMention,
                    filePath,
                    startLine,
                    endLine,
                    objectMapper.writeValueAsString(diffPatch)
            );

            mockMvc.perform(post("/api/internal/v1/reviews/" + execution.getId() + "/results")
                            .header("X-Internal-Service-Token", TEST_INTERNAL_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resultPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.findings.length()").value(1))
                    .andExpect(jsonPath("$.findings[0].category").value(category))
                    .andExpect(jsonPath("$.findings[0].severity").value(severity))
                    .andExpect(jsonPath("$.findings[0].filePath").value(filePath))
                    .andExpect(jsonPath("$.findings[0].startLine").value(startLine));
        }

        assertThat(coveredCategories).containsExactlyInAnyOrder(
                "BUG",
                "SECURITY",
                "MAINTAINABILITY",
                "TEST"
        );
    }

    private Set<String> fieldNames(JsonNode objectNode) {
        Set<String> names = new HashSet<>();
        Iterator<String> it = objectNode.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    private Set<String> arrayValues(JsonNode arrayNode) {
        Set<String> values = new HashSet<>();
        for (JsonNode item : arrayNode) {
            values.add(item.asText());
        }
        return values;
    }

    private Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (current.endsWith("backend")) {
            return current.getParent().getParent();
        }
        return current;
    }
}

