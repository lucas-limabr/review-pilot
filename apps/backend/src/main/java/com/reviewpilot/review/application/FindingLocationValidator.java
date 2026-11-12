package com.reviewpilot.review.application;

import com.reviewpilot.shared.exception.SemanticValidationException;
import org.springframework.stereotype.Component;

@Component
public class FindingLocationValidator {

    public ValidatedLocation validateAndNormalize(String rawFilePath, Integer startLine, Integer endLine) {
        if (rawFilePath == null || rawFilePath.isBlank()) {
            if (startLine != null || endLine != null) {
                throw new SemanticValidationException(
                        "Finding startLine/endLine cannot be specified when filePath is null or blank."
                );
            }
            return new ValidatedLocation(null, null, null);
        }

        String normalizedPath = rawFilePath.trim().replace('\\', '/');
        while (normalizedPath.startsWith("./")) {
            normalizedPath = normalizedPath.substring(2);
        }

        if (normalizedPath.isEmpty()
                || normalizedPath.startsWith("/")
                || normalizedPath.matches("^[A-Za-z]:/.*")
                || normalizedPath.contains("\0")
                || containsTraversalSegment(normalizedPath)) {
            throw new SemanticValidationException("Invalid or unsafe finding filePath: '" + rawFilePath + "'.");
        }

        if (startLine == null && endLine != null) {
            throw new SemanticValidationException("Finding endLine cannot be provided without startLine.");
        }

        if (startLine != null) {
            if (startLine < 1) {
                throw new SemanticValidationException("Finding startLine must be >= 1.");
            }
            int resolvedEndLine = (endLine == null) ? startLine : endLine;
            if (resolvedEndLine < startLine) {
                throw new SemanticValidationException("Finding endLine must be >= startLine.");
            }
            return new ValidatedLocation(normalizedPath, startLine, resolvedEndLine);
        }

        return new ValidatedLocation(normalizedPath, null, null);
    }

    private boolean containsTraversalSegment(String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    public record ValidatedLocation(
            String filePath,
            Integer startLine,
            Integer endLine
    ) {
    }
}

