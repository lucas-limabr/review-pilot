package com.reviewpilot.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import org.junit.jupiter.api.Test;

class ReviewPilotPropertiesTest {

    @Test
    void shouldFailFastWhenInternalServiceTokenUsesPlaceholder() {
        ReviewPilotProperties props = new ReviewPilotProperties();
        props.getSecurity().setInternalServiceToken("CHANGE_ME_LOCALLY");
        props.getSecurity().setGithubWebhookSecret("valid-webhook-secret-value");
        props.getN8n().setInternalWebhookUrl("http://n8n:5678/webhook/reviewpilot-dispatch");

        assertThatThrownBy(props::validateMandatoryProperties)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_SERVICE_TOKEN");
    }

    @Test
    void shouldFailFastWhenGithubWebhookSecretIsBlank() {
        ReviewPilotProperties props = new ReviewPilotProperties();
        props.getSecurity().setInternalServiceToken("valid-internal-token-value");
        props.getSecurity().setGithubWebhookSecret("   ");
        props.getN8n().setInternalWebhookUrl("http://n8n:5678/webhook/reviewpilot-dispatch");

        assertThatThrownBy(props::validateMandatoryProperties)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_WEBHOOK_SECRET");
    }

    @Test
    void shouldPassValidationWhenAllSecretsAreConfigured() {
        ReviewPilotProperties props = new ReviewPilotProperties();
        props.getSecurity().setInternalServiceToken("valid-internal-token-value");
        props.getSecurity().setGithubWebhookSecret("valid-webhook-secret-value");
        props.getN8n().setInternalWebhookUrl("http://n8n:5678/webhook/reviewpilot-dispatch");

        assertThatCode(props::validateMandatoryProperties).doesNotThrowAnyException();
    }
}

