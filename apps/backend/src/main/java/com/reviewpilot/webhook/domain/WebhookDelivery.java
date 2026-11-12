package com.reviewpilot.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, unique = true, length = 128)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "repository_full_name", nullable = false, length = 255)
    private String repositoryFullName;

    @Column(name = "pull_request_number")
    private Integer pullRequestNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private WebhookProcessingStatus processingStatus;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookDelivery() {
    }

    public static WebhookDelivery createReceived(
            String deliveryId,
            String eventType,
            String action,
            String repositoryFullName,
            Integer pullRequestNumber,
            Instant now
    ) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.deliveryId = Objects.requireNonNull(deliveryId, "deliveryId is required");
        delivery.eventType = Objects.requireNonNull(eventType, "eventType is required");
        delivery.action = Objects.requireNonNull(action, "action is required");
        delivery.repositoryFullName = Objects.requireNonNull(repositoryFullName, "repositoryFullName is required");
        delivery.pullRequestNumber = pullRequestNumber;
        delivery.processingStatus = WebhookProcessingStatus.RECEIVED;
        delivery.receivedAt = Objects.requireNonNull(now, "now is required");
        return delivery;
    }

    public void markProcessed(WebhookProcessingStatus status, Instant now) {
        this.processingStatus = Objects.requireNonNull(status, "status is required");
        this.processedAt = Objects.requireNonNull(now, "now is required");
    }

    public UUID getId() {
        return id;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAction() {
        return action;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public Integer getPullRequestNumber() {
        return pullRequestNumber;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

