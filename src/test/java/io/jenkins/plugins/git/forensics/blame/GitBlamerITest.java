package io.jenkins.plugins.git.forensics.blame;

import org.junit.Test;

import io.jenkins.plugins.git.forensics.GitITest;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameCallback;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
public class GitBlamerITest extends GitITest {
    /**
     * Verifies that the blames are empty if there are no requests defined.
     *
     * @throws InterruptedException
     *         if the blaming has been canceled
     */
    @Test
    public void shouldCreateEmptyBlamesIfRequestIsEmpty() throws InterruptedException {
        BlameCallback blameCallback = new BlameCallback(new Blames(), getHeadCommit());

        Blames blames = blameCallback.invoke(createRepository(), null);

        assertThat(blames).isEmpty();
    }

    /**
     * Verifies that the blames are empty if there are no requests defined.
     *
     * @throws InterruptedException
     *         if the blaming has been canceled
     */
    @Test
    public void shouldCreateBlamesIfRequestIsExistingFile() throws InterruptedException {
        create2RevisionsWithDifferentAuthors();

        Blames blames = new Blames();
        blames.addLine(FILE_NAME, 2);
        blames.addLine(FILE_NAME, 3);
        blames.addLine(FILE_NAME, 4);
        blames.addLine(FILE_NAME, 5);

        BlameCallback blameCallback = new BlameCallback(blames, getHeadCommit());

        assertThat(blameCallback.invoke(createRepository(), null)).isSameAs(blames);

        assertThat(blames).isNotEmpty();
        assertThat(blames).hasFiles(FILE_NAME);
        assertThat(blames).hasNoErrorMessages();
        assertThat(blames).hasInfoMessages("-> blamed authors of issues in 1 files");

        BlameRequest request = blames.get(FILE_NAME);
        assertThat(request).hasFileName(FILE_NAME);

        assertThatBlameIsEmpty(request, 1);
        assertThatBlameIs(request, 2);
        assertThatBlameIsHeadWith(request, 3);
        assertThatBlameIsHeadWith(request, 4);
        assertThatBlameIs(request, 5);
        assertThatBlameIsEmpty(request, 6);
    }

    private void create2RevisionsWithDifferentAuthors() {
        writeFileAsAuthorFoo("OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");

        writeFileAsAuthorBar("OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
    }

    private void assertThatBlameIsHeadWith(final BlameRequest request, final int line) {
        assertThat(request.getName(line)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(line)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(line)).isEqualTo(getHead());
    }

    private void assertThatBlameIs(final BlameRequest request, final int line) {
        assertThat(request.getName(line)).isEqualTo(FOO_NAME);
        assertThat(request.getEmail(line)).isEqualTo(FOO_EMAIL);
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }

    private void assertThatBlameIsEmpty(final BlameRequest request, final int line) {
        assertThat(request.getName(line)).isEqualTo("-");
        assertThat(request.getEmail(line)).isEqualTo("-");
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }
}
