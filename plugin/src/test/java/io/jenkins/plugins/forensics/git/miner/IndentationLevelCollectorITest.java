package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.hm.hafner.util.FilteredLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link IndentationLevelCollector}. Unlike {@link DiffsCollectorITest} (which relies on the
 * heavyweight {@link io.jenkins.plugins.forensics.git.util.GitITest} base class and a full Jenkins test harness),
 * these tests use a plain, temporary Git repository created directly with JGit's porcelain API. This is sufficient
 * here since {@link IndentationLevelCollector} only depends on plain JGit types ({@link Repository}, {@link
 * ObjectId}) and does not require a configured {@code GitClient} or Jenkins job.
 *
 * @author Akash Manna
 */
class IndentationLevelCollectorITest {
    private static final PersonIdent AUTHOR = new PersonIdent("Test User", "test@example.com");

    private final IndentationLevelCollector collector = new IndentationLevelCollector();

    private Git git;
    private Repository repository;

    @BeforeEach
    void initializeRepository(@TempDir final File baseDirectory) throws IOException, GitAPIException {
        git = Git.init().setDirectory(baseDirectory).call();
        repository = git.getRepository();
    }

    @AfterEach
    void closeRepository() {
        git.close();
        repository.close();
    }

    @Test
    void shouldComputeIndentationLevelForSingleFile() throws IOException, GitAPIException {
        writeFile("Sample.java", String.join("\n", List.of(
                "public class Sample {",     // level 0
                "    void run() {",          // level 1
                "        doSomething();",    // level 2
                "    }",                     // level 1
                "}"                          // level 0
        )));
        ObjectId commitId = commitAll("Add Sample.java");

        var logger = new FilteredLog("Errors");
        Map<String, IndentationLevel> result = collector.collect(repository, commitId, logger);

        assertThat(result).containsKey("Sample.java");
        var level = result.get("Sample.java");
        assertThat(level.getNumberOfLines()).isEqualTo(5);
        assertThat(level.getTotal()).isEqualTo(0 + 1 + 2 + 1 + 0);
        assertThat(level.getMaximum()).isEqualTo(2);
        assertThat(logger.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldCollectMultipleFilesIncludingNestedOnes() throws IOException, GitAPIException {
        writeFile("a.txt", "no indentation\n");
        writeFile("nested/b.txt", "\tindented once\n\t\tindented twice\n");
        ObjectId commitId = commitAll("Add files");

        Map<String, IndentationLevel> result = collector.collect(repository, commitId, new FilteredLog("Errors"));

        assertThat(result).containsOnlyKeys("a.txt", "nested/b.txt");
        assertThat(result.get("a.txt").getMaximum()).isZero();
        assertThat(result.get("nested/b.txt").getMaximum()).isEqualTo(2);
        assertThat(result.get("nested/b.txt").getNumberOfLines()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreEmptyFile() throws IOException, GitAPIException {
        writeFile("empty.txt", "");
        ObjectId commitId = commitAll("Add empty file");

        Map<String, IndentationLevel> result = collector.collect(repository, commitId, new FilteredLog("Errors"));

        assertThat(result).containsKey("empty.txt");
        assertThat(result.get("empty.txt").isEmpty()).isTrue();
    }

    @Test
    void shouldSkipBinaryFiles() throws IOException, GitAPIException {
        writeBinaryFile("image.png", new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});
        writeFile("readme.txt", "    not binary\n");
        ObjectId commitId = commitAll("Add binary and text file");

        var logger = new FilteredLog("Errors");
        Map<String, IndentationLevel> result = collector.collect(repository, commitId, logger);

        assertThat(result).doesNotContainKey("image.png");
        assertThat(result).containsKey("readme.txt");
    }

    @Test
    void shouldUpdateResultsAcrossCommits() throws IOException, GitAPIException {
        writeFile("Growing.java", "level0();\n");
        ObjectId firstCommit = commitAll("Initial version");

        writeFile("Growing.java", "level0();\n\tlevel1();\n\t\tlevel2();\n");
        ObjectId secondCommit = commitAll("Add more nesting");

        var firstResult = collector.collect(repository, firstCommit, new FilteredLog("Errors"));
        var secondResult = collector.collect(repository, secondCommit, new FilteredLog("Errors"));

        assertThat(firstResult.get("Growing.java").getMaximum()).isZero();
        assertThat(secondResult.get("Growing.java").getMaximum()).isEqualTo(2);
        assertThat(secondResult.get("Growing.java").getNumberOfLines()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmptyMapAndLogErrorForUnknownCommit() {
        var logger = new FilteredLog("Errors");

        Map<String, IndentationLevel> result = collector.collect(repository, ObjectId.zeroId(), logger);

        assertThat(result).isEmpty();
        assertThat(logger.getErrorMessages()).isNotEmpty();
    }

    private void writeFile(final String relativePath, final String content) throws IOException {
        Path file = resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void writeBinaryFile(final String relativePath, final byte[] content) throws IOException {
        Path file = resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private Path resolve(final String relativePath) {
        return repository.getWorkTree().toPath().resolve(relativePath);
    }

    private ObjectId commitAll(final String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        RevCommit commit = git.commit()
                .setMessage(message)
                .setAuthor(AUTHOR)
                .setCommitter(AUTHOR)
                .call();

        return commit.getId();
    }
}