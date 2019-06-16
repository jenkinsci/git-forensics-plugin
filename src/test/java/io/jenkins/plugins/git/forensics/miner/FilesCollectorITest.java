package io.jenkins.plugins.git.forensics.miner;

import org.junit.Test;

import io.jenkins.plugins.git.forensics.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link FilesCollector}.
 *
 * @author Ullrich Hafner
 */
public class FilesCollectorITest extends GitITest {
    /** Verifies that the single file of the default initialization will be found. */
    @Test
    public void shouldCollectSingleFile() {
        FilesCollector collector = new FilesCollector(createRepository());

        assertThat(collector.findAllFor(getHeadCommit())).containsExactly("file");
    }

    /** Verifies that several files in the top level directory will be found. */
    @Test
    public void shouldCollectFlatFiles() {
        for (int i = 1; i < 4; i++) {
            String fileName = String.format("file%d.txt", i);
            writeFile(fileName, "Some text.");
            git("add", fileName);
        }
        git("commit", "--message=Additional");
        git("rev-parse", "HEAD");

        FilesCollector collector = new FilesCollector(createRepository());

        assertThat(collector.findAllFor(getHeadCommit())).containsExactlyInAnyOrder(
                "file",
                "file1.txt", "file2.txt", "file3.txt");
    }

    /** Verifies that several files in a hierarchy of directories will be found. */
    @Test
    public void shouldCollectHierarchyOfFiles() {
        String path = "";
        for (int j = 1; j < 4; j++) {
            for (int i = 1; i < 4; i++) {
                String fileName = String.format("%sfile%d.txt", path, i);
                writeFile(fileName, "Some text.");
                git("add", fileName);
            }
            path += j + "/";
        }
        git("commit", "--message=Additional");
        git("rev-parse", "HEAD");

        FilesCollector collector = new FilesCollector(createRepository());

        assertThat(collector.findAllFor(getHeadCommit())).containsExactlyInAnyOrder(
                "file",
                "file1.txt", "file2.txt", "file3.txt",
                "1/file1.txt", "1/file2.txt", "1/file3.txt",
                "1/2/file1.txt", "1/2/file2.txt", "1/2/file3.txt");
    }
}
