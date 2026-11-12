package com.reviewpilot.review.domain;

public enum ReviewExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED || this == CANCELLED;
    }
}

