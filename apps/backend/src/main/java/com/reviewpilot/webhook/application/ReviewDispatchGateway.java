package com.reviewpilot.webhook.application;

import java.util.UUID;

public interface ReviewDispatchGateway {

    void dispatchReviewRequest(ReviewDispatchPayload payload);

    record ReviewDispatchPayload(
            UUID eventId,
            UUID reviewExecutionId,
            String repositoryFullName,
            Integer pullRequestNumber,
            String expectedHeadSha,
            String baseBranch,
            String sonarProjectKey
    ) {
    }
}

