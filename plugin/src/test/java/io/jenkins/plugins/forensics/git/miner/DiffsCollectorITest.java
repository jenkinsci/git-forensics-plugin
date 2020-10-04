package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.junit.Test;

import edu.hm.hafner.util.FilteredLog;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.Commit;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link DiffsCollector}.
 *
 * @author Ullrich Hafner
 */
public class DiffsCollectorITest extends GitITest {
    private static final String MOVED_FILE = "moved";
    private static final String AUTHOR = "author";
    private static final EmptyTreeIterator NULL_ITERATOR = new EmptyTreeIterator();

    /** Verifies that the initial repository contains a single commit. */
    @Test
    public void shouldInitializeCounter() {
        runTest((repository, git) -> {
            String head = getHead();
            Map<String, Commit> actualCommits = createDiff(repository, git, head, NULL_ITERATOR);
            assertThat(actualCommits).hasSize(1).containsKey(INITIAL_FILE);
            assertThat(actualCommits.get(INITIAL_FILE))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that adding lines to the same file works. */
    @Test
    public void shouldCountAddedLines() {
        String firstCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\n");
        String secondCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\nThird Line\n");
        String head = getHead();

        runTest((repository, git) -> {
            Map<String, Commit> allDeltas = createDiff(repository, git, head, NULL_ITERATOR);
            assertThat(allDeltas).hasSize(2).containsKeys(INITIAL_FILE, ADDITIONAL_FILE);
            assertThat(allDeltas.get(INITIAL_FILE)).hasTotalAddedLines(0).hasTotalDeletedLines(0);
            assertThat(allDeltas.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(3)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            Map<String, Commit> deltaFirstCommit = createDiff(repository, git, secondCommit, firstCommit);
            assertThat(deltaFirstCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaFirstCommit.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(2)
                    .hasTotalDeletedLines(0)
                    .hasId(secondCommit)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            Map<String, Commit> deltaLastCommit = createDiff(repository, git, head, secondCommit);
            assertThat(deltaLastCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaLastCommit.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(1)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that deleting lines from the same file works. */
    @Test
    public void shouldCountDeletedLines() {
        String firstCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\n");
        String secondCommit = getHead();
        writeFileAsAuthorBar("First Line\n");
        String head = getHead();

        runTest((repository, git) -> {
            Map<String, Commit> allDeltas = createDiff(repository, git, secondCommit, firstCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(2)
                    .hasTotalDeletedLines(0)
                    .hasId(secondCommit)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            Map<String, Commit> deltaLastCommit = createDiff(repository, git, head, secondCommit);
            assertThat(deltaLastCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaLastCommit.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(1)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that deleting multiple non-overlapping line blocks in the same file works. */
    @Test
    public void shouldHandleMultipleSections() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String secondCommit = getHead();
        writeFileAsAuthorBar("1 =====\n3 =====\n5 =====\n");
        String head = getHead();

        runTest((repository, git) -> {
            Map<String, Commit> allDeltas = createDiff(repository, git, head, secondCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(2)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that removing a file correctly identifies the deleted lines. */
    @Test
    public void shouldHandleRemovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String initialCommit = getHead();
        git("rm", ADDITIONAL_FILE);
        commit("Removed file");

        runTest((repository, git) -> {
            String head = getHead();
            Map<String, Commit> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(5)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that moving a file correctly identifies an entry that contains a reference to the old file name. */
    @Test
    public void shouldHandleMovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String initialCommit = getHead();
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        commit("Moved file");

        runTest((repository, git) -> {
            String head = getHead();
            Map<String, Commit> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(MOVED_FILE);
            assertThat(allDeltas.get(MOVED_FILE))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that moving and changing a file correctly identifies the changed lines in the moved file. */
    @Test
    public void shouldHandleMovedAndChangedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String initialCommit = getHead();
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        writeFile(MOVED_FILE, "1 =====\n2a =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        commit("Moved and changed file");

        verifyMovedAndChangedFile(initialCommit);
    }

    /** Verifies that changing and moving a file correctly identifies the changed lines in the moved file. */
    @Test
    public void shouldHandleChangedAndMovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String initialCommit = getHead();
        writeFile(ADDITIONAL_FILE, "1 =====\n2a =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        commit("Moved and changed file");

        verifyMovedAndChangedFile(initialCommit);
    }

    private void verifyMovedAndChangedFile(final String initialCommit) {
        runTest((repository, git) -> {
            String head = getHead();
            Map<String, Commit> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(MOVED_FILE);
            assertThat(allDeltas.get(MOVED_FILE))
                    .hasTotalAddedLines(1)
                    .hasTotalDeletedLines(1)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    private Map<String, Commit> createDiff(final Repository repository,
            final Git git, final String newCommit, final String oldCommit) throws IOException {
        AbstractTreeIterator toTree = CommitAnalyzer.createTreeIteratorFor(repository, oldCommit);
        return createDiff(repository, git, newCommit, toTree);
    }

    private Map<String, Commit> createDiff(final Repository repository, final Git git, final String newCommit,
            final AbstractTreeIterator toTree) {
        DiffsCollector collector = new DiffsCollector();
        return collector.getFilesAndDiffEntriesForCommit(repository, git,
                new Commit(newCommit, AUTHOR, 0),
                toTree, new FilteredLog("Errors")
        );
    }
}
