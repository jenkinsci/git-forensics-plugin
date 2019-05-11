package io.jenkins.plugins.git.forensics;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import jenkins.plugins.git.GitSampleRepoRule;

import io.jenkins.plugins.git.forensics.GitBlamer.BlameCallback;

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

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Before
    public void init() throws Exception {
        sampleRepo.init();

        sampleRepo.write(FILE_NAME, "OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");
        sampleRepo.git("add", FILE_NAME);
        sampleRepo.git("config", "user.name", FOO_NAME);
        sampleRepo.git("config", "user.email", FOO_EMAIL);
        sampleRepo.git("commit", "--message=Init");
        sampleRepo.git("rev-parse", "HEAD");
        System.out.println(sampleRepo.head());

        sampleRepo.write(FILE_NAME, "OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
        sampleRepo.git("add", FILE_NAME);
        sampleRepo.git("config", "user.name", BAR_NAME);
        sampleRepo.git("config", "user.email", BAR_EMAIL);
        sampleRepo.git("commit", "--message=Change");
        sampleRepo.git("rev-parse", "HEAD");
        System.out.println(sampleRepo.head());
    }

    @Test
    public void shouldCreateEmptyBlamesIfRequestIsEmpty() throws Exception {
        BlameCallback blameCallback = new BlameCallback(new Blames(), ObjectId.fromString(sampleRepo.head()));

        Blames blames = blameCallback.invoke(createRepository(), null);

        assertThat(blames).isEmpty();
    }

    @Test
    public void shouldCreateBlamesIfRequestIsExistingFile() throws Exception {
        Blames blames = new Blames();
        blames.addLine(FILE_NAME, 2);
        blames.addLine(FILE_NAME, 3);
        blames.addLine(FILE_NAME, 4);
        blames.addLine(FILE_NAME, 5);

        BlameCallback blameCallback = new BlameCallback(blames, ObjectId.fromString(sampleRepo.head()));

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

    private void assertThatBlameIsHeadWith(final BlameRequest request, final int line) throws Exception {
        assertThat(request.getName(line)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(line)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(line)).isEqualTo(sampleRepo.head());
    }

    private void assertThatBlameIs(final BlameRequest request, final int line) throws Exception {
        assertThat(request.getName(line)).isEqualTo(FOO_NAME);
        assertThat(request.getEmail(line)).isEqualTo(FOO_EMAIL);
        assertThat(request.getCommit(line)).isNotEqualTo(sampleRepo.head());
    }

    private void assertThatBlameIsEmpty(final BlameRequest request, final int line) throws Exception {
        assertThat(request.getName(line)).isEqualTo("-");
        assertThat(request.getEmail(line)).isEqualTo("-");
        assertThat(request.getCommit(line)).isNotEqualTo(sampleRepo.head());
    }

    private Repository createRepository() throws IOException {
        return new RepositoryBuilder().setWorkTree(sampleRepo.getRoot()).build();
    }
}
