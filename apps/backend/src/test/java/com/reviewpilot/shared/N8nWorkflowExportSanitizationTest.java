package com.reviewpilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
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
        for (JsonNode node : root.withArray("nodes")) {
            String name = node.path("name").asText();
            nodeNames.add(name);
            if ("Receive Review Dispatch".equals(name)) {
                webhookNode = node;
            }
        }

        assertThat(nodeNames).contains(
                "Receive Review Dispatch",
                "Validate Dispatch Payload",
                "Acknowledge Dispatch Accepted",
                "Prepare Agent Context",
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

    private Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (current.endsWith("backend")) {
            return current.getParent().getParent();
        }
        return current;
    }
}

