package io.jenkins.plugins.git.forensics.miner;

import java.util.Set;

import org.eclipse.jgit.lib.Repository;
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
public class RepositoryMinerITest extends GitITest {
    private static final String FILE = "file";

    /** Verifies that the single file of the default initialization does have 1 commit and author. */
    @Test
    public void shouldCollectSingleFile() {
        Repository repository = createRepository();
        GitRepositoryMiner collector = new GitRepositoryMiner(repository);

        Set<String> files = findAllFiles(repository);
        RepositoryStatistics statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).hasFiles(FILE);

        assertDefaultFileStatistics(statisticsPerFile);
    }

    private void assertDefaultFileStatistics(final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = statistics.get(FILE);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    /** Verifies that the number of commits is correctly detected. */
    @Test
    public void shouldCountNumberOfCommits() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorFoo("Second");

        Repository repository = createRepository();
        Set<String> files = findAllFiles(repository);

        GitRepositoryMiner collector = new GitRepositoryMiner(repository);

        RepositoryStatistics statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).hasFiles(FILE, FILE_NAME);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(FILE_NAME);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(2);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    /** Verifies that the number of distinct authors is correctly detected. */
    @Test
    public void shouldCountNumberOfAuthors() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        Repository repository = createRepository();
        Set<String> files = findAllFiles(repository);

        GitRepositoryMiner collector = new GitRepositoryMiner(repository);

        RepositoryStatistics statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).hasFiles(FILE, FILE_NAME);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(FILE_NAME);
        assertThat(fileStatistics).hasNumberOfAuthors(2);
        assertThat(fileStatistics).hasNumberOfCommits(4);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    private Set<String> findAllFiles(final Repository repository) {
        FilesCollector filesCollector = new FilesCollector(repository);
        return filesCollector.findAllFor(getHeadCommit());
    }

}
