package com.reviewpilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class N8nWorkflowExportSanitizationTest {

    private static final Pattern FORBIDDEN_SECRET_PATTERN =
            Pattern.compile("(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|AIza[0-9A-Za-z\\-_]{20,}|CHANGE_ME_LOCALLY)");

    private static final Pattern FORBIDDEN_WRITE_TOOL_PATTERN =
            Pattern.compile("(?i)(create_pull_request|merge_pull_request|create_or_update_file|push_files|create_branch|add_issue_comment|create_pull_request_review|update_pull_request)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mainAnalyzePrWorkflowShouldBeValidSanitizedAndContainRequiredNodes() throws Exception {
        Path workflowPath = resolveRepoRoot().resolve("infra/n8n/workflows/main/reviewpilot-analyze-pr.workflow.json");
        assertThat(workflowPath).exists();

        String rawContent = Files.readString(workflowPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();
        assertThat(FORBIDDEN_WRITE_TOOL_PATTERN.matcher(rawContent).find()).isFalse();

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
                "github_pr_read_tool",
                "github_get_file_contents_tool",
                "github_list_commits_tool",
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
    void githubMcpSubworkflowsAndDiagnosticShouldBeValidSanitizedAndReadOnly() throws Exception {
        Path repoRoot = resolveRepoRoot();
        Map<String, String> expectedToolSubworkflows = Map.of(
                "infra/n8n/workflows/tools/github-mcp-pull-request-read.workflow.json", "pull_request_read",
                "infra/n8n/workflows/tools/github-mcp-get-file-contents.workflow.json", "get_file_contents",
                "infra/n8n/workflows/tools/github-mcp-list-commits.workflow.json", "list_commits"
        );

        for (Map.Entry<String, String> entry : expectedToolSubworkflows.entrySet()) {
            Path subworkflowPath = repoRoot.resolve(entry.getKey());
            assertThat(subworkflowPath).exists();

            String rawContent = Files.readString(subworkflowPath);
            assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();
            assertThat(FORBIDDEN_WRITE_TOOL_PATTERN.matcher(rawContent).find()).isFalse();

            JsonNode root = objectMapper.readTree(rawContent);
            assertThat(root.path("pinData").isObject()).isTrue();
            assertThat(root.path("pinData").isEmpty()).isTrue();

            JsonNode mcpClientNode = null;
            JsonNode triggerNode = null;
            for (JsonNode node : root.withArray("nodes")) {
                String type = node.path("type").asText();
                if ("@n8n/n8n-nodes-langchain.mcpClient".equals(type)) {
                    mcpClientNode = node;
                } else if ("n8n-nodes-base.executeWorkflowTrigger".equals(type)) {
                    triggerNode = node;
                }
            }

            assertThat(triggerNode).isNotNull();
            assertThat(mcpClientNode).isNotNull();
            assertThat(mcpClientNode.path("parameters").path("serverTransport").asText())
                    .isEqualTo("httpStreamable");
            assertThat(mcpClientNode.path("parameters").path("endpointUrl").asText())
                    .isEqualTo("https://api.githubcopilot.com/mcp/");
            assertThat(mcpClientNode.path("parameters").path("authentication").asText())
                    .isEqualTo("multipleHeadersAuth");
            assertThat(mcpClientNode.path("parameters").path("tool").path("value").asText())
                    .isEqualTo(entry.getValue());
            assertThat(mcpClientNode.path("credentials").path("httpMultipleHeadersAuth").path("id").asText())
                    .isEqualTo("CREDENTIAL_ID_GITHUB_MCP_HEADERS");
        }

        Path diagnosticPath = repoRoot.resolve("infra/n8n/workflows/main/reviewpilot-github-mcp-diagnostic.workflow.json");
        assertThat(diagnosticPath).exists();
        String diagnosticContent = Files.readString(diagnosticPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(diagnosticContent).find()).isFalse();
        JsonNode diagnosticRoot = objectMapper.readTree(diagnosticContent);
        assertThat(diagnosticRoot.path("name").asText()).isEqualTo("ReviewPilot - GitHub MCP Diagnostic (Phase 6)");
        assertThat(diagnosticRoot.path("pinData").isEmpty()).isTrue();
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
        assertThat(envExample).doesNotContainIgnoringCase("gemini-3.5-flash-lite");

        List<String> forbiddenInJava = List.of("models/gemini-3.5-flash-lite", "GEMINI_API_KEY");
        Path mainSrc = repoRoot.resolve("apps/backend/src/main");
        try (Stream<Path> paths = Files.walk(mainSrc)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file);
                for (String token : forbiddenInJava) {
                    assertThat(content)
                            .as("File %s must not hardcode %s", file.getFileName(), token)
                            .doesNotContain(token);
                }
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
