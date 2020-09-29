package io.jenkins.plugins.forensics.git.miner;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import edu.hm.hafner.util.FilteredLog;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;

/**
 * Tests the class {@link DiffsCollector}.
 *
 * @author Ullrich Hafner
 */
public class DiffsCollectorITest extends GitITest {

    private static final String MOVED_FILE = "moved";

    /** Verifies that the initial repository contains a single commit. */
    @Test
    public void shouldInitializeCounter() {
        runTest((repository, git) -> {
            Map<String, CommitFileDelta> actualCommits = createDiff(repository, git, null, getHead());
            assertThat(actualCommits).hasSize(1).containsKey(INITIAL_FILE);
            assertThat(actualCommits.get(INITIAL_FILE)).hasAddedLines(0)
                    .hasDeletedLines(0)
                    .hasCommitId(getHead())
                    .hasTotalLoc(0);
        });
    }

    private Map<String, CommitFileDelta> createDiff(final org.eclipse.jgit.lib.Repository repository,
            final org.eclipse.jgit.api.Git git, final String oldCommit, final String newCommit) {
        DiffsCollector collector = new DiffsCollector();

        return collector.getFilesAndDiffEntriesFromCommit(repository, git,
                oldCommit, newCommit, new FilteredLog("Errors"), Collections.emptyMap());
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
            Map<String, CommitFileDelta> allDeltas = createDiff(repository, git, null, head);
            assertThat(allDeltas).hasSize(2).containsKeys(INITIAL_FILE, ADDITIONAL_FILE);
            assertThat(allDeltas.get(INITIAL_FILE)).hasAddedLines(0).hasDeletedLines(0);
            assertThat(allDeltas.get(ADDITIONAL_FILE)).hasAddedLines(3)
                    .hasDeletedLines(0)
                    .hasTotalLoc(3)
                    .hasCommitId(head);

            Map<String, CommitFileDelta> deltaFirstCommit = createDiff(repository, git, firstCommit, secondCommit);
            assertThat(deltaFirstCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaFirstCommit.get(ADDITIONAL_FILE)).hasAddedLines(2)
                    .hasDeletedLines(0)
                    .hasTotalLoc(2)
                    .hasCommitId(secondCommit);

            Map<String, CommitFileDelta> deltaLastCommit = createDiff(repository, git, secondCommit, head);
            assertThat(deltaLastCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaLastCommit.get(ADDITIONAL_FILE)).hasAddedLines(1)
                    .hasDeletedLines(0)
                    .hasTotalLoc(1)
                    .hasCommitId(head);
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
            Map<String, CommitFileDelta> allDeltas = createDiff(repository, git, firstCommit, secondCommit);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE)).hasAddedLines(2)
                    .hasDeletedLines(0)
                    .hasTotalLoc(2)
                    .hasCommitId(secondCommit);

            Map<String, CommitFileDelta> deltaLastCommit = createDiff(repository, git, secondCommit, head);
            assertThat(deltaLastCommit).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(deltaLastCommit.get(ADDITIONAL_FILE)).hasAddedLines(0)
                    .hasDeletedLines(1)
                    .hasTotalLoc(-1)
                    .hasCommitId(head);
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
            Map<String, CommitFileDelta> allDeltas = createDiff(repository, git, secondCommit, head);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE)).hasAddedLines(0)
                    .hasDeletedLines(2)
                    .hasTotalLoc(-2)
                    .hasCommitId(getHead());
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
            Map<String, CommitFileDelta> allDeltas = createDiff(repository, git, initialCommit, head);
            assertThat(allDeltas).hasSize(1).containsKeys(ADDITIONAL_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE)).hasAddedLines(0)
                    .hasDeletedLines(5)
                    .hasTotalLoc(-5)
                    .hasCommitId(head);
        });
    }

    /** Verifies that moving a file correctly identifies the moved lines (old file: deleted, new file: added). */
    @Test
    public void shouldHandleMovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        String initialCommit = getHead();
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        commit("Moved file");

        runTest((repository, git) -> {
            String head = getHead();
            Map<String, CommitFileDelta> allDeltas = createDiff(repository, git, initialCommit, head);
            assertThat(allDeltas).hasSize(2).containsKeys(ADDITIONAL_FILE, MOVED_FILE);
            assertThat(allDeltas.get(ADDITIONAL_FILE)).hasAddedLines(0)
                    .hasDeletedLines(5)
                    .hasTotalLoc(-5)
                    .hasCommitId(head);
            assertThat(allDeltas.get(MOVED_FILE)).hasAddedLines(5)
                    .hasDeletedLines(0)
                    .hasTotalLoc(5)
                    .hasCommitId(head);
        });
    }
}
