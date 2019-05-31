package io.jenkins.plugins.git.forensics.blame;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link Blames}.
 *
 * @author Ullrich Hafner
 */
class BlamesTest {
    private static final String COMMIT = "commit";
    private static final String NAME = "name";
    private static final String EMAIL = "email";

    private static final String FILE_NAME = "file.txt";
    private static final String EMPTY = "-";
    private static final String ANOTHER_FILE = "other.txt";

    @Test
    void shouldCreateEmptyInstance() {
        Blames empty = new Blames();

        assertThat(empty).isEmpty();
        assertThat(empty.size()).isEqualTo(0);
        assertThat(empty).hasNoFiles();
        assertThat(empty).hasNoFileBlames();
        assertThat(empty).hasNoErrorMessages();
        assertThat(empty).hasNoInfoMessages();

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> empty.get(FILE_NAME));
    }

    @Test
    void shouldAddBlamesOfSingleFile() {
        Blames blames = new Blames();

        FileBlame fileBlame = createBlame(1, NAME, EMAIL, COMMIT);
        blames.add(fileBlame);

        assertThatBlamesContainsOneFile(blames, fileBlame);
        assertThat(blames.get(FILE_NAME)).isEqualTo(fileBlame);

        FileBlame other = createBlame(2, NAME, EMAIL, COMMIT);
        blames.add(other);

        assertThatBlamesContainsOneFile(blames, fileBlame);

        assertThat(blames.get(FILE_NAME)).hasFileName(FILE_NAME);
        assertThat(blames.get(FILE_NAME)).hasLines(1, 2);

        FileBlame duplicate = createBlame(1, EMPTY, EMPTY, EMPTY);
        blames.add(duplicate);

        assertThat(blames.size()).isEqualTo(1);
        assertThat(blames.get(FILE_NAME).getName(1)).isEqualTo(NAME);
        assertThat(blames.get(FILE_NAME).getEmail(1)).isEqualTo(EMAIL);
        assertThat(blames.get(FILE_NAME).getCommit(1)).isEqualTo(COMMIT);
    }

    @Test
    void shouldAddBlamesOfTwoFiles() {
        Blames blames = new Blames();

        FileBlame fileBlame = createBlame(FILE_NAME, 1, NAME, EMAIL, COMMIT);
        blames.add(fileBlame);
        FileBlame other = createBlame(ANOTHER_FILE, 2, NAME, EMAIL, COMMIT);
        blames.add(other);

        verifyBlamesOfTwoFiles(blames, fileBlame, other);
    }

    @Test
    void shouldMergeBlames() {
        Blames blames = new Blames();
        FileBlame fileBlame = createBlame(FILE_NAME, 1, NAME, EMAIL, COMMIT);
        blames.add(fileBlame);

        Blames otherBlames = new Blames();
        FileBlame other = createBlame(ANOTHER_FILE, 2, NAME, EMAIL, COMMIT);
        otherBlames.add(other);

        blames.addAll(otherBlames);

        verifyBlamesOfTwoFiles(blames, fileBlame, other);
    }

    @Test
    void shouldLogMessagesAndErrors() {
        Blames blames = new Blames();

        blames.logInfo("Hello %s", "Info");
        blames.logError("Hello %s", "Error");
        blames.logException(new IllegalArgumentException("Error"), "Hello %s", "Exception");

        assertThat(blames).hasInfoMessages("Hello Info");
        assertThat(blames).hasErrorMessages("Hello Error", "Hello Exception");

        for (int i = 0; i < 19; i++) {
            blames.logError("Hello %s %d", "Error", i);
        }
        blames.logSummary();
        assertThat(blames).hasErrorMessages("  ... skipped logging of 1 additional errors ...");
    }

    private void verifyBlamesOfTwoFiles(final Blames blames, final FileBlame fileBlame, final FileBlame other) {
        assertThat(blames.size()).isEqualTo(2);
        assertThat(blames).hasFiles(FILE_NAME, ANOTHER_FILE);
        assertThat(blames.getFileBlames()).hasSize(2);
        assertThat(blames).hasFileBlames(fileBlame, other);
        assertThat(blames.contains(FILE_NAME)).isTrue();
        assertThat(blames.contains(ANOTHER_FILE)).isTrue();
        assertThat(blames.get(FILE_NAME)).isEqualTo(fileBlame);
        assertThat(blames.get(ANOTHER_FILE)).isEqualTo(other);
    }

    private void assertThatBlamesContainsOneFile(final Blames blames, final FileBlame fileBlame) {
        assertThat(blames.size()).isEqualTo(1);
        assertThat(blames).hasFiles(FILE_NAME);
        assertThat(blames.getFileBlames()).hasSize(1);
        assertThat(blames).hasFileBlames(fileBlame);
        assertThat(blames.contains(FILE_NAME)).isTrue();
    }

    private FileBlame createBlame(final int lineNumber, final String name, final String email, final String commit) {
        return createBlame(FILE_NAME, lineNumber, name, email, commit);
    }

    private FileBlame createBlame(final String fileName, final int lineNumber, final String name, final String email,
            final String commit) {
        FileBlame fileBlame = new FileBlame(fileName);
        fileBlame.setName(lineNumber, name);
        fileBlame.setCommit(lineNumber, commit);
        fileBlame.setEmail(lineNumber, email);
        return fileBlame;
    }
}
