package io.jenkins.plugins.git.forensics.miner;

import java.util.Collections;

import org.eclipse.collections.impl.factory.Lists;
import org.junit.Test;

import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;
import io.jenkins.plugins.git.forensics.util.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitRepositoryMiner}.
 *
 * @author Ullrich Hafner
 */
public class GitRepositoryMinerITest extends GitITest {
    private static final String OTHER_FILE = "file";

    /** 
     * Verifies that the single file of the default initialization does have 1 commit and author. 
     *
     * @throws InterruptedException
     *         never thrown
     */
    @Test
    public void shouldCollectSingleFile() throws InterruptedException {
        GitRepositoryMiner miner = createMiner();
        String absolute = absolute(OTHER_FILE);
        RepositoryStatistics statisticsPerFile = miner.mine(Collections.singleton(absolute));

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

        String absoluteOtherFile = absolute(OTHER_FILE);
        String absoluteFile = absolute(FILE_NAME);

        GitRepositoryMiner miner = createMiner();

        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(absoluteOtherFile, absoluteFile));

        assertThat(statisticsPerFile).hasFiles(absoluteOtherFile, absoluteFile);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absoluteFile);
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

        String absoluteOtherFile = absolute(OTHER_FILE);
        String absoluteFile = absolute(FILE_NAME);

        GitRepositoryMiner miner = createMiner();
        RepositoryStatistics statisticsPerFile = miner.mine(Lists.fixedSize.of(absoluteFile, absoluteOtherFile));

        assertThat(statisticsPerFile).hasFiles(absoluteOtherFile, absoluteFile);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(absoluteFile);
        assertThat(fileStatistics).hasNumberOfAuthors(2);
        assertThat(fileStatistics).hasNumberOfCommits(4);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    private GitRepositoryMiner createMiner() {
        return new GitRepositoryMiner(createGitClient());
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = statistics.get(absolute(OTHER_FILE));
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }
}
