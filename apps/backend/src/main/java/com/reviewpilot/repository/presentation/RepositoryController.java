package com.reviewpilot.repository.presentation;

import com.reviewpilot.repository.application.TrackedRepositoryService;
import com.reviewpilot.repository.presentation.RepositoryDtos.RegisterRepositoryRequest;
import com.reviewpilot.repository.presentation.RepositoryDtos.RepositoryResponse;
import com.reviewpilot.shared.presentation.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Repositories", description = "Manage allowlisted repositories in ReviewPilot")
public class RepositoryController {

    private final TrackedRepositoryService repositoryService;

    public RepositoryController(TrackedRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping
    @Operation(summary = "List allowlisted repositories")
    public PagedResponse<RepositoryResponse> listRepositories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return repositoryService.listRepositories(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new allowlisted repository")
    public RepositoryResponse registerRepository(@Valid @RequestBody RegisterRepositoryRequest request) {
        return repositoryService.registerRepository(request);
    }
}

