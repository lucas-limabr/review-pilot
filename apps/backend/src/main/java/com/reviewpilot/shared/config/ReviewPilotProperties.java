package com.reviewpilot.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "reviewpilot")
public class ReviewPilotProperties {

    private static final String PLACEHOLDER = "CHANGE_ME_LOCALLY";

    private String environment = "local";
    private Security security = new Security();
    private N8n n8n = new N8n();

    @PostConstruct
    public void validateMandatoryProperties() {
        validateSecret("reviewpilot.security.internal-service-token (INTERNAL_SERVICE_TOKEN)", security.getInternalServiceToken());
        validateSecret("reviewpilot.security.github-webhook-secret (GITHUB_WEBHOOK_SECRET)", security.getGithubWebhookSecret());
        if (!StringUtils.hasText(n8n.getInternalWebhookUrl())) {
            throw new IllegalStateException("Mandatory property reviewpilot.n8n.internal-webhook-url (N8N_INTERNAL_WEBHOOK_URL) must be configured.");
        }
    }

    private void validateSecret(String propertyName, String value) {
        if (!StringUtils.hasText(value) || PLACEHOLDER.equalsIgnoreCase(value.trim())) {
            throw new IllegalStateException(
                    "Mandatory secret " + propertyName + " is missing or still uses placeholder CHANGE_ME_LOCALLY."
            );
        }
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public N8n getN8n() {
        return n8n;
    }

    public void setN8n(N8n n8n) {
        this.n8n = n8n;
    }

    public static class Security {
        private String internalServiceToken;
        private String githubWebhookSecret;

        public String getInternalServiceToken() {
            return internalServiceToken;
        }

        public void setInternalServiceToken(String internalServiceToken) {
            this.internalServiceToken = internalServiceToken;
        }

        public String getGithubWebhookSecret() {
            return githubWebhookSecret;
        }

        public void setGithubWebhookSecret(String githubWebhookSecret) {
            this.githubWebhookSecret = githubWebhookSecret;
        }
    }

    public static class N8n {
        private String internalWebhookUrl;

        public String getInternalWebhookUrl() {
            return internalWebhookUrl;
        }

        public void setInternalWebhookUrl(String internalWebhookUrl) {
            this.internalWebhookUrl = internalWebhookUrl;
        }
    }
}

