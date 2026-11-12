package com.reviewpilot;

import com.reviewpilot.shared.config.ReviewPilotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ReviewPilotProperties.class)
public class ReviewPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewPilotApplication.class, args);
    }
}

