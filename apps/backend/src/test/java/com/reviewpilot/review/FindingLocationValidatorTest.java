package com.reviewpilot.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reviewpilot.review.application.FindingLocationValidator;
import com.reviewpilot.review.application.FindingLocationValidator.ValidatedLocation;
import com.reviewpilot.shared.exception.SemanticValidationException;
import org.junit.jupiter.api.Test;

class FindingLocationValidatorTest {

    private final FindingLocationValidator validator = new FindingLocationValidator();

    @Test
    void shouldAllowGeneralObservationWithoutFilePathOrLines() {
        ValidatedLocation location = validator.validateAndNormalize(null, null, null);
        assertThat(location.filePath()).isNull();
        assertThat(location.startLine()).isNull();
        assertThat(location.endLine()).isNull();
    }

    @Test
    void shouldNormalizeRelativePathAndDefaultEndLineToStartLine() {
        ValidatedLocation location = validator.validateAndNormalize("./src\\main\\App.java", 42, null);
        assertThat(location.filePath()).isEqualTo("src/main/App.java");
        assertThat(location.startLine()).isEqualTo(42);
        assertThat(location.endLine()).isEqualTo(42);
    }

    @Test
    void shouldRejectPathTraversalInFilePath() {
        assertThatThrownBy(() -> validator.validateAndNormalize("src/../../etc/passwd", 10, 12))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("Invalid or unsafe finding filePath");
    }

    @Test
    void shouldRejectAbsoluteFilePath() {
        assertThatThrownBy(() -> validator.validateAndNormalize("/etc/shadow", 1, 1))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("Invalid or unsafe finding filePath");
    }

    @Test
    void shouldRejectLinesWhenFilePathIsMissing() {
        assertThatThrownBy(() -> validator.validateAndNormalize("  ", 10, 15))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("cannot be specified when filePath is null or blank");
    }

    @Test
    void shouldRejectEndLineLessThanStartLine() {
        assertThatThrownBy(() -> validator.validateAndNormalize("src/App.java", 20, 15))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("endLine must be >= startLine");
    }
}

