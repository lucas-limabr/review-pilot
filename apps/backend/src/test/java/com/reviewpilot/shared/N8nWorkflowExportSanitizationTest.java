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
            Pattern.compile("(?i)(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+|AIza[0-9A-Za-z\\-_]{20,}|squ_[A-Za-z0-9_]+|CHANGE_ME_LOCALLY)");

    private static final Pattern FORBIDDEN_WRITE_TOOL_PATTERN =
            Pattern.compile("(?i)(create_pull_request|merge_pull_request|create_or_update_file|push_files|create_branch|add_issue_comment|create_pull_request_review|update_pull_request|change_sonar_issue_status|analyze_code_snippet)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mainAnalyzePrWorkflowShouldBeValidSanitizedAndContainReviewAgentAndReadOnlyTools() throws Exception {
        Path repoRoot = resolveRepoRoot();
        Path workflowPath = repoRoot.resolve("infra/n8n/workflows/main/reviewpilot-analyze-pr.workflow.json");
        Path promptPath = repoRoot.resolve("infra/n8n/prompts/review_agent.md");
        assertThat(workflowPath).exists();
        assertThat(promptPath).exists();

        String rawContent = Files.readString(workflowPath);
        String expectedSystemPrompt = Files.readString(promptPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();
        assertThat(FORBIDDEN_WRITE_TOOL_PATTERN.matcher(rawContent).find()).isFalse();

        JsonNode root = objectMapper.readTree(rawContent);
        assertThat(root.path("name").asText()).isEqualTo("ReviewPilot - Analyze Pull Request");
        assertThat(root.path("pinData").isObject()).isTrue();
        assertThat(root.path("pinData").isEmpty()).isTrue();

        Set<String> nodeNames = new HashSet<>();
        JsonNode webhookNode = null;
        JsonNode geminiNode = null;
        JsonNode reviewAgentNode = null;
        JsonNode verifyContractAndShaNode = null;
        JsonNode submitResultNode = null;
        JsonNode reportFailureNode = null;
        for (JsonNode node : root.withArray("nodes")) {
            String name = node.path("name").asText();
            nodeNames.add(name);
            if ("Receive Review Dispatch".equals(name)) {
                webhookNode = node;
            } else if ("Google Gemini Chat Model".equals(name)) {
                geminiNode = node;
            } else if ("Review Agent".equals(name)) {
                reviewAgentNode = node;
            } else if ("Verify Contract and Head SHA".equals(name)) {
                verifyContractAndShaNode = node;
            } else if ("Submit Review Result".equals(name)) {
                submitResultNode = node;
            } else if ("Report Execution Failure".equals(name)) {
                reportFailureNode = node;
            }
        }

        assertThat(nodeNames).contains(
                "Receive Review Dispatch",
                "Validate Dispatch Payload",
                "Acknowledge Dispatch Accepted",
                "Prepare Agent Context",
                "Review Agent",
                "Google Gemini Chat Model",
                "github_pr_read_tool",
                "github_get_file_contents_tool",
                "github_list_commits_tool",
                "sonarqube_search_issues_tool",
                "sonarqube_quality_gate_tool",
                "sonarqube_show_rule_tool",
                "Validate and Normalize Result",
                "Verify Contract and Head SHA",
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
        assertThat(geminiNode.path("retryOnFail").asBoolean(false)).isFalse();
        assertThat(geminiNode.path("credentials").path("googlePalmApi").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_GOOGLE_GEMINI");

        assertThat(reviewAgentNode).isNotNull();
        assertThat(reviewAgentNode.path("type").asText()).isEqualTo("@n8n/n8n-nodes-langchain.agent");
        assertThat(reviewAgentNode.path("retryOnFail").asBoolean(false)).isFalse();
        assertThat(reviewAgentNode.path("parameters").path("options").path("maxIterations").asInt()).isEqualTo(10);
        assertThat(reviewAgentNode.path("parameters").path("options").path("systemMessage").asText().strip())
                .isEqualTo(expectedSystemPrompt.strip());

        assertThat(verifyContractAndShaNode).isNotNull();
        assertThat(verifyContractAndShaNode.path("type").asText()).isEqualTo("n8n-nodes-base.if");

        assertThat(submitResultNode).isNotNull();
        assertThat(submitResultNode.path("retryOnFail").asBoolean()).isTrue();
        assertThat(submitResultNode.path("maxTries").asInt()).isEqualTo(2);
        assertThat(submitResultNode.path("parameters").path("options").path("timeout").asInt()).isEqualTo(15000);
        assertThat(submitResultNode.path("credentials").path("httpHeaderAuth").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_INTERNAL_SERVICE_AUTH");

        assertThat(reportFailureNode).isNotNull();
        assertThat(reportFailureNode.path("retryOnFail").asBoolean()).isTrue();
        assertThat(reportFailureNode.path("maxTries").asInt()).isEqualTo(2);
        assertThat(reportFailureNode.path("parameters").path("options").path("timeout").asInt()).isEqualTo(10000);
        assertThat(reportFailureNode.path("credentials").path("httpHeaderAuth").path("id").asText())
                .isEqualTo("CREDENTIAL_ID_INTERNAL_SERVICE_AUTH");

        assertThat(root.path("settings").path("executionTimeout").asInt()).isEqualTo(180);
        assertThat(root.path("settings").path("errorWorkflow").asText())
                .isEqualTo("ReviewPilot - Global Error Handler");

        JsonNode connections = root.path("connections");
        assertThat(connections.path("Validate and Normalize Result").path("main").path(0).path(0).path("node").asText())
                .isEqualTo("Verify Contract and Head SHA");
        assertThat(connections.path("Verify Contract and Head SHA").path("main").path(0).path(0).path("node").asText())
                .isEqualTo("Submit Review Result");
        assertThat(connections.path("Verify Contract and Head SHA").path("main").path(1).path(0).path("node").asText())
                .isEqualTo("Build Failure Payload");
        assertThat(connections.path("Google Gemini Chat Model").path("ai_languageModel").path(0).path(0).path("node").asText())
                .isEqualTo("Review Agent");

        List<String> expectedTools = List.of(
                "github_pr_read_tool",
                "github_get_file_contents_tool",
                "github_list_commits_tool",
                "sonarqube_search_issues_tool",
                "sonarqube_quality_gate_tool",
                "sonarqube_show_rule_tool"
        );
        for (String toolNodeName : expectedTools) {
            assertThat(connections.path(toolNodeName).path("ai_tool").path(0).path(0).path("node").asText())
                    .as("Tool %s must connect to Review Agent via ai_tool", toolNodeName)
                    .isEqualTo("Review Agent");
        }
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
    void sonarqubeMcpSubworkflowsAndDiagnosticShouldBeValidSanitizedReadOnlyAndResilient() throws Exception {
        Path repoRoot = resolveRepoRoot();
        Map<String, String> expectedSonarSubworkflows = Map.of(
                "infra/n8n/workflows/tools/sonarqube-mcp-search-issues.workflow.json", "search_sonar_issues_in_projects",
                "infra/n8n/workflows/tools/sonarqube-mcp-quality-gate.workflow.json", "get_project_quality_gate_status",
                "infra/n8n/workflows/tools/sonarqube-mcp-show-rule.workflow.json", "show_rule"
        );

        for (Map.Entry<String, String> entry : expectedSonarSubworkflows.entrySet()) {
            Path subworkflowPath = repoRoot.resolve(entry.getKey());
            assertThat(subworkflowPath).exists();

            String rawContent = Files.readString(subworkflowPath);
            assertThat(FORBIDDEN_SECRET_PATTERN.matcher(rawContent).find()).isFalse();
            assertThat(FORBIDDEN_WRITE_TOOL_PATTERN.matcher(rawContent).find()).isFalse();
            assertThat(rawContent).contains("UNAVAILABLE_OPTIONAL");

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
                    .isEqualTo("http://sonarqube-mcp:8080/mcp");
            assertThat(mcpClientNode.path("parameters").path("authentication").asText())
                    .isEqualTo("bearerAuth");
            assertThat(mcpClientNode.path("parameters").path("tool").path("value").asText())
                    .isEqualTo(entry.getValue());
            assertThat(mcpClientNode.path("credentials").path("httpBearerAuth").path("id").asText())
                    .isEqualTo("CREDENTIAL_ID_SONARQUBE_MCP_BEARER");
        }

        Path diagnosticPath = repoRoot.resolve("infra/n8n/workflows/main/reviewpilot-sonarqube-mcp-diagnostic.workflow.json");
        assertThat(diagnosticPath).exists();
        String diagnosticContent = Files.readString(diagnosticPath);
        assertThat(FORBIDDEN_SECRET_PATTERN.matcher(diagnosticContent).find()).isFalse();
        JsonNode diagnosticRoot = objectMapper.readTree(diagnosticContent);
        assertThat(diagnosticRoot.path("name").asText()).isEqualTo("ReviewPilot - SonarQube MCP Diagnostic (Phase 7)");
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
