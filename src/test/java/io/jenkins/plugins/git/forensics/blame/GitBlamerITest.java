package io.jenkins.plugins.git.forensics.blame;

import java.io.IOException;
import java.util.Collections;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.extensions.GitSCMExtension;

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.git.forensics.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
public class GitBlamerITest extends GitITest {
    private static final String FILE_NAME = "source.txt";
    private static final String FOO_NAME = "Foo";
    private static final String FOO_EMAIL = "foo@jenkins.io";
    private static final String BAR_NAME = "Bar";
    private static final String BAR_EMAIL = "bar@jenkins.io";

    /** Jenkins rule per suite. */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();

    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    public void shouldCreateEmptyBlamesIfRequestIsEmpty() {
        GitBlamer gitBlamer = createBlamer();

        Blames blames = gitBlamer.blame(new FileLocations());

        assertThat(blames).isEmpty();
    }

    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    public void shouldCreateBlamesIfRequestIsExistingFile() {
        create2RevisionsWithDifferentAuthors();

        FileLocations input = new FileLocations();
        input.addLine(FILE_NAME, 2);
        input.addLine(FILE_NAME, 3);
        input.addLine(FILE_NAME, 4);
        input.addLine(FILE_NAME, 5);

        GitBlamer gitBlamer = createBlamer();

        Blames blames = gitBlamer.blame(input);

        assertThat(blames).isNotEmpty();
        assertThat(blames).hasFiles(FILE_NAME);
        assertThat(blames).hasNoErrorMessages();
        assertThat(blames).hasInfoMessages("-> blamed authors of issues in 1 files");

        FileBlame request = blames.get(FILE_NAME);
        assertThat(request).hasFileName(FILE_NAME);

        assertThatBlameIsEmpty(request, 1);
        assertThatBlameIs(request, 2);
        assertThatBlameIsHeadWith(request, 3);
        assertThatBlameIsHeadWith(request, 4);
        assertThatBlameIs(request, 5);
        assertThatBlameIsEmpty(request, 6);
    }

    private GitBlamer createBlamer() {
        try {
            GitSCM scm = new GitSCM(
                    GitSCM.createRepoList("file:///" + sampleRepo.getRoot(), null),
                    Collections.emptyList(), false, Collections.<SubmoduleConfig>emptyList(),
                    null, null, Collections.<GitSCMExtension>emptyList());
            Run run = mock(Run.class);
            Job job = mock(Job.class);
            when(run.getParent()).thenReturn(job);

            GitClient gitClient = scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(sampleRepo.getRoot()));
            return new GitBlamer(gitClient, "HEAD");
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    private void create2RevisionsWithDifferentAuthors() {
        writeFile(FILE_NAME, "OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");
        git("add", FILE_NAME);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        git("commit", "--message=Init");
        git("rev-parse", "HEAD");

        writeFile(FILE_NAME, "OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
        git("add", FILE_NAME);
        git("config", "user.name", BAR_NAME);
        git("config", "user.email", BAR_EMAIL);
        git("commit", "--message=Change");
        git("rev-parse", "HEAD");
    }

    private void assertThatBlameIsHeadWith(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(line)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(line)).isEqualTo(getHead());
    }

    private void assertThatBlameIs(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(FOO_NAME);
        assertThat(request.getEmail(line)).isEqualTo(FOO_EMAIL);
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }

    private void assertThatBlameIsEmpty(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo("-");
        assertThat(request.getEmail(line)).isEqualTo("-");
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }
}
