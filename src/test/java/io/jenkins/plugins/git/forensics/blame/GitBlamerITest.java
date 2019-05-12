package io.jenkins.plugins.git.forensics.blame;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import jenkins.plugins.git.GitSampleRepoRule;

import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameCallback;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
public class GitBlamerITest {
    private static final String FILE_NAME = "source.txt";
    private static final String FOO_NAME = "Foo";
    private static final String FOO_EMAIL = "foo@jenkins.io";
    private static final String BAR_NAME = "Bar";
    private static final String BAR_EMAIL = "bar@jenkins.io";

    /** Git repository in a temporary folder. */
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    /**
     * Initializes the Git repository.
     *
     * @throws Exception
     *         if the initialization fails
     */
    @Before
    @SuppressWarnings("IllegalCatch")
    public void init() throws Exception {
        sampleRepo.init();
    }

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

    @SuppressWarnings("IllegalCatch")
    private void create2RevisionsWithDifferentAuthors() {
        try {
            sampleRepo.write(FILE_NAME, "OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");
            sampleRepo.git("add", FILE_NAME);
            sampleRepo.git("config", "user.name", FOO_NAME);
            sampleRepo.git("config", "user.email", FOO_EMAIL);
            sampleRepo.git("commit", "--message=Init");
            sampleRepo.git("rev-parse", "HEAD");

            sampleRepo.write(FILE_NAME, "OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
            sampleRepo.git("add", FILE_NAME);
            sampleRepo.git("config", "user.name", BAR_NAME);
            sampleRepo.git("config", "user.email", BAR_EMAIL);
            sampleRepo.git("commit", "--message=Change");
            sampleRepo.git("rev-parse", "HEAD");
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
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

    @SuppressWarnings("IllegalCatch")
    private ObjectId getHeadCommit() {
        try {
            return ObjectId.fromString(getHead());
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("IllegalCatch")
    private String getHead() {
        try {
            return sampleRepo.head();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Repository createRepository() {
        try {
            return new RepositoryBuilder().setWorkTree(sampleRepo.getRoot()).build();
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
