package io.jenkins.plugins.git.forensics.blame;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link BlamerInput}.
 *
 * @author Ullrich Hafner
 */
class BlamerInputTest {
    private static final String RELATIVE_PATH = "with/file.txt";
    private static final String WORKSPACE = "/absolute/path/to/workspace/";
    private static final String ABSOLUTE_PATH = WORKSPACE + RELATIVE_PATH;
    private static final String WINDOWS_WORKSPACE = "C:\\absolute\\path\\to\\workspace\\";
    private static final String WINDOWS_RELATIVE_PATH = "with/file.txt";
    private static final String WINDOWS_ABSOLUTE_PATH = "C:/absolute/path/to/workspace/" + WINDOWS_RELATIVE_PATH;
    private static final String ANOTHER_FILE = "another-file.txt";

    @Test
    void shouldCreateEmptyInstance() {
        BlamerInput empty = new BlamerInput();

        assertThat(empty).isEmpty();
        assertThat(empty.size()).isEqualTo(0);
    }

    @Test
    void shouldCreateSingleBlame() {
        BlamerInput blames = new BlamerInput(WORKSPACE);

        blames.addLine(ABSOLUTE_PATH, 1);

        assertThat(blames).isNotEmpty();
        assertThat(blames.size()).isEqualTo(1);
        assertThat(blames).hasFiles(RELATIVE_PATH);
        assertThat(blames.contains(RELATIVE_PATH)).isTrue();

        assertThat(blames.get(RELATIVE_PATH)).containsExactly(1);
        assertThat(blames).hasNoSkippedFiles();
    }

    @Test
    void shouldSkipBlameForFileNotInWorkspace() {
        BlamerInput blames = new BlamerInput(WORKSPACE);

        String expectedSkippedFile = "/somewhere-else/" + RELATIVE_PATH;
        blames.addLine(expectedSkippedFile, 1);

        assertThat(blames).isEmpty();
        assertThat(blames).hasSkippedFiles(expectedSkippedFile);
    }

    @Test
    void shouldConvertWindowsPathToUnix() {
        BlamerInput blames = new BlamerInput(WINDOWS_WORKSPACE);

        blames.addLine(WINDOWS_ABSOLUTE_PATH, 1);

        assertThat(blames).isNotEmpty();
        assertThat(blames.size()).isEqualTo(1);
        assertThat(blames).hasFiles(RELATIVE_PATH);
        assertThat(blames.contains(RELATIVE_PATH)).isTrue();

        assertThat(blames.get(RELATIVE_PATH)).containsExactly(1);
    }

    @Test
    void shouldAddAdditionalLinesToRequest() {
        BlamerInput blames = new BlamerInput(WORKSPACE);

        blames.addLine(ABSOLUTE_PATH, 1);
        blames.addLine(ABSOLUTE_PATH, 2);

        assertThat(blames.size()).isEqualTo(1);
        assertThat(blames).hasFiles(RELATIVE_PATH);

        assertThat(blames.get(RELATIVE_PATH)).containsExactly(1, 2);
    }

    @Test
    void shouldCreateTwoDifferentBlamerInput() {
        BlamerInput blames = new BlamerInput(WORKSPACE);

        blames.addLine(ABSOLUTE_PATH, 1);
        blames.addLine(WORKSPACE + ANOTHER_FILE, 2);

        assertThat(blames.size()).isEqualTo(2);
        assertThat(blames).hasFiles(RELATIVE_PATH, ANOTHER_FILE);

        assertThat(blames.get(RELATIVE_PATH)).containsExactly(1);
        assertThat(blames.get(ANOTHER_FILE)).containsExactly(2);

        String wrongFile = "wrong file";
        assertThatThrownBy(() -> blames.get(wrongFile))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(wrongFile);
    }
}
