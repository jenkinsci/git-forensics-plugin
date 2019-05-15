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
        FilesCollector filesCollector = new FilesCollector(repository);
        Set<String> files = filesCollector.findAllFor(getHeadCommit());

        RepositoryMiner collector = new RepositoryMiner(repository);

        Map<String, FileStatistics> statisticsPerFile = collector.analyze(files);

        assertThat(statisticsPerFile).containsOnlyKeys("file");

        FileStatistics fileStatistics = statisticsPerFile.get("file");
        assertThat(fileStatistics).hasNumberOfAuthors(1);
        assertThat(fileStatistics).hasNumberOfCommits(1);
        assertThat(fileStatistics).hasAgeInDays(0);
        assertThat(fileStatistics).hasLastModifiedInDays(0);
    }

}