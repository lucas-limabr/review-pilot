package com.reviewpilot.shared.exception;

public class UpstreamServiceUnavailableException extends RuntimeException {

    public UpstreamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpstreamServiceUnavailableException(String message) {
        super(message);
    }
}

