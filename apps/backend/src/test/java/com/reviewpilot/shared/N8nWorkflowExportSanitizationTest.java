package com.reviewpilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class N8nWorkflowExportSanitizationTest {

    private static final Pattern FORBIDDEN_SECRET_PATTERN =
            Pattern.compile("(?i)(gh[pousr]_[A-Za-z0-9_]+|AIza[0-9A-Za-z\\-_]{20,}|CHANGE_ME_LOCALLY)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mainAnalyzePrWorkflowShouldBeValidSanitizedAndContainRequiredNodes() throws Exception {
        Path workflowPath = resolveRepoRoot().resolve("infra/n8n/workflows/main/reviewpilot-analyze-pr.workflow.json");
        assertThat(workflowPath).exists();

        String rawContent = Files.readString(workflowPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();

        JsonNode root = objectMapper.readTree(rawContent);
        assertThat(root.path("name").asText()).isEqualTo("ReviewPilot - Analyze Pull Request");
        assertThat(root.path("pinData").isObject()).isTrue();
        assertThat(root.path("pinData").isEmpty()).isTrue();

        Set<String> nodeNames = new HashSet<>();
        JsonNode webhookNode = null;
        JsonNode geminiNode = null;
        for (JsonNode node : root.withArray("nodes")) {
            String name = node.path("name").asText();
            nodeNames.add(name);
            if ("Receive Review Dispatch".equals(name)) {
                webhookNode = node;
            } else if ("Google Gemini Chat Model".equals(name)) {
                geminiNode = node;
            }
        }

        assertThat(nodeNames).contains(
                "Receive Review Dispatch",
                "Validate Dispatch Payload",
                "Acknowledge Dispatch Accepted",
                "Prepare Agent Context",
                "Google Gemini Chat Model",
                "Validate and Normalize Result",
                "Submit Review Result",
                "Build Failure Payload",
                "Report Execution Failure",
                "Reject Invalid Dispatch Payload"
        );

        assertThat(webhookNode).isNotNull();
        assertThat(webhookNode.path("parameters").path("path").asText()).isEqualTo("reviewpilot-dispatch");
        assertThat(webhookNode.path("parameters").path("authentication").asText()).isEqualTo("headerAuth");
        assertThat(webhookNode.path("credentials").path("httpHeaderAuth").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_INTERNAL_SERVICE_AUTH");

        assertThat(geminiNode).isNotNull();
        assertThat(geminiNode.path("type").asText()).isEqualTo("@n8n/n8n-nodes-langchain.lmChatGoogleGemini");
        assertThat(geminiNode.path("parameters").path("modelName").asText()).isEqualTo("models/gemini-3.5-flash-lite");
        assertThat(geminiNode.path("parameters").path("options").path("temperature").asDouble()).isEqualTo(0.1);
        assertThat(geminiNode.path("parameters").path("options").path("maxOutputTokens").asInt()).isEqualTo(4096);
        assertThat(geminiNode.path("credentials").path("googlePalmApi").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_GOOGLE_GEMINI");
    }

    @Test
    void geminiDiagnosticWorkflowShouldBeValidSanitizedAndConnectGeminiChatModel() throws Exception {
        Path workflowPath = resolveRepoRoot()
                .resolve("infra/n8n/workflows/main/reviewpilot-gemini-diagnostic.workflow.json");
        assertThat(workflowPath).exists();

        String rawContent = Files.readString(workflowPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();

        JsonNode root = objectMapper.readTree(rawContent);
        assertThat(root.path("name").asText()).isEqualTo("ReviewPilot - Gemini Diagnostic (Phase 5)");
        assertThat(root.path("pinData").isObject()).isTrue();
        assertThat(root.path("pinData").isEmpty()).isTrue();

        Set<String> nodeNames = new HashSet<>();
        JsonNode geminiNode = null;
        for (JsonNode node : root.withArray("nodes")) {
            String name = node.path("name").asText();
            nodeNames.add(name);
            if ("Google Gemini Chat Model".equals(name)) {
                geminiNode = node;
            }
        }

        assertThat(nodeNames).contains(
                "When clicking 'Test workflow'",
                "Prepare Fictional Code Snippet",
                "Gemini Diagnostic Review",
                "Google Gemini Chat Model",
                "Format Diagnostic Success",
                "Classify Gemini API Failure"
        );

        assertThat(geminiNode).isNotNull();
        assertThat(geminiNode.path("type").asText()).isEqualTo("@n8n/n8n-nodes-langchain.lmChatGoogleGemini");
        assertThat(geminiNode.path("parameters").path("modelName").asText()).isEqualTo("models/gemini-3.5-flash-lite");
        assertThat(geminiNode.path("credentials").path("googlePalmApi").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_GOOGLE_GEMINI");

        JsonNode languageModelConn = root.path("connections")
                .path("Google Gemini Chat Model")
                .path("ai_languageModel")
                .path(0)
                .path(0);
        assertThat(languageModelConn.path("node").asText()).isEqualTo("Gemini Diagnostic Review");
        assertThat(languageModelConn.path("type").asText()).isEqualTo("ai_languageModel");
    }

    @Test
    void globalErrorHandlerWorkflowShouldBeValidAndSanitized() throws Exception {
        Path workflowPath = resolveRepoRoot().resolve("infra/n8n/workflows/main/reviewpilot-error-handler.workflow.json");
        assertThat(workflowPath).exists();

        String rawContent = Files.readString(workflowPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();

        JsonNode root = objectMapper.readTree(rawContent);
        assertThat(root.path("name").asText()).isEqualTo("ReviewPilot - Global Error Handler");
        assertThat(root.path("pinData").isObject()).isTrue();
        assertThat(root.path("pinData").isEmpty()).isTrue();
    }

    @Test
    void backendSourceAndEnvTemplateShouldNotHardcodeGeminiModelOrApiKey() throws Exception {
        Path repoRoot = resolveRepoRoot();
        String envExample = Files.readString(repoRoot.resolve(".env.example"));
        assertThat(envExample).doesNotContainIgnoringCase("GEMINI_API_KEY");
        assertThat(envExample).doesNotContainIgnoringCase("gemini-2.5-flash");

        Path mainSrc = repoRoot.resolve("apps/backend/src/main");
        try (Stream<Path> paths = Files.walk(mainSrc)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file);
                assertThat(content)
                        .as("File %s must not hardcode Gemini model ID or API key", file.getFileName())
                        .doesNotContain("models/gemini-3.5-flash-lite")
                        .doesNotContain("GEMINI_API_KEY");
            }
        }
    }

    private Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (current.endsWith("backend")) {
            return current.getParent().getParent();
        }
        return current;
    }
}
