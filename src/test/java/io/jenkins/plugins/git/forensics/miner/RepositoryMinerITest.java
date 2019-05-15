package io.jenkins.plugins.git.forensics.miner;

import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import io.jenkins.plugins.git.forensics.GitITest;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link RepositoryMiner}.
 *
 * @author Ullrich Hafner
 */
public class RepositoryMinerITest extends GitITest {
    @Test
    public void shouldCollectSingleFile() {
        Repository repository = createRepository();
        RepositoryMiner collector = new RepositoryMiner(repository);

        Set<String> files = findAllFiles(repository);
        Map<String, FileStatistics> statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).containsOnlyKeys("file");

        assertDefaultFileStatistics(statisticsPerFile);
    }

    private void assertDefaultFileStatistics(final Map<String, FileStatistics> statisticsPerFile) {
        FileStatistics fileStatistics = statisticsPerFile.get("file");
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    @Test
    public void shouldCountNumberOfCommits() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorFoo("Second");

        Repository repository = createRepository();
        Set<String> files = findAllFiles(repository);

        RepositoryMiner collector = new RepositoryMiner(repository);

        Map<String, FileStatistics> statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).containsOnlyKeys("file", FILE_NAME);

        assertDefaultFileStatistics(statisticsPerFile);

        FileStatistics fileStatistics = statisticsPerFile.get(FILE_NAME);
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(2);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

    @Test
    public void shouldCountNumberOfAuthors() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        Repository repository = createRepository();
        Set<String> files = findAllFiles(repository);

        RepositoryMiner collector = new RepositoryMiner(repository);

        Map<String, FileStatistics> statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).containsOnlyKeys("file", FILE_NAME);

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