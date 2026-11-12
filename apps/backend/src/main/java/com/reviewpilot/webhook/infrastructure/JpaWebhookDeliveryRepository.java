package com.reviewpilot.webhook.infrastructure;

import com.reviewpilot.webhook.domain.WebhookDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    boolean existsByDeliveryId(String deliveryId);

    Optional<WebhookDelivery> findByDeliveryId(String deliveryId);
}

