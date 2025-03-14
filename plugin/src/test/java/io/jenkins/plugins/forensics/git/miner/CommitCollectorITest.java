package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link CommitCollector}.
 *
 * @author Ullrich Hafner
 */
class CommitCollectorITest extends GitITest {
    /** Verifies that the initial repository contains a single commit. */
    @Test
    void shouldFindInitialCommits() {
        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");

            assertThat(actualCommits).hasSize(1);
            assertThat(actualCommits.get(0).getId()).isEqualTo(getHeadCommit());
        });
    }

    /** Verifies that additional commits are recorded and sorted correctly. */
    @Test
    void shouldFindAdditionalCommits() {
        var start = getHeadCommit();
        writeFileAsAuthorFoo("Middle");
        var middle = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");
            assertThat(actualCommits).hasSize(2);
            assertThat(extractCommitIds(actualCommits)).containsExactly(middle, start);
        });

        writeFileAsAuthorBar("Second");
        var head = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");
            assertThat(actualCommits).hasSize(3);
            assertThat(extractCommitIds(actualCommits)).containsExactly(head, middle, start);
        });
    }

    /** Verifies that commits are recorded only up to a given commit. */
    @Test
    void shouldFindCommitsUpToGivenCommit() {
        var start = getHeadCommit();
        writeFileAsAuthorFoo("Middle");
        var middle = getHeadCommit();
        writeFileAsAuthorBar("Head");
        var head = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> upToHeadCommits = findCommits(repository, git, start.getName());
            assertThat(upToHeadCommits).hasSize(2);
            assertThat(extractCommitIds(upToHeadCommits)).containsExactly(head, middle);

            List<RevCommit> upToMiddleCommits = findCommits(repository, git, middle.getName());
            assertThat(upToMiddleCommits).hasSize(1);
            assertThat(extractCommitIds(upToMiddleCommits)).containsExactly(head);
        });
    }

    private Stream<ObjectId> extractCommitIds(final List<RevCommit> actualCommits) {
        return actualCommits.stream().map(RevCommit::getId);
    }

    private List<RevCommit> findCommits(final Repository repository, final Git git, final String lastCommitId)
            throws IOException, GitAPIException {
        var collector = new CommitCollector();

        return collector.findAllCommits(repository, git, lastCommitId, new FilteredLog("unused"));
    }
}
