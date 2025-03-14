package io.jenkins.plugins.forensics.git.miner;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitRepositoryMiner}.
 *
 * @author Ullrich Hafner
 */
class GitRepositoryMinerITest extends GitITest {
    private static final String OTHER_FILE = "file";
    private static final FilteredLog LOG = new FilteredLog(StringUtils.EMPTY);

    /**
     * Verifies that the single file of the default initialization does have 1 commit and author.
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    void shouldCollectSingleFile() throws InterruptedException {
        var statisticsPerFile = createRepositoryStatistics();

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
    void shouldCountNumberOfCommits() throws InterruptedException {
        writeFileAsAuthorFoo("First\n");
        var firstCommit = getHead();
        writeFileAsAuthorFoo("Second\nLine\n");
        var head = getHead();

        var statisticsPerFile = createRepositoryStatistics();

        assertThat(statisticsPerFile).hasFiles(OTHER_FILE, ADDITIONAL_FILE);

        assertDefaultFileStatistics(statisticsPerFile);

        var fileStatistics = statisticsPerFile.get(ADDITIONAL_FILE);
        assertThat(fileStatistics)
                .hasFileName(ADDITIONAL_FILE)
                .hasNumberOfAuthors(1)
                .hasNumberOfCommits(2)
                .hasLinesOfCode(2);

        assertThat(fileStatistics.getCommits()).hasSize(2);
        assertThat(fileStatistics.getCommits().get(0)).hasId(firstCommit)
                .hasAuthor(FOO_EMAIL)
                .hasTotalAddedLines(1)
                .hasTotalDeletedLines(0);
        assertThat(fileStatistics.getCommits().get(1)).hasId(head)
                .hasAuthor(FOO_EMAIL)
                .hasTotalAddedLines(2)
                .hasTotalDeletedLines(1);
    }

    /**
     * Verifies that the number of distinct authors is correctly detected.
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    void shouldCountNumberOfAuthors() throws InterruptedException {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        var statisticsPerFile = createRepositoryStatistics();

        assertThat(statisticsPerFile).hasFiles(OTHER_FILE, ADDITIONAL_FILE);

        assertDefaultFileStatistics(statisticsPerFile);

        var fileStatistics = statisticsPerFile.get(ADDITIONAL_FILE);
        assertThat(fileStatistics).hasFileName(ADDITIONAL_FILE)
                .hasNumberOfAuthors(2)
                .hasNumberOfCommits(4)
                .hasLinesOfCode(1)
                .hasAbsoluteChurn(7)
                .hasNumberOfCommits(4);
    }

    private RepositoryStatistics createRepositoryStatistics() throws InterruptedException {
        return new GitRepositoryMiner(createGitClient()).mine(new RepositoryStatistics(), LOG);
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        var fileStatistics = statistics.get(OTHER_FILE);
        assertThat(fileStatistics).hasFileName(OTHER_FILE)
                .hasNumberOfAuthors(1)
                .hasNumberOfCommits(1)
                .hasLinesOfCode(0)
                .hasAbsoluteChurn(0);
    }
}
