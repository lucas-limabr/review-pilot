package com.reviewpilot.review.presentation;

import com.reviewpilot.review.application.ReviewExecutionService;
import com.reviewpilot.review.presentation.ReviewDtos.DashboardSummaryResponse;
import com.reviewpilot.review.presentation.ReviewDtos.ReviewDetailResponse;
import com.reviewpilot.review.presentation.ReviewDtos.ReviewSummaryItemResponse;
import com.reviewpilot.shared.presentation.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews & Dashboard", description = "Query Pull Request reviews, findings, and dashboard metrics")
public class ReviewController {

    private final ReviewExecutionService reviewExecutionService;

    public ReviewController(ReviewExecutionService reviewExecutionService) {
        this.reviewExecutionService = reviewExecutionService;
    }

    @GetMapping("/repositories/{id}/reviews")
    @Operation(summary = "List review executions for a specific repository")
    public PagedResponse<ReviewSummaryItemResponse> listRepositoryReviews(
            @PathVariable("id") UUID repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return reviewExecutionService.listReviewsByRepository(
                repositoryId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/reviews/{id}")
    @Operation(summary = "Get full details and findings for a specific review execution")
    public ReviewDetailResponse getReviewDetails(@PathVariable("id") UUID reviewExecutionId) {
        return reviewExecutionService.getReviewDetails(reviewExecutionId);
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get aggregated dashboard metrics")
    public DashboardSummaryResponse getDashboardSummary() {
        return reviewExecutionService.getDashboardSummary();
    }
}

