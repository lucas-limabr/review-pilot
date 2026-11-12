package com.reviewpilot.approval.presentation;

import com.reviewpilot.approval.application.ReviewApprovalService;
import com.reviewpilot.approval.presentation.ApprovalDtos.ApprovalActionResponse;
import com.reviewpilot.approval.presentation.ApprovalDtos.SubmitApprovalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "Approvals", description = "Human approval and rejection decisions for completed reviews")
public class ReviewApprovalController {

    private final ReviewApprovalService approvalService;

    public ReviewApprovalController(ReviewApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{id}/approval")
    @Operation(summary = "Record a human approval or rejection decision in ReviewPilot (without publishing to GitHub)")
    public ApprovalActionResponse submitApproval(
            @PathVariable("id") UUID reviewExecutionId,
            @Valid @RequestBody SubmitApprovalRequest request
    ) {
        return approvalService.recordHumanApproval(reviewExecutionId, request);
    }
}

