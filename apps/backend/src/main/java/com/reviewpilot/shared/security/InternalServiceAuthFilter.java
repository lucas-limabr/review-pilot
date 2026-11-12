package com.reviewpilot.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewpilot.shared.config.ReviewPilotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final ReviewPilotProperties properties;
    private final ObjectMapper objectMapper;

    public InternalServiceAuthFilter(ReviewPilotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providedToken = extractToken(request);
        if (!StringUtils.hasText(providedToken)) {
            writeProblemResponse(
                    response,
                    request,
                    HttpStatus.UNAUTHORIZED,
                    "Missing internal service authentication token."
            );
            return;
        }

        String expectedToken = properties.getSecurity().getInternalServiceToken();
        boolean valid = MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            writeProblemResponse(
                    response,
                    request,
                    HttpStatus.FORBIDDEN,
                    "Invalid internal service authentication token."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String headerToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (StringUtils.hasText(headerToken)) {
            return headerToken.trim();
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void writeProblemResponse(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String detail
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://reviewpilot.local/problems/internal-auth-error"));
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}

