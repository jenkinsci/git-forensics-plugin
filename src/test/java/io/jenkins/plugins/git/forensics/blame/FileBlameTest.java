package io.jenkins.plugins.git.forensics.blame;

import org.junit.jupiter.api.Test;

import static io.jenkins.plugins.git.forensics.blame.FileBlame.*;
import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link FileBlame}.
 *
 * @author Ullrich Hafner
 */
class FileBlameTest {
    private static final String COMMIT = "commit";
    private static final String NAME = "name";
    private static final String EMAIL = "email";

    @Test
    void shouldCreateInstance() {
        FileBlame request = new FileBlame("file");

        assertThat(request).hasNoLines();
        assertThat(request.getFileName()).isEqualTo("file");
        assertThat(request).isEqualTo(new FileBlame("file"));

        setDetails(request, 15);
        verifyDetails(request, 15);

        FileBlame other = new FileBlame("file");
        setDetails(other, 15);

        assertThat(request).isEqualTo(other);

        request.addLineNumber(25);
        assertThat(request).hasLines(15, 25);

        setDetails(request, 25);
        verifyDetails(request, 25);
    }

    private void verifyDetails(final FileBlame request, final int line) {
        assertThat(request.getCommit(line)).isEqualTo(COMMIT);
        assertThat(request.getName(line)).isEqualTo(NAME);
        assertThat(request.getEmail(line)).isEqualTo(EMAIL);
    }

    private void setDetails(final FileBlame request, final int lineNumber) {
        request.setCommit(lineNumber, COMMIT);
        request.setName(lineNumber, NAME);
        request.setEmail(lineNumber, EMAIL);
    }

    @Test
    void shouldMergeRequest() {
        FileBlame request = new FileBlame("file");
        setDetails(request, 1);
        assertThat(request).hasLines(1);

        FileBlame sameLine = new FileBlame("file");
        request.merge(sameLine);
        assertThat(request).hasLines(1);
        verifyDetails(request, 1);

        FileBlame otherLine = new FileBlame("file");
        setDetails(otherLine, 2);

        request.merge(otherLine);
        assertThat(request.iterator()).toIterable().containsExactly(1, 2);
        assertThat(request).hasLines(1, 2);
        verifyDetails(request, 1);
        verifyDetails(request, 2);

        otherLine.setCommit(2, EMPTY);
        otherLine.setName(2, EMPTY);
        otherLine.setEmail(2, EMPTY);
        request.merge(otherLine);
        verifyDetails(request, 1);
        verifyDetails(request, 2);

        assertThatThrownBy(() -> request.merge(new FileBlame("wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrong").hasMessageContaining("file");
    }

    @Test
    void shouldReturnMeaningfulDefaults() {
        FileBlame request = new FileBlame("file");

        assertThat(request.getCommit(2)).isEqualTo(EMPTY);
        assertThat(request.getEmail(2)).isEqualTo(EMPTY);
        assertThat(request.getName(2)).isEqualTo(EMPTY);
    }
}
