package io.jenkins.plugins.forensics.git.miner;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.impl.factory.Lists;
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
        GitRepositoryMiner miner = createMiner();
        String absolute = OTHER_FILE;
        RepositoryStatistics statisticsPerFile = miner.mine(Collections.singleton(absolute), LOG);

        assertThat(statisticsPerFile).hasFiles(absolute);

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

        String absoluteOtherFile = OTHER_FILE;
        String absoluteFile = GitITest.FILE_NAME;

        GitRepositoryMiner miner = createMiner();

        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(absoluteOtherFile, absoluteFile), LOG);

        assertThat(statisticsPerFile).hasFiles(absoluteOtherFile, absoluteFile);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absoluteFile);
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

        String absoluteOtherFile = OTHER_FILE;
        String absoluteFile = GitITest.FILE_NAME;

        GitRepositoryMiner miner = createMiner();
        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(absoluteFile, absoluteOtherFile), LOG);

        assertThat(statisticsPerFile).hasFiles(absoluteOtherFile, absoluteFile);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absoluteFile);
        assertThat(fileStatistics).hasNumberOfAuthors(2);
        assertThat(fileStatistics).hasNumberOfCommits(4);
    }

    private GitRepositoryMiner createMiner() {
        return new GitRepositoryMiner(createGitClient());
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = statistics.get(OTHER_FILE);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
    }
}
