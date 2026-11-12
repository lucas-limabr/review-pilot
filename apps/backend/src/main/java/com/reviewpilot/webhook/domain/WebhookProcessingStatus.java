package com.reviewpilot.webhook.domain;

public enum WebhookProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    DISPATCH_FAILED,
    FAILED
}
