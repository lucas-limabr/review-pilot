package com.reviewpilot.shared.presentation;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service health check endpoint")
public class HealthController {

    private final ReviewPilotProperties properties;

    public HealthController(ReviewPilotProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Check API health status")
    public HealthResponse getHealth() {
        return new HealthResponse(
                "UP",
                "reviewpilot-backend",
                properties.getEnvironment(),
                Instant.now()
        );
    }

    public record HealthResponse(
            String status,
            String service,
            String environment,
            Instant timestamp
    ) {
    }
}

