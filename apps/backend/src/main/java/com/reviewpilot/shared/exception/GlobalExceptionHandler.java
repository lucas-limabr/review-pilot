package com.reviewpilot.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedOperationException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                "https://reviewpilot.local/problems/unauthorized",
                request
        );
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                ex.getMessage(),
                "https://reviewpilot.local/problems/forbidden",
                request
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                "https://reviewpilot.local/problems/not-found",
                request
        );
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleConflict(ResourceConflictException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.CONFLICT,
                "Resource Conflict",
                ex.getMessage(),
                "https://reviewpilot.local/problems/conflict",
                request
        );
    }

    @ExceptionHandler(SemanticValidationException.class)
    public ProblemDetail handleSemanticValidation(SemanticValidationException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Semantic Validation Failed",
                ex.getMessage(),
                "https://reviewpilot.local/problems/unprocessable-entity",
                request
        );
    }

    @ExceptionHandler(UpstreamServiceUnavailableException.class)
    public ProblemDetail handleUpstreamUnavailable(UpstreamServiceUnavailableException ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Upstream Service Unavailable",
                ex.getMessage(),
                "https://reviewpilot.local/problems/service-unavailable",
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid Request Payload",
                "Request validation failed for one or more fields.",
                "https://reviewpilot.local/problems/validation-error",
                request
        );
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "Malformed Request",
                ex.getMessage() != null ? ex.getMessage() : "The request payload or parameters could not be parsed.",
                "https://reviewpilot.local/problems/bad-request",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while handling request [{} {}]", request.getMethod(), request.getRequestURI(), ex);
        return createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected internal error occurred.",
                "https://reviewpilot.local/problems/internal-error",
                request
        );
    }

    private ProblemDetail createProblem(
            HttpStatus status,
            String title,
            String detail,
            String typeUri,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(typeUri));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
