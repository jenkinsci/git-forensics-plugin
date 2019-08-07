package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.collections.impl.factory.Lists;
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

import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;
import io.jenkins.plugins.git.forensics.util.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitRepositoryMiner}.
 *
 * @author Ullrich Hafner
 */
public class GitRepositoryMinerITest extends GitITest {
    /** Jenkins rule per suite. */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();

    private static final String FILE = "file";

    /** 
     * Verifies that the single file of the default initialization does have 1 commit and author. 
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    public void shouldCollectSingleFile() throws InterruptedException {
        GitRepositoryMiner miner = createMiner();
        RepositoryStatistics statisticsPerFile = miner.mine(Collections.singleton(FILE));

        assertThat(statisticsPerFile).hasFiles(absolute(FILE));

        assertDefaultFileStatistics(statisticsPerFile);
    }

    /**
     * Verifies that the number of commits is correctly detected.
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    public void shouldCountNumberOfCommits() throws InterruptedException {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorFoo("Second");

        GitRepositoryMiner miner = createMiner();

        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(FILE, FILE_NAME));

        assertThat(statisticsPerFile).hasFiles(absolute(FILE), absolute(FILE_NAME));

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absolute(FILE_NAME));
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(2);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    /**
     * Verifies that the number of distinct authors is correctly detected.
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    public void shouldCountNumberOfAuthors() throws InterruptedException {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        GitRepositoryMiner miner = createMiner();

        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(FILE, FILE_NAME));

        assertThat(statisticsPerFile).hasFiles(absolute(FILE), absolute(FILE_NAME));

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absolute(FILE_NAME));
        assertThat(fileStatistics).hasNumberOfAuthors(2);
        assertThat(fileStatistics).hasNumberOfCommits(4);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    private String absolute(final String fileName) {
        FileLocations locations = new FileLocations(sampleRepo.getRoot());

        return locations.getWorkspace() + fileName;
    }

    private GitRepositoryMiner createMiner() {
        try {
            GitSCM scm = new GitSCM(
                    GitSCM.createRepoList("file:///" + sampleRepo.getRoot(), null),
                    Collections.emptyList(), false, Collections.emptyList(),
                    null, null, Collections.emptyList());
            Run run = mock(Run.class);
            Job job = mock(Job.class);
            when(run.getParent()).thenReturn(job);

            GitClient gitClient = scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(sampleRepo.getRoot()));
            return new GitRepositoryMiner(gitClient);
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = statistics.get(absolute(FILE));
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }
}
