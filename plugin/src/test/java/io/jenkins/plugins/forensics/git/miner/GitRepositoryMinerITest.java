package io.jenkins.plugins.forensics.git.miner;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import edu.hm.hafner.util.FilteredLog;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitRepositoryMiner}.
 *
 * @author Ullrich Hafner
 */
public class GitRepositoryMinerITest extends GitITest {
    private static final String OTHER_FILE = "file";
    private static final FilteredLog LOG = new FilteredLog(StringUtils.EMPTY);

    /**
     * Verifies that the single file of the default initialization does have 1 commit and author.
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    public void shouldCollectSingleFile() throws InterruptedException {
        RepositoryStatistics statisticsPerFile = createRepositoryStatistics();

        assertThat(statisticsPerFile).hasFiles(OTHER_FILE);

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

        RepositoryStatistics statisticsPerFile = createRepositoryStatistics();

        assertThat(statisticsPerFile).hasFiles(OTHER_FILE, GitITest.ADDITIONAL_FILE);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(GitITest.ADDITIONAL_FILE);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(2);
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

        RepositoryStatistics statisticsPerFile = createRepositoryStatistics();

        assertThat(statisticsPerFile).hasFiles(OTHER_FILE, GitITest.ADDITIONAL_FILE);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(GitITest.ADDITIONAL_FILE);
        assertThat(fileStatistics).hasNumberOfAuthors(2);
        assertThat(fileStatistics).hasNumberOfCommits(4);
    }

    private RepositoryStatistics createRepositoryStatistics() throws InterruptedException {
        return new GitRepositoryMiner(createGitClient()).mine(new RepositoryStatistics(), LOG);
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = statistics.get(OTHER_FILE);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
    }
}
