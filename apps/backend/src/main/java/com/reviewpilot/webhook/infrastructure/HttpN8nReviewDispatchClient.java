package com.reviewpilot.webhook.infrastructure;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import com.reviewpilot.shared.exception.UpstreamServiceUnavailableException;
import com.reviewpilot.shared.security.InternalServiceAuthFilter;
import com.reviewpilot.webhook.application.ReviewDispatchGateway;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpN8nReviewDispatchClient implements ReviewDispatchGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpN8nReviewDispatchClient.class);
    private static final int MAX_ATTEMPTS = 2;

    private final ReviewPilotProperties properties;
    private final RestClient restClient;

    public HttpN8nReviewDispatchClient(ReviewPilotProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void dispatchReviewRequest(ReviewDispatchPayload payload) {
        String webhookUrl = properties.getN8n().getInternalWebhookUrl();
        String serviceToken = properties.getSecurity().getInternalServiceToken();

        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(InternalServiceAuthFilter.INTERNAL_TOKEN_HEADER, serviceToken)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();

                log.info(
                        "Successfully dispatched reviewExecutionId={} (eventId={}) to n8n on attempt {}",
                        payload.reviewExecutionId(),
                        payload.eventId(),
                        attempt
                );
                return;
            } catch (RestClientResponseException ex) {
                lastException = ex;
                HttpStatusCode status = ex.getStatusCode();
                if (status.is4xxClientError()) {
                    log.warn(
                            "Permanent 4xx rejection from n8n webhook for reviewExecutionId={} (status={})",
                            payload.reviewExecutionId(),
                            status.value()
                    );
                    throw new UpstreamServiceUnavailableException(
                            "n8n internal webhook rejected dispatch with HTTP status " + status.value() + ".",
                            ex
                    );
                }
                log.warn(
                        "Transient 5xx error dispatching reviewExecutionId={} to n8n on attempt {}/{} (status={})",
                        payload.reviewExecutionId(),
                        attempt,
                        MAX_ATTEMPTS,
                        status.value()
                );
            } catch (RestClientException ex) {
                lastException = ex;
                log.warn(
                        "Network error dispatching reviewExecutionId={} to n8n on attempt {}/{}",
                        payload.reviewExecutionId(),
                        attempt,
                        MAX_ATTEMPTS
                );
            }
        }

        throw new UpstreamServiceUnavailableException(
                "Failed to dispatch review execution '" + payload.reviewExecutionId() + "' to n8n after " + MAX_ATTEMPTS + " attempts.",
                lastException
        );
    }
}

