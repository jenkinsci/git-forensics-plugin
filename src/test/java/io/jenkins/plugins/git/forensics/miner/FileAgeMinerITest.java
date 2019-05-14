package io.jenkins.plugins.git.forensics.miner;

import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import io.jenkins.plugins.git.forensics.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link FileAgeMiner}.
 *
 * @author Ullrich Hafner
 */
public class FileAgeMinerITest extends GitITest {
    @Test
    public void shouldCollectSingleFile() {
        Repository repository = createRepository();
        FilesCollector filesCollector = new FilesCollector(repository);
        Set<String> files = filesCollector.findAllFor(getHeadCommit());

        FileAgeMiner collector = new FileAgeMiner(repository);

        assertThat(collector.computeAge(files)).containsExactly(entry("file", 0L));
    }

}